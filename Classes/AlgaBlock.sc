// AlgaLib: SuperCollider implementation of the Alga live coding language
// Copyright (C) 2020-2021 Francesco Cameli

// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.

// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.

// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

AlgaBlock {

	//all the nodes for this block
	var <nodesDict;

	//the ordered array of nodes for the block
	var <orderedArray;

	//A dict storing node -> (true or false) to state if all inputs have been checked or not
	var <statesDict;

	//Counter for correct ordering of entries in orderedArray
	var <runningIndex;

	//bottom most and top most nodes in this block
	var <bottomOutNodes; //, <topInNodes;

	//the index for this block in the AlgaBlocksDict global dict
	var <blockIndex;

	*new { | inBlockIndex |
		^super.new.init(inBlockIndex);
	}

	init { | inBlockIndex |

		blockIndex = inBlockIndex;

		nodesDict      = IdentityDictionary(20);
		statesDict     = IdentityDictionary(20);
		bottomOutNodes = IdentityDictionary();
		//topInNodes     = IdentityDictionary();
	}

	addNode { | node, addingInRearrangeBlockLoop = false |
		//Unpack AlgaArg
		if(node.isAlgaArg, {
			node = node.sender;
			if(node.isAlgaNode.not, { ^nil });
		});

		//Add to dict
		nodesDict.put(node, node);

		//Check mismatch
		if(node.blockIndex != blockIndex, {
			//("blockIndex mismatch detected. Using " ++ blockIndex).warn;
			node.blockIndex = blockIndex;

			//Also update statesDict and add one more entry to ordered array
			if(addingInRearrangeBlockLoop, {
				statesDict.put(node, false);
				orderedArray.add(nil);
			});
		});
	}

	removeNode { | node |
		var nodeBlockIndex = node.blockIndex;

		if(nodeBlockIndex != blockIndex, {
			"Trying to remove a node from a block that did not contain it!".warn;
			^nil;
		});

		//Remove from dict
		nodesDict.removeAt(node);

		//Set node's index to -1
		node.blockIndex = -1;

		//Remove this block from AlgaBlocksDict if it's empty!
		if(nodesDict.size == 0, {
			//("Deleting empty block: " ++ blockIndex).warn;
			AlgaBlocksDict.blocksDict.removeAt(nodeBlockIndex);
		});
	}

	rearrangeBlock {
		//ordered collection
		orderedArray = Array.new;

		//Find the nodes with no outNodes (so, the last ones in the chain!), and init the statesDict
		this.findBottomMostOutNodesAndInitStatesDict;

		//init runningIndex
		runningIndex = 0;

		//Store the rearranging results in this.orderedArray
		bottomOutNodes.do({ | node |
			this.rearrangeBlockLoop(node);
		});

		this.sanitizeArray;

		if(orderedArray.size > 0, {
			var sizeMinusOne = orderedArray.size - 1;

			//First one here is the last in the chain.
			var firstNode = orderedArray[0];

			//Must loop reverse to correct order stuff
			sizeMinusOne.reverseDo({ | index |

				var count = index + 1;

				var thisEntry = orderedArray[count];
				var prevEntry = orderedArray[count - 1];

				prevEntry.moveBefore(thisEntry);
			});
		});

		//Remove all the nodes that were not used in the connections
		this.sanitizeDict;
	}

	//Remove nil entries or ones that do not have any in or out connections
	sanitizeArray {
		orderedArray.removeAllSuchThat({ | item |
			var removeCondition = false;

			//If nil, remove entry anyway. Otherwise, look for the other cases.
			if(item == nil, {
				removeCondition = true;
			}, {
				removeCondition = (item.inNodes.size == 0).and(item.outNodes.size == 0);

				if(item.patternOutNodes != nil, {
					removeCondition = removeCondition.and(item.patternOutNodes.size == 0);
				});

				//Check that the current item is not used for patternOut of other nodes
				nodesDict.do({ | algaNode |
					if(algaNode.isAlgaNode, {
						removeCondition = removeCondition.and(
							algaNode.isContainedInPatternOut(item).not
						);
					});
				});
			});

			removeCondition;
		});
	}

	//Remove non-used entries and set their blockIndex back to -1
	sanitizeDict {
		if(orderedArray.size > 0, {
			nodesDict = nodesDict.select({ | node |
				var result;

				block ({ | break |
					orderedArray.do({ | nodeInArray |
						result = (node == nodeInArray);

						//Break on true, otherwise keep searching.
						if(result, {
							break.(nil);
						});
					});
				});

				//Remove node from block
				if(result.not, {
					//("Removing node at group " ++ node.group ++ " from block number " ++ blockIndex).warn;
					this.removeNode(node);
				});

				result;
			});
		}, {
			//Ordered array has size 0. Free all
			nodesDict.do({ | node |
				node.blockIndex = -1;
			});

			nodesDict.clear;
		});
	}

	//Have something to automatically remove Nodes that haven't been touched from the dict
	rearrangeBlockLoop { | node |
		//Unpack AlgaArg if needed
		if(node.isAlgaArg, { node = node.sender });

		if(node.isAlgaNode, {
			var nodeState = statesDict[node];

			//If for any reason the node wasn't already in the nodesDict, add it
			this.addNode(node, true);

			//("adding group " ++ node.group ++ " to block " ++ blockIndex).postln;

			//If this node has never been touched, avoid repetitions
			if(nodeState == false, {
				//put it to true so it's not added again
				//This is essential to make feedback work!
				//it's also essential for this to be before the next loop
				statesDict[node] = true;

				//rearrange inputs to this, this will add the inNodes
				node.inNodes.nodesLoop ({ | inNode |
					this.rearrangeBlockLoop(inNode);
				});

				//rearrange inputs to this, this will add the patternOutNodes
				if(node.patternOutNodes != nil, {
					node.patternOutNodes.nodesLoop ({ | inNode |
						this.rearrangeBlockLoop(inNode);
					});
				});

				//Add node to orderedArray
				orderedArray = orderedArray.add(node);
			});
		});
	}

	findBottomMostOutNodesAndInitStatesDict {
		bottomOutNodes.clear;
		statesDict.clear;

		//If only one node, just add that one.
		if(nodesDict.size == 1, {
			nodesDict.do({ | node |
				bottomOutNodes.put(node, node);
				statesDict.put(node, false);
			});
		}, {
			nodesDict.do({ | node |
				var inNodesCondition = (node.inNodes.size > 0);
				var condition;

				if(node.patternOutNodes != nil, {
					inNodesCondition = inNodesCondition.or(node.patternOutNodes.size > 0)
				});

				condition = (node.outNodes.size == 0).and(inNodesCondition);

				//Find the ones with no outNodes but at least one inNode or patternOutNode
				if(condition, {
					bottomOutNodes.put(node, node);
				});

				//init statesDict for all nodes to false
				statesDict.put(node, false);
			});
		});
	}
}

//Have a global one. No need to make one per server, as server identity is checked already.
AlgaBlocksDict {
	classvar <blocksDict;

	*initClass {
		blocksDict = IdentityDictionary(50);
	}

	*createNewBlockIfNeeded { | receiver, sender |
		//This happens when patching a simple number or array in to set a param
		if((receiver.isAlgaNode.not).or(sender.isAlgaNode.not), { ^nil });

		//Can't connect nodes from two different servers together
		if(receiver.server != sender.server, {
			("AlgaBlocksDict: Trying to create a block between two AlgaNodes on different servers").error;
			^receiver;
		});

		//Check if groups are instantiated, otherwise push action to scheduler
		if((receiver.group != nil).and(sender.group != nil), {
			this.createNewBlockIfNeeded_inner(receiver, sender)
		}, {
			receiver.scheduler.addAction(
				condition: { (receiver.group != nil).and(sender.group != nil) },
				func: {
					this.createNewBlockIfNeeded_inner(receiver, sender)
				}
			)
		});
	}

	*createNewBlockIfNeeded_inner { | receiver, sender |
		var newBlockIndex;
		var newBlock;

		var receiverBlockIndex;
		var senderBlockIndex;
		var receiverBlock;
		var senderBlock;

		//Unpack things
		receiverBlockIndex = receiver.blockIndex;
		senderBlockIndex = sender.blockIndex;
		receiverBlock = blocksDict[receiverBlockIndex];
		senderBlock = blocksDict[senderBlockIndex];

		//Create new block if both connections didn't have any
		if((receiverBlockIndex == -1).and(senderBlockIndex == -1), {

			//"No block inidices. Create new".warn;

			newBlockIndex = UniqueID.next;
			newBlock = AlgaBlock.new(newBlockIndex);

			receiver.blockIndex = newBlockIndex;
			sender.blockIndex = newBlockIndex;

			//Add nodes to the block
			newBlock.addNode(receiver);
			newBlock.addNode(sender);

			//Add block to blocksDict
			blocksDict.put(newBlockIndex, newBlock);
		}, {
			//If they are not already in same block
			if(receiverBlockIndex != senderBlockIndex, {
				//Merge receiver with sender if receiver is not in a block yet
				if(receiverBlockIndex == -1, {

					//"No receiver block index. Set to sender".warn;

					//Check block validity
					if(senderBlock == nil, {
						("Invalid block with index " ++ senderBlockIndex).error;
						^nil;
					});

					//Add proxy to the block
					receiver.blockIndex = senderBlockIndex;
					senderBlock.addNode(receiver);

					//This is for the changed at the end of function...
					newBlockIndex = senderBlockIndex;
				}, {
					//Merge sender with receiver if sender is not in a block yet
					if(senderBlockIndex == -1, {

						//"No sender block index. Set to receiver".warn;

						if(receiverBlock == nil, {
							("Invalid block with index " ++ receiverBlockIndex).error;
							^nil;
						});

						//Add proxy to the block
						sender.blockIndex = receiverBlockIndex;
						receiverBlock.addNode(sender);

						//This is for the changed at the end of function...
						newBlockIndex = receiverBlockIndex;
					}, {
						//Else, it means both nodes are already in blocks.
						//Create a new one and merge them into a new one (including relative ins/outs)

						//"Different block indices. Merge into new one.".warn;

						newBlockIndex = UniqueID.next;
						newBlock = AlgaBlock.new(newBlockIndex);

						//Change group of all nodes in the receiver's previous block
						if(receiverBlock != nil, {
							blocksDict[receiverBlockIndex].nodesDict.do({ | node |
								node.blockIndex = newBlockIndex;
								newBlock.addNode(node);
							});
						});

						//Change group of all nodes in the sender's previous block
						if(senderBlock != nil,  {
							blocksDict[senderBlockIndex].nodesDict.do({ | node |
								node.blockIndex = newBlockIndex;
								newBlock.addNode(node);
							});
						});

						//Remove previous groups
						blocksDict.removeAt(receiverBlockIndex);
						blocksDict.removeAt(senderBlockIndex);

						//Add the two nodes to this new group
						receiver.blockIndex = newBlockIndex;
						sender.blockIndex = newBlockIndex;
						newBlock.addNode(receiver);
						newBlock.addNode(sender);

						//Finally, add the actual block to the dict
						blocksDict.put(newBlockIndex, newBlock);
					});
				});
			});
		});

		//If the function passes through (no actions taken), pass receiver's block instead
		if(newBlockIndex == nil, { newBlockIndex = receiver.blockIndex; });

		//Actually reorder the block's nodes
		newBlock = blocksDict[newBlockIndex];
		if(newBlock != nil, {
			newBlock.rearrangeBlock;
		});
	}
}

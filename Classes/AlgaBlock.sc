// AlgaLib: SuperCollider implementation of Alga, an interpolating live coding environment.
// Copyright (C) 2020-2021 Francesco Cameli.

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
	//All the nodes in the block
	var <nodes;

	//All the nodes that already have been visited
	var <visitedNodes;

	//All the nodes that have FB connections
	var <feedbackNodes;

	//Upper most nodes to start processing from
	var <upperMostNodes;

	//OrderedIdentitySet of IdentitySets of node dependencies
	var <orderedNodes;

	//the index for this block in the AlgaBlocksDict global dict
	var <blockIndex;

	//the Group
	var <group;

	//the ParGroups / Groups within the Group
	var <groups;

	*new { | parGroup |
		^super.new.init(parGroup)
	}

	init { | parGroup |
		nodes          = IdentityDictionary(10);
		visitedNodes   = IdentitySet(10);
		feedbackNodes  = IdentityDictionary(10);
		orderedNodes   = OrderedIdentitySet(10);
		groups         = IdentitySet(10);
		upperMostNodes = IdentitySet(10);
		group          = Group(parGroup);
		blockIndex     = group.nodeID;
	}

	//Add node to the block
	addNode { | node, addingInRearrangeBlockLoop = false |
		//Unpack AlgaArg
		if(node.isAlgaArg, {
			node = node.sender;
			if(node.isAlgaNode.not, { ^nil });
		});

		//Add to dict
		nodes.put(node, node);

		//Add to group
		//node.moveToHead(group);

		//Check mismatch
		if(node.blockIndex != blockIndex, {
			//("blockIndex mismatch detected. Using " ++ blockIndex).warn;
			node.blockIndex = blockIndex;
		});
	}

	//Remove a node from the block. If the block is empty, free its group
	removeNode { | node |
		if(node.blockIndex != blockIndex, {
			"Trying to remove a node from a block that did not contain it!".warn;
			^nil;
		});

		//Remove from dict
		nodes.removeAt(node);

		//Set node's index to -1
		node.blockIndex = -1;

		//Remove this block from AlgaBlocksDict if it's empty!
		if(nodes.size == 0, {
			//("Deleting empty block: " ++ blockIndex).warn;
			AlgaBlocksDict.blocksDict.removeAt(blockIndex);
			group.free;
		});
	}

	//Re-arrange block starting from the sender
	rearrangeBlock { | sender |
		//Stage 1: detect feedback and find upper most nodes
		this.stage1(sender);

		//Debug
		this.debugFeedback;

		//Stage 2: order nodes and create ParGroups / Groups accordingly
		this.stage2;

		//Debug
		this.debugOrderedNodes;

		"".postln;
	}

	//Stage 1: detect feedback and find upper most nodes
	stage1 { | sender |
		//Reset all needed things for stage1
		visitedNodes.clear;
		feedbackNodes.clear;
		upperMostNodes.clear;

		//Detect FBs
		this.detectFeedback(sender);

		//Find upper most nodes
		this.findUpperMostNodes;
	}

	//Debug the FB connections
	debugFeedback {
		feedbackNodes.keysValuesDo({ | sender, receiversSet |
			receiversSet.do({ | receiver |
				("FB: " ++ sender.asString ++ " >> " ++ receiver.asString).error;
			});
		});
	}

	//Add FB pair
	addFeedback { | sender, receiver |
		//Create IdentitySet if needed
		if(feedbackNodes[sender] == nil, {
			feedbackNodes[sender] = IdentitySet();
		});

		//Check if the receiver already had the same connection defined.
		case
		{ feedbackNodes[receiver] == nil } {
			feedbackNodes[sender].add(receiver);
		}
		//Only add one pair
		{ feedbackNodes[receiver].includes(sender).not } {
			feedbackNodes[sender].add(receiver);
		};
	}

	//Loop through each node and check for visited nodes
	detectFeedback { | sender |
		//If not in nodes, add it
		if(sender.blockIndex != blockIndex, { this.addNode(sender) });

		//Set visited
		visitedNodes.add(sender);

		//Check all outNodes for FB connections
		sender.outNodes.keys.do({ | receiver |
			if(visitedNodes.includes(receiver).not, {
				this.detectFeedback(receiver);
			}, {
				this.addFeedback(sender, receiver);
			});
		});
	}

	//Find the nodes with no ins
	findUpperMostNodes {
		nodes.do({ | node |
			//No inNodes: use node as one of the starting points
			if(node.inNodes.size == 0, {
				upperMostNodes.add(node)
			});
		});

		//Look to add just one FB connection if no other were found
		/* if(upperMostNodes.size == 0, {
			var addFirst = false;
			feedbackNodes.do({ | receiver |
				if(addFirst.not, {
					upperMostNodes.add(receiver);
					addFirst = true;
				});
			});
		}); */
	}

	//Loop through currentUpperMostNodes to find nodes to add to newUpperMostNodes
	findNewUpperMostNodes { | currentUpperMostNodes |
		var newUpperMostNodes = IdentitySet(10);
		//Loop through currentUpperMostNodes to build the newNodes IdentitySet
		currentUpperMostNodes.do({ | node |
			//Add it to visitedNodes
			visitedNodes.add(node);

			//If receiver's inNodes have already been visited, it can be added
			node.outNodes.keys.do({ | receiver |
				//Check all of receiver's inNodes
				receiver.inNodes.do({ | sendersSet |
					var sendersVisited = true;
					sendersSet.do({ | sender |
						if(visitedNodes.includes(sender).not, {
							sendersVisited = false;
						});
					});

					//If all senders have been visited, add the receiver to newNodes
					if(sendersVisited, {
						newUpperMostNodes.add(receiver)
					});
				});
			});
		});

		^newUpperMostNodes;
	}

	//Loop through nodes and order them in IdentitySets
	orderNodes { | currentUpperMostNodes |
		if(currentUpperMostNodes.size > 0, {
			var newUpperMostNodes;

			//Add currentUpperMostNodes to orderedNodes
			orderedNodes.add(currentUpperMostNodes);

			//Loop through currentUpperMostNodes to find nodes to add to newUpperMostNodes
			newUpperMostNodes = this.findNewUpperMostNodes(currentUpperMostNodes);

			currentUpperMostNodes.do({ | node |
				node.name.asString.warn;
			});

			newUpperMostNodes.do({ | node |
				node.name.asString.error;
			});

			//Move on to the next group of nodes
			if(newUpperMostNodes.size > 0, {
				this.orderNodes(newUpperMostNodes);
			});
		});
	}

	//Debug orderedNodes
	debugOrderedNodes {
		var counter = 1;
		orderedNodes.do({ | nodesSet |
			("Group " ++ counter).warn;
			nodesSet.do({ | node |
				node.name.asString.postln
			});
			counter = counter + 1;
			"".postln;
		});
	}

	//Stage 2: order nodes and create ParGroups / Groups accordingly
	stage2 {
		//Reset all needed things for stage2
		orderedNodes.clear;
		visitedNodes.clear;

		/* upperMostNodes.do({ | node |
			node.asString.warn;
		}); */

		//upperMostNodes.asString.warn;

		//Fill the orderedNodes
		//this.orderNodes(upperMostNodes);

		//Build groups from orderedNodes
	}
}

//Have a global one. No need to make one per server, as server identity is checked already.
AlgaBlocksDict {
	classvar <blocksDict;

	*initClass {
		blocksDict = IdentityDictionary(20);
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
			//"No block indices. Creating a new one".warn;

			newBlock = AlgaBlock(Alga.parGroup(receiver.server));
			if(newBlock == nil, { ^nil });

			newBlockIndex = newBlock.blockIndex;

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
					//"No receiver block index. Set to sender's".warn;

					//Check block validity
					if(senderBlock == nil, {
						//("Invalid block with index " ++ senderBlockIndex).error;
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
							//("Invalid block with index " ++ receiverBlockIndex).error;
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

						//"Different block indices. Merge into a new one".warn;

						newBlock = AlgaBlock(Alga.parGroup(receiver.server));
						if(newBlock == nil, { ^nil });

						newBlockIndex = newBlock.blockIndex;

						//Change group of all nodes in the receiver's previous block
						if(receiverBlock != nil, {
							blocksDict[receiverBlockIndex].nodes.do({ | node |
								node.blockIndex = newBlockIndex;
								newBlock.addNode(node);
							});
						});

						//Change group of all nodes in the sender's previous block
						if(senderBlock != nil,  {
							blocksDict[senderBlockIndex].nodes.do({ | node |
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
		if(newBlockIndex == nil, { newBlockIndex = receiver.blockIndex });

		//Actually reorder the block's nodes starting from the sender
		newBlock = blocksDict[newBlockIndex];
		if(newBlock != nil, {
			newBlock.rearrangeBlock(sender);
		});
	}
}

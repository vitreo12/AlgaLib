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

/*
TODOS:

1. Fix feedback ordering (shouldn't be reverted if already connected once)

*/

AlgaBlock {
	//All the nodes in the block
	var <nodes;

	//All the nodes that already have been visited
	var <visitedNodes;

	//Used for disconnect
	var <disconnectVisitedNodes;

	//All the nodes that have FB connections
	var <feedbackNodes;

	//Used for disconnect
	var <atLeastOneFeedback = false;

	//OrderedIdentitySet of ordered nodes
	var <orderedNodes;

	//Array of IdentitySets of node dependencies
	var <groupedOrderedNodes;
	var <currentGroupSet;

	//the index for this block in the AlgaBlocksDict global dict
	var <blockIndex;

	//the Group
	var <group;

	//the ParGroups / Groups within the Group
	var <groups;

	//These are the top groups that belonged to merging AlgaBlocks
	var <isMergedGroup = false;
	var <mergedGroups;

	//Nodes at the top of the block
	var <upperMostNodes;

	//Used when all connections are FB
	var <lastSender;

	*new { | parGroup |
		^super.new.init(parGroup)
	}

	init { | parGroup |
		nodes          = OrderedIdentitySet(10);
		feedbackNodes  = IdentityDictionary(10);

		visitedNodes   = OrderedIdentitySet(10);
		disconnectVisitedNodes = IdentitySet(10);

		upperMostNodes = OrderedIdentitySet(10);
		orderedNodes   = OrderedIdentitySet(10);

		groupedOrderedNodes = Array.newClear;

		groups         = OrderedIdentitySet(10);
		group          = Group(parGroup, \addToHead);
		blockIndex     = group.nodeID;

		//Remove from blocks dict when group gets freed, eventually
		group.onFree({
			AlgaBlocksDict.blocksDict.removeAt(blockIndex)
		});
	}

	//Copy an AlgaBlock's nodes
	copyBlock { | senderBlock |
		if(senderBlock != nil, {
			senderBlock.nodes.do({ | node |
				this.addNode(node)
			});

			senderBlock.feedbackNodes.keysValuesDo({ | sender, receiver |
				this.addFeedback(sender, receiver)
			});

			//When merging blocks, the old one's core group must be freed!
			this.addMergedGroup(senderBlock);
			isMergedGroup = true;
		});
	}

	//Copy a node from a block to another and reset
	copyNodeAndReset { | node, senderBlock |
		if((senderBlock != nil).and(node != nil), {
			senderBlock.orderedNodes.remove(node);
			senderBlock.upperMostNodes.remove(node);
			senderBlock.removeNode(node); //this sets blockIndex to -1
			this.addNode(node); //this sets blockIndex to the new one
			senderBlock.feedbackNodes.keysValuesDo({ | sender, receiver |
				if(sender == node, {
					this.addFeedback(sender, receiver);
					senderBlock.removeFeedback(sender, receiver);
				});
			});
		});
	}

	//Add group to mergedGroups for deletion. Also add the mergedGroups that that one had.
	addMergedGroup { | senderBlock |
		mergedGroups = mergedGroups ? IdentitySet();
		mergedGroups.add(senderBlock.group);
		senderBlock.mergedGroups.do({ | oldMergedGroup |
			mergedGroups.add(oldMergedGroup)
		});
	}

	//Add node to the block
	addNode { | node |
		//Unpack AlgaArg
		if(node.isAlgaArg, {
			node = node.sender;
			if(node.isAlgaNode.not, { ^nil });
		});

		//Add to IdentitySet
		nodes.add(node);

		//Check mismatch
		if(node.blockIndex != blockIndex, {
			//("blockIndex mismatch detected. Using " ++ blockIndex).warn;
			node.blockIndex = blockIndex;
		});
	}

	//Remove all FB related to the node
	removeFeedbacks { | node |
		feedbackNodes.removeAt(node);
		feedbackNodes.keysValuesDo({ | sender, receiverSet |
			receiverSet.remove(node);
			if(receiverSet.size == 0, { feedbackNodes.removeAt(sender) })
		});
	}

	//Is connection a feedback one?
	isFeedback { | sender, receiver |
		if(feedbackNodes[sender] != nil, {
			^feedbackNodes[sender].includes(receiver)
		})
		^false
	}

	//Remove a node from the block. If the block is empty, free its group
	removeNode { | node |
		if(node.blockIndex != blockIndex, {
			"Trying to remove a node from a block that did not contain it!".warn;
			^nil;
		});

		("Removing node: " ++ node.asString ++ " from block: " ++ blockIndex).error;

		//Remove from dict
		nodes.remove(node);

		//Remove from FB connections
		this.removeFeedbacks(node);

		//Set node's index to -1
		node.blockIndex = -1;

		//Put node back to top parGroup
		node.moveToHead(Alga.parGroup(node.server));

		//Remove this block from AlgaBlocksDict if it's empty!
		if(nodes.size == 0, {
			//("Deleting empty block: " ++ blockIndex).warn;
			AlgaBlocksDict.blocksDict.removeAt(blockIndex);
			group.free;
		});
	}

	//Debug the FB connections
	debugFeedbacks {
		feedbackNodes.keysValuesDo({ | sender, receiversSet |
			receiversSet.do({ | receiver |
				("FB: " ++ sender.asString ++ " >> " ++ receiver.asString).error;
			});
		});
	}

	//Debug orderedNodes
	debugOrderedNodes {
		"".postln;
		"OrderedNodes:".warn;
		orderedNodes.do({ | node |
			node.asString.warn;
		});
	}

	//Debug groupedOrderedNodes
	debugGroupedOrderedNodes {
		"".postln;
		"GroupedOrderedNodes:".warn;
		groupedOrderedNodes.do({ | set, i |
			("Group " ++ (i+1).asString ++ ":").warn;
			set.do({ | node |
				node.asString.warn;
			});
			"".postln;
		});
	}

	/***********/
	/* CONNECT */
	/***********/

	//Re-arrange block on connection
	rearrangeBlock { | sender, receiver |
		var server, supernova;

		//Update lastSender
		lastSender = sender ? lastSender;

		//Stage 1: detect feedbacks between sender and receiver (if they're valid)
		if((sender != nil).and(receiver != nil), {
			this.stage1(sender, receiver);
		});

		this.debugFeedbacks;

		//Stage 2: order nodes according to I/O
		this.stage2;

		this.debugOrderedNodes;

		//Check lastSender's server (it's been updated if sender != nil in stage1)
		server = if(lastSender != nil, { lastSender.server }, { Server.default });

		//Check if it's supernova
		supernova = Alga.supernova(server);

		//Stages 3-4
		if(supernova, {
			//Stage 3: optimize the ordered nodes (make groups)
			this.stage3;

			this.debugGroupedOrderedNodes;

			//Build ParGroups / Groups out of the optimized ordered nodes
			this.stage4_supernova;
		}, {
			//Simply add orderedNods to group. No stage3 (no need to parallelize order)
			this.stage4_scsynth;
		});

		"".postln;
	}

	/***********/
	/* STAGE 1 */
	/***********/

	//Stage 1: detect feedbacks
	stage1 { | sender, receiver |
		//Clear all needed stuff
		visitedNodes.clear;

		//Start to detect feedback from the receiver
		this.detectFeedback(
			node: receiver,
			blockSender: sender,
			blockReceiver: receiver
		);

		//Find unused feedback loops (from previous disconnections)
		this.findAllUnusedFeedbacks;
	}

	//Add FB pair (both ways)
	addFeedback { | sender, receiver |
		//Create IdentitySets if needed
		if(feedbackNodes[sender] == nil, {
			feedbackNodes[sender] = IdentitySet();
		});
		if(feedbackNodes[receiver] == nil, {
			feedbackNodes[receiver] = IdentitySet();
		});

		//Add the FB connection
		feedbackNodes[sender].add(receiver);
		feedbackNodes[receiver].add(sender);
	}

	//Remove FB pair
	removeFeedback { | sender, receiver |
		if(feedbackNodes[sender] != nil, {
			feedbackNodes[sender].remove(receiver);
			if(feedbackNodes[sender].size == 0, { feedbackNodes.removeAt(sender) });
		});

		if(feedbackNodes[receiver] != nil, {
			feedbackNodes[receiver].remove(sender);
			if(feedbackNodes[receiver].size == 0, { feedbackNodes.removeAt(receiver) });
		});
	}

	//Resolve feedback: check for the inNodes of the node.
	resolveFeedback { | node, nodeSender, blockSender, blockReceiver |
		//If there is a match between who sent the node (nodeSender)
		//and the original sender, AND between the current node and
		//the original receiver, it's feedback!
		nodeSender.asString.warn;
		blockSender.asString.warn;
		node.asString.warn;
		blockReceiver.asString.warn;
		if((nodeSender == blockSender).and(node == blockReceiver), {
			this.addFeedback(blockSender, blockReceiver);
			atLeastOneFeedback = true;
		});
	}

	//Detect feedback for a node
	detectFeedback { | node, nodeSender, blockSender, blockReceiver |
		//If node was already visited, its outNodes have already all been scanned.
		//This means that it can either be a feedback loop to be resolved, or an
		//already completed connection branch.
		var visited = visitedNodes.includes(node);
		if(visited, {
			^this.resolveFeedback(node, nodeSender, blockSender, blockReceiver);
		});

		//This node can be marked as visited
		visitedNodes.add(node);

		//Scan outNodes of this node
		node.activeOutNodes.keys.do({ | outNode |
			//nodeSender == node: the node who sent this outNode
			this.detectFeedback(outNode, node, blockSender, blockReceiver);
		});
	}

	/***********/
	/* STAGE 2 */
	/***********/

	//Stage 2: order nodes
	stage2 {
		var visitedUpperMostNodes;

		//Clear all needed stuff
		visitedNodes.clear;
		orderedNodes.clear;

		//Find the upper most nodes. Use lastSender if none found
		this.findUpperMostNodes;

		//Need to know upperMostNodes' size
		visitedUpperMostNodes = Array.newClear(upperMostNodes.size);

		//Order the nodes starting from upperMostNodes
		this.orderNodes(visitedUpperMostNodes);

		//Find blocks to separate
		this.findBlocksToSplit(visitedUpperMostNodes);
	}

	//Find the upper most nodes
	findUpperMostNodes {
		//Reset
		upperMostNodes.clear;

		//Nodes with no inputs
		nodes.do({ | node |
			if(node.activeInNodes.size == 0, {
				upperMostNodes.add(node)
			});
		});

		//All FB connections. Use lastSender as upper most node
		if(upperMostNodes.size == 0, {
			upperMostNodes.add(lastSender)
		});
	}

	//Create a new block
	createNewBlock { | server |
		var newBlock = AlgaBlock(Alga.parGroup(server));
		newBlock.blockIndex.postln;
		AlgaBlocksDict.blocksDict[newBlock.blockIndex] = newBlock;
		^newBlock;
	}

	//Create new block and order it
	createNewBlockAndOrderIt { | nodesSet |
		var firstNode, server, newBlock;
		block { | break |
			nodesSet.do({ | node |
				firstNode = node;
				server = node.server;
				break.value(nil)
			})
		};
		newBlock = this.createNewBlock(server);
		nodesSet.do({ | node | newBlock.copyNodeAndReset(node, this) });
		newBlock.rearrangeBlock(firstNode); //Have at least a sender for lastSender
	}

	//Find blocks that should be separated. Basically, this checks that
	//everytime a branch is computed from an upperMostNode, at least one of its visited nodes
	//must be in another branch, meaning they're linked. If a branch does not
	//have any node in common, it means it can be split into another block entirely.
	findBlocksToSplit { | visitedUpperMostNodes |
		if(visitedUpperMostNodes.size > 1, {
			visitedUpperMostNodes.do({ | entry1, i |
				//Only consider branches with more than one node
				if(entry1.size > 1, {
					var containsAnyOtherNode = false;

					block { | break |
						visitedUpperMostNodes.do({ | entry2 |
							if(entry1 != entry2, {
								if(entry1.sect(entry2).size > 0, {
									containsAnyOtherNode = true;
									break.value(nil);
								});
							});
						});
					};

					//Create new block and order it
					if(containsAnyOtherNode.not, {
						this.createNewBlockAndOrderIt(entry1);
					});
				});
			});
		});
	}

	//Order the inNodes of a node
	orderNodeInNodes { | node, visitedNodesThisUpperMostNode |
		//Detect FB connections that might need to be added here (check comment later)
		var fbConnectionsToAdd = OrderedIdentitySet();

		//Add to visited
		visitedNodes.add(node);
		visitedNodesThisUpperMostNode.add(node);

		//Check inNodes
		node.activeInNodes.do({ | sendersSet |
			sendersSet.do({ | sender |
				//If not visited and not FB connection, check its inNodes too
				var visited = visitedNodes.includes(sender);
				var isFeedback = this.isFeedback(sender, node);
				if(visited.not.and(isFeedback.not), {
					this.orderNodeInNodes(sender, visitedNodesThisUpperMostNode);
				}, {
					//These might be added (check comment later)
					if(isFeedback, {
						fbConnectionsToAdd.add(sender);
					});
				});
			});
		});

		//All its inNodes have been added: we can now add the node to orderedNodes
		orderedNodes.add(node);

		//This is to cover a very specific situation in which, at the end of a chain,
		//only a FB connection remains, with no other ins / outs. This must be added
		//after the node with which FB is happening. Basically, this connection would
		//not be reached otherwise, so it needs to be added here.
		fbConnectionsToAdd.do({ | fbNode |
			var areAllFbNodeOutsFb = true;
			var areAllFbNodeInsFb  = true;

			block { | break |
				fbNode.activeOutNodes.keys.do({ | receiver |
					if(this.isFeedback(fbNode, receiver).not, {
						areAllFbNodeOutsFb = false;
						break.value(nil);
					});
				});
				fbNode.activeInNodes.do({ | senderSet |
					senderSet.do({ | sender |
						if(this.isFeedback(sender, fbNode).not, {
							areAllFbNodeInsFb = false;
							break.value(nil);
						});
					});
				});
			};

			if(areAllFbNodeInsFb.and(
				areAllFbNodeOutsFb).and(
				orderedNodes.includes(fbNode).not), {
				orderedNodes.add(fbNode);
				visitedNodesThisUpperMostNode.add(fbNode);
			})
		});
	}

	//Order a node
	orderNode { | node, visitedNodesThisUpperMostNode |
		//Check output
		node.activeOutNodes.keys.do({ | receiver |
			var visited = visitedNodes.includes(receiver);
			//If not visited yet, visit inputs and then start ordering it too
			if(visited.not, {
				this.orderNodeInNodes(receiver, visitedNodesThisUpperMostNode);
				this.orderNode(receiver, visitedNodesThisUpperMostNode);
			});

			//Add node to visitedNodesThisUpperMostNode
			visitedNodesThisUpperMostNode.add(receiver);
		});

		//Add node to visitedNodesThisUpperMostNode
		visitedNodesThisUpperMostNode.add(node);
	}

	//Order the nodes ignoring FB
	orderNodes { | visitedUpperMostNodes |
		upperMostNodes.do({ | node, i |
			var visitedNodesThisUpperMostNode = OrderedIdentitySet(10);
			this.orderNode(node, visitedNodesThisUpperMostNode);
			visitedUpperMostNodes[i] = visitedNodesThisUpperMostNode;
		});
	}

	/***********/
	/* STAGE 3 */
	/***********/

	//Stage 3: optimize the ordered nodes (make groups)
	stage3 {
		//Clear all needed stuff
		visitedNodes.clear;
		groupedOrderedNodes = Array.newClear;
		currentGroupSet = IdentitySet();
		groupedOrderedNodes = groupedOrderedNodes.add(currentGroupSet);

		//Run optimizer
		this.optimizeOrderedNodes;
	}

	//Check if groupSet includes a sender of node
	groupSetIncludesASender { | groupSet, node |
		node.activeInNodes.do({ | sendersSet |
			sendersSet.do({ | sender |
				if(groupSet.includes(sender), { ^true });
			})
		})
		^false;
	}

	//Optimize a node
	optimizeNode { | node |
		var currentGroupSetIncludesASender = this.groupSetIncludesASender(currentGroupSet, node);

		//New group to create
		if(currentGroupSetIncludesASender, {
			var newGroupSet = IdentitySet();
			newGroupSet.add(node);
			groupedOrderedNodes = groupedOrderedNodes.add(newGroupSet);
			currentGroupSet = newGroupSet;
		}, {
			//Add to currentGroupSet
			currentGroupSet.add(node);
		});
	}

	//Optimize orderedNodes
	optimizeOrderedNodes {
		orderedNodes.do({ | node |
			this.optimizeNode(node);
		});
	}

	/***********/
	/* STAGE 4 */
	/***********/

	//Build Groups / ParGroups
	buildGroups {
		groupedOrderedNodes.do({ | groupSet |
			var newGroup;
			if(groupSet.size > 1, {
				newGroup = ParGroup(group, \addToTail);
			}, {
				newGroup = Group(group, \addToTail);
			});
			groupSet.do({ | node |
				node.moveToHead(newGroup);
				visitedNodes.add(node);
			});
			groups.add(newGroup);
		});
	}

	//Delete old groups and merged groups
	deleteOldGroups { | oldGroups |
		//If a group has just been merged, free those groups too
		if(isMergedGroup, {
			mergedGroups.do({ | mergedGroup | mergedGroup.free });
			mergedGroups.clear;
			isMergedGroup = false;
		});

		if(oldGroups != nil, {
			oldGroups.do({ | oldGroup | oldGroup.free })
		});
	}

	//Make a block with all the nodes that should be removed.
	removeUnvisitedNodes {
		var nodesToBeRemoved = OrderedIdentitySet();

		nodes.do({ | node |
			if(visitedNodes.includes(node).not, {
				nodesToBeRemoved.add(node);
			});
		});

		//At least 2 nodes, or it will loop forever!
		if((nodes.size != nodesToBeRemoved.size).and(
			nodesToBeRemoved.size > 1), {
			this.createNewBlockAndOrderIt(nodesToBeRemoved)
		}, {
			nodesToBeRemoved.do({ | node |
				this.removeNode(node)
			});
		});
	}

	//Stage 4: build ParGroups / Groups out of the optimized ordered nodes
	stage4_supernova {
		//Copy old groups
		var oldGroups = groups.copy;

		//Clear all needed stuff
		groups.clear;
		visitedNodes.clear;

		//Build new grups
		this.buildGroups;

		//Delete old groups (need to be locked due to fork)
		this.deleteOldGroups(oldGroups);

		//Remove unvisited nodes
		this.removeUnvisitedNodes;
	}

	//Simply add orderedNodes to group
	addOrderedNodesToGroup {
		orderedNodes.do({ | node |
			node.moveToTail(group);
			visitedNodes.add(node);
		});
	}

	//Stage 4 scsynth: simply add orderedNodes to group
	stage4_scsynth {
		//Clear all needed stuff
		visitedNodes.clear;

		//Simply add orderedNodes to group
		this.addOrderedNodesToGroup;

		//In scsynth case, this is only needed to delete merged groups
		this.deleteOldGroups;

		//Remove unvisited nodes
		this.removeUnvisitedNodes;
	}

	/**************/
	/* DISCONNECT */
	/**************/

	//Re-arrange block on disconnect (needs WIP)
	rearrangeBlock_disconnect { | node |
		//Stage 1: free unused FB connections
		this.stage1_disconnect(node);

		this.debugFeedbacks;
	}

	/***********/
	/* STAGE 1 */
	/***********/

	//Free unused FB connections
	stage1_disconnect { | node |
		//Clear needed things
		disconnectVisitedNodes.clear;

		//Find unused FB connections from this node
		this.findUnusedFeedbacks(node);
	}

	//Find unused feedback loops related to node
	findUnusedFeedbacks { | node |
		disconnectVisitedNodes.add(node);
		node.activeOutNodes.keys.do({ | receiver |
			var visited = disconnectVisitedNodes.includes(receiver);

			//(node.asString ++ " >> " ++ receiver.asString).postln;

			//Found a FB connection
			if(this.isFeedback(node, receiver), {
				//detectFeedback uses Class's visitedNodes and atLeastOneFeedback
				visitedNodes.clear;
				atLeastOneFeedback = false;

				//Run FB detection to see if at least one feedback is generated
				this.detectFeedback(
					node: receiver,
					blockSender: node,
					blockReceiver: receiver
				);

				//atLeastOneFeedback.asString.error;

				//If no feedbacks, the pair can be removed.
				//Effectively, this means that the disconnection of the node in
				//rearrangeBlock_disconnect freed this particular feedback loop
				if(atLeastOneFeedback.not, {
					this.removeFeedback(node, receiver);
				});
			});

			//Not visited, look through
			if(visited.not, {
				this.findUnusedFeedbacks(receiver);
			});
		});
	}

	//Running this on new connections?
	findAllUnusedFeedbacks {
		nodes.do({ | node |
			disconnectVisitedNodes.clear;
			this.findUnusedFeedbacks(node)
		});
	}
}

//Have a global one. No need to make one per server, as server identity is checked already.
AlgaBlocksDict {
	classvar <blocksDict;

	*initClass {
		var clearFunc = { AlgaBlocksDict.blocksDict.clear };
		blocksDict = IdentityDictionary(20);
		CmdPeriod.add(clearFunc);
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

						//Merge the old blocks into the new one
						newBlock.copyBlock(blocksDict[senderBlockIndex]);
						newBlock.copyBlock(blocksDict[receiverBlockIndex]);

						//Change index
						newBlockIndex = newBlock.blockIndex;

						//Remove previous blocks
						blocksDict.removeAt(receiverBlockIndex);
						blocksDict.removeAt(senderBlockIndex);

						//Add the two nodes to this new block
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

		//Actually reorder the block's nodes starting from the receiver
		this.rearrangeBlock(newBlockIndex, sender, receiver);
	}

	*rearrangeBlock { | index, sender, receiver |
		var block = blocksDict[index];
		if(block != nil, {
			block.rearrangeBlock(sender, receiver);
		});
	}

	*rearrangeBlock_disconnect { | node |
		var index = node.blockIndex;
		var block = blocksDict[index];
		if(block != nil, {
			block.rearrangeBlock_disconnect(node);
		});
	}
}

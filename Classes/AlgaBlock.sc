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
		nodes          = IdentitySet(10);
		feedbackNodes  = IdentityDictionary(10);

		visitedNodes   = IdentitySet(10);
		orderedNodes   = List(10);

		groups         = IdentitySet(10);
		group          = Group(parGroup);
		blockIndex     = group.nodeID;
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

	//Remove a node from the block. If the block is empty, free its group
	removeNode { | node |
		if(node.blockIndex != blockIndex, {
			"Trying to remove a node from a block that did not contain it!".warn;
			^nil;
		});

		//Remove from dict
		nodes.remove(node);

		//Set node's index to -1
		node.blockIndex = -1;

		//Remove this block from AlgaBlocksDict if it's empty!
		if(nodes.size == 0, {
			//("Deleting empty block: " ++ blockIndex).warn;
			AlgaBlocksDict.blocksDict.removeAt(blockIndex);
			group.free;
		});
	}

	//Re-arrange block
	rearrangeBlock { | sender, receiver |
		//Stage 1: detect feedbacks between sender and receiver
		this.stage1(sender, receiver);

		this.debugFeedback;

		//Stage 2: order nodes accrding to I/O
		this.stage2;

		//Stage 3: optimize the ordered nodes (make groups)
		this.stage3;

		//Stage 4: build ParGroups / Groups out of the optimized ordered nodes
		this.stage4;

		"".postln;
	}

	//Stage 1: detect feedbacks
	stage1 { | sender, receiver |
		visitedNodes.clear;
		this.detectFeedback(sender, receiver);
	}

	//Debug the FB connections
	debugFeedback {
		feedbackNodes.keysValuesDo({ | sender, receiversSet |
			receiversSet.do({ | receiver |
				("FB: " ++ sender.asString ++ " >> " ++ receiver.asString).error;
			});
		});
	}

	//Add FB pair (both ways)
	addFeedback { | sender, receiver |
		("FB: " ++ sender.asString ++ " >> " ++ receiver.asString).postln;
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

	//Detect feedback
	detectFeedbackInner { | topSender, topReceiver, sender |
		sender.outNodes.keys.do({ | receiver |
			//Back to starting node == FB
			var isFeedback = topSender == receiver;
			var visited = visitedNodes.includes(receiver);

			if(visited.not.and(isFeedback.not), {
				(sender.asString ++ " >> " ++ receiver.asString).postln;
				visitedNodes.add(receiver);
				this.detectFeedbackInner(topSender, topReceiver, receiver)
			}, {
				if(isFeedback, {
					this.addFeedback(topSender, topReceiver)
				});
			});
		});
	}

	//Detect feedback for a node
	detectFeedback { | topSender, topReceiver |
		this.detectFeedbackInner(topSender, topReceiver, topSender)
	}

	//Stage 2: order nodes
	stage2 {

	}

	//Stage 3: optimize the ordered nodes (make groups)
	stage3 {

	}

	//Stage 4: build ParGroups / Groups out of the optimized ordered nodes
	stage4 {

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
		newBlock = blocksDict[newBlockIndex];
		if(newBlock != nil, {
			newBlock.rearrangeBlock(sender, receiver);
		});
	}
}

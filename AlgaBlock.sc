AlgaBlock {

	//all the proxies for this block
	var <>dictOfProxies;

	//the ordered array of proxies for the block
	var <>orderedArray;

	//A dict storing proxy -> (true or false) to state if all inputs have been checked or not
	var <>statesDict;

	//Counter for correct ordering of entries in orderedArray
	var <>runningIndex;

	//bottom most and top most proxies in this block
	var <>bottomOutProxies, <>topInProxies;

	//if the block has changed form (new proxies added, etc...)
	var <>changed = true;

	//the index for this block in the AlgaBlocksDict global dict
	var <>blockIndex;

	*new {
		arg inBlockIndex;
		^super.new.init(inBlockIndex);
	}

	init {
		arg inBlockIndex;

		this.blockIndex = inBlockIndex;

		dictOfProxies    = IdentityDictionary.new(20);
		statesDict       = Dictionary.new(20);
		bottomOutProxies = IdentityDictionary.new;
		topInProxies     = IdentityDictionary.new;
	}

	addProxy {
		arg proxy, addingInRearrangeBlockLoop = false;

		this.dictOfProxies.put(proxy, proxy);

		if(proxy.blockIndex != this.blockIndex, {

			("blockIndex mismatch detected. Using " ++ this.blockIndex).warn;
			proxy.blockIndex = this.blockIndex;

			//Also update statesDict and add one more entry to ordered array
			if(addingInRearrangeBlockLoop, {
				this.statesDict.put(proxy, false);

				this.orderedArray.add(nil);
			});

		});

		//this.changed = true;
	}

	removeProxy {
		arg proxy;

		var proxyBlockIndex = proxy.blockIndex;

		if(proxyBlockIndex != this.blockIndex, {
			"Trying to remove a proxy from a block that did not contain it!".warn;
			^nil;
		});

		this.dictOfProxies.removeAt(proxy);

		//Remove this block from AlgaBlocksDict if it's empty!
		if(this.dictOfProxies.size == 0, {
			AlgaBlocksDict.blocksDict.removeAt(proxyBlockIndex);
		});

		//this.changed = true;
	}

	rearrangeBlock {
		arg server;

		//Only rearrangeBlock when new connections have been done... It should check for inner connections,
		//not general connections though... It should be done from NodeProxy's side.
		//if(this.changed == true, {

		//ordered collection
		this.orderedArray = Array.newClear(dictOfProxies.size);

		//dictOfProxies.postln;

		//("Reordering proxies for block number " ++ this.blockIndex).warn;

		//this.orderedArray.size.postln;

		//Find the proxies with no outProxies (so, the last ones in the chain!), and init the statesDict
		this.findBottomMostOutProxiesAndInitStatesDict;

		//"Block's bottomOutProxies: ".postln;
		//this.bottomOutProxies.postln;

		//"Block's statesDict: ".postln;
		//this.statesDict.postln;

		//this.orderedArray.postln;

		//init runningIndex
		this.runningIndex = 0;

		//Store the rearranging results in this.orderedArray
		this.bottomOutProxies.do({
			arg proxy;

			this.rearrangeBlockLoop(proxy); //start from index 0
		});

		//"Block's orderedArray: ".postln;
		//this.orderedArray.postln;

		//Actual ordering of groups. Need to be s.bind so that concurrent operations are synced together!
		//Routine.run({

		//server.sync;

		//this.orderedArray.postln;

		//this.dictOfProxies.postln;

		this.sanitizeArray;

		//server.bind allows here to be sure that this bundle will be sent in any case after
		//the NodeProxy creation bundle for interpolation proxies.
		if(orderedArray.size > 0, {
			server.bind({

				var sizeMinusOne = orderedArray.size - 1;

				//First one here is the last in the chain.. I think this should actually be done for each
				//bottomOutProxy...
				var firstProxy = orderedArray[0];


				//is proxy playing?

				//Must loop reverse to correct order stuff
				sizeMinusOne.reverseDo({
					arg index;

					var count = index + 1;

					var thisEntry = orderedArray[count];
					var prevEntry = orderedArray[count - 1];

					prevEntry.beforeMoveNextInterpProxies(thisEntry);

					//(prevEntry.asString ++ " before " ++ thisEntry.asString).postln;

					//thisEntry.class.postln;
					//prevEntry.class.postln;


				});

				//Also move first one (so that its interpolationProxies are correct)
				if(firstProxy != nil, {
					firstProxy.before(firstProxy);
				});
			});
		});

		//REVIEW THIS:
		//this.changed = false;

		//}, 1024);

		//});

		//"BEFORE".postln;
		//this.dictOfProxies.postln;
		//this.orderedArray.postln;

		//Remove all the proxies that were not used in the connections
		this.sanitizeDict;

		//"AFTER".postln;
		//this.dictOfProxies.postln;
		//this.orderedArray.postln;

	}

	//Remove nil entries, coming from mistakes in adding/removing elements to block.
	//Also remove entries that have no outProxies and are not playing to output. This
	//is needed for a particular case when switching proxies connection to something that
	//is playing to output, the ordering could bring the previous playing proxy after the
	//one that is playing out, not accounting if there was fadetime, generating click.
	sanitizeArray {
		//this.orderedArray.removeEvery([nil]);

		this.orderedArray.removeAllSuchThat({

			arg item;

			var removeCondition;

			//If nil, remove entry anyway. Otherwise, look for the other cases.
			if(item == nil, {
				removeCondition = true;
			}, {
				removeCondition = (item.isMonitoring == false).and(item.outProxies.size == 0);
			});

			removeCondition;

		});

	}

	//Remove non-used entries and set their blockIndex back to -1
	sanitizeDict {

		if(this.orderedArray.size > 0, {
			this.dictOfProxies = this.dictOfProxies.select({
				arg proxy;
				var result;

				block ({
					arg break;

					this.orderedArray.do({
						arg proxyInArray;
						result = proxy == proxyInArray;

						//Break on true, otherwise keep searching.
						if(result, {
							break.(nil);
						});
					});
				});

				//Reset blockIndex too
				if(result.not, {
					("Removing proxy: " ++ proxy.asString ++ " from block number " ++ this.blockIndex).warn;
					proxy.blockIndex = -1;
				});

				result;

			});
		}, {

			//Ordered array has size 0. Free all

			this.dictOfProxies.do({
				arg proxy;
				proxy.blockIndex = -1;
			});

			this.dictOfProxies.clear;
		});

	}

	//Have something to automatically remove Proxies that haven't been touched from the dict
	rearrangeBlockLoop {
		arg proxy;

		if(proxy != nil, {

			var currentState;

			//If for any reason the proxy wasn't already in the dictOfProxies, add it
			this.addProxy(proxy, true);

			currentState = statesDict[proxy];

			//If this proxy has never been touched, avoids repetition
			if(currentState == false, {

				//("inProxies to " ++  proxy.asString ++ " : ").postln;

				proxy.inProxies.doProxiesLoop ({
					arg inProxy;

					//rearrangeInputs to this, this will add the inProxies
					this.rearrangeBlockLoop(inProxy);
				});

				//Add this
				this.orderedArray[runningIndex] = proxy;

				//Completed: put it to true so it's not added again
				statesDict[proxy] = true;

				//Advance counter
				this.runningIndex = this.runningIndex + 1;
			});
		});
	}

	findBottomMostOutProxiesAndInitStatesDict {
		this.bottomOutProxies.clear;
		this.statesDict.clear;

		//If only one proxy, just add that one.
		if(dictOfProxies.size == 1, {
			this.dictOfProxies.do({
				arg proxy;
				this.bottomOutProxies.put(proxy, proxy);
				this.statesDict.put(proxy, false);
			});
		}, {

			this.dictOfProxies.do({
				arg proxy;

				//Find the ones with no outProxies but at least one inProxy
				if((proxy.outProxies.size == 0).and(proxy.inProxies.size > 0), {
					this.bottomOutProxies.put(proxy, proxy);
				});

				//init statesDict for all proxies to false
				this.statesDict.put(proxy, false);

			});
		});
	}

}

//Have a global one, so that NodeProxies can be shared across VNdef, VNProxy and VPSpace...
AlgaBlocksDict {
	classvar< blocksDict;

	*initClass {
		blocksDict = Dictionary.new(50);
	}

	*reorderBlock {
		arg blockIndex, server;

		var entryInBlocksDict = blocksDict[blockIndex];

		if(entryInBlocksDict != nil, {

			//Make sure everything is synced with server!
			Routine.run({
				server.sync;
				entryInBlocksDict.rearrangeBlock(server);
			});
		});

	}

}

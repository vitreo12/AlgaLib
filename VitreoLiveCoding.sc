/*
THINGS TO DO:

1) Create all the interpolationProxies for every param AT VitreoNodeProxy instantiation (in the "put" function)

2) Supernova ParGroups used by default in Patterns?

3) Multiple servers connection.
   (boot servers with a lot of I/O and stream bettween them, with a block size difference OFC).
   (Also, servers must be linked in Jack)

X) Make "Restoring previous connections!" actually work

X) Make SURE that all connections work fine, ensuring that interpolationProxies are ALWAYS before the modulated
proxy and after the modulator. This gets screwed up with long chains.

X) When using clear / free, interpolationProxies should not fade

*/

//Just as TempoBusClock, but with slaves for multiple servers
VitreoTempoBusClock : TempoBusClock {

	//Slaves' tempo proxy functions
	var <>slavesControl;

	init {
		arg tempo, beats, seconds, queueSize;

		//Init clock, actually:
		super.init(tempo, beats, seconds, queueSize);

		//Init dictionary
		slavesControl = IdentityDictionary.new;

		^this;
	}

	//Called when changing tempo
	setTempoAtBeat { | newTempo, beats |
		slavesControl.do({
			arg slaveControl;

			if(slaveControl.numChannels != nil, {
				slaveControl.set(\fadeTime, 0.0, \tempo, newTempo);
			}, {
				//It's been deleted from its parent ProxySpace, remove it from array
				slavesControl.removeAt(slaveControl);
			});
		});

		control.set(\fadeTime, 0.0, \tempo, newTempo);

		^super.setTempoAtBeat(newTempo, beats)
	}

	//Called when changing tempo
	setTempoAtSec { | newTempo, secs |
		slavesControl.do({
			arg slaveControl;

			if(slaveControl.numChannels != nil, {
				slaveControl.set(\fadeTime, 0.0, \tempo, newTempo);
			}, {
				//It's been deleted from its parent ProxySpace, remove it from array
				slavesControl.removeAt(slaveControl);
			});
		});

		control.set(\fadeTime, 0.0, \tempo, newTempo);

		^super.setTempoAtSec(newTempo, secs)
	}
}

VitreoProxySpace : ProxySpace {

	/*

	makeProxy {

		//VitreoProxySpace's default is a one channel audio proxy
		var proxy = VitreoNodeProxy.new(server, \audio, 1);

		this.initProxy(proxy);

		//Change reshaping to be elastic by default
		proxy.reshaping = \elastic;

		^proxy
	}

	*/

	makeProxy {
		var proxy = VitreoNodeProxy.new(server);

		this.initProxy(proxy);

		//Change reshaping to be elastic by default
		proxy.reshaping = \elastic;

		^proxy
	}

	makeMasterClock { | tempo = 1.0, beats, seconds |
		var clock, proxy;
		proxy = envir[\tempo];
		if(proxy.isNil) { proxy = VitreoNodeProxy.control(server, 1); envir.put(\tempo, proxy); };
		proxy.fadeTime = 0.0;
		proxy.put(0, { |tempo = 1.0| tempo }, 0, [\tempo, tempo]);
		this.clock = VitreoTempoBusClock.new(proxy, tempo, beats, seconds).permanent_(true);
		if(quant.isNil) { this.quant = 1.0 };
	}

	makeSlaveClock { | masterProxySpace |
		var masterClock, proxy, tempo;

		if(masterProxySpace.class != VitreoProxySpace, {
			"A VitreoProxySpace is required as a master proxy space".warn;
			^nil;
		});

		masterClock = masterProxySpace.clock;

		if(masterClock.class != VitreoTempoBusClock, {
			"A VitreoProxySpace with a running VitreoTempoBusClock is required".warn;
			^nil;
		});

		tempo = masterClock.tempo;

		proxy = envir[\tempo];
		if(proxy.isNil) { proxy = VitreoNodeProxy.control(server, 1); envir.put(\tempo, proxy); };
		proxy.fadeTime = 0.0;
		proxy.put(0, { |tempo = 1.0| tempo }, 0, [\tempo, tempo]);

		//Add slave control to this ProxySpace's ~tempo proxy
		masterClock.slavesControl.put(proxy, proxy);

		//Set tempo and quant
		this.clock = masterClock;
		this.quant = masterProxySpace.quant;
	}

	clear { |fadeTime|
		//Call ProxySpace's clear
		super.clear;

		//Remove this VitreoProxySpace from Clock's slaves
		if(this.clock.class == VitreoTempoBusClock, {
			this.clock.slavesControl.removeAt(this.envir[\tempo]);
		});
	}

	ft_ { | dur |
		this.fadeTime_(dur);
	}

	ft {
		^this.fadeTime;
	}
}

//Alias
VPSpace : VitreoProxySpace {

}

VitreoProxyBlock {

	//the proxies for this block
	var <>dictOfProxies;

	//the ordered array of proxies for the block
	var <>orderedArray;

	//A dict storing proxy -> (true or false) to state if all inputs have been checked or not
	var <>statesDict;

	//Counter for correct ordering of entries in orderedArray
	var <>runningIndex;

	//bottom most and top most proxies in this block
	var <>bottomOutProxies, <>topInProxies;

	var <>changed = false;

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
		arg proxy;

		this.dictOfProxies.put(proxy, proxy);

		this.changed = true;
	}

	removeProxy {
		arg proxy;

		var oldBlockIndex = proxy.blockIndex;

		this.dictOfProxies.removeAt(proxy);

		//Remove this block from dict if it's empty!
		if(this.dictOfProxies.size == 0, {
			VitreoBlocksDict.blocksDict.removeAt(oldBlockIndex);
		});

		this.changed = true;
	}

	rearrangeBlock {
		arg server;

		//Only rearrangeBlock when new connections have been done... It should check for inner connections,
		//not general connections though... It should be done from NodeProxy's side.
		if(this.changed == true, {

			//ordered collection
			this.orderedArray = Array.newClear(dictOfProxies.size);

			("Reordering proxies for block number " ++ this.blockIndex).warn;

			//this.orderedArray.size.postln;

			//Find the proxies with no outProxies (so, the last ones in the chain!),
			//and init the statesDict
			this.findBottomMostOutProxiesAndInitStatesDict;

			//"Block's bottomOutProxies: ".postln;
			//this.bottomOutProxies.postln;

			//"Block's statesDict: ".postln;
			//this.statesDict.postln;

			//init runningIndex
			this.runningIndex = 0;

			this.bottomOutProxies.do({
				arg proxy;

				this.rearrangeBlockLoop(proxy); //start from index 0
			});

			//"Block's orderedArray: ".postln;
			//this.orderedArray.postln;

			//Actual ordering of groups. Need to be s.bind so that concurrent operations are synced together!
			//Routine.run({

			//server.sync;

			//server.bind allows here to be sure that this bundle will be sent in any case after
			//the NodeProxy creation bundle for interpolation proxies.
			server.bind({

				var sizeMinusOne = orderedArray.size - 1;
				var firstProxy = orderedArray[0];

				//Must loop reverse to correct order stuff
				sizeMinusOne.reverseDo({
					arg counts;

					var count = counts + 1;

					var thisEntry = orderedArray[count];
					var prevEntry = orderedArray[count - 1];

					//(prevEntry.asString ++ " before " ++ thisEntry.asString).postln;

					//thisEntry.class.postln;
					//prevEntry.class.postln;

					prevEntry.beforeMoveNextInterpProxies(thisEntry);
				});

				//Also move first one (so that its interpolationProxies are correct)
				firstProxy.before(firstProxy);

			});

			//REVIEW THIS:
			this.changed = false;

			//}, 1024);

		});

	}

	rearrangeBlockLoop {
		arg proxy;

		var currentState = statesDict[proxy];

		//If this proxy has never been touched, avoids repetition
		if(currentState == false, {

			//("inProxies to " ++  proxy.asString ++ " : ").postln;

			proxy.inProxies.doProxiesLoop ({
				arg inProxy;

				//inProxy.class.postln;

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
	}

	findBottomMostOutProxiesAndInitStatesDict {
		this.bottomOutProxies.clear;
		this.statesDict.clear;

		this.dictOfProxies.do({
			arg proxy;

			//Find the ones with no outProxies
			if(proxy.outProxies.size == 0, {
				this.bottomOutProxies.put(proxy, proxy);
			});

			//reset statesDict to false
			this.statesDict.put(proxy, false);

		});
	}

}

//Have a global one, so that NodeProxies can be shared across Ndef, NodeProxy and ProxySpace...
VitreoBlocksDict {
	classvar< blocksDict;

	*initClass {
		blocksDict = Dictionary.new(50);
	}

}

VitreoNodeProxy : NodeProxy {
	classvar <>defaultAddAction=\addToTail;

	//The actual dict of all blocks, keys are generated with UniqueID.next
	//classvar <>VitreoBlocksDict.blocksDict;

	//The block index that contains this proxy
	var <>blockIndex = -1;

	var <>interpolationProxies, <>interpolationProxiesCopy, <>defaultParamsVals, <>inProxies, <>outProxies;

	//Add the SynthDef for ins creation at startup!
	*initClass {
		StartUp.add({
			SynthDef(\proxyIn_ar, {

				/*

				This envelope takes care for all fadeTime related stuff. Check GraphBuilder.sc.
				Also check ProxySynthDef.sc, where the *new method is used to create the new
				SynthDef that defines a NodeProxy's output when using a Function as source.
				In ProxySynthDef.sc, this is how the fadeTime envelope is generated:

				    envgen = if(makeFadeEnv) {
				        EnvGate(i_level: 0, doneAction:2, curve: if(rate === 'audio') { 'sin' } { 'lin' })
				    } { 1.0 };

				\sin is used for \audio, \lin for \control.

				ProxySynthDef.sc also checks if there are gate and out arguments, in order
				to trigger releases and stuff.

				*/

				var fadeTimeEnv = EnvGate.new(i_level: 0, doneAction:2, curve: 'sin');
				Out.ar(\out.ir(0), \in.ar(0) * fadeTimeEnv);
			}).add;

			SynthDef(\proxyIn_kr, {
				var fadeTimeEnv = EnvGate.new(i_level: 0, doneAction:2, curve: 'lin');
				Out.kr(\out.ir(0), \in.kr(0) * fadeTimeEnv);
			}).add;
		});

		//Keys are unique, generated with UniqueID.next
		//VitreoBlocksDict.blocksDict = Dictionary.new;
	}

	init {
		nodeMap = ProxyNodeMap.new;
		objects = Order.new;

		//These will be in the form of: \param -> NodeProxy

		//These are the interpolated ones!!
		interpolationProxies = IdentityDictionary.new;

		//These are used for <| (unmap) to restore default values
		defaultParamsVals = IdentityDictionary.new;

		//General I/O
		inProxies  = IdentityDictionary.new(20);
		outProxies = IdentityDictionary.new(20);

		loaded = false;
		reshaping = defaultReshaping;
		this.linkNodeMap;
	}

	clear { | fadeTime = 0, isInterpolationProxy = false |
		//copy interpolationProxies in new IdentityDictionary in order to free them only
		//after everything has been freed already.
		//Also, remove block from VitreoBlocksDict.blocksDict
		if(isInterpolationProxy.not, {
			var blockWithThisProxy;

			interpolationProxies.postln;

			interpolationProxiesCopy = interpolationProxies.copy;

			//remove from block in VitreoBlocksDict.blocksDict
			blockWithThisProxy = VitreoBlocksDict.blocksDict[this.blockIndex];

			if(blockWithThisProxy != nil, {
				blockWithThisProxy.removeProxy(this);
			});
		});

		//This will run through before anything.. that's why the copies
		this.free(fadeTime, true, isInterpolationProxy); 	// free group and objects

		//Remove all connected inProxies
		inProxies.keysValuesDo({
			arg param, proxy;

			if(proxy.class != Array, {
				//Remove the outProxy entry in the connected proxies
				proxy.outProxies.removeAt(param);
			}, {
				//Function, Binops, Arrays
				proxy.do({
					arg proxyInArray;
					proxyInArray.outProxies.removeAt(param);
				})
			});


		});

		//Remove all connected outProxies
		outProxies.keysValuesDo({
			arg param, proxy;

			//Remove the inProxy entry in the connected proxies
			proxy.inProxies.removeAt(param);
		});

		//Remove all NodeProxies used for param interpolation!!
		//(interpolationProxies don't have other interpolation proxies, don't need to run this:)
		if(isInterpolationProxy.not, {

			if(fadeTime == nil, {fadeTime = 0});

			Routine.run({

				(fadeTime + 0.001).wait;

				//"Clearing interp proxies".postln;

				interpolationProxiesCopy.postln;

				interpolationProxiesCopy.do({
					arg proxy;
					proxy.clear(0, true, true);
				});

				//Only clear at the end of routine
				interpolationProxiesCopy.clear; interpolationProxiesCopy = nil;

			});
		});

		this.removeAll; 			// take out all objects

		children = nil;             // for now: also remove children

		this.stop(fadeTime, true);		// stop any monitor

		monitor = nil;

		this.fadeTime = fadeTime; // set the fadeTime one last time for freeBus
		this.freeBus;	 // free the bus from the server allocator

		//Reset
		inProxies.clear; inProxies  = nil;
		outProxies.clear; outProxies = nil;
		defaultParamsVals.clear; defaultParamsVals = nil;

		this.blockIndex = -1;

		this.init;	// reset the environment
		this.changed(\clear, [fadeTime]);
	}

	free { | fadeTime = 0, freeGroup = true, isInterpolationProxy = false |
		var bundle, freetime;
		var oldGroup = group;
		if(this.isPlaying) {
			bundle = MixedBundle.new;
			if(fadeTime.notNil) {
				bundle.add([15, group.nodeID, "fadeTime", fadeTime]) // n_set
			};
			this.stopAllToBundle(bundle, fadeTime);
			if(freeGroup) {
				oldGroup = group;
				group = nil;
				freetime = (fadeTime ? this.fadeTime) + (server.latency ? 0) + 1e-9; // delay a tiny little
				server.sendBundle(freetime, [11, oldGroup.nodeID]); // n_free
			};
			bundle.send(server);
			this.changed(\free, [fadeTime, freeGroup]);
		};

		//interpolationProxies don't have other interpolationProxies, no need to run this.
		if(isInterpolationProxy.not, {

			//If just running free without clear, this hasn't been copied over
			if(interpolationProxiesCopy.size != interpolationProxies.size, {
				interpolationProxiesCopy = interpolationProxies.copy;
			});

			if(fadeTime == nil, {fadeTime = 0});

			Routine.run({
				(fadeTime + 0.001).wait;

				//"Freeing interp proxies".postln;

				interpolationProxiesCopy.do({
					arg proxy;
					proxy.free(0, freeGroup, true);
				});
			});
		});

	}

	fadeTime_ { | dur |
		if(dur.isNil) { this.unset(\fadeTime) } { this.set(\fadeTime, dur) };

		//fadeTime_ also applies to interpolated input proxies...
		//This should only be set for ProxySpace stuff, not in general to be honest...
		interpolationProxies.do({
			arg proxy;
			proxy.fadeTime = dur;
		});
	}

	ft_ { | dur |
		this.fadeTime_(dur);
	}

	ft {
		^this.fadeTime
	}

	params {
		^this.interpolationProxies;
	}

	//When a new object is assigned to a VitreoNodeProxy!
	put { | index, obj, channelOffset = 0, extraArgs, now = true |
		var container, bundle, oldBus = bus;

		var entryInBlocksDict;
		var isObjAFunction;
		var isObjAnOp;
		var isObjAnArray;

		if(obj.isNil) { this.removeAt(index); ^this };
		if(index.isSequenceableCollection) {
			^this.putAll(obj.asArray, index, channelOffset)
		};

		bundle = MixedBundle.new;
		container = obj.makeProxyControl(channelOffset, this);
		container.build(this, index ? 0); // bus allocation happens here


		if(this.shouldAddObject(container, index)) {
			// server sync happens here if necessary
			if(server.serverRunning) { container.loadToBundle(bundle, server) } { loaded = false; };
			this.prepareOtherObjects(bundle, index, oldBus.notNil and: { oldBus !== bus });
		} {
			format("failed to add % to node proxy: %", obj, this).postln;
			^this
		};

		this.putNewObject(bundle, index, container, extraArgs, now);
		this.changed(\source, [obj, index, channelOffset, extraArgs, now]);

		//Different cases!

		//Function:
		//~c = {~a * 0.5}, ensuring ~a is before ~c
		isObjAFunction = obj.class == Function;

		//Binary/Unary ops:
		//~c = ~a * 0.5, ensuring ~a is before ~c
		isObjAnOp = obj.class.superclass == AbstractOpPlug;

		//Array:
		//~c = [~a, ~b], ensuring ~a and ~b are before ~c
		isObjAnArray = obj.class == Array;

		//Free previous entries in the indices slots
		if(index == nil, {

			//Free all previous connected proxies, if there were any...
			this.inProxies.keysValuesDo({
				arg param, proxy;

				//This will consider all indices.
				if(param.asString.beginsWith("___SPECIAL_ASSIGNMENT___"), {

					//proxy is going to be an array
					proxy.do({
						arg proxyArrayEntry;
						proxyArrayEntry.outProxies.removeAt(this);
					});

					this.inProxies.removeAt(param);
				});
			});

		}, {

			//Free previous connected proxy at index
			this.inProxies.keysValuesDo({
				arg param, proxy;

				//This will consider the correct iindex
				if(param == (\___SPECIAL_ASSIGNMENT___ ++ index.asSymbol), {

					//proxy is going to be an array
					proxy.do({
						arg proxyArrayEntry;
						proxyArrayEntry.outProxies.removeAt(this);
					});

					this.inProxies.removeAt(param);

				});
			});

		});

		if((isObjAFunction).or(isObjAnOp).or(isObjAnArray), {

			//Special overloaded function for Function, AbstractOpPlug and Array
			//which takes care of proper ordering the proxies
			obj.putObjBefore(this, index);

		});

		////////////////////////////////////////////////////////////////

		//REARRANGE BLOCK!!

		entryInBlocksDict = VitreoBlocksDict.blocksDict[this.blockIndex];

		entryInBlocksDict.postln;

		"MDMDMDMMD".postln;

		if(entryInBlocksDict != nil, {
			entryInBlocksDict.rearrangeBlock(server);
		});

		//////////////////////////////////////////////////////////////
	}

	//Start group if necessary. Here is the defaultAddAction at work.
	//This function is called in put -> putNewObject
	prepareToBundle { arg argGroup, bundle, addAction = defaultAddAction;
		if(this.isPlaying.not) {
			group = Group.basicNew(server, this.defaultGroupID);
			NodeWatcher.register(group);
			group.isPlaying = server.serverRunning;
			if(argGroup.isNil and: { parentGroup.isPlaying }) { argGroup = parentGroup };
			bundle.addPrepare(group.newMsg(argGroup ?? { server.asGroup }, addAction));
		}
	}

	//These are straight up copied from BusPlug. Overwriting to retain group ordering stuff
	play { | out, numChannels, group, multi=false, vol, fadeTime, addAction |
		var entryInBlocksDict;
		var bundle = MixedBundle.new;
		if(this.homeServer.serverRunning.not) {
			("server not running:" + this.homeServer).warn;
			^this
		};
		if(bus.rate == \control) { "Can't monitor a control rate bus.".warn; monitor.stop; ^this };
		group = group ?? {this.homeServer.defaultGroup};
		this.playToBundle(bundle, out, numChannels, group, multi, vol, fadeTime, addAction);
		// homeServer: multi client support: monitor only locally
		bundle.schedSend(this.homeServer, this.clock ? TempoClock.default, this.quant);

		////////////////////////////////////////////////////////////////

		//REARRANGE BLOCK!

		entryInBlocksDict = VitreoBlocksDict.blocksDict[this.blockIndex];
		if(entryInBlocksDict != nil, {
			entryInBlocksDict.rearrangeBlock(server);
		});

		////////////////////////////////////////////////////////////////

		this.changed(\play, [out, numChannels, group, multi, vol, fadeTime, addAction]);
	}

	playN { | outs, amps, ins, vol, fadeTime, group, addAction |
		var entryInBlocksDict;
		var bundle = MixedBundle.new;
		if(this.homeServer.serverRunning.not) {
			("server not running:" + this.homeServer).warn;
			^this
		};
		if(bus.rate == \control) { "Can't monitor a control rate bus.".warn; monitor.stop; ^this };
		group = group ?? {this.homeServer.defaultGroup};
		this.playNToBundle(bundle, outs, amps, ins, vol, fadeTime, group, addAction);
		bundle.schedSend(this.homeServer, this.clock ? TempoClock.default, this.quant);

		////////////////////////////////////////////////////////////////

		//REARRANGE BLOCK!

		entryInBlocksDict = VitreoBlocksDict.blocksDict[this.blockIndex];
		if(entryInBlocksDict != nil, {
			entryInBlocksDict.rearrangeBlock(server);
		});

		////////////////////////////////////////////////////////////////

		this.changed(\playN, [outs, amps, ins, vol, fadeTime, group, addAction]);
	}

	//Same as <<> but uses .xset instead of .xmap.
	connectXSet { | proxy, key = \in |
		var ctl, rate, numChannels, canBeMapped;
		if(proxy.isNil) { ^this.unmap(key) };
		ctl = this.controlNames.detect { |x| x.name == key };
		rate = ctl.rate ?? {
			if(proxy.isNeutral) {
				if(this.isNeutral) { \audio } { this.rate }
			} {
				proxy.rate
			}
		};
		numChannels = ctl !? { ctl.defaultValue.asArray.size };
		canBeMapped = proxy.initBus(rate, numChannels); // warning: proxy should still have a fixed bus
		if(canBeMapped) {
			if(this.isNeutral) { this.defineBus(rate, numChannels) };
			this.xset(key, proxy);
		} {
			"Could not link node proxies, no matching input found.".warn
		};
		^proxy // returns first argument for further chaining
	}

	//Same as <<> but uses .set instead of .xmap.
	connectSet { | proxy, key = \in |
		var ctl, rate, numChannels, canBeMapped;
		if(proxy.isNil) { ^this.unmap(key) };
		ctl = this.controlNames.detect { |x| x.name == key };
		rate = ctl.rate ?? {
			if(proxy.isNeutral) {
				if(this.isNeutral) { \audio } { this.rate }
			} {
				proxy.rate
			}
		};
		numChannels = ctl !? { ctl.defaultValue.asArray.size };
		canBeMapped = proxy.initBus(rate, numChannels); // warning: proxy should still have a fixed bus
		if(canBeMapped) {
			if(this.isNeutral) { this.defineBus(rate, numChannels) };
			this.set(key, proxy);
		} {
			"Could not link node proxies, no matching input found.".warn
		};
		^proxy // returns first argument for further chaining
	}

	createNewInterpProxyIfNeeded {
		arg param = \in, src = nil;

		var interpolationProxyEntry = this.interpolationProxies[param];

		//Returns nil with a Pbind.. this could be problematic for connections, rework it!
		var paramRate = (this.controlNames.detect{ |x| x.name == param }).rate;

		if(interpolationProxyEntry == nil, {
			var interpolationProxy;

			//Get the original default value, used to restore things when unmapping ( <| )
			block ({
				arg break;
				this.getKeysValues.do({
					arg paramAndValPair;
					if(paramAndValPair[0] == param, {
						this.defaultParamsVals.put(param, paramAndValPair[1]);
						break.(nil);
					});
				});
			});

			//Pass in something as src (used for Function, Binops, Array, etc..)
			if(src != nil, {
				//Doesn't work with Pbinds, would just create a kr version
				if(paramRate == \audio, {
					interpolationProxy = VitreoNodeProxy.new(server, \audio, 1).source   = src;
				}, {
					interpolationProxy = VitreoNodeProxy.new(server, \control, 1).source = src;
				});

			}, {
				//Doesn't work with Pbinds, would just create a kr version
				if(paramRate == \audio, {
					interpolationProxy = VitreoNodeProxy.new(server, \audio, 1).source   = \proxyIn_ar;
				}, {
					interpolationProxy = VitreoNodeProxy.new(server, \control, 1).source = \proxyIn_kr;
				});

			});

			//Default fadeTime: use nextProxy's (the modulated one) fadeTime
			interpolationProxy.fadeTime = this.fadeTime;

			//Add the new interpolation NodeProxy to interpolationProxies dict
			this.interpolationProxies.put(param, interpolationProxy);

			//Make connection from the interpolation proxy..
			//This connection is quite useless, as interpolationProxy already belongs to this proxy... it could easily be removed.
			interpolationProxy.outProxies.put(param, this);

			//Returns true if has just been created
			^true;

		}, {

			//Just switch the function
			if(src != nil, {
				interpolationProxyEntry.source = src;
			});

			//Return false if the interp proxy was already in place
			^false;
		});
	}

	//Combines before with <<>
	=> {
		arg nextProxy, param = \in;

		var isNextProxyAProxy, isThisProxyAnOp, isThisProxyAFunc, isThisProxyAnArray,
		interpolationProxyEntry, thisParamEntryInNextProxy, paramRate;

		var thisBlockIndex;
		var nextProxyBlockIndex;

		var isThisProxyInstantiated = true;
		var isNextProxyInstantiated = true;

		isNextProxyAProxy = (nextProxy.class == VitreoNodeProxy).or(nextProxy.class.superclass == VitreoNodeProxy).or(nextProxy.class.superclass.superclass == VitreoNodeProxy);

		if((isNextProxyAProxy.not), {
			"nextProxy is not a valid VitreoNodeProxy!!!".warn;
			^this;
		});

		if(this.server != nextProxy.server, {
			"nextProxy is on a different server!!!".warn;
			^this;
		});

		if(this.group == nil, {
			("This proxy hasn't been instantiated yet!!!").warn;
			isThisProxyInstantiated = false;

			//^this;
		});

		if(nextProxy.group == nil, {
			("nextProxy hasn't been instantiated yet!!!").warn;
			isNextProxyInstantiated = false;

			//^this;
		});

		//Different cases:
		//Binary / Unary operators:
		//~b = ~a * 0.1
		//~b => ~c
		isThisProxyAnOp = (this.source.class.superclass == AbstractOpPlug);
		if(isThisProxyAnOp, {

			//Run the function from the overloaded functions in ClassExtensions.sc
			^this.source.perform('=>', nextProxy, param);
		});

		//DON'T RUN Function's as this.source will always be a function anyway.
		//It would overwrite standard casese like:
		//~saw = {Saw.ar(\f.kr(100))}
		//~lfo = {SinOsc.kr(1).range(1, 10)}
		//~lfo =>.f ~saw
		//~lfo.source here is a function!! I don't want to overwrite that

		//Array:
		//~a = [~lfo1, ~lfo2]
		//~a => b
		isThisProxyAnArray = (this.source.class == Array);
		if(isThisProxyAnArray, {

			//Run the function from the overloaded functions in ClassExtensions.sc
			^this.source.perform('=>', nextProxy, param);
		});


		//Retrieve if a connection was already created a first time
		interpolationProxyEntry = nextProxy.interpolationProxies[param];

		//This is the connection that is in place with the interpolation NodeProxy.
		thisParamEntryInNextProxy = nextProxy.inProxies[param];

		//Free previous connections to the nextProxy, if there were any
		nextProxy.freePreviousConnection(param);

		//Returns nil with a Pbind.. this could be problematic for connections, rework it!
		paramRate = (nextProxy.controlNames.detect{ |x| x.name == param }).rate;

		//Create a new block if needed
		this.createNewBlockIfNeeded(nextProxy);

		//If connecting to the param for the first time, create a new NodeProxy
		//to be used for interpolated connections, and store it in the outProxies for this, and
		//into nextProxy.inProxies.
		if(interpolationProxyEntry == nil, {
			var interpolationProxy;

			//Get the original default value, used to restore things when unmapping ( <| )
			block ({
				arg break;
				nextProxy.getKeysValues.do({
					arg paramAndValPair;
					if(paramAndValPair[0] == param, {
						nextProxy.defaultParamsVals.put(param, paramAndValPair[1]);
						break.(nil);
					});
				});
			});

			//nextProxy.defaultParamsVals[param].postln;

			//Doesn't work with Pbinds, would just create a kr version
			if(paramRate == \audio, {
				interpolationProxy = VitreoNodeProxy.new(server, \audio, 1).source   = \proxyIn_ar;
			}, {
				interpolationProxy = VitreoNodeProxy.new(server, \control, 1).source = \proxyIn_kr;
			});

			//This needs to be forked for the .before stuff to work properly
			//Routine.run({

			//Need to wait for the NodeProxy's group to be instantiated on the server.
			//server.sync;

			//Default fadeTime: use nextProxy's (the modulated one) fadeTime
			interpolationProxy.fadeTime = nextProxy.fadeTime;

			//Add the new interpolation NodeProxy to interpolationProxies dict
			nextProxy.interpolationProxies.put(param, interpolationProxy);

			//These are the actual connections that take place, excluding interpolationProxy
			nextProxy.inProxies.put(param, this);              //modulated

			//Don't use param indexing for outs, as this proxy could be linked
			//to multiple proxies with same param names
			outProxies.put(nextProxy, nextProxy);           //modulator

			//Also add connections for interpolationProxy
			interpolationProxy.inProxies.put(\in, this);
			interpolationProxy.outProxies.put(param, nextProxy);

			//Only rearrange block if both proxies are actually instantiated.
			if(isThisProxyInstantiated.and(isNextProxyInstantiated), {
				VitreoBlocksDict.blocksDict[this.blockIndex].rearrangeBlock(server);
			});

			//Connections:
			//Without fade: with the modulation proxy at the "\in" param
			interpolationProxy.connectSet(this, \in);

			//With fade: with modulated proxy at the specified param
			nextProxy.connectXSet(interpolationProxy, param);

			//});

		}, {

			//Only reconnect entries if a different NodeProxy is used for this entry.
			if(thisParamEntryInNextProxy != this, {

				var interpolationProxySource = interpolationProxyEntry.source;

				//Remake connections
				nextProxy.inProxies.put(param, this);

				//Don't use param indexing for outs, as this proxy could be linked
				//to multiple proxies with same param names
				outProxies.put(nextProxy, nextProxy);

				//re-instantiate source if it's not correct, could have been modified by Binops, Function, array
				if((interpolationProxySource != \proxyIn_ar).or(interpolationProxySource != \proxyIn_kr), {
					if(paramRate == \audio, {
						interpolationProxyEntry.source = \proxyIn_ar;
					}, {
						interpolationProxyEntry.source = \proxyIn_kr;
					});
				});


				//interpolationProxyEntry.outProxies remains the same!
				interpolationProxyEntry.inProxies.put(\in, this);

				//Only rearrange block if both proxies are actually instantiated.
				if(isThisProxyInstantiated.and(isNextProxyInstantiated), {
					VitreoBlocksDict.blocksDict[this.blockIndex].rearrangeBlock(server);
				});

				//Switch connections just for interpolationProxy. nextProxy is already connected to
				//interpolationProxy
				interpolationProxyEntry.connectXSet(this, \in);
			});

		});

		//return nextProxy for further chaining
		^nextProxy;
	}

	//combines before (on nextProxy) with <>>
	//It also allows to set to plain numbers, e.g. ~sine <=.freq 440

	<= {
		arg nextProxy, param = \in;

		var isNextProxyAProxy, isNextProxyAnOp, isNextProxyAFunc, isNextProxyAnArray, paramRate;

		/* Overloaded calls for AbstractOpPlug, Function and Array */

		//Binary or Unary ops, e.g. ~b <= (~a * 0.5)
		isNextProxyAnOp = nextProxy.class.superclass == AbstractOpPlug;
		if(isNextProxyAnOp, {

			//Run the function from the overloaded functions in ClassExtensions.sc
			^nextProxy.perform('=>', this, param);
		});

		//Function, e.g. ~b <= {~a * 0.5}
		isNextProxyAFunc = nextProxy.class == Function;
		if(isNextProxyAFunc, {

			//Run the function from the overloaded functions in ClassExtensions.sc
			^nextProxy.source.perform('=>', this, param);
		});

		//Array, e.g. ~a <=.freq [~lfo1, ~lfo2]
		isNextProxyAnArray = nextProxy.class == Array;
		if(isNextProxyAnArray, {

			//Run the function from the overloaded functions in ClassExtensions.sc
			^nextProxy.perform('=>', this, param);
		});

		isNextProxyAProxy = (nextProxy.class == VitreoNodeProxy).or(nextProxy.class.superclass == VitreoNodeProxy).or(nextProxy.class.superclass.superclass == VitreoNodeProxy);

		/*
		What if interpolationProxies to set are an array ???
		e.g.: ~sines <=.freq [~lfo1, ~lfo2]
		*/

		//Standard case with another NodeProxy
		if(isNextProxyAProxy, {

			//If next proxy is an AbstractOpPlug or Function, check ClassExtensions.sc
			nextProxy.perform('=>', this, param);

			//Return nextProxy for further chaining
			^nextProxy;

		}, {

			//This is the case for single values stuff, e.g. ~sine <=.freq 440

			var nextObj, interpolationProxyEntry, isThisProxyInstantiated = true;

			nextObj = nextProxy;

			//Free previous connections
			this.freePreviousConnection(param);

			//Retrieve if there was already a interpolationProxy going on
			interpolationProxyEntry = interpolationProxies[param];

			if(this.group == nil, {
				"this proxy hasn't been instantiated yet".warn;
				isThisProxyInstantiated = false;
			});

			//if there was not
			if(interpolationProxyEntry == nil, {

				//Create it anew
				var interpolationProxy, paramRate;

				//Create block if needed
				this.createNewBlockIfNeeded(this);

				//Get the original default value, used to restore things when unmapping ( <| )
				block ({
					arg break;
					this.getKeysValues.do({
						arg paramAndValPair;
						if(paramAndValPair[0] == param, {
							defaultParamsVals.put(param, paramAndValPair[1]);
							break.(nil);
						});
					});
				});

				//Doesn't work with Pbinds, would just create a kr version
				if(paramRate == \audio, {
					interpolationProxy = VitreoNodeProxy.new(server, \audio, 1).source   = \proxyIn_ar;
				}, {
					interpolationProxy = VitreoNodeProxy.new(server, \control, 1).source = \proxyIn_kr;
				});

				//This needs to be forked for the .before stuff to work properly
				//Routine.run({

				//Need to wait for the NodeProxy's group to be instantiated on the server.
				//server.sync;

				//Default fadeTime: use nextObj's (the modulated one) fadeTime
				interpolationProxy.fadeTime = this.fadeTime;

				//Add the new interpolation NodeProxy to interpolationProxies dict
				this.interpolationProxies.put(param, interpolationProxy);

				//Also add connections for interpolationProxy
				interpolationProxy.outProxies.put(param, this);

				//Only rearrange block if both proxies are actually instantiated.
				if(isThisProxyInstantiated, {
					VitreoBlocksDict.blocksDict[this.blockIndex].rearrangeBlock(server);
				});

				//With fade: with modulated proxy at the specified param
				this.connectXSet(interpolationProxy, param);
				//});

			}, {

				var interpolationProxySource = interpolationProxyEntry.source;

				//re-instantiate source if it's not correct, could have been modified by Binops, Function, array
				if((interpolationProxySource != \proxyIn_ar).or(interpolationProxySource != \proxyIn_kr), {
					if(paramRate == \audio, {
						interpolationProxyEntry.source = \proxyIn_ar;
					}, {
						interpolationProxyEntry.source = \proxyIn_kr;
					});
				});

				//Disconnect input to interpolation proxy...
				//The outProxies of the previous NodeProxy have already been cleared
				interpolationProxyEntry.inProxies.clear;

				//Simply XSet the new number in with the interpolation
				interpolationProxyEntry.connectXSet(nextObj, \in);

				/* REARRANGE HERE??? */

			});

			//return this for further chaining
			^this;

		});
	}

	//Unmap
	<| {
		arg param = \in;

		var defaultValue = defaultParamsVals[param];

		if(defaultValue == nil, {
			"Trying to restore a nil value".warn;
		}, {
			("Restoring default value for " ++ param ++ " : " ++ defaultValue).postln;

			//Simply restore the default original value using the <= operator
			this.perform('<=', defaultValue, param);
		});

		^this;
	}

	nextProxyIsAnOp {
		arg op;

		^this;
	}

	freeAllInProxiesConnections {
		//Remove all relative outProxies
		inProxies.keysValuesDo({
			arg param, proxy;

			if(proxy.class != Array, {
				//Remove the outProxy entry in the connected proxies
				proxy.outProxies.removeAt(param);
			}, {
				//Function, Binops, Arrays
				proxy.do({
					arg proxyInArray;
					proxyInArray.outProxies.removeAt(param);
				})
			});
		});

		inProxies.clear;
	}

	freeAllOutProxiesConnections {
		//Remove all relative inProxies
		outProxies.keysValuesDo({
			arg param, proxy;

			//Remove the inProxy entry in the connected proxies
			proxy.inProxies.removeAt(param);
		});

		outProxies.clear;
	}

	freePreviousConnection {
		arg param = \in;

		//First, empty the connections that were on before (if there were any)
		var previousEntry = this.inProxies[param];

		var isPreviousEntryAProxy = (previousEntry.class == VitreoNodeProxy).or(previousEntry.class.superclass == VitreoNodeProxy).or(previousEntry.class.superclass.superclass == VitreoNodeProxy);

		if(isPreviousEntryAProxy, {
			//Remove connection in previousEntry's outProxies to this one
			previousEntry.removeOutProxy(this);
		}, {

			//Array is used to store connections for Function, AbstractOpPlug and Array, since multiple NodeProxies might be connected to the same param.
			var isPreviousEntryAnArray = previousEntry.class == Array;

			if(isPreviousEntryAnArray, {
				previousEntry.do({
					arg previousProxyEntry;
					previousProxyEntry.removeOutProxy(this);
				});
			});
		});

		//Remove this' connection to previousEntry
		this.inProxies.removeAt(param);

		/*
		//First, empty the connections that were on before (if there were any)
		var previousEntry = this.inProxies[param];



		//("prev entry " ++ previousEntry.asString).postln;

		if(isPreviousEntryAProxy, {

			/*

			//Remove older connection to this.. outProxies are stored with proxy -> proxy, not param -> proxy
			previousEntry.outProxies.removeAt(this);

			//Also reset block index if needed, if its outProxies have size 0
			if((previousEntry.outProxies.size == 0).and(previousEntry.inProxies.size == 0), {
				previousEntry.blockIndex = -1;
			});

			*/

			previousEntry.removeOutProxy(this, param);

			//Remove connection to old one
			this.inProxies.removeAt(param);

		}, {

			//Array is used to store connections for Function, AbstractOpPlug and Array, since multiple NodeProxies might be connected to the same param.
			var isPreviousEntryAnArray = previousEntry.class == Array;

			if(isPreviousEntryAnArray, {

				previousEntry.do({
					arg previousProxyEntry;

					("GOT ARRAY ENTRY").postln;

				});

			});


		});
		*/
	}

	removeOutProxy {
		arg proxyToRemove;

		var isThisConnectedToAnotherParam = false;

		//First, check if the this was perhaps connected to another param of the other proxy
		block ({
			arg break;
			proxyToRemove.inProxies.doProxiesLoop({
				arg inProxy;
				if(inProxy == this, { isThisConnectedToAnotherParam = true; break.(nil); });
			});
		});

		if(isThisConnectedToAnotherParam == false, {
			//Remove older connection to this only if it's not connected to
			//any other param of this proxy..
			//Remember that outProxies are stored with proxy -> proxy, not param -> proxy
			this.outProxies.removeAt(proxyToRemove);
		}, );

		//Also reset block index if needed, if its outProxies have size 0
		if((this.outProxies.size == 0).and(this.inProxies.size == 0), {
			this.blockIndex = -1;
		});
	}

	createNewBlockIfNeeded {
		arg nextProxy;

		var newBlockIndex;
		var newBlock;

		var thisBlockIndex = this.blockIndex;
		var nextProxyBlockIndex = nextProxy.blockIndex;

		//Create new block if both connections didn't have any
		if((thisBlockIndex == -1).and(nextProxyBlockIndex == -1), {
			newBlockIndex = UniqueID.next;
			newBlock = VitreoProxyBlock.new(newBlockIndex);

			"new block".postln;

			this.blockIndex = newBlockIndex;
			nextProxy.blockIndex = newBlockIndex;

			//Add block to blocksDict
			VitreoBlocksDict.blocksDict.put(newBlockIndex, newBlock);

			//Add proxies to the block
			VitreoBlocksDict.blocksDict[newBlockIndex].addProxy(this);
			VitreoBlocksDict.blocksDict[newBlockIndex].addProxy(nextProxy);

		}, {

			//If they are not already in same block
			if(thisBlockIndex != nextProxyBlockIndex, {

				//Else, add this proxy to nextProxy's block
				if(thisBlockIndex == -1, {
					"add this to nextProxy's block".postln;
					this.blockIndex = nextProxyBlockIndex;

					//Add proxy to the block
					VitreoBlocksDict.blocksDict[nextProxyBlockIndex].addProxy(this);

					//This is for the changed at the end of function...
					newBlockIndex = nextProxyBlockIndex;
				}, {

					//Else, add nextProxy to this block
					if(nextProxyBlockIndex == -1, {
						"add nextProxy to this' block".postln;
						nextProxy.blockIndex = thisBlockIndex;

						//Add proxy to the block
						VitreoBlocksDict.blocksDict[thisBlockIndex].addProxy(nextProxy);

						//This is for the changed at the end of function...
						newBlockIndex = thisBlockIndex;
					});
				});
			});
		});

		//If both are already into blocks and the block is different, the two blocks should merge into a new one!
		if((thisBlockIndex != nextProxyBlockIndex).and((thisBlockIndex != -1).and(nextProxyBlockIndex != -1)), {

			newBlockIndex = UniqueID.next;
			newBlock = VitreoProxyBlock.new(newBlockIndex);

			"both already into blocks. creating new".postln;

			//Change all proxies' group to the new one and add then to new block
			VitreoBlocksDict.blocksDict[thisBlockIndex].dictOfProxies.do({
				arg proxy;
				proxy.blockIndex = newBlockIndex;

				newBlock.addProxy(proxy);
			});

			VitreoBlocksDict.blocksDict[nextProxyBlockIndex].dictOfProxies.do({
				arg proxy;
				proxy.blockIndex = newBlockIndex;

				newBlock.addProxy(proxy);
			});

			//Remove previous' groups
			VitreoBlocksDict.blocksDict.removeAt(thisBlockIndex);
			VitreoBlocksDict.blocksDict.removeAt(nextProxyBlockIndex);

			//Also add the two connected proxies to this new group
			this.blockIndex = newBlockIndex;
			nextProxy.blockIndex = newBlockIndex;

			//Finally, add the actual block to the dict
			VitreoBlocksDict.blocksDict.put(newBlockIndex, newBlock);
		});

		//If the function pass through, pass this' blockIndex instead
		if(newBlockIndex == nil, {newBlockIndex = this.blockIndex;});

		//A new connection happened in any case! Some things might have changed in the block
		VitreoBlocksDict.blocksDict[newBlockIndex].changed = true;
	}

	//Also moves interpolation proxies
	after {
		arg nextProxy;

		this.group.moveAfter(nextProxy.group);

		this.interpolationProxies.do({
			arg interpolationProxy;
			interpolationProxy.group.moveBefore(this.group);
		});

		^this;
	}

	//Also moves interpolation proxies
	before {
		arg nextProxy;

		this.group.moveBefore(nextProxy.group);

		this.interpolationProxies.do({
			arg interpolationProxy;
			interpolationProxy.group.moveBefore(this.group);
		});

		^this;
	}

	//Also moves interpolation proxies for next one, used for reverseDo when reordering a block
	beforeMoveNextInterpProxies {
		arg nextProxy;

		this.group.moveBefore(nextProxy.group);

		this.interpolationProxies.do({
			arg interpolationProxy;
			interpolationProxy.group.moveBefore(this.group);
		});

		nextProxy.interpolationProxies.do({
			arg interpolationProxy;
			interpolationProxy.group.moveBefore(nextProxy.group);
		});

		^this;
	}
}

//Alias
VNProxy : VitreoNodeProxy {

}


//Just copied over from Ndef, and ProxySpace replaced with VitreoProxySpace.
//I need to inherit from VitreoNodeProxy though, to make it act the same.
VitreoNdef : VitreoNodeProxy {

	classvar <>defaultServer, <>all;
	var <>key;

	*initClass { all = () }

	*new { | key, object |
		// key may be simply a symbol, or an association of a symbol and a server name
		var res, server, dict;

		if(key.isKindOf(Association)) {
			server = Server.named.at(key.value);
			if(server.isNil) {
				Error("VitreoNdef(%): no server found with this name.".format(key)).throw
			};
			key = key.key;
		} {
			server = defaultServer ? Server.default;
		};

		dict = this.dictFor(server);
		res = dict.envir.at(key);
		if(res.isNil) {
			res = super.new(server).key_(key);
			dict.initProxy(res);
			dict.envir.put(key, res)
		};

		object !? { res.source = object };
		^res;
	}

	*ar { | key, numChannels, offset = 0 |
		^this.new(key).ar(numChannels, offset)
	}

	*kr { | key, numChannels, offset = 0 |
		^this.new(key).kr(numChannels, offset)
	}

	*clear { | fadeTime = 0 |
		all.do(_.clear(fadeTime));
		all.clear;
	}

	*dictFor { | server |
		var dict = all.at(server.name);
		if(dict.isNil) {
			dict = VitreoProxySpace.new(server); // use a proxyspace for ease of access.
			all.put(server.name, dict);
			dict.registerServer;
		};
		^dict
	}

	copy { |toKey|
		if(key == toKey) { Error("cannot copy to identical key").throw };
		^this.class.new(toKey).copyState(this)
	}

	proxyspace {
		^this.class.dictFor(this.server)
	}

	storeOn { | stream |
		this.printOn(stream);
	}
	printOn { | stream |
		var serverString = if (server == Server.default) { "" } {
			" ->" + server.name.asCompileString;
		};
		stream << this.class.name << "(" <<< this.key << serverString << ")"
	}

}

//Alias
VNdef : VitreoNdef {

}
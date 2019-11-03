/*
+ NodeProxy {
	after {
		arg nextProxy;
		this.group.moveAfter(nextProxy.group);
		^this;
	}

	before {
		arg nextProxy;
		this.group.moveBefore(nextProxy.group);
		^this;
	}
}
*/


/*
THINGS TO DO:

1) Create all the interpolationProxies for every param AT VitreoNodeProxy instantiation (in the "put" function)

2) Make "Restoring previous connections!" actually work

X) Make SURE that all connections work fine, ensuring that interpolationProxies are ALWAYS before the modulated
proxy and after the modulator. This gets screwed up with long chains.

X) When using clear / free, interpolationProxies should not fade

*/

VitreoProxySpace : ProxySpace {

	makeProxy {
		var proxy = VitreoNodeProxy.new(server);
		this.initProxy(proxy);
		^proxy
	}

	makeTempoClock { | tempo = 1.0, beats, seconds |
		var clock, proxy;
		proxy = envir[\tempo];
		if(proxy.isNil) { proxy = VitreoNodeProxy.control(server, 1); envir.put(\tempo, proxy); };
		proxy.fadeTime = 0.0;
		proxy.put(0, { |tempo = 1.0| tempo }, 0, [\tempo, tempo]);
		this.clock = TempoBusClock.new(proxy, tempo, beats, seconds).permanent_(true);
		if(quant.isNil) { this.quant = 1.0 };
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

VitreoNodeProxy : NodeProxy {
	var <>interpolationProxies, <>interpolationProxiesCopy, <>defaultParamsVals, <>inProxies, <>outProxies;
	classvar <>defaultAddAction=\addToTail;

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
		inProxies = IdentityDictionary.new;
		outProxies = IdentityDictionary.new;

		loaded = false;
		reshaping = defaultReshaping;
		this.linkNodeMap;
	}

	clear { | fadeTime = 0, isInterpolationProxy = false |

		//copy interpolationProxies in new IdentityDictionary in order to free them only
		//after everything
		if(isInterpolationProxy.not, {
			interpolationProxiesCopy = interpolationProxies.copy;
		});

		//This will run through before anything.. that's why the copies
		this.free(fadeTime, true, isInterpolationProxy); 	// free group and objects

		this.removeAll; 			// take out all objects

		children = nil;             // for now: also remove children

		this.stop(fadeTime, true);		// stop any monitor

		monitor = nil;

		this.fadeTime = fadeTime; // set the fadeTime one last time for freeBus
		this.freeBus;	 // free the bus from the server allocator

		//Remove all connected inProxies
		inProxies.keysValuesDo({
			arg param, proxy;
			proxy.outProxies.removeAt(param);
		});

		//Remove all connected outProxies
		outProxies.keysValuesDo({
			arg param, proxy;
			proxy.inProxies.removeAt(param);
		});


		//Remove all NodeProxies used for param interpolation

		//interpolationProxies don't have other interpolation proxies, don't need to run this:
		if(isInterpolationProxy.not, {

			if(fadeTime == nil, {fadeTime = 0});

			Routine.run({

				(fadeTime + 0.001).wait;

				"Clearing interp proxies".postln;

				interpolationProxiesCopy.postln;

				interpolationProxiesCopy.do({
					arg proxy;
					proxy.clear(0, true, true);
				});

				//Only clear at the end of routine
				interpolationProxiesCopy.clear; interpolationProxiesCopy = nil;
			});
		});

		//Reset
		inProxies.clear; inProxies  = nil;
		outProxies.clear; outProxies = nil;
		defaultParamsVals.clear; defaultParamsVals = nil;

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

				"Freeing interp proxies".postln;

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

		//("New VitreoNodeProxy: " ++ obj.class).warn;
		//("New VitreoNodeProxy: " ++ container.asDefName).warn;


		////////////////////////////////////////////////////////////////
		//RESTORE CONNECTIONS:
		//Re-arrange the graph by moving the connected NodeProxies to this (which is the modulator) back up.

		//If it's connected to anything, run this
		if(this.outProxies.size > 0, {
			Routine.run({
				var bottomMostOutProxy;

				//Need to sync it to make sure that prepareToBundle (called in putNewObject) has been caleld.
				server.sync;

				//Find the bottom of the chain
				bottomMostOutProxy = this.findBottomMostOutProxy;

				//Re-arrange all proxies in relation to it
				bottomMostOutProxy.rearrangeProxies(bottomMostOutProxy);

				("Re-arranging all proxies according to " ++ bottomMostOutProxy).warn;
			});
		});

		//Elfe if no out proxies but inProxies, re-arrange according to this
		if((this.outProxies.size == 0).and(this.inProxies.size > 0), {
			Routine.run({
				//Need to sync it to make sure that prepareToBundle (called in putNewObject) has been caleld.
				server.sync;

				this.rearrangeProxies(this);

				("Re-arranging all proxies according to " ++ this).warn;
			});
		});

		//////////////////////////////////////////////////////////////
	}

	findBottomMostOutProxy {
		this.outProxies.do({
			arg outProxy;

			if(outProxy.outProxies.size == 0, {
				^outProxy;
			}, {
				^outProxy.findBottomMostOutProxy;
			});

		});
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

	/*
	//Old way, standard xset used:
	=> {
		arg nextProxy, param = \in;

		//Retrieve if the there already was a connection
		var thisParamEntryInNextProxyInProxies = nextProxy.inProxies[param];

		//If connecting to a new NodeProxy, xfade them (BUT NOT THE modulated one)??
		//If it's the same NodeProxy, simply use the connectMap alternative??

		//Only reconnect entries if a different NodeProxy is used for this entry.
		if(thisParamEntryInNextProxyInProxies != this, {

			//Should this be moved regardless?
			this.before(nextProxy);

			//This creates a new one of the modulated NodeProxy, and xfade in volume
			//between the two. How to crossfade JUST the modulation NodeProxies?
			nextProxy.connectXSet(this, param);

			//overwrite entries if necessary
			nextProxy.inProxies.put(param, this);
			outProxies.put(param, nextProxy);

			//re-arrange the graph by moving the connected NodeProxies back up
			//FIND A WAY TO MAINTAIN THE ORIGINAL CODING ORDER...
			inProxies.keysValuesDo({
				arg inParamName, inProxy;
				inProxy.rearrangeProxies(this);
			});

		});

		//return nextProxy for further chaining
		^nextProxy;
	}

	//Best working version:
	=> {
		arg nextProxy, param = \in;

		//Retrieve if the there already was a connection
		var thisParamEntryInNextProxyInProxies = nextProxy.inProxies[param];

		//Doesn't work when nextProxy is a Pbind...
		var paramRate = nextProxy.controlNames.detect { |x| x.name == param };

		//("Param rate:" ++ paramRate.rate.asString).postln;

		//If connecting to the param for the first time, create a new NodeProxy
		//to be used for interpolated connections, and store it in the outProxies for this, and
		//into nextProxy.inProxies.

		//Then, in the inProxies of the newly created NodeProxy, insert the sources of modulation

		//Only reconnect entries if a different NodeProxy is used for this entry. It also works when
		//thisParamEntryInNextProxyInProxies == nil
		if(thisParamEntryInNextProxyInProxies != this, {

			//Should this be moved regardless?
			this.before(nextProxy);

			//This creates a new one of the modulated NodeProxy, and xfade in volume
			//between the two. How to crossfade JUST the modulation NodeProxies?

			//If param is \in, it gives for granted that the connection
			//is used for some sort of effecting, so it should not apply the new crossfading
			//version...
			nextProxy.connectXSet(this, param);

			//overwrite entries if necessary
			nextProxy.inProxies.put(param, this);
			outProxies.put(param, nextProxy);

			//ALWAYS make sure that when rearraging proxies, the "in-between" interpolation NodeProxies
			//are kept right before the modulated proxy, and all the source of modulation are kept
			//before the "in-between" NodeProxy.

			//re-arrange the graph by moving the connected NodeProxies back up
			inProxies.keysValuesDo({
				arg inParamName, inProxy;
				inProxy.rearrangeProxies(this);
			});

		});

		//return nextProxy for further chaining
		^nextProxy;
	}
	*/

	//Combines before with <<>
	=> {
		arg nextProxy, param = \in;

		var isNextProxyAProxy, interpolationProxyEntry, thisParamEntryInNextProxy, thisParamEntryInNextProxyIsAProxy, paramRate;

		isNextProxyAProxy = (nextProxy.class == VitreoNodeProxy).or(nextProxy.class.superclass == VitreoNodeProxy).or(nextProxy.class.superclass.superclass == VitreoNodeProxy);

		if(isNextProxyAProxy.not, {
			"nextProxy is not a VitreoNodeProxy!!!".error;
		});

		if(this.group == nil, {
			("This proxy hasn't been instantiated yet!!!").warn;
			^nil;
		});

		if(nextProxy.group == nil, {
			("nextProxy hasn't been instantiated yet!!!").warn;
			^nil;
		});

		//Retrieve if a connection was already created a first time
		interpolationProxyEntry = nextProxy.interpolationProxies[param];

		//This is the connection that is in place with the interpolation NodeProxy.
		thisParamEntryInNextProxy = nextProxy.inProxies[param];

		//Check if the previous connection was a NodeProxy at all
		thisParamEntryInNextProxyIsAProxy = (thisParamEntryInNextProxy.class == VitreoNodeProxy).or(thisParamEntryInNextProxy.class.superclass == VitreoNodeProxy).or(thisParamEntryInNextProxy.class.superclass.superclass == VitreoNodeProxy);

		//Returns nil with a Pbind.. this could be problematic for connections, rework it!
		paramRate = (nextProxy.controlNames.detect{ |x| x.name == param }).rate;

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
			Routine.run({

				//Need to wait for the NodeProxy's group to be instantiated on the server.
				server.sync;

				//Set it before the modulated NodeProxy but after the modulation proxy
				//interpolationProxy.before(nextProxy);
				//this.before(interpolationProxy);

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

				//Re-arrange the WHOLE graph.. It's not the most efficient way
				nextProxy.rearrangeProxies(nextProxy);
				//this.rearrangeProxies(interpolationProxy);

				//this.postln;

				//Connections:
				//Without fade: with the modulation proxy at the "\in" param
				interpolationProxy.connectSet(this, \in);

				//With fade: with modulated proxy at the specified param
				nextProxy.connectXSet(interpolationProxy, param);

			});

		}, {

			//Only reconnect entries if a different NodeProxy is used for this entry.
			if(thisParamEntryInNextProxy != this, {

				//Should these be moved regardless?
				//interpolationProxyEntry.before(nextProxy);
				//this.before(interpolationProxyEntry);

				//Remove older connection for the previously used NodeProxy, if it was a NodeProxy, before
				//using the new nextProxy
				if(thisParamEntryInNextProxyIsAProxy, {
					thisParamEntryInNextProxy.outProxies.removeAt(param);
				});

				//Remake connections
				nextProxy.inProxies.put(param, this);

				//Don't use param indexing for outs, as this proxy could be linked
				//to multiple proxies with same param names
				outProxies.put(nextProxy, nextProxy);

				//interpolationProxyEntry.outProxies remains the same!
				interpolationProxyEntry.inProxies.put(\in, this);

				/*
				ALWAYS make sure that when rearraging proxies,
				the "in-between" interpolation NodeProxies are kept right before
				the modulated proxy, and all the source of modulation are kept
				before the "in-between" NodeProxy.
				*/

				//Re-arrange the WHOLE graph.. It's not the most efficient way
				nextProxy.rearrangeProxies(nextProxy);
				//this.rearrangeProxies(interpolationProxyEntry);

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

		var isNextProxyAProxy = (nextProxy.class == VitreoNodeProxy).or(nextProxy.class.superclass == VitreoNodeProxy).or(nextProxy.class.superclass.superclass == VitreoNodeProxy);

		//Standard case with another NodeProxy
		if(isNextProxyAProxy, {
			nextProxy.perform('=>', this, param);

			//Return nextProxy for further chaining
			^nextProxy;

		}, {

			var nextObj, previousEntry, isPreviousEntryAProxy, interpolationProxyEntry;

			/*
			What if interpolationProxies to set are an array ???
			e.g.: ~sines <=.freq [~lfo1, ~lfo2]
			*/

			/*
			What if interpolationProxies to set are a function ???
			e.g.: ~sine <=.freq {rrand(30, 400)}
			*/

			nextObj = nextProxy;

			//First, empty the connections that were on before (if there were any)
			previousEntry = inProxies[param];

			isPreviousEntryAProxy = (previousEntry.class == VitreoNodeProxy).or(previousEntry.class.superclass == VitreoNodeProxy).or(previousEntry.class.superclass.superclass == VitreoNodeProxy);

			//Free previous connections
			if(isPreviousEntryAProxy, {
				inProxies.removeAt(param);
				previousEntry.outProxies.removeAt(param);
			});

			//Retrieve if there was already a interpolationProxy going on
			interpolationProxyEntry = interpolationProxies[param];

			//if there was not
			if(interpolationProxyEntry == nil, {

				//Create it anew
				var interpolationProxy, paramRate;

				//Returns nil with a Pbind.. this could be problematic for connections, rework it!
				paramRate = (this.controlNames.detect{ |x| x.name == param }).rate;

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
				Routine.run({

					//Need to wait for the NodeProxy's group to be instantiated on the server.
					server.sync;

					//Set it before the modulated NodeProxy but after the modulation proxy
					//interpolationProxy.before(this);

					//Default fadeTime: use nextObj's (the modulated one) fadeTime
					interpolationProxy.fadeTime = this.fadeTime;

					//Add the new interpolation NodeProxy to interpolationProxies dict
					this.interpolationProxies.put(param, interpolationProxy);

					//Also add connections for interpolationProxy
					interpolationProxy.outProxies.put(param, this);

					//Re-arrange the WHOLE graph
					this.rearrangeProxies(this);
					//nextProxy.rearrangeProxies(interpolationProxy);

					//Connections:
					//Without fade: with the modulation proxy at the "\in" param
					interpolationProxy.connectSet(nextObj, \in);

					//With fade: with modulated proxy at the specified param
					this.connectXSet(interpolationProxy, param);
				});

			}, {

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

	//Simply re-arrange the whole graph, keeping the same connections.
	//Should this bundled in a single OSC message to maintain order??
	rearrangeProxies {
		arg parentProxy;

		//parentProxy.group.postln;

		//reset it before the parent proxy
		this.before(parentProxy);

		//First, re-arrange the graph by moving the connected NodeProxies back up
		inProxies.keysValuesDo({
			arg paramName, inProxy;

			("inProxy : " ++ paramName).postln;

			inProxy.rearrangeProxies(this);
		});

		//Then, move the interpolationProxies back up too, so that it's ensured they are set
		//after all the modulators (which are in inProxies)
		interpolationProxies.keysValuesDo({
			arg paramName, interpProxy;

			//("interpolationProxy : " ++ paramName).postln;

			interpProxy.before(this);

			//interpProxy.rearrangeProxies(this);
		});

		//Make sure that the outProxies are maintained after the modified proxy (this)
		outProxies.keysValuesDo({
			arg paramName, outProxy;

			//("outProxy : " ++ paramName).postln;

			//First move the proxy itself
			outProxy.after(this);

			//Then move all related interpolation proxies back up, with all branching!
			outProxy.interpolationProxies.keysValuesDo({
				arg interpParamName, outInterpolationProxy;

				//("outInterpolationProxy : " ++ interpParamName).postln;

				outInterpolationProxy.before(outProxy);

				//outInterpolationProxy.rearrangeProxies(outProxy);

			});
		});
	}

	after {
		arg nextProxy;
		this.group.moveAfter(nextProxy.group);
		^this;
	}

	before {
		arg nextProxy;
		this.group.moveBefore(nextProxy.group);
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
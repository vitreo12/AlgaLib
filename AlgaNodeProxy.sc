AlgaNodeProxy : NodeProxy {

	classvar <>defaultAddAction = \addToTail;
	classvar <>defaultReshaping = \elastic;   //Use \elasitc as default. It's set in NodeProxy's init (super.init)

	//The block index that contains this proxy
	var <>blockIndex = -1;

	var <>isInterpProxy = false;

	var <>defaultControlNames;

	var <>inProxies, <>outProxies;

	var <>interpolationProxies, <>interpolationProxiesCopy;
	var <>interpolationProxiesNormalizer, <>interpolationProxiesNormalizerCopy;

	init {
		//These are the interpolated ones!!
		interpolationProxies = IdentityDictionary.new;

		interpolationProxiesNormalizer = IdentityDictionary.new;

		//These are used for <| (unmap) to restore default values and to get number of channels per parameter
		defaultControlNames = Dictionary.new;

		//General I/O
		inProxies  = IdentityDictionary.new(20);
		outProxies = IdentityDictionary.new(20);

		blockIndex = -1;

		//Call NodeProxy's init
		super.init;

		//Default reshaping is expanding
		this.reshaping = defaultReshaping;
	}

	clear { | fadeTime = 0, isInterpolationProxy = false |

		//copy interpolationProxies in new IdentityDictionary in order to free them only
		//after everything has been freed already.
		//Also, remove block from AlgaBlocksDict.blocksDict
		if(isInterpolationProxy.not, {
			var blockWithThisProxy;

			interpolationProxies.postln;

			interpolationProxiesCopy = interpolationProxies.copy;
			interpolationProxiesNormalizerCopy = interpolationProxiesNormalizer.copy;

			//remove from block in AlgaBlocksDict.blocksDict
			blockWithThisProxy = AlgaBlocksDict.blocksDict[this.blockIndex];

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
				/*
				//Function, Binops, Arrays
				proxy.do({
					arg proxyInArray;
					proxyInArray.outProxies.removeAt(param);
				})
				*/
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

				//interpolationProxiesCopy.postln;

				interpolationProxiesCopy.do({
					arg proxy;
					proxy.clear(0, true, true);
				});

				interpolationProxiesNormalizerCopy.do({
					arg proxy;
					proxy.clear(0, true, true);
				});

				//Only clear at the end of routine
				interpolationProxiesCopy.clear; interpolationProxiesCopy = nil;
				interpolationProxiesNormalizerCopy.clear; interpolationProxiesNormalizerCopy = nil;

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
		defaultControlNames.clear; defaultControlNames = nil;
		interpolationProxies.clear; interpolationProxies = nil;

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

			if(interpolationProxiesNormalizerCopy.size != interpolationProxiesNormalizer.size, {
				interpolationProxiesNormalizerCopy = interpolationProxiesNormalizer.copy;
			});

			if(fadeTime == nil, {fadeTime = 0});

			Routine.run({
				(fadeTime + 0.001).wait;

				//"Freeing interp proxies".postln;

				interpolationProxiesCopy.do({
					arg proxy;
					proxy.free(0, freeGroup, true);
				});

				interpolationProxiesNormalizerCopy.do({
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

	//Copied over from NodeProxy with added defaultControlNames
	putExtended { | index, obj, channelOffset = 0, extraArgs, now = true |
		var container, bundle, oldBus = bus;

		if(obj.isNil) { this.removeAt(index); ^this };
		if(index.isSequenceableCollection) {
			^this.putAll(obj.asArray, index, channelOffset)
		};

		bundle = MixedBundle.new;

		container = obj.makeProxyControl(channelOffset, this);
		container.build(this, index ? 0); // bus allocation happens here

		server.bind({

			//Need this to retrieve default values and number of channels per parameter
			if(isInterpProxy == false, {

				//Instantiating a Synth or SynthDef:
				if((container.class == SynthControl).or(container.class == SynthDefControl), {
					container.controlNames.do({
						arg controlName;

						var controlNameName = controlName.name;

						//Ignore gate, out and fadeTime params
						if((controlNameName != \gate).and(
							controlNameName != \out).and(
							controlNameName != \fadeTime), {

							//Add param to dict
							defaultControlNames.put(controlNameName, controlName);
						});
					});
				});

				//Instantiating a Pattern...
				if(container.class == PatternControl, {
					var foundInstrument = false;
					var synthDesc;

					var foundFreq = false;
					var foundAmp = false;

					//CHECK IF IT'S A PBIND HERE!

					obj.patternpairs.do({
						arg val, index;

						//found instrument entry
						if(val == \instrument, {
							var synthDefName;

							//out of bounds, provided \instrument without the synthdef name at the end of array
							if(index + 1 >= obj.patternpairs.size, {
								"\instrument must be followed by a SynthDef name".error;
							});

							//the one that follows \instrument
							synthDefName = obj.patternpairs[index + 1];

							if(synthDefName.class != Symbol, {
								"\instrument must be followed by a SynthDef name".error;
							});

							//Look for the synthDesc in the global library. From there, parameters can be extracted
							synthDesc = SynthDescLib.global.at(synthDefName);

							if(synthDesc == nil, {
								"invalid SynthDef for \instrument".error;
							});

							foundInstrument = true;
						});

						if(val == \freq, { foundFreq = true; });
						if(val == \amp, { foundAmp = true; });
					});

					if(foundInstrument, {
						//Retrieve parameters from the SynthDesc
						synthDesc.controls.do({
							arg controlName;

							var controlNameName = controlName.name;

							//Ignore gate, out and fadeTime params
							if((controlNameName != \gate).and(
								controlNameName != \out).and(
								controlNameName != \fadeTime), {

								//Add param to dict
								defaultControlNames.put(controlNameName, controlName);

								//Also, if \freq is not provided in Pbind's param and param == freq, use it as default
								if((foundFreq == false).and(controlNameName == \freq), {
									this.set(controlNameName, controlName.defaultValue);
								});

								//Also, if \amp is not provided in Pbind's param and param == freq, use it as default
								if((foundAmp == false).and(controlNameName == \amp), {
									this.set(controlNameName, controlName.defaultValue);
								});
							});
						});
					}, {
						//Make sure \instrument is ALWAYS provided
						"Alga Pbinds must always provide an \instrument".error;
					});
				});

				//defaultControlNames.postln;

				//create all interp proxies
				this.createAllInterpProxies;
			});


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

			if(isInterpProxy == false, {

				//Then, reinstantiate connections that were in place, adapting ins/outs and rates.
				//this.recoverConnections;

				////////////////////////////////////////////////////////////////

				//REARRANGE BLOCK!!
				this.createNewBlockIfNeeded(this);
				AlgaBlocksDict.reorderBlock(this.blockIndex, server);

				//////////////////////////////////////////////////////////////
			});
		});
	}

	//To be done when re-instantiating a source
	recoverConnections {
		this.outProxies.do({
			arg outProxy;

			//find this one
			block ({
				arg break;

				outProxy.inProxies.keysValuesDoProxiesLoop({
					arg paramName, inProxy;

					//found it! remake connection
					if(inProxy == this, {
						("Restoring connection of " ++ outProxy.asString ++ " with " ++ this.asString).postln;
						outProxy.setInterpProxy(this, paramName, reorderBlock:false);
						break.(nil);
					});
				});
			});
		});

		/*
		this.inProxies.keysValuesDoProxiesLoop({
			arg paramName, inProxy;
		});
		*/
	}

	//When a new object is assigned to a AlgaNodeProxy!
	put { | index, obj, channelOffset = 0, extraArgs, now = true |

		var numberOfChannels;

		var isObjAFunction, isObjAnOp, isObjAnArray;

		//Call NodeProxy's put, first.
		//super.put(index, obj, channelOffset, extraArgs, now);
		this.putExtended(index, obj, channelOffset, extraArgs, now);

		//Create interpolationProxies for all params
		if(isInterpProxy == false, {

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

			/*
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

			*/

			////////////////////////////////////////////////////////////////

			//REARRANGE BLOCK!!

			//AlgaBlocksDict.reorderBlock(this.blockIndex, server);

			//////////////////////////////////////////////////////////////
		});
	}

	//Start group if necessary. Here is the defaultAddAction at work.
	//This function is called in put -> putNewObject
	prepareToBundle { arg argGroup, bundle, addAction = defaultAddAction;
		super.prepareToBundle(argGroup, bundle, addAction);
	}

	//These are straight up copied from BusPlug. Overwriting to retain group ordering stuff
	play { | out, numChannels, group, multi=false, vol, fadeTime, addAction |
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

		//REARRANGE BLOCK!!
		AlgaBlocksDict.reorderBlock(this.blockIndex, server);

		////////////////////////////////////////////////////////////////

		/*
		//Add defaultAddAction
		if(addAction == nil, {
			addAction = defaultAddAction;
		});
		*/

		this.changed(\play, [out, numChannels, group, multi, vol, fadeTime, addAction]);
	}

	playN { | outs, amps, ins, vol, fadeTime, group, addAction |
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

		//REARRANGE BLOCK!!

		AlgaBlocksDict.reorderBlock(this.blockIndex, server);

		////////////////////////////////////////////////////////////////

		/*
		//Add defaultAddAction
		if(addAction == nil,
			addAction = defaultAddAction;
		});
		*/

		this.changed(\playN, [outs, amps, ins, vol, fadeTime, group, addAction]);
	}

	createAllInterpProxies {

		server.bind({

			defaultControlNames.do({
				arg controlName;

				var paramName = controlName.name;
				var paramVal  = controlName.defaultValue;

				var paramNumberOfChannels = controlName.numChannels;

				//Create interpProxy for this paramName
				this.createInterpProxy(paramName, controlName, paramNumberOfChannels);

			});

		});

		//this.defaultControlNames.postln;
		//this.interpolationProxies.postln;
	}

	freePreviousInterpProxies {
		this.interpolationProxies.do({
			arg interpProxy;
			interpProxy.free(isInterpolationProxy:true);
		});

		this.interpolationProxiesNormalizer.do({
			arg interpProxyNorm;
			interpProxyNorm.free(isInterpolationProxy:true);
		});
	}

	createInterpProxy {
		arg paramName = \in, controlName, paramNumberOfChannels = 1,
		recursiveCall = false, onCreation = nil, src = nil;

		var paramRate, paramRateString;

		var isThisProxyInstantiated = true;

		var prevInterpProxy;

		var interpolationProxy;
		var interpolationProxyNormalizer;

		var defaultValue;

		/*
		if(this.group == nil, {
			("This proxy hasn't been instantiated yet!!!").warn;
			isThisProxyInstantiated = false;
		});
		*/

		if(controlName != nil, {
			paramRate = controlName.rate;
		}, {
			("Can't retrieve parameter rate for " ++ paramName).warn;
			^nil;
		});

		if(paramRate == \audio, {paramRateString = "ar";}, {paramRateString = "kr";});

		//Check if interpolationProxy was already created.
		prevInterpProxy = this.interpolationProxies[paramName];

		//Create new ones!
		if((prevInterpProxy == nil).or(recursiveCall), {

			defaultValue = controlName.defaultValue;

			//Doesn't work with Pbinds with ar param, would just create a kr version
			interpolationProxy = AlgaNodeProxy.new(server, paramRate, paramNumberOfChannels);
			interpolationProxyNormalizer = AlgaNodeProxy.new(server, paramRate, paramNumberOfChannels);

			interpolationProxy.isInterpProxy = true;
			interpolationProxyNormalizer.isInterpProxy = true;

			//Should it not be elastic?
			interpolationProxy.reshaping = defaultReshaping;
			interpolationProxyNormalizer.reshaping = defaultReshaping;

			//Default fadeTime: use nextProxy's (the modulated one) fadeTime
			//interpolationProxy.fadeTime = 0;
			interpolationProxy.fadeTime = this.fadeTime;
			interpolationProxyNormalizer.fadeTime = 0;

			//Add the new interpolation NodeProxy to interpolationProxies dict
			this.interpolationProxies.put(paramName, interpolationProxy);
			this.interpolationProxiesNormalizer.put(paramName, interpolationProxyNormalizer);

			//This is quite useless. interpolationProxies are kept in the appropriate dictionary of the proxy
			interpolationProxy.outProxies.put(paramName, this);

			//This routine stuff needs to be tested on Linux...
			Routine.run({
				var interpolationProxySymbol = ("proxyIn_" ++
					paramRateString ++ paramNumberOfChannels ++
					"_" ++ paramRateString ++ paramNumberOfChannels).asSymbol;

				var interpolationProxyNormalizesSymbol = ("interpProxyNorm_" ++
					paramRateString ++ paramNumberOfChannels).asSymbol;

				//interpolationProxySymbol.postln;
				//interpolationProxyNormalizesSymbol.postln;


				//REVIEW!
				//Doing connections before the instantiation in order to make sure they are set when
				//the proxies are created, avoiding any "clicks".
				//Assign the defaultValue to the interpolationProxy
				interpolationProxy.set(\in, defaultValue);

				//Connect interpolationProxy to normalizer
				interpolationProxyNormalizer.set(\args, interpolationProxy);

				server.sync;

				//Actually instantiate ProxySynthDef / SynthDef
				interpolationProxy.source = interpolationProxySymbol;
				interpolationProxyNormalizer.source = interpolationProxyNormalizesSymbol;

				//sync server so group is correctly created for interpolationProxy
				server.sync;

				//REVIEW!
				//Connect to Normalizer
				//this.set(paramName, interpolationProxyNormalizer);

				//if(recursiveCall, { interpolationProxy.unset(\in); });

				//REVIEW!
				//interpolationProxy.set(\in, defaultValue);
				//server.sync;
				//interpolationProxyNormalizer.set(\args, interpolationProxy);

				if(onCreation != nil, {

					server.sync;

					//"before".postln;

					//Execute callback function after server sync.
					onCreation.value();
				});

			});

		}, {

			var prevInterpProxyNorm = this.interpolationProxiesNormalizer[paramName];

			var onCreationFunc = {

				var proxyToRestore;
				var newInterpProxy, newInterpProxyNorm;

				//REVIEW!
				//Unset from previous connections, this does the fadeout
				//this.xunset(paramName);

				//Old proxy connected to the param
				proxyToRestore = prevInterpProxy.inProxies[\in];

				("proxyToRestore: " ++ proxyToRestore.asString).postln;

				//This will be the newly created in the createInterpProxy function.
				newInterpProxy = this.interpolationProxies[paramName];
				newInterpProxyNorm = this.interpolationProxiesNormalizer[paramName];

				if(proxyToRestore != nil, {

					//This is where fadeout/fadein happens
					//this.perform('<=', proxyToRestore, paramName);
					this.backwardConnectionInner(proxyToRestore, paramName, newlyCreatedInterpProxyNorm:true);

					server.sync;

					//this.inProxies.postln;
					//proxyToRestore.outProxies.postln;
				});

				//"Start fade time clear".postln;

				//Clear previous ones after fade time (this func is gonna be called in a Routine, so .wait can be used)
				//This should take account of parameter's fade time, not proxy's !!!
				this.fadeTime.wait;
				prevInterpProxy.clear(0, true);
				prevInterpProxyNorm.clear(0, true);
			};

			if(prevInterpProxyNorm == nil, {
				"Invalid parameter " ++ paramName.asString ++ "for interpProxyNorm".warn;
				^this;
			});

			//Create new ones, then run the callback function onCreationFunc
			this.createInterpProxy(paramName, controlName, paramNumberOfChannels,
				recursiveCall: true,
				onCreation: onCreationFunc);
		});
	}

	connectToInterpProxy {
		//Pass interpolationProxy as argument to save CPU cycles of retrieving it from dict
		arg param = \in, interpolationProxy = nil, prevProxy, useXSet = true;

		var controlName, paramRate, paramNumberOfChannels, canBeMapped;
		var prevProxyRate, isPrevProxyAProxy, prevProxyNumChannels;

		isPrevProxyAProxy = (prevProxy.class == AlgaNodeProxy).or(
			prevProxy.class.superclass == AlgaNodeProxy).or(
			prevProxy.class.superclass.superclass == AlgaNodeProxy);

		if(prevProxy.isNil) { ^this.unmap(param) };

		controlName = this.defaultControlNames[param];

		if(controlName == nil, {
			("ERROR: Could not find param " ++ param).warn;
			^prevProxy;
		});

		//If nil, try to retrieve it from the dict
		if(interpolationProxy == nil, {

			interpolationProxy = this.interpolationProxies[param];

			//If still nil, exit
			if(interpolationProxy == nil, {
				("ERROR: Could not find interpolationProxy for " ++ param).warn;
				^prevProxy;
			});
		});

		paramRate = controlName.rate;
		paramNumberOfChannels = controlName.numChannels;

		//If it's a proxy, use previousProxy's stuff
		if(isPrevProxyAProxy, {
			prevProxyRate = prevProxy.rate;
			prevProxyNumChannels = prevProxy.numChannels;
		}, {

			//Otherwise, use param's ones
			prevProxyRate = paramRate;
			prevProxyNumChannels = paramNumberOfChannels
		});

		//prevProxyRate.postln;
		//prevProxyNumChannels.postln;

		//Init prev proxy's out bus!
		canBeMapped = prevProxy.initBus(prevProxyRate, prevProxyNumChannels);

		if(canBeMapped) {
			//Init interpolationProxy's in bus with same values!
			if(interpolationProxy.isNeutral) { interpolationProxy.defineBus(prevProxyRate, prevProxyNumChannels) };

			if(useXSet, {
				interpolationProxy.xset(\in, prevProxy);
			}, {
				interpolationProxy.set(\in, prevProxy);
			});
		} {
			"Could not link node proxies, no matching input found.".warn
		};

		^prevProxy;
	}

	setInterpProxy {
		arg prevProxy, param = \in, src = nil, reorderBlock = true, newlyCreatedInterpProxyNorm = false;

		//Check if there already was an interpProxy for the parameter
		var interpolationProxyEntry = this.interpolationProxies[param];
		var interpolationProxyNormalizerEntry = this.interpolationProxiesNormalizer[param];

		//Returns nil with a Pbind.. this could be problematic for connections, rework it!
		var paramRate, prevProxyRate;

		var controlName;

		var paramNumberOfChannels, prevProxyNumberOfChannels;

		var isThisProxyInstantiated = true;
		var isPrevProxyInstantiated = true;

		//This is the connection that is in place with the interpolation NodeProxy.
		var previousParamEntry = this.inProxies[param];

		//This is used to discern the different behaviours
		var prevProxyClass = prevProxy.class;

		var isPrevProxyAProxy = (prevProxyClass == AlgaNodeProxy).or(
			prevProxyClass.superclass == AlgaNodeProxy).or(
			prevProxyClass.superclass.superclass == AlgaNodeProxy);

		/*
		var isPrevProxyANumber = false;

		if(isPrevProxyAProxy.not, {
			isPrevProxyANumber = (prevProxyClass == Number).or(
				prevProxyClass.superclass == Number).or(
				prevProxyClass.superclass.superclass == Number);
		});
		*/

		if((interpolationProxyEntry == nil).or(interpolationProxyNormalizerEntry == nil), {
			("Invalid interpolation proxy").warn;
			^this;
		});


		if(this.group == nil, {
			("This proxy hasn't been instantiated yet!!!").warn;
			isThisProxyInstantiated = false;

			//^this;
		});

		if(isPrevProxyAProxy, {
			if(prevProxy.group == nil, {
				("prevProxy hasn't been instantiated yet!!!").warn;
				isPrevProxyInstantiated = false;

				//^this;
			});
		});

		controlName = defaultControlNames[param];

		if(controlName != nil, {
			paramRate = controlName.rate;
		}, {
			("Can't retrieve parameter rate for " ++ param).warn;
			^nil;
		});

		if(controlName != nil, {
			paramNumberOfChannels = controlName.numChannels;
		}, {
			("Can't retrieve parameter number of channels for " ++ param).warn;
			^nil;
		});

		if(isPrevProxyAProxy, {
			prevProxyNumberOfChannels = prevProxy.numChannels;
			prevProxyRate = prevProxy.rate;

			//("prev proxy channels : " ++ prevProxy.numChannels).postln;
			//("prev proxy rate : " ++ prevProxyRate).postln;
			//("this proxy's " ++ param ++ " channels : " ++ paramNumberOfChannels).postln;
			//("this proxy's " ++ param ++ " rate : " ++ paramRate).postln
		}, {
			//If not a proxy (but, like, a number), use same number of channels and rate
			prevProxyNumberOfChannels = paramNumberOfChannels;
			prevProxyRate = paramRate;

		});

		//Free previous connections to the this, if there were any
		this.freePreviousConnection(param);

		//Just switch the function
		//if(src != nil, {
		//	interpolationProxyEntry.source = src;
		//});

		//previousParamEntry.postln;
		//prevProxy.postln;

		//REVIEW!
		//if(previousParamEntry != prevProxy, {
		//if(true, {
		Routine.run({

			var changeInterpProxySymbol = false, interpolationProxySymbol, interpolationProxyNormalizesSymbol;

			//Previous interpProxy
			var interpolationProxySource = interpolationProxyEntry.source;
			var interpolationProxySourceString = interpolationProxySource.asString;

			var previousInterpProxyIns = 1, previousInterpProxyOuts = 1;

			if(interpolationProxySourceString.beginsWith("\proxyIn"), {
				previousInterpProxyIns = interpolationProxySourceString[10..11];
				previousInterpProxyOuts = interpolationProxySourceString[interpolationProxySourceString.size-2..interpolationProxySourceString.size-1];

				//strip < 10 in/outs count
				if(previousInterpProxyIns[1].asString == "_", { previousInterpProxyIns = previousInterpProxyIns[0].asString; });
				if(previousInterpProxyOuts[0].asString == "r", { previousInterpProxyOuts = previousInterpProxyOuts[1].asString; });
			});

			//Don't use param indexing for outs, as this proxy could be linked
			//to multiple proxies with same param names
			if(isPrevProxyAProxy, {
				this.inProxies.put(param, prevProxy);
				prevProxy.outProxies.put(this, this);
			});

			//re-instantiate source if not correct, here is where rate conversion and multichannel connectons happen.
			if(((interpolationProxySourceString.beginsWith("\proxyIn").not).or(
				previousInterpProxyOuts != paramNumberOfChannels).or(
				previousInterpProxyIns != prevProxyNumberOfChannels).or(
				paramNumberOfChannels != prevProxyNumberOfChannels).or(
				paramRate != prevProxyRate)),
			{
				var prevProxyRateString;
				var paramRateString;

				/*
				("previousInterpProxyOuts: " ++ previousInterpProxyOuts).postln;
				("paramNumberOfChannels: " ++ paramNumberOfChannels).postln;
				("previousInterpProxyIns: " ++ previousInterpProxyIns).postln;
				("prevProxyNumberOfChannels: " ++ prevProxyNumberOfChannels).postln;
				("paramNumberOfChannels: " ++ paramNumberOfChannels).postln;
				("prevProxyNumberOfChannels: " ++ prevProxyNumberOfChannels).postln;
				("paramRate: " ++ paramRate).postln;
				("prevProxyRate: " ++ prevProxyRate).postln;
				*/

				if(paramRate == \audio, {
					paramRateString = "ar";
				}, {
					paramRateString = "kr";
				});

				if(prevProxyRate == \audio, {
					prevProxyRateString = "ar";
				}, {
					if(prevProxyRate == \control, {
						prevProxyRateString = "kr";
					});
				});

				interpolationProxySymbol = ("proxyIn_" ++ prevProxyRateString ++ prevProxyNumberOfChannels
					++ "_" ++ paramRateString ++ paramNumberOfChannels).asSymbol;

				//changeInterpProxySymbol = true;

				//interpolationProxyNormalizesSymbol = ("interpProxyNorm_" ++ paramRateString ++ paramNumberOfChannels).asSymbol;

				//free previous ones?
				//interpolationProxyEntry.free(interpolationProxyEntry.fadeTime, true, true);

				//REVIEW!
				//Should .source be modified like this OR should a new interpolationProxy be created
				//on a new group and freeing this one after fadeTime? This is done in createInterpProxy, check
				//it there!
				interpolationProxyEntry.source = interpolationProxySymbol;

				//interpolationProxyNormalizerEntry.source = interpolationProxyNormalizesSymbol;
			});

			//interpolationProxyEntry.outProxies remains the same, connected to this!
			if(isPrevProxyAProxy, {
				interpolationProxyEntry.inProxies.put(\in, prevProxy);
			});

			//Only rearrange block if both proxies are actually instantiated.
			if(reorderBlock.and(isThisProxyInstantiated.and(isPrevProxyInstantiated)), {
				AlgaBlocksDict.reorderBlock(this.blockIndex, server);
			});

			//sync server (so it's sure that .source got executed before making the connections)
			server.sync;

			//REVIEW!
			//Make connection to the normalizer
			if(newlyCreatedInterpProxyNorm.not, {
				//This is executed with normal connections. It will set the parameter
				//right now (the interpolation happens in the interpProxy, not interpProxyNorm).
				//For successive connections, the same interpNorm will be utilized, effectively making
				//this .set useless after the first stable connection.
				this.set(param, interpolationProxyNormalizerEntry);

				//Make connection to the interpolationProxy. Values are here interpolated using xset.
				this.connectToInterpProxy(param, interpolationProxyEntry, prevProxy);
			}, {
				//Make connection to the interpolationProxy. No need of interpolating as it's happening already
				//between the two different interpNorms.
				//This should not make much of a difference, as interpolation values are scaled in the interpNorm anyway.
				this.connectToInterpProxy(param, interpolationProxyEntry, prevProxy, useXSet:false);

				//This means that the previous interpNorm has been replaced by re-instantiating.
				//interpolation between the two is needed to switch states.
				this.xset(param, interpolationProxyNormalizerEntry);
			});


			//If prevProxy is not a NodeProxy, also run the unset command.
			//The connection to prevProxy (if it was like, a number) has already been set anyway.
			//this just helps removing connectons that are actually not in place anymore
			if(isPrevProxyAProxy.not, {
				"Unsetting param".postln;
				this.unset(param);
			});


			//Actually change the source
			//if(changeInterpProxySymbol, {
				//interpolationProxyEntry.source = interpolationProxySymbol;
			//});
		});
	}

	forwardConnectionInner {
		arg nextProxy, param = \in, newlyCreatedInterpProxyNorm = false;

		var isNextProxyAProxy, isThisProxyAnOp, isThisProxyAFunc, isThisProxyAnArray;

		var thisBlockIndex;
		var nextProxyBlockIndex;

		isNextProxyAProxy = (nextProxy.class == AlgaNodeProxy).or(
			nextProxy.class.superclass == AlgaNodeProxy).or(
			nextProxy.class.superclass.superclass == AlgaNodeProxy);

		if((isNextProxyAProxy.not), {
			"nextProxy is not a valid AlgaNodeProxy!!!".warn;
			^this;
		});

		if(this.server != nextProxy.server, {
			"nextProxy is on a different server!!!".warn;
			^this;
		});

		//("nextProxy's num of channels: " ++ nextProxy.numChannels).postln;

		/*
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
		*/

		//Create a new block if needed
		this.createNewBlockIfNeeded(nextProxy);

		/*
		"=>".postln;
		this.asString.postln;
		this.numChannels.postln;
		*/

		//Create a new interp proxy if needed, and make correct connections
		nextProxy.setInterpProxy(this, param, newlyCreatedInterpProxyNorm:newlyCreatedInterpProxyNorm);

		//return nextProxy for further chaining
		^nextProxy;
	}

	//Combines before with <<>
	=> {
		arg nextProxy, param = \in;
		this.forwardConnectionInner(nextProxy, param);
	}

	backwardConnectionInner {
		arg nextProxy, param = \in, newlyCreatedInterpProxyNorm = false;

		var isNextProxyAProxy, isNextProxyAnOp, isNextProxyAFunc, isNextProxyAnArray, paramRate;

		/* Overloaded calls for AbstractOpPlug, Function and Array */

		/*

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
		*/

		isNextProxyAProxy = (nextProxy.class == AlgaNodeProxy).or(
			nextProxy.class.superclass == AlgaNodeProxy).or(
			nextProxy.class.superclass.superclass == AlgaNodeProxy);

		/*
		What if interpolationProxies to set are an array ???
		e.g.: ~sines <=.freq [~lfo1, ~lfo2]
		*/

		//Standard case with another NodeProxy
		if(isNextProxyAProxy, {

			//If next proxy is an AbstractOpPlug or Function, check ClassExtensions.sc
			//nextProxy.perform('=>', this, param);
			nextProxy.forwardConnectionInner(this, param, newlyCreatedInterpProxyNorm:newlyCreatedInterpProxyNorm); // equal to '=>'

			//Return nextProxy for further chaining
			^nextProxy;

		}, {
			//Case when nextProxy is a Number.. like ~sine <=.freq 400.
			//Can't use => as Number doesn't have => method

			//Create a new block if needed
			this.createNewBlockIfNeeded(this);

			//Actually set the connections.
			this.setInterpProxy(nextProxy, param, newlyCreatedInterpProxyNorm:newlyCreatedInterpProxyNorm);
		});

		//return this for further chaining
		^this;
	}

	//combines before (on nextProxy) with <>>
	//It also allows to set to plain numbers, e.g. ~sine <=.freq 440
	<= {
		arg nextProxy, param = \in;
		this.backwardConnectionInner(nextProxy, param);
	}

	//Unmap
	<| {
		arg param = \in;

		var controlName = defaultControlNames[param];

		if(controlName == nil, {
			"Trying to restore a nil value".warn;
		}, {
			var defaultValue = controlName.defaultValue;

			("Restoring default value for " ++ param ++ " : " ++ defaultValue).postln;

			//Simply restore the default original value using the <= operator
			this.perform('<=', defaultValue, param);
		});

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
		var previousInterpolatonProxy = this.interpolationProxies[param];

		var isPreviousEntryAProxy = (previousEntry.class == AlgaNodeProxy).or(
			previousEntry.class.superclass == AlgaNodeProxy).or(
			previousEntry.class.superclass.superclass == AlgaNodeProxy);

		if(isPreviousEntryAProxy, {
			//Remove connection in previousEntry's outProxies to this one
			previousEntry.removeOutProxy(this, param);

		}, {
			//ARRAY!

			//Array is used to store connections for Function, AbstractOpPlug and Array,
			//since multiple NodeProxies might be connected to the same param.
			var isPreviousEntryAnArray = previousEntry.class == Array;

			if(isPreviousEntryAnArray, {
				previousEntry.do({
					arg previousProxyEntry;
					previousProxyEntry.removeOutProxy(this, param);
				});
			});
		});

		//FIX HERE!
		//Clear interpolationProxies' previous connection (Abslutely needed)
		if(previousInterpolatonProxy != nil, {
			previousInterpolatonProxy.inProxies.removeAt(\in);
		});

		//Clear previousEntry
		if(previousEntry != nil, {
			this.inProxies.removeAt(param);
		});
	}

	removeOutProxy {
		arg proxyToRemove, param = \in;

		var isThisConnectedToAnotherParam = false;

		//First, check if the this was perhaps connected to another param of the other proxy..
		//This is a little to expensive, find a better way
		block ({
			arg break;
			proxyToRemove.inProxies.keysValuesDoProxiesLoop({
				arg inParam, inProxy;

				//Check for param duplicates and identity
				if((inParam != param).and(inProxy == this), {
					isThisConnectedToAnotherParam = true;
					break.(nil);
				});
			});
		});

		//this.postln;
		//proxyToRemove.postln;
		//isThisConnectedToAnotherParam.postln;

		if(isThisConnectedToAnotherParam == false, {
			//Remove older connection to this only if it's not connected to
			//any other param of this proxy..
			//Remember that outProxies are stored with proxy -> proxy, not param -> proxy
			this.outProxies.removeAt(proxyToRemove);
		});

		//Also reset block index if needed, if its outProxies
		//and inProxies have size 0 (meaning it's not connected to anything anymore)
		if((this.outProxies.size == 0).and(this.inProxies.size == 0), {
			this.blockIndex = -1;
		});
	}

	//This function should be moved to AlgaProxyBlock
	createNewBlockIfNeeded {
		arg nextProxy;

		var newBlockIndex;
		var newBlock;

		var thisBlockIndex = this.blockIndex;
		var nextProxyBlockIndex = nextProxy.blockIndex;

		//"createNewBlockIfNeeded".postln;

		//thisBlockIndex.postln;
		//nextProxyBlockIndex.postln;

		//Create new block if both connections didn't have any
		if((thisBlockIndex == -1).and(nextProxyBlockIndex == -1), {
			newBlockIndex = UniqueID.next;
			newBlock = AlgaProxyBlock.new(newBlockIndex);

			//"new block".postln;

			this.blockIndex = newBlockIndex;
			nextProxy.blockIndex = newBlockIndex;

			//Add block to blocksDict
			AlgaBlocksDict.blocksDict.put(newBlockIndex, newBlock);

			//Add proxies to the block
			AlgaBlocksDict.blocksDict[newBlockIndex].addProxy(this);
			AlgaBlocksDict.blocksDict[newBlockIndex].addProxy(nextProxy);

		}, {

			//If they are not already in same block
			if(thisBlockIndex != nextProxyBlockIndex, {

				//Else, add this proxy to nextProxy's block, together with all proxies from this' block
				if(thisBlockIndex == -1, {
					//"add this to nextProxy's block".postln;
					this.blockIndex = nextProxyBlockIndex;

					//Add proxy to the block
					AlgaBlocksDict.blocksDict[nextProxyBlockIndex].addProxy(this);

					//This is for the changed at the end of function...
					newBlockIndex = nextProxyBlockIndex;
				}, {

					//Else, add nextProxy to this block, together with all proxies from nextProxy's block
					if(nextProxyBlockIndex == -1, {
						//"add nextProxy to this' block".postln;
						nextProxy.blockIndex = thisBlockIndex;

						//Add proxy to the block
						AlgaBlocksDict.blocksDict[thisBlockIndex].addProxy(nextProxy);

						//This is for the changed at the end of function...
						newBlockIndex = thisBlockIndex;
					});
				}, {

					//Else, it means bot proxies are already in blocks. Merge them into a new one!

					newBlockIndex = UniqueID.next;
					newBlock = AlgaProxyBlock.new(newBlockIndex);

					//"both proxies already into blocks. creating new".postln;

					//Change all proxies' group to the new one and add then to new block
					AlgaBlocksDict.blocksDict[thisBlockIndex].dictOfProxies.do({
						arg proxy;

						proxy.blockIndex = newBlockIndex;

						newBlock.addProxy(proxy);
					});

					AlgaBlocksDict.blocksDict[nextProxyBlockIndex].dictOfProxies.do({
						arg proxy;

						proxy.blockIndex = newBlockIndex;

						newBlock.addProxy(proxy);
					});

					//Remove previous' groups
					AlgaBlocksDict.blocksDict.removeAt(thisBlockIndex);
					AlgaBlocksDict.blocksDict.removeAt(nextProxyBlockIndex);

					//Also add the two connected proxies to this new group
					this.blockIndex = newBlockIndex;
					nextProxy.blockIndex = newBlockIndex;

					//Finally, add the actual block to the dict
					AlgaBlocksDict.blocksDict.put(newBlockIndex, newBlock);
				});
			});
		});

		//If both are already into blocks and the block is different, the two blocks should merge into a new one!
		//if((thisBlockIndex != nextProxyBlockIndex).and((thisBlockIndex != -1).and(nextProxyBlockIndex != -1)), {
		//});

		//If the function pass through, pass this' blockIndex instead
		if(newBlockIndex == nil, {newBlockIndex = this.blockIndex;});

		//A new connection happened in any case! Some things might have changed in the block
		AlgaBlocksDict.blocksDict[newBlockIndex].changed = true;
	}

	//Also moves interpolation proxies
	after {
		arg nextProxy;

		this.group.moveAfter(nextProxy.group);

		this.interpolationProxies.do({
			arg interpolationProxy;
			interpolationProxy.group.moveBefore(this.group);
		});

		this.interpolationProxiesNormalizer.do({
			arg interpolationProxyNormalizer;
			interpolationProxyNormalizer.group.moveBefore(this.group);
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

		this.interpolationProxiesNormalizer.do({
			arg interpolationProxyNormalizer;
			interpolationProxyNormalizer.group.moveBefore(this.group);
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

		this.interpolationProxiesNormalizer.do({
			arg interpolationProxyNormalizer;
			interpolationProxyNormalizer.group.moveBefore(this.group);
		});

		nextProxy.interpolationProxies.do({
			arg interpolationProxy;
			interpolationProxy.group.moveBefore(nextProxy.group);
		});

		nextProxy.interpolationProxiesNormalizer.do({
			arg interpolationProxyNormalizer;
			interpolationProxyNormalizer.group.moveBefore(nextProxy.group);
		});

		^this;
	}
}

//Alias
ANProxy : AlgaNodeProxy {

}


//Just copied over from Ndef, and ProxySpace replaced with AlgaProxySpace.
//I need to inherit from AlgaNodeProxy though, to make it act the same.
AlgaNdef : AlgaNodeProxy {

	classvar <>defaultServer, <>all;
	var <>key;

	*initClass { all = () }

	*new { | key, object |
		// key may be simply a symbol, or an association of a symbol and a server name
		var res, server, dict;

		if(key.isKindOf(Association)) {
			server = Server.named.at(key.value);
			if(server.isNil) {
				Error("AlgaNdef(%): no server found with this name.".format(key)).throw
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
			dict = AlgaProxySpace.new(server); // use a proxyspace for ease of access.
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
ANdef : AlgaNdef {

}

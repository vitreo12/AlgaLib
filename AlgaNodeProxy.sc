//Types of AlgaNodeProxies, used like enum
AlgaNodeProxyType {}
AlgaNodeProxySynth : AlgaNodeProxyType {}
AlgaNodeProxyScalar : AlgaNodeProxyType {}
AlgaNodeProxyArray : AlgaNodeProxyType {}
AlgaNodeProxyPatternInstrument : AlgaNodeProxyType {}
AlgaNodeProxyPatternControl : AlgaNodeProxyType {}
AlgaNodeProxyPatternStream : AlgaNodeProxyType {}

AlgaNodeProxy : NodeProxy {

	classvar <>defaultAddAction = \addToTail;
	classvar <>defaultReshaping = \elastic;   //Use \elasitc as default. It's set in NodeProxy's init (super.init)

	//Which AlgaNodeProxyType
	var <>type;

	//The block index that contains this proxy
	var <>blockIndex = -1;

	var <>isInterpProxy = false;

	var <>instantiated = false;

	var <>connectionTime_inner = 0;

	var <>defaultControlNames;

	var <>inProxies, <>outProxies;

	var <>interpolationProxies, <>interpolationProxiesCopy;
	var <>interpolationProxiesNormalizer, <>interpolationProxiesNormalizerCopy;

	init {
		//These are the interpolated ones!!
		interpolationProxies = IdentityDictionary.new;

		//These are the one that normalize all the interpolationProxies
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

		if(fadeTime == 0, {fadeTime = this.fadeTime});

		//copy interpolationProxies in new IdentityDictionary in order to free them only
		//after everything has been freed already.
		//Also, remove block from AlgaBlocksDict.blocksDict
		if(isInterpolationProxy.not, {
			var blockWithThisProxy;

			//interpolationProxies.postln;

      //copy both the interpProxies and the interpNoamlizers
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

		if(fadeTime == 0, {fadeTime = this.fadeTime});

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

	queryInstantiation {
		var oscfunc = OSCFunc({
			arg msg;
			var numChildren = msg[3];
			if(numChildren > 0, {
				this.instantiated = true;
			});
		}, '/g_queryTree.reply', server.addr).oneShot;

		server.sendMsg("/g_queryTree", this.group.nodeID);

		SystemClock.sched(2, {
			if(this.instantiated.not, {
				oscfunc.free;
			})
		})
	}

	//This should be used to shush, NOT free ...
	stop { | fadeTime = 0, reset = false |
		if(fadeTime == 0, {fadeTime = this.fadeTime});
		super.stop(fadeTime, reset);
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

	setConnectionTime { |dur|
		if(dur.isNil) { this.set(\connectionTime, 0) } { this.set(\connectionTime, dur) };
		this.connectionTime_inner = dur;
		interpolationProxies.do({
			arg proxy;
			proxy.setConnectionTime(dur);
			proxy.fadeTime = dur;
		});
	}

	ct_ { | dur |
		this.setConnectionTime(dur);
	}

	ct {
		^this.connectionTime_inner;
	}

	connectionTime_ { | dur |
		this.setConnectionTime(dur);
	}

	connectionTime {
		^this.connectionTime_inner;
	}

	params {
		^this.interpolationProxies;
	}

	//Copied over from NodeProxy with added defaultControlNames
	putExtended { | index, obj, channelOffset = 0, extraArgs, now = true |
		var container, bundle, oldBus = bus;

		//Don't re-instantiate same function... Should also to the same for all the other cases!
		if((this.source.class == Function).and(obj.source.class == Function), {
			var thisFunDef = this.source.def;
			var objFunDef = obj.source.def;
			if(thisFunDef.sourceCode == objFunDef.sourceCode, {^this});
		});


		//Reset instantiation stage
		this.instantiated = false;

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

				//Instantiating a number OR array
				if((container.class == StreamControl), {
					if(container.source.size == 0, {
						this.type = AlgaNodeProxyScalar;
					}, {
						this.type = AlgaNodeProxyArray;
					});
				});

				//Instantiating a Synth or SynthDef:
				if((container.class == SynthControl).or(container.class == SynthDefControl), {

					//Synth or SynthDef -> AlgaNodeProxySynth
					this.type = AlgaNodeProxySynth;

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

				//Instantiating a Pattern:
				if(container.class == PatternControl, {
					var synthDesc;
					var foundInstrument = false;
					var foundFreq = false;
					var foundAmp = false;
					var foundDur = false;
					var durVal = 1.0;

					var patternType = container.source.class;

					if((patternType == Pbind).or(patternType == PbindProxy), {

						//Check the arguments provided: \instrument must always be provided
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

							if(val == \dur, {
								//out of bounds, provided \instrument without the synthdef name at the end of array
								if(index + 1 >= obj.patternpairs.size, {
									"\dur must be followed number or pattern".error;
								});

								durVal = obj.patternpairs[index + 1];
								foundDur = true;
							});

							if(val == \freq, { foundFreq = true; });
							if(val == \amp, { foundAmp = true; });
						});

						if(foundInstrument, {
							this.type = AlgaNodeProxyPatternInstrument;

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

									//Also, if \amp is not provided in Pbind's param and param == amp, use it as default
									if((foundAmp == false).and(controlNameName == \amp), {
										this.set(controlNameName, controlName.defaultValue);
									});
								});
							});
						}, {
							//no \instrument provided: it's a pbind that will be used to control
							//parameters  (it would need to implement a \val one though)
							this.type = AlgaNodeProxyPatternControl;
						});

						//Add dur / delta / stretch
						if(foundDur, {
							var durControlName = ControlName(\dur, -1, \control, durVal);

							//Add param to dict
							defaultControlNames.put(\dur, durControlName);

						}, {
							"Alga Pbinds must always provide a \dur".error;
						});
					}, {

						//All other pattern types!
						this.type = AlgaNodeProxyPatternStream;

					});
				});

				//create all interp proxies
				this.createAllInterpProxies;
			});

			//Standard Proxy init from here on

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
				this.recoverConnections;

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
						//("Restoring connection of " ++ outProxy.asString ++ " with " ++ this.asString).postln;
						//outProxy.synth_setInterpProxy(this, paramName, reorderBlock:false);
						this.forwardConnectionInner(outProxy, paramName, useInputFadeTime:true);
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
	play { | out, numChannels, group, multi=false, vol, fadeTime = 0, addAction |
		var bundle = MixedBundle.new;

		if(fadeTime == 0, {fadeTime = this.fadeTime});

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

		this.changed(\play, [out, numChannels, group, multi, vol, fadeTime, addAction]);
	}

	playN { | outs, amps, ins, vol, fadeTime, group, addAction |
		var bundle = MixedBundle.new;

		if(fadeTime == 0, {fadeTime = this.fadeTime});

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

			interpolationProxy.reshaping = defaultReshaping;
			interpolationProxyNormalizer.reshaping = defaultReshaping;

			//Default fadeTime: set according to connectionTime
			interpolationProxy.fadeTime = this.connectionTime;
			interpolationProxy.setConnectionTime(this.connectionTime);
			interpolationProxyNormalizer.fadeTime = 0;
			interpolationProxyNormalizer.setConnectionTime(0);

			//Add the new interpolation NodeProxy to interpolationProxies dict
			this.interpolationProxies.put(paramName, interpolationProxy);
			this.interpolationProxiesNormalizer.put(paramName, interpolationProxyNormalizer);

			//This is quite useless. interpolationProxies are kept in the appropriate dictionary of the proxy
			interpolationProxy.outProxies.put(paramName, this);

			//This routine stuff needs to be tested on Linux...
			//if(true, {
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

					//Wait for instantiation of interpProxy
					while(
						{(interpolationProxy.instantiated.not)}, {
							0.01.wait;
							"Waiting for interp proxy instantiation".warn;
							interpolationProxy.queryInstantiation;
					});

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

	forwardConnectionInner {
		arg nextProxy, param = \in, newlyCreatedInterpProxyNorm = false, useInputFadeTime = false;

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

		Routine.run({

			//Spinlock for instantiation of both proxies
			while(
				{(this.instantiated.not).or(nextProxy.instantiated.not)}, {
					0.01.wait;
					"Waiting for both proxies' instantiation".warn;
					this.queryInstantiation;
					nextProxy.queryInstantiation;
			});

			//AlgaNodeProxySynth -> AlgaNodeProxySynth OR
			//AlgaNodeProxyScalar/Array -> AlgaNodeProxySynth
			if(((nextProxy.type == AlgaNodeProxySynth).and(this.type == AlgaNodeProxySynth)).or(
				(nextProxy.type == AlgaNodeProxySynth).and((this.type == AlgaNodeProxyScalar).or(this.type == AlgaNodeProxyArray))), {
				//Create a new block if needed
				this.createNewBlockIfNeeded(nextProxy);
				nextProxy.synth_setInterpProxy(this, param,
					newlyCreatedInterpProxyNorm:newlyCreatedInterpProxyNorm,
					useInputFadeTime:useInputFadeTime
				);
			});

		});

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
			this.synth_setInterpProxy(nextProxy, param, newlyCreatedInterpProxyNorm:newlyCreatedInterpProxyNorm);
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
ANProxy : AlgaNodeProxy {}
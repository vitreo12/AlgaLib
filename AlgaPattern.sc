AlgaPattern : AlgaNode {
	/*
	Todos and questions:
	1) What about inNodes for an AlgaPattern?
	   Are these set only through direct mapping and ListPatterns (Pseq, etc..)?

	2) How to connect an AlgaNode to an AlgaPattern parameter? What about kr / ar?

	3) Can an AlgaNode connect to \dur? (For now, I'd say no)
	*/

	//The actual Pattern to be manipulated
	var <pattern;

	//The eventPairs used in the Pattern
	var <eventPairs;

	//Add the \algaNote event to Event
	*initClass {
		//StartUp.add is needed: Event class must be compiled first
		StartUp.add({
			this.addAlgaNoteEventType;
		});
	}

	//Doesn't have args and outsMapping like AlgaNode
	*new { | obj, connectionTime = 0, playTime = 0, server |
		^super.new(
			obj: obj,
			connectionTime: connectionTime,
			playTime: playTime,
			server: server
		)
	}

	//Add the \algaNote event type
	*addAlgaNoteEventType {
		Event.addEventType(\algaNote, #{
			//The final OSC bundle
			var bundle;

			//The name of the SynthDef
			var synthDefName = ~synthDefName.valueEnvir;

			//AlgaPattern related stuff. Passed in from Pattern
			var server = ~algaServer;
			var clock = ~algaClock;
			var controlNames = ~algaControlNames;
			var synthBusIndex = ~algaSynthBusIndex;
			var synthGroup = ~algaSynthGroup;
			var interpGroup = ~algaInterpGroup;

			//Other things for pattern syncing / clocking / scheduling
			var offset = ~timingOffset;
			var lag = ~lag;

			//Needed ?
			~isPlaying = true;

			//Create all Synths and pack the bundle
			bundle = server.makeBundle(false, {
				//These will be populated and freed when the patternSynth is released
				var interpBussesAndSynths = IdentityDictionary(controlNames.size);

				var synthArgs = [
					\gate, 1,
					\out, synthBusIndex
				];

				var patternSynth;

				//As of now, multi channel expansion doesn't work unless
				//the parameter implements it. Implement it in the future
				//with specific rules
				controlNames.do({ | controlName |
					var paramName = controlName.name;
					var paramNumChannels = controlName.numChannels;
					var paramRate = controlName.rate;

					//get param value from the \algaNote Event context (~freq, ~amp, etc..)
					var paramVal = ("~" ++ paramName.asString).compile.value;

					var senderNumChannels, senderRate;
					var interpBus, interpSymbol, interpSynthArgs, interpSynth;

					if(paramVal.isNumberOrArray, {
						//num / array
						if(paramVal.isSequenceableCollection, {
							//an array
							senderNumChannels = paramVal.size;
							senderRate = "control";
						}, {
							//a num
							senderNumChannels = 1;
							senderRate = "control";
						});
					}, {
						if(paramVal.isAlgaNode, {
							//AlgaNode
							if(paramVal.instantiated, {
								//if instantiated, use the rate, numchannels and bus arg from the alga bus
								senderRate = paramVal.rate;
								senderNumChannels = paramVal.numChannels;
								paramVal = paramVal.synthBus.busArg;
							}, {
								//otherwise, use default
								senderRate = "control";
								senderNumChannels = paramNumChannels;
								paramVal = controlName.defaultValue;
								("AlgaNode wasn't instantiated yet. Using default value for " ++ paramName).warn;
							});
						}, {
							("AlgaPattern: Invalid parameter " ++ paramName.asString).error;
						});
					});

					interpSymbol = (
						"alga_interp_" ++
						senderRate ++
						senderNumChannels ++
						"_" ++
						paramRate ++
						paramNumChannels
					).asSymbol;

					//Remember: interp busses must always have one extra channel for
					//the interp envelope, even if the envelope is not used here (no normSynth)
					//Otherwise, the envelope will write to other busses!
					//Here, patternSynth, won't even look at that extra channel, but at least
					//SuperCollider knows it's been written to.
					//This, in fact, caused a huge bug when playing an AlgaPattern through
					//other AlgaNodes!
					interpBus = AlgaBus(server, paramNumChannels + 1, paramRate);

					interpSynthArgs = [
						\in, paramVal,
						\out, interpBus.index,
						\fadeTime, 0
					];

					interpSynth = AlgaSynth(
						interpSymbol,
						interpSynthArgs,
						interpGroup,
						waitForInst: false
					);

					//add interpBus and interpSynth to interpBussesAndSynths
					interpBussesAndSynths[interpBus] = interpSynth;

					//add \paramName, interpBus.busArg to synth arguments
					synthArgs = synthArgs.add(paramName).add(interpBus.busArg);
				});

				//Specify an fx?
				//fx: (def: Pseq([\delay, \tanh]), delayTime: Pseq([0.2, 0.4]))
				//Things to do:
				// 1) Check the def exists
				// 2) Check it has an \in parameter
				// 3) Check it can free itself (like with DetectSilence).
				//    If not, it will be freed with interpSynths
				// 4) Route synth's audio through it

				//Connect to specific nodes? It would just connect to nodes with \in param.
				//What about mixing? Is mixing the default behaviour for this? (yes!!)
				//out: (node: Pseq([a, b])

				//This synth writes directly to synthBus
				patternSynth = AlgaSynth(
					synthDefName,
					synthArgs,
					synthGroup,
					waitForInst: false
				);

				//Free all interpBusses and interpSynths on patternSynth's release
				OSCFunc.newMatching({ | msg |
					interpBussesAndSynths.keysValuesDo({ | interpBus, interpSynth |
						interpSynth.free;
						interpBus.free;
					});
				}, '/n_end', server.addr, argTemplate:[patternSynth.nodeID]).oneShot;
			});

			//Send bundle to server using the same AlgaScheduler's clock.
			//Should this be moved to the AlgaScheduler altogether?
			schedBundleArrayOnClock(
				offset,
				clock,
				bundle,
				lag,
				server
			);
		});
	}

	//dispatchNode: first argument is an Event
	dispatchNode { | obj, args, initGroups = false, replace = false,
		keepChannelsMapping = false, outsMapping, keepScale = false |

		//def: entry
		var defEntry = obj[\def];

		if(defEntry == nil, {
			"AlgaPattern: no 'def' entry in the Event".error;
			^this;
		});

		//Store initial eventPairs
		eventPairs = obj;

		//Store class of the synthEntry
		objClass = defEntry.class;

		//If there is a synth playing, set its instantiated status to false:
		//this is mostly needed for .replace to work properly and wait for the new synth
		//to be instantiated!
		if(synth != nil, { synth.instantiated = false });

		//Symbol
		if(objClass == Symbol, {
			this.dispatchSynthDef(defEntry, initGroups, replace,
				keepChannelsMapping:keepChannelsMapping,
				keepScale:keepScale
			);
		}, {
			//Function
			if(objClass == Function, {
				this.dispatchFunction;
			}, {
				//ListPattern (Pseq, Pser, Prand...)
				if(objClass.superclass == ListPattern, {
					this.dispatchListPattern;
				}, {
					("AlgaPattern: class '" ++ objClass ++ "' is invalid").error;
				});
			});
		});
	}

	//Overloaded function
	buildFromSynthDef { | initGroups = false, replace = false,
		keepChannelsMapping = false, keepScale = false |

		//Retrieve controlNames from SynthDesc
		var synthDescControlNames = synthDef.asSynthDesc.controls;
		this.createControlNamesAndParamsConnectionTime(synthDescControlNames);

		numChannels = synthDef.numChannels;
		rate = synthDef.rate;

		//Detect if SynthDef can be freed automatically. Otherwise, error!
		if(synthDef.canFreeSynth.not, {
			("AlgaPattern: AlgaSynthDef '" ++ synthDef.name.asString ++ "' can't free itself: it doesn't implement any DoneAction.").error;
			^this
		});

		//Generate outsMapping (for outsMapping connectinons)
		this.calculateOutsMapping(replace, keepChannelsMapping);

		//Create groups if needed
		if(initGroups, { this.createAllGroups });

		//Create busses
		this.createAllBusses;

		//Create the actual pattern
		this.createPattern(eventPairs);

	}

	//Support Function in the future
	dispatchFunction {
		"AlgaPattern: Functions are not supported yet".error;
	}

	//Support multiple SynthDefs in the future,
	//only if expressed with ListPattern subclasses (like Pseq, Prand, etc...)
	dispatchListPattern {
		"AlgaPattern: ListPatterns are not supported yet".error;
	}

	//Build the actual pattern
	createPattern {
		var foundDurOrDelta = false;
		var patternPairs = Array.newClear(0);

		//Turn every Pattern entry into a Stream
		eventPairs.keysValuesDo({ | paramName, value |
			if((paramName == \dur).or(paramName == \delta), {
				foundDurOrDelta = true;
			});

			if(paramName != \def, {
				//Behaviour for keys != \def
				var valueAsStream = value.asStream;

				//delta == dur
				if(paramName == \delta, {
					paramName = \dur;
				});

				//Update eventPairs with the Stream
				eventPairs[paramName] = valueAsStream;

				//Use Pfuncn on the Stream for parameters
				patternPairs = patternPairs.add(paramName).add(
					Pfuncn( { eventPairs[paramName].next }, inf )
				);
			}, {
				//Add \def key as \instrument
				patternPairs = patternPairs.add(\instrument).add(value);
			});
		});

		//If no dur or delta, default to 1
		if(foundDurOrDelta.not, {
			patternPairs = patternPairs.add(\dur).add(1)
		});

		//Add all the default entries from SynthDef that the user hasn't set yet
		controlNames.do({ | controlName |
			var paramName = controlName.name;

			//if not set explicitly yet
			if(eventPairs[paramName] == nil, {
				var paramDefault = this.getDefaultOrArg(controlName, paramName);

				//Update eventPairs with the Stream
				eventPairs[paramName] = paramDefault.asStream;

				//Use Pfuncn on the Stream for parameters
				patternPairs = patternPairs.add(paramName).add(
					Pfuncn( { eventPairs[paramName].next }, inf )
				);
			});
		});

		//Add \type, \algaNode, and all things related to
		//the context of this AlgaPattern
		patternPairs = patternPairs.addAll([
			\type, \algaNote,
			\algaServer, this.server,
			\algaClock, this.scheduler.clock,
			\algaControlNames, this.controlNames,
			\algaSynthBusIndex, this.synthBus.index,
			\algaSynthGroup, this.synthGroup,
			\algaInterpGroup, this.interpGroup
		]);

		//Create the Pattern by calling .next from the streams
		pattern = Pbind(*patternPairs);

		//start the pattern right away
		pattern.play;
	}

	//the interpolation function for AlgaPattern << Pattern / Number / Array
	interpPattern { | param = \in, sender, time = 0, curves = \lin |
		var interpSeg;
		var eventPairAtParam;
		var paramConnectionTime = paramsConnectionTime[param];

		//delta == dur
		if(param == \delta, {
			param = \dur
		});

		if(paramConnectionTime == nil, { paramConnectionTime = connectionTime });
		if(paramConnectionTime < 0, { paramConnectionTime = connectionTime });
		time = time ? paramConnectionTime;

		//Just \lin for now (just like the other interps)
		interpSeg = Pseg([0, 1, 1], [time, inf], curves);

		//retrieve it here so it also applies to delta == dur
		eventPairAtParam = eventPairs[param];

		//Run interp by replacing the entry directly, using the .blend function
		if(eventPairAtParam != nil, {
			//Direct replacing in the IdentityDictionary. This is what
			//is indexed and played by the Pattern.
			eventPairs[param] = (
				eventPairAtParam.blend(sender, interpSeg)
			).asStream;
		});
	}

	//the interpolation function for AlgaPattern << AlgaNode
	interpAlgaNode { | param = \in, sender, time = 0, curves = \lin |
		"AlgaPattern: interpAlgaNode not implemented yet".error;
	}

	//the interpolation function for AlgaPattern << ListPattern
	//this is separated from interpPattern because there's the need to check
	//for the presence of AlgaNodes in the list, to run specific interpolation magic
	interpListPattern { | param = \in, sender, time = 0, curves = \lin |
		var containsAlgaNodes = false;

		//Check if it's a ListPattern that contains AlgaNodes
		sender.list.do({ | element |
			if(element.isAlgaNode, {
				containsAlgaNodes = true
			});
		});

		if(containsAlgaNodes, {
			"AlgaPattern: interpListPattern with AlgaNodes not implemented yet".error;
		}, {
			^this.interpPattern(param, sender, time, curves);
		});
	}

	//<<, <<+ and <|
	makeConnection { | param = \in, sender, time = 0, curves = \lin |
		case
		{ sender.isAlgaNode } {
			// <<.param AlgaNode
			// <<+.param AlgaNode (not yet)
			^this.interpAlgaNode(param, sender, time, curves);
		}
		{ sender.isListPattern } {
			// <<.param ListPattern
			// <<+.param ListPattern (not yet)
			^this.interpListPattern(param, sender, time, curves);
		}
		{ (sender.isPattern.not).and(sender.isNumberOrArray.not) } {
			"AlgaPattern: interpPattern only works with Patterns, Numbers and Arrays".error;
			^this;
		};

		// <<.param Pattern
		// <<+.param Pattern (not yet)
		this.interpPattern(param, sender, time, curves);
	}

	// <<| \param (goes back to defaults)
	//previousSender is the mix one, in case that will be implemented in the future
	resetParam { | param = \in, previousSender = nil, time |

	}

	//replace entries.
	// options:
	// 1) replace the entire AlgaPattern with a new one (like AlgaNode.replace)
	// 2) replace just the SynthDef with either a new SynthDef or a ListPattern with JUST SynthDefs.
	//    This would be equivalent to <<.def \newSynthDef
	//    OR <<.def Pseq([\newSynthDef1, \newSynthDef2])
	replace { | obj, connectionTime = 0, playTime = 0, server |

	}

	//Don't support <<+ for now
	mixFrom { | sender, param = \in, inChans, scale, time |
		"AlgaPattern: mixFrom is not supported yet".error;
	}

	//Don't support >>+ for now
	mixTo { | receiver, param = \in, outChans, scale, time |
		"AlgaPattern: mixTo is not supported yet".error;
	}

	//yas
	isAlgaPattern { ^true }

	//Since I can't check each synth, just check if the necessary groups have been allocated
	instantiated {
		^(group.instantiated.and(
			interpGroup.instantiated.and(
				synthGroup.instantiated.and(
					playGroup.instantiated
				)
			)
		))
	}
}

//Alias
AP : AlgaPattern {}

//Implements Pmono behaviour
AlgaMonoPattern : AlgaPattern {}

//Alias
AMP : AlgaMonoPattern {}

/*
+Object {
blend { | that, blendFrac = 0.5 |
if(this.isAlgaNode, {
^(this.synthBus.bus.getSynchronous) + (blendFrac * (that - this.synthBus.bus.getSynchronous));
}, {
if(that.isAlgaNode, {
^(this + (blendFrac * (that.synthBus.bus.getSynchronous - this)));
});
});

//Standard behaviour
^this + (blendFrac * (that - this));
}
}
*/
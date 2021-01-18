AlgaPatternInterpState {
	var <entry;
	var <state = false;

	*new { | entry |
		^super.new.init(entry)
	}

	init { | argEntry |
		entry = argEntry;
	}

	//Triggered interpolation synth
	setStateFlag {
		state = true;
	}

	print {
		("entry: " ++ entry.asString).postln;
		("state: " ++ state.asString).postln;
	}
}

//Keeps track of active Patterns / Nodes per param (used for interp)
AlgaPatternInterpStates {
	var <entries;
	var <times;

	*new {
		^super.new.init;
	}

	init {
		entries = IdentityDictionary();
		times   = IdentityDictionary();
	}

	add { | param = \in, entry, time = 0 |
		var prevEntry = entries[param];

		if(entry.isPattern, {
			entry = \pattern;
		});

		if(prevEntry == nil, {
			entries[param] = IdentitySet().add(
				AlgaPatternInterpState(entry)
			);
		}, {
			entries[param].add(
				AlgaPatternInterpState(entry)
			);
		});

		times[param] = time;
	}

	setStateFlag { | param = \in |
		entries[param].do({ | entry |
			entry.setStateFlag
		})
	}

	remove { | param = \in |
		entries.removeAt(param);
		times.removeAt(param);
	}

	removeFromIdentitySet { | param = \in, what |
		entries[param].remove(what);
	}

	print { | param = \in |
		("param : " ++ param).warn;
		entries[param].do({ | entry | entry.print });
	}
}

AlgaPattern : AlgaNode {
	/*
	Todos and questions:
	1) What about inNodes for an AlgaPattern?
	   Are these set only through direct mapping and ListPatterns (Pseq, etc..)?

	2) How to connect an AlgaNode to an AlgaPattern parameter? What about kr / ar?

	3) Can an AlgaNode connect to \dur? Only if it's \control rate (using AlgaPkr)

	4) Continuous or SAH interpolation (both in Patterns and AlgaNodes)

	5) \dur implementation: doesn't work cause it's not time accurate: there's no way
	   of syncing multiple patterns, as the interpolation process with Pseg will end up out
	   of phase. Right now, \dur just sets the requested value AFTER fadeTime.
	*/

	//The actual Pattern to be manipulated
	var <pattern;

	//The ReschedulingEventStreamPlayer
	var <reschedulingEventStreamPlayer;

	//Dict of per-param AlgaPatternInterpState
	var <>interpStates;

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
	*new { | obj, connectionTime = 0, playTime = 0, server, sched = 0 |
		^super.new(
			obj: obj,
			connectionTime: connectionTime,
			playTime: playTime,
			server: server,
			sched: sched
		);
	}

	//Add the \algaNote event type
	*addAlgaNoteEventType {
		Event.addEventType(\algaNote, #{
			//The final OSC bundle
			var bundle;

			//AlgaPattern and its server / clock
			var algaPattern = ~algaPattern;
			var algaPatternServer = ~algaPatternServer;
			var algaPatternClock = ~algaPatternClock;

			//Other things for pattern syncing / clocking / scheduling
			var offset = ~timingOffset;
			var lag = ~lag;

			//Needed ?
			~isPlaying = true;

			//Create the bundle with all needed Synths for this Event.
			bundle = algaPatternServer.makeBundle(false, {
				//Pass the Event's environment (where all the values coming from pattern exist)
				//This function will also take care of Pattern / AlgaNode interpolations
				~algaPattern.createEventSynths(
					currentEnvironment
				)
			});

			//Send bundle to server using the same server / clock as the AlgaPattern
			//( AlgaScheduler ??? )
			schedBundleArrayOnClock(
				offset,
				algaPatternClock,
				bundle,
				lag,
				algaPatternServer
			);
		});
	}

	//Create one Synth that writes to interpBus
	createInterpPatternSynth { | paramName, paramNumChannels, paramRate,
		paramDefault, interpBus, eventEnvironment, interpBussesAndSynths |

		var validParam = true;

		//The value coming from eihter a Pattern or ListPattern
		// ( ~freq, ~amp ... )
		var paramVal = eventEnvironment[paramName];

		var senderNumChannels, senderRate;

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
					paramVal = paramDefault;
					("AlgaPattern: AlgaNode wasn't instantiated yet. Using default value for " ++ paramName).warn;
				});
			}, {
				("AlgaPattern: Invalid parameter " ++ paramName.asString).error;
				validParam = false;
			});
		});

		if(validParam, {
			var interpSymbol = (
				"alga_interp_" ++
				senderRate ++
				senderNumChannels ++
				"_" ++
				paramRate ++
				paramNumChannels
			).asSymbol;

			var interpSynthArgs = [
				\in, paramVal,
				\out, interpBus.index,
				\fadeTime, 0
			];

			var interpSynth = AlgaSynth(
				interpSymbol,
				interpSynthArgs,
				interpGroup,
				waitForInst: false
			);

			//add interpSynth to interpBussesAndSynths
			interpBussesAndSynths.add(interpSynth);
		});

		^nil;
	}

	//Create all needed Synths for this Event. This is triggered by the \algaNote Event
	createEventSynths { | eventEnvironment |
		//The SynthDef ( ~synthDefName in Event )
		var synthDef = eventEnvironment[\synthDefName].valueEnvir;

		//These will be populated and freed when the patternSynth is released
		var interpBussesAndSynths = IdentitySet(controlNames.size * 2);

		//args to patternSynth
		var patternSynthArgs = [
			\gate, 1,
			\out, synthBus.index
		];

		//The actual synth that will be created
		var patternSynth;

		//Loop over controlNames and create as many Synths as needed,
		//also considering interpolation issues
		controlNames.do({ | controlName |
			var paramName = controlName.name;
			var paramNumChannels = controlName.numChannels;
			var paramRate = controlName.rate;
			var paramDefault = controlName.defaultValue;

			//Remember: interp busses must always have one extra channel for
			//the interp envelope, even if the envelope is not used here (no normSynth)
			//Otherwise, the envelope will write to other busses!
			//Here, patternSynth, won't even look at that extra channel, but at least
			//SuperCollider knows it's been written to.
			//This, in fact, caused a huge bug when playing an AlgaPattern through
			//other AlgaNodes!
			var interpBus = AlgaBus(server, paramNumChannels + 1, paramRate);

			//create synth
			this.createInterpPatternSynth(
				paramName, paramNumChannels, paramRate,
				paramDefault, interpBus, eventEnvironment, interpBussesAndSynths
			);

			//add interpBus to interpBussesAndSynths
			interpBussesAndSynths.add(interpBus);

			//append \paramName, interpBus to patternSynthArgs for correct connections
			patternSynthArgs = patternSynthArgs.add(paramName).add(interpBus.busArg);
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
			synthDef,
			patternSynthArgs,
			synthGroup,
			waitForInst: false
		);

		//Free all interpBusses and interpSynths on patternSynth's release
		OSCFunc.newMatching({ | msg |
			interpBussesAndSynths.do({ | entry |
				//.free works both for AlgaSynths and AlgaBusses
				entry.free;
			});
		}, '/n_end', server.addr, argTemplate:[patternSynth.nodeID]).oneShot;
	}

	//dispatchNode: first argument is an Event
	dispatchNode { | obj, args, initGroups = false, replace = false,
		keepChannelsMapping = false, outsMapping, keepScale = false, sched = 0 |

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
				keepScale:keepScale,
				sched:sched
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
		keepChannelsMapping = false, keepScale = false, sched = 0 |

		//Retrieve controlNames from SynthDesc
		var synthDescControlNames = synthDef.asSynthDesc.controls;
		this.createControlNamesAndParamsConnectionTime(synthDescControlNames);

		numChannels = synthDef.numChannels;
		rate = synthDef.rate;

		sched = sched ? 0;

		//Detect if AlgaSynthDef can be freed automatically. Otherwise, error!
		if(synthDef.explicitFree.not, {
			("AlgaPattern: AlgaSynthDef '" ++ synthDef.name.asString ++ "' can't free itself: it doesn't implement any DoneAction.").error;
			^this
		});

		//Generate outsMapping (for outsMapping connectinons)
		this.calculateOutsMapping(replace, keepChannelsMapping);

		//Create groups if needed
		if(initGroups, { this.createAllGroups });

		//Create busses
		this.createAllBusses;

		//Create the actual pattern, pushing to scheduler ???
		scheduler.addAction(func: {
			this.createPattern;
		}, sched: sched);
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

	//Set current interp state
	setInterpState { | param = \in, sender, time = 0 |
		interpStates.add(param, sender, time);
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
				var paramConnectionTime = paramsConnectionTime[paramName];

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

				if(paramConnectionTime == nil, { paramConnectionTime = connectionTime });
				if(paramConnectionTime < 0, { paramConnectionTime = connectionTime });

				//Not .asStream
				this.setInterpState(paramName, value, paramConnectionTime);
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
				var paramDefaultAsStream = paramDefault.asStream;
				var paramConnectionTime = paramsConnectionTime[paramName];

				//Update eventPairs with the Stream
				eventPairs[paramName] = paramDefaultAsStream;

				//Use Pfuncn on the Stream for parameters
				patternPairs = patternPairs.add(paramName).add(
					Pfuncn( { eventPairs[paramName].next }, inf )
				);

				if(paramConnectionTime == nil, { paramConnectionTime = connectionTime });
				if(paramConnectionTime < 0, { paramConnectionTime = connectionTime });

				//Not .asStream
				this.setInterpState(paramName, paramDefault, paramConnectionTime);
			});
		});

		//Add \type, \algaNode, and all things related to
		//the context of this AlgaPattern
		patternPairs = patternPairs.addAll([
			\type, \algaNote,
			\algaPattern, this,
			\algaPatternServer, server,
			\algaPatternClock, this.clock
		]);

		//Create the Pattern by calling .next from the streams
		pattern = Pbind(*patternPairs);

		//start the pattern right away. quant?
		reschedulingEventStreamPlayer = pattern.playRescheduling(
			clock: this.clock
		);
	}

	//the interpolation function for AlgaPattern << Pattern / Number / Array
	interpPattern { | param = \in, sender, time = 0, scale, curves = \lin |
		var eventPairAtParam;

		// \dur doesn't interpolate well for now
		if(param == \dur, {
			var clock = this.clock;

			if(clock.isTempoClock, {
				("AlgaPattern: interpolating \dur is still WIP. Changes will be applied after " ++ time ++ " beats").warn;
			}, {
				("AlgaPattern: interpolating \dur is still WIP. Changes will be applied after " ++ time ++ " seconds").warn;
			});

			//Overwrite \dur with sender after time
			^clock.algaSchedAtQuant(
				time,
				{ eventPairs[\dur] = sender.asStream }
			);
		});

		//retrieve it here so it also applies to delta == dur
		eventPairAtParam = eventPairs[param];

		//Run interp by replacing the entry directly, using the .blend function
		if(eventPairAtParam != nil, {
			//Just \lin for now (just like the other interps)
			var interpSeg = Pseg([0, 1, 1], [time, inf], curves);

			//calculate the scaling
			var scaling = this.calculateScaling(param, sender, nil, scale);

			if(scaling != nil, {
				//linear (0)..there should be the option for curve
				var scaleCurve = 0;

				case

				//One scale value (number)
				{ scaling.size == 2 } {
					sender = sender * scaling[1];
				}

				//Two scale values (-1.0 / 1.0 / highMin / highMax)
				{ scaling.size == 6 } {
					sender = sender.lincurve(
						-1.0, 1.0, scaling[1], scaling[3], scaleCurve
					);
				}

				//Four scale values (lowMin / lowMax / highMin / highMax)
				{ scaling.size == 10 } {
					sender = sender.lincurve(
						scaling[1], scaling[3], scaling[5], scaling[7], scaleCurve
					);
				};
			});

			//Set correct interpState
			this.setInterpState(param, sender, time);

			//Direct replacing in the IdentityDictionary.
			//This is what is indexed and played by the Pattern!
			eventPairs[param] = (
				eventPairAtParam.blend(sender, interpSeg)
			).asStream;

		}, {
			("AlgaPattern: invalid param: " ++ param.asString).error;
		});
	}

	//the interpolation function for AlgaPattern << AlgaNode
	interpAlgaNode { | param = \in, sender, time = 0, scale, curves = \lin |
		var correctConnection = true;

		if(param == \dur, {
			//Check if \dur and \control: use AlgaPkr...
			//AlgaPkr can also be used for another mode where control AlgaNode
			//are effectively sampled and held at the trigger of the event
			if(sender.rate == \control, {
				this.interpPattern(param, AlgaPkr(sender, 0.001), time, scale, curves);
			}, {
				"AlgaPattern: interpAlgaNode can't modulate 'dur' at audio rate".error;
				correctConnection = false;
			});
		}, {
			//Connecting to anything else than \dur

		});

		//Ok, good connections. Go on with adding inNodes / outNodes
		//and setting block node ordering
		if(correctConnection, {
			//Add inNodes / outNodes
			this.addInOutNodesDict(sender, param, false);

			//Set correct interpState
			this.setInterpState(param, sender, time);

			//Figure out AlgaBlocks too

		});
	}

	//the interpolation function for AlgaPattern << ListPattern
	//this is separated from interpPattern because there's the need to check
	//for the presence of AlgaNodes in the list, to run specific interpolation magic
	interpListPattern { | param = \in, sender, time = 0, scale, curves = \lin |
		var containsAlgaNodes = false;

		//Check if it's a ListPattern that contains AlgaNodes
		sender.list.do({ | element |
			if(element.isAlgaNode, {
				containsAlgaNodes = true
			});
		});

		if(containsAlgaNodes, {
			"AlgaPattern: interpListPattern with AlgaNodes isn't implemented yet".error;
		}, {
			^this.interpPattern(param, sender, time, scale, curves);
		});
	}

	//<<, <<+ and <|
	makeConnectionInner { | param = \in, sender, time = 0, scale, curves = \lin |
		var paramConnectionTime = paramsConnectionTime[param];
		if(paramConnectionTime == nil, { paramConnectionTime = connectionTime });
		if(paramConnectionTime < 0, { paramConnectionTime = connectionTime });
		time = time ? paramConnectionTime;

		//delta == dur
		if(param == \delta, {
			param = \dur
		});

		case
		{ sender.isAlgaNode } {
			// <<.param AlgaNode
			// <<+.param AlgaNode (not yet)
			^this.interpAlgaNode(param, sender, time, scale, curves);
		}

		{ sender.isListPattern } {
			// <<.param ListPattern
			// <<+.param ListPattern (not yet)
			^this.interpListPattern(param, sender, time, scale, curves);
		}

		{ (sender.isPattern.not).and(sender.isNumberOrArray.not) } {
			"AlgaPattern: makeConnection only works with AlgaNodes, Patterns, Numbers and Arrays".error;
			^this;
		};

		// <<.param Pattern
		// <<+.param Pattern (not yet)
		this.interpPattern(param, sender, time, scale, curves);
	}

	//<<, <<+ and <|
	makeConnection { | param = \in, sender, time = 0, scale, curves = \lin |
		if(this.cleared.not.and(sender.cleared.not).and(sender.toBeCleared.not), {
			scheduler.addAction({ (this.instantiated).and(sender.instantiated) }, {
				this.makeConnectionInner(param, sender, time, scale, curves)
			})
		});
	}

	//stop and reschedule in the future
	reschedule { | sched = 0 |
		reschedulingEventStreamPlayer.reschedule(sched);
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
	replaceInner { | obj, time, keepChannelsMappingIn = true, keepChannelsMappingOut = true,
		keepInScale = true, keepOutScale = true |

	}

	replace { | obj, time, keepChannelsMappingIn = true, keepChannelsMappingOut = true,
		keepInScale = true, keepOutScale = true |

	}

	//Don't support <<+ for now
	mixFrom { | sender, param = \in, inChans, scale, time |
		"AlgaPattern: mixFrom is not supported yet".error;
	}

	//Don't support >>+ for now
	mixTo { | receiver, param = \in, outChans, scale, time |
		"AlgaPattern: mixTo is not supported yet".error;
	}

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
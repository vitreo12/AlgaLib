AlgaPattern : AlgaNode {
	//The building eventPairs
	var <eventPairs;

	//The actual Pattern to be manipulated
	var <pattern;

	var <>patternInstantiated = false;

	//Add the \algaNote event to Event
	*initClass {
		StartUp.add({ //StartUp.add is needed
			Event.addEventType(\algaNote, #{
				var bundle;

				var offset = ~timingOffset;
				var lag = ~lag;

				//AlgaPattern related stuff
				var server = ~algaPattern.server;
				var clock = ~algaPattern.algaScheduler.clock;
				var controlNames = ~algaPattern.controlNames;
				var synthGroup = ~algaPattern.synthGroup;
				var interpGroup = ~algaPattern.interpGroup;

				//this is a function that generates the array of args for the synth in the form:
				//[\freq, ~freq, \amp, ~amp]
				var msgFunc = ~getMsgFunc.valueEnvir;

				//The name of the synthdef
				var instrumentName = ~synthDefName.valueEnvir;

				//The name of the fxDef
				var fx = ~fx;

				//Retrieved from the synthdef
				var sendGate = ~sendGate ? ~hasGate;

				//Needed for Pattern syncing
				~isPlaying = true;

				//Create all Synths and pack the bundle
				bundle = server.makeBundle(false, {
					var interpBussesAndSynths = IdentityDictionary(controlNames.size);
					var synthArgs = [\gate, 1];
					var synth;

					//As of now, multi channel expansion doesn't work unless the parameter implements it
					//Implement it in the future
					controlNames.do({ | controlName |
						var paramName = controlName.name;
						var paramRate = controlName.rate;
						var paramNumChannels = controlName.numChannels;
						var paramVal = ("~" ++ paramName.asString).compile.value; //get param value from Event context

						var interpBus;

						var senderRate;
						var senderNumChannels;
						var interpSymbol, interpSynthArgs, interpSynth;

						if(paramVal.isNumberOrArray, {
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
							//Check if it's an algaNode
							if(paramVal.isAlgaNode, {
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

						interpBus = AlgaBus(server, paramNumChannels, paramRate);

						interpSynthArgs = [
							\in, paramVal,
							\out, interpBus.index,
							\fadeTime, 0
						];

						interpSynth = AlgaSynth(
							interpSymbol,
							interpSynthArgs,
							interpGroup
						);

						//add interpBus and interpSynth
						interpBussesAndSynths[interpBus] = interpSynth;

						//add paramName, interpBus to synth arguments
						synthArgs = synthArgs.add(paramName).add(interpBus.busArg);
					});

					//Specified an fx
					//fx: (def: Pseq([\delay, \tanh]), dt: Pseq([0.2, 0.4]))
					//Things to do:
					// 1) Check the def exists
					// 2) Check it has an \in parameter
					// 3) Check it can free itself (like with DetectSilence).
					//    If not, it will be freed with interpSynths
					// 4) Route synth's audio through it

					//Connect to specific nodes? It would just with nodes with \in param.
					//Mixing?
					//out: (node: Pseq([a, b])

					synth = AlgaSynth(
						instrumentName,
						synthArgs,
						synthGroup
					);

					//Free all interpBusses and interpSynths on synth's release
					OSCFunc.newMatching({ | msg |
						interpBussesAndSynths.keysValuesDo({ | interpBus, interpSynth |
							interpBus.free;
							interpSynth.free;
						});
					}, '/n_end', server.addr, argTemplate:[synth.nodeID]).oneShot;

					//Consider instantiated to be true at this point
					~algaPattern.patternInstantiated = true;
				});

				//bundle.postln;

				//Send bundle to server using the same AlgaScheduler's clock
				schedBundleArrayOnClock(
					offset,
					clock,
					bundle,
					lag,
					server
				);
			});
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
				//ListPattern
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

		//Generate outs (for outsMapping connectinons)
		this.calculateOuts(replace, keepChannelsMapping);

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
		var patternPairs = Array.newClear(0);

		//Turn every Pattern entry into a Stream
		eventPairs.keysValuesDo({ | paramName, value |
			if(paramName != \def, {
				//Behaviour for keys != \def
				var valueAsStream = value.asStream;

				//Update eventPairs with the Stream
				eventPairs[paramName] = valueAsStream;

				//Use Pfuncn on the Stream for parameters
				patternPairs = patternPairs.addAll([
					paramName,
					Pfuncn( { eventPairs[paramName].next }, inf)
				]);
			}, {
				//Add \def key as \instrument
				patternPairs = patternPairs.addAll([
					\instrument,
					value
				]);
			});
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
				patternPairs = patternPairs.addAll([
					paramName,
					Pfuncn( { eventPairs[paramName].next }, inf)
				]);
			});
		});

		//Add \type, \algaNode, \algaPattern, \this and \out, synthBus.index
		patternPairs = patternPairs.addAll([
			\type, \algaNote,
			\algaPattern, this,
			\out, synthBus.index
		]);

		patternPairs.postln;

		//Create the Pattern by calling .next from the streams
		pattern = Pbind(*patternPairs);

		//start the pattern right away
		pattern.play;
	}

	//the interpolation function for AlgaPattern << Pattern
	interpPattern { | param = \in, sender, time = 0 |
		var interpSeg;

		var paramConnectionTime = paramsConnectionTime[param];
		if(paramConnectionTime == nil, { paramConnectionTime = connectionTime });
		if(paramConnectionTime < 0, { paramConnectionTime = connectionTime });

		time = time ? paramConnectionTime;

		if((sender.isPattern.not).and(sender.isNumberOrArray.not), {
			"AlgaPattern: interpPattern only works with Patterns, Numbers and Arrays".error;
		});

		//Just \lin for now (just like the other interps)
		interpSeg = Pseg([0, 1, 1], [time, inf], \lin);

		//Run interp by replacing the entry directly
		if(eventPairs[param] != nil, {
			eventPairs[param] = (eventPairs[param].blend(sender, interpSeg)).asStream;
		});
	}

	makeConnection {
		//This must add support for Patterns as args for <<, <<+ and <|
	}

	replace {
		//Basically, replace the SynthDef used, or the ListPattern
	}

	isAlgaPattern { ^true }

	instantiated { ^patternInstantiated }
}

AP : AlgaPattern {}

//Implements Pmono behaviour
AlgaMonoPattern : AlgaPattern {}
AlgaPattern : AlgaNode {
	//The building eventPairs
	var <eventPairs;

	//The actual Pattern to be manipulated
	var <pattern;

	//Add the \algaNote event to Event
	*initClass {
		StartUp.add({ //StartUp.add is needed
			Event.addEventType(\algaNote, #{
				var bundle;
				var offset = ~timingOffset;
				var lag = ~lag;
				var addAction = Node.actionNumberFor(~addAction);
				var server = ~algaPattern.server;
				var clock = ~algaPattern.algaScheduler.clock;

				//this is a function that generates the array of args for the synth in the form:
				//[\freq, ~freq, \amp, ~amp]
				var msgFunc = ~getMsgFunc.valueEnvir;

				//The name of the synthdef
				var instrumentName = ~synthDefName.valueEnvir;

				//Retrieved from the synthdef
				var sendGate = ~sendGate ? ~hasGate;

				//msgFunc.def.sourceCode.postln;
				//instrumentName.postln;

				//Needed for Pattern syncing
				~isPlaying = true;

				msgFunc.valueEnvir.postln;

				//Create all Synths and pack the bundle
				bundle = server.makeBundle(false, {
					AlgaSynth(
						instrumentName,
						msgFunc.valueEnvir, //Compute Array of args
						~algaPattern.synthGroup
					)
				});

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
		eventPairs.keysValuesDo({ | event, value |
			if(event != \def, {
				//Behaviour for keys != \def
				var valueAsStream = value.asStream;

				//Update eventPairs with the Stream
				eventPairs[event] = valueAsStream;

				//Use Pfuncn on the Stream for parameters
				patternPairs = patternPairs.addAll([
					event,
					Pfuncn( { eventPairs[event].next }, inf)
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

	//the interpolation function for Pattern << Pattern
	interpPattern {

	}

	makeConnection {
		//This must add support for Patterns as args for <<, <<+ and <|
	}

	replace {
		//Basically, replace the SynthDef used, or the ListPattern
	}

	isAlgaPattern { ^true }

	//debug purposes
	instantiated { ^true }
}

AP : AlgaPattern {}
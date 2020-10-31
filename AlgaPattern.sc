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
				var server = ~algaPattern.server;
				var clock = ~algaPattern.algaScheduler.clock;

				//Needed for some Pattern syncing
				~isPlaying = true;

				//per-param busses and synths synths
				~freq.postln;

				//Create all Synths and pack the bundle
				bundle = server.makeBundle(false, {
					AlgaSynth(\test)
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
		var synthEntry = obj[\def];

		if(synthEntry == nil, {
			"AlgaPattern: no synth entry in the Event".error;
			^this;
		});

		//Store initial eventPairs
		eventPairs = obj;

		//Store class of the synthEntry
		objClass = synthEntry.class;

		//If there is a synth playing, set its instantiated status to false:
		//this is mostly needed for .replace to work properly and wait for the new synth
		//to be instantiated!
		if(synth != nil, { synth.instantiated = false });

		//Symbol
		if(objClass == Symbol, {
			this.dispatchSynthDef(synthEntry, initGroups, replace,
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

		//Add \type, \algaNode, \algaPattern, \this and \out, synthBus.busArg
		patternPairs = patternPairs.addAll([
			\type, \algaNode,
			\algaPattern, this,
			\out, synthBus.busArg
		]);

		//Create the Pattern by calling .next from the streams
		pattern = Pbind(*patternPairs);
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
}

AP : AlgaPattern {}
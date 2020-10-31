AlgaPattern : AlgaNode {

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
	dispatchNode { | eventPairs, args, initGroups = false, replace = false,
		keepChannelsMapping = false, outsMapping, keepScale = false |

		//synth: entry
		var obj = eventPairs[\synth];

		if(obj == nil, {
			"AlgaPattern: no synth entry in the Event".error;
			^this;
		});

		//Store class
		objClass = obj.class;

		//If there is a synth playing, set its instantiated status to false:
		//this is mostly needed for .replace to work properly and wait for the new synth
		//to be instantiated!
		if(synth != nil, { synth.instantiated = false });

		//Symbol
		if(objClass == Symbol, {
			this.dispatchSynthDef(obj, initGroups, replace,
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
					this.clear;
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

	makeConnection {
		//This must add support for Patterns as args for <<, <<+ and <|
	}

	replace {
		//Basically, replace the SynthDef used, or the ListPattern
	}

	isAlgaPattern { ^true }
}

AP : AlgaPattern {}
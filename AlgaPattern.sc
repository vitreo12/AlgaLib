AlgaPattern : AlgaNode {

	//Add the \algaNote event to Event
	*initClass {
		StartUp.add({ //StartUp.add is needed
			Event.addEventType(\algaNote, { | server |
				//per-param synths

				//actual synth that makes sound, connected to synthBus

				//Use the overloaded algaSchedBundleArrayOnClock function to send all bundles together
			});
		});
	}

	*new { | ... pairs |
		//Don't know why ^super.new won't work here tbh.
		//It would call AlgaNode's new and init
		^super.newCopyArgs(nil).init(pairs);
	}

	init { | pairs |
		this.dispatchNode(pairs[0]);
	}

	dispatchNode { | obj, initGroups = false, replace = false,
		keepChannelsMapping = false, outsMapping, keepScale = false |

		//Store class
		objClass = obj.class;

		//If there is a synth playing, set its instantiated status to false:
		//this is mostly needed for .replace to work properly and wait for the new synth
		//to be instantiated!
		if(synth != nil, { synth.instantiated = false });

		//Symbol
		if(objClass == Symbol, {
			this.dispatchSynthDef;
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

	//Only support one SynthDef symbol for now.
	dispatchSynthDef {
		"AlgaPattern: dispatching SynthDef".warn;
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
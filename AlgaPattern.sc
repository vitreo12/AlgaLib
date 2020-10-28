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

	*new {

	}

	init {

	}

	dispatchPattern {
		//Only support one SynthDef symbol for now. Support Function in the future
		//Support multiple SynthDefs in the future,
		//only if expressed with ListPattern subclasses (like Pseq, Prand, etc...)

	}

	makeConnection {
		//This must add support for Patterns as args for <<, <<+ and <|
	}

	replace {
		//Basically, replace the SynthDef used, or the ListPattern
	}

}

AP : AlgaPattern {}
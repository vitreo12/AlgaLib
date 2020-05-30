AlgaSynth : Synth {

}

AlgaNode {
	var <>fadeTime = 0;
	var <>synth;
	var <>group, <>synthGroup, <>normGroup, <>interpGroup;

	*new { | obj, fadeTime = 0|
		^super.new.init(obj, fadeTime)
	}

	init { | obj, fadeTime = 0 |

		this.fadeTime = fadeTime;

		this.createAllGroups;

		//Dispatch node creation
		this.dispatchNode(obj);
	}

	createAllGroups {
		//This order is to add everything to head
		this.group = Group.new;
		this.synthGroup = Group(group); //could be ParGroup here for supernova + patterns...
		this.normGroup = Group(group);
		this.interpGroup = Group(group);
	}

	freeAllGroups {
		//Just delete top group (it will delete all chilren too)
		this.group.free;
	}

	replace { | obj |
		//Free previous one
		this.freeSynth;

		//New one
		this.dispatchNode(obj);
	}

	dispatchNode { | obj |
		var objClass = obj.class;

		//Dispatch creation
		if(objClass == Symbol, {
			this.newSynthFromSymbol(obj);
		}, {
			"AlgaNode: class '" ++ objClass ++ "' is invalid".error;
			this.clear;
		});

	}

	newSynthFromSymbol { | defName |
		this.synth = AlgaSynth.new(defName, [\fadeTime, this.fadeTime], this.synthGroup);
	}

	freeSynth {
		if(this.synth != nil, {
			//Send fadeTime too again in case it has been changed by user
			this.synth.set(\gate, 0, \fadeTime, this.fadeTime);
		});
	}

	clear {
		fork {
			this.freeSynth;

			this.fadeTime.wait;

			this.freeAllGroups;
		}
	}
}

AlgaSynthDef : SynthDef {

}
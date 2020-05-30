AlgaNode {
	var <>fadeTime = 0;
	var <>synth;
	var <>group, <>synthGroup, <>normGroup, <>interpGroup;
	var <>toBeCleared=false;

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
		if(this.group == nil, {
			this.group = Group.new;
			this.synthGroup = Group(group); //could be ParGroup here for supernova + patterns...
			this.normGroup = Group(group);
			this.interpGroup = Group(group);
		});
	}

	//Groups (and state) will be reset only if they are nil AND they are set to be freed.
	//the toBeCleared variable can be changed in real time, if AlgaNode.replace is called while
	//clearing is happening!
	freeAllGroups {
		if((this.group != nil).and(this.toBeCleared), {
			//Just delete top group (it will delete all chilren too)
			this.group.free;

			//Reset values. Don't reset fadeTime, as it can still be used!
			this.group = nil;
			this.synthGroup = nil;
			this.normGroup = nil;
			this.interpGroup = nil;
		});
	}

	replace { | obj |
		//Free previous one
		this.freeSynth;

		//In case it has been set to true when clearing, then replacing before clear ends!
		this.toBeCleared = false;

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
			//fade time will eventually be put just to the interp proxies!
			this.synth.set(\gate, 0, \fadeTime, this.fadeTime);

			//Set to nil (should it fork?)
			this.synth = nil;
		});
	}

	clear {
		fork {
			this.freeSynth;

			this.toBeCleared = true;

			this.fadeTime.wait;

			this.freeAllGroups;
		}
	}
}
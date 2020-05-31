AlgaNode {
	var <>server;
	var <>fadeTime = 0;
	var <>objClass;
	var <>synthDef;

	var <>controlNames, <>numChannels, <>rate;

	var <>group, <>synthGroup, <>normGroup, <>interpGroup;
	var <>synth, <>normSynth, <>interpSynth;
	var <>synthBus, <>normBus, <>interpBus;

	var <>toBeCleared=false;

	*new { | obj, server, fadeTime = 0 |
		^super.new.init(obj, server, fadeTime)
	}

	init { | obj, server, fadeTime = 0 |

		this.fadeTime = fadeTime;

		if(server == nil, {this.server = Server.default}, {this.server = server});

		//Dispatch node creation
		this.dispatchNode(obj);

		//Create all utilities
		this.createAllGroups;
		this.createAllBusses;

		//Create node
		this.newNode;
	}

	createAllGroups {
		if(this.group == nil, {
			this.group = Group(this.server);
			this.synthGroup = Group(group); //could be ParGroup here for supernova + patterns...
			this.normGroup = Group(group);
			this.interpGroup = Group(group);
		});
	}

	//spinlock to wait for synth creation
	createAllBusses {
		this.synthBus = AlgaBus(this.server, this.numChannels, this.rate);
	}

	//Groups (and state) will be reset only if they are nil AND they are set to be freed.
	//the toBeCleared variable can be changed in real time, if AlgaNode.replace is called while
	//clearing is happening!
	freeAllGroups {
		if((this.group != nil).and(this.toBeCleared), {
			//Just delete top group (it will delete all chilren too)
			this.group.free;

			//Reset values
			this.group = nil;
			this.synthGroup = nil;
			this.normGroup = nil;
			this.interpGroup = nil;
		});
	}

	freeAllBusses {
		if((this.synthBus != nil).and(this.toBeCleared), {
			this.synthBus.freeBus;
		});
	}

	replaceBusses {
		this.freeAllBusses;
		this.createAllBusses;
	}

	replace { | obj |
		//Free previous one
		this.freeSynth;

		//In case it has been set to true when clearing, then replacing before clear ends!
		this.toBeCleared = false;

		//New one
		this.dispatchNode(obj);

		this.replaceBusses;

		this.newNode;
	}

	//dispatches controlnames / numChannels / rate according to obj class
	dispatchNode { | obj |
		this.objClass = obj.class;
		if(this.objClass == Symbol, {
			var synthDesc = SynthDescLib.global.at(obj);
			this.synthDef = synthDesc.def;

			if(this.synthDef.class != AlgaSynthDef, {
				("Invalid AlgaSynthDef: '" ++ obj.asString ++"'").error;
				this.clear;
				^nil;
			});

			this.controlNames = synthDesc.controlNames;
			this.numChannels = this.synthDef.numChannels;
			this.rate = this.synthDef.rate;

			this.controlNames.postln;
		}, {
			"AlgaNode: class '" ++ objClass ++ "' is invalid".error;
			this.clear;
		});
	}

	newNode {
		//Dispatch creation
		if(this.objClass == Symbol, {
			//This should only allow SynthDefs defined with AlgaSynthDef to play...
			this.newSynthFromSymbol(this.synthDef.name);
		}, {
			"AlgaNode: class '" ++ objClass ++ "' is invalid".error;
			this.clear;
		});
	}

	newSynthFromSymbol { | defName |
		//asUgenInput doens't work with args set like this
		this.synth = AlgaSynth.new(defName, [\out, this.synthBus.bus.index, \fadeTime, this.fadeTime], this.synthGroup);
	}

	freeSynth {
		if(this.synth != nil, {
			//Send fadeTime too again in case it has been changed by user
			//fade time will eventually be put just to the interp proxies!
			this.synth.set(\gate, 0, \fadeTime, this.fadeTime);

			//Set to nil (should it fork?)
			this.synth = nil;
			this.controlNames = nil;
			this.numChannels = 0;
			this.rate = nil;
		});
	}

	clear {
		fork {
			this.freeSynth;

			this.toBeCleared = true;

			this.fadeTime.wait;

			this.freeAllGroups;

			this.freeAllBusses;
		}
	}

	instantiated {
		^this.synth.instantiated;
	}

}
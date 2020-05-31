AlgaNode {
	var <>server;
	var <>fadeTime = 0;
	var <>objClass;

	var <>synthDef;

	var <>controlNames, <>numChannels, <>rate;

	var <>group, <>synthGroup, <>normGroup, <>interpGroup;
	var <>synth, <>normSynths, <>interpSynths;
	var <>synthBus, <>normBusses, <>interpBusses;

	var <>isPlaying = false;
	var <>toBeCleared = false;

	*new { | obj, server, fadeTime = 0 |
		^super.new.init(obj, server, fadeTime)
	}

	init { | obj, server, fadeTime = 0 |

		this.fadeTime = fadeTime;

		if(server == nil, {this.server = Server.default}, {this.server = server});

		this.normBusses   = Dictionary(10);
		this.interpBusses = Dictionary(10);
		this.normSynths   = Dictionary(10);
		this.interpSynths = Dictionary(10);

		//Dispatch node creation
		this.dispatchNode(obj, true);
	}

	createAllGroups {
		if(this.group == nil, {
			this.group = Group(this.server);
			this.synthGroup = Group(group); //could be ParGroup here for supernova + patterns...
			this.normGroup = Group(group);
			this.interpGroup = Group(group);
		});
	}

	resetGroups {
		//Reset values
		this.group = nil;
		this.synthGroup = nil;
		this.normGroup = nil;
		this.interpGroup = nil;
	}

	//Groups (and state) will be reset only if they are nil AND they are set to be freed.
	//the toBeCleared variable can be changed in real time, if AlgaNode.replace is called while
	//clearing is happening!
	freeAllGroups { | now = false |
		if((this.group != nil).and(this.toBeCleared), {
			if(now, {
				//Just delete top group (it will delete all children too)
				this.group.free;
				this.resetGroups;
			}, {
				fork {
					this.fadeTime.wait;
					this.group.free;
					this.resetGroups;
				};
			});
		});
	}

	createAllBusses {
		this.controlNames.do({ | controlName |
			var argName = controlName.name;

			//Ignore gate / fadeTime / out
			if((argName != \out).and(argName != \fadeTime).and(argName != \gate), {
				var argDefaultVal = controlName.defaultValue;
				var argRate = controlName.rate;
				var argNumChannels = controlName.numChannels;

				this.normBusses[argName.asSymbol] = AlgaBus(this.server, argNumChannels, argRate);
				this.interpBusses[argName.asSymbol] = AlgaBus(this.server, argNumChannels, argRate);
			});
		});

		this.synthBus = AlgaBus(this.server, this.numChannels, this.rate);

		//if(this.isPlaying, {this.synthBus.play});
	}

	freeAllBusses { | now = false |
		//if forking, this.synthBus could have changed, that's why this is needed
		var previousSynthBus = this.synthBus;
		var previousNormBusses = this.normBusses;
		var previousInterpBusses = this.interpBusses;

		if(now, {
			if(previousSynthBus != nil, { previousSynthBus.free });
			if(previousNormBusses != nil, {
				previousNormBusses.do({ | normBus |
					if(normBus != nil, { normBus.free });
				});
			});
			if(previousInterpBusses != nil, {
				previousInterpBusses.do({ | interpBus |
					if(interpBus != nil, { interpBus.free });
				});
			});
		}, {
			//Free previous busses after fadeTime
			fork {
				this.fadeTime.wait;
				if(previousSynthBus != nil, { previousSynthBus.free });
				if(previousNormBusses != nil, {
					previousNormBusses.do({ | normBus |
						if(normBus != nil, { normBus.free });
					});
				});
				if(previousInterpBusses != nil, {
					previousInterpBusses.do({ | interpBus |
						if(interpBus != nil, { interpBus.free });
					});
				});
			}
		});
	}

	replace { | obj |
		//re-init groups if clear was used
		var initGroups = if(this.group == nil, {true}, {false});

		//In case it has been set to true when clearing, then replacing before clear ends!
		this.toBeCleared = false;

		//Free previous one
		this.freeSynth;

		this.freeInterpNormSynths(true, true);

		//Is it really necessary to delete previous busses?
		this.freeAllBusses;

		//New one
		this.dispatchNode(obj, initGroups);
	}

	//dispatches controlnames / numChannels / rate according to obj class
	dispatchNode { | obj, initGroups = false |
		this.objClass = obj.class;

		//Symbol
		if(this.objClass == Symbol, {
			this.dispatchSynthDef(obj, initGroups);
		}, {
			//Function
			if(this.objClass == Function, {
				this.dispatchFunction(obj, initGroups);

			}, {
				("AlgaNode: class '" ++ this.objClass ++ "' is invalid").error;
				this.clear;
			});
		});
	}

	dispatchSynthDef { | obj, initGroups = false |
		var synthDesc = SynthDescLib.global.at(obj);
		this.synthDef = synthDesc.def;

		if(this.synthDef.class != AlgaSynthDef, {
			("Invalid AlgaSynthDef: '" ++ obj.asString ++"'").error;
			this.clear;
			^nil;
		});

		this.controlNames = synthDesc.controls;
		this.numChannels = this.synthDef.numChannels;
		this.rate = this.synthDef.rate;

		//Create all utilities
		if(initGroups, { this.createAllGroups });
		this.createAllBusses;

		//Create actual synths
		this.createSynth(this.synthDef.name);
	}

	dispatchFunction { | obj, initGroups = false |
		//Need to wait for server's receiving the sdef
		fork {
			this.synthDef = AlgaSynthDef(("alga_" ++ UniqueID.next).asSymbol, obj).send(this.server);
			server.sync;
			this.controlNames = this.synthDef.asSynthDesc.controls;
			this.numChannels = this.synthDef.numChannels;
			this.rate = this.synthDef.rate;

			//Create all utilities
			if(initGroups, { this.createAllGroups });
			this.createAllBusses;

			//Create actual synths
			this.createSynth(this.synthDef.name);
		};
	}

	resetSynth {
		//Set to nil (should it fork?)
		this.synth = nil;
		this.synthDef = nil;
		this.controlNames = nil;
		this.numChannels = 0;
		this.rate = nil;
	}

	resetInterpNormSynths {
		this.intepSynths.clear;
		this.normSynths.clear;
	}

	createSynth { | defName |
		this.synth = AlgaSynth.new(defName,
			[\out, this.synthBus.asUGenInput, \fadeTime, this.fadeTime],
			this.synthGroup
		);
	}

	createInterpSynths {

	}

	createNormSynths {

	}

	freeSynth {
		if(this.synth != nil, {
			//Send fadeTime too again in case it has been changed by user
			//fade time will eventually be put just to the interp proxies!
			this.synth.set(\gate, 0, \fadeTime, this.fadeTime);

			this.resetSynth;
		});
	}

	freeInterpNormSynths { | useFadeTime = false, now = false |
		var prevInterpSynths = interpSynths;
		var prevNormSynths = normSynths;

		if(now, {
			prevInterpSynths.do({ | interpSynth |
				interpSynth.set(\gate, 0, \fadeTime, if(useFadeTime, {this.fadeTime}, {0}));
			});

			prevNormSynths.do({ | normSynth |
				normSynth.set(\gate, 0, \fadeTime, if(useFadeTime, {this.fadeTime}, {0}));
			});

			//this.resetInterpNormSynths;

		}, {
			fork {
				this.fadeTime.wait;

				prevInterpSynths.do({ | interpSynth |
					interpSynth.set(\gate, 0, \fadeTime, 0);
				});

				prevNormSynths.do({ | normSynth |
					normSynth.set(\gate, 0, \fadeTime, 0);
				});

				//this.resetInterpNormSynths;
			}
		});
	}

	clear {
		fork {
			this.freeSynth;

			this.toBeCleared = true;

			//Wait time before clearing groups and busses
			this.fadeTime.wait;
			this.freeInterpNormSynths(false, true);
			this.freeAllGroups(true);
			this.freeAllBusses(true);
		}
	}

	play {
		this.isPlaying = true;
		this.synthBus.play;
	}

	instantiated {
		if(this.synth == nil, { ^false });
		^this.synth.instantiated;
	}

	<< {

	}

	>> {

	}

	<| {

	}

}
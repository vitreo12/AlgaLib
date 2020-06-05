AlgaNode {
	var <server;
	var <>fadeTime = 0; //Can be set from outside
	var <objClass;

	var <synthDef;

	var <controlNames, <numChannels, <rate;

	var <group, <synthGroup, <normGroup, <interpGroup;
	var <synth, <normSynths, <interpSynths;
	var <synthBus, <normBusses, <interpBusses;

	var <defaultBusses;

	var <inConnections, <outConnections;

	var <isPlaying = false;
	var <toBeCleared = false;

	*new { | obj, server, fadeTime = 0 |
		^super.new.init(obj, server, fadeTime)
	}

	init { | obj, argServer, argFadeTime = 0 |

		fadeTime = argFadeTime;

		if(argServer == nil, { server = Server.default }, { server = argServer });

		//Per-argument dictionaries of interp/norm Busses and Synths belonging to this AlgaNode
		normBusses   = Dictionary(10);
		interpBusses = Dictionary(10);
		defaultBusses = Dictionary(10);

		normSynths   = Dictionary(10);
		interpSynths = Dictionary(10);

		//Per-argument connections to this AlgaNode
		inConnections = Dictionary.new(10);
		outConnections = Dictionary.new(10);

		//Dispatch node creation
		this.dispatchNode(obj, true);
	}

	createAllGroups {
		if(group == nil, {
			group = Group(this.server);
			synthGroup = Group(group); //could be ParGroup here for supernova + patterns...
			normGroup = Group(group);
			interpGroup = Group(group);
		});
	}

	resetGroups {
		//Reset values
		group = nil;
		synthGroup = nil;
		normGroup = nil;
		interpGroup = nil;
	}

	//Groups (and state) will be reset only if they are nil AND they are set to be freed.
	//the toBeCleared variable can be changed in real time, if AlgaNode.replace is called while
	//clearing is happening!
	freeAllGroups { | now = false |
		if((group != nil).and(toBeCleared), {
			if(now, {
				//Free now
				group.free;
				this.resetGroups;
			}, {
				//Wait fadeTime, then free
				fork {
					fadeTime.wait;
					group.free;
					this.resetGroups;
				};
			});
		});
	}

	createAllBusses {
		controlNames.do({ | controlName |
			var argName = controlName.name;

			var argDefaultVal = controlName.defaultValue;
			var argRate = controlName.rate;
			var argNumChannels = controlName.numChannels;

			//interpBusses have 1 more channel for the envelope shape
			interpBusses[argName] = AlgaBus(server, argNumChannels + 1, argRate);
			normBusses[argName] = AlgaBus(server, argNumChannels, argRate);

			defaultBusses[argName] = AlgaBus(server, argNumChannels, argRate);
		});

		synthBus = AlgaBus(server, numChannels, rate);

		if(isPlaying, { synthBus.play });
	}

	freeAllBusses { | now = false |
		//if forking, this.synthBus could have changed, that's why this is needed
		var previousSynthBus = synthBus;
		var previousNormBusses = normBusses;
		var previousInterpBusses = interpBusses;
		var previousDefaultBusses = defaultBusses;

		if(now, {
			//Free busses now
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
			if(previousDefaultBusses != nil, {
				previousDefaultBusses.do({ | defaultBus |
					if(defaultBus != nil, { defaultBus.free });
				});
			});
		}, {
			//Free previous busses after fadeTime
			fork {
				fadeTime.wait;
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
				if(previousDefaultBusses != nil, {
				previousDefaultBusses.do({ | defaultBus |
					if(defaultBus != nil, { defaultBus.free });
				});
			});
			}
		});
	}

	replace { | obj |
		//re-init groups if clear was used
		var initGroups = if(group == nil, { true }, { false });

		//In case it has been set to true when clearing, then replacing before clear ends!
		toBeCleared = false;

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
		objClass = obj.class;

		//Symbol
		if(objClass == Symbol, {
			this.dispatchSynthDef(obj, initGroups);
		}, {
			//Function
			if(objClass == Function, {
				this.dispatchFunction(obj, initGroups);
			}, {
				("AlgaNode: class '" ++ objClass ++ "' is invalid").error;
				this.clear;
			});
		});
	}

	dispatchSynthDef { | obj, initGroups = false |
		var synthDesc = SynthDescLib.global.at(obj);

		if(synthDesc == nil, {
			("Invalid AlgaSynthDef: '" ++ obj.asString ++"'").error;
			this.clear;
			^nil;
		});

		synthDef = synthDesc.def;

		if(synthDef.class != AlgaSynthDef, {
			("Invalid AlgaSynthDef: '" ++ obj.asString ++"'").error;
			this.clear;
			^nil;
		});

		controlNames = synthDesc.controls;
		this.sanitizeControlNames;
		numChannels = synthDef.numChannels;
		rate = synthDef.rate;

		//Create all utilities
		if(initGroups, { this.createAllGroups });
		this.createAllBusses;

		//Create actual synths
		this.createAllSynths(synthDef.name, numChannels);
	}

	dispatchFunction { | obj, initGroups = false |
		//Need to wait for server's receiving the sdef
		fork {
			synthDef = AlgaSynthDef(("alga_" ++ UniqueID.next).asSymbol, obj).send(server);
			server.sync;
			controlNames = synthDef.asSynthDesc.controls;
			this.sanitizeControlNames;
			numChannels = synthDef.numChannels;
			rate = synthDef.rate;

			//Create all utilities
			if(initGroups, { this.createAllGroups });
			this.createAllBusses;

			//Create actual synths
			this.createAllSynths(synthDef.name);
		};
	}

	//Remove \fadeTime \out and \gate from controlNames
	sanitizeControlNames {
		controlNames.removeAllSuchThat({ | controlName |
			var argName = controlName.name;
			(controlName.name == \fadeTime).or(controlName.name == \out).or(controlName.name == \gate);
		});
	}

	resetSynth {
		//Set to nil (should it fork?)
		synth = nil;
		synthDef = nil;
		controlNames = nil;
		numChannels = 0;
		rate = nil;
	}

	resetInterpNormSynths {
		//Just reset the Dictionaries entries
		interpSynths.clear;
		normSynths.clear;
	}

	createAllSynths { | defName |
		this.createSynth(synthDef.name);
		this.createInterpNormSynths;
	}

	//Synth writes to the synthBus
	createSynth { | defName |
		synth = AlgaSynth.new(
			defName,
			[\out, synthBus.index, \fadeTime,fadeTime],
			synthGroup
		);
	}

	//This should take in account the nextNode's numChannels when making connections
	createInterpNormSynths { | inChannels, inRate |
		controlNames.do({ | controlName |
			var interpBus, normBus, interpSynth, normSynth;

			var interpSymbol, normSymbol;

			var inputChannels, inputRate;

			var argName = controlName.name;
			var argChannels = controlName.numChannels.asString;
			var argRate = controlName.rate.asString;
			var argDefault = controlName.defaultValue;

			if(inChannels == nil, { inputChannels = argChannels }, { inputChannels = inChannels });
			if(inRate == nil, { inputRate = argRate }, { inputRate = inRate });

			interpSymbol = (
				"algaInterp_" ++
				inputRate.asString ++
				inputChannels.asString ++
				"_" ++
				argRate ++
				argChannels
			).asSymbol;

			normSymbol = (
				"algaNorm_" ++
				argRate ++
				argChannels
			).asSymbol;

			interpBus = interpBusses[argName];
			normBus = normBusses[argName];

			interpSymbol.postln;

			interpSynth = AlgaSynth.new(
				interpSymbol,
				[\in, argDefault, \out, interpBus.index, \fadeTime, fadeTime],
				interpGroup
			);

			normSynth = AlgaSynth.new(
				normSymbol,
				[\args, interpBus.busArg, \out, normBus.index, \fadeTime, fadeTime],
				normGroup
			);

			interpSynths[argName] = interpSynth;
			normSynths[argName] = normSynth;

			//Wait fade time then patch the synth's arguments to the normBusses
			fork {
				fadeTime.wait;
				synth.set(argName, normBus.busArg);
			}

		});
	}

	freeSynth {
		if(synth != nil, {
			//Send fadeTime too again in case it has been changed by user
			//fade time will eventually be put just to the interp proxies!
			synth.set(\gate, 0, \fadeTime, fadeTime);

			this.resetSynth;
		});
	}

	freeInterpNormSynths { | useFadeTime = false, now = false |
		var prevInterpSynths = interpSynths;
		var prevNormSynths = normSynths;

		if(now, {
			prevInterpSynths.do({ | interpSynth |
				interpSynth.set(\gate, 0, \fadeTime, if(useFadeTime, { fadeTime }, {0}));
			});

			prevNormSynths.do({ | normSynth |
				normSynth.set(\gate, 0, \fadeTime, if(useFadeTime, { fadeTime }, {0}));
			});

			//this.resetInterpNormSynths;

		}, {
			fork {
				fadeTime.wait;

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

			toBeCleared = true;

			//Wait time before clearing groups and busses
			fadeTime.wait;
			this.freeInterpNormSynths(false, true);
			this.freeAllGroups(true);
			this.freeAllBusses(true);
		}
	}

	play {
		isPlaying = true;
		synthBus.play;
	}

	/*
	instantiated {
		if(this.synth == nil, { ^false });
		^this.synth.instantiated;
	}
	*/

	>> { | nextNode, param = \in |
		//Should re-create interpSynth and interpBus for specific param
	}

	<< { | nextNode, param = \in |

	}

	<| { | param = \in |

	}
}
AN : AlgaNode {}

AlgaNode {
	var <server;

	//This is the time when making a new connection to this proxy.
	//Could be just named interpolationTime OR connectionTime
	var <fadeTime = 0;

	//This is the longestFadeTime between all the outConnections.
	//it's used when .replacing a node connected to something, in order for it to be kept alive
	//for all the connected nodes to run their interpolator on it
	//longestFadeTime will be moved to AlgaBlock and applied per-block!
	var <fadeTimeConnections, <longestFadeTime = 0;

	var <objClass;
	var <synthDef;

	var <controlNames;

	var <numChannels, <rate;

	var <group, <synthGroup, <normGroup, <interpGroup;
	var <synth, <normSynths, <interpSynths;
	var <synthBus, <normBusses, <interpBusses;

	var <inConnections, <outConnections;

	var <isPlaying = false;
	var <toBeCleared = false;

	//This is used in feedback situations when using .replace
	var <beingReplaced = false;

	*new { | obj, server, fadeTime = 0 |
		^super.new.init(obj, server, fadeTime)
	}

    init { | obj, argServer, argFadeTime = 0 |
		//Default server if not specified otherwise
		if(argServer == nil, { server = Server.default }, { server = argServer });

		//param -> ControlName
		controlNames = Dictionary(10);

		//Per-argument dictionaries of interp/norm Busses and Synths belonging to this AlgaNode
		normBusses   = Dictionary(10);
		interpBusses = Dictionary(10);
		normSynths   = Dictionary(10);
		interpSynths = Dictionary(10);

		//Per-argument connections to this AlgaNode. These are in the form:
		//(param -> Set[AlgaNode, AlgaNode...]). Multiple AlgaNodes are used when
		//using the mixing <<+ / >>+
		inConnections = Dictionary.new(10);

		//outConnections are not indexed by param name, as they could be attached to multiple nodes with same param name.
		//they are indexed by identity of the connected node, and then it contains a Set of all parameters
		//that it controls in that node (AlgaNode -> Set[\freq, \amp ...])
		outConnections = Dictionary.new(10);

		//Keeps all the fadeTimes of the connected nodes
		fadeTimeConnections = Dictionary.new(10);

		//starting fadeTime (using the setter so it also sets longestFadeTime)
		this.fadeTime_(argFadeTime);

		//Dispatch node creation
		this.dispatchNode(obj, true);
	}

	fadeTime_ { | val |
		fadeTime = val;
		this.calculateLongestFadeTime(val);
	}

	ft {
		^fadeTime;
	}

	ft_ { | val |
		this.fadeTime_(val);
	}

	//Also sets for inConnections.. outConnections would create endless loop?
	calculateLongestFadeTime { | argFadeTime |
		longestFadeTime = if(fadeTime > argFadeTime, { fadeTime }, { argFadeTime });

		fadeTimeConnections.do({ | val |
			if(val > longestFadeTime, { longestFadeTime = val });
		});

		inConnections.do({ | sendersSet |
			sendersSet.do({ | sender |
				sender.calculateLongestFadeTime(argFadeTime);
			});
		});
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

				//this.resetGroups;
			}, {
				//Wait fadeTime, then free
				fork {
					longestFadeTime.wait;

					group.free;

					//this.resetGroups;
				};
			});
		});
	}

	createSynthBus {
		synthBus = AlgaBus(server, numChannels, rate);
		if(isPlaying, { synthBus.play });
	}

	createInterpNormBusses {
		controlNames.do({ | controlName |
			var paramName = controlName.name;

			var argDefaultVal = controlName.defaultValue;
			var paramRate = controlName.rate;
			var paramNumChannels = controlName.numChannels;

			//interpBusses have 1 more channel for the envelope shape
			interpBusses[paramName] = AlgaBus(server, paramNumChannels + 1, paramRate);
			normBusses[paramName] = AlgaBus(server, paramNumChannels, paramRate);
		});
	}

	createAllBusses {
		this.createInterpNormBusses;
		this.createSynthBus;
	}

	freeSynthBus { | now = false |
		if(now, {
			if(synthBus != nil, { synthBus.free });
		}, {
			//if forking, this.synthBus could have changed, that's why this is needed
			var prevSynthBus = synthBus.copy;
			fork {
				longestFadeTime.wait;

				if(prevSynthBus != nil, { prevSynthBus.free });
			}
		});
	}

	freeInterpNormBusses { | now = false |

		if(now, {
			//Free busses now
			if(normBusses != nil, {
				normBusses.do({ | normBus |
					if(normBus != nil, { normBus.free });
				});
			});

			if(normBusses != nil, {
				normBusses.do({ | interpBus |
					if(interpBus != nil, { interpBus.free });
				});
			});
		}, {
			//Dictionary need to be deepcopied
			var prevNormBusses = normBusses.copy;
			var prevInterpBusses = interpBusses.copy;

			//Free prev busses after fadeTime
			fork {
				longestFadeTime.wait;

				if(prevNormBusses != nil, {
					prevNormBusses.do({ | normBus |
						if(normBus != nil, { normBus.free });
					});
				});

				if(prevInterpBusses != nil, {
					prevInterpBusses.do({ | interpBus |
						if(interpBus != nil, { interpBus.free });
					});
				});
			}
		});
	}

	freeAllBusses { | now = false |
		this.freeSynthBus(now);
		this.freeInterpNormBusses(now);
	}

	//dispatches controlnames / numChannels / rate according to obj class
	dispatchNode { | obj, initGroups = false |
		objClass = obj.class;

		//If there is a synth playing, set its instantiated status to false:
		//this is mostly needed for .replace to work properly and wait for the new synth
		//to be instantiated!
		if(synth != nil, { synth.instantiated = false });

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

	//Dispatch a SynthDef
	dispatchSynthDef { | obj, initGroups = false |
		var synthDescControlNames;
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

		synthDescControlNames = synthDesc.controls;
		this.createControlNames(synthDescControlNames);

		numChannels = synthDef.numChannels;
		rate = synthDef.rate;

		//Create all utilities
		if(initGroups, { this.createAllGroups });
		this.createAllBusses;

		//Create actual synths
		this.createAllSynths(synthDef.name);
	}

	//Dispatch a Function
	dispatchFunction { | obj, initGroups = false |
		//Need to wait for server's receiving the sdef
		fork {
			var synthDescControlNames;

			synthDef = AlgaSynthDef(("alga_" ++ UniqueID.next).asSymbol, obj).send(server);
			server.sync;

			synthDescControlNames = synthDef.asSynthDesc.controls;
			this.createControlNames(synthDescControlNames);

			numChannels = synthDef.numChannels;
			rate = synthDef.rate;

			//Create all utilities
			if(initGroups, { this.createAllGroups });
			this.createAllBusses;

			//Create actual synths
			this.createAllSynths(synthDef.name);
		};
	}

	//Remove \fadeTime \out and \gate and generate controlNames dict entries
	createControlNames { | synthDescControlNames |
		synthDescControlNames.do({ | controlName |
			var paramName = controlName.name;
			if((controlName.name != \fadeTime).and(
				controlName.name != \out).and(
				controlName.name != \gate).and(
				controlName.name != '?'), {
				controlNames[controlName.name] = controlName;
			});
		});
	}

	resetSynth {
		//Set to nil (should it fork?)
		synth = nil;
		synthDef = nil;
		controlNames.clear;
		numChannels = 0;
		rate = nil;
	}

	resetInterpNormSynths {
		//Just reset the Dictionaries entries
		interpSynths.clear;
		normSynths.clear;
	}

	//Synth writes to the synthBus
	//Synth always uses longestFadeTime, in order to make sure that everything connected to it
	//will have time to run fade ins and outs when running .replace!
	createSynth { | defName |
		//synth's fadeTime is longestFadeTime!
		var synthArgs = [\out, synthBus.index, \fadeTime, longestFadeTime];

		//Add the param busses (which have already been allocated)
		//Should this connect here or in createInterpNormSynths
		/*
		normBusses.keysValuesDo({ | param, normBus |
		synthArgs = synthArgs.add(param);
		synthArgs = synthArgs.add(normBus.busArg);
		});
		*/

		synth = AlgaSynth.new(
			defName,
			synthArgs,
			synthGroup
		);
	}

	//This should take in account the nextNode's numChannels when making connections
	createInterpNormSynths {
		controlNames.do({ | controlName |
			var interpSymbol, normSymbol;
			var interpBus, normBus, interpSynth, normSynth;

			var paramName = controlName.name;
			var paramNumChannels = controlName.numChannels.asString;
			var paramRate = controlName.rate.asString;
			var paramDefault = controlName.defaultValue;

			//e.g. \algaInterp_audio1_control1
			interpSymbol = (
				"algaInterp_" ++
				paramRate.asString ++
				paramNumChannels.asString ++
				"_" ++
				paramRate ++
				paramNumChannels
			).asSymbol;

			//e.g. \algaNorm_audio1
			normSymbol = (
				"algaNorm_" ++
				paramRate ++
				paramNumChannels
			).asSymbol;

			interpBus = interpBusses[paramName];
			normBus = normBusses[paramName];

			//Make sure interpSynth / normSynth are created before synth.set
			//Does this introduce latency though? Is it necessary? (looks like it's not)
			//server.bind({

			//Instantiated right away, with no fadeTime, as it will directly be connected to
			//synth's parameter
			interpSynth = AlgaSynth.new(
				interpSymbol,
				[\in, paramDefault, \out, interpBus.index, \fadeTime, 0],
				interpGroup
			);

			//Instantiated right away, with no fadeTime, as it will directly be connected to
			//synth's parameter (synth is already reading from all the normBusses)
			normSynth = AlgaSynth.new(
				normSymbol,
				[\args, interpBus.busArg, \out, normBus.index, \fadeTime, 0],
				normGroup
			);

			interpSynths[paramName] = interpSynth;
			normSynths[paramName] = normSynth;

			//server.sync;

			//Connect synth's parameter to the normBus
			synth.set(paramName, normBus.busArg);
			//});
		});
	}

	createAllSynths { | defName |
		this.createSynth(defName);
		this.createInterpNormSynths;
	}

	//Used at every << / >> / <|
	createInterpSynthAtParam { | sender, param = \in |
		var controlName;
		var paramNumChannels, paramRate;
		var senderNumChannels, senderRate;
		var interpSymbol;

		var interpBus, interpSynth;

		controlName = controlNames[param];

		paramNumChannels = controlName.numChannels;
		paramRate = controlName.rate;

		if(sender != nil, {
			// Used in << / >>
			senderNumChannels = sender.numChannels;
			senderRate = sender.rate;
		}, {
			//Used in <|
			senderNumChannels = paramNumChannels;
			senderRate = paramRate;
		});

		interpSymbol = (
			"algaInterp_" ++
			senderRate ++
			senderNumChannels ++
			"_" ++
			paramRate ++
			paramNumChannels
		).asSymbol;

		interpBus = interpBusses[param];

		//new interp synth, with input connected to sender and output to the interpBus
		//USES fadeTime!! This is the whole core of the interpolation behaviour!
		if(sender != nil, {
			//Used in << / >>
			//Read \in from the sender's synthBus
			interpSynth = AlgaSynth.new(
				interpSymbol,
				[\in, sender.synthBus.busArg, \out, interpBus.index, \fadeTime, fadeTime],
				interpGroup
			);
		}, {
			//Used in <|
			//if sender is nil, restore the original default value. This is used in <|
			var paramDefault = controlName.defaultValue;
			interpSynth = AlgaSynth.new(
				interpSymbol,
				[\in, paramDefault, \out, interpBus.index, \fadeTime, fadeTime],
				interpGroup
			);
		});

		//Add synth to interpSynths
		interpSynths[param] = interpSynth;
	}

	//Default now and fadetime to true for synths.
	//Synth always uses longestFadeTime, in order to make sure that everything connected to it
	//will have time to run fade ins and outs
	freeSynth { | useFadeTime = true, now = true |
		if(now, {
			if(synth != nil, {
				//synth's fadeTime is longestFadeTime!
				synth.set(\gate, 0, \fadeTime, if(useFadeTime, { longestFadeTime }, {0}));

				//this.resetSynth;
			});
		}, {
			fork {
				//longestFadeTime?
				longestFadeTime.wait;

				if(synth != nil, {
					synth.set(\gate, 0, \fadeTime, 0);

					//this.resetSynth;
				});
			}
		});
	}

	//Default now and fadetime to true for synths
	freeInterpNormSynths { | useFadeTime = true, now = true |

		if(now, {
			//Free synths now
			interpSynths.do({ | interpSynth |
				interpSynth.set(\gate, 0, \fadeTime, if(useFadeTime, { longestFadeTime }, {0}));
			});

			normSynths.do({ | normSynth |
				normSynth.set(\gate, 0, \fadeTime, if(useFadeTime, { longestFadeTime }, {0}));
			});

			//this.resetInterpNormSynths;

		}, {
			//Dictionaries need to be deep copied
			var prevInterpSynths = interpSynths.copy;
			var prevNormSynths = normSynths.copy;

			fork {
				//Wait, then free synths
				longestFadeTime.wait;

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

	freeAllSynths { | useFadeTime = true, now = true |
		this.freeSynth(useFadeTime, now);
		this.freeInterpNormSynths(useFadeTime, now);
	}

	//This is only used in connection situations
	//This, together with createInterpSynthAtParam, is the whole core of the interpolation behaviour!
	freeInterpSynthAtParam { | param = \in |
		var interpSynthAtParam = interpSynths[param];
		if(interpSynthAtParam == nil, { ("Invalid param for interp synth to free: " ++ param).error; ^this });
		interpSynthAtParam.set(\gate, 0, \fadeTime, fadeTime);
	}

	//param -> Set[AlgaNode, AlgaNode, ...]
	addInConnection { | sender, param = \in, mix = false |
		//Empty entry OR not doing mixing, create new Set. Otherwise, add to existing
		if((inConnections[param] == nil).or(mix.not), {
			inConnections[param] = Set[sender];
		}, {
			inConnections[param].add(sender);
		})
	}

	//AlgaNode -> Set[param, param, ...]
	addOutConnection { | receiver, param = \in |
		//Empty entry, create Set. Otherwise, add to existing
		if(outConnections[receiver] == nil, {
			outConnections[receiver] = Set[param];
		}, {
			outConnections[receiver].add(param);
		});
	}

	//add entries to the inConnections / outConnections / fadeTimeConnections of the two AlgaNodes
	addConnectionsDict { | sender, param = \in |
		//This will replace the entries on new connection (as mix == false)
		this.addInConnection(sender, param);

		//This will add the entries to the existing Set, or create a new one
		sender.addOutConnection(this, param);

		//Add to fadeTimeConnections and recalculate longestFadeTime
		sender.fadeTimeConnections[this] = this.fadeTime;
		sender.calculateLongestFadeTime(this.fadeTime);
	}

	removeInOutConnection { | sender, param = \in |
		sender.outConnections[this].remove(param);
		inConnections[param].remove(sender);

		//Recalculate longestFadeTime too
		sender.fadeTimeConnections[this] = 0;
		sender.calculateLongestFadeTime(0);
	}

	//Remove entries from inConnections / outConnections / fadeTimeConnections for all involved nodes
	removeConnectionsDict { | previousSender = nil, param = \in |
		var previousSenders = inConnections[param];
		if(previousSenders == nil, { ("No previous connection enstablished at param:" ++ param).error; ^this; });

		previousSenders.do({ | sender |
			var sendersParamsSet = sender.outConnections[this];
			if(sendersParamsSet != nil, {
				//Multiple entries in the set
				if(sendersParamsSet.size > 1, {
					//no previousSender specified: remove them all!
					if(previousSender == nil, {
						this.removeInOutConnection(sender, param);
					}, {
						//If specified previousSender, only remove that one (in mixing scenarios)
						if(sender == previousSender, {
							this.removeInOutConnection(sender, param);
						})
					})
				}, {
					//If Set with just one entry, remove the entire Set
					sender.outConnections.removeAt(this);

					//Recalculate longestFadeTime too
					sender.fadeTimeConnections[this] = 0;
					sender.calculateLongestFadeTime(0);
				})
			})
		});

		//If Set with just one entry, remove the entire Set
		if(previousSenders.size == 1, {
			inConnections.removeAt(param);
		})
	}

	resetConnectionsDicts {
		if(toBeCleared, {
			inConnections.clear;
			outConnections.clear;
		});
	}

	newInterpConnectionAtParam { | sender, param = \in |
		var controlName = controlNames[param];
		if(controlName == nil, { ("Invalid param to create a new interp synth for: " ++ param).error; ^this; });

		//Free prev interp synth (fades out)
		this.freeInterpSynthAtParam(param);

		//Spawn new interp synth (fades in)
		this.createInterpSynthAtParam(sender, param);

		//Add proper inConnections / outConnections / fadeTimeConnections
		this.addConnectionsDict(sender, param);
	}

	restoreInterpConnectionAtParam { | previousSender = nil, param = \in  |
		var controlName = controlNames[param];
		if(controlName == nil, { ("Invalid param to reset: " ++ param).error; ^this; });

		//Free prev interp synth (fades out)
		this.freeInterpSynthAtParam(param);

		//Create new interp synth with default value (or the one supplied with args at start) (fades in)
		this.createInterpSynthAtParam(nil, param);

		//Remove inConnections / outConnections / fadeTimeConnections
		this.removeConnectionsDict(previousSender, param);
	}

	//implements receiver <<.param sender
	makeConnection { | sender, param = \in |
		//Can't connect AlgaNode to itsels
		if(this === sender, { "Can't connect an AlgaNode to itself".error; ^this });

		//Connect interpSynth to the sender's synthBus
		AlgaSpinRoutine.waitFor( { (this.instantiated).and(sender.instantiated) }, {
			this.newInterpConnectionAtParam(sender, param);
		});
	}

	//arg is the sender
	<< { | sender, param = \in |
		if(sender.class == AlgaNode, {
			this.makeConnection(sender, param);
		}, {
			("Trying to enstablish a connection from an invalid AlgaNode: " ++ sender).error;
		});
	}

	//arg is the receiver
	>> { | receiver, param = \in |
        if(receiver.class == AlgaNode, {
            receiver.makeConnection(this, param);
        }, {
			("Trying to enstablish a connection to an invalid AlgaNode: " ++ receiver).error;
        });
	}

	//add to already running nodes (mix)
	<<+ { | sender, param = \in |
		//Would this require to have also inConnections to be some kind of Set?
	}

	//add to already running nodes (mix)
	>>+ { | receiver, param = \in |
		//Would this require to have also inConnections to be some kind of Set?
	}

	//resets to the default value in controlNames
	//OR, if provided, to the value of the original args that were used to create the node
	//previousSender is used in case of mixing, to only remove that one
	<| { | param = \in, previousSender = nil |
		//Also remove inConnections / outConnections / fadeTimeConnections
		if(previousSender != nil, {
			if(previousSender.class == AlgaNode, {
				AlgaSpinRoutine.waitFor( { (this.instantiated).and(previousSender.instantiated) }, {
					this.restoreInterpConnectionAtParam(previousSender, param);
				});
			}, {
				("Trying to remove a connection to an invalid AlgaNode: " ++ previousSender).error;
			})
		}, {
			AlgaSpinRoutine.waitFor( { this.instantiated }, {
				this.restoreInterpConnectionAtParam(nil, param);
			});
		})
	}

	//All synths must be instantiated (including interpolators and normalizers)
	instantiated {
		if(synth == nil, { ^false });

		interpSynths.do({ | interpSynth |
			if(interpSynth.instantiated == false, { ^false });
		});

		normSynths.do({ | normSynth |
			if(normSynth.instantiated == false, { ^false });
		});

		//Lastly, the actual synth
		^synth.instantiated;
	}

	//Remake both inConnections and outConnections...
	//Look for feedback!
	replaceConnections {
		//inConnections
		inConnections.keysValuesDo({ | param, sendersSet |
			sendersSet.do({ | sender |
				if(sender.beingReplaced, {
					"Sender is being already replaced".postln;
				});

				this.makeConnection(sender, param);
			})
		});

		//outConnections
		outConnections.keysValuesDo({ | receiver, paramsSet |
			paramsSet.do({ | param |
				receiver.makeConnection(this, param);
			});
		});

		//Also set beingReplaced to false when done instantiating this
		AlgaSpinRoutine.waitFor({ this.instantiated }, {
			beingReplaced = false;
		});
	}

	//replace content of the node, re-making all the connections
	replace { | obj |
		//re-init groups if clear was used
		var initGroups = if(group == nil, { true }, { false });

		//In case it has been set to true when clearing, then replacing before clear ends!
		toBeCleared = false;

		//Current node is being replaced.
		beingReplaced = true;

		//Free previous ones
		this.freeAllSynths;

		//Should perhaps check for new numChannels / rate, instead of just deleting it all
		this.freeAllBusses;

		//New one
		this.dispatchNode(obj, initGroups);

		//Re-enstablish connections that were already in place
		this.replaceConnections;
	}

	//Clears it all.. It should do some sort of fading
	clear {
		fork {
			this.freeSynth;

			toBeCleared = true;

			//Wait time before clearing groups and busses
			longestFadeTime.wait;
			this.freeInterpNormSynths(false, true);
			this.freeAllGroups(true);
			this.freeAllBusses(true);

			//Reset connection dicts
			this.resetConnectionsDicts;
		}
	}

	play {
		isPlaying = true;
		synthBus.play;
	}
}

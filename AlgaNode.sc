AlgaNode {
	var <server;

	var <>blockIndex = -1;

	//This is the time when making a new connection to this proxy.
	//Could be just named interpolationTime OR connectionTime
	var <fadeTime = 0.01;

    //This controls the fade in and out when .play / .stop
    var <>playTime = 0;

	//This is the longestFadeTime between all the outNodes.
	//it's used when .replacing a node connected to something, in order for it to be kept alive
	//for all the connected nodes to run their interpolator on it
	//longestFadeTime will be moved to AlgaBlock and applied per-block!
	var <fadeTimeConnections;
    var <longestFadeTime = 0;

    //The max between longestFadeTime and playTime
    var <longestTime = 0;

	//This will be added: args passed in at creation to overwrite SynthDef's one,
	//When using <|, then, these are the ones that will be restored!
	var <objArgs;

	var <objClass;
	var <synthDef;

	var <controlNames;

	var <numChannels, <rate;

	var <group, <playGroup, <synthGroup, <normGroup, <interpGroup;
	var <playSynth, <synth, <normSynths, <interpSynths;
	var <synthBus, <normBusses, <interpBusses;

	var <inNodes, <outNodes;

	var <isPlaying = false;
	var <toBeCleared = false;
    var <beingStopped = false;

	*new { | obj, args, fadeTime = 0, server |
		^super.new.init(obj, args, fadeTime, server)
	}

    init { | argObj, argArgs, argFadeTime = 0, argServer |
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
		inNodes = Dictionary.new(10);

		//outNodes are not indexed by param name, as they could be attached to multiple nodes with same param name.
		//they are indexed by identity of the connected node, and then it contains a Set of all parameters
		//that it controls in that node (AlgaNode -> Set[\freq, \amp ...])
		outNodes = Dictionary.new(10);

		//Keeps all the fadeTimes of the connected nodes
		fadeTimeConnections = Dictionary.new(10);

		//starting fadeTime (using the setter so it also sets longestFadeTime)
		this.fadeTime_(argFadeTime);

		//Dispatch node creation
		this.dispatchNode(argObj, argArgs, true);
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

    pt {
        ^playTime;
    }

    pt_ { | val |
        playTime = val;
        this.calculateLongestTime;
    }

    calculateLongestTime {
        longestTime = max(longestFadeTime, playTime);
    }

	//Also sets for inNodes.. outNodes would create endless loop?
	calculateLongestFadeTime { | argFadeTime, topNode = true |
		longestFadeTime = if(fadeTime > argFadeTime, { fadeTime }, { argFadeTime });

		fadeTimeConnections.do({ | val |
			if(val > longestFadeTime, { longestFadeTime = val });
		});

        this.calculateLongestTime;

		//Only run this on the nodes that are strictly connected to the one
		//which calculateLongestFadeTime was called on
		if(topNode, {
			inNodes.do({ | sendersSet |
				sendersSet.do({ | sender |
					//Detect feedbacks
					//if(sender != originalNode, {
						sender.calculateLongestFadeTime(argFadeTime, false);
					//},{
						//"Feedback detected".warn;
					//});
				});
			});
		});
	}

	createAllGroups {
		if(group == nil, {
			group = Group(this.server);
            playGroup = Group(group);
			synthGroup = Group(group); //could be ParGroup here for supernova + patterns...
			normGroup = Group(group);
			interpGroup = Group(group);
		});
	}

	resetGroups {
		if(toBeCleared, {
            playGroup = nil;
			group = nil;
			synthGroup = nil;
			normGroup = nil;
			interpGroup = nil;
		});
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
					longestTime.wait;

					group.free;

					//this.resetGroups;
				};
			});
		});
	}

	createSynthBus {
		synthBus = AlgaBus(server, numChannels, rate);
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
				//Cheap solution when having to replacing a synth that had other interp stuff
				//going on. Simply wait longer than longestFadeTime (which will be the time the replaced
				//node will take to interpolate to the previous receivers) and then free all the previous stuff
				(longestTime + 1.0).wait;

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
				//Cheap solution when having to replacing a synth that had other interp stuff
				//going on. Simply wait longer than longestFadeTime (which will be the time the replaced
				//node will take to interpolate to the previous receivers) and then free all the previous stuff
				(longestTime + 1.0).wait;

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
	dispatchNode { | obj, args, initGroups = false, replace = false |
		objClass = obj.class;

		//If there is a synth playing, set its instantiated status to false:
		//this is mostly needed for .replace to work properly and wait for the new synth
		//to be instantiated!
		if(synth != nil, { synth.instantiated = false });

		//Symbol
		if(objClass == Symbol, {
			this.dispatchSynthDef(obj, args, initGroups, replace);
		}, {
			//Function
			if(objClass == Function, {
				this.dispatchFunction(obj, args, initGroups, replace);
			}, {
				("AlgaNode: class '" ++ objClass ++ "' is invalid").error;
				this.clear;
			});
		});
	}

	//Dispatch a SynthDef
	dispatchSynthDef { | obj, args, initGroups = false, replace = false |
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
		this.createAllSynths(synthDef.name, replace);
	}

	//Dispatch a Function
	dispatchFunction { | obj, args, initGroups = false, replace = false |
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
			this.createAllSynths(synthDef.name, replace);
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
		if(toBeCleared, {
			//Set to nil (should it fork?)
			synth = nil;
			synthDef = nil;
			controlNames.clear;
			numChannels = 0;
			rate = nil;
		});
	}

	resetInterpNormSynths {
		if(toBeCleared, {
			//Just reset the Dictionaries entries
			interpSynths.clear;
			normSynths.clear;
		});
	}

	//Synth writes to the synthBus
	//Synth always uses longestFadeTime, in order to make sure that everything connected to it
	//will have time to run fade ins and outs when running .replace!
	createSynth { | defName |
		//synth's fadeTime is longestFadeTime!
		var synthArgs = [\out, synthBus.index, \fadeTime, longestTime];

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
	createInterpNormSynths { | replace = false |
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


            //If replace, connect to the pervious bus, not default
            //This wouldn't work with mixing for now...
            if(replace, {
                var sendersSet = inNodes[paramName];
                if(sendersSet.size > 1, { "Restoring mixing parameters is not implemented yet"; ^nil; });
                if(sendersSet != nil, {
                    if(sendersSet.size == 1, {
                        var prevSender;
                        sendersSet.do({ | sender | prevSender = sender }); //Sets can't be indexed, need to loop over

                        //It would be cool if I could keep the same interpSynth as before if it has same number
                        //of channels as the new one, so that it could continue the interpolation of the previous node,
                        //if one was taking place...
                        interpSynth = AlgaSynth.new(
                            interpSymbol,
                            [\in, prevSender.synthBus.busArg, \out, interpBus.index, \fadeTime, 0],
                            interpGroup
                        )
                    })
                }, {
                    //sendersSet is nil, run the normal one
                    interpSynth = AlgaSynth.new(
                        interpSymbol,
                        [\in, paramDefault, \out, interpBus.index, \fadeTime, 0],
                        interpGroup
                    );
                })
            }, {
                //No previous nodes connected: create a new interpSynth with the paramDefault value
                //Instantiated right away, with no fadeTime, as it will directly be connected to
                //synth's parameter
                interpSynth = AlgaSynth.new(
                    interpSymbol,
                    [\in, paramDefault, \out, interpBus.index, \fadeTime, 0],
                    interpGroup
                );
            });

			//Instantiated right away, with no fadeTime, as it will directly be connected to
			//synth's parameter (synth is already reading from all the normBusses)
			normSynth = AlgaSynth.new(
				normSymbol,
				[\args, interpBus.busArg, \out, normBus.index, \fadeTime, 0],
				normGroup
			);

			interpSynths[paramName] = interpSynth;
			normSynths[paramName] = normSynth;

			//Connect synth's parameter to the normBus
			synth.set(paramName, normBus.busArg);
		});
	}

	createAllSynths { | defName, replace = false |
		this.createSynth(defName);
		this.createInterpNormSynths(replace);
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

		if(sender.isAlgaNode, {
			// Used in << / >>
			senderNumChannels = sender.numChannels;
			senderRate = sender.rate;
		}, {
			//Used in <| AND << with number/array
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
		if(sender.isAlgaNode, {
			//Used in << / >>
			//Read \in from the sender's synthBus
            interpSynth = AlgaSynth.new(
                interpSymbol,
                [\in, sender.synthBus.busArg, \out, interpBus.index, \fadeTime, fadeTime],
                interpGroup
            );
		}, {
			//Used in <| AND << with number / array
			//if sender is nil, restore the original default value. This is used in <|
			var paramVal;
			if(sender == nil, {
				paramVal = controlName.defaultValue;
			}, {
				if(sender.isNumberOrArray,  {
					paramVal = sender
				}, {
					"Invalid paramVal for AlgaNode".error;
					^nil;
				});
			});

			interpSynth = AlgaSynth.new(
				interpSymbol,
				[\in, paramVal, \out, interpBus.index, \fadeTime, fadeTime],
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
				synth.set(\gate, 0, \fadeTime, if(useFadeTime, { longestTime }, {0}));

				//this.resetSynth;
			});
		}, {
			var prevSynth = synth.copy;

			fork {
				//Cheap solution when having to replacing a synth that had other interp stuff
				//going on. Simply wait longer than longestFadeTime (which will be the time the replaced
				//node will take to interpolate to the previous receivers) and then free all the previous stuff
				(longestTime + 1.0).wait;

				if(prevSynth != nil, {
					prevSynth.set(\gate, 0, \fadeTime, 0);
				});
			}
		});
	}

	//Default now and fadetime to true for synths
	freeInterpNormSynths { | useFadeTime = true, now = true |
		if(now, {
			//Free synths now
			interpSynths.do({ | interpSynth |
				interpSynth.set(\gate, 0, \fadeTime, if(useFadeTime, { longestTime }, {0}));
			});

			normSynths.do({ | normSynth |
				normSynth.set(\gate, 0, \fadeTime, if(useFadeTime, { longestTime }, {0}));
			});

			//this.resetInterpNormSynths;

		}, {
			//Dictionaries need to be deep copied
			var prevInterpSynths = interpSynths.copy;
			var prevNormSynths = normSynths.copy;

			fork {
				//Cheap solution when having to replacing a synth that had other interp stuff
				//going on. Simply wait longer than longestFadeTime (which will be the time the replaced
				//node will take to interpolate to the previous receivers) and then free all the previous stuff
				(longestTime + 1.0).wait;

				prevInterpSynths.do({ | interpSynth |
					interpSynth.set(\gate, 0, \fadeTime, 0);
				});

				prevNormSynths.do({ | normSynth |
					normSynth.set(\gate, 0, \fadeTime, 0);
				});
			}
		});
	}

	freeAllSynths { | useFadeTime = true, now = true |
		this.freeInterpNormSynths(useFadeTime, now);
		this.freeSynth(useFadeTime, now);
	}

	freeAllSynthOnNewInstantiation { | useFadeTime = true, now = true |
		this.freeAllSynths(useFadeTime, now);
	}

	//This is only used in connection situations
	//This, together with createInterpSynthAtParam, is the whole core of the interpolation behaviour!
	freeInterpSynthAtParam { | param = \in |
		var interpSynthAtParam = interpSynths[param];
		if(interpSynthAtParam == nil, { ("Invalid param for interp synth to free: " ++ param).error; ^this });
        interpSynthAtParam.set(\gate, 0, \fadeTime, fadeTime);
	}

	//param -> Set[AlgaNode, AlgaNode, ...]
	addInNode { | sender, param = \in, mix = false |
		//First of all, remove the outNodes that the previous sender had with the
		//param of this node, if there was any. Only apply if mix==false (no <<+ / >>+)
		if(mix == false, {
			var previousSenderSet = inNodes[param];
			if(previousSenderSet != nil, {
				previousSenderSet.do({ | previousSender |
					previousSender.outNodes.removeAt(this);
				});
			});
		});

		if(sender.isAlgaNode, {
			//Empty entry OR not doing mixing, create new Set. Otherwise, add to existing
			if((inNodes[param] == nil).or(mix.not), {
				inNodes[param] = Set[sender];
			}, {
				inNodes[param].add(sender);
			})
		});
	}

	//AlgaNode -> Set[param, param, ...]
	addOutNode { | receiver, param = \in |
		//Empty entry, create Set. Otherwise, add to existing
		if(outNodes[receiver] == nil, {
			outNodes[receiver] = Set[param];
		}, {
			outNodes[receiver].add(param);
		});
	}

	//add entries to the inNodes / outNodes / fadeTimeConnections of the two AlgaNodes
	addInOutNodesDict { | sender, param = \in |
		//This will replace the entries on new connection (as mix == false)
		this.addInNode(sender, param);

		//This will add the entries to the existing Set, or create a new one
		if(sender.isAlgaNode, {
			sender.addOutNode(this, param);

			//Add to fadeTimeConnections and recalculate longestFadeTime
			sender.fadeTimeConnections[this] = this.fadeTime;
			sender.calculateLongestFadeTime(this.fadeTime);
		});
	}

	removeInOutNode { | sender, param = \in |
		sender.outNodes[this].remove(param);
		inNodes[param].remove(sender);

		//Recalculate longestFadeTime too...
		//SHOULD THIS BE DONE AFTER THE SYNTHS ARE CREATED???
		//(Right now, this happens before creating new synths)
		sender.fadeTimeConnections[this] = 0;
		sender.calculateLongestFadeTime(0);
	}

	//Remove entries from inNodes / outNodes / fadeTimeConnections for all involved nodes
	removeInOutNodesDict { | previousSender = nil, param = \in |
		var previousSenders = inNodes[param];
		if(previousSenders == nil, { (/* "No previous connection enstablished at param:" ++ param).error;*/ ^this; });

		previousSenders.do({ | sender |
			var sendersParamsSet = sender.outNodes[this];
			if(sendersParamsSet != nil, {
				//Multiple entries in the set
				if(sendersParamsSet.size > 1, {
					//no previousSender specified: remove them all!
					if(previousSender == nil, {
						this.removeInOutNode(sender, param);
					}, {
						//If specified previousSender, only remove that one (in mixing scenarios)
						if(sender == previousSender, {
							this.removeInOutNode(sender, param);
						})
					})
				}, {
					//If Set with just one entry, remove the entire Set
					sender.outNodes.removeAt(this);

					//Recalculate longestFadeTime too...
					//SHOULD THIS BE DONE AFTER THE SYNTHS ARE CREATED???
					//(Right now, this happens before creating new synths)
					sender.fadeTimeConnections[this] = 0;
					sender.calculateLongestFadeTime(0);
				})
			})
		});

		//If Set with just one entry, remove the entire Set
		if(previousSenders.size == 1, {
			inNodes.removeAt(param);
		})
	}

	//Clear the dicts
	resetInOutNodesDicts {
		if(toBeCleared, {
			inNodes.clear;
			outNodes.clear;
		});
	}

	//New interp connection at specific parameter
	newInterpConnectionAtParam { | sender, param = \in, replace = false |
		var controlName = controlNames[param];
		if(controlName == nil, { ("Invalid param to create a new interp synth for: " ++ param).error; ^this; });

		//Add proper inNodes / outNodes / fadeTimeConnections
		this.addInOutNodesDict(sender, param);

		//Re-order groups
		//Actually reorder the block's nodes ONLY if not running .replace
		//(no need there, they are already ordered, and it also avoids a lot of problems
		//with feedback connections)
		if(replace.not, {
			AlgaBlocksDict.createNewBlockIfNeeded(this, sender);
			//this.freeInterpSynthAtParam(param);
		});

		//Free previous interp synth (fades out)
        this.freeInterpSynthAtParam(param);

        //Spawn new interp synth (fades in)
        this.createInterpSynthAtParam(sender, param);
	}

	//Used in <|
	removeInterpConnectionAtParam { | previousSender = nil, param = \in  |
		var controlName = controlNames[param];
		if(controlName == nil, { ("Invalid param to reset: " ++ param).error; ^this; });

		//Remove inNodes / outNodes / fadeTimeConnections
		this.removeInOutNodesDict(previousSender, param);

		//Re-order groups shouldn't be needed when removing connections

		//Free previous interp synth (fades out)
		this.freeInterpSynthAtParam(param);

		//Create new interp synth with default value (or the one supplied with args at start) (fades in)
		this.createInterpSynthAtParam(nil, param);
	}

	//implements receiver <<.param sender
	makeConnection { | sender, param = \in, replace = false |
		//Can't connect AlgaNode to itself
		if(this === sender, { "Can't connect an AlgaNode to itself".error; ^this });

		//Connect interpSynth to the sender's synthBus
		AlgaSpinRoutine.waitFor( { (this.instantiated).and(sender.instantiated) }, {
			this.newInterpConnectionAtParam(sender, param, replace:replace);
		});
	}

	//arg is the sender. it can also be a number / array to set individual values
	<< { | sender, param = \in |
		if(sender.isAlgaNode, {
			if(this.server != sender.server, {
				("Trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
			this.makeConnection(sender, param);
		}, {
			if(sender.isNumberOrArray, {
				this.makeConnection(sender, param);
			}, {
				("Trying to enstablish a connection from an invalid AlgaNode: " ++ sender).error;
			});
		});
	}

	//arg is the receiver
	>> { | receiver, param = \in |
        if(receiver.isAlgaNode, {
			if(this.server != receiver.server, {
				("Trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
            receiver.makeConnection(this, param);
        }, {
			("Trying to enstablish a connection to an invalid AlgaNode: " ++ receiver).error;
        });
	}

	//add to already running nodes (mix)
	<<+ { | sender, param = \in |
		if(sender.isAlgaNode, {
			if(this.server != sender.server, {
				("Trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
			this.makeConnection(sender, param);
		}, {
			("Trying to enstablish a connection from an invalid AlgaNode: " ++ sender).error;
		});
	}

	//add to already running nodes (mix)
	>>+ { | receiver, param = \in |
        if(receiver.isAlgaNode, {
			if(this.server != receiver.server, {
				("Trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
            receiver.makeConnection(this, param);
        }, {
			("Trying to enstablish a connection to an invalid AlgaNode: " ++ receiver).error;
        });
	}

    //Replace a mix entry ???
    <<! { | sender, previousSender, param = \in |

    }

	//resets to the default value in controlNames
	//OR, if provided, to the value of the original args that were used to create the node
	//previousSender is used in case of mixing, to only remove that one
	<| { | param = \in, previousSender = nil |
		//Also remove inNodes / outNodes / fadeTimeConnections
		if(previousSender != nil, {
			if(previousSender.isAlgaNode, {
				AlgaSpinRoutine.waitFor( { (this.instantiated).and(previousSender.instantiated) }, {
					this.removeInterpConnectionAtParam(previousSender, param);
				});
			}, {
				("Trying to remove a connection to an invalid AlgaNode: " ++ previousSender).error;
			})
		}, {
			AlgaSpinRoutine.waitFor( { this.instantiated }, {
				this.removeInterpConnectionAtParam(nil, param);
			});
		})
	}

	//replace connections FROM this
	replaceConnections {
        //inNodes are already handled in dispatchNode(replace:true)

		//outNodes. Remake connections that were in place with receivers.
		//This will effectively trigger interpolation process.
		outNodes.keysValuesDo({ | receiver, paramsSet |
			paramsSet.do({ | param |
				receiver.makeConnection(this, param, replace:true);
			});
		});
	}

	//replace content of the node, re-making all the connections.
	//If this was connected to a number / array, should I restore that value too or keep the new one?
	replace { | obj, args |
        var wasPlaying = false;
		//re-init groups if clear was used
		var initGroups = if(group == nil, { true }, { false });

		//In case it has been set to true when clearing, then replacing before clear ends!
		toBeCleared = false;

        //If it was playing, free previous playSynth
        if(isPlaying, {
            this.stop;
            wasPlaying = true;
        });

		//This doesn't work with feedbacks, as synths would be freed slightly before
		//The new ones finish the rise, generating click. These should be freed
		//When the new synths/busses are surely instantiated on the server!
		//The cheap solution that it's in place now is to wait 0.5 longer than longestFadeTime...
		//Work out a better solution!
		this.freeAllSynths(false, false);
		this.freeAllBusses;

		//New one
		this.dispatchNode(obj, args, initGroups, true);

		//Re-enstablish connections that were already in place
		this.replaceConnections;

        //If node was playing, or .replace has been called while .stop / .clear, play again
        if(wasPlaying.or(beingStopped), {
            this.play;
        })
	}

	//Execute <| on all outNodes' parameters that are connected to this
	removeConnectionFromReceivers {
		outNodes.keysValuesDo({ | receiver, paramsSet |
			paramsSet.do({ | param |
				receiver.perform('<|', param);
			});
		});
	}

	//Clears it all... It should do some sort of fading
	clear {
		//If synth had connections, run <| on the receivers (so it resets to defaults)
		this.removeConnectionFromReceivers;

		//This could be overwritten if .replace is called
		toBeCleared = true;

        //Stop playing (if it was playing at all)
        this.freePlaySynth();

		fork {
			//Wait time before clearing groups and busses...
			longestTime.wait;

			//this.freeInterpNormSynths(false, true);
			this.freeAllGroups(true); //I can just remove the groups, as they contain the synths
			this.freeAllBusses(true);

			//Reset all instance variables
			this.resetSynth;
			this.resetInterpNormSynths;
			this.resetGroups;
			this.resetInOutNodesDicts;
		}
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

	//Move this node's group before another node's one
	moveBefore { | node |
		group.moveBefore(node.group);
	}

	//Move this node's group after another node's one
	moveAfter { | node |
		group.moveAfter(node.group);
	}

    createPlaySynth {
        if((isPlaying.not).or(beingStopped), {
            var playSynthSymbol;

            if(rate == \control, { "Cannot play a kr AlgaNode".error; ^nil; });

            playSynthSymbol = ("alga_play_" ++ numChannels).asSymbol;

            playSynth = Synth(
                playSynthSymbol,
                [\in, synthBus.busArg, \gate, 1, \fadeTime, playTime],
                playGroup
            );
            isPlaying = true;
            beingStopped = false;
        })
    }

    freePlaySynth {
        if(isPlaying, {
            playSynth.set(\gate, 0, \fadeTime, playTime);
            isPlaying = false;
            beingStopped = true;
        })
    }

	play {
		AlgaSpinRoutine.waitFor({ this.instantiated }, {
            this.createPlaySynth;
		});
	}

    stop {
		AlgaSpinRoutine.waitFor({ this.instantiated }, {
            this.freePlaySynth;
		});
    }

	isAlgaNode { ^true }
}

//Alias
AN : AlgaNode {}

+Dictionary {
	//Loop over a Dict, unpacking Set. It's used in AlgaBlock
	//to unpack inNodes of an AlgaNode
	nodesLoop { | function |
		this.keysValuesDo({
			arg key, value, i;
			if(value.class == Set, {
				value.do({ | entry |
					function.value(entry, i);
				});
			}, {
				function.value(value, i);
			});
		});
	}
}

+Object {
	isAlgaNode { ^false }
	instantiated { ^true }
	isNumberOrArray { ^((this.isNumber).or(this.isArray)) }
}

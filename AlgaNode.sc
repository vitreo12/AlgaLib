AlgaNode {
	var <server;

	//Index of the corresponding AlgaBlock in the AlgaBlocksDict
	var <>blockIndex = -1;

	//This is the time when making a new connection to this node
	var <connectionTime = 0;

    //This controls the fade in and out when .play / .stop
    var <playTime = 0;

	//The algaScheduler @ this server
	var <algaScheduler;

	//This is the longestConnectionTime between all the outNodes.
	//it's used when .replacing a node connected to something, in order for it to be kept alive
	//for all the connected nodes to run their interpolator on it
	//longestConnectionTime will be moved to AlgaBlock and applied per-block!
	var <connectionTimeOutNodes;
    var <longestConnectionTime = 0;

    //The max between longestConnectionTime and playTime
    var <longestWaitTime = 0;

	//This will be added: args passed in at creation to overwrite SynthDef's one,
	//When using <|, then, these are the ones that will be restored!
	var <objArgs;

	var <objClass;
	var <synthDef;

	var <controlNames;

	//per-parameter connectionTime
	var <paramsConnectionTime;

	var <numChannels, <rate;

	var <outs;

	var <group, <playGroup, <synthGroup, <normGroup, <interpGroup;
	var <playSynth, <synth, <normSynths, <interpSynths;
	var <synthBus, <normBusses, <interpBusses;

	var <inNodes, <outNodes;

	//keep track of current \default nodes
	var <currentDefaults;

	var <paramChansMapping;

	var <isPlaying = false;
	var <toBeCleared = false;
    var <beingStopped = false;

	*new { | obj, args, connectionTime = 0, playTime = 0, outsMapping, server |
		^super.new.init(obj, args, connectionTime, playTime, outsMapping, server)
	}

    init { | argObj, argArgs, argConnectionTime = 0, argPlayTime = 0, outsMapping, argServer |
		//Default server if not specified otherwise
		server = argServer ? Server.default;

		//AlgaScheduler from specific server
		algaScheduler = Alga.algaSchedulers[server];
		if(algaScheduler == nil, {
			(
				"Can't retrieve correct AlgaScheduler for server " ++
				server.name ++
				". Has Alga.boot been called on it?"
			).error;
			^nil;
		});

		//param -> ControlName
		controlNames = IdentityDictionary(10);

		//param -> connectionTime
		paramsConnectionTime = IdentityDictionary(10);

		//These are only one per param. All the mixing normalizers will be summed at the bus anyway.
		//\param -> normBus
		normBusses   = IdentityDictionary(10);

		//IdentityDictionary of IdentityDictonaries: (needed for mixing)
		//\param -> IdentityDictionary(sender -> interpBus)
		interpBusses = IdentityDictionary(10);

		//IdentityDictionary of IdentityDictonaries: (needed for mixing)
		//\param -> IdentityDictionary(sender -> normSynth)
		normSynths   = IdentityDictionary(10);

		//IdentityDictionary of IdentityDictonaries: (needed for mixing)
		//\param -> IdentityDictionary(sender -> interpSynth)
		interpSynths = IdentityDictionary(10);

		//Per-argument connections to this AlgaNode. These are in the form:
		//(param -> IdentitySet[AlgaNode, AlgaNode...]). Multiple AlgaNodes are used when
		//using the mixing <<+ / >>+
		inNodes = IdentityDictionary(10);

		//outNodes are not indexed by param name, as they could be attached to multiple nodes with same param name.
		//they are indexed by identity of the connected node, and then it contains a IdentitySet of all parameters
		//that it controls in that node (AlgaNode -> IdentitySet[\freq, \amp ...])
		outNodes = IdentityDictionary(10);

		//Chans mapping from inNodes... How to support <<+ / >>+ ???
		//IdentityDictionary of IdentityDictionaries:
		//\param -> IdentityDictionary(sender -> paramChanMapping)
		paramChansMapping = IdentityDictionary(10);

		//This keeps track of current \default nodes for every param.
		//These are then used to restore default connections on <| or << after the param being a mix one (<<+)
		currentDefaults = IdentityDictionary(10);

		//Keeps all the connectionTimes of the connected nodes
		connectionTimeOutNodes = IdentityDictionary(10);

		//starting connectionTime (using the setter so it also sets longestConnectionTime)
		this.connectionTime_(argConnectionTime, true);
        this.playTime_(argPlayTime);

		//Dispatch node creation
		this.dispatchNode(argObj, argArgs, true, outsMapping:outsMapping);
	}

	setParamsConnectionTime { | val, all = false, param |
		//If all, set all paramConnectionTime regardless of their previous value
		if(all, {
			paramsConnectionTime.keysValuesChange({ val });
		}, {
			//If not all, only set new param value if param != nil
			if(param != nil, {
				var paramConnectionTime = paramsConnectionTime[param];
				if(paramConnectionTime != nil, {
					paramsConnectionTime[param] = val;
				}, {
					("Invalid param to set connection time for: " ++ param).error;
				});
			}, {
				//This will just change the
				//paramConnectionTime for paramConnectionTimes that haven't been explicitly modified
				paramsConnectionTime.keysValuesChange({ | param, paramConnectionTime |
					if(paramConnectionTime == connectionTime, { val }, { paramConnectionTime });
				});
			});
		});
	}

	//connectionTime / connectTime / ct / interpolationTime / interpTime / it
	connectionTime_ { | val, all = false, param |
		if(val < 0, { val = 0 });
		//this must happen before setting connectionTime, as it's been used to set
		//paramConnectionTimes, checking against the previous connectionTime (before updating it)
		this.setParamsConnectionTime(val, all, param);

		//Only set global connectionTime if param is nil
		if(param == nil, {
			connectionTime = val;
		});

		this.calculateLongestConnectionTime(val);
	}

	//Convenience wrappers
	setAllConnectionTime { | val |
		this.connectionTime_(val, true);
	}

	allct { | val |
		this.connectionTime_(val, true);
	}

	allit { | val |
		this.connectionTime_(val, true);
	}

	act { | val |
		this.connectionTime_(val, true);
	}

	ait { | val |
		this.connectionTime_(val, true);
	}

	setParamConnectionTime { | param, val |
		this.connectionTime_(val, false, param);
	}

	paramct { | param, val |
		this.connectionTime_(val, false, param);
	}

	pct { | param, val |
		this.connectionTime_(val, false, param);
	}

	paramit { | param, val |
		this.connectionTime_(val, false, param);
	}

	pit { | param, val |
		this.connectionTime_(val, false, param);
	}

    connectTime_ { | val, all = false, param | this.connectionTime_(val, all, param) }

    connectTime { ^connectionTime }

	ct_ { | val, all = false, param | this.connectionTime_(val, all, param) }

	ct { ^connectionTime }

    interpolationTime_ { | val, all = false, param | this.connectionTime_(val, all, param) }

    interpolationTime { ^connectionTime }

	interpTime_ { | val, all = false, param | this.connectionTime_(val, all, param) }

	interpTime { ^connectionTime }

	it_ { | val, all = false, param | this.connectionTime_(val, all, param) }

	it { ^connectionTime }

	//playTime
    playTime_ { | val |
		if(val < 0, { val = 0 });
        playTime = val;
        this.calculateLongestWaitTime;
    }

    pt { ^playTime }

    pt_ { | val | this.playTime_(val) }

    //maximum between longestConnectionTime and playTime...
	//is this necessary? Yes it is, cause if running .clear on the receiver,
	//I need to be sure that if .replace or .clear is set to a sender, they will be longer
	//than the .playTime of the receiver being cleared.
    calculateLongestWaitTime {
        longestWaitTime = max(longestConnectionTime, playTime);
    }

	//calculate longestConnectionTime
	calculateLongestConnectionTime { | argConnectionTime, topNode = true |
		longestConnectionTime = max(connectionTime, argConnectionTime);

		//Doing the check instead of .max cause it's faster, imagine there are a lot of entries.
		connectionTimeOutNodes.do({ | val |
			if(val > longestConnectionTime, { longestConnectionTime = val });
		});

        this.calculateLongestWaitTime;

		//Only run this on the nodes that are strictly connected to the one
		//which calculateLongestConnectionTime was called on (topNode = true)
		if(topNode, {
			inNodes.do({ | sendersSet |
				sendersSet.do({ | sender |
					//Update sender's connectionTimeOutNodes and run same function on it
					sender.connectionTimeOutNodes[this] = longestConnectionTime;
                    sender.calculateLongestConnectionTime(longestConnectionTime, false);
				});
			});
		});
	}

	outsMapping {
		^outs
	}

	createAllGroups {
		if(group == nil, {
			group = Group(this.server);
            playGroup = Group(group);
			synthGroup = Group(group); //It could be ParGroup here for supernova
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
				//Wait longestWaitTime, then free
				fork {
					longestWaitTime.wait;

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
			//interpBusses[paramName] = AlgaBus(server, paramNumChannels + 1, paramRate);
			interpBusses[paramName][\default] = AlgaBus(server, paramNumChannels + 1, paramRate);
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
				//going on. Simply wait longer than longestConnectionTime (which will be the time the replaced
				//node will take to interpolate to the previous receivers) and then free all the previous stuff
				(longestWaitTime + 1.0).wait;

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

			//Free prev busses after longestWaitTime
			fork {
				//Cheap solution when having to replacing a synth that had other interp stuff
				//going on. Simply wait longer than longestConnectionTime (which will be the time the replaced
				//node will take to interpolate to the previous receivers) and then free all the previous stuff
				(longestWaitTime + 1.0).wait;

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
	dispatchNode { | obj, args, initGroups = false, replace = false, keepChannelsMapping = false, outsMapping |
		objClass = obj.class;

		//If there is a synth playing, set its instantiated status to false:
		//this is mostly needed for .replace to work properly and wait for the new synth
		//to be instantiated!
		if(synth != nil, { synth.instantiated = false });

		//Symbol
		if(objClass == Symbol, {
			this.dispatchSynthDef(obj, args, initGroups, replace, keepChannelsMapping:keepChannelsMapping);
		}, {
			//Function
			if(objClass == Function, {
				this.dispatchFunction(obj, args, initGroups, replace, keepChannelsMapping:keepChannelsMapping, outsMapping:outsMapping);
			}, {
				("AlgaNode: class '" ++ objClass ++ "' is invalid").error;
				this.clear;
			});
		});
	}

	//Dispatch a SynthDef
	dispatchSynthDef { | obj, args, initGroups = false, replace = false, keepChannelsMapping = false |
		var synthDescControlNames;
		var synthDesc = SynthDescLib.global.at(obj);

		if(synthDesc == nil, {
			("Invalid AlgaSynthDef: '" ++ obj.asString ++ "'").error;
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
		this.createControlNamesAndParamsConnectionTime(synthDescControlNames);

		numChannels = synthDef.numChannels;
		rate = synthDef.rate;

		//Create all utilities
		if(initGroups, { this.createAllGroups });
		this.createAllBusses;

		//Create actual synths
		this.createAllSynths(synthDef.name, replace, keepChannelsMapping:keepChannelsMapping);
	}

	//Dispatch a Function
	dispatchFunction { | obj, args, initGroups = false, replace = false, keepChannelsMapping = false, outsMapping |
		//Need to wait for server's receiving the sdef
		fork {
			var synthDescControlNames;

			synthDef = AlgaSynthDef(("alga_" ++ UniqueID.next).asSymbol, obj, outsMapping:outsMapping).send(server);
			server.sync;

			synthDescControlNames = synthDef.asSynthDesc.controls;
			this.createControlNamesAndParamsConnectionTime(synthDescControlNames);

			numChannels = synthDef.numChannels;
			rate = synthDef.rate;

			//Accumulate across .replace calls? This would be weird though:
			//this way, some params mapping set 5 replace ago would be kept and eventually set.
			//As of now, only the previous ones are kept.
			if(replace.and(keepChannelsMapping), {
				var new_outs = IdentityDictionary(10);

				//copy previous ones
				outs.keysValuesDo({ | key, value |
					//Delete out of bounds entries? Or keep it for future .replaces?
					//if(value < numChannels, {
						new_outs[key] = value;
					//});
				});

				//new ones from the synthDef
				synthDef.outsMapping.keysValuesDo({ | key, value |
					//Delete out of bounds entries? Or keep it for future .replaces?
					//if(value < numChannels, {
						new_outs[key] = value;
					//});
				});

				outs = new_outs;

			}, {
				outs = synthDef.outsMapping;
			});

			//Create all utilities
			if(initGroups, { this.createAllGroups });
			this.createAllBusses;

			//Create actual synths
			this.createAllSynths(synthDef.name, replace, keepChannelsMapping:keepChannelsMapping);
		};
	}

	//Remove \fadeTime \out and \gate and generate controlNames dict entries
	createControlNamesAndParamsConnectionTime { | synthDescControlNames |
		//Reset entries first (not paramsConnectionTime, reusing old params' one?? )
		controlNames.clear;

		synthDescControlNames.do({ | controlName |
			var paramName = controlName.name;
			if((controlName.name != \fadeTime).and(
				controlName.name != \out).and(
				controlName.name != \gate).and(
				controlName.name != '?'), {

				var paramName = controlName.name;

				//Create controlNames
				controlNames[paramName] = controlName;

				//Create paramsConnectionTime ... keeping same value among .replace calls.
				//Only replace if entry is clear
				if(paramsConnectionTime[paramName] == nil, {
					paramsConnectionTime[paramName] = connectionTime;
				});

				//Create IdentityDictionaries for each interpNode
				interpBusses[paramName] = IdentityDictionary();
				normSynths[paramName] = IdentityDictionary();
				interpSynths[paramName] = IdentityDictionary();
				paramChansMapping[paramName] = IdentityDictionary();
			});
		});
	}

	resetSynth {
		if(toBeCleared, {
			//IdentitySet to nil (should it fork?)
			synth = nil;
			synthDef = nil;
			controlNames.clear;
			paramsConnectionTime.clear;
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
	//Synth always uses longestConnectionTime, in order to make sure that everything connected to it
	//will have time to run fade ins and outs when running .replace!
	createSynth { | defName |
		//synth's \fadeTime is longestWaitTime... It could probably be removed here
		var synthArgs = [\out, synthBus.index, \fadeTime, longestWaitTime];

        /*
		//Add the param busses (which have already been allocated)
		//Should this connect here or in createInterpNormSynths? (now it's done in createInterpNormSynths)
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

	resetInterpNormDicts {
		interpSynths.clear;
		normSynths.clear;
		interpBusses.clear;
		normBusses.clear;
	}

	//First creation: use defaults
	createInterpNormSynths { | replace = false, keepChannelsMapping = false |
		controlNames.do({ | controlName |
			var interpSymbol, normSymbol;
			var interpBus, normBus, interpSynth, normSynth;

			var paramName = controlName.name;
			var paramNumChannels = controlName.numChannels;
			var paramRate = controlName.rate;
			var paramDefault = controlName.defaultValue;

			//e.g. \alga_interp_audio1_control1
			interpSymbol = (
				"alga_interp_" ++
				paramRate ++
				paramNumChannels ++
				"_" ++
				paramRate ++
				paramNumChannels
			).asSymbol;

			//e.g. \alga_norm_audio1
			normSymbol = (
				"alga_norm_" ++
				paramRate ++
				paramNumChannels
			).asSymbol;

			interpBus = interpBusses[paramName][\default];
			normBus = normBusses[paramName];

            //If replace, connect to the pervious bus, not default
            //This wouldn't work with mixing for now...
            if(replace, {
                var sendersSet = inNodes[paramName];

				if(sendersSet.size > 1, { "Restoring mixing parameters is not implemented yet"; ^nil; });

				if(sendersSet != nil, {
                    if(sendersSet.size == 1, {
                        var prevSender;

						//nil will use Array.series
						var oldParamChansMapping = nil;
						var channelsMapping;

						//Use previous entry for the channel mapping, otherwise, nil.
						//nil will generate Array.series(...) in calculateSenderChansMappingArray
						if(keepChannelsMapping, {
							oldParamChansMapping = paramChansMapping[paramName][prevSender];
						});

						//Sets can't be indexed, need to loop over even if it's just one entry
						sendersSet.do({ | sender | prevSender = sender });

						//overwrite interp symbol considering the senders' num channels!
						interpSymbol = (
							"alga_interp_" ++
							prevSender.rate ++
							prevSender.numChannels ++
							"_" ++
							paramRate ++
							paramNumChannels
						).asSymbol;

						//Calculate the array for channelsMapping
						channelsMapping = this.calculateSenderChansMappingArray(
							paramName,
							prevSender,
							oldParamChansMapping,
							prevSender.numChannels,
							paramNumChannels,
							false
						);

                        interpSynth = AlgaSynth.new(
                            interpSymbol,
                            [
								\in, prevSender.synthBus.busArg,
								\out, interpBus.index,
								\indices, channelsMapping,
								\fadeTime, 0
							],
                            interpGroup
                        )
                    })
                }, {
                    //sendersSet is nil, run the default one
                    interpSynth = AlgaSynth.new(
                        interpSymbol,
                        [\in, paramDefault, \out, interpBus.index, \fadeTime, 0],
                        interpGroup
                    );
                })
            }, {
                //No previous nodes connected: create a new interpSynth with the paramDefault value
                //Instantiated right away, with no \fadeTime, as it will directly be connected to
                //synth's parameter
                interpSynth = AlgaSynth.new(
                    interpSymbol,
                    [\in, paramDefault, \out, interpBus.index, \fadeTime, 0],
                    interpGroup
                );
            });

			//Instantiated right away, with no \fadeTime, as it will directly be connected to
			//synth's parameter (synth is already reading from all the normBusses)
			normSynth = AlgaSynth.new(
				normSymbol,
				[\args, interpBus.busArg, \out, normBus.index, \fadeTime, 0],
				normGroup
			);

			//interpSynths and normSynths are a IdentityDict of IdentityDicts
			interpSynths[paramName][\default] = interpSynth;
			normSynths[paramName][\default] = normSynth;

			//Connect synth's parameter to the normBus
			synth.set(paramName, normBus.busArg);
		});
	}

	createAllSynths { | defName, replace = false, keepChannelsMapping = false |
		this.createSynth(defName);
		this.createInterpNormSynths(replace, keepChannelsMapping:keepChannelsMapping);
	}

	calculateSenderChansMappingArray { | param, sender, senderChansMapping, senderNumChans, paramNumChans, updateParamChansMapping = true |
		var actualSenderChansMapping = senderChansMapping;

		//Connect with outMapping symbols. Retrieve it from the sender
		if(actualSenderChansMapping.class == Symbol, {
			actualSenderChansMapping = sender.outsMapping[actualSenderChansMapping];
		});

		//Update entry in Dict with the non-modified one (used in .replace then)
		if(updateParamChansMapping, {
			paramChansMapping[param][sender] = actualSenderChansMapping;
		});

		//Standard case (perhaps, overkill. This is default of the \indices param anyway)
		if(actualSenderChansMapping == nil, { ^(Array.series(paramNumChans)) });

		if(actualSenderChansMapping.isSequenceableCollection, {
			//Also allow [\out1, \out2] here.
			actualSenderChansMapping.do({ | entry, index |
				if(entry.class == Symbol, {
					actualSenderChansMapping[index] = sender.outsMapping[entry];
				});
			});

			//flatten the array, modulo around the max number of channels and reshape according to param num chans
			^((actualSenderChansMapping.flat % senderNumChans).reshape(paramNumChans));
		}, {
			if(actualSenderChansMapping.isNumber, {
				^(Array.fill(paramNumChans, { actualSenderChansMapping }));
			}, {
				"senderChansMapping must be a number or an array. Using default one.".error;
				^(Array.series(paramNumChans));
			});
		});
	}

	//Run when <<+
	createMixInterpSynthInterpBusBusNormSynthAtParam { | sender, param = \in, replace = false, senderChansMapping |
		//Only run if replace OR no param/sender combination is present already, otherwise it means it was already connected!
		if(replace.or(this.mixerParamContainsSender(param, sender).not), {
			var controlName;
			var paramNumChannels, paramRate;
			var normSymbol, normBus;
			var interpBus, normSynth;

			controlName = controlNames[param];
			normBus = normBusses[param];

			paramNumChannels = controlName.numChannels;
			paramRate = controlName.rate;

			normSymbol = (
				"alga_norm_" ++
				paramRate ++
				paramNumChannels
			).asSymbol;

			//interpBus has always one more channel for the envelope
			interpBus = AlgaBus(server, paramNumChannels + 1, paramRate);

			normSynth = AlgaSynth.new(
				normSymbol,
				[\args, interpBus.busArg, \out, normBus.index, \fadeTime, 0],
				normGroup
			);

			interpBusses[param][sender] = interpBus;
			normSynths[param][sender] = normSynth;

			this.createInterpSynthAtParam(sender, param,
				mix:true, senderChansMapping:senderChansMapping
			);
		}, {
			("The AlgaNode is already mixed at param " ++ param).warn;
		});
	}

	//Used at every <<, >>, <<+, >>+, <|
	createInterpSynthAtParam { | sender, param = \in, mix = false, senderChansMapping |
		var controlName, paramConnectionTime;
		var paramNumChannels, paramRate;
		var senderNumChannels, senderRate;

		var senderChansMappingToUse;

		var interpSymbol;

		var interpBusAtParam;
		var interpBus, interpSynth;

		var senderSym = sender;

		if(mix == true, {
			if(sender == nil, {
				senderSym = \default;
			}, {
				//create an ad-hoc entry... This won't ever be triggered for now
				//(mix has been already set to false in makeConnection for number/array)
				if(sender.isNumberOrArray, {
					senderSym = (sender.asString ++ "_" ++ UniqueID.next.asString).asSymbol;
				});
			});
		}, {
			senderSym = \default;
		});

		controlName = controlNames[param];
		paramConnectionTime = paramsConnectionTime[param];

		if((controlName.isNil).or(paramConnectionTime.isNil), {
			("Invalid param for interp synth to free: " ++ param).error;
			^this
		});

		//If -1, or invalid, set to global connectionTime
		if(paramConnectionTime < 0, { paramConnectionTime = connectionTime });

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
			"alga_interp_" ++
			senderRate ++
			senderNumChannels ++
			"_" ++
			paramRate ++
			paramNumChannels
		).asSymbol;

		//get interp bus ident dict at specific param
		interpBusAtParam = interpBusses[param];
		if(interpBusAtParam == nil, { ("Invalid interp bus at param " ++ param).error; ^this });

		//Try to get sender one.
		//If not there, get the default one (and assign it to sender for both interpBus and normSynth at param)
		interpBus = interpBusAtParam[senderSym];
		if(interpBus == nil, {
			interpBus = interpBusAtParam[\default];
			if(interpBus == nil, { ("Invalid interp bus at param " ++ param ++ " and node " ++ senderSym.asString).error; ^this });
			interpBusAtParam[senderSym] = interpBus;
		});

		senderChansMappingToUse = this.calculateSenderChansMappingArray(
			param,
			sender, //must be sender! not senderSym!
			senderChansMapping,
			senderNumChannels,
			paramNumChannels,
		);

		//new interp synth, with input connected to sender and output to the interpBus
		//THIS USES connectionTime!!
        //THIS, together with freeInterpSynthAtParam, IS THE WHOLE CORE OF THE INTERPOLATION BEHAVIOUR!!!
		if(sender.isAlgaNode, {
			//If mix, spawn a fadeIn synth, which balances out the interpSynth's envelope for normSynth
			if(mix == true, {
				var fadeInSymbol = ("alga_fadeIn_" ++
					paramRate ++
					paramNumChannels
				).asSymbol;

				AlgaSynth.new(
					fadeInSymbol,
					[
						\out, interpBus.index,
						\fadeTime, paramConnectionTime,
					],
					interpGroup
				);
			});

			//Read \in from the sender's synthBus
            interpSynth = AlgaSynth.new(
                interpSymbol,
                [
					\in, sender.synthBus.busArg,
					\out, interpBus.index,
					\indices, senderChansMappingToUse,
					\fadeTime, paramConnectionTime
				],
                interpGroup
            );
		}, {
			//Used in <| AND << with number / array
			//if sender is nil, restore the original default value. This is used in <|
			var paramVal;

			if(sender == nil, {
				paramVal = controlName.defaultValue;
			}, {
                //If not nil, check if it's a number or array. Use it if that's the case
				if(sender.isNumberOrArray,  {
					paramVal = sender;
				}, {
					"Invalid paramVal for AlgaNode".error;
					^nil;
				});
			});

			interpSynth = AlgaSynth.new(
				interpSymbol,
				[
					\in, paramVal,
					\out, interpBus.index,
					\indices, senderChansMappingToUse,
					\fadeTime, paramConnectionTime
				],
				interpGroup
			);
		});

		//Add to interpSynths for the param
		interpSynths[param][senderSym] = interpSynth;

		//If present, remove the \default stuff from interpSynths
		//Store current \default (needed when mix == true)
		if((senderSym == \default).and(mix == false), {
			currentDefaults[param] = sender;
		});
	}

	//Default now and useConnectionTime to true for synths.
	//Synth always uses longestConnectionTime, in order to make sure that everything connected to it
	//will have time to run fade ins and outs
	freeSynth { | useConnectionTime = true, now = true |
		if(now, {
			if(synth != nil, {
				//synth's fadeTime is longestWaitTime!
				synth.set(\gate, 0, \fadeTime, if(useConnectionTime, { longestWaitTime }, { 0 }));

				//this.resetSynth;
			});
		}, {
            //Needs to be deep copied (a new synth could be instantiated meanwhile)
			var prevSynth = synth.copy;

			fork {
				//Cheap solution when having to replacing a synth that had other interp stuff
				//going on. Simply wait longer than longestWaitTime (which will be the time the replaced
				//node will take to interpolate to the previous receivers) and then free all the previous stuff
				(longestWaitTime + 1.0).wait;

				if(prevSynth != nil, {
					prevSynth.set(\gate, 0, \fadeTime, 0);
				});
			}
		});
	}

	//Default now and useConnectionTime to true for synths
	freeInterpNormSynths { | useConnectionTime = true, now = true |
		if(now, {
			//Free synths now
			interpSynths.do({ | interpSynthsAtParam |
				interpSynthsAtParam.do({ | interpSynth |
					interpSynth.set(\gate, 0, \fadeTime, 0);
				});
			});

			normSynths.do({ | normSynthsAtParam |
				normSynthsAtParam.do({ | normSynth |
					normSynth.set(\gate, 0, \fadeTime, 0);
				});
			});

			//this.resetInterpNormSynths;
		}, {
			//Dictionaries need to be deep copied
			var prevInterpSynths = interpSynths.copy;
			var prevNormSynths = normSynths.copy;

			fork {
				//Cheap solution when having to replacing a synth that had other interp stuff
				//going on. Simply wait longer than longestWaitTime (which will be the time the replaced
				//node will take to interpolate to the previous receivers) and then free all the previous stuff
				(longestWaitTime + 1.0).wait;

				prevInterpSynths.do({ | interpSynthsAtParam |
					interpSynthsAtParam.do({ | interpSynth |
						interpSynth.set(\gate, 0, \fadeTime, 0);
					});
				});

				prevNormSynths.do({ | normSynthsAtParam |
					normSynthsAtParam.do({ | normSynth |
						normSynth.set(\gate, 0, \fadeTime, 0);
					});
				});
			}
		});
	}

	freeAllSynths { | useConnectionTime = true, now = true |
		this.freeInterpNormSynths(useConnectionTime, now);
		this.freeSynth(useConnectionTime, now);
	}

	freeAllSynthOnNewInstantiation { | useConnectionTime = true, now = true |
		this.freeAllSynths(useConnectionTime, now);
	}

	//Free the entire mix node at specific param
	freeMixNodeAtParam { | sender, param = \in, paramConnectionTime, cleanupDicts = false |
		var interpSynthAtParam = interpSynths[param][sender];
		if(interpSynthAtParam != nil, {
			//Only run fadeOut and remove normSynth if they are also not the ones that are used for \default.
			//\default will then be re-used when disconnecting all mixing parameters!
			if(sender != currentDefaults[param], {
				var normSynthAtParam = normSynths[param][sender];
				var interpBusAtParam = interpBusses[param][sender];

				var fadeOutSymbol = ("alga_fadeOut_" ++
					interpBusAtParam.rate ++
					(interpBusAtParam.numChannels - 1) //it has one more for env. need to remove that from symbol
				).asSymbol;

				AlgaSynth.new(
					fadeOutSymbol,
					[
						\out, interpBusAtParam.index,
						\fadeTime, paramConnectionTime,
					],
					interpGroup
				);

				normSynthAtParam.set(\gate, 0, \fadeTime, paramConnectionTime);
			});

			interpSynthAtParam.set(\gate, 0, \fadeTime, paramConnectionTime);
		});

		if(cleanupDicts, {
			interpSynths[param].removeAt(sender);
			interpBusses[param].removeAt(sender);
			normSynths[param].removeAt(sender);
		});
	}

	//Free interpSynth at param. This is also used in .replace for both mix entries and normal ones
	//THIS USES connectionTime!!
	//THIS, together with freeInterpSynthAtParam, IS THE WHOLE CORE OF THE INTERPOLATION BEHAVIOUR!!!
	freeInterpSynthAtParam { | sender, param = \in, mix = false |
		var interpSynthsAtParam = interpSynths[param];
		var paramConnectionTime = paramsConnectionTime[param];

		//If -1, or invalid, set to global connectionTime
		if((paramConnectionTime < 0).or(paramConnectionTime == nil), { paramConnectionTime = connectionTime });

		//Free them all (check if there were mix entries).
		//sender == nil comes from <|
		//mix == false comes from <<
		//mix == true comes from <<+
		if((sender == nil).or(mix == false), {
			//If interpSynthsAtParam length is more than one, the param has mix entries. Fade them all out.
			if(interpSynthsAtParam.size > 1, {
				interpSynthsAtParam.keysValuesDo({ | interpSender, interpSynthAtParam  |
					if(interpSender != \default, { // ignore \default !
						this.freeMixNodeAtParam(interpSender, param, paramConnectionTime, true);
					});
				});
			}, {
				//Just one entry in the dict (\default), just free the interp synth!
				interpSynthsAtParam.do({ | interpSynthAtParam |
					interpSynthAtParam.set(\gate, 0, \fadeTime, paramConnectionTime);
				});
			});
		}, {
			//mix == true, only free the one (disconnect function)
			this.freeMixNodeAtParam(sender, param, paramConnectionTime, true);
		});
	}

	//param -> IdentitySet[AlgaNode, AlgaNode, ...]
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
			//Empty entry OR not doing mixing, create new IdentitySet. Otherwise, add to existing
			if((inNodes[param] == nil).or(mix.not), {
				inNodes[param] = IdentitySet[sender];
			}, {
				inNodes[param].add(sender);
			})
		});

		mix.postln;
		inNodes.postln;
	}

	//AlgaNode -> IdentitySet[param, param, ...]
	addOutNode { | receiver, param = \in |
		//Empty entry, create IdentitySet. Otherwise, add to existing
		if(outNodes[receiver] == nil, {
			outNodes[receiver] = IdentitySet[param];
		}, {
			outNodes[receiver].add(param);
		});
	}

	//add entries to the inNodes / outNodes / connectionTimeOutNodes of the two AlgaNodes
	addInOutNodesDict { | sender, param = \in, mix = false |
		//This will replace the entries on new connection (as mix == false)
		this.addInNode(sender, param, mix);

		//This will add the entries to the existing IdentitySet, or create a new one
		if(sender.isAlgaNode, {
			sender.addOutNode(this, param);

			//Add to connectionTimeOutNodes and recalculate longestConnectionTime
			sender.connectionTimeOutNodes[this] = this.connectionTime;
			sender.calculateLongestConnectionTime(this.connectionTime);
		});
	}

	removeInOutNodeAtParam { | sender, param = \in |
		//Just remove one param from sender's set at this entry
		sender.outNodes[this].remove(param);

		//If IdentitySet is now empty, remove it entirely
		if(sender.outNodes[this].size == 0, {
			sender.outNodes.removeAt(this);
		});

		//Remove the specific param / sender combination from inNodes
		inNodes[param].remove(sender);

		//If IdentitySet is now empty, remove it entirely
		if(inNodes[param].size == 0, {
			inNodes.removeAt(param);
		});

		//Recalculate longestConnectionTime too...
		//SHOULD THIS BE DONE AFTER THE SYNTHS ARE CREATED???
		//(Right now, this happens before creating new synths)
		sender.connectionTimeOutNodes[this] = 0;
		sender.calculateLongestConnectionTime(0);
	}

	//Remove entries from inNodes / outNodes / connectionTimeOutNodes for all involved nodes
	removeInOutNodesDict { | previousSender = nil, param = \in |
		var previousSenders = inNodes[param];
		if(previousSenders == nil, { ( "No previous connection enstablished at param:" ++ param).error; ^this; });

		previousSenders.do({ | sender |
			var sendersParamsSet = sender.outNodes[this];
			if(sendersParamsSet != nil, {
				//no previousSender specified: remove them all!
				if(previousSender == nil, {
					this.removeInOutNodeAtParam(sender, param);
				}, {
					//If specified previousSender, only remove that one (in mixing scenarios)
					if(sender == previousSender, {
						this.removeInOutNodeAtParam(sender, param);
					})
				})
			})
		});
	}

	//Clear the dicts
	resetInOutNodesDicts {
		if(toBeCleared, {
			inNodes.clear;
			outNodes.clear;
		});
	}

	//New interp connection at specific parameter
	newInterpConnectionAtParam { | sender, param = \in, replace = false, senderChansMapping |
		var controlName = controlNames[param];
		if(controlName == nil, {
			("Invalid param to create a new interp synth for: " ++ param).error;
			^this;
		});

		//Add proper inNodes / outNodes / connectionTimeOutNodes
		this.addInOutNodesDict(sender, param, mix:false);

		//Re-order groups
		//Actually reorder the block's nodes ONLY if not running .replace
		//(no need there, they are already ordered, and it also avoids a lot of problems
		//with feedback connections)
		if(replace.not, {
			AlgaBlocksDict.createNewBlockIfNeeded(this, sender);
		});

		//Free previous interp synth(s) (fades out)
		this.freeInterpSynthAtParam(sender, param);

        //Spawn new interp synth (fades in)
        this.createInterpSynthAtParam(sender, param, senderChansMapping:senderChansMapping);
	}

	//New mix connection at specific parameter
	newMixConnectionAtParam { | sender, param = \in, replace = false, senderChansMapping |
		var controlName = controlNames[param];
		if(controlName == nil, {
			("Invalid param to create a new interp synth for: " ++ param).error;
			^this;
		});

		//Add proper inNodes / outNodes / connectionTimeOutNodes
		this.addInOutNodesDict(sender, param, mix:true);

		//Re-order groups
		//Actually reorder the block's nodes ONLY if not running .replace
		//(no need there, they are already ordered, and it also avoids a lot of problems
		//with feedback connections)
		if(replace.not, {
			AlgaBlocksDict.createNewBlockIfNeeded(this, sender);
		});

		//If replace = true, the call comes from replaceMix, which already calls freeInterpSynthAtParam!

		//Spawn new interp mix node
		this.createMixInterpSynthInterpBusBusNormSynthAtParam(sender, param,
			replace:replace, senderChansMapping:senderChansMapping
		);
	}

	//Used in <| and replaceMix
	removeInterpConnectionAtParam { | previousSender = nil, param = \in  |
		var controlName = controlNames[param];
		if(controlName == nil, {
			("Invalid param to reset: " ++ param).error;
			^this;
		});

		//Remove inNodes / outNodes / connectionTimeOutNodes
		this.removeInOutNodesDict(previousSender, param);

		//Re-order groups shouldn't be needed when removing connections

		//Free previous interp synth (fades out)
		this.freeInterpSynthAtParam(previousSender, param);

		//Create new interp synth with default value (or the one supplied with args at start) (fades in)
		this.createInterpSynthAtParam(nil, param);
	}

	//Cleans up all Dicts at param, leaving the \default entry only
	cleanupMixBussesAndSynths { | param |
		var interpBusAtParam = interpBusses[param];
		if(interpBusAtParam.size > 1, {
			var interpSynthAtParam = interpSynths[param];
			var normSynthAtParam = normSynths[param];
			interpBusAtParam.keysValuesDo({ | key, val |
				if(key != \default, {
					interpBusAtParam.removeAt(key);
					interpSynthAtParam.removeAt(key);
					normSynthAtParam.removeAt(key);
				});
			});
		});
	}

	//implements receiver <<.param sender
	makeConnectionInner { | sender, param = \in, replace = false, mix = false, senderChansMapping |
		var currentDefaultAtParam;

		if(sender.isAlgaNode.not, { "Can't connect to something that's not an AlgaNode".error; ^this });

		//Can't connect AlgaNode to itself
		if(this === sender, { "Can't connect an AlgaNode to itself".error; ^this });

		if(mix, {
			currentDefaultAtParam = currentDefaults[param];

			//trying to <<+ instead of << on first connection
			if((currentDefaultAtParam == nil), {
				mix = false;
			});

			//can't add to a num. just replace it
			if(currentDefaultAtParam.isNumberOrArray, {
				("Trying to add to a non-AlgaNode: " ++ currentDefaultAtParam.asString ++ ". Replacing it.").warn;
				mix = false;
			});

			//can't <<+ with numbers or arrays... how to track them?
			if((sender == nil).or(sender.isNumberOrArray), {
				("Mixing only works for explicit AlgaNodes.").error;
				^this;
			});
		});

		//need to re-check as mix might have changed!
		if(mix, {
			//Update currentDefaultAtParam
			if(currentDefaultAtParam.isAlgaNode, {
				if(currentDefaultAtParam != sender, {
					var interpBusAtParam = interpBusses[param];
					var interpSynthAtParam = interpSynths[param];
					var normSynthAtParam = normSynths[param];

					//Copy the \default key (but keep it in the dict! it will be useful when returning to a non-mix state!)
					interpBusAtParam[currentDefaultAtParam] = interpBusAtParam[\default];
					interpSynthAtParam[currentDefaultAtParam] = interpSynthAtParam[\default];
					normSynthAtParam[currentDefaultAtParam] = normSynthAtParam[\default];
				});
			});

			//Create new interpBus and normSynth for specific param and sender combination:
			//only if connection isn't already in the mix (overkill)
			if(this.mixerParamContainsSender(param, sender).not, {
				this.newMixConnectionAtParam(sender, param,
					replace:replace, senderChansMapping:senderChansMapping
				)
			});
		}, {
			//Connect interpSynth to the sender's synthBus
			this.newInterpConnectionAtParam(sender, param,
				replace:replace, senderChansMapping:senderChansMapping
			);

			//Cleanup interpBusses / interpSynths / normSynths from previous mix, leaving \default only
			this.cleanupMixBussesAndSynths(param);
		});

		/*
		//need to re-check as mix might have changed!
		if(mix, {
			//Create new interpBus and normSynth for specific param and sender combination,
			//only if connection isn't already in the mix
			if(this.mixerParamContainsSender(param, sender).not, {
				this.newMixConnectionAtParam(sender, param,
					replace:replace, senderChansMapping:senderChansMapping
				)
			});
		}, {
			//Connect interpSynth to the sender's synthBus
			this.newInterpConnectionAtParam(sender, param,
				replace:replace, senderChansMapping:senderChansMapping
			);

			//Cleanup interpBusses / interpSynths / normSynths from previous mix, leaving \default only
			this.cleanupMixBussesAndSynths(param);
		});
		*/
	}

	//Wrapper for scheduler
	makeConnection { | sender, param = \in, replace = false, mix = false, senderChansMapping |
		algaScheduler.addAction({ (this.instantiated).and(sender.instantiated) }, {
			this.makeConnectionInner(sender, param, replace, mix, senderChansMapping);
		});
	}

	from { | sender, param = \in, inChans |
		if(sender.isAlgaNode, {
			if(this.server != sender.server, {
				("Trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
			this.makeConnection(sender, param, senderChansMapping:inChans);
		}, {
			if(sender.isNumberOrArray, {
				this.makeConnection(sender, param, senderChansMapping:inChans);
			}, {
				("Trying to enstablish a connection from an invalid AlgaNode: " ++ sender).error;
			});
		});
	}

	//arg is the sender. it can also be a number / array to set individual values
	<< { | sender, param = \in |
		this.from(sender: sender, param: param);
	}

	to { | receiver, param = \in, outChans |
        if(receiver.isAlgaNode, {
			if(this.server != receiver.server, {
				("Trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
            receiver.makeConnection(this, param, senderChansMapping:outChans);
        }, {
			("Trying to enstablish a connection to an invalid AlgaNode: " ++ receiver).error;
        });
	}

	//arg is the receiver
	>> { | receiver, param = \in |
		this.to(receiver: receiver, param: param);
	}

	mixFrom { | sender, param = \in, inChans |
		if(sender.isAlgaNode, {
			if(this.server != sender.server, {
				("Trying to enstablish a connection between two AlgaNodes on different servers").error;
			}, {
				this.makeConnection(sender, param, mix:true, senderChansMapping:inChans);
			});
		}, {
			if(sender.isNumberOrArray, {
				this.makeConnection(sender, param, mix:true, senderChansMapping:inChans);
			}, {
				("Trying to enstablish a connection from an invalid AlgaNode: " ++ sender).error;
			});
		});
	}

	//add to already running nodes (mix)
	<<+ { | sender, param = \in |
		this.mixFrom(sender: sender, param: param);
	}

	mixTo { | receiver, param = \in, outChans |
        if(receiver.isAlgaNode, {
			if(this.server != receiver.server, {
				("Trying to enstablish a connection between two AlgaNodes on different servers").error;
			}, {
				receiver.makeConnection(this, param, mix:true, senderChansMapping:outChans);
			});
        }, {
			("Trying to enstablish a connection to an invalid AlgaNode: " ++ receiver).error;
        });
	}

	//add to already running nodes (mix)
	>>+ { | receiver, param = \in |
		this.mixTo(receiver: receiver, param: param);
	}

	replaceMixInner { | param = \in, previousSender, newSender, inChans |
		this.disconnectInner(param, previousSender, true);
		this.makeConnectionInner(newSender, param,
			replace:true, mix:true,
			senderChansMapping:inChans
		);
	}

	//Replace a mix entry at param... Practically just freeing the old one and triggering the new one.
	//This will be useful in the future if wanting to implement some kind of system to retrieve individual
	//mix entries (like, \in1, \in2). No need it for now
	replaceMix { | param = \in, previousSender, newSender, inChans |
		if(newSender.isAlgaNode.not, {
			(newSender.asString) ++ " is not an AlgaNode".error;
			^this;
		});

		algaScheduler.addAction({ (this.instantiated).and(previousSender.instantiated).and(newSender.instantiated) }, {
			var validPreviousSender = true;

			if(this.mixerParamContainsSender(param, previousSender).not, {
				(previousSender.asString ++ " was not present in the mix for param " ++ param.asString).error;
				validPreviousSender = false;
			});

			if(validPreviousSender, {
				this.replaceMixInner(param, previousSender, newSender, inChans);
			});
		});
	}

	resetParamInner { | param = \in, previousSender = nil |
		//Also remove inNodes / outNodes / connectionTimeOutNodes
		if(previousSender != nil, {
			if(previousSender.isAlgaNode, {
				this.removeInterpConnectionAtParam(previousSender, param);
			}, {
				("Trying to remove a connection to an invalid AlgaNode: " ++ previousSender).error;
			})
		}, {
			this.removeInterpConnectionAtParam(nil, param);
		})
	}

	resetParam { | param = \in, previousSender = nil |
		algaScheduler.addAction({ (this.instantiated).and(previousSender.instantiated) }, {
			this.resetParamInner(param, previousSender);
		});
	}

	//resets to the default value in controlNames
	//OR, if provided, to the value of the original args that were used to create the node
	//previousSender is used in case of mixing, to only remove that one
	<| { | param = \in, previousSender = nil |
		this.resetParam(param, previousSender);
	}

	//replace connections FROM this
	replaceConnections { | keepChannelsMapping = true |
        //inNodes are already handled in dispatchNode(replace:true)

		//outNodes. Remake connections that were in place with receivers.
		//This will effectively trigger interpolation process.
		outNodes.keysValuesDo({ | receiver, paramsSet |
			paramsSet.do({ | param |
				var oldParamChansMapping = nil;

				//Restore old channels mapping! It can either be a symbol, number or array here
				if(keepChannelsMapping, { oldParamChansMapping = receiver.paramChansMapping[param][this]; });

				//If it was a mixer connection, use replaceMix
				if(receiver.mixerParamContainsSender(param, this), {
					//use the scheduler version! don't know if receiver and this are both instantiated
					receiver.replaceMix(param, this, this, inChans:oldParamChansMapping);
				}, {
					//use the scheduler version! don't know if receiver and this are both instantiated
					//Normal connection, use makeConnection to re-enstablish it
					receiver.makeConnection(this, param,
						replace:true, senderChansMapping:oldParamChansMapping
					);
				});
			});
		});
	}

	replaceInner { | obj, args, keepChannelsMappingIn = true, keepChannelsMappingOut = true, outsMapping |
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
		//The cheap solution that it's in place now is to wait 0.5 longer than longestConnectionTime...
		//Work out a better solution!
		this.freeAllSynths(false, false);
		this.freeAllBusses;

		//RESET DICT ENTRIES? SHOULD IT BE DONE SOMEWHERE ELSE? SHOULD IT BE DONE AT ALL?
		this.resetInterpNormDicts;

		//New one
		//Just pass the entry, not the whole thingy
		this.dispatchNode(obj, args, initGroups, true,
			keepChannelsMapping:keepChannelsMappingIn, outsMapping:outsMapping
		);

		//Re-enstablish connections that were already in place
		this.replaceConnections(keepChannelsMapping:keepChannelsMappingOut);

        //If node was playing, or .replace has been called while .stop / .clear, play again
        if(wasPlaying.or(beingStopped), {
            this.play;
        })
	}

	//replace content of the node, re-making all the connections.
	//If this was connected to a number / array, should I restore that value too or keep the new one?
	replace { | obj, args, keepChannelsMappingIn = true, keepChannelsMappingOut = true, outsMapping |
		algaScheduler.addAction({ this.instantiated }, {
			this.replaceInner(obj, args, keepChannelsMappingIn, keepChannelsMappingOut, outsMapping);
		});
	}

	//Remove individual mix entries at param (called from replaceMix too)
	disconnectInner { | param = \in, previousSender, replaceMix = false |
		if(this.mixerParamContainsSender(param, previousSender).not, {
			(previousSender.asString ++ " was not present in the mix for param " ++ param.asString).error;
			^this;
		});

		if(replaceMix == false, {
			var interpSynthsAtParam;

			//Remove inNodes / outNodes / connectionTimeOutNodes
			this.removeInOutNodesDict(previousSender, param);

			this.freeInterpSynthAtParam(previousSender, param, true);

			interpSynthsAtParam = interpSynths[param];

			//If length is now 2, it means it's just one mixer AND the \default node left in the dicts.
			//Assign the node to \default and remove the previous mixer
			if(interpSynthsAtParam.size == 2, {
				interpSynthsAtParam.keysValuesDo({ | interpSender, interpSynthAtParam |
					if(interpSender != \default, {
						var normSynthsAtParam = normSynths[param];
						var interpBussesAtParam = interpBusses[param];

						interpSynthsAtParam[\default] = interpSynthAtParam;
						interpBussesAtParam[\default] = interpBussesAtParam[interpSender];
						normSynthsAtParam[\default]   = normSynthsAtParam[interpSender];

						interpSynthsAtParam.removeAt(interpSender);
						interpBussesAtParam.removeAt(interpSender);
						normSynthsAtParam.removeAt(interpSender);
					});
				});
			});
		}, {
			//Just free if replaceMix == true
			this.freeInterpSynthAtParam(previousSender, param, true);
		});
	}

	//Remove individual mix entries at param
	disconnect { | param = \in, previousSender |
		if(previousSender.isAlgaNode.not, {
			(previousSender.asString) ++ " is not an AlgaNode".error;
			^this;
		});

		algaScheduler.addAction({ (this.instantiated).and(previousSender.instantiated) }, {
			this.disconnectInner(param, previousSender);
		});
	}

	//Find out if specific param / sender combination is in the mix
	mixerParamContainsSender { | param = \in, sender |
		^(interpSynths[param][sender] != nil)
	}

	//When clear, run disconnections to nodes connected to this
	removeConnectionFromReceivers {
		outNodes.keysValuesDo({ | receiver, paramsSet |
			paramsSet.do({ | param |
				//If mixer param, just disconnect the entry connected to this
				if(receiver.mixerParamContainsSender(param, this), {
					receiver.disconnectInner(param, this);
				}, {
					//no mixer param, just run the disconnect to restore defaults
					receiver.resetParamInner(param, nil);
				});
			});
		});
	}

	clearInner {
		//If synth had connections, run <| (or disconnect, if mixer) on the receivers
		this.removeConnectionFromReceivers;

		//This could be overwritten if .replace is called
		toBeCleared = true;

        //Stop playing (if it was playing at all)
        this.stopInner;

		fork {
			//Wait time before clearing groups and busses...
			(longestWaitTime + 1.0).wait;

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

	clear {
		algaScheduler.addAction({ this.instantiated }, {
			this.clearInner;
		});
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

	//Number plays those number of channels sequentially
	//Array selects specific output
    createPlaySynth { | channelsToPlay |
        if((isPlaying.not).or(beingStopped), {
            var actualNumChannels, playSynthSymbol;

			if(rate == \control, { "Cannot play a kr AlgaNode".error; ^nil; });

			if(channelsToPlay != nil, {
				if(channelsToPlay.isSequenceableCollection, {
					var channelsToPlaySize = channelsToPlay.size;
					if(channelsToPlaySize < numChannels, {
						actualNumChannels = channelsToPlaySize;
					}, {
						actualNumChannels = numChannels;
					});
				}, {
					if(channelsToPlay < numChannels, {
						if(channelsToPlay < 1, { channelsToPlay = 1 });
						if(channelsToPlay > AlgaStartup.algaMaxIO, { channelsToPlay = AlgaStartup.algaMaxIO });
						actualNumChannels = channelsToPlay;
					}, {
						actualNumChannels = numChannels;
					});
				})
			}, {
				actualNumChannels = numChannels
			});

			playSynthSymbol = ("alga_play_" ++ numChannels ++ "_" ++ actualNumChannels).asSymbol;

			if(channelsToPlay.isSequenceableCollection, {
				//Wrap around the indices entries (or delete out of bounds???)
				channelsToPlay = channelsToPlay % numChannels;

				playSynth = Synth(
					playSynthSymbol,
					[\in, synthBus.busArg, \indices, channelsToPlay, \gate, 1, \fadeTime, playTime],
					playGroup
				);
			}, {
				playSynth = Synth(
					playSynthSymbol,
					[\in, synthBus.busArg, \gate, 1, \fadeTime, playTime],
					playGroup
				);
			});

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

	playInner { | channelsToPlay |
		this.createPlaySynth(channelsToPlay);
	}

	//Add option for fade time here!
	play { | channelsToPlay |
		algaScheduler.addAction({ this.instantiated }, {
			this.playInner(channelsToPlay);
		});
	}

	stopInner {
		this.freePlaySynth;
	}

	//Add option for fade time here!
    stop {
		algaScheduler.addAction({ this.instantiated }, {
            this.stop;
		});
    }

	isAlgaNode { ^true }

	debug {
		"connectionTime:".postln;
		("\t" ++ connectionTime.asString).postln;
		"paramsConnectionTime".postln;
		("\t" ++ paramsConnectionTime.asString).postln;
		"longestWaitTime:".postln;
		("\t" ++ longestWaitTime.asString).postln;
		"controlNames".postln;
		("\t" ++ controlNames.asString).postln;
		"inNodes:".postln;
		("\t" ++ inNodes.asString).postln;
		"outNodes:".postln;
		("\t" ++ outNodes.asString).postln;
		"outs:".postln;
		("\t" ++ outs.asString).postln;
		"paramChansMapping:".postln;
		("\t" ++ paramChansMapping.asString).postln;
		"interpSynths:".postln;
		("\t" ++ interpSynths.asString).postln;
		"interpBusses:".postln;
		("\t" ++ interpBusses.asString).postln;
		"normSynths:".postln;
		("\t" ++ normSynths.asString).postln;
		"normBusses:".postln;
		("\t" ++ normBusses.asString).postln;
		"currentDefaults:".postln;
		("\t" ++ currentDefaults.asString).postln;
	}
}

//Alias
AN : AlgaNode {}
// AlgaLib: SuperCollider implementation of Alga, an interpolating live coding environment.
// Copyright (C) 2020-2022 Francesco Cameli.

// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.

// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.

// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

AlgaNode {
	//Server where this node lives
	var <server;

	//The AlgaActionScheduler
	var <actionScheduler;

	//The AlgaParser
	var <parser;

	//The sched used internally if set
	var schedInner = 0;

	//Sched action in seconds instead of beats
	var <schedInSeconds = false;

	//Index of the corresponding AlgaBlock in the AlgaBlocksDict.
	//This is being set in AlgaBlock
	var <>blockIndex = -1;

	//This is the time when making a new connection to this node
	var <connectionTime = 0;

	//This controls the fade in and out when .play / .stop
	var <playTime = 0;

	//On replace, use time if possible. Otherwise, playTime
	var <replacePlayTime = true;

	//Function to use in .play to clip
	var <playSafety = \none;

	//Scale time with clock's tempo
	var <tempoScaling = false;

	//On replace, use this scaling if possible.
	var <prevPlayScale = 1.0;

	//On replace, use this out if possible
	var <prevPlayOut = 0;

	//This is the longestConnectionTime between all the outNodes.
	//It's used when .replacing a node connected to something, in order for it to be kept alive
	//for all the connected nodes to run their interpolator on it
	//longestConnectionTime will be moved to AlgaBlock and applied per-block!
	var <longestConnectionTime = 0;

	//Keeps track of all the connectionTime of all nodes with this node as input
	var <connectionTimeOutNodes;

	//per-parameter connectionTime
	var <paramsConnectionTime;

	//The Env shape
	var <interpShape;

	//per-parameter interpShape
	var <paramsInterpShapes;

	//The max between longestConnectionTime and playTime
	var <longestWaitTime = 0;

	//Explicit args provided by the user
	//This will be added: args passed in at creation to overwrite SynthDef's one,
	//When using <|, then, these are the ones that will be restored!
	var <defArgs;

	//Whenever args have just been set, record their state in order for .replace to correctly replace
	//the right values ... Check TestBuffers.scd for it in action
	var <explicitArgs;

	//When setting a number and running .replace, these values will be considered, unless
	//the user EXPLICITLY set defArgs ... Check TestBuffers.scd for it in action
	var <replaceArgs;

	//SynthDef, either explicit or internal (Function generated)
	var <synthDef;

	//Spec of parameters (names, default values, channels, rate)
	var <controlNames;

	//Number of channels and rate
	var <numChannels, <rate;

	//Output channel mapping
	var <outsMapping;

	//All Groups / Synths / Busses
	var <group, <playGroup, <synthGroup, <normGroup, <interpGroup, <tempGroup;
	var <playSynth, <synth, <normSynths, <interpSynths;
	var <synthBus, <normBusses, <interpBusses;

	//Currently active interpSynths per param.
	//These are used when changing time on connections, and need to update already running
	//interpSynths at specific param / sender combination. It's the whole core that allows
	//to have dynamic fadeTimes
	var <activeInterpSynths;

	//Connected nodes
	var <inNodes, <outNodes;

	//Connected ACTIVE nodes (interpolation going on)
	var <activeInNodes, <activeOutNodes;
	var <activeInNodesCounter, <activeOutNodesCounter;

	//Keep track of current \default nodes (this is used for mix parameters)
	var <currentDefaultNodes;

	//Keep track of current scaling for params
	var <paramsScaling;

	//Keep track of current chans mapping for params
	var <paramsChansMapping;

	//Keep track of the "chans" arg for play so it's kept across .replaces
	var <playChans;

	//This is used to trigger the creation of AlgaBlocks, if needed.
	//It needs a setter cause it's also used in AlgaPatternInterpStreams
	var <>connectionAlreadyInPlace = false;

	//Needed to receive out: from an AlgaPattern.
	var <patternOutNodes;
	var <patternOutEnvSynths;
	var <patternOutEnvBusses;
	var <patternOutEnvBussesToBeFreed;
	var <lockInterpBusses;
	var <patternOutUniqueIDs;

	//General state queries
	var <isPlaying = false;
	var <beingStopped = false;
	var <algaToBeCleared = false;
	var <algaWasBeingCleared = false;
	var <algaCleared = false;

	//Only trigger replace when latestReplaceDef is same as def
	var latestReplaceDef;

	//Only trigger from when sender equals the latest sender used
	var latestSenders;

	//Debugging tool
	var <name;

	*new { | def, args, interpTime, interpShape, playTime, playSafety, sched,
		schedInSeconds = false, tempoScaling = false, outsMapping, server |
		^super.new.init(
			argDef: def,
			argArgs: args,
			argConnectionTime: interpTime,
			argInterpShape: interpShape,
			argPlayTime: playTime,
			argPlaySafety: playSafety,
			argSched: sched,
			argSchedInSeconds: schedInSeconds,
			argTempoScaling: tempoScaling,
			argOutsMapping: outsMapping,
			argServer: server,
		)
	}

	*newAP { | def, interpTime, interpShape, playTime, playSafety, sched = 1,
		schedInSeconds = false, tempoScaling = false,
		sampleAccurateFuncs = true,  player, server |
		^super.new.init(
			argDef: def,
			argConnectionTime: interpTime,
			argInterpShape: interpShape,
			argPlayTime: playTime,
			argPlaySafety: playSafety,
			argSched: sched,
			argSchedInSeconds: schedInSeconds,
			argTempoScaling: tempoScaling,
			argSampleAccurateFuncs: sampleAccurateFuncs,
			argPlayer: player,
			argServer: server,
		)
	}

	*debug { | def, args, interpTime, interpShape, playTime, playSafety, sched,
		schedInSeconds = false, tempoScaling = false, outsMapping, server, name |
		^super.new.init(
			argDef: def,
			argArgs: args,
			argConnectionTime: interpTime,
			argInterpShape: interpShape,
			argPlayTime: playTime,
			argPlaySafety: playSafety,
			argSched: sched,
			argSchedInSeconds: schedInSeconds,
			argTempoScaling: tempoScaling,
			argOutsMapping: outsMapping,
			argServer: server,
			argName: name
		)
	}

	initAllVariables { | argServer |
		var scheduler;

		//Default server if not specified otherwise
		server = argServer ? Server.default;

		//AlgaScheduler from specific server
		scheduler = Alga.getScheduler(server);
		if(scheduler == nil, {
			(
				"AlgaNode: can't retrieve a valid AlgaScheduler for server '" ++
				server.name ++
				"'. Has Alga.boot been called on it?"
			).error;
			^false;
		});

		//AlgaActionScheduler
		actionScheduler = AlgaActionScheduler(this, scheduler);

		//AlgaParser
		parser = AlgaParser(this);

		//param -> ControlName
		controlNames = IdentityDictionary(10);

		//param -> value
		defArgs = IdentityDictionary(10);

		//param -> value
		replaceArgs = IdentityDictionary(10);

		//param -> value
		explicitArgs = IdentityDictionary(10);

		//param -> connectionTime
		paramsConnectionTime = IdentityDictionary(10);

		//param -> interpShape
		paramsInterpShapes = IdentityDictionary(10);

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

		//IdentityDictionary of IdentityDictonaries: (needed for mixing)
		//\param -> IdentityDictionary(sender -> IdentitySet(interpSynth))
		//these are used when changing time on connections, and need to update already running
		//interpSynths at specific param / sender combination. It's the whole core that allows
		//to have dynamic fadeTimes
		activeInterpSynths = IdentityDictionary(10);

		//Per-argument connections to this AlgaNode. These are in the form:
		//(param -> OrderedIdentitySet[AlgaNode, AlgaNode...]). Multiple AlgaNodes are used when
		//using the mixing <<+ / >>+
		inNodes = IdentityDictionary(10);

		//outNodes are not indexed by param name, as they could be attached to multiple nodes with same param name.
		//they are indexed by identity of the connected node, and then it contains a OrderedIdentitySet of all parameters
		//that it controls in that node (AlgaNode -> OrderedIdentitySet[\freq, \amp ...])
		outNodes = IdentityDictionary(10);

		//Like inNodes
		activeInNodes = IdentityDictionary(10);

		//Like outNodes
		activeOutNodes = IdentityDictionary(10);

		//Used to counter same Nodes
		activeInNodesCounter  = IdentityDictionary(10);
		activeOutNodesCounter = IdentityDictionary(10);

		//Chans mapping from inNodes... How to support <<+ / >>+ ???
		//IdentityDictionary of IdentityDictionaries:
		//\param -> IdentityDictionary(sender -> paramChanMapping)
		paramsChansMapping = IdentityDictionary(10);

		//Keep track of the scale arguments for senders (for replace calls)
		//\param -> IdentityDictionary(sender -> scale)
		paramsScaling = IdentityDictionary(10);

		//This keeps track of current \default nodes for every param.
		//These are then used to restore default connections on <| or << after the param being a mix one (<<+)
		currentDefaultNodes = IdentityDictionary(10);

		//Keeps all the connectionTimes of the connected nodes
		connectionTimeOutNodes = IdentityDictionary(10);

		//AlgaPattern specific
		if(this.isAlgaPattern, {
			this.latestPatternInterpSumBusses = IdentityDictionary(10);
			this.currentActivePatternInterpSumBusses = IdentityDictionary(10);
			this.currentPatternBussesAndSynths = IdentityDictionary(10);
			this.currentActivePatternParamSynths = IdentityDictionary(10);
			this.currentActiveInterpBusses = IdentityDictionary(10);
		});

		^true;
	}

	//Add an action to scheduler. This takes into account sched == AlgaStep
	addAction { | condition, func, sched = 0, topPriority = false, preCheck = false |
		actionScheduler.addAction(
			condition: condition,
			func: func,
			sched: sched,
			topPriority: topPriority,
			preCheck: preCheck
		)
	}

	//If needed, it will compile the AlgaSynthDefs in functionSynthDefDict and wait before executing func.
	//Otherwise, it will just execute func
	compileFunctionSynthDefDictIfNeeded { | func, functionSynthDefDict |
		^actionScheduler.compileFunctionSynthDefDictIfNeeded(func, functionSynthDefDict);
	}

	//Wait for all args to be instantiated before going forward
	executeOnArgsInstantiation { | args, dispatchFunc |
		if(args.isArray, {
			var functionSynthDefDict;
			var algaArgs;
			args.do({ | entry, i |
				case
				{ entry.isAlgaTemp } {
					if(i == 0, {
						("AlgaNode: 'args' first element can't be an AlgaTemp").error;
						^this
					});

					functionSynthDefDict = functionSynthDefDict ? IdentityDictionary();
					entry = this.parseAlgaTempParam(entry, functionSynthDefDict);
					args[i] = entry;
				}
				{ (entry.isAlgaArg).or(entry.isAlgaNode) } {
					var sender = entry;

					if(i == 0, {
						("AlgaNode: 'args' first element can't be an AlgaArg").error;
						^this
					});

					algaArgs = algaArgs ? IdentitySet();

					if(entry.isAlgaArg, { sender = entry.sender });
					if(sender.isAlgaNode, {
						var param = args[i-1];
						algaArgs.add(sender); //This is used later to check instantiation
						//If previous entry was symbol, use that to map inNodes
						if(param.isSymbol, {
							this.addInOutNodesDict(entry, param); //Add entry to inNodes. entry MUST be the AlgaArg!
							AlgaBlocksDict.createNewBlockIfNeeded(this, sender);
						});
					});
				};
			});

			//Make sure to compile the AlgaTemps' Functions
			this.compileFunctionSynthDefDictIfNeeded(
				func: {
					//Make sure that all AlgaArgs' nodes are instantiated
					if(algaArgs != nil, {
						this.addAction(
							condition: {
								var instantiated = true;
								algaArgs.do({ | algaArg |
									if(algaArg.algaInstantiatedAsSender.not, {
										instantiated = false
									});
								});
								instantiated
							},
							func: dispatchFunc,
							preCheck: true //execute right away if possible
						);
					}, {
						dispatchFunc.value
					});
				},
				functionSynthDefDict: functionSynthDefDict
			);

			^this;
		});

		//If no args, execute the function right away
		dispatchFunc.value;
	}

	init { | argDef, argArgs, argConnectionTime = 0, argInterpShape,
		argPlayTime = 0, argPlaySafety, argSched = 0, argOutsMapping,
		argSampleAccurateFuncs = true, argSchedInSeconds = false,
		argTempoScaling = false, argPlayer, argServer, argName |

		//Check supported classes for argObj, so that things won't even init if wrong.
		//Also check for AlgaPattern
		if(this.isAlgaPattern, {
			//AlgaPattern init
			if((argDef.class != Event).and(argDef.class != Symbol).and(argDef.class != Function), {
				"AlgaPattern: first argument must be an Event describing the pattern, a Symbol or a Function".error;
				^this;
			});
		}, {
			//AlgaNode init
			if((argDef.class != Symbol).and(
				argDef.class != Function), {
				"AlgaNode: first argument must be either a Symbol or a Function".error;
				^this;
			});
		});

		//initialize all IdentityDictionaries. Check if init went through correctly,
		//otherwise, don't go through with anything
		if(this.initAllVariables(argServer).not, { ^this });

		//Init debug name
		name = argName;

		//Set times
		this.connectionTime_(argConnectionTime, all:true);
		this.playTime_(argPlayTime);

		//Set playSafety
		if(argPlaySafety != nil, { this.playSafety_(argPlaySafety) });

		//Set env shape
		interpShape = Env([0, 1], 1); //default
		this.interpShape_(argInterpShape, all:true);

		//Set schedInSeconds
		this.schedInSeconds_(argSchedInSeconds);

		//Set tempoScaling
		this.tempoScaling_(argTempoScaling);

		//If AlgaPattern, parse the def and then dispatch accordingly (waiting for server if needed)
		if(this.isAlgaPattern, {
			var argDefAndFunctionSynthDefDict, functionSynthDefDict;

			//Init sampleAccurateFuncs: must happen BEFORE parseDef
			this.sampleAccurateFuncs_(argSampleAccurateFuncs);

			//Assign player
			if(argPlayer.isAlgaPatternPlayer, {
				argPlayer.addAlgaPattern(this);
				this.player = argPlayer;
			}, {
				if(argPlayer != nil, {
					"AlgaPattern: 'player' must be an AlgaPatternPlayer".warn;
				});
			});

			argDefAndFunctionSynthDefDict = this.parseDef(argDef);
			argDef = argDefAndFunctionSynthDefDict[0];
			functionSynthDefDict = argDefAndFunctionSynthDefDict[1];

			//Go through
			this.compileFunctionSynthDefDictIfNeeded(
				func: {
					this.dispatchNode(
						argDef, argArgs,
						initGroups: true,
						outsMapping: argOutsMapping,
						sched: argSched
					);
				},
				functionSynthDefDict: functionSynthDefDict
			);

			^this;
		});

		//Parse args looking for AlgaTemps / AlgaArgs / AlgaNodes
		this.executeOnArgsInstantiation(
			args: argArgs,
			dispatchFunc: {
				this.dispatchNode(
					argDef, argArgs,
					initGroups: true,
					outsMapping: argOutsMapping,
					sched: argSched
				)
			}
		);

		^this;
	}

	sched { ^schedInner }

	sched_ { | value |
		if((value.isNumber.or(value.isAlgaStep)).not, {
			"AlgaNode: 'sched' can only be a Number or AlgaStep".error;
			^this;
		});
		schedInner = value
	}

	schedInSeconds_ { | value |
		if(value.isKindOf(Boolean).not, {
			"AlgaNode: 'schedInSeconds' only supports boolean values. Setting it to false".error;
			value = false;
		});
		schedInSeconds = value
	}

	tempoScaling_ { | value |
		if(value.isKindOf(Boolean).not, {
			"AlgaNode: 'tempoScaling' only supports boolean values. Setting it to false".error;
			value = false;
		});
		tempoScaling = value
	}

	setParamsConnectionTime { | value, param, all = false |
		//If all, set all paramConnectionTime regardless of their previous value
		if(all, {
			paramsConnectionTime.keysValuesChange({ value });
		}, {
			//If not all, only set new param value if param != nil
			if(param != nil, {
				var paramConnectionTime = paramsConnectionTime[param];
				if(paramConnectionTime != nil, {
					paramsConnectionTime[param] = value;
				}, {
					("AlgaNode: invalid param to set connection time for: '" ++ param ++ "'").error;
				});
			}, {
				//This will just change the
				//paramConnectionTime for paramConnectionTimes that haven't been explicitly modified
				paramsConnectionTime.keysValuesChange({ | param, paramConnectionTime |
					if(paramConnectionTime == connectionTime, { value }, { paramConnectionTime });
				});
			});
		});
	}

	//Get a param time
	getParamConnectionTime { | param = \in |
		var paramConnectionTime = paramsConnectionTime[param] ? connectionTime;
		if(paramConnectionTime < 0, { paramConnectionTime = connectionTime });
		^paramConnectionTime;
	}

	//Set interp shape
	interpShape_ { | value, param, all = false |
		value = value.algaCheckValidEnv(server: server);
		if(value != nil, {
			//Set the global one if param is nil
			if(param == nil, { interpShape = value });

			//If all, set all paramConnectionTime regardless of their previous value
			if(all, {
				paramsInterpShapes.keysValuesChange({ value });
			}, {
				if(param != nil, {
					var paramInterpShape = paramsInterpShapes[param];
					if(paramInterpShape != nil, {
						paramsInterpShapes[param] = value;
					}, {
						("AlgaNode: invalid param to set interpShape for: '" ++ param ++ "'").error;
					});
				}, {
					paramsInterpShapes.keysValuesChange({ | param, paramInterpShape |
						if(paramInterpShape == interpShape, { value }, { paramInterpShape });
					});
				});
			});
		});
	}

	is { ^interpShape }

	is_ { | value, param, all = false | this.interpShape_(value, param, all) }

	paramInterpShape_ { | param, value |
		this.interpShape_(value, param);
	}

	paramInterpShape { | param | ^paramsInterpShapes[param] }

	pis { | param | ^paramsInterpShapes[param] }

	//get interp shape at param
	getInterpShape { | param |
		var interpShapeAtParam = paramsInterpShapes[param];
		if(interpShapeAtParam != nil, { ^interpShapeAtParam });
		^(interpShape ? Env([0, 1], 1));
	}

	//connectionTime / ct / interpolationTime / it
	//If all, set all paramConnectionTime regardless of their previous value
	connectionTime_ { | value, param, all = false |
		value = value ? 0;
		if(value < 0, { value = 0 });
		//this must happen before setting connectionTime, as it's been used to set
		//paramConnectionTimes, checking against the previous connectionTime (before updating it)
		this.setParamsConnectionTime(value, param, all);

		//Only set global connectionTime if param is nil
		if(param == nil, {
			connectionTime = value;
		});

		this.calculateLongestConnectionTime(value);
	}

	ct_ { | value |
		this.connectionTime_(value)
	}

	ct { ^connectionTime }

	interpolationTime_ { | value |
		this.connectionTime_(value)
	}

	interpolationTime { ^connectionTime }

	interpTime_ { | value |
		this.connectionTime_(value)
	}

	interpTime { ^connectionTime }

	it_ { | value |
		this.connectionTime_(value)
	}

	it { ^connectionTime }

	paramConnectionTime_ { | param, value |
		this.connectionTime_(value, param);
	}

	paramConnectionTime { | param | ^paramsConnectionTime[param] }

	pct_ { | param, value |
		this.connectionTime_(value, param);
	}

	pct { | param | ^paramsConnectionTime[param] }

	paramInterpolationTime_ { | param, value |
		this.connectionTime_(value, param);
	}

	paramInterpolationTime { | param | ^paramsConnectionTime[param] }

	paramInterpTime_ { | param, value |
		this.connectionTime_(value, param);
	}

	paramInterpTime { | param | ^paramsConnectionTime[param] }

	pit_ { | param, value |
		this.connectionTime_(value, param);
	}

	pit { | param | ^paramsConnectionTime[param] }

	//playTime
	playTime_ { | value |
		value = value ? 0;
		if(value < 0, { value = 0 });
		playTime = value;
		this.calculateLongestWaitTime;
	}

	pt { ^playTime }

	pt_ { | value | this.playTime_(value) }

	replacePlayTime_ { | value |
		if(value.isKindOf(Boolean).not, {
			"AlgaNode: 'replacePlayTime' only supports boolean values. Setting it to false".error;
			value = false;
		});
		replacePlayTime = value
	}

	rpt { ^replacePlayTime }

	rpt_ { | value | this.replacePlayTime_(value) }

	playSafety_ { | value |
		var valueSymbol = value.asSymbol;
		if((valueSymbol == \none).or(valueSymbol == \clip).or(valueSymbol == \tanh).or(valueSymbol == \softclip).or(valueSymbol == \limiter), {
			playSafety = valueSymbol
		}, {
			"AlgaNode: 'playSafety' must be either 'none', 'clip', 'tanh', 'softclip' or 'limiter'.".error
		})
	}

	ps { ^playSafety }

	ps_ { | value | this.playSafety_(value) }

	//Used in AlgaProxySpace
	copyVars { | nodeToCopy |
		if(nodeToCopy.isAlgaNode, {
			this.sched = nodeToCopy.sched;
			this.interpTime = nodeToCopy.connectionTime;
			this.playTime = nodeToCopy.playTime;
			this.replacePlayTime = nodeToCopy.replacePlayTime;
			playSafety = nodeToCopy.playSafety;
			paramsConnectionTime = nodeToCopy.paramsConnectionTime;
			paramsInterpShapes = nodeToCopy.paramsInterpShapes;
		});
	}

	//maximum between longestConnectionTime and playTime...
	//is this necessary? Yes it is, cause if running .clear on the receiver,
	//I need to be sure that if .replace or .clear is set to a sender, they will be longer
	//than the .playTime of the receiver being cleared.
	calculateLongestWaitTime { | time |
		longestWaitTime = max(longestConnectionTime, playTime);
	}

	//2 args: new time and original time.
	//return time, but also set longestWaitTime accordingly
	calculateTemporaryLongestWaitTime { | time, otherTime |
		//if nil time, use otherTime (the original one)
		if((time == nil).or(time.isNumber.not), { ^otherTime });

		//this is to set a temporary longestWaitTime
		if(time > longestWaitTime, {
			var prevLongestWaitTime = longestWaitTime;
			longestWaitTime = time;

			//after time, calculate values again
			fork {
				time.wait;

				//might have changed meanwhile (due to another function)
				if(longestWaitTime != time, {
					//use the longest available
					//(remember: longestWaitTime here has been changed from time)
					longestWaitTime = max(longestWaitTime, prevLongestWaitTime);
				}, {
					//Restore previous one: nothing changed
					longestWaitTime = prevLongestWaitTime;
				})
			}
		});

		//return time
		^time;
	}

	//calculate longestConnectionTime
	calculateLongestConnectionTime { | argConnectionTime, topNode = true |
		longestConnectionTime = max(connectionTime, argConnectionTime);

		//Doing the check instead of .max cause it's faster, imagine there are a lot of entries.
		connectionTimeOutNodes.do({ | value |
			if(value > longestConnectionTime, { longestConnectionTime = value });
		});

		//Calculate longestWaitTime
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

	createAllGroups {
		//Retrieve the global ParGroup
		var parGroup = Alga.parGroup(server);

		//This one must be Group for the simple fact that the ordering of the next groups
		//needs to be maintained as it's declared here.
		group = AlgaGroup(parGroup);

		//Keep playGroup as Group: no need to multithread here
		playGroup = AlgaGroup(group);

		//For AlgaPattern, use a ParGroup to parallelize
		if(this.isAlgaPattern, {
			this.fxConvGroup = AlgaParGroup(group);
			this.fxGroup = AlgaParGroup(group);
			this.synthConvGroup = AlgaParGroup(group);
			synthGroup = AlgaParGroup(group);
			normGroup = AlgaParGroup(group);
			interpGroup = AlgaParGroup(group);
			tempGroup = AlgaParGroup(group);
		}, {
			//With mixing and everything, it's nice to parallelize interpGroup, normGroup and tempGroup.
			//Of course, with fewer parameters and / or mix inputs, the gains will not be huge.
			//synthGroup is better as standard Group, as it's mostly one, unless .repalce is happening,
			//in which case, it's still fine to keep a low count as it's not happening all the time.

			synthGroup = AlgaGroup(group);
			//synthGroup = AlgaParGroup(group);

			//normGroup = AlgaGroup(group);
			normGroup = AlgaParGroup(group);

			//interpGroup = AlgaGroup(group);
			interpGroup = AlgaParGroup(group);

			//tempGroup = AlgaGroup(group);
			tempGroup = AlgaParGroup(group);
		});
	}

	resetGroups {
		if(algaToBeCleared, {
			playGroup = nil;
			group = nil;
			synthGroup = nil;
			normGroup = nil;
			tempGroup = nil;
			interpGroup = nil;
			if(this.isAlgaPattern, {
				this.fxGroup = nil;
				this.fxConvGroup = nil;
				this.synthConvGroup = nil;
			});
		});
	}

	//Groups (and state) will be reset only if they are nil AND they are set to be freed.
	freeAllGroups { | now = false, time |
		if(group != nil, {
			if(now, {
				group.free;
			}, {
				var groupOld = group.copy;

				if(time == nil, { time = longestWaitTime });

				fork {
					(time + 1.0).wait;
					groupOld.free;
				};
			});
		});
	}

	createSynthBus {
		if(numChannels == nil, {
			"AlgaNode: 'numChannels' is nil. 1 will be used instead".warn;
			numChannels = 1;
		});
		if(rate == nil, {
			"AlgaNode: 'rate' is nil. 'audio' will be used instead".warn;
			rate = \audio;
		});
		synthBus = AlgaBus(server, numChannels, rate);
	}

	createInterpNormBusses {
		controlNames.do({ | controlName |
			var paramName = controlName.name;
			var paramRate = controlName.rate;
			var paramNumChannels = controlName.numChannels;

			if((paramRate == \control).or(paramRate == \audio), {
				//This is crucial: interpBusses have 1 more channel for the interp envelope!
				interpBusses[paramName][\default] = AlgaBus(server, paramNumChannels + 1, paramRate);
				normBusses[paramName] = AlgaBus(server, paramNumChannels, paramRate);
			});
		});
	}

	createAllBusses {
		this.createInterpNormBusses;
		this.createSynthBus;
	}

	freeSynthBus { | now = false, time, isClear = false|
		if(now, {
			if(synthBus != nil, {
				synthBus.free;
				synthBus = nil; //Necessary for correct .play behaviour!
			});
		}, {
			//if forking, this.synthBus could change, that's why this is needed
			var prevSynthBus = synthBus.copy;

			if(isClear.not, {
				synthBus = nil;  //Necessary for correct .play behaviour!
			});

			if(time == nil, { time = longestWaitTime });

			fork {
				//Cheap solution when having to replacing a synth that had other interp stuff
				//going on. Simply wait longer than longestConnectionTime (which will be the time the replaced
				//node will take to interpolate to the previous receivers) and then free all the previous stuff
				(time + 1.0).wait;
				if(prevSynthBus != nil, { prevSynthBus.free });
				if(isClear, {
					if(prevSynthBus == synthBus, { synthBus == nil });
				});
			}
		});
	}

	freeInterpNormBusses { | now = false, time |
		//These are handled by AlgaPattern
		if(this.isAlgaPattern, { ^nil });

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

			if(time == nil, { time = longestWaitTime });

			fork {
				//Cheap solution when having to replacing a synth that had other interp stuff
				//going on. Simply wait longer than longestConnectionTime (which will be the time the replaced
				//node will take to interpolate to the previous receivers) and then free all the previous stuff
				(time + 1.0).wait;

				if(prevNormBusses != nil, {
					prevNormBusses.do({ | normBus |
						if(normBus != nil, { normBus.free });
					});
				});

				if(prevInterpBusses != nil, {
					prevInterpBusses.do({ | interpBussesAtParam |
						interpBussesAtParam.do({ | interpBus |
							if(interpBus != nil, { interpBus.free });
						});
					});
				});
			}
		});
	}

	freeAllBusses { | now = false, time, isClear = false |
		this.freeSynthBus(now, time, isClear);
		this.freeInterpNormBusses(now, time);
	}

	//Parse reset. Basically, deal with removal from defArgs / replaceArgs / inNodes / explicitArgs
	parseResetOnReplace { | reset |
		case
		{ reset.isArray } {
			reset.do({ | entry |
				if(entry.isSymbol, {
					defArgs.removeAt(entry);
					replaceArgs.removeAt(entry);
					inNodes.removeAt(entry);
					explicitArgs[entry] = false;
				});
			});
		}
		{ reset == true } {
			controlNames.do({ | controlName |
				var paramName = controlName.name;
				replaceArgs.removeAt(paramName);
				inNodes.removeAt(paramName);
				defArgs.removeAt(paramName);
				explicitArgs[paramName] = false;
			});
		};
	}

	//This will also be kept across replaces, as it's just updating the dict
	createDefArgs { | args |
		if(args != nil, {
			if(args.isSequenceableCollection.not, { "AlgaNode: args must be an array".error; ^this });
			if((args.size) % 2 != 0, { "AlgaNode: args' size must be a power of two".error; ^this });
			args.do({ | param, i |
				if(param.isSymbol, {
					var iPlusOne = i + 1;
					if(iPlusOne < args.size, {
						var value = args[i + 1];
						if(value.isBuffer, { value = value.bufnum });
						if(this.isAlgaPattern, {
							//AlgaPattern
							if((value.isNumberOrArray).or(value.isAlgaNode).or(
								value.isPattern).or(value.isAlgaArg).or(value.isAlgaTemp), {
								defArgs[param] = value;
								explicitArgs[param] = true;
							}, {
								("AlgaPattern: args at param '" ++ param ++ "' must be an AlgaNode, AlgaPattern, AlgaArg, AlgaTemp, Number, Array, Pattern, or Buffer").error
							});
						}, {
							//AlgaNode
							if((value.isNumberOrArray).or(value.isAlgaNode).or(value.isAlgaTemp).or(value.isAlgaArg), {
								defArgs[param] = value;
								explicitArgs[param] = true;
							}, {
								("AlgaNode: args at param '" ++ param ++ "' must be an AlgaNode, AlgaPattern, AlgaTemp, AlgaArg, Number, Array or Buffer").error;
							});
						});
					});
				});
			});
		});
	}

	//dispatches controlnames / numChannels / rate according to def class
	dispatchNode { | def, args, initGroups = false, replace = false, reset = false,
		keepChannelsMapping = false, outsMapping, keepScale = false, sched = 0 |

		//If there is a synth playing, set its algaInstantiated status to false:
		//this is mostly needed for .replace to work properly and wait for the new synth
		//to be algaInstantiated!
		if(synth != nil, { synth.algaInstantiated = false });

		//Parse reset
		this.parseResetOnReplace(reset);

		//Create args dict
		this.createDefArgs(args);

		//Symbol
		if(def.isSymbol, {
			if(outsMapping != nil, {
				"AlgaNode: outsMapping will not be considered when def is a SynthDef.".warn;
			});
			this.dispatchSynthDef(def, initGroups, replace,
				keepChannelsMapping:keepChannelsMapping,
				keepScale:keepScale,
				sched:sched
			);
		}, {
			//Function
			if(def.isFunction, {
				this.dispatchFunction(def, initGroups, replace,
					keepChannelsMapping:keepChannelsMapping,
					outsMapping:outsMapping,
					keepScale:keepScale,
					sched:sched
				);
			}, {
				("AlgaNode: '" ++ def.asString ++ "' is invalid").error;
			});
		});
	}

	//Remove \fadeTime \out and \gate and generate controlNames dict entries
	createControlNamesAndParamsConnectionTime { | synthDescControlNames |
		//Reset entries first
		controlNames.clear;

		synthDescControlNames.do({ | controlName |
			var paramName = controlName.name;
			if(this.checkValidControlName(paramName), {
				var paramNumChannels = controlName.numChannels;
				if(paramNumChannels > AlgaStartup.algaMaxIO, {
					("AlgaNode: trying to instantiate the AlgaSynthDef '" ++ synthDef.name ++ "' whose parameter '" ++ paramName ++ "' has more channels(" ++ paramNumChannels ++ ") than 'Alga.maxIO'(" ++ AlgaStartup.algaMaxIO ++ "). Change 'Alga.maxIO' to fit your needs and run 'Alga.boot' again.").error;
					this.clear;
					^false
				});

				//Create controlNames
				controlNames[paramName] = controlName;

				//Create IdentityDictionaries for everything needed
				interpSynths[paramName] = IdentityDictionary();
				normSynths[paramName] = IdentityDictionary();
				interpBusses[paramName] = IdentityDictionary();
				activeInterpSynths[paramName] = IdentityDictionary();

				//These need to be kept across .replace calls!
				//Only replace if entry is clear
				if(paramsConnectionTime[paramName] == nil, {
					paramsConnectionTime[paramName] = connectionTime;
				});

				//These need to be kept across .replace calls!
				//Only replace if entry is clear
				if(paramsChansMapping[paramName] == nil, {
					paramsChansMapping[paramName] = IdentityDictionary();
				});
			});
		});

		^true;
	}

	//Unpack outsMappings for a single SynthDef
	unpackSynthDefSymbol { | synthDefSymbol, outsMappingSum |
		var synthDef = SynthDescLib.alga.at(synthDefSymbol).def;
		if(synthDef.isKindOf(SynthDef).not, {
			("AlgaPattern: Invalid AlgaSynthDef: '" ++
				synthDefSymbol.asString ++ "'").error;
			^nil
		});
		synthDef.outsMapping.keysValuesDo({ | key, outMapping |
			var oldOutsMapping = outsMappingSum[key];
			if(oldOutsMapping == nil, {
				outsMappingSum[key] = outMapping
			}, {
				if(oldOutsMapping != outMapping, {
					("AlgaPattern: outsMapping mismatch of SynthDef '" ++
						synthDefSymbol ++ "' for key '" ++ key ++ "'. Expected '" ++
						oldOutsMapping ++ "' but got '" ++ outMapping ++ "'").error;
					^nil;
				})
			});
		});
	}

	//This is for AlgaPattern + Pattern
	unpackPatternOutsMapping { | pattern, outsMappingSum |
		outsMappingSum = outsMappingSum ? IdentityDictionary();

		if(pattern.isSymbol, {
			if(this.unpackSynthDefSymbol(pattern, outsMappingSum) == nil, { ^nil });
		}, {
			//Pattern
			parser.parseGenericObject(pattern,
				func: { | val |
					this.unpackPatternOutsMapping(val, outsMappingSum);
				},
				replace: false
			)
		});

		^outsMappingSum;
	}

	//calculate the outs variable (the outs channel mapping)
	calculateOutsMapping { | replace = false, keepChannelsMapping = false |
		var outsMappingSynthDef;

		//For AlgaPattern: synthDef can be a ListPattern / FilterPatterm. In that case, sum all outsMappings
		if((this.isAlgaPattern).and(synthDef.isPattern), {
			outsMappingSynthDef = this.unpackPatternOutsMapping(synthDef);
			if(outsMappingSynthDef == nil, { ^nil });
		}, {
			//Normal case (no ListPattern): synthDef is an actual synthDef
			outsMappingSynthDef = synthDef.outsMapping;
		});

		//Accumulate channelsMapping across .replace calls.
		if(replace.and(keepChannelsMapping), {
			var newOutsMapping = IdentityDictionary(10);

			//copy previous ones
			outsMapping.keysValuesDo({ | key, value |
				//Delete out of bounds entries? Or keep them for future .replaces?
				//if(value < numChannels, {
				newOutsMapping[key] = value;
				//});
			});

			//new ones from the synthDef
			outsMappingSynthDef.keysValuesDo({ | key, value |
				//Delete out of bounds entries? Or keep them for future .replaces?
				//if(value < numChannels, {
				newOutsMapping[key] = value;
				//});
			});

			outsMapping = newOutsMapping;
		}, {
			//no replace: use synthDef's ones
			outsMapping = outsMappingSynthDef;
		});
	}

	//build all synths
	buildFromSynthDef { | initGroups = false, replace = false,
		keepChannelsMapping = false, keepScale = false, sched = 0 |

		//Retrieve controlNames
		var synthDescControlNames = synthDef.asSynthDesc.controls;
		if(this.createControlNamesAndParamsConnectionTime(synthDescControlNames).not, { ^this });

		numChannels = synthDef.numChannels;
		if(numChannels > AlgaStartup.algaMaxIO, {
			("AlgaNode: trying to instantiate the AlgaSynthDef '" ++ synthDef.name ++ "' which has more outputs(" ++ numChannels ++ ") than 'Alga.maxIO'(" ++ AlgaStartup.algaMaxIO ++ "). Change 'Alga.maxIO' to fit your needs and run 'Alga.boot' again.").error;
			this.clear;
			^this
		});

		//If explicit free, can't use in AlgaNode
		if(synthDef.explicitFree, {
			("AlgaNode: trying to instantiate the AlgaSynthDef '" ++ synthDef.name ++ "' which can free its synth. This is not supported for AlgaNodes, but it is for AlgaPatterns.").error;
			this.clear;
			^this;
		});

		//Check rate
		rate = synthDef.rate;

		//Calculate correct outsMapping
		this.calculateOutsMapping(replace, keepChannelsMapping);

		//Create groups if needed
		if(initGroups, { this.createAllGroups });

		//Create busses
		this.createAllBusses;

		//Check sched
		if(replace.not, { sched = sched ? schedInner });
		sched = sched ? 0;

		//Create actual synths
		this.addAction(
			func: {
				this.createAllSynths(
					replace,
					keepChannelsMapping:keepChannelsMapping,
					keepScale:keepScale
				);
			},
			sched: sched
		);
	}

	//Dispatch a SynthDef (symbol)
	dispatchSynthDef { | def, initGroups = false, replace = false,
		keepChannelsMapping = false, keepScale = false, sched = 0 |

		var synthDesc = SynthDescLib.alga.at(def);
		if(synthDesc == nil, {
			("AlgaNode: Invalid AlgaSynthDef: '" ++ def.asString ++ "'").error;
			^this;
		});

		synthDef = synthDesc.def;
		if(synthDef.isKindOf(SynthDef).not, {
			("AlgaNode: Invalid AlgaSynthDef: '" ++ def.asString ++"'").error;
			^this;
		});

		this.buildFromSynthDef(
			initGroups: initGroups,
			replace: replace,
			keepChannelsMapping:keepChannelsMapping,
			keepScale:keepScale,
			sched:sched
		);
	}

	//Dispatch a Function
	dispatchFunction { | def, initGroups = false, replace = false,
		keepChannelsMapping = false, outsMapping, keepScale = false, sched = 0 |

		//Wait condition
		var wait = Condition();

		fork {
			//Send the SynthDef, store it and wait for completion
			synthDef = AlgaSynthDef(
				("alga_" ++ UniqueID.next).asSymbol,
				def,
				outsMapping:outsMapping
			).sendAndAddToGlobalDescLib(server);

			//Just get standard SynthDef
			if(synthDef.isKindOf(AlgaSynthDefSpec), { synthDef = synthDef.synthDef });

			//Unlock condition
			server.sync(wait);
		};

		//Go ahead with the build function when Sdef is sent
		this.addAction(
			condition: { wait.test == true },
			func: {
				this.buildFromSynthDef(
					initGroups: initGroups,
					replace: replace,
					keepChannelsMapping:keepChannelsMapping,
					keepScale:keepScale,
					sched:sched
				)
			}
		);
	}

	resetSynth {
		if(algaToBeCleared, {
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
		if(algaToBeCleared, {
			//Just reset the Dictionaries entries
			interpSynths.clear;
			normSynths.clear;
		});
	}

	//Synth writes to the synthBus
	//Synth always uses longestConnectionTime, in order to make sure that everything connected to it
	//will have time to run fade ins and outs when running .replace!
	createSynth {
		var defName = synthDef.name;

		//synth's \fadeTime is longestWaitTime. It could probably be removed here,
		//as it will be set eventually in the case of .clear / etc...
		var synthArgs = [
			\out, synthBus.index,
			\fadeTime, if(tempoScaling, { longestWaitTime / this.clock.tempo }, { longestWaitTime }),
			\gate, 1
		];

		//connect each param with the already allocated normBus
		normBusses.keysValuesDo({ | param, normBus |
			synthArgs = synthArgs.add(param).add(normBus.busArg);
		});

		//create synth
		synth = AlgaSynth(
			defName,
			synthArgs,
			synthGroup
		);
	}

	//Reset the interp / norm dicts
	resetInterpNormDicts {
		interpSynths.clear;
		normSynths.clear;
		interpBusses.clear;
		normBusses.clear;
	}

	//Either retrieve default value from controlName or from args
	getDefaultOrArg { | controlName, param = \in, replace = false |
		var defArg;
		var defaultOrArg = controlName.defaultValue;
		var explicitArg = explicitArgs[param];

		if(defArgs != nil, {
			if(replace, {
				//replaceArgs are all the numbers that are set while coding.
				//On replace I wanna restore the current value I'm using, not the default value...
				//Unless I explicily set new args:
				defArg = replaceArgs[param];
			});

			//No values provided in replaceArgs, or new explicit args have been just set
			if((defArg == nil).or(explicitArg == true), {
				defArg = defArgs[param];
				explicitArgs[param] = false; //reset state
				replaceArgs.removeAt(param); //reset replaceArg
			});

			//If defArgs has entry, use that one as default instead
			if(defArg != nil, {
				//Pattern needs to be returned whatever the case (it's parsed later)
				if(this.isAlgaPattern, { ^defArg });

				//Special cases
				if((defArg.isAlgaNode).or(defArg.isAlgaTemp).or(defArg.isNumberOrArray).or(defArg.isAlgaArg), {
					defaultOrArg = defArg;
				});
			});
		});

		^defaultOrArg;
	}

	//Check correct array size for scale arguments
	checkScaleParameterSize { | scaleEntry, name, param, paramNumChannels |
		if(paramNumChannels == nil, {
			^scaleEntry
		});
		if(scaleEntry.isSequenceableCollection, {
			var scaleEntrySize = scaleEntry.size;
			if(scaleEntrySize != paramNumChannels, {
				("AlgaNode: the " ++ name ++ " entry of the scale parameter has less channels(" ++
					scaleEntrySize ++ ") than param " ++ param ++
					". Wrapping around " ++ paramNumChannels ++ " number of channels."
				).warn;
				^(scaleEntry.reshape(paramNumChannels));
			});
			^scaleEntry;
		}, {
			if(scaleEntry.isNumber, {
				^([scaleEntry].reshape(paramNumChannels));
			}, {
				"AlgaNode: scale entries can only be number or array".error;
				^nil;
			})
		});
	}

	addScaling { | param, sender, scale |
		if(paramsScaling[param] == nil, {
			paramsScaling[param] = IdentityDictionary(2);
			paramsScaling[param][sender] = scale;
		}, {
			paramsScaling[param][sender] = scale;
		});
	}

	removeScaling { | param, sender |
		if(paramsScaling[param] != nil, {
			paramsScaling[param].removeAt(sender);
		});
	}

	getParamScaling { | param, sender |
		if(paramsScaling[param] != nil, {
			^(paramsScaling[param][sender])
		});
		^nil;
	}

	//Calculate scale to send to interp synth
	calculateScaling { | param, sender, paramNumChannels, scale, addScaling = true |
		var scaleCopy;
		if(scale == nil, { ^nil });

		if(scale.isNumberOrArray.not, {
			"AlgaNode: the scale parameter must be a Number or an Array".error;
			^nil
		});

		//Essential
		scaleCopy = scale.copy;

		//just a number: act like a multiplier
		if(scale.isNumber, {
			var outArray = [\outMultiplier, scale];
			if(addScaling, { this.addScaling(param, sender, scale) });
			^outArray;
		});

		//highMin / highMax
		if(scale.size == 2, {
			var outArray = Array.newClear(6);
			var highMin = scale[0];
			var highMax = scale[1];
			var newHighMin = this.checkScaleParameterSize(highMin, "highMin", param, paramNumChannels);
			var newHighMax = this.checkScaleParameterSize(highMax, "highMax", param, paramNumChannels);

			if((newHighMin.isNil).or(newHighMax.isNil), {
				^nil
			});

			outArray[0] = \highMin;    outArray[1] = newHighMin;
			outArray[2] = \highMax;    outArray[3] = newHighMax;
			outArray[4] = \useScaling; outArray[5] = 1;

			scaleCopy[0] = newHighMin;
			scaleCopy[1] = newHighMax;

			if(addScaling, { this.addScaling(param, sender, scaleCopy) });

			^outArray;
		});

		//highMin / highMax / scaleCurve
		if(scale.size == 3, {
			var outArray = Array.newClear(8);
			var highMin = scale[0];
			var highMax = scale[1];
			var scaleCurve = scale[2];
			var newHighMin = this.checkScaleParameterSize(highMin, "highMin", param, paramNumChannels);
			var newHighMax = this.checkScaleParameterSize(highMax, "highMax", param, paramNumChannels);
			var newScaleCurve = scaleCurve.clip(-50, 50); //clip scaleScurve -50 / 50

			if((newHighMin.isNil).or(newHighMax.isNil).or(newScaleCurve.isNil), { ^nil });

			outArray[0] = \highMin;    outArray[1] = newHighMin;
			outArray[2] = \highMax;    outArray[3] = newHighMax;
			outArray[4] = \scaleCurve; outArray[5] = newScaleCurve;
			outArray[6] = \useScaling; outArray[7] = 1;

			scaleCopy[0] = newHighMin;
			scaleCopy[1] = newHighMax;
			scaleCopy[2] = newScaleCurve;

			if(addScaling, { this.addScaling(param, sender, scaleCopy) });

			^outArray;
		});

		//lowMin / lowMax / highMin / highMax
		if(scale.size == 4, {
			var outArray = Array.newClear(10);
			var lowMin = scale[0];
			var lowMax = scale[1];
			var highMin = scale[2];
			var highMax = scale[3];
			var newLowMin = this.checkScaleParameterSize(lowMin, "lowMin", param, paramNumChannels);
			var newLowMax = this.checkScaleParameterSize(lowMax, "lowMax", param, paramNumChannels);
			var newHighMin = this.checkScaleParameterSize(highMin, "highMin", param, paramNumChannels);
			var newHighMax = this.checkScaleParameterSize(highMax, "highMax", param, paramNumChannels);

			if((newLowMin.isNil).or(newHighMin.isNil).or(newLowMax.isNil).or(newHighMax.isNil), {
				^nil
			});

			outArray[0] = \lowMin;     outArray[1] = newLowMin;
			outArray[2] = \lowMax;     outArray[3] = newLowMax;
			outArray[4] = \highMin;    outArray[5] = newHighMin;
			outArray[6] = \highMax;    outArray[7] = newHighMax;
			outArray[8] = \useScaling; outArray[9] = 1;

			scaleCopy[0] = newLowMin;
			scaleCopy[1] = newLowMax;
			scaleCopy[2] = newHighMin;
			scaleCopy[3] = newHighMax;

			if(addScaling, { this.addScaling(param, sender, scaleCopy) });

			^outArray;
		});

		//lowMin / lowMax / highMin / highMax / scaleCurve
		if(scale.size == 5, {
			var outArray = Array.newClear(12);
			var lowMin = scale[0];
			var lowMax = scale[1];
			var highMin = scale[2];
			var highMax = scale[3];
			var scaleCurve = scale[4];
			var newLowMin = this.checkScaleParameterSize(lowMin, "lowMin", param, paramNumChannels);
			var newLowMax = this.checkScaleParameterSize(lowMax, "lowMax", param, paramNumChannels);
			var newHighMin = this.checkScaleParameterSize(highMin, "highMin", param, paramNumChannels);
			var newHighMax = this.checkScaleParameterSize(highMax, "highMax", param, paramNumChannels);
			var newScaleCurve = scaleCurve.clip(-50, 50); //clip scaleScurve -50 / 50

			if((newLowMin.isNil).or(newHighMin.isNil).or(newLowMax.isNil).or(newHighMax.isNil).or(newScaleCurve.isNil), {
				^nil
			});

			outArray[0] = \lowMin;      outArray[1] = newLowMin;
			outArray[2] = \lowMax;      outArray[3] = newLowMax;
			outArray[4] = \highMin;     outArray[5] = newHighMin;
			outArray[6] = \highMax;     outArray[7] = newHighMax;
			outArray[8] = \scaleCurve;  outArray[9] = newScaleCurve;
			outArray[10] = \useScaling; outArray[11] = 1;

			scaleCopy[0] = newLowMin;
			scaleCopy[1] = newLowMax;
			scaleCopy[2] = newHighMin;
			scaleCopy[3] = newHighMax;
			scaleCopy[4] = newScaleCurve;

			if(addScaling, { this.addScaling(param, sender, scaleCopy) });

			^outArray;
		});

		("AlgaNode: the 'scale' argument must be a Number (for multiplication) or an Array of either 2" ++
			" (highMin, highMax), 3 (highMin, highMax, curve), 4 (lowMin, lowMax, highMin, highMax)" ++
			" or 5 (lowMin, lowMax, highMin, highMax, curve) elements.").error;
		^nil
	}

	getParamChansMapping { | param, sender |
		if(paramsChansMapping[param] != nil, {
			^(paramsChansMapping[param][sender])
		});
		^nil;
	}

	//Calculate the array to be used as \indices param for interpSynth
	calculateSenderChansMappingArray { | param, sender, senderChansMapping,
		senderNumChans, paramNumChans, updateParamsChansMapping = true |

		var actualSenderChansMapping;

		actualSenderChansMapping = senderChansMapping.copy;

		//If senderChansMapping is nil or sender is not an AlgaNode, use default, modulo around senderNumChans
		if((actualSenderChansMapping == nil).or(sender.isAlgaNode.not), {
			^(Array.series(paramNumChans) % senderNumChans)
		});

		//Connect with outMapping symbols. Retrieve it from the sender
		if(actualSenderChansMapping.isSymbol, {
			actualSenderChansMapping = sender.outsMapping[actualSenderChansMapping];
			if(actualSenderChansMapping == nil, {
				("AlgaNode: invalid channel name '" ++ senderChansMapping ++ "'. Default will be used.").warn;
			});
		});

		//Update entry in Dict with the non-modified one (used in .replace then)
		if(updateParamsChansMapping, {
			paramsChansMapping[param][sender] = actualSenderChansMapping;
		});

		//Standard case, modulo around senderNumChans
		if(actualSenderChansMapping == nil, { ^(Array.series(paramNumChans) % senderNumChans) });

		if(actualSenderChansMapping.isSequenceableCollection, {
			//Also allow [\out1, \out2] here.
			actualSenderChansMapping.do({ | entry, index |
				if(entry.isSymbol, {
					actualSenderChansMapping[index] = sender.outsMapping[entry];
				});
			});

			//flatten the array, modulo around the max number of channels and reshape according to param num chans
			^((actualSenderChansMapping.flat % senderNumChans).reshape(paramNumChans));
		}, {
			if(actualSenderChansMapping.isNumber, {
				^(Array.fill(paramNumChans, { actualSenderChansMapping }) % senderNumChans);
			}, {
				"AlgaNode: senderChansMapping must be a number or an array. Using default one.".error;
				^(Array.series(paramNumChans) % senderNumChans);
			});
		});
	}

	//Remove activeInNodes / outNodes and reorder block
	removeActiveNodeAndRearrangeBlock { | param, sender |
		if(sender.isAlgaArg, { sender = sender.sender });
		if(sender.isAlgaNode, {
			//If this == sender, no need to go through with this
			var connectionToItself = (this == sender);
			if(connectionToItself, { ^this });

			//Remove active nodes
			this.removeActiveInNode(sender, param);
			sender.removeActiveOutNode(this, param);

			//Only re-order if all references to sender have been consumed
			if(activeInNodesCounter[sender] != nil, {
				if(activeInNodesCounter[sender] < 1, {
					var thisBlock   = AlgaBlocksDict.blocksDict[blockIndex];
					var senderBlock = AlgaBlocksDict.blocksDict[sender.blockIndex];
					if((thisBlock == senderBlock).and(thisBlock != nil), {
						thisBlock.rearrangeBlock;
					}, {
						if(thisBlock != nil, {
							thisBlock.rearrangeBlock;
						});
						if(senderBlock != nil, {
							senderBlock.rearrangeBlock;
						});
					});
				})
			})
		});
	}

	//Remove activeInNodes / outNodes and reorder block
	removeActiveNodesAndRearrangeBlocks { | param, sender |
		//AlgaNode or AlgaArg
		if(param.isAlgaNodeOrAlgaArg, {
			this.removeActiveNodeAndRearrangeBlock(param, sender);
		}, {
			//Generic object
			parser.parseGenericObject(sender,
				func: { | val |
					this.removeActiveNodesAndRearrangeBlocks(param, val);
				},
				replace: false
			)
		});
	}

	//The actual empty function
	removeActiveInterpSynthOnFree { | param, sender, senderSym, interpSynth, action |
		interpSynth.onFree({
			//Remove activeInterpSynth
			activeInterpSynths[param][senderSym].remove(interpSynth);

			//Remove activeInNodes / activeOutNodes and reorder blocks accordingly
			if(sender != nil, {
				this.removeActiveNodesAndRearrangeBlocks(param, sender);
			});

			//This is used in AlgaPattern
			if(action != nil, { action.value });
		});
	}

	//Use the .onFree node function to dynamically fill and empty the activeInterpSynths for
	//each param / sender combination!
	addActiveInterpSynthOnFree { | param, sender, senderSym, interpSynth, action |
		//Each sender has IdentitySet with all the active ones
		if(activeInterpSynths[param][senderSym].class == IdentitySet, {
			activeInterpSynths[param][senderSym].add(interpSynth)
		}, {
			activeInterpSynths[param][senderSym] = IdentitySet();
			activeInterpSynths[param][senderSym].add(interpSynth)
		});

		//The actual function that empties
		this.removeActiveInterpSynthOnFree(param, sender, senderSym, interpSynth, action);
	}

	//Set proper fadeTime / shape for all active interpSynths on param / sender combination
	setFadeTimeForAllActiveInterpSynths { | param, sender, time, shape |
		activeInterpSynths[param][sender].do({ | activeInterpSynth |
			activeInterpSynth.set(
				\t_release, 1,
				\fadeTime, if(tempoScaling, { time / this.clock.tempo }, { time }),
				\envBuf, AlgaDynamicEnvelopes.getOrAdd(shape, server),
			);
		});
	}

	//AlgaNode.new or .replace
	createInterpNormSynths { | replace = false, keepChannelsMapping = false, keepScale = false |
		controlNames.do({ | controlName |
			var normBus;

			var paramName = controlName.name;
			var paramNumChannels = controlName.numChannels;

			var paramRate = controlName.rate;
			var paramDefault = this.getDefaultOrArg(controlName, paramName, replace);

			var noSenders = false;

			normBus = normBusses[paramName];
			if(normBus == nil, {
				("AlgaNode: invalid normBus for param: '" ++ paramName ++ "'. Unlike AlgaPatterns, 'scalar' parameters are not supported in AlgaNodes. Use a 'control' parameter instead.").error;
				^this
			});

			//If replace and sendersSet contains inNodes, connect back to the .synthBus of the AlgaNode.
			//Note that an inNode can also be an AlgaArg, in which case it also gets unpacked accordingly
			if(replace, {
				//Restoring a connected parameter, being it normal or mix
				var sendersSet = inNodes[paramName];
				if(sendersSet != nil, {
					if(sendersSet.size > 0, {
						//if size == 1, index from \default
						var onlyEntry = sendersSet.size == 1;

						//Loop through
						sendersSet.do({ | prevSender |
							var interpBus, interpSynth, normSynth;
							var interpSymbol, normSymbol;

							var prevSenderNumChannels, prevSenderRate;

							var oldParamsChansMapping = nil;
							var oldParamScale = nil;

							var interpSynthArgs;

							var channelsMapping;
							var scaleArray;

							if(onlyEntry, {
								//normal param... Also update default node!
								interpBus = interpBusses[paramName][\default];
							}, {
								//mix param: create a new bus too
								interpBus = AlgaBus(server, paramNumChannels + 1, paramRate);
								interpBusses[paramName][prevSender] = interpBus;
							});

							//If AlgaArg, unpack
							if(prevSender.isAlgaArg, {
								oldParamsChansMapping = prevSender.chansStream;
								oldParamScale = prevSender.scaleStream;
								prevSender = prevSender.sender;
							});

							//If prevSender == this, it's a FB connection: consider it valid
							//Make sure that the algaNode can actually send the connection
							if((prevSender == this).or(prevSender.isAlgaNode.and(prevSender.algaInstantiatedAsSender)), {
								prevSenderRate = prevSender.rate;
								prevSenderNumChannels = prevSender.numChannels;

								//Use previous entry for the channel mapping, otherwise, nil.
								//nil will generate Array.series(...) in calculateSenderChansMappingArray
								if(keepChannelsMapping, {
									oldParamsChansMapping = oldParamsChansMapping ?
									this.getParamChansMapping(paramName, prevSender);
								});

								//Use previous entry for inputs scaling
								if(keepScale, {
									oldParamScale = oldParamScale ?
									this.getParamScaling(paramName, prevSender);
								});

								//overwrite interp symbol considering the senders' num channels!
								interpSymbol = (
									"alga_interp_" ++
									prevSenderRate ++
									prevSenderNumChannels ++
									"_" ++
									paramRate ++
									paramNumChannels
								).asSymbol;

								normSymbol = (
									"alga_norm_" ++
									paramRate ++
									paramNumChannels
								).asSymbol;

								//Calculate the array for channelsMapping
								channelsMapping = this.calculateSenderChansMappingArray(
									paramName,
									prevSender,
									oldParamsChansMapping,
									prevSenderNumChannels,
									paramNumChannels,
									false
								);

								scaleArray = this.calculateScaling(
									paramName,
									prevSender,
									paramNumChannels,
									oldParamScale
								);

								interpSynthArgs = [
									\in, prevSender.synthBus.busArg,
									\out, interpBus.index,
									\indices, channelsMapping,
									\fadeTime, 0,
									\envShape, AlgaDynamicEnvelopes.getOrAdd(interpShape, server)
								];

								//Add scale array to args
								if(scaleArray != nil, {
									interpSynthArgs = interpSynthArgs.addAll(scaleArray);
								});

								interpSynth = AlgaSynth(
									interpSymbol,
									interpSynthArgs,
									interpGroup
								);

								//Instantiated right away, with no \fadeTime, as it will directly be connected to
								//synth's parameter. Synth will read its params from all the normBusses
								normSynth = AlgaSynth(
									normSymbol,
									[\args, interpBus.busArg, \out, normBus.index, \fadeTime, 0],
									normGroup,
									waitForInst:false
								);

								//The activeInNode / activeOutNode counter MUST be updated on replace!
								//AlgaPattern already does this with AlgaPatternInterpStreams.add
								this.addActiveInOutNodes(prevSender, paramName);

								//Normal param OR mix param
								if(onlyEntry, {
									//normal param
									interpSynths[paramName][\default] = interpSynth;
									normSynths[paramName][\default] = normSynth;

									//Add interpSynth to the current active ones for specific param / sender combination
									this.addActiveInterpSynthOnFree(paramName, prevSender, \default, interpSynth);
								}, {
									//mix param
									interpSynths[paramName][prevSender] = interpSynth;
									normSynths[paramName][prevSender] = normSynth;

									//Add interpSynth to the current active ones for specific param / sender combination
									this.addActiveInterpSynthOnFree(paramName, prevSender, prevSender, interpSynth);

									//And update the ones in \default if this sender is the currentDefaultNode!!!
									//This is essential, because otherwise interpBusses[paramName][\default]
									//would be a bus reading from nothing!
									if(currentDefaultNodes[paramName] == prevSender, {
										interpBusses[paramName][\default] = interpBus; //previous' \default should be freed
										interpSynths[paramName][\default] = interpSynth;
										normSynths[paramName][\default] = normSynth;
									});
								});
							});
						})
					}, {
						noSenders = true
					});
				}, {
					noSenders = true;
				})
			});

			//paramDefault can either be an AlgaTemp or a Number / Array
			if(replace.not.or(noSenders), {
				var oldParamsChansMapping = nil;
				var oldParamScale = nil;
				var defaultNumChannels = paramNumChannels;
				var defaultRate = paramRate;
				var channelsMapping, scaleArray;
				var interpSymbol, normSymbol, interpBus, interpSynthArgs, interpSynth, normSynth;
				var tempSynthsAndBusses;
				var isValid = false;
				var storeCurrentDefaultForMix = false;
				var senderToStoreForMix;

				case
				//AlgaNode
				{ paramDefault.isAlgaNode } {
					if(paramDefault.algaInstantiatedAsSender, {
						var algaNodeSynthBus = paramDefault.synthBus;
						senderToStoreForMix = paramDefault;
						defaultNumChannels = paramDefault.numChannels;
						defaultRate = paramDefault.rate;
						paramDefault = algaNodeSynthBus.busArg;
						isValid = true;
						storeCurrentDefaultForMix = true;
					});
				}

				//AlgaTemp
				{ paramDefault.isAlgaTemp } {
					tempSynthsAndBusses = IdentitySet(8);

					//If invalid, exit
					if(paramDefault.valid.not, {
						("AlgaNode: invalid AlgaTemp for parameter '" ++ paramName.asString ++ "'").error;
						^this;
					});

					defaultNumChannels = paramDefault.numChannels;
					defaultRate = paramDefault.rate;

					//Make sure to use AlgaTemp's scaling if possible (it will be nil otherwise)
					oldParamsChansMapping = paramDefault.chans;
					oldParamScale = paramDefault.scale;

					paramDefault = this.createAlgaTempSynth(
						algaTemp: paramDefault,
						tempSynthsAndBusses: tempSynthsAndBusses
					); //returns busArg that tempSynth is writing to

					isValid = true;
				}

				//AlgaArg
				{ paramDefault.isAlgaArg } {
					var algaNodeSynthBus;
					var algaNode = paramDefault.sender;

					if(algaNode.isAlgaNode.not, {
						("AlgaNode: invalid AlgaArg for parameter '" ++ paramName.asString ++ "'").error;
						^this
					});

					if(algaNode.synthBus == nil, {
						("AlgaNode: invalid AlgaArg synthBus for parameter '" ++ paramName.asString ++ "'").error;
						^this
					});

					defaultNumChannels = algaNode.numChannels;
					defaultRate = algaNode.rate;

					//Make sure to use AlgaArgs's scaling if possible (it will be nil otherwise)
					oldParamsChansMapping = paramDefault.chansStream;
					oldParamScale = paramDefault.scaleStream;

					//Get the busArg of the synthBus
					if(algaNode.algaInstantiatedAsSender, {
						var algaNodeSynthBus = algaNode.synthBus;
						senderToStoreForMix = algaNode;
						paramDefault = algaNodeSynthBus.busArg;
						isValid = true;
						storeCurrentDefaultForMix = true;
					});
				}

				//Array
				{ paramDefault.isArray } {
					defaultNumChannels = paramDefault.size;
					defaultRate = controlName.rate;
					isValid = true;
				}

				//Number
				{ paramDefault.isNumber } {
					defaultNumChannels = controlName.numChannels;
					defaultRate = controlName.rate;
					isValid = true;
				};

				//If valid is false, use default from controlNames
				if(isValid.not, {
					paramDefault = controlName.defaultValue;
					defaultNumChannels = controlName.numChannels;
					defaultRate = controlName.rate;
					("AlgaNode: invalid default value retrieved. Using the definition's one").warn;
				});

				//e.g. \alga_interp_audio1_control1
				interpSymbol = (
					"alga_interp_" ++
					defaultRate ++
					defaultNumChannels ++
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

				//Use previous entry for the channel mapping, otherwise, nil.
				//nil will generate Array.series(...) in calculateSenderChansMappingArray
				if(keepChannelsMapping, {
					//Might have been defined by AlgaTemp
					oldParamsChansMapping = oldParamsChansMapping ? this.getParamChansMapping(paramName, \default);
				});

				//Use previous entry for inputs scaling
				if(keepScale, {
					//Might have been defined by AlgaTemp
					oldParamScale = oldParamScale ? this.getParamScaling(paramName, \default);
				});

				//Calculate the array for channelsMapping
				channelsMapping = this.calculateSenderChansMappingArray(
					paramName,
					\default,
					oldParamsChansMapping,
					defaultNumChannels,
					paramNumChannels,
					false //Always false, either if keeping the old one or using AlgaTemp's
				);

				//calculate scale array (use senderSym (\default))
				scaleArray = this.calculateScaling(
					paramName,
					\default,
					paramNumChannels,
					oldParamScale,
					false //Always false, either if keeping the old one or using AlgaTemp's
				);

				//default interpBus
				interpBus = interpBusses[paramName][\default];

				//args
				interpSynthArgs = [
					\in, paramDefault,
					\out, interpBus.index,
					\indices, channelsMapping,
					\fadeTime, 0,
					\envShape, AlgaDynamicEnvelopes.getOrAdd(interpShape, server)
				];

				//add scaleArray to args
				if(scaleArray != nil, {
					interpSynthArgs = interpSynthArgs.addAll(scaleArray);
				});

				//new interpSynth
				interpSynth = AlgaSynth(
					interpSymbol,
					interpSynthArgs,
					interpGroup
				);

				//Instantiated right away, with no \fadeTime, as it will directly be connected to
				//synth's parameter. Synth will read its params from all the normBusses
				normSynth = AlgaSynth(
					normSymbol,
					[\args, interpBus.busArg, \out, normBus.index, \fadeTime, 0],
					normGroup,
					waitForInst:false
				);

				//replace \default entries
				interpSynths[paramName][\default] = interpSynth;
				normSynths[paramName][\default] = normSynth;

				//Add interpSynth to the current active ones for specific param / sender combination
				this.addActiveInterpSynthOnFree(paramName, nil, \default, interpSynth);

				//When interpSynth is freed, free the synths / busses for AlgaTemps
				if(tempSynthsAndBusses != nil, {
					interpSynth.onFree({
						fork {
							0.5.wait;
							tempSynthsAndBusses.do({ | tempSynthOrBus |
								tempSynthOrBus.free
							})
						}
					});
				});

				//Store current \default: this is needed for .mixFrom.
				//It's only true for AlgaNodes and AlgaArgs
				if(storeCurrentDefaultForMix, { currentDefaultNodes[paramName] = senderToStoreForMix });
			});
		});
	}

	//Create all synths for each param
	createAllSynths { | replace = false, keepChannelsMapping = false, keepScale = false |
		this.createInterpNormSynths(
			replace:replace,
			keepChannelsMapping:keepChannelsMapping,
			keepScale:keepScale
		);

		this.createSynth;
	}

	//Create new interpBus and normSynth for mix entry
	createMixInterpBusAndNormSynthAtParam {  | sender, param = \in |
		var controlName;
		var paramNumChannels, paramRate;
		var normSymbol, normBus;
		var interpBus, normSynth;
		var senderSym = sender;

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

		normSynth = AlgaSynth(
			normSymbol,
			[\args, interpBus.busArg, \out, normBus.index, \fadeTime, 0],
			normGroup,
			waitForInst:false
		);

		//If AlgaArg, use its AlgaNode as sender
		if(sender.isAlgaArg, { senderSym = sender.sender });

		interpBusses[param][senderSym] = interpBus;
		normSynths[param][senderSym] = normSynth;
	}

	//Run when <<+ / .replace (on mixed connection) / .replaceMix
	createMixInterpSynthAndInterpBusAndNormSynthAtParam { | sender, param = \in, replaceMix = false,
		replace = false, senderChansMapping, scale, time, shape |

		//also check sender is not the default!
		var newMixConnection = (
			this.mixParamContainsSender(param, sender).not).and(
			sender != currentDefaultNodes[param]
		);

		//Only run if in need of a new connection, and not replaceMix.
		//With replaceMix, normSynth and interpBus are already alive and kept from before
		if(newMixConnection.and(replaceMix.not), {
			this.createMixInterpBusAndNormSynthAtParam(
				sender: sender,
				param: param
			);
		});

		//Make sure to not duplicate something that's already in the mix
		if(newMixConnection.or(replace).or(replaceMix), {
			this.createInterpSynthAtParam(
				sender:sender, param:param,
				mix:true, replaceMix:replaceMix,
				senderChansMapping:senderChansMapping,
				scale:scale, time:time, shape:shape
			);
		}, {
			//the alga node is already mixed. run replaceMix with itself
			//this is useful in case scale parameter has been changed by user
			"AlgaNode: sender was already mixed. Running 'replaceMix' with itself instead".warn;
			this.replaceMixInner(
				param: param,
				oldSender: sender,
				newSender: sender,
				chans: senderChansMapping,
				scale: scale,
				time: time,
				shape: shape
			);
		});
	}

	//Check valid controlName name
	checkValidControlName { | paramName |
		^((paramName != '?').and(paramName != \instrument).and(
			paramName != \def).and(paramName != \out).and(
			paramName != \gate).and(paramName != \fadeTime).and(
			paramName != \dur).and(paramName != \sustain).and(
			paramName != \stetch).and(paramName != \legato).and(
			paramName != \timingOffset).and(paramName != \lag)
		)
	}

	//Create tempSynth for AlgaTemp
	createAlgaTempSynth { | algaTemp, tempSynthsAndBusses, topLevelTempGroup |
		//The bus the tempSynth will write to
		var tempBus, tempSynth;
		var tempSynthArgs = [\gate, 1];
		var tempNumChannels = algaTemp.numChannels;
		var tempRate = algaTemp.rate;
		var def, algaTempDef, controlNames;
		var defIsEvent = false;

		//Create a new top level group
		if(topLevelTempGroup == nil, {
			topLevelTempGroup = AlgaGroup(tempGroup, waitForInst: false);
		});

		//Check AlgaTemp validity
		if(algaTemp.valid.not, {
			"AlgaNode: Invalid AlgaTemp, using default parameter".error;
			^nil
		});

		//Unpack SynthDef
		algaTempDef = algaTemp.def;
		def = algaTempDef;

		//If Event, SynthDef is under [\def]
		if(algaTempDef.isEvent, {
			def = algaTempDef[\def];
			defIsEvent = true;
		});

		//Unpack controlNames
		controlNames = algaTemp.controlNames;

		//Loop around the controlNames to set relevant parameters
		controlNames.do({ | controlName |
			var paramName = controlName.name;
			var paramNumChannels = controlName.numChannels;
			var paramRate = controlName.rate;
			var paramDefault = controlName.defaultValue;
			var entry;

			//Ignore static params
			if(this.checkValidControlName(paramName), {
				//Retrieve param if entry is Event
				if(defIsEvent, { entry = algaTempDef[paramName] });

				//If entry is nil, the tempSynth will already use the default value
				if(entry != nil, {
					case
					//If entry is an AlgaTemp
					{ entry.isAlgaTemp } {
						if((paramRate == \control).or(paramRate == \audio), {
							//Check scale / chans
							var paramTempScale = entry.scale;
							var paramTempChans = entry.chans;

							//Create a new AlgaTemp and return its bus (it's already registered, and it returns .busArg)
							var paramTempBus = this.createAlgaTempSynth(entry, tempSynthsAndBusses, topLevelTempGroup);

							//Check for channels / rate mismatch
							if((paramRate != entry.rate).or(paramNumChannels != entry.numChannels).or(
								paramTempScale != nil).or(paramTempChans != nil), {
								//Converter bus
								var paramBus = AlgaBus(server, paramNumChannels, paramRate);

								//Converter symbol
								var converterSymbol = (
									"alga_pattern_" ++
									entry.rate ++
									entry.numChannels ++
									"_" ++
									paramRate ++
									paramNumChannels ++
									"_fx"
								).asSymbol;

								//converter Synth
								var converterSynth;

								//converter synth args
								var converterSynthArgs = [
									\in, paramTempBus,
									\out, paramBus.index
								];

								//Calculate scaleArray
								if(paramTempScale != nil, {
									var scaleArray = this.calculateScaling(
										paramName,
										nil,
										paramNumChannels,
										paramTempScale,
										false //don't update the AlgaNode's scalings dict
									);
									converterSynthArgs = converterSynthArgs.addAll(scaleArray);
								});

								//Calculate chansMapping
								if(paramTempChans != nil, {
									var indices = this.calculateSenderChansMappingArray(
										paramName,
										nil,
										paramTempChans,
										entry.numChannels,
										paramNumChannels,
										false //don't update the AlgaNode's chans dict
									);
									converterSynthArgs = converterSynthArgs.add(\indices).add(indices);
								});

								//Create synth
								converterSynth = AlgaSynth(
									converterSymbol,
									converterSynthArgs,
									topLevelTempGroup,
									\addToTail,
									false
								);

								//Add bus to tempSynth at correct paramName
								tempSynthArgs = tempSynthArgs.add(paramName).add(paramBus.busArg);

								//Register bus to be freed
								tempSynthsAndBusses.add(paramBus);
							}, {
								//Same rate / num channels: use paramTempBus directly
								tempSynthArgs = tempSynthArgs.add(paramName).add(paramTempBus);
							});
						});
					}
					//If entry is a Number / Array
					{ entry.isNumberOrArray } {
						if((paramRate == \scalar).or(entry.size == 0), {
							tempSynthArgs = tempSynthArgs.add(paramName).add(entry);
						}, {
							if((paramRate == \control).or(paramRate == \audio), {
								//Check if array needs conversion
								if(paramNumChannels != entry.size, {
									var arraySize = entry.size;

									//Converter bus
									var paramBus = AlgaBus(server, paramNumChannels, paramRate);

									//Converter symbol
									var converterSymbol = (
										"alga_pattern_" ++
										paramRate ++
										arraySize ++
										"_" ++
										paramRate ++
										paramNumChannels ++
										"_fx"
									).asSymbol;

									//Converter Synth
									var converterSynth = AlgaSynth(
										converterSymbol,
										[ \in, entry, \out, paramBus.index ],
										topLevelTempGroup,
										\addToTail,
										false
									);

									//Add bus to tempSynth at correct paramName
									tempSynthArgs = tempSynthArgs.add(paramName).add(paramBus.busArg);

									//Register bus to be freed
									tempSynthsAndBusses.add(paramBus);
								});
							});
						});
					};
				});
			});
		});

		//The AlgaBus the tempSynth will write to
		tempBus = AlgaBus(server, tempNumChannels, tempRate);

		//Write output to tempBus
		tempSynthArgs = tempSynthArgs.add(\out).add(tempBus.index);

		//The actual AlgaSynth
		tempSynth = AlgaSynth(
			def,
			tempSynthArgs,
			topLevelTempGroup,
			\addToTail,
			false
		);

		//Free the group on synth's free (it will free all synths too)
		tempSynthsAndBusses.add(topLevelTempGroup);

		//Free the bus on synth's free
		tempSynthsAndBusses.add(tempBus);

		//Return the AlgaBus that the tempSynth writes to
		^tempBus.busArg;
	}

	//Used at every <<, >>, <<+, >>+, <|
	createInterpSynthAtParam { | sender, param = \in, mix = false,
		replaceMix = false, senderChansMapping, scale, time, shape |

		var controlName, paramConnectionTime;
		var paramNumChannels, paramRate;
		var senderNumChannels, senderRate;

		var senderChansMappingToUse;

		var scaleArray;

		var interpSymbol;

		var interpBusAtParam;
		var interpBus, interpSynth;
		var interpSynthArgs;

		var senderSym = sender;

		//senderSym is used to address interpSynthsAtParam. It's either \default or AlgaNode
		if(mix, {
			if(sender == nil, { senderSym = \default });
		}, {
			senderSym = \default;
		});

		//Extract controlName / paramConnectionTime
		controlName = controlNames[param];
		paramConnectionTime = this.getParamConnectionTime(param);
		if((controlName.isNil).or(paramConnectionTime.isNil), {
			("AlgaNode: invalid param for interp synth to free: '" ++ param ++ "'").error;
			^this
		});

		//calc temporary time
		time = this.calculateTemporaryLongestWaitTime(time, paramConnectionTime);

		//Get shape
		shape = shape.algaCheckValidEnv(server: server) ? this.getInterpShape(param);

		//Get param's numChannels / rate
		paramNumChannels = controlName.numChannels;
		paramRate = controlName.rate;

		//get interp bus ident dict at specific param
		interpBusAtParam = interpBusses[param];
		if(interpBusAtParam == nil, { ("AlgaNode: invalid interp bus at param '" ++ param ++ "'").error; ^this });

		//Try to get sender's interpBusAtParam
		//If not there, get the default one (and assign it to sender for both interpBus and normSynth at param)
		interpBus = interpBusAtParam[senderSym];
		if(interpBus == nil, {
			interpBus = interpBusAtParam[\default];
			if(interpBus == nil, {
				(
					"AlgaNode: invalid interp bus at param '" ++
					param ++ "' and node " ++ senderSym.asString
				).error;
				^this
			});

			interpBusAtParam[senderSym] = interpBus;
		});

		//If mix and NOT replaceMix, spawn a fadeIn synth.
		//fadeIn balances out the interpSynth's envelope before normSynth.
		//A fadeIn synth contains all zeroes, except for the envelope (at last position).
		if(mix.and(replaceMix.not), {
			var fadeInSymbol = (
				"alga_fadeIn_" ++
				paramRate ++
				paramNumChannels
			).asSymbol;

			AlgaSynth(
				fadeInSymbol,
				[
					\out, interpBus.index,
					\fadeTime, if(tempoScaling, { time / this.clock.tempo }, { time }),
					\envShape, AlgaDynamicEnvelopes.getOrAdd(shape, server)
				],
				interpGroup,
				waitForInst:false
			);
		});

		//new interp synth, with input connected to sender and output to the interpBus
		//THIS USES connectionTime!!
		if(sender.isAlgaNode, {
			var updateScale = true;
			var updateChans = true;

			//Take numChannels / rate of AlgaNode
			senderNumChannels = sender.numChannels;
			senderRate = sender.rate;

			//Symbol for the interpSynth
			interpSymbol = (
				"alga_interp_" ++
				senderRate ++
				senderNumChannels ++
				"_" ++
				paramRate ++
				paramNumChannels
			).asSymbol;

			//chansMapping
			senderChansMappingToUse = this.calculateSenderChansMappingArray(
				param,
				sender, //must be sender! not senderSym!
				senderChansMapping,
				senderNumChannels,
				paramNumChannels,
				updateChans //Don't add to global if using AlgaArg's chans
			);

			//The actual interpSynth args
			interpSynthArgs = [
				\in, sender.synthBus.busArg,
				\out, interpBus.index,
				\indices, senderChansMappingToUse,
				\fadeTime, if(tempoScaling, { time / this.clock.tempo }, { time }),
				\envShape, AlgaDynamicEnvelopes.getOrAdd(shape, server)
			];

			//calculate scale array (use sender)
			scaleArray = this.calculateScaling(
				param,
				sender,
				paramNumChannels,
				scale,
				updateScale //Don't add to global if using AlgaArg's scale
			);

			//add scaleArray to args
			if(scaleArray != nil, {
				interpSynthArgs = interpSynthArgs.addAll(scaleArray);
			});

			//Read \in from the sender's synthBus
			interpSynth = AlgaSynth(
				interpSymbol,
				interpSynthArgs,
				interpGroup
			);
		}, {
			//Used in <| AND << with number / array
			//if sender is nil, restore the original default value. This is used in <|
			var paramVal;
			var tempSynthsAndBusses;
			var updateScale = true;
			var updateChans = true;
			var isValid = false;

			//Use default value if sender == nil
			if(sender == nil, {
				paramVal = this.getDefaultOrArg(controlName, param); //either default or provided arg!
				sender = paramVal;
			});

			case
			//AlgaNode (can come from getDefaultOrArg)
			{ sender.isAlgaNode } {
				if(sender.algaInstantiatedAsSender, {
					var algaNodeSynthBus = sender.synthBus;
					senderNumChannels = sender.numChannels;
					senderRate = sender.rate;
					paramVal = algaNodeSynthBus.busArg;
					isValid = true
				});
			}

			//Just a Number / Array
			{ sender.isNumberOrArray } {
				if(sender.isArray, {
					senderNumChannels = sender.size
				}, {
					senderNumChannels = paramNumChannels
				});
				senderRate = paramRate;
				paramVal = sender;
				isValid = true;
			}

			//Function / Symbol / AlgaTemp
			{ sender.isAlgaTemp } {
				tempSynthsAndBusses = IdentitySet(8);
				senderNumChannels = sender.numChannels;
				senderRate = sender.rate;

				//Used to avoid updating scale / chans if retrieving from AlgaTemp's
				if(sender.scale != nil, { updateScale = false });
				if(sender.chans != nil, { updateChans = false });

				//Use AlgaTemp's scale / chans if provided
				scale = sender.scale ? scale;
				senderChansMapping = sender.chans ? senderChansMapping;

				paramVal = this.createAlgaTempSynth(
					algaTemp: sender,
					tempSynthsAndBusses: tempSynthsAndBusses
				); //returns busArg that tempSynth is writing to

				isValid = true;
			}

			//AlgaArg
			{ sender.isAlgaArg } {
				var algaNode = sender.sender;
				if(algaNode.isAlgaNode.not, { ^nil });

				senderNumChannels = algaNode.numChannels;
				senderRate = algaNode.rate;

				//Used to avoid updating scale / chans if retrieving from AlgaTemp's
				if(sender.scale != nil, { updateScale = false });
				if(sender.chans != nil, { updateChans = false });

				//Use AlgaTemp's scale / chans if provided
				scale = sender.scale ? scale;
				senderChansMapping = sender.chans ? senderChansMapping;

				//Get the busArg of the synthBus
				if(algaNode.algaInstantiatedAsSender, {
					var algaNodeSynthBus = algaNode.synthBus;
					paramVal = algaNodeSynthBus.busArg;
					senderSym = algaNode; //Also senderSym needs to be modified using the AlgaNode
					isValid = true
				});
			};

			//If valid is false, use default from controlNames
			if(isValid.not, {
				paramVal = controlName.defaultValue;
				senderNumChannels = controlName.numChannels;
				senderRate = controlName.rate;
				senderSym = \default;
				("AlgaNode: invalid default value retrieved. Using the definition's one").warn;
			});

			//Symbol for the interpSynth
			interpSymbol = (
				"alga_interp_" ++
				senderRate ++
				senderNumChannels ++
				"_" ++
				paramRate ++
				paramNumChannels
			).asSymbol;

			//chansMapping
			senderChansMappingToUse = this.calculateSenderChansMappingArray(
				param,
				\default, //Pass \default!
				senderChansMapping,
				senderNumChannels,
				paramNumChannels,
				updateChans //Don't add to global if using AlgaTemp's chans
			);

			//args
			interpSynthArgs = [
				\in, paramVal,
				\out, interpBus.index,
				\indices, senderChansMappingToUse,
				\fadeTime, if(tempoScaling, { time / this.clock.tempo }, { time }),
				\envShape, AlgaDynamicEnvelopes.getOrAdd(shape, server)
			];

			//calculate scale array
			scaleArray = this.calculateScaling(
				param,
				\default, //Pass \default !
				paramNumChannels,
				scale,
				updateScale //Don't add to global if using AlgaTemp's scale
			);

			//add scaleArray to args
			if(scaleArray != nil, {
				interpSynthArgs = interpSynthArgs.addAll(scaleArray);
			});

			//Actual interpSynth. Make sure it's always before the synths created
			//by AlgaTemp (that' why \addToHead)
			interpSynth = AlgaSynth(
				interpSymbol,
				interpSynthArgs,
				interpGroup,
				\addToHead
			);

			//When interpSynth is freed, free the synths / busses for algaTemp
			if(tempSynthsAndBusses != nil, {
				interpSynth.onFree({
					fork {
						0.5.wait;
						tempSynthsAndBusses.do({ | tempSynthOrBus |
							tempSynthOrBus.free
						})
					}
				});
			});
		});

		//Add to interpSynths for the param
		interpSynths[param][senderSym] = interpSynth;

		//Add interpSynth to the current active ones for specific param / sender combination
		this.addActiveInterpSynthOnFree(param, sender, senderSym, interpSynth);

		//Store current \default (needed when going from mix == true to mix == false)...
		//basically, restoring proper connections after going from <<+ to << or <|
		//This is only activated with << and <<|, and it makes sure of hainvg proper \default nodes
		//The \default entries for interpBusses, interpSynths and normSynths
		//are already taken care of in createInterpNormSynths
		if((senderSym == \default).and(mix == false), {
			currentDefaultNodes[param] = sender;
		});
	}

	//Default now and useConnectionTime to true for synths.
	//Synth always uses longestConnectionTime, in order to make sure that everything connected to it
	//will have time to run fade ins and outs
	freeSynth { | useConnectionTime = true, now = true, time |
		if(now, {
			if(synth != nil, {
				//synth's fadeTime is longestWaitTime!
				synth.set(
					\gate, 0,
					\fadeTime, if(useConnectionTime, {
						if(tempoScaling, { longestWaitTime / this.clock.tempo }, { longestWaitTime })
					}, { 0 })
				);

				//this.resetSynth;
			});
		}, {
			//Needs to be deep copied (a new synth could be algaInstantiated meanwhile)
			var prevSynth = synth.copy;

			if(time == nil, { time = longestWaitTime });

			fork {
				//Cheap solution when having to replacing a synth that had other interp stuff
				//going on. Simply wait longer than longestWaitTime (which will be the time the replaced
				//node will take to interpolate to the previous receivers) and then free all the previous stuff
				(time + 1.0).wait;

				if(prevSynth != nil, {
					prevSynth.set(\gate, 0, \fadeTime, 0);
				});
			}
		});
	}

	//Default now to true
	freeInterpNormSynths { | now = true, time |
		//These are handled by AlgaPattern
		if(this.isAlgaPattern, { ^nil });

		if(now, {
			//Free synths now
			interpSynths.do({ | interpSynthsAtParam |
				interpSynthsAtParam.do({ | interpSynth |
					interpSynth.set(\t_release, 1, \fadeTime, 0);
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

			if(time == nil, { time = longestWaitTime });

			fork {
				//Cheap solution when having to replacing a synth that had other interp stuff
				//going on. Simply wait longer than longestWaitTime (which will be the time the replaced
				//node will take to interpolate to the previous receivers) and then free all the previous stuff
				(time + 1.0).wait;

				prevInterpSynths.do({ | interpSynthsAtParam |
					interpSynthsAtParam.do({ | interpSynth |
						interpSynth.set(\t_release, 1, \fadeTime, 0);
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

	freeAllSynths { | useConnectionTime = true, now = true, time |
		this.freeInterpNormSynths(now, time);
		this.freeSynth(useConnectionTime, now, time);
	}

	//Free the entire mix node at specific param.
	freeMixNodeAtParam { | sender, param = \in, paramConnectionTime,
		replace = false, cleanupDicts = false, replaceMix = false, time, shape |

		var interpSynthAtParam;

		//If AlgaArg: extract hte node (it's what it's used in interpSynths as index)
		if(sender.isAlgaArg, { sender = sender.sender });

		//Get the actual interpSynths at parameter
		interpSynthAtParam = interpSynths[param][sender];

		if(interpSynthAtParam != nil, {
			//These must be fetched before the addAction (they refer to the current state!)
			var normSynthAtParam = normSynths[param][sender];
			var interpBusAtParam = interpBusses[param][sender];
			var currentDefaultNode = currentDefaultNodes[param];

			//Store it here cause it can also be executed right away, not only in scheduler
			var fadeOutFunc = {
				var notDefaultNode = false;

				//Only run fadeOut and remove normSynth if they are also not the ones that are used for \default.
				//This makes sure that \defauls is kept alive at all times
				if(sender != currentDefaultNode, { notDefaultNode = true });

				//calculate temporary time
				time = this.calculateTemporaryLongestWaitTime(time, paramConnectionTime);

				//Get shape
				shape = shape.algaCheckValidEnv(server: server) ? this.getInterpShape(param);

				//Only create fadeOut and free normSynth on .disconnect! (not .replace / .replaceMix).
				//Also, don't create it for the default node, as that needs to be kept alive at all times!
				if(notDefaultNode.and(replace.not).and(replaceMix.not), {
					var fadeOutSymbol = (
						"alga_fadeOut_" ++
						interpBusAtParam.rate ++
						(interpBusAtParam.numChannels - 1) //it has one more for env. need to remove that from symbol
					).asSymbol;

					AlgaSynth(
						fadeOutSymbol,
						[
							\out, interpBusAtParam.index,
							\fadeTime, if(tempoScaling, { time / this.clock.tempo }, { time }),
							\envShape, AlgaDynamicEnvelopes.getOrAdd(shape, server)
						],
						interpGroup,
						waitForInst:false
					);

					//Free the normSynth only if not replaceMix. In that case, it must be kept
					if(replaceMix.not, {
						normSynthAtParam.set(
							\gate, 0,
							\fadeTime, if(tempoScaling, { time / this.clock.tempo }, { time })
						);
					});
				});

				//This has to be surely algaInstantiated before being freed
				interpSynthAtParam.set(
					\t_release, 1,
					\fadeTime, if(tempoScaling, { time / this.clock.tempo }, { time }),
					\envShape, AlgaDynamicEnvelopes.getOrAdd(shape, server)
				);

				//Set correct fadeTime for all active interp synths at param / sender combination
				this.setFadeTimeForAllActiveInterpSynths(param, sender, time, shape);
			};

			//Make sure all of these are scheduled correctly to each other!
			if((interpBusAtParam != nil).and(normSynthAtParam != nil), {
				if(interpSynthAtParam.algaInstantiated, {
					fadeOutFunc.value
				}, {
					this.addAction(
						condition: {
							interpSynthAtParam.algaInstantiated
						},
						func: fadeOutFunc
					)
				});
			}, {
				("AlgaNode: invalid interpBus or normSynth at param").error;
				^this
			});
		});

		//On a .disconnect / .replaceMix, remove the entries
		if(cleanupDicts, {
			interpSynths[param].removeAt(sender);
			activeInterpSynths[param].removeAt(sender);
			//Only free interpBus and normSynth if not replaceMix: in that case it must be kept
			if(replaceMix.not, {
				interpBusses[param].removeAt(sender);
				normSynths[param].removeAt(sender);
			});
		});
	}

	//Free interpSynth at param. This is also used in .replace for both mix entries and normal ones
	//THIS USES connectionTime!!
	freeInterpSynthAtParam { | sender, param = \in, mix = false, replaceMix = false,
		replace = false, time, shape |
		var interpSynthsAtParam = interpSynths[param];
		var paramConnectionTime = this.getParamConnectionTime(param);

		//Free them all (check if there were mix entries).
		//sender == nil comes from <|
		//mix == false comes from <<
		//mix == true comes from <<+, .replaceMix, .replace, .disconnect
		if((sender == nil).or(mix == false), {
			//If interpSynthsAtParam length is more than one, the param has mix entries. Fade them all out.
			if(interpSynthsAtParam.size > 1, {
				interpSynthsAtParam.keysValuesDo({ | interpSender, interpSynthAtParam  |
					if(interpSender != \default, { // ignore \default !
						this.freeMixNodeAtParam(
							sender: interpSender,
							param: param,
							paramConnectionTime: paramConnectionTime,
							replace:replace, cleanupDicts:true, time:time, shape:shape
						);
					});
				});
			}, {
				//Calculate temporary time
				time = this.calculateTemporaryLongestWaitTime(time, paramConnectionTime);

				//Get shape
				shape = shape.algaCheckValidEnv(server: server) ? this.getInterpShape(param);

				//Start the free on the previous individual interp synth (size is ALWAYS 1 here)
				interpSynthsAtParam.do({ | interpSynthAtParam |
					interpSynthAtParam.set(
						\t_release, 1,
						\fadeTime, if(tempoScaling, { time / this.clock.tempo }, { time }),
						\envShape, AlgaDynamicEnvelopes.getOrAdd(shape, server)
					);
				});

				//Set correct fadeTime for all active interp synths at param / sender combination
				this.setFadeTimeForAllActiveInterpSynths(param, \default, time, shape:shape);
			});
		}, {
			//mix == true
			this.freeMixNodeAtParam(
				sender: sender,
				param: param,
				paramConnectionTime:paramConnectionTime,
				replace:replace, cleanupDicts:false, replaceMix: replaceMix,
				time:time, shape:shape
			);
		});
	}

	//param -> OrderedIdentitySet[AlgaNode, AlgaNode, ...]
	addInNode { | sender, param = \in, mix = false |
		//First of all, remove the outNodes that the previous sender had with the
		//param of this node, if there was any. Only apply if mix==false (no <<+ / >>+)
		if(mix == false, {
			var oldSenderSet = inNodes[param];
			if(oldSenderSet != nil, {
				oldSenderSet.do({ | oldSender |
					oldSender.outNodes.removeAt(this);
				});
			});
		});

		if((sender.isAlgaNode).or(sender.isAlgaArg), {
			//Empty entry OR not doing mixing, create new OrderedIdentitySet.
			//Otherwise, add to existing
			if((inNodes[param] == nil).or(mix.not), {
				inNodes[param] = OrderedIdentitySet[sender];
			}, {
				inNodes[param].add(sender);
			})
		}, {
			//Number / AlgaTemp. Always replace as mixing is not supported for numbers
			replaceArgs[param] = sender;

			//This is not an explicit arg anymore
			explicitArgs[param] = false;
		});
	}

	//AlgaNode -> OrderedIdentitySet[param, param, ...]
	addOutNode { | receiver, param = \in |
		//Empty entry, create OrderedIdentitySet. Otherwise, add to existing
		if(outNodes[receiver] == nil, {
			outNodes[receiver] = OrderedIdentitySet[param];
		}, {
			outNodes[receiver].add(param);
		});
	}

	//Add active in nodes connections
	addActiveInOutNodes { | sender, param = \in |
		//AlgaPattern handles it in its own addInNode, this would double it!
		if(this.isAlgaPattern.not, {
			//Don't add to active if sender == this
			var connectionToItself = (this == sender);
			if(connectionToItself.not, {
				this.addActiveInNode(sender, param);
				sender.addActiveOutNode(this, param);
			});
		});
	}

	//add entries to the inNodes / outNodes / connectionTimeOutNodes of the two AlgaNodes
	addInOutNodesDict { | sender, param = \in, mix = false |
		//This will replace the entries on new connection (when mix == false)
		this.addInNode(sender, param, mix);

		//Unpack AlgaArg
		if(sender.isAlgaArg, { sender = sender.sender });

		//This will add the entries to the existing OrderedIdentitySet, or create a new one
		if(sender.isAlgaNode, {
			sender.addOutNode(this, param);

			//Add to connectionTimeOutNodes and recalculate longestConnectionTime
			sender.connectionTimeOutNodes[this] = this.connectionTime;
			sender.calculateLongestConnectionTime(this.connectionTime);

			//Like inNodes / outNodes. They get freed on the accoding interpSynth
			this.addActiveInOutNodes(sender, param);
		});
	}

	removeInOutNodeAtParam { | sender, param = \in |
		var inNodesAtParam = inNodes[param];
		var senderOutNodesAtThis;

		if((sender.isAlgaNode).or(sender.isAlgaArg), {
			senderOutNodesAtThis = sender.outNodes[this]
		});

		if(senderOutNodesAtThis != nil, {
			//Just remove one param from sender's set at this entry
			senderOutNodesAtThis.remove(param);

			//If OrderedIdentitySet is now empty, remove it entirely
			if(senderOutNodesAtThis.size == 0, {
				sender.outNodes.removeAt(this);
			});
		});

		if(inNodesAtParam != nil, {
			//Remove the specific param / sender combination from inNodes
			inNodesAtParam.remove(sender);

			//If OrderedIdentitySet is now empty, remove it entirely
			if(inNodesAtParam.size == 0, {
				inNodes.removeAt(param);
			});
		});

		//Recalculate longestConnectionTime too...
		//This should also take in account eventual multiple sender / param combinations
		if(sender.isAlgaNode, {
			sender.connectionTimeOutNodes[this] = 0;
			sender.calculateLongestConnectionTime(0);
		});
	}

	//Remove entries from inNodes / outNodes / connectionTimeOutNodes for all involved nodes
	removeInOutNodesDict { | oldSender = nil, param = \in |
		var oldSenders = inNodes[param];

		if(oldSenders == nil, { ^this });

		oldSenders.do({ | sender |
			var sendersParamsSet = sender.outNodes[this];
			if(sendersParamsSet != nil, {
				case
				//If nil or same sender
				{ (oldSender == nil).or(sender == oldSender) } {
					this.removeInOutNodeAtParam(sender, param);
				}
				//If AlgaArg, look if it's the same sender
				{ sender.isAlgaArg } {
					if(sender.sender == oldSender, {
						this.removeInOutNodeAtParam(sender, param);
					})
				};
			});
		});

		//Remove replaceArgs
		replaceArgs.removeAt(param);
	}

	//Like addInNode
	addActiveInNode { | sender, param = \in |
		if(activeInNodes[param] == nil, {
			activeInNodes[param] = OrderedIdentitySet[sender];
		}, {
			activeInNodes[param].add(sender);
		});

		if(activeInNodesCounter[sender] == nil, {
			activeInNodesCounter[sender] = 1;
		}, {
			activeInNodesCounter[sender] = activeInNodesCounter[sender] + 1;
		});
	}

	//Like inNodes'
	removeActiveInNode { | sender, param = \in |
		if(activeInNodesCounter[sender] != nil, {
			var activeInNodesAtParam = activeInNodes[param];
			activeInNodesCounter[sender] = activeInNodesCounter[sender] - 1;
			if((activeInNodesAtParam != nil).and(activeInNodesCounter[sender] < 1), {
				activeInNodesAtParam.remove(sender);
				if(activeInNodesAtParam.size == 0, {
					activeInNodes.removeAt(param);
				});
			});
		});
	}

	//Like addOutNode
	addActiveOutNode { | receiver, param = \in |
		if(activeOutNodes[receiver] == nil, {
			activeOutNodes[receiver] = OrderedIdentitySet[param];
		}, {
			activeOutNodes[receiver].add(param);
		});

		if(activeOutNodesCounter[receiver] == nil, {
			activeOutNodesCounter[receiver] = 1
		}, {
			activeOutNodesCounter[receiver] = activeOutNodesCounter[receiver] + 1
		});
	}

	//Like outNodes'
	removeActiveOutNode { | receiver, param = \in |
		if(activeOutNodesCounter[receiver] != nil, {
			var activeOutNodesAtReceiver = activeOutNodes[receiver];
			activeOutNodesCounter[receiver] = activeOutNodesCounter[receiver] - 1;
			if((activeOutNodesAtReceiver != nil).and(activeOutNodesCounter[receiver] < 1), {
				activeOutNodesAtReceiver.remove(param);
				if(activeOutNodesAtReceiver.size == 0, {
					activeOutNodes.removeAt(receiver)
				});
			});
		});
	}

	//Clear the dicts
	resetInOutNodesDicts {
		if(algaToBeCleared, {
			inNodes.clear;
			outNodes.clear;
			activeInNodes.clear;
			activeOutNodes.clear;
			activeInNodesCounter.clear;
			activeOutNodesCounter.clear;
		});
	}

	//Implementation of checkConnectionAlreadyInPlace
	checkConnectionAlreadyInPlaceInner { | sendersSet, sender |
		case
		{ sender.isAlgaTemp } { sender = sender.def } //Useless?
		{ sender.isAlgaArg }  { sender = sender.sender };

		//AlgaNode
		if(sender.isAlgaNode, {
			if(blockIndex != sender.blockIndex, { ^false }); //Different block, always false
			if(sendersSet.includes(sender), { ^true }); //Connection is in place
		}, {
			//Pattern: keep looking
			parser.parseGenericObject(sender,
				func: { | val |
					if(this.checkConnectionAlreadyInPlaceInner(sendersSet, val), { ^true });
				},
				replace: false
			)
		});

		//Fallback
		^false;
	}

	//Check if connection was already in place at any param
	checkConnectionAlreadyInPlace { | sender |
		inNodes.do({ | sendersSet |
			if(this.checkConnectionAlreadyInPlaceInner(sendersSet, sender), { ^true });
		});
		^false;
	}

	//New interp connection at specific parameter
	newInterpConnectionAtParam { | sender, param = \in, replace = false,
		senderChansMapping, scale, time, shape |

		//Check sender == this
		var connectionToItself = (this == sender);

		//Check valid param
		var controlName = controlNames[param];
		if(controlName == nil, {
			("AlgaNode: invalid param to create a new interp synth for: '" ++ param ++ "'").error;
			^this;
		});

		//Check if connection was already there (must come before removeInOutNodesDict)
		connectionAlreadyInPlace = this.checkConnectionAlreadyInPlace(sender);

		//Remove ALL previous inNodes / outNodes at param
		this.removeInOutNodesDict(nil, param);

		//Add proper inNodes / outNodes / connectionTimeOutNodes
		this.addInOutNodesDict(sender, param, mix:false);

		//Re-order groups
		//Actually reorder the block's nodes ONLY if not running .replace
		//(no need there, they are already ordered, and it also avoids a lot of problems
		//with feedback connections)
		if((replace.not).and(connectionAlreadyInPlace.not).and(connectionToItself.not), {
			AlgaBlocksDict.createNewBlockIfNeeded(this, sender);
		});

		//Free previous interp synth(s) (fades out)
		this.freeInterpSynthAtParam(
			sender, param,
			time:time, shape:shape
		);

		//If replacing an AlgaPattern + stopPatternBeforeReplace, time is 0 for the new synth
		if(replace.and(sender.isAlgaPattern), {
			if(sender.stopPatternBeforeReplace, { time = 0 })
		});

		//Spawn new interp synth (fades in)
		this.createInterpSynthAtParam(
			sender, param,
			senderChansMapping:senderChansMapping, scale:scale,
			time:time, shape:shape
		);

		//Reset connectionAlreadyInPlace
		connectionAlreadyInPlace = false;
	}

	//New mix connection at specific parameter
	newMixConnectionAtParam { | sender, param = \in, replace = false,
		replaceMix = false, senderChansMapping, scale, time, shape |

		//Don't reorder block if connecting to itself
		var connectionToItself = (this == sender);

		//Check valid param
		var controlName = controlNames[param];
		if(controlName == nil, {
			("AlgaNode: invalid param to create a new interp synth for: '" ++ param ++ "'").error;
			^this;
		});

		//Check if connection was already there
		connectionAlreadyInPlace = this.checkConnectionAlreadyInPlace(sender);

		/*
		Note: there's no removeInOutNodesDict here. Since it's a mix connection,
		no previous connections should be removed!!!!
		*/

		//Add proper inNodes / outNodes / connectionTimeOutNodes
		this.addInOutNodesDict(sender, param, mix:true);

		//Re-order groups
		//Actually reorder the block's nodes ONLY if not running .replace
		//(no need there, they are already ordered, and it also avoids a lot of problems
		//with feedback connections)
		if((replace.not).and(connectionAlreadyInPlace.not).and(connectionToItself.not).and(replaceMix.not), {
			AlgaBlocksDict.createNewBlockIfNeeded(this, sender);
		});

		//Needed for .replace. .replaceMix is already handled by .disconnectInner
		if(replace, {
			this.freeInterpSynthAtParam(
				sender, param, mix:true,
				replace:true, time:time, shape:shape
			);
		});

		//If replacing an AlgaPattern + stopPatternBeforeReplace, time is 0 for the new synth
		if(replace.and(sender.isAlgaPattern), {
			if(sender.stopPatternBeforeReplace, { time = 0 })
		});

		//Spawn new interp mix node
		this.createMixInterpSynthAndInterpBusAndNormSynthAtParam(
			sender: sender, param: param,
			replaceMix:replaceMix, replace:replace,
			senderChansMapping:senderChansMapping,
			scale:scale, time:time, shape:shape
		);

		//Reset connectionAlreadyInPlace
		connectionAlreadyInPlace = false;
	}

	//Used in <| and replaceMix
	removeInterpConnectionAtParam { | oldSender = nil, param = \in, time, shape |
		var controlName = controlNames[param];
		if(controlName == nil, {
			("AlgaNode: invalid param to reset: '" ++ param ++ "'").error;
			^this;
		});

		//Remove inNodes / outNodes / connectionTimeOutNodes
		this.removeInOutNodesDict(oldSender, param);

		//Free previous interp synth (fades out)
		this.freeInterpSynthAtParam(oldSender, param, time:time, shape:shape);

		//Create new interp synth with default value (or the one supplied with args at start) (fades in)
		this.createInterpSynthAtParam(nil, param, time:time, shape:shape);
	}

	//Cleans up all Dicts at param, leaving the \default entry only
	cleanupMixBussesAndSynths { | param |
		var interpBusAtParam = interpBusses[param];
		if(interpBusAtParam.size > 1, {
			var interpSynthAtParam = interpSynths[param];
			var normSynthAtParam = normSynths[param];
			interpBusAtParam.keysValuesDo({ | key, value |
				if(key != \default, {
					interpBusAtParam.removeAt(key);
					interpSynthAtParam.removeAt(key);
					normSynthAtParam.removeAt(key);
				});
			});
		});
	}

	//First time a mix parameter is added: make sure to add what was the \default to
	//all the mix dictionaries. This is actually only needed once, on the first time of <<+
	//so that the previous connection is considered part of the mix
	moveDefaultNodeToMix { | param = \in, sender |
		var currentDefaultNodeAtParam = currentDefaultNodes[param];
		if(currentDefaultNodeAtParam.isAlgaNode, { //only if default is AlgaNode (not number or array)
			if(currentDefaultNodeAtParam != sender, {
				var interpBussesAtParam = interpBusses[param];
				var interpSynthsAtParam = interpSynths[param];
				var normSynthsAtParam = normSynths[param];

				//If there was no entry, add it!
				if(interpBussesAtParam[currentDefaultNodeAtParam] == nil, {
					interpBussesAtParam[currentDefaultNodeAtParam] = interpBussesAtParam[\default];
				});

				if(interpSynthsAtParam[currentDefaultNodeAtParam] == nil, {
					interpSynthsAtParam[currentDefaultNodeAtParam] = interpSynthsAtParam[\default];
				});

				if(normSynthsAtParam[currentDefaultNodeAtParam] == nil, {
					normSynthsAtParam[currentDefaultNodeAtParam] = normSynthsAtParam[\default];
				});
			});
		});
	}

	checkParamExists { | param = \in |
		if(controlNames[param] == nil, { ^false });
		^true;
	}

	//implements receiver <<.param sender
	makeConnectionInner { | sender, param = \in, replace = false, mix = false,
		replaceMix = false, senderChansMapping, scale, time, shape |

		if((sender.isAlgaNode.not).and(sender.isNumberOrArray.not).and(
			sender.isAlgaTemp.not).and(sender.isAlgaArg.not), {
			"AlgaNode: can't connect to something that's not an AlgaNode, a Symbol, an AlgaTemp, an AlgaArg, a Function, a Number or an Array".error;
			^this
		});

		//Check parameter in controlNames
		if(this.checkParamExists(param).not, {
			("AlgaNode: '" ++ param ++ "' is not a valid parameter, it is not defined in the 'def'.").error;
			^this
		});

		if(mix, {
			var currentDefaultNodeAtParam = currentDefaultNodes[param];

			//trying to <<+ instead of << on first connection, OR inNodes' size is 0
			if((currentDefaultNodeAtParam == nil).or(inNodes.size == 0), {
				("AlgaNode: first connection. Running 'from' instead.").warn;
				mix = false;
			});

			//Can't add to a num. just replace it.. It would be impossible to keep track of all
			//the numbers. Instead, one should use nodes with DC.kr/ar
			if(currentDefaultNodeAtParam.isNumberOrArray, {
				("AlgaNode: trying to mix values to a non-AlgaNode: " ++ currentDefaultNodeAtParam.asString ++ ". Replacing it.").warn;
				mix = false;
			});

			//can't <<+
			if((sender == nil), {
				("AlgaNode: mixing only works for explicit AlgaNodes.").error;
				^this;
			});

			//Trying to run replaceMix / mixFrom / mixTo when sender is the only entry!
			if(inNodes[param].size == 1, {
				if(inNodes[param].findMatch(sender) != nil, {
					"AlgaNode: sender was the only entry. Running 'makeConnection' instead".warn;
					mix = false;
				});
			});
		});

		//need to re-check as mix might have changed!
		if(mix, {
			//Make sure the \default node is also added to mix
			this.moveDefaultNodeToMix(param, sender);

			//Either new one <<+ / .replaceMix OR .replace
			this.newMixConnectionAtParam(
				sender: sender, param: param,
				replace:replace, replaceMix:replaceMix,
				senderChansMapping:senderChansMapping,
				scale:scale, time:time, shape:shape
			)
		}, {
			//mix == false, clean everything up

			//Connect interpSynth to the sender's synthBus
			this.newInterpConnectionAtParam(
				sender: sender, param: param,
				replace:replace, senderChansMapping:senderChansMapping,
				scale:scale, time:time, shape:shape
			);

			//Cleanup interpBusses / interpSynths / normSynths from previous mix, leaving \default only
			this.cleanupMixBussesAndSynths(param);
		});
	}

	//Store latest sender. Only works with no mix
	addLatestSenderAtParam { | sender, param = \in, mix = false |
		if(mix.not, {
			latestSenders = latestSenders ? IdentityDictionary(10);
			latestSenders[param] = sender;
		})
	}

	//Get latest sender. Only works with no no mix
	getLatestSenderAtParam { | sender, param = \in, mix = false |
		if(mix.not, {
			var latestSenderAtParam = latestSenders[param];
			if(latestSenderAtParam != nil, { ^latestSenderAtParam });
		})
		^sender; //Just return sender if mix or not found!
	}

	//<<.param sender
	makeConnection { | sender, param = \in, replace = false, mix = false,
		replaceMix = false, senderChansMapping, scale, time, shape,
		forceReplace = false, sched = 0 |

		var shapeNeedsSending = false;
		var makeConnectionFunc;

		//Check sched
		if(replace.not, { sched = sched ? schedInner });

		//Force a replace call
		if(forceReplace, {
			if(mix, { "AlgaNode: 'forceReplace' does not work with mixing".error; ^this });
			^this.replace(synthDef.name, [param, sender], time: time, sched: sched)
		});

		//Store latest sender. This is used to only execute the latest .from call.
		//This allows for a smoother live coding experience: instead of triggering
		//every .from that was executed (perhaps the user found a mistake), only the latest one
		//will be considered when sched comes. Mix is not affected by this mechanism
		this.addLatestSenderAtParam(sender, param, mix);

		//Actual makeConnection function
		makeConnectionFunc = { | shape |
			if(this.algaCleared.not.and(sender.algaCleared.not).and(sender.algaToBeCleared.not), {
				this.addAction(
					condition: {
						(this.algaInstantiatedAsReceiver(param, sender, mix)).and(
							sender.algaInstantiatedAsSender)
					},
					func: {
						//Check against latest sender!
						if(sender == this.getLatestSenderAtParam(sender, param, mix), {
							this.makeConnectionInner(sender, param, replace, mix,
								replaceMix, senderChansMapping, scale, time:time, shape:shape
							)
						});
					},
					sched: sched
				);
			}, {
				"AlgaNode: can't run 'makeConnection', sender has been cleared".error
			});
		};

		//Check if the new shape needs to be sent to Server
		if(shape != nil, {
			shapeNeedsSending = (AlgaDynamicEnvelopes.get(shape, server) == nil).and(
				AlgaDynamicEnvelopes.isNextBufferPreAllocated.not
			);
		});

		//If shape needs sending, wrap in Routine so that .sendCollection's sync is picked up
		if(shapeNeedsSending, {
			forkIfNeeded {
				shape = shape.algaCheckValidEnv(server: server);
				makeConnectionFunc.value(shape);
			}
		}, {
			makeConnectionFunc.value(shape);
		});
	}

	//<<.param { }
	makeConnectionFunction { | sender, param = \in, replace = false,
		senderChansMapping, scale, time, shape, forceReplace = false, sched = 0 |

		var defName = ("alga_" ++ UniqueID.next).asSymbol;
		var algaTemp = AlgaTemp(defName);
		var functionSynthDefDict = IdentityDictionary()[defName] = [
			AlgaSynthDef(
				defName,
				sender
			),
			algaTemp
		];

		^this.compileFunctionSynthDefDictIfNeeded(
			{
				if(algaTemp.valid, {
					this.makeConnectionAlgaTemp(
						sender: algaTemp,
						param: param,
						replace: replace,
						senderChansMapping: senderChansMapping,
						scale: scale,
						time: time,
						shape: shape,
						forceReplace: forceReplace,
						sched: sched
					)
				}, {
					("AlgaNode: Invalid AlgaSynthDef: '" ++ sender.asString ++ "'").error;
				});
			},
			functionSynthDefDict
		);
	}

	//<<.param \someDef
	makeConnectionSymbol { | sender, param = \in, replace = false,
		senderChansMapping, scale, time, shape, forceReplace = false, sched = 0 |

		var algaTemp = AlgaTemp(sender);
		algaTemp.checkValidSynthDef(sender);

		if(algaTemp.valid.not, {
			("AlgaNode: Invalid AlgaSynthDef: '" ++ sender.asString ++ "'").error;
			^this
		});

		this.makeConnectionAlgaTemp(
			sender: algaTemp,
			param: param,
			replace: replace,
			senderChansMapping: senderChansMapping,
			scale: scale,
			time: time,
			shape: shape,
			forceReplace: forceReplace,
			sched: sched
		)
	}

	//Parse an AlgaTemp
	parseAlgaTempParam { | algaTemp, functionSynthDefDict, topAlgaTemp |
		^(parser.parseAlgaTempParam(algaTemp,functionSynthDefDict, topAlgaTemp))
	}

	//Parse an entry
	parseParam { | value, functionSynthDefDict |
		^(parser.parseParam(value, functionSynthDefDict))
	}

	//<<.param AlgaTemp
	makeConnectionAlgaTemp { | sender, param = \in, replace = false,
		senderChansMapping, scale, time, shape, forceReplace = false, sched = 0 |

		var functionSynthDefDict = IdentityDictionary();
		var algaTemp = this.parseAlgaTempParam(sender, functionSynthDefDict);
		if(algaTemp == nil, { ^this });

		//Check sched
		if(replace.not, { sched = sched ? schedInner });

		^this.compileFunctionSynthDefDictIfNeeded(
			{
				if(algaTemp.valid, {
					this.makeConnection(
						sender: algaTemp,
						param: param,
						replace: replace,
						senderChansMapping: senderChansMapping,
						scale: scale,
						time: time,
						shape:shape,
						forceReplace: forceReplace,
						sched: sched
					)
				}, {
					("AlgaNode: Invalid AlgaSynthDef: '" ++ sender.asString ++ "'").error;
				});
			},
			functionSynthDefDict
		);
	}

	//Receive a connection
	from { | sender, param = \in, chans, scale, time, shape, forceReplace = false, sched |
		case
		{ sender.isAlgaNode } {
			if(this.server != sender.server, {
				("AlgaNode: trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
			^this.makeConnection(sender, param, senderChansMapping:chans,
				scale:scale, time:time, shape:shape, forceReplace: forceReplace, sched:sched
			);
		}
		{ sender.isAlgaArg } {
			("AlgaNode: AlgaArgs must only be used as 'args'. Use a normal 'from' connection with an AlgaNode instead").error;
			^this;
		}
		{ sender.isNumberOrArray } {
			^this.makeConnection(sender, param, senderChansMapping:chans,
				scale:scale, time:time, shape:shape, forceReplace: forceReplace, sched:sched
			);
		}
		{ sender.isFunction } {
			^this.makeConnectionFunction(sender, param, senderChansMapping:chans,
				scale:scale, time:time, shape:shape, forceReplace: forceReplace, sched:sched
			);
		}
		{ sender.isSymbol } {
			^this.makeConnectionSymbol(sender, param, senderChansMapping:chans,
				scale:scale, time:time, shape:shape, forceReplace: forceReplace, sched:sched
			);
		}
		{ sender.isAlgaTemp } {
			^this.makeConnectionAlgaTemp(sender, param, senderChansMapping:chans,
				scale:scale, time:time, shape:shape, forceReplace: forceReplace, sched:sched
			);
		}
		{ sender.isBuffer } {
			var senderBufNum = sender.bufnum;
			var args = [ param, senderBufNum ];
			"AlgaNode: changing a Buffer. This will trigger 'replace'.".warn;
			^this.replace(synthDef.name, args, time, sched);
		};

		("AlgaNode: trying to enstablish a connection from an invalid class: " ++ sender.class).error;
	}

	//arg is the sender. it can also be a number / array to set individual values
	<< { | sender, param = \in |
		this.from(sender: sender, param: param);
	}

	//Send a connection
	to { | receiver, param = \in, chans, scale, time, shape, forceReplace = false, sched |
		if(receiver.isAlgaNode, {
			if(this.server != receiver.server, {
				("AlgaNode: trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
			receiver.makeConnection(this, param, senderChansMapping:chans,
				scale:scale, time:time, shape:shape, forceReplace: forceReplace, sched:sched
			);
		}, {
			("AlgaNode: trying to enstablish a connection to an invalid class: " ++ receiver.class).error;
		});
	}

	//arg is the receiver
	>> { | receiver, param = \in |
		this.to(receiver: receiver, param: param);
	}

	//Mix a connection
	mixFrom { | sender, param = \in, chans, scale, time, shape, sched |
		case
		{ sender.isAlgaNode } {
			if(this.server != sender.server, {
				("AlgaNode: trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
			^this.makeConnection(sender, param, mix:true, senderChansMapping:chans,
				scale:scale, time:time, shape:shape, sched:sched
			);
		}
		{ sender.isAlgaArg } {
			("AlgaNode: AlgaArgs must only be used as 'args'. Use a normal 'mixFrom' connection with an AlgaNode instead").error;
			^this;
		}
		{ sender.isNumberOrArray } {
			"AlgaNode: Numbers and Arrays cannot be mixed to AlgaNodes' parameters. Use 'from' instead.".warn;
			^this;
		}
		{ sender.isFunction } {
			"AlgaNode: Functions cannot be mixed to AlgaNodes' parameters. Use 'from' instead.".warn;
			^this;
		}
		{ sender.isSymbol } {
			"AlgaNode: AlgaSynthDefs cannot be mixed to AlgaNodes' parameters. Use 'from' instead.".warn;
			^this;
		}
		{ sender.isAlgaTemp } {
			"AlgaNode: AlgaTemps cannot be mixed to AlgaNodes' parameters. Use 'from' instead.".warn;
			^this;
		}
		{ sender.isBuffer } {
			"AlgaNode: Buffers cannot be mixed to AlgaNodes' parameters. Use 'from' instead.".warn;
			^this;
		};

		("AlgaNode: trying to enstablish a connection from an invalid class: " ++ sender.class).error;
	}

	//add to already running nodes (mix)
	<<+ { | sender, param = \in |
		this.mixFrom(sender: sender, param: param);
	}

	//Send a mix connection
	mixTo { | receiver, param = \in, chans, scale, time, shape, sched |
		if(receiver.isAlgaNode, {
			if(this.server != receiver.server, {
				("AlgaNode: trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
			receiver.makeConnection(this, param, mix:true, senderChansMapping:chans,
				scale:scale, time:time, shape:shape, sched:sched
			);
		}, {
			("AlgaNode: trying to enstablish a connection to an invalid class: " ++ receiver.class).error;
		});
	}

	//add to already running nodes (mix)
	>>+ { | receiver, param = \in |
		this.mixTo(receiver: receiver, param: param);
	}

	//Replace old interpBus and normSynth with the new on
	//Only do it if oldSender != newSender (or it will empty the entry)
	replaceInterpBusAndNormSynth { | param, oldSender, newSender |
		if(oldSender != newSender, {
			var oldSenderInterpBus = interpBusses[param][oldSender];
			var oldSenderNormSynth = normSynths[param][oldSender];
			if(oldSenderInterpBus != nil, {
				interpBusses[param][newSender] = oldSenderInterpBus;
				interpBusses[param].removeAt(oldSender);
			});
			if(oldSenderNormSynth != nil, {
				normSynths[param][newSender] = oldSenderNormSynth;
				normSynths[param].removeAt(oldSender);
			});
		});
	}

	//disconnect + makeConnection, very easy
	replaceMixInner { | param = \in, oldSender, newSender, chans, scale, time, shape |
		//Disconnect previous entries (replaceMix == true)
		this.disconnectInner(
			param: param,
			oldSender: oldSender,
			replaceMix: true,
			time: time,
			shape: shape
		);

		//Replace old interpBus and normSynth with the new one
		this.replaceInterpBusAndNormSynth(
			param: param,
			oldSender: oldSender,
			newSender: newSender
		);

		//Make a new connection (replaceMix == true)
		this.makeConnectionInner(
			sender:newSender,
			param:param,
			replace:false, mix:true, replaceMix:true,
			senderChansMapping:chans, scale:scale,
			time:time, shape:shape
		);
	}

	//Replace a mix entry at param... Practically just freeing the old one and triggering the new one.
	//This will be useful in the future if wanting to implement some kind of system to retrieve individual
	//mix entries (like, \in1, \in2). No need it for now
	replaceMix { | param = \in, oldSender, newSender, chans, scale, time, shape, sched |
		if(newSender.isAlgaNode.not, {
			("AlgaNode: " ++ (newSender.class.asString) ++ " is not an AlgaNode").error;
			^this;
		});

		//Check sched
		sched = sched ? schedInner;

		this.addAction(
			condition: {
				(this.algaInstantiatedAsReceiver(param, oldSender, true)).and(
					oldSender.algaInstantiatedAsSender).and(
					newSender.algaInstantiatedAsSender)
			},
			func: {
				var validOldSender = true;

				//if not contained, it's invalid.
				if(this.mixParamContainsSender(param, oldSender).not, {
					("AlgaNode: " ++ oldSender.asString ++ " was not present in the mix for param '" ++ "'" ++ param.asString).error;
					validOldSender = false;
				});

				if(validOldSender, {
					this.replaceMixInner(param, oldSender, newSender, chans, scale, time, shape);
				});
			},
			sched: sched
		);
	}

	//Alias for replaceMix (which should be deprecated, collides name with .replace)
	mixSwap { | param = \in, oldSender, newSender, chans, scale, time, shape, sched |
		^this.replaceMix(param, oldSender, newSender, chans, scale, time, shape, sched);
	}

	resetParamInner { | param = \in, oldSender = nil, time, shape |
		//Also remove inNodes / outNodes / connectionTimeOutNodes
		if(oldSender != nil, {
			if(oldSender.isAlgaNode, {
				this.removeInterpConnectionAtParam(oldSender, param, time:time, shape:shape);
			}, {
				("AlgaNode: trying to remove a connection to an invalid AlgaNode: " ++ oldSender.asString).error;
			})
		}, {
			this.removeInterpConnectionAtParam(nil, param, time:time, shape:shape);
		})
	}

	resetParam { | param = \in, oldSender = nil, time, shape, sched |
		//Check sched
		sched = sched ? schedInner;

		//Exec on instantiated receiver / sender
		this.addAction(
			condition: {
				(this.algaInstantiatedAsReceiver(param, oldSender, false)).and(oldSender.algaInstantiatedAsSender)
			},
			func: {
				this.resetParamInner(param, oldSender, time:time, shape:shape)
			},
			sched: sched
		);
	}

	//Same as resetParam, which could be deprecated (bad naming)
	reset { | param = \in, time, shape, sched |
		^this.resetParam(param, nil, time, shape, sched);
	}

	//resets to the default value in controlNames
	//OR, if provided, to the value of the original args that were used to create the node
	//oldSender is used in case of mixing, to only remove that one
	<| { | param = \in |
		this.resetParam(param, nil);
	}

	//On .replace on an already running mix connection
	replaceMixConnectionInner { | param = \in, sender, senderChansMapping, scale, time, shape |
		this.makeConnectionInner(sender, param,
			replace:true, mix:true, replaceMix:false,
			senderChansMapping:senderChansMapping,
			scale:scale, time:time, shape:shape
		);
	}

	//On .replace on an already running mix connection
	replaceMixConnection { | param = \in, sender, senderChansMapping, scale, time, shape |
		this.addAction(
			condition: {
				(this.algaInstantiatedAsReceiver(param, sender, true)).and(sender.algaInstantiatedAsSender)
			},
			func: {
				this.replaceMixConnectionInner(param, sender, senderChansMapping, scale, time, shape)
			}
		);
	}

	//replace connections FROM this
	replaceConnections { | keepChannelsMapping = true, keepScale = true, time |
		//outNodes. Remake connections that were in place with receivers.
		//This will effectively trigger interpolation process.
		outNodes.keysValuesDo({ | receiver, paramsSet |
			paramsSet.do({ | param |
				var oldParamsChansMapping = nil;
				var oldScale = nil;

				//Ignore FB: it's been done already in createInterpNormSynths
				if(this != receiver, {
					//Restore old channels mapping! It can either be a symbol, number or array here
					if(keepChannelsMapping, { oldParamsChansMapping = receiver.getParamChansMapping(param, this) });

					//Restore old scale mapping!
					if(keepScale, { oldScale = receiver.getParamScaling(param, this) });

					//If it was a mix connection, use replaceMixConnection
					if(receiver.mixParamContainsSender(param, this), {
						//use the scheduler version! don't know if receiver and this are both instantiated
						receiver.replaceMixConnection(param, this,
							senderChansMapping:oldParamsChansMapping,
							scale:oldScale, time:time
						);
					}, {
						//use the scheduler version! don't know if receiver and this are both instantiated
						//Normal connection, use makeConnection to re-enstablish it
						receiver.makeConnection(this, param,
							replace:true, senderChansMapping:oldParamsChansMapping,
							scale:oldScale, time:time
						);
					});
				});
			});
		});

		//Re-create previous out: connections with patterns
		this.createAllPatternOutConnections(time);
	}

	//Replace implementation
	replaceInner { | def, args, time, outsMapping, reset, keepOutsMappingIn = true,
		keepOutsMappingOut = true, keepScalesIn = true, keepScalesOut = true |

		var playTimeOnReplace = if(replacePlayTime, { time }, { nil });
		var wasPlaying = false;

		//Re-init groups if clear was used or toBeCleared
		var initGroups = if((group == nil).or(algaCleared).or(algaToBeCleared), { true }, { false });

		//Check reset
		reset = reset ? false;

		//Trying to .replace on a cleared AlgaNode
		if(algaCleared, {
			"AlgaNode: trying to 'replace' on a cleared AlgaNode. Running 'AlgaNode.new' instead.".warn;
			algaCleared = false;
			^this.init(
				def: def,
				args: args,
				connectionTime: connectionTime,
				playTime: playTime,
				outsMapping: outsMapping,
				server: server
			);
		});

		//In case it was being cleared, set flag. This is used in AlgaPattern
		if(algaToBeCleared, { algaWasBeingCleared = true });

		//In case it has been set to true when clearing, then replacing before clear ends!
		algaToBeCleared = false;

		//calc temporary time
		time = this.calculateTemporaryLongestWaitTime(time, time);

		//Wait for instantiation of all args before going forward
		this.executeOnArgsInstantiation(
			args: args,
			dispatchFunc: {
				//If it was playing, free previous playSynth
				if(isPlaying, {
					this.stopInner(
						time: playTimeOnReplace,
						replace: true
					);
					wasPlaying = true;
				});

				//Free all previous out: connections from patterns
				this.freeAllPatternOutConnections(time);

				//This doesn't work with feedbacks, as synths would be freed slightly before
				//The new ones finish the rise, generating click. These should be freed
				//When the new synths/busses are surely algaInstantiated on the server!
				//The cheap solution that it's in place now is to wait 1.0 longer than longestConnectionTime.
				//Work out a better solution now that AlgaScheduler is well tested!
				this.freeAllSynths(false, false, time);

				//Free all previous busses
				this.freeAllBusses;

				//Reset interp / norm dictionaries
				this.resetInterpNormDicts;

				//New node
				this.dispatchNode(
					def:def,
					args:args,
					initGroups:initGroups,
					replace:true,
					reset:reset,
					keepChannelsMapping:keepOutsMappingIn, outsMapping:outsMapping,
					keepScale:keepScalesIn
				);

				//Re-enstablish connections that were already in place
				this.replaceConnections(
					keepChannelsMapping:keepOutsMappingOut,
					keepScale:keepScalesOut,
					time:time
				);

				//If node was playing, or .replace has been called while .stop / .clear, play again
				if(wasPlaying/*.or(beingStopped)*/, {
					//In this case, no fadeIn must be provided
					if(this.isAlgaPattern, {
						if(this.stopPatternBeforeReplace, {
							playTimeOnReplace = 0
						})
					});

					this.playInner(
						time: playTimeOnReplace,
						replace: true,
						usePrevPlayScale: true,
						usePrevPlayOut: true
					)
				});

				//Reset flag
				algaWasBeingCleared = false;
			}
		);

		^this;
	}

	//replace content of the node, re-making all the connections.
	//If this was connected to a number / array, should I restore that value too or keep the new one?
	replace { | def, args, time, sched, outsMapping, reset = false, keepOutsMappingIn = true,
		keepOutsMappingOut = true, keepScalesIn = true, keepScalesOut = true |

		//Check sched
		sched = sched ? schedInner;

		//This makes sure that only the latest executed code will go through,
		//allowing for a smoother live coding experience. This way, instead of
		//going through each iteration (perhaps there were mistakes), only
		//the latest executed code will be considered at the moment of .replace
		latestReplaceDef = def;

		//Check global algaInstantiated
		this.addAction(
			condition: { this.algaInstantiated },
			func: {
				if(def == latestReplaceDef, {
					this.replaceInner(
						def:def, args:args, time:time,
						outsMapping:outsMapping,
						reset:reset,
						keepOutsMappingIn:keepOutsMappingIn,
						keepOutsMappingOut:keepOutsMappingOut,
						keepScalesIn:keepScalesIn, keepScalesOut:keepScalesOut
					);
				});
			},
			sched: sched,
			topPriority: true //always top priority
		);

		//Not cleared
		algaCleared = false;
	}

	//Basically, this checks if the current sender that is being disconnected was the \default node.
	//if it is, it switch the default node with the next available
	checkForUpdateToDefaultNodeAtParam { | param = \in, oldSender |
		//If disconnecting the one that \default is assigned to, it must be switched to another one first!!
		if(currentDefaultNodes[param] == oldSender, {
			var newDefaultNode;

			//Find another one (the first one available)
			newDefaultNode = block ({ | break |
				inNodes[param].do({ | inNode |
					if(inNode != oldSender, {
						break.(inNode); //break returns the argument
					});
				});
			});

			//Update default entries
			if(newDefaultNode != nil, {
				currentDefaultNodes[param] = newDefaultNode;
				interpSynths[param][\default] = interpSynths[param][newDefaultNode];
				normSynths[param][\default] = normSynths[param][newDefaultNode];
				interpBusses[param][\default] = interpBusses[param][newDefaultNode];
			});
		});
	}

	//Remove individual mix entries at param (called from replaceMix too)
	disconnectInner { | param = \in, oldSender, replaceMix = false, time, shape |
		if(this.mixParamContainsSender(param, oldSender).not, {
			("AlgaNode: " ++ oldSender.asString ++ " was not present in the mix for param " ++ param.asString).error;
			^this;
		});

		//Remove inNodes / outNodes / connectionTimeOutNodes for oldSender
		this.removeInOutNodesDict(oldSender, param);

		//check if \default node needs updating
		this.checkForUpdateToDefaultNodeAtParam(param, oldSender);

		if(replaceMix.not, {
			var interpSynthsAtParam;

			//First: free the interpSynth previously there
			this.freeInterpSynthAtParam(oldSender, param, true, time:time);

			//retrieve the updated ones
			interpSynthsAtParam = interpSynths[param];

			//If length is now 2, it means it's just one mixer AND the \default node left in the dicts.
			//Assign the node to \default and remove the previous mixer.
			if(interpSynthsAtParam.size == 2, {
				interpSynthsAtParam.keysValuesDo({ | interpSender, interpSynthAtParam |
					if(interpSender != \default, {
						var normSynthsAtParam = normSynths[param];
						var interpBussesAtParam = interpBusses[param];

						//leave only \default
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
			this.freeInterpSynthAtParam(
				sender: oldSender,
				param: param,
				mix: true,
				replaceMix: true,
				time: time,
				shape:shape
			);
		});
	}

	//Remove individual mix entries at param
	disconnect { | param = \in, oldSender = nil, time, shape, sched |
		//If it wasn't a mix param, but the only entry, run <| instead
		if(inNodes[param].size == 1, {
			if(inNodes[param].findMatch(oldSender) != nil, {
				"AlgaNode: oldSender was the only entry. Running 'reset' instead".warn;
				^this.resetParam(
					param, oldSender,
					time:time, shape:shape, sched:sched
				);
			});
		});

		//If nil, reset parameter
		if(oldSender == nil, {
			^this.resetParam(
				param, oldSender,
				time:time, shape:shape, sched:sched
			);
		});

		//Else, mix param
		if(oldSender.isAlgaNode.not, {
			("AlgaNode: " ++ (oldSender.class.asString) ++ " is not an AlgaNode").error;
			^this;
		});

		//Check sched. resetParam will already check its own.
		//This needs to be here, not on top
		sched = sched ? schedInner;

		this.addAction(
			condition: {
				(this.algaInstantiatedAsReceiver(param, oldSender, true)).and(oldSender.algaInstantiatedAsSender)
			},
			func: { this.disconnectInner(param, oldSender, time:time, shape:shape) },
			sched: sched
		);
	}

	//alias for disconnect: remove a mix entry
	removeMix { | param = \in, oldSender, time, shape, sched |
		this.disconnect(param, oldSender, time, shape, sched);
	}

	//Find out if specific param / sender combination is in the mix
	mixParamContainsSender { | param = \in, sender |
		var interpSynthsAtParam = interpSynths[param];
		if(interpSynthsAtParam == nil, { ^false });
		if(interpSynthsAtParam[sender] != nil, { ^true });

		//Last resort: look at AlgaArgs' senders
		interpSynthsAtParam.keysValuesDo({ | key, value |
			if(key.isAlgaArg, {
				var oldSender = key.sender;
				if(oldSender == sender, { ^true });
			})
		});

		^false;
	}

	//When clear, run disconnections to nodes connected to this
	removeConnectionFromReceivers { | time |
		outNodes.keysValuesDo({ | receiver, paramsSet |
			paramsSet.do({ | param |
				//If mixer param, just disconnect the entry connected to this
				if(receiver.mixParamContainsSender(param, this), {
					receiver.disconnect(param, this, time:time);
				}, {
					//no mixer param, just run the disconnect to restore defaults
					receiver.resetParam(param, nil, time:time);
				});
			});
		});
	}

	clearInner { | onClear, time |
		//calc temporary time
		var stopTime = this.calculateTemporaryLongestWaitTime(time, playTime);
		time = max(stopTime, longestWaitTime); //makes sure to wait longest time to run clears

		//If synth had connections, run <| (or disconnect, if mixer) on the receivers
		this.removeConnectionFromReceivers(time);

		//This could be overwritten if .replace is called
		algaToBeCleared = true;

		//Stop playing (if it was playing at all)
		this.stopInner(stopTime, isClear:true, action:onClear);

		//Just remove groups, they contain the synths
		this.freeAllGroups(false, time);
		this.freeAllBusses(false, time, true);

		fork {
			//Wait time before clearing groups, synths and busses...
			(time + 1.0).wait;

			//Reset all instance variables
			if(algaToBeCleared, {
				this.resetSynth;
				this.resetInterpNormSynths;
				this.resetGroups;
				this.resetInOutNodesDicts;

				defArgs = nil;
				if(this.isAlgaPattern, {
					this.resetAlgaPattern;
				});
			});

			algaCleared = true;
		}
	}

	//for clear, check algaInstantiated and not isPlaying
	clear { | onClear, time, sched |
		//Check sched
		sched = sched ? schedInner;

		//Exec clear on algaInstantiated
		this.addAction(
			condition: { this.algaInstantiated },
			func: { this.clearInner(onClear, time) },
			sched: sched
		);
	}

	//Number plays those number of channels sequentially
	//Array selects specific output
	createPlaySynth { | channelsToPlay, time, scale, out = 0, replace = false,
		usePrevPlayScale = false, usePrevPlayOut = false |
		var actualNumChannels, playSynthSymbol;

		//Can't play a kr node!
		if(rate == \control, { "AlgaNode: cannot play a kr node".error; ^nil; });

		//Scale for play can only be a num (prevPlayScale is used on .replace)
		if(usePrevPlayScale, { scale = prevPlayScale }, { scale = scale ? 1.0 });
		if(scale.isNumber.not, {
			"AlgaNode: play: the 'scale' argument can only be a Number.".error;
			^this;
		});
		//Store prevPlayScale
		prevPlayScale = scale;

		//Out for a play can only be a num (prevPlayOut is used on .replace)
		if(usePrevPlayOut, { out = prevPlayOut }, { out = out ? 0 });
		if(out.isNumber.not, {
			"AlgaNode: play: the 'out' argument can only be a Number. This will point to the first Bus index to play to.".error;
			^this;
		});
		//Store prevPlayOut
		prevPlayOut = out;

		//If not replace, if it was playing and not being stopped, free the previous one.
		//This is used to dynamically change the scale of the output.
		if((replace.not).and(isPlaying).and(beingStopped.not), {
			this.freePlaySynth(time: time)
		});

		if(channelsToPlay != nil, {
			//store it so it's kept across replaces, unless a new one is specified
			playChans = channelsToPlay
		}, {
			//If nil use the one stored
			channelsToPlay = playChans
		});

		//Calc time
		time = this.calculateTemporaryLongestWaitTime(time, playTime);

		if(channelsToPlay.isSequenceableCollection, {
			//Array input. It can be channel numbers or outsMapping
			//Detect outsMapping and replace them with the actual channels value
			channelsToPlay.do({ | entry, i |
				var outMapping = synthDef.outsMapping[entry.asSymbol];
				case
				{ outMapping.isNumberOrArray } {
					channelsToPlay = channelsToPlay.put(i, outMapping);
				};
			});

			//Flatten so that outsMapping are not subarrays
			channelsToPlay = channelsToPlay.flatten;

			//Wrap around the indices entries around the actual
			//number of outputs of the node... Should it ignore out of bounds?
			channelsToPlay = channelsToPlay % numChannels;
			actualNumChannels = channelsToPlay.size;

			playSynthSymbol = (
				"alga_play_" ++
				playSafety ++ "_" ++
				numChannels ++ "_" ++
				actualNumChannels
			).asSymbol;

			playSynth = AlgaSynth(
				playSynthSymbol,
				[
					\in, synthBus.busArg,
					\indices, channelsToPlay,
					\gate, 1,
					\fadeTime, if(tempoScaling, { time / this.clock.tempo }, { time }),
					\scale, scale,
					\out, out
				],
				playGroup,
				waitForInst:false
			);
		}, {
			if(channelsToPlay.isNumber, {
				//Tell it to play that specific number of channels, e.g. 2 for just stereo
				actualNumChannels = channelsToPlay
			}, {
				actualNumChannels = numChannels
			});

			playSynthSymbol = (
				"alga_play_" ++
				playSafety ++ "_" ++
				numChannels ++ "_" ++
				actualNumChannels
			).asSymbol;

			playSynth = AlgaSynth(
				playSynthSymbol,
				[
					\in, synthBus.busArg,
					\gate, 1,
					\fadeTime, if(tempoScaling, { time / this.clock.tempo }, { time }),
					\scale, scale,
					\out, out
				],
				playGroup,
				waitForInst:false
			);
		});

		isPlaying = true;
		beingStopped = false;
	}

	playInner { | channelsToPlay, time, scale, out, sched, replace = false,
		usePrevPlayScale = false, usePrevPlayOut = false |

		//Check sched. If replace, it's always 0 (it's already been considered)
		if(replace.not, { sched = sched ? schedInner });

		//Check only for synthBus, it makes more sense than also checking for synth.algaIstantiated,
		//As it allows meanwhile to create the play synth while synth is getting instantiated
		this.addAction(
			condition: { synthBus != nil },
			func: {
				this.createPlaySynth(
					channelsToPlay: channelsToPlay,
					time: time,
					scale: scale,
					out: out,
					replace: replace,
					usePrevPlayScale: usePrevPlayScale,
					usePrevPlayOut: usePrevPlayOut
				)
			},
			sched: sched,
			preCheck: true
		);
	}

	play { | chans, time, scale, out = 0, sched |
		this.playInner(
			channelsToPlay: chans,
			time: time,
			scale: scale,
			out: out,
			sched: sched
		);
	}

	freePlaySynth { | time, isClear = false, action |
		if(isPlaying, {
			if(isClear.not, {
				//time has already been calculated if isClear == true
				time = this.calculateTemporaryLongestWaitTime(time, playTime);
			});
			if(time == 0, {
				//If time == 0, free right away (or it will be freed next block with \fadeTime, 0)
				playSynth.free
			}, {
				//Set \fadeTime
				playSynth.set(
					\gate, 0,
					\fadeTime, if(tempoScaling, { time / this.clock.tempo }, { time })
				)
			});
			isPlaying = false;
			beingStopped = true;
			if(action != nil, {
				playSynth.onFree({
					action.value
				});
			});
		})
	}

	stopInner { | time, sched, isClear = false, replace = false, action |
		case
		{ replace == true } {
			//Already in a scheduled action
			^this.freePlaySynth(time, false, action);
		}
		{ isClear == true } {
			//Already in a scheduled action
			^this.freePlaySynth(time, true, action);
		};

		//Check sched. If replace.not, don't consider schedInner
		if(replace.not, { sched = sched ? schedInner });

		//Normal case
		this.addAction(
			condition: { this.isPlaying },
			func: { this.freePlaySynth(time, false, action); },
			sched: sched,
			preCheck: true
		);
	}

	stop { | time, sched |
		this.stopInner(time, sched);
	}

	//Global init: all interp synths and synth are correct
	algaInstantiated {
		if(algaCleared, { ^false });

		interpSynths.do({ | interpSynthsAtParam |
			interpSynthsAtParam.do( { | interpSynthAtParam |
				if(interpSynthAtParam.algaInstantiated.not, { ^false })
			});
		});

		^(synth.algaInstantiated);
	}

	//To send signal, only the synth and synthBus are needed to be surely insantiated
	algaInstantiatedAsSender {
		if(synth == nil, { ^false });
		if(algaCleared, { ^false });
		^((synth.algaInstantiated).and(synthBus != nil));
	}

	//To receive signals, and perform interpolation, the specific interpSynth(s)
	//is needed to be surely insantiated
	algaInstantiatedAsReceiver { | param = \in, sender, mix = false |
		var interpSynthsAtParam = interpSynths[param];
		var inNodesAtParam = inNodes[param];

		//Has been cleared
		if(algaCleared, { ^false });

		//First connection
		if((interpSynthsAtParam.size == 1).and(inNodesAtParam.size == 0), {
			if(interpSynthsAtParam[\default].algaInstantiated, {
				^true
			});
		});

		//Subsequent connections
		if((interpSynthsAtParam.size > 0).and(inNodesAtParam.size > 0), {
			//.replaceMix
			if(mix.and(interpSynthsAtParam[sender] != nil), {
				if(interpSynthsAtParam[sender].algaInstantiated, {
					^true
				});
			});

			//Normal from / to and mixFrom / mixTo
			^true
		});

		//Fallback
		^false;
	}

	//Move this node's group before another node's one
	moveBefore { | node |
		group.moveBefore(node.group);
	}

	//Move this node's group after another node's one
	moveAfter { | node |
		group.moveAfter(node.group);
	}

	//Move inside another group (head)
	moveToHead { | argGroup |
		group.moveToHead(argGroup);
	}

	//Move inside another group (tail)
	moveToTail { | argGroup |
		group.moveToTail(argGroup);
	}

	clock { ^(actionScheduler.scheduler.clock) }

	scheduler { ^(actionScheduler.scheduler) }

	//asString: use name or group's ID
	asString {
		if(name == nil, { ^(this.class.asString) });
		^(this.class.asString ++ "(" ++ name.asString ++ ")");
	}

	isAlgaNode { ^true }
}

//Alias
AN : AlgaNode {}

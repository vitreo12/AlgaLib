// AlgaLib: SuperCollider implementation of the Alga live coding language
// Copyright (C) 2020-2021 Francesco Cameli

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

	//The AlgaScheduler @ this server
	var <scheduler;

	//Index of the corresponding AlgaBlock in the AlgaBlocksDict.
	//This is being set in AlgaBlock
	var <>blockIndex = -1;

	//This is the time when making a new connection to this node
	var <connectionTime = 0;

	//This controls the fade in and out when .play / .stop
	var <playTime = 0;

	//This is the longestConnectionTime between all the outNodes.
	//It's used when .replacing a node connected to something, in order for it to be kept alive
	//for all the connected nodes to run their interpolator on it
	//longestConnectionTime will be moved to AlgaBlock and applied per-block!
	var <longestConnectionTime = 0;

	//Keeps track of all the connectionTime of all nodes with this node as input
	var <connectionTimeOutNodes;

	//per-parameter connectionTime
	var <paramsConnectionTime;

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

	//Class for def
	var <defClass;

	//SynthDef, either explicit or internal (Function generated)
	var <synthDef;

	//Spec of parameters (names, default values, channels, rate)
	var <controlNames;

	//Number of channels and rate
	var <numChannels, <rate;

	//Output channel mapping
	var <outsMapping;

	//All Groups / Synths / Busses
	var <group, <playGroup, <synthGroup, <normGroup, <interpGroup;
	var <playSynth, <synth, <normSynths, <interpSynths;
	var <synthBus, <normBusses, <interpBusses;

	//Currently active interpSynths per param.
	//These are used when changing time on connections, and need to update already running
	//interpSynths at specific param / sender combination. It's the whole core that allows
	//to have dynamic fadeTimes
	var <activeInterpSynths;

	//Connected nodes
	var <inNodes, <outNodes;

	//keep track of current \default nodes (this is used for mix parameters)
	var <currentDefaultNodes;

	//Keep track of current scaling for params
	var <paramsScaling;

	//Keep track of current chans mapping for params
	var <paramsChansMapping;

	//Keep track of the "chans" arg for play so it's kept across .replaces
	var <playChans;

	//General state queries
	var <isPlaying = false;
	var <beingStopped = false;
	var <algaToBeCleared = false;
	var <algaWasBeingCleared = false;
	var <algaCleared = false;

	*new { | def, args, connectionTime, playTime, sched, outsMapping, server |
		^super.new.init(def, args, connectionTime, playTime, sched, outsMapping, server)
	}

	initAllVariables { | argServer |
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
			this.temporaryParamSynths = IdentitySet(10);
		});

		^true;
	}

	init { | argDef, argArgs, argConnectionTime = 0, argPlayTime = 0,
		argSched = 0, argOutsMapping, argServer |

		//Check supported classes for argObj, so that things won't even init if wrong.
		//Also check for AlgaPattern
		if(this.isAlgaPattern, {
			//AlgaPattern init
			if((argDef.class != Event).and(argDef.class != Symbol), {
				"AlgaPattern: first argument must be an Event describing the pattern or a Symbol pointing to a SynthDef".error;
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
		if(this.initAllVariables(argServer).not, {
			^this
		});

		//starting connectionTime (using the setter so it also sets longestConnectionTime)
		this.connectionTime_(argConnectionTime, all:true);
		this.playTime_(argPlayTime);

		//Dispatch node creation
		this.dispatchNode(
			argDef, argArgs,
			initGroups: true,
			outsMapping: argOutsMapping,
			sched: argSched
		);
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
		group = AlgaGroup(server);
		playGroup = AlgaGroup(group);
		synthGroup = AlgaGroup(group);
		normGroup = AlgaGroup(group);
		interpGroup = AlgaGroup(group);
	}

	resetGroups {
		if(algaToBeCleared, {
			playGroup = nil;
			group = nil;
			synthGroup = nil;
			normGroup = nil;
			interpGroup = nil;
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
		if((numChannels != nil).and(rate != nil), {
			synthBus = AlgaBus(server, numChannels, rate);
		});
	}

	createInterpNormBusses {
		controlNames.do({ | controlName |
			var paramName = controlName.name;
			var paramRate = controlName.rate;
			var paramNumChannels = controlName.numChannels;

			//This is crucial: interpBusses have 1 more channel for the interp envelope!
			interpBusses[paramName][\default] = AlgaBus(server, paramNumChannels + 1, paramRate);
			normBusses[paramName] = AlgaBus(server, paramNumChannels, paramRate);
		});
	}

	createAllBusses {
		this.createInterpNormBusses;
		this.createSynthBus;
	}

	freeSynthBus { | now = false, time |
		if(now, {
			if(synthBus != nil, {
				synthBus.free;
				synthBus = nil; //Necessary for correct .play behaviour!
			});
		}, {
			//if forking, this.synthBus could change, that's why this is needed
			var prevSynthBus = synthBus.copy;
			synthBus = nil;  //Necessary for correct .play behaviour!

			if(time == nil, { time = longestWaitTime });

			fork {
				//Cheap solution when having to replacing a synth that had other interp stuff
				//going on. Simply wait longer than longestConnectionTime (which will be the time the replaced
				//node will take to interpolate to the previous receivers) and then free all the previous stuff
				(time + 1.0).wait;
				if(prevSynthBus != nil, { prevSynthBus.free });
			}
		});
	}

	freeInterpNormBusses { | now = false, time |
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

	freeAllBusses { | now = false, time |
		this.freeSynthBus(now, time);
		this.freeInterpNormBusses(now, time);
	}

	//This will also be kept across replaces, as it's just updating the dict
	createDefArgs { | args |
		if(args != nil, {
			if(args.isSequenceableCollection.not, { "AlgaNode: args must be an array".error; ^this });
			if((args.size) % 2 != 0, { "AlgaNode: args' size must be a power of two".error; ^this });

			args.do({ | param, i |
				if(param.class == Symbol, {
					var iPlusOne = i + 1;
					if(iPlusOne < args.size, {
						var value = args[i + 1];
						if(value.isKindOf(Buffer), { value = value.bufnum });
						if((value.isNumberOrArray).or(value.isAlgaNode), {
							defArgs[param] = value;
							explicitArgs[param] = true;
						}, {
							("AlgaNode: args at param '" ++ param ++ "' must be a number, array or AlgaNode").error;
						});
					});
				});
			});
		});
	}

	//dispatches controlnames / numChannels / rate according to def class
	dispatchNode { | def, args, initGroups = false, replace = false,
		keepChannelsMapping = false, outsMapping, keepScale = false, sched = 0 |

		defClass = def.class;

		//If there is a synth playing, set its algaInstantiated status to false:
		//this is mostly needed for .replace to work properly and wait for the new synth
		//to be algaInstantiated!
		if(synth != nil, { synth.algaInstantiated = false });

		//Create args dict
		this.createDefArgs(args);

		//Symbol
		if(defClass == Symbol, {
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
			if(defClass == Function, {
				this.dispatchFunction(def, initGroups, replace,
					keepChannelsMapping:keepChannelsMapping,
					outsMapping:outsMapping,
					keepScale:keepScale,
					sched:sched
				);
			}, {
				("AlgaNode: class '" ++ defClass ++ "' is invalid").error;
			});
		});
	}

	//Remove \fadeTime \out and \gate and generate controlNames dict entries
	createControlNamesAndParamsConnectionTime { | synthDescControlNames |
		//Reset entries first
		controlNames.clear;

		synthDescControlNames.do({ | controlName |
			var paramName = controlName.name;
			if((controlName.name != \fadeTime).and(
				controlName.name != \out).and(
				controlName.name != \gate).and(
				controlName.name != '?'), {

				var paramName = controlName.name;

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

	//calculate the outs variable (the outs channel mapping)
	calculateOutsMapping { | replace = false, keepChannelsMapping = false |
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
			synthDef.outsMapping.keysValuesDo({ | key, value |
				//Delete out of bounds entries? Or keep them for future .replaces?
				//if(value < numChannels, {
				newOutsMapping[key] = value;
				//});
			});

			outsMapping = newOutsMapping;
		}, {
			//no replace: use synthDef's ones
			outsMapping = synthDef.outsMapping;
		});
	}

	//build all synths
	buildFromSynthDef { | initGroups = false, replace = false,
		keepChannelsMapping = false, keepScale = false, sched = 0 |

		//Retrieve controlNames from SynthDesc
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
			("AlgaNode: trying to instantiate the AlgaSynthDef '" ++ synthDef.name ++ "' which can free its synth. This is not supported for AlgaNodes, but it will be for AlgaPatterns.").error;
			this.clear;
			^this;
		});

		rate = synthDef.rate;

		sched = sched ? 0;

		//Calculate correct outsMapping
		this.calculateOutsMapping(replace, keepChannelsMapping);

		//Create groups if needed
		if(initGroups, { this.createAllGroups });

		//Create busses
		this.createAllBusses;

		//Create actual synths
		scheduler.addAction(func: {
			this.createAllSynths(
				replace,
				keepChannelsMapping:keepChannelsMapping,
				keepScale:keepScale
			);
		}, sched: sched);
	}

	//Dispatch a SynthDef (symbol)
	dispatchSynthDef { | def, initGroups = false, replace = false,
		keepChannelsMapping = false, keepScale = false, sched = 0 |

		var synthDesc = SynthDescLib.global.at(def);

		if(synthDesc == nil, {
			("AlgaNode: Invalid AlgaSynthDef: '" ++ def.asString ++ "'").error;
			^this;
		});

		synthDef = synthDesc.def;

		if(synthDef.class != AlgaSynthDef, {
			("AlgaNode: Invalid AlgaSynthDef: '" ++ def.asString ++"'").error;
			^this;
		});

		this.buildFromSynthDef(
			initGroups, replace,
			keepChannelsMapping:keepChannelsMapping,
			keepScale:keepScale,
			sched:sched
		);
	}

	//Dispatch a Function
	dispatchFunction { | def, initGroups = false, replace = false,
		keepChannelsMapping = false, outsMapping, keepScale = false, sched = 0 |

		var dispatchCondition = Condition();

		//Note that this forking mechanism is not robust on \udp
		if(server.options.protocol == \udp, {
			"AlgaNode: using a server with UDP protocol. The handling of 'server.sync' can be lost if multiple packets are sent together. It's suggested to use Alga with a server booted with the TCP protocol instead.".warn;
		});

		//Need to wait for server to receive the sdef
		fork {
			synthDef = AlgaSynthDef(
				("alga_" ++ UniqueID.next).asSymbol,
				def,
				outsMapping:outsMapping
			).send(server);

			server.sync(dispatchCondition);
		};

		scheduler.addAction(
			condition: { dispatchCondition.test == true },
			func: {
				this.buildFromSynthDef(
					initGroups, replace,
					keepChannelsMapping:keepChannelsMapping,
					keepScale:keepScale,
					sched:sched
				);
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
			\fadeTime, longestWaitTime
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

	resetInterpNormDicts {
		interpSynths.clear;
		normSynths.clear;
		interpBusses.clear;
		normBusses.clear;
	}

	//Either retrieve default value from controlName or from args
	getDefaultOrArg { | controlName, param = \in, replace = false |
		var defaultOrArg = controlName.defaultValue;
		var defArg;
		var explicitArg = explicitArgs[param];

		if(defArgs != nil, {
			if(replace, {
				//replaceArgs are all the numbers that are set while coding.
				//On replace I wanna restore the current value I'm using, not the default value...
				//Unless I explicily set a new args:
				defArg = replaceArgs[param];
			});

			//No values provided in replaceArgs, or new explicit args: have been just set
			if((defArg == nil).or(explicitArg == true), {
				defArg = defArgs[param];
				explicitArgs[param] = false; //reset state
				replaceArgs.removeAt(param); //reset replaceArg
			});

			//If defArgs has entry, use that one as default instead
			if(defArg != nil, {
				if(defArg.isNumberOrArray, {
					//If it's a number, embed it directly! No interpolation, as it's just setting defaults.
					//Also this works perfectly with replacing Buffer entries
					defaultOrArg = defArg;
				}, {
					//AlgaPattern needs the value to be returned, not to make a connection!
					if(this.isAlgaPattern, { ^defArg });

					if(defArg.isAlgaNode, {
						//Schedule connection with the algaNode
						this.makeConnection(defArg, param);
					});
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
			if(scaleEntry.size != paramNumChannels, {
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
		if(scale.isNil, { ^nil });

		if(scale.isNumberOrArray.not, {
			"AlgaNode: the scale parameter must be a Number or an Array".error;
			^nil
		});

		//just a number: act like a multiplier
		if(scale.isNumber, {
			var outArray = [\outMultiplier, scale];
			if(addScaling, { this.addScaling(param, sender, scale) });
			^outArray;
		});

		//just lowMax / hiMax
		if(scale.size == 2, {
			var outArray = Array.newClear(6);
			var highMin = scale[0];
			var highMax = scale[1];
			var newHighMin = this.checkScaleParameterSize(highMin, "highMin", param, paramNumChannels);
			var newHighMax = this.checkScaleParameterSize(highMax, "highMax", param, paramNumChannels);

			if((newHighMin.isNil).or(newHighMax.isNil), {
				^nil
			});

			outArray[0] = \highMin; outArray[1] = newHighMin;
			outArray[2] = \highMax; outArray[3] = newHighMax;
			outArray[4] = \useScaling; outArray[5] = 1;

			scale[0] = newHighMin;
			scale[1] = newHighMax;

			if(addScaling, { this.addScaling(param, sender, scale) });

			^outArray;
		}, {
			//all four of the scales
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

				outArray[0] = \lowMin; outArray[1] = newLowMin;
				outArray[2] = \lowMax; outArray[3] = newLowMax;
				outArray[4] = \highMin; outArray[5] = newHighMin;
				outArray[6] = \highMax; outArray[7] = newHighMax;
				outArray[8] = \useScaling; outArray[9] = 1;

				scale[0] = newLowMin;
				scale[1] = newLowMax;
				scale[2] = newHighMin;
				scale[3] = newHighMax;

				if(addScaling, { this.addScaling(param, sender, scale) });

				^outArray;
			}, {
				("AlgaNode: the scale parameter must be an array of either 2 " ++
					" (hiMin / hiMax) or 4 (lowMin, lowMax, hiMin, hiMax) entries.").error;
				^nil
			});
		});
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

		var actualSenderChansMapping = senderChansMapping;

		//If sender is not an AlgaNode, use default
		if(sender.isAlgaNode.not, { ^(Array.series(paramNumChans)) });

		//Connect with outMapping symbols. Retrieve it from the sender
		if(actualSenderChansMapping.class == Symbol, {
			actualSenderChansMapping = sender.outsMapping[actualSenderChansMapping];
			if(actualSenderChansMapping == nil, {
				("AlgaNode: invalid channel name '" ++ senderChansMapping ++ "'. Default will be used.").warn;
			});
		});

		//Update entry in Dict with the non-modified one (used in .replace then)
		if(updateParamsChansMapping, {
			paramsChansMapping[param][sender] = actualSenderChansMapping;
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
				"AlgaNode: senderChansMapping must be a number or an array. Using default one.".error;
				^(Array.series(paramNumChans));
			});
		});
	}

	//The actual empty function
	removeActiveInterpSynthOnFree { | param, sender, interpSynth, action |
		interpSynth.onFree({
			activeInterpSynths[param][sender].remove(interpSynth);

			//This is used in AlgaPattern
			if(action != nil, {
				action.value;
			});
		});
	}

	//Use the .onFree node function to dynamically fill and empty the activeInterpSynths for
	//each param / sender combination!
	addActiveInterpSynthOnFree { | param, sender, interpSynth, action |
		//Each sender has IdentitySet with all the active ones
		if(activeInterpSynths[param][sender].class == IdentitySet, {
			activeInterpSynths[param][sender].add(interpSynth)
		}, {
			activeInterpSynths[param][sender] = IdentitySet();
			activeInterpSynths[param][sender].add(interpSynth)
		});

		//The actual function that empties
		this.removeActiveInterpSynthOnFree(param, sender, interpSynth, action);
	}

	//Set proper fadeTime for all active interpSynths on param / sender combination
	setFadeTimeForAllActiveInterpSynths { | param, sender, time |
		activeInterpSynths[param][sender].do({ | activeInterpSynth |
			activeInterpSynth.set(\fadeTime, time);
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

			//If replace, connect to the pervious bus, not default
			if(replace, {
				var sendersSet = inNodes[paramName];

				//Restoring a connected parameter, being it normal or mix
				if(sendersSet != nil, {
					var onlyEntry = false;

					//if size == 1, index from \default
					if(sendersSet.size == 1, {
						onlyEntry = true;
					});

					sendersSet.do({ | prevSender |
						var interpBus, interpSynth, normSynth;
						var interpSymbol, normSymbol;

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

						//Use previous entry for the channel mapping, otherwise, nil.
						//nil will generate Array.series(...) in calculateSenderChansMappingArray
						if(keepChannelsMapping, {
							oldParamsChansMapping = this.getParamChansMapping(paramName, prevSender);
						});

						//Use previous entry for inputs scaling
						if(keepScale, {
							oldParamScale = this.getParamScaling(paramName, prevSender);
						});

						//overwrite interp symbol considering the senders' num channels!
						interpSymbol = (
							"alga_interp_" ++
							prevSender.rate ++
							prevSender.numChannels ++
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
							prevSender.numChannels,
							paramNumChannels,
							false
						);

						scaleArray = this.calculateScaling(
							paramName, prevSender,
							paramNumChannels, oldParamScale
						);

						interpSynthArgs = [
							\in, prevSender.synthBus.busArg,
							\out, interpBus.index,
							\indices, channelsMapping,
							\fadeTime, 0
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

						if(onlyEntry, {
							//normal param
							interpSynths[paramName][\default] = interpSynth;
							normSynths[paramName][\default] = normSynth;

							//Add interpSynth to the current active ones for specific param / sender combination
							this.addActiveInterpSynthOnFree(paramName, \default, interpSynth);
						}, {
							//mix param
							interpSynths[paramName][prevSender] = interpSynth;
							normSynths[paramName][prevSender] = normSynth;

							//Add interpSynth to the current active ones for specific param / sender combination
							this.addActiveInterpSynthOnFree(paramName, prevSender, interpSynth);

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
				}, {
					noSenders = true;
				})
			});

			//interpSynths and normSynths are a IdentityDict of IdentityDicts
			if(replace.not.or(noSenders), {
				//e.g. \alga_interp_audio1_control1
				var interpSymbol = (
					"alga_interp_" ++
					paramRate ++
					paramNumChannels ++
					"_" ++
					paramRate ++
					paramNumChannels
				).asSymbol;

				//e.g. \alga_norm_audio1
				var normSymbol = (
					"alga_norm_" ++
					paramRate ++
					paramNumChannels
				).asSymbol;

				//default interpBus
				var interpBus = interpBusses[paramName][\default];

				//Instantiated right away, with no \fadeTime, as it will directly be connected to
				//synth's parameter. Synth will read its params from all the normBusses
				var normSynth = AlgaSynth(
					normSymbol,
					[\args, interpBus.busArg, \out, normBus.index, \fadeTime, 0],
					normGroup,
					waitForInst:false
				);

				//use paramDefault: no replace or no senders in sendersSet
				var interpSynth = AlgaSynth(
					interpSymbol,
					[\in, paramDefault, \out, interpBus.index, \fadeTime, 0],
					interpGroup
				);

				interpSynths[paramName][\default] = interpSynth;
				normSynths[paramName][\default] = normSynth;

				//Add interpSynth to the current active ones for specific param / sender combination
				this.addActiveInterpSynthOnFree(paramName, \default, interpSynth);
			});
		});
	}

	//Create all synths for each param
	createAllSynths { | replace = false, keepChannelsMapping = false, keepScale = false |
		this.createInterpNormSynths(
			replace,
			keepChannelsMapping:keepChannelsMapping,
			keepScale:keepScale
		);

		this.createSynth;
	}

	//Run when <<+ / .replace (on mixed connection) / .replaceMix
	createMixInterpSynthInterpBusBusNormSynthAtParam { | sender, param = \in, replaceMix = false,
		replace = false, senderChansMapping, scale, time |

		//also check sender is not the default!
		var newMixConnection = (
			this.mixParamContainsSender(param, sender).not).and(
			sender != currentDefaultNodes[param]
		);

		var newMixConnectionOrReplaceMix = newMixConnection.or(replaceMix);
		var newMixConnectionOrReplace = newMixConnection.or(replace);

		//Only run if replaceMix (meaning, a mix entry has been explicitly replaced)
		//OR no param/sender combination is present already, otherwise it means it was already connected!
		if(newMixConnectionOrReplaceMix, {
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

			normSynth = AlgaSynth(
				normSymbol,
				[\args, interpBus.busArg, \out, normBus.index, \fadeTime, 0],
				normGroup,
				waitForInst:false
			);

			interpBusses[param][sender] = interpBus;
			normSynths[param][sender] = normSynth;
		});

		//Make sure to not duplicate something that's already in the mix
		if(newMixConnectionOrReplace.or(newMixConnectionOrReplaceMix), {
			this.createInterpSynthAtParam(sender, param,
				mix:true, newMixConnectionOrReplaceMix:newMixConnectionOrReplaceMix,
				senderChansMapping:senderChansMapping, scale:scale, time:time
			);
		}, {
			//the alga node is already mixed. run replaceMix with itself
			//this is useful in case scale parameter has been changed by user
			"AlgaNode: sender was already mixed. Running 'replaceMix' with itself instead".warn;
			this.replaceMixInner(param, sender, sender, senderChansMapping, scale, time:time);
		});
	}

	//Used at every <<, >>, <<+, >>+, <|
	createInterpSynthAtParam { | sender, param = \in, mix = false,
		newMixConnectionOrReplaceMix = false, senderChansMapping, scale, time |

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

		if(mix, {
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
			("AlgaNode: invalid param for interp synth to free: '" ++ param ++ "'").error;
			^this
		});

		//If -1, or invalid, set to global connectionTime
		if(paramConnectionTime == nil, { paramConnectionTime = connectionTime });
		if(paramConnectionTime < 0, { paramConnectionTime = connectionTime });

		//calc temporary time
		time = this.calculateTemporaryLongestWaitTime(time, paramConnectionTime);

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
		if(interpBusAtParam == nil, { ("AlgaNode: invalid interp bus at param '" ++ param ++ "'").error; ^this });

		//Try to get sender one.
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

		senderChansMappingToUse = this.calculateSenderChansMappingArray(
			param,
			sender, //must be sender! not senderSym!
			senderChansMapping,
			senderNumChannels,
			paramNumChannels,
		);

		//calculate scale array
		scaleArray = this.calculateScaling(param, sender, paramNumChannels, scale);

		//new interp synth, with input connected to sender and output to the interpBus
		//THIS USES connectionTime!!
		if(sender.isAlgaNode, {
			//If mix and replaceMix, spawn a fadeIn synth, which balances out the interpSynth's envelope for normSynth
			if(mix.and(newMixConnectionOrReplaceMix), {
				var fadeInSymbol = ("alga_fadeIn_" ++
					paramRate ++
					paramNumChannels
				).asSymbol;

				AlgaSynth(
					fadeInSymbol,
					[
						\out, interpBus.index,
						\fadeTime, time,
					],
					interpGroup,
					waitForInst:false
				);
			});

			interpSynthArgs = [
				\in, sender.synthBus.busArg,
				\out, interpBus.index,
				\indices, senderChansMappingToUse,
				\fadeTime, time
			];

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

			if(sender == nil, {
				paramVal = this.getDefaultOrArg(controlName, param); //either default or provided arg!
			}, {
				//If not nil, check if it's a number or array. Use it if that's the case
				if(sender.isNumberOrArray,  {
					paramVal = sender;
				}, {
					"AlgaNode: invalid paramVal for AlgaNode".error;
					^nil;
				});
			});

			interpSynthArgs = [
				\in, paramVal,
				\out, interpBus.index,
				\indices, senderChansMappingToUse,
				\fadeTime, time
			];

			//add scaleArray to args
			if(scaleArray != nil, {
				interpSynthArgs = interpSynthArgs.addAll(scaleArray);
			});

			interpSynth = AlgaSynth(
				interpSymbol,
				interpSynthArgs,
				interpGroup
			);
		});

		//Add to interpSynths for the param
		interpSynths[param][senderSym] = interpSynth;

		//Add interpSynth to the current active ones for specific param / sender combination
		this.addActiveInterpSynthOnFree(param, senderSym, interpSynth);

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
					\fadeTime, if(useConnectionTime, { longestWaitTime }, { 0 })
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
		replace = false, cleanupDicts = false, time |

		var interpSynthAtParam = interpSynths[param][sender];

		if(interpSynthAtParam != nil, {
			//These must be fetched before the addAction (they refer to the current state!)
			var normSynthAtParam = normSynths[param][sender];
			var interpBusAtParam = interpBusses[param][sender];
			var currentDefaultNode = currentDefaultNodes[param];

			//Make sure all of these are scheduled correctly to each other!
			scheduler.addAction(
				condition: {
					(interpSynthAtParam.algaInstantiated)/*.and(normSynthAtParam.algaInstantiated)*/
				},
				func: {
					var notDefaultNode = false;

					//Only run fadeOut and remove normSynth if they are also not the ones that are used for \default.
					//This makes sure that \defauls is kept alive at all times
					if(sender != currentDefaultNode, {
						notDefaultNode = true;
					});

					//calculate temporary time
					time = this.calculateTemporaryLongestWaitTime(time, paramConnectionTime);

					//Only create fadeOut and free normSynth on .replaceMix and .disconnect! (not .replace).
					//Also, don't create it for the default node, as that needs to be kept alive at all times!
					if(notDefaultNode.and(replace.not), {
						var fadeOutSymbol = ("alga_fadeOut_" ++
							interpBusAtParam.rate ++
							(interpBusAtParam.numChannels - 1) //it has one more for env. need to remove that from symbol
						).asSymbol;

						AlgaSynth(
							fadeOutSymbol,
							[
								\out, interpBusAtParam.index,
								\fadeTime, time,
							],
							interpGroup,
							waitForInst:false
						);

						//This has to be surely algaInstantiated before being freed
						normSynthAtParam.set(\gate, 0, \fadeTime, time);
					});

					//This has to be surely algaInstantiated before being freed
					interpSynthAtParam.set(\t_release, 1, \fadeTime, time);

					//Set correct fadeTime for all active interp synths at param / sender combination
					this.setFadeTimeForAllActiveInterpSynths(param, sender, time);
				}
			);
		});

		//On a .disconnect / .replaceMix, remove the entries
		if(cleanupDicts, {
			interpSynths[param].removeAt(sender);
			activeInterpSynths[param].removeAt(sender);
			interpBusses[param].removeAt(sender);
			normSynths[param].removeAt(sender);
		});
	}

	//Free interpSynth at param. This is also used in .replace for both mix entries and normal ones
	//THIS USES connectionTime!!
	freeInterpSynthAtParam { | sender, param = \in, mix = false, replace = false, time |
		var interpSynthsAtParam = interpSynths[param];
		var paramConnectionTime = paramsConnectionTime[param];

		//If -1, or invalid, set to global connectionTime
		if(paramConnectionTime == nil, { paramConnectionTime = connectionTime });
		if(paramConnectionTime < 0, { paramConnectionTime = connectionTime });

		//Free them all (check if there were mix entries).
		//sender == nil comes from <|
		//mix == false comes from <<
		//mix == true comes from <<+, .replaceMix, .replace, .disconnect
		if((sender == nil).or(mix == false), {
			//If interpSynthsAtParam length is more than one, the param has mix entries. Fade them all out.
			if(interpSynthsAtParam.size > 1, {
				interpSynthsAtParam.keysValuesDo({ | interpSender, interpSynthAtParam  |
					if(interpSender != \default, { // ignore \default !
						if(replace, {
							//on .replace of a mix node, just free that one (without fadeOut)
							this.freeMixNodeAtParam(interpSender, param,
								replace:true, cleanupDicts:false
							);
						}, {
							//.disconnect
							this.freeMixNodeAtParam(interpSender, param,
								paramConnectionTime:paramConnectionTime,
								replace:false, cleanupDicts:true, time:time
							);
						});
					});
				});
			}, {
				//calculate temporary time
				time = this.calculateTemporaryLongestWaitTime(time, paramConnectionTime);

				//Start the free on the previous interp synth (size is 1 here)
				interpSynthsAtParam.do({ | interpSynthAtParam |
					interpSynthAtParam.set(\t_release, 1, \fadeTime, time);
				});

				//Set correct fadeTime for all active interp synths at param / sender combination
				this.setFadeTimeForAllActiveInterpSynths(param, \default, time);
			});
		}, {
			//mix == true, only free the one (disconnect function)
			if(replace, {
				//on .replace of a mix node, just free that one (without fadeOut)
				this.freeMixNodeAtParam(sender, param,
					paramConnectionTime:paramConnectionTime,
					replace:true, cleanupDicts:false, time:time
				);
			}, {
				//.disconnect
				this.freeMixNodeAtParam(sender, param,
					paramConnectionTime:paramConnectionTime,
					replace:false, cleanupDicts:true, time:time
				);
			});
		});
	}

	//param -> IdentitySet[AlgaNode, AlgaNode, ...]
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

		if(sender.isAlgaNode, {
			//Empty entry OR not doing mixing, create new IdentitySet. Otherwise, add to existing
			if((inNodes[param] == nil).or(mix.not), {
				inNodes[param] = IdentitySet[sender];
			}, {
				inNodes[param].add(sender);
			})
		}, {
			//Number ... Always replace as mixing is not supported for numbers
			replaceArgs[param] = sender;
		});
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
		//This will replace the entries on new connection (when mix == false)
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
		var inNodesAtParam       = inNodes[param];
		var senderOutNodesAtThis = sender.outNodes[this];

		if(senderOutNodesAtThis != nil, {
			//Just remove one param from sender's set at this entry
			senderOutNodesAtThis.remove(param);

			//If IdentitySet is now empty, remove it entirely
			if(senderOutNodesAtThis.size == 0, {
				sender.outNodes.removeAt(this);
			});
		});

		if(inNodesAtParam != nil, {
			//Remove the specific param / sender combination from inNodes
			inNodesAtParam.remove(sender);

			//If IdentitySet is now empty, remove it entirely
			if(inNodesAtParam.size == 0, {
				inNodes.removeAt(param);
			});
		});

		//Recalculate longestConnectionTime too...
		//This should also take in account eventual multiple sender / param combinations
		sender.connectionTimeOutNodes[this] = 0;
		sender.calculateLongestConnectionTime(0);
	}

	//Remove entries from inNodes / outNodes / connectionTimeOutNodes for all involved nodes
	removeInOutNodesDict { | oldSender = nil, param = \in |
		var oldSenders = inNodes[param];

		if(oldSenders == nil, {
			//AlgaPattern won't care about printing this
			if(this.isAlgaPattern.not, { ( "AlgaNode: no previous connection enstablished at param '" ++ param ++ "'").error });
			^this
		});

		oldSenders.do({ | sender |
			var sendersParamsSet = sender.outNodes[this];
			if(sendersParamsSet != nil, {
				//no oldSender specified: remove them all!
				if(oldSender == nil, {
					this.removeInOutNodeAtParam(sender, param);
				}, {
					//If specified oldSender, only remove that one (in mixing scenarios)
					if(sender == oldSender, {
						this.removeInOutNodeAtParam(sender, param);
					})
				})
			})
		});

		//Remove replaceArgs
		replaceArgs.removeAt(param);
	}

	//Clear the dicts
	resetInOutNodesDicts {
		if(algaToBeCleared, {
			inNodes.clear;
			outNodes.clear;
		});
	}

	//New interp connection at specific parameter
	newInterpConnectionAtParam { | sender, param = \in, replace = false,
		senderChansMapping, scale, time |

		var controlName = controlNames[param];
		if(controlName == nil, {
			("AlgaNode: invalid param to create a new interp synth for: '" ++ param ++ "'").error;
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
		this.freeInterpSynthAtParam(sender, param, time:time);

		//Spawn new interp synth (fades in)
		this.createInterpSynthAtParam(sender, param,
			senderChansMapping:senderChansMapping, scale:scale, time:time
		);
	}

	//New mix connection at specific parameter
	newMixConnectionAtParam { | sender, param = \in, replace = false,
		replaceMix = false, senderChansMapping, scale, time |

		var controlName = controlNames[param];
		if(controlName == nil, {
			("AlgaNode: invalid param to create a new interp synth for: '" ++ param ++ "'").error;
			^this;
		});

		//Add proper inNodes / outNodes / connectionTimeOutNodes
		this.addInOutNodesDict(sender, param, mix:true);

		//Re-order groups
		//Actually reorder the block's nodes ONLY if not running .replace
		//(no need there, they are already ordered, and it also avoids a lot of problems
		//with feedback connections)
		if((replace.not).and(replaceMix).not, {
			AlgaBlocksDict.createNewBlockIfNeeded(this, sender);
		});

		//If replaceMix = true, the call comes from replaceMix, which already calls freeInterpSynthAtParam!
		if(replace, {
			this.freeInterpSynthAtParam(sender, param, mix:true, replace:true, time:time);
		});

		//Spawn new interp mix node
		this.createMixInterpSynthInterpBusBusNormSynthAtParam(sender, param,
			replaceMix:replaceMix, replace:replace,
			senderChansMapping:senderChansMapping, scale:scale, time:time
		);
	}

	//Used in <| and replaceMix
	removeInterpConnectionAtParam { | oldSender = nil, param = \in, time |
		var controlName = controlNames[param];
		if(controlName == nil, {
			("AlgaNode: invalid param to reset: '" ++ param ++ "'").error;
			^this;
		});

		//Remove inNodes / outNodes / connectionTimeOutNodes
		this.removeInOutNodesDict(oldSender, param);

		//Re-order groups shouldn't be needed when removing connections

		//Free previous interp synth (fades out)
		this.freeInterpSynthAtParam(oldSender, param, time:time);

		//Create new interp synth with default value (or the one supplied with args at start) (fades in)
		this.createInterpSynthAtParam(nil, param, time:time);
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
		replaceMix = false, senderChansMapping, scale, time |

		if((sender.isAlgaNode.not).and(sender.isNumberOrArray.not), {
			"AlgaNode: can't connect to something that's not an AlgaNode, a Number or an Array".error;
			^this
		});

		//Can't connect AlgaNode to itself (yet)
		if(this === sender, { "AlgaNode: can't connect an AlgaNode to itself".error; ^this });

		//Check parameter in controlNames
		if(this.checkParamExists(param).not, {
			("AlgaNode: '" ++ param ++ "' is not a valid parameter, it is not defined in the def.").error;
			^this
		});

		if(mix, {
			var currentDefaultNodeAtParam = currentDefaultNodes[param];

			//trying to <<+ instead of << on first connection
			if((currentDefaultNodeAtParam == nil), {
				("AlgaNode: first connection. Running 'from' instead.").warn;
				mix = false;
			});

			//can't add to a num. just replace it.. It would be impossible to keep track of all
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

			//trying to run replaceMix / mixFrom / mixTo when sender is the only entry!
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
			this.newMixConnectionAtParam(sender, param,
				replace:replace, replaceMix:replaceMix,
				senderChansMapping:senderChansMapping,
				scale:scale, time:time
			)
		}, {
			//mix == false, clean everything up

			//Connect interpSynth to the sender's synthBus
			this.newInterpConnectionAtParam(sender, param,
				replace:replace, senderChansMapping:senderChansMapping,
				scale:scale, time:time
			);

			//Cleanup interpBusses / interpSynths / normSynths from previous mix, leaving \default only
			this.cleanupMixBussesAndSynths(param);
		});
	}

	//Wrapper for scheduler
	makeConnection { | sender, param = \in, replace = false, mix = false,
		replaceMix = false, senderChansMapping, scale, time, sched = 0 |

		if(this.algaCleared.not.and(sender.algaCleared.not).and(sender.algaToBeCleared.not), {
			scheduler.addAction(
				condition: {
					(this.algaInstantiatedAsReceiver(param, sender, mix)).and(sender.algaInstantiatedAsSender)
				},
				func: {
					this.makeConnectionInner(sender, param, replace, mix,
						replaceMix, senderChansMapping, scale, time:time
					)
				},
				sched: sched
			);
		}, { "AlgaNode: can't makeConnection, sender has been cleared".error; }
		);
	}

	from { | sender, param = \in, chans, scale, time, sched |
		//If buffer, use .bufnum and .replace
		if(sender.isKindOf(Buffer), {
			var senderBufNum = sender.bufnum;
			var args = [ param, senderBufNum ];
			"AlgaNode: changing a Buffer. This will trigger 'replace'.".warn;
			^this.replace(synthDef.name, args, time, sched);
		});

		if(sender.isAlgaNode, {
			if(this.server != sender.server, {
				("AlgaNode: trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
			this.makeConnection(sender, param, senderChansMapping:chans,
				scale:scale, time:time, sched:sched
			);
		}, {
			if(sender.isNumberOrArray, {
				this.makeConnection(sender, param, senderChansMapping:chans,
					scale:scale, time:time, sched:sched
				);
			}, {
				("AlgaNode: trying to enstablish a connection from an invalid class: " ++ sender.class).error;
			});
		});
	}

	//arg is the sender. it can also be a number / array to set individual values
	<< { | sender, param = \in |
		this.from(sender: sender, param: param);
	}

	to { | receiver, param = \in, chans, scale, time, sched |
		if(receiver.isAlgaNode, {
			if(this.server != receiver.server, {
				("AlgaNode: trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
			receiver.makeConnection(this, param, senderChansMapping:chans,
				scale:scale, time:time, sched:sched
			);
		}, {
			("AlgaNode: trying to enstablish a connection to an invalid class: " ++ receiver.class).error;
		});
	}

	//arg is the receiver
	>> { | receiver, param = \in |
		this.to(receiver: receiver, param: param);
	}

	mixFrom { | sender, param = \in, chans, scale, time, sched |
		if(sender.isKindOf(Buffer), {
			"AlgaNode: Buffers cannot be mixed to AlgaNodes' parameters. Running 'from' instead.".warn;
			^this.from(sender, param, chans, scale, time, sched);
		});

		if(sender.isAlgaNode, {
			if(this.server != sender.server, {
				("AlgaNode: trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
			this.makeConnection(sender, param, mix:true, senderChansMapping:chans,
				scale:scale, time:time, sched:sched
			);
		}, {
			if(sender.isNumberOrArray, {
				this.makeConnection(sender, param, mix:true, senderChansMapping:chans,
					scale:scale, time:time, sched:sched
				);
			}, {
				("AlgaNode: trying to enstablish a connection from an invalid class: " ++ sender.class).error;
			});
		});
	}

	//add to already running nodes (mix)
	<<+ { | sender, param = \in |
		this.mixFrom(sender: sender, param: param);
	}

	mixTo { | receiver, param = \in, chans, scale, time, sched |
		if(receiver.isAlgaNode, {
			if(this.server != receiver.server, {
				("AlgaNode: trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
			receiver.makeConnection(this, param, mix:true, senderChansMapping:chans,
				scale:scale, time:time, sched:sched
			);
		}, {
			("AlgaNode: trying to enstablish a connection to an invalid class: " ++ receiver.class).error;
		});
	}

	//add to already running nodes (mix)
	>>+ { | receiver, param = \in |
		this.mixTo(receiver: receiver, param: param);
	}

	//disconnect + makeConnection, very easy
	replaceMixInner { | param = \in, oldSender, newSender, chans, scale, time |
		this.disconnectInner(param, oldSender, true, time:time);
		this.makeConnectionInner(newSender, param,
			replace:false, mix:true, replaceMix:true,
			senderChansMapping:chans, scale:scale, time:time
		);
	}

	//Alias for replaceMix (which must be deprecated, collides name with .replace)
	mixSwap { | param = \in, oldSender, newSender, chans, scale, time, sched |
		^this.replaceMix(param, oldSender, newSender, chans, scale, time, sched);
	}

	//Replace a mix entry at param... Practically just freeing the old one and triggering the new one.
	//This will be useful in the future if wanting to implement some kind of system to retrieve individual
	//mix entries (like, \in1, \in2). No need it for now
	replaceMix { | param = \in, oldSender, newSender, chans, scale, time, sched |
		if(newSender.isAlgaNode.not, {
			("AlgaNode: " ++ (newSender.class.asString) ++ " is not an AlgaNode").error;
			^this;
		});

		scheduler.addAction(
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
					this.replaceMixInner(param, oldSender, newSender, chans, scale, time);
				});
			},
			sched: sched
		);
	}

	resetParamInner { | param = \in, oldSender = nil, time |
		//Also remove inNodes / outNodes / connectionTimeOutNodes
		if(oldSender != nil, {
			if(oldSender.isAlgaNode, {
				this.removeInterpConnectionAtParam(oldSender, param, time:time);
			}, {
				("AlgaNode: trying to remove a connection to an invalid AlgaNode: " ++ oldSender.asString).error;
			})
		}, {
			this.removeInterpConnectionAtParam(nil, param, time:time);
		})
	}

	resetParam { | param = \in, oldSender = nil, time, sched |
		scheduler.addAction(
			condition: {
				(this.algaInstantiatedAsReceiver(param, oldSender, false)).and(oldSender.algaInstantiatedAsSender)
			},
			func: {
				this.resetParamInner(param, oldSender, time:time)
			},
			sched: sched
		);
	}

	//same as resetParam, which must be deprecated (bad naming)
	reset { | param = \in, time, sched |
		^this.resetParam(param, nil, time, sched);
	}

	//resets to the default value in controlNames
	//OR, if provided, to the value of the original args that were used to create the node
	//oldSender is used in case of mixing, to only remove that one
	<| { | param = \in |
		this.resetParam(param, nil);
	}

	//On .replace on an already running mix connection
	replaceMixConnectionInner { | param = \in, sender, senderChansMapping, scale, time |
		this.makeConnectionInner(sender, param,
			replace:true, mix:true, replaceMix:false,
			senderChansMapping:senderChansMapping,
			scale:scale, time:time
		);
	}

	//On .replace on an already running mix connection
	replaceMixConnection { | param = \in, sender, senderChansMapping, scale, time |
		scheduler.addAction(
			condition: {
				(this.algaInstantiatedAsReceiver(param, sender, true)).and(sender.algaInstantiatedAsSender)
			},
			func: {
				this.replaceMixConnectionInner(param, sender, senderChansMapping, scale, time)
			}
		);
	}

	//replace connections FROM this
	replaceConnections { | keepChannelsMapping = true, keepScale = true, time |
		//inNodes are already handled in dispatchNode(replace:true)

		//outNodes. Remake connections that were in place with receivers.
		//This will effectively trigger interpolation process.
		outNodes.keysValuesDo({ | receiver, paramsSet |
			paramsSet.do({ | param |
				var oldParamsChansMapping = nil;
				var oldScale = nil;

				//Restore old channels mapping! It can either be a symbol, number or array here
				if(keepChannelsMapping, { oldParamsChansMapping = receiver.getParamChansMapping(param, this) });

				//Restore old scale mapping!
				if(keepScale, { oldScale = receiver.getParamScaling(param, this) });

				//If it was a mixer connection, use replaceMixConnection
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
	}

	replaceInner { | def, args, time, outsMapping, keepOutsMappingIn = true,
		keepOutsMappingOut = true, keepScalesIn = true, keepScalesOut = true |

		var wasPlaying = false;

		//Re-init groups if clear was used or toBeCleared
		var initGroups = if((group == nil).or(algaCleared).or(algaToBeCleared), { true }, { false });

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

		//If it was playing, free previous playSynth
		if(isPlaying, {
			this.stop;
			wasPlaying = true;
		});

		//This doesn't work with feedbacks, as synths would be freed slightly before
		//The new ones finish the rise, generating click. These should be freed
		//When the new synths/busses are surely algaInstantiated on the server!
		//The cheap solution that it's in place now is to wait 1.0 longer than longestConnectionTime.
		//Work out a better solution now that AlgaScheduler is well tested!
		this.freeAllSynths(false, false);
		this.freeAllBusses;

		//Reset dict entries
		this.resetInterpNormDicts;

		//New one
		//Just pass the entry, not the whole thing
		this.dispatchNode(
			def:def,
			args:args,
			initGroups:initGroups,
			replace:true,
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
		if(wasPlaying.or(beingStopped), {
			this.playInner(replace:true)
		});

		//Reset flag
		algaWasBeingCleared = false;
	}

	//replace content of the node, re-making all the connections.
	//If this was connected to a number / array, should I restore that value too or keep the new one?
	replace { | def, args, time, sched, outsMapping, keepOutsMappingIn = true,
		keepOutsMappingOut = true, keepScalesIn = true, keepScalesOut = true |

		//Check global algaInstantiated
		scheduler.addAction(
			condition: { this.algaInstantiated },
			func: {
				this.replaceInner(
					def:def, args:args, time:time,
					outsMapping:outsMapping,
					keepOutsMappingIn:keepOutsMappingIn,
					keepOutsMappingOut:keepOutsMappingOut,
					keepScalesIn:keepScalesIn, keepScalesOut:keepScalesOut
				)
			},
			sched: sched
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
	disconnectInner { | param = \in, oldSender, replaceMix = false, time |
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

			this.freeInterpSynthAtParam(oldSender, param, true, time:time);

			//retrieve the updated ones
			interpSynthsAtParam = interpSynths[param];

			//If length is now 2, it means it's just one mixer AND the \default node left in the dicts.
			//Assign the node to \default and remove the previous mixer.
			//Should I retrieve inNodes.size == 1 instead?
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
			this.freeInterpSynthAtParam(oldSender, param, true, time:time);
		});
	}

	//Remove individual mix entries at param
	disconnect { | param = \in, oldSender = nil, time, sched |
		//If it wasn't a mix param, but the only entry, run <| instead
		if(inNodes[param].size == 1, {
			if(inNodes[param].findMatch(oldSender) != nil, {
				"AlgaNode: oldSender was the only entry. Running 'reset' instead".warn;
				^this.resetParam(param, oldSender, time:time);
			});
		});

		if(oldSender == nil, {
			^this.resetParam(param, oldSender, time:time);
		});

		//Else, mix param
		if(oldSender.isAlgaNode.not, {
			("AlgaNode: " ++ (oldSender.class.asString) ++ " is not an AlgaNode").error;
			^this;
		});

		scheduler.addAction(
			condition: {
				(this.algaInstantiatedAsReceiver(param, oldSender, true)).and(oldSender.algaInstantiatedAsSender)
			},
			func: { this.disconnectInner(param, oldSender, time:time) },
			sched: sched
		);
	}

	//alias for disconnect: remove a mix entry
	removeMix { | param = \in, oldSender, time, sched |
		this.disconnect(param, oldSender, time, sched);
	}

	//Find out if specific param / sender combination is in the mix
	mixParamContainsSender { | param = \in, sender |
		var interpSynthsAtParam = interpSynths[param];
		if(interpSynthsAtParam == nil, { ^false });
		^(interpSynthsAtParam[sender] != nil)
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

	clearInner { | time |
		//calc temporary time
		var stopTime = this.calculateTemporaryLongestWaitTime(time, playTime);
		time = max(stopTime, longestWaitTime); //makes sure to wait longest time to run clears

		//If synth had connections, run <| (or disconnect, if mixer) on the receivers
		this.removeConnectionFromReceivers(time);

		//This could be overwritten if .replace is called
		algaToBeCleared = true;

		//Stop playing (if it was playing at all)
		this.stopInner(stopTime, isClear:true);

		//Just remove groups, they contain the synths
		this.freeAllGroups(false, time);
		this.freeAllBusses(false, time);

		fork {
			//Wait time before clearing groups, synths and busses...
			(time + 1.0).wait;

			//Reset all instance variables
			if(algaToBeCleared, {
				this.resetSynth;
				this.resetInterpNormSynths;
				this.resetGroups;
				this.resetInOutNodesDicts;

				defClass = nil;
				defArgs = nil;
				if(this.isAlgaPattern, {
					this.resetAlgaPattern;
				});
			});

			algaCleared = true;
		}
	}

	//for clear, check algaInstantiated and not isPlaying
	clear { | time, sched |
		scheduler.addAction(
			condition: { this.algaInstantiated },
			func: { this.clearInner(time) },
			sched: sched
		);
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
	createPlaySynth { | time, channelsToPlay, replace = false |
		if((isPlaying.not).or(beingStopped), {
			var actualNumChannels, playSynthSymbol;

			if(rate == \control, { "AlgaNode: cannot play a kr node".error; ^nil; });

			if(channelsToPlay != nil, {
				//store it so it's kept across replaces, unless a new one is specified
				playChans = channelsToPlay
			}, {
				//If nil and replace, use the one stored
				if(replace, {
					channelsToPlay = playChans
				});
			});

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

				playSynthSymbol = ("alga_play_" ++ numChannels ++ "_" ++ actualNumChannels).asSymbol;

				playSynth = AlgaSynth(
					playSynthSymbol,
					[\in, synthBus.busArg, \indices, channelsToPlay, \gate, 1, \fadeTime, time],
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

				playSynthSymbol = ("alga_play_" ++ numChannels ++ "_" ++ actualNumChannels).asSymbol;

				playSynth = AlgaSynth(
					playSynthSymbol,
					[\in, synthBus.busArg, \gate, 1, \fadeTime, time],
					playGroup,
					waitForInst:false
				);
			});

			isPlaying = true;
			beingStopped = false;
		}, {
			"AlgaNode: node is already playing.".warn;
		})
	}

	freePlaySynth { | time, isClear |
		if(isPlaying, {
			if(isClear.not, {
				//time has already been calculated if isClear == true
				time = this.calculateTemporaryLongestWaitTime(time, playTime);
			});
			playSynth.set(\gate, 0, \fadeTime, time);
			isPlaying = false;
			beingStopped = true;
		})
	}

	playInner { | time, channelsToPlay, sched, replace = false |
		//Check only for synthBus, it makes more sense than also checking for synth.algaIstantiated,
		//As it allows meanwhile to create the play synth while synth is getting instantiated
		scheduler.addAction(
			condition: { synthBus != nil },
			func: { this.createPlaySynth(time, channelsToPlay, replace) },
			sched: sched
		);

	}

	play { | time, chans, sched |
		this.playInner(time, chans, sched);
	}

	stopInner { | time, sched, isClear = false |
		if(isClear, {
			//Already in a scheduled action
			this.freePlaySynth(time, true);
		}, {
			scheduler.addAction(
				condition: { this.isPlaying },
				func: { this.freePlaySynth(time, false); },
				sched: sched
			);
		});
	}

	stop { | time, sched |
		this.stopInner(time, sched);
	}

	//Global init: all interp synths and synth are correct
	algaInstantiated {
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
		^((synth.algaInstantiated).and(synthBus != nil));
	}

	//To receive signals, and perform interpolation, the specific interpSynth(s)
	//is needed to be surely insantiated
	algaInstantiatedAsReceiver { | param = \in, sender, mix = false |
		var interpSynthsAtParam = interpSynths[param];
		var inNodesAtParam = inNodes[param];

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

	isAlgaNode { ^true }

	clock {
		^(scheduler.clock)
	}
}

//Alias
AN : AlgaNode {}

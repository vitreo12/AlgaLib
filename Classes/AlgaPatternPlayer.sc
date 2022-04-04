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

//Play and dispatch streams to registered AlgaPatterns
AlgaPatternPlayer {
	var <pattern, <patternAsStream, <algaReschedulingEventStreamPlayer;
	var actionScheduler, parser;
	var <timeInner = 0, <schedInner = 1;
	var <schedResync = 1;
	var <durInterpResync = true;
	var <durInterpReset = false;
	var <tempoScaling = false;

	var <entries;
	var <results;
	var <algaPatterns;
	var <algaPatternEntries;
	var <algaPatternsPrevFunc;
	var <server;

	var <beingStopped = false;
	var <schedInSeconds = false;

	var <dur = 1, <durAlgaPseg;
	var <stretch = 1, <stretchAlgaPseg;
	var <manualDur = false;

	*initClass {
		StartUp.add({ this.addAlgaPatternPlayerEventType });
	}

	*addAlgaPatternPlayerEventType {
		Event.addEventType(\algaPatternPlayer, #{
			var algaPatternPlayer = ~algaPatternPlayer;
			var entries = algaPatternPlayer.entries;
			var results = algaPatternPlayer.results;
			var algaPatterns = algaPatternPlayer.algaPatterns;

			//scheduledStepActionsPre
			algaPatternPlayer.advanceAndConsumeScheduledStepActions(false);

			//Advance entries and results' pointers
			if(algaPatternPlayer.beingStopped.not, {
				entries.keysValuesDo({ | key, value |
					//For interpolation, value can be IdentityDictionary(UniqueID -> entry)
					if(key != \dur, {
						value[\entries].keysValuesDo({ | uniqueID, entry |
							//Advance patterns
							entry = entry.next(currentEnvironment);
							entry.algaAdvance(currentEnvironment);

							//Assign results
							results[key][uniqueID] = entry;
						});
					});
				});

				//Dispatch children's triggering
				algaPatterns.do({ | algaPattern | algaPattern.advance });
			});

			//scheduledStepActionsPost
			algaPatternPlayer.advanceAndConsumeScheduledStepActions(true);
		});
	}

	*new { | def, server |
		^super.new.init(def, server)
	}

	init { | argDef, argServer |
		var scheduler;
		var patternPairs = Array.newClear;
		var patternPairsDict = IdentityDictionary();
		var foundDurOrDelta = false;
		var functionSynthDefDict = IdentityDictionary(); //AlgaTemp parser needs this
		manualDur = false;

		//Get scheduler
		server = argServer ? Server.default;
		scheduler = Alga.getScheduler(server);
		if(scheduler == nil, {
			(
				"AlgaPatternPlayer: can't retrieve a valid AlgaScheduler for server '" ++
				server.name ++
				"'. Has Alga.boot been called on it?"
			).error;
			^nil;
		});

		//Create AlgaActionScheduler
		actionScheduler = AlgaActionScheduler(this, scheduler);

		//Create AlgaParser
		parser = AlgaParser(this);

		//Create vars
		results = IdentityDictionary();
		entries = IdentityDictionary();
		algaPatterns = IdentitySet();
		algaPatternEntries = IdentityDictionary();

		//1) Add support for arrays to keep ordering of execution of params!

		//Event handling
		if(argDef.isEvent, {
			patternPairs = patternPairs.add(\type).add(\algaPatternPlayer);
			patternPairs = patternPairs.add(\algaPatternPlayer).add(this);
			argDef.keysValuesDo({ | key, entry |
				var isDurOrStretch = false;
				case
				{ (key == \dur).or(key == \delta) } {
					if((entry.isSymbol).or(entry.isNil), { manualDur = true });
					foundDurOrDelta = true;
					isDurOrStretch  = true;
					dur = entry.algaAsStream;
				}
				{ key == \stretch } {
					isDurOrStretch = true;
					stretch = entry.algaAsStream;
				};

				//All other cases
				if(isDurOrStretch.not, {
					var uniqueID = UniqueID.next;
					//Parse entry for AlgaTemps
					entry = this.parseParam(entry, functionSynthDefDict);
					results[key] = IdentityDictionary();
					entries[key] = IdentityDictionary();
					entries[key][\lastID] = uniqueID;
					entries[key][\entries] = IdentityDictionary();
					entries[key][\entries][uniqueID] = entry.algaAsStream; //.next support
					patternPairsDict[key] = entry;
				});
			});
		}, {
			("AlgaPatternPlayer: Invalid 'def': " ++ argDef.class.asString).error;
			^nil;
		});

		//Add reschedulable \dur
		if(foundDurOrDelta, {
			if(manualDur.not, {
				patternPairsDict[\dur] = Pfunc { | e | dur.next(e) }
			});
		});

		//Add reschedulable \stretch
		patternPairsDict[\stretch] = Pfunc { | e | stretch.next(e) };

		//Order pattern pairs dict alphabetically and convert to array.
		//This allows the user to use Pfunc { | e | } functions with any
		//scalar OR generic parameter, as long as they're ordered alphabetically
		if(patternPairsDict.size > 0, {
			var order = patternPairsDict.order;
			var entries = patternPairsDict.atAll(order);
			var array = ([order] ++ [entries]).lace(order.size * 2);
			patternPairs = patternPairs ++ array;
		});

		//Finally, only activate the pattern if all AlgaTemps are compiled
		this.compileFunctionSynthDefDictIfNeeded(
			func: {
				pattern = Pbind(*patternPairs);
				patternAsStream = pattern.algaAsStream; //Needed for things like dur: \none
			},
			functionSynthDefDict: functionSynthDefDict
		)
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

	//Iterate through all scheduledStepActions and execute them accordingly
	advanceAndConsumeScheduledStepActions { | post = false |
		actionScheduler.advanceAndConsumeScheduledStepActions(post)
	}

	//If needed, it will compile the AlgaSynthDefs in functionSynthDefDict and wait before executing func.
	//Otherwise, it will just execute func
	compileFunctionSynthDefDictIfNeeded { | func, functionSynthDefDict |
		^actionScheduler.compileFunctionSynthDefDictIfNeeded(func, functionSynthDefDict)
	}

	//Parse an entry
	parseParam { | value, functionSynthDefDict |
		^parser.parseParam(value, functionSynthDefDict)
	}

	sched { ^schedInner }

	sched_ { | value |
		if((value.isNumber.or(value.isAlgaStep)).not, {
			"AlgaPatternPlayer: 'sched' can only be a Number or AlgaStep".error;
			^this;
		});
		schedInner = value
	}

	time { ^timeInner }

	time_ { | value |
		if(value.isNumber.not, {
			"AlgaPatternPlayer: 'time' can only be a Number".error;
			^this;
		});
		timeInner = value
	}

	schedInSeconds_ { | value |
		if(value.isKindOf(Boolean).not, {
			"AlgaPatternPlayer: 'schedInSeconds' only supports boolean values. Setting it to false".error;
			value = false;
		});
		schedInSeconds = value
	}

	//Set schedResync
	schedResync_ { | value = 1 |
		if(value.isNumber.not, {
			"AlgaPattern: 'schedResync' only supports numbers. Setting it to 1".error;
			value = 1;
		});
		schedResync = value
	}

	//Set durInterpResync
	durInterpResync_ { | value = true |
		if(value.isKindOf(Boolean).not, {
			"AlgaPatternPlayer: 'durInterpResync' only supports boolean values. Setting it to true".error;
			value = true;
		});
		durInterpResync = value
	}

	//Set durInterpReset
	durInterpReset_ { | value = false |
		if(value.isKindOf(Boolean).not, {
			"AlgaPatternPlayer: 'durInterpReset' only supports boolean values. Setting it to false".error;
			value = false;
		});
		durInterpReset = value
	}

	//Set tempoScaling
	tempoScaling_ { | value |
		if(value.isKindOf(Boolean).not, {
			"AlgaPatternPlayer: 'tempoScaling' only supports boolean values. Setting it to false".error;
			value = false;
		});
		tempoScaling = value
	}

	//Run the pattern
	play { | sched = 1 |
		if(manualDur.not, {
			sched = sched ? schedInner;
			sched = sched ? 0;
			this.addAction(
				condition: { pattern != nil },
				func: {
					beingStopped = false;
					algaReschedulingEventStreamPlayer = pattern.playAlgaRescheduling(
						clock: this.clock
					);
				},
				sched: sched
			);
		});
	}

	//Stop the pattern
	stop { | sched = 1 |
		sched = sched ? schedInner;
		sched = sched ? 0;
		if(sched.isAlgaStep, {
			if(algaReschedulingEventStreamPlayer != nil, {
				var algaReschedulingEventStreamPlayerLock = algaReschedulingEventStreamPlayer;
				this.addAction(
					func: {
						if(sched.post.not, { beingStopped = true });
						algaReschedulingEventStreamPlayerLock.stop
					},
					sched: sched
				)
			});
		}, {
			if(algaReschedulingEventStreamPlayer != nil, {
				algaReschedulingEventStreamPlayer.stopAtTopPriority(sched)
			});
		});
	}

	//Manually advance the pattern. 'next' as function name won't work as it's reserved, apparently
	advance { | sched = 0 |
		sched = sched ? schedInner;
		sched = sched ? 0;
		if(patternAsStream != nil, {
			//If sched is 0, go right away: user might have its own scheduling setup
			if(sched == 0, {
				//Empty event as protoEvent!
				patternAsStream.next(()).play;
			}, {
				this.addAction(
					//Empty event as protoEvent!
					func: { patternAsStream.next(()).play },
					sched: sched
				);
			});
		});
	}

	//Alias of advance
	step { | sched = 0 | this.advance(sched) }

	//Add an AlgaPattern
	addAlgaPattern { | algaPattern |
		if(algaPattern.isAlgaPattern, { algaPatterns.add(algaPattern) });
	}

	//Remove an AlgaPattern and reset its .player. Works with scheduling
	removeAlgaPattern { | algaPattern, sched |
		sched = sched ? schedInner;
		sched = sched ? 0;
		algaPattern.player = nil;
		this.addAction(
			func: { algaPatterns.remove(algaPattern) },
			sched: sched
		)
	}

	//Add an AlgaPattern that has at least one AlgaReaderPfunc at param
	addAlgaPatternEntry { | algaPattern, algaPatternParam |
		algaPatternEntries[algaPattern] = algaPatternEntries[algaPattern] ? IdentitySet();
		algaPatternEntries[algaPattern].add(algaPatternParam);
	}

	//Remove an AlgaPattern that has at least one AlgaReaderPfunc at param
	removeAlgaPatternEntry { | algaPattern, algaPatternParam |
		var paramsAtAlgaPattern = algaPatternEntries[algaPattern];
		if(paramsAtAlgaPattern != nil, {
			paramsAtAlgaPattern.remove(algaPatternParam);
			if(paramsAtAlgaPattern.size == 0, {
				algaPatternEntries.removeAt(algaPattern)
			});
		});
	}

	//Wrap result in AlgaReader
	at { | param, repeats = inf |
		var result;

		//Lock ID (needs to be out of Pfunc: locked on creation)
		var id = entries[param][\lastID];
		if(id == nil, {
			("AlgaPattern: undefined parameter in AlgaPatternPlayer: '" ++ param ++ "'").error;
		});

		//Create the AlgaReaderPfunc, wrapping the indexing of the result
		result = AlgaReaderPfunc({
			AlgaReader(results[param][id]);
		}, repeats ? inf);

		//Assign patternPlayer
		result.patternPlayer = this;
		result.keyOrFunc = param;
		result.params = [ param ]; //it expects Array later on

		//Return
		^result;
	}

	//Execute func and wrap result in AlgaReader
	read { | func, repeats = inf |
		var argNames, argVals, retriever;
		var ids, result;

		//Must be Function
		if(func.isFunction.not, {
			"AlgaPatternPlayer: 'func' must be a Function.".error;
			^nil
		});

		//Retrieve argNames. Use them to index the results
		argNames = func.def.argNames;
		ids = Array.newClear(argNames.size);

		//Lock IDs (needs to be out of Pfunc: locked on creation)
		argNames.do({ | argName, i |
			var id = entries[argName][\lastID];
			if(id == nil, {
				("AlgaPattern: undefined parameter in AlgaPatternPlayer: '" ++ argName ++ "'").error;
			});
			ids[i] = id;
		});

		//Assign the results to key -> result pairs
		retriever = {
			var returnPairs = ();
			argNames.do({ | argName, i |
				var id = ids[i];
				var result = results[argName][id];
				returnPairs[argName] = result;
			});
			returnPairs;
		};

		//Create the AlgaReaderPfunc, wrapping func execution
		result = AlgaReaderPfunc({
			AlgaReader(func.performWithEnvir(\value, retriever.value))
		}, repeats ? inf);

		//Assign patternPlayer / function
		result.patternPlayer = this;
		result.keyOrFunc = func;
		result.params = argNames;

		//Return
		^result;
	}

	//Alias of read
	value { | func | ^this.read(func) }

	//Set dur at specified sched, rescheduling the player
	setDurAtSched { | value, sched, isReset = false, isStretch = false |
		//Check sched
		sched = sched ? schedInner;
		algaReschedulingEventStreamPlayer.rescheduleAtQuant(sched, {
			if(isStretch.not, {
				// \dur
				if(durAlgaPseg.isAlgaPseg, { durAlgaPseg.stop });
				if(stretchAlgaPseg.isAlgaPseg, { stretchAlgaPseg.extStop });
			}, {
				// \stretch
				if(durAlgaPseg.isAlgaPseg, { durAlgaPseg.extStop });
				if(stretchAlgaPseg.isAlgaPseg, { stretchAlgaPseg.stop });
			});
			if(isReset, { this.setDur(value) });
		});
	}

	//Set stretch at specified sched, rescheduling the player
	setStretchAtSched { | value, sched, isReset = false |
		//Check sched
		sched = sched ? schedInner;
		algaReschedulingEventStreamPlayer.rescheduleAtQuant(sched, {
			if(isReset, { stretch = value.algaAsStream })
		});
	}

	//Like AlgaPattern: retriggers at specific sched
	interpolateDur { | value, time, shape, resync, reset, sched |
		if(sched.isAlgaStep, {
			//sched == AlgaStep
			this.addAction(
				func: {
					beingStopped = true;
					algaReschedulingEventStreamPlayer.rescheduleAtQuant(0, {
						dur = value.algaAsStream;
						beingStopped = false;
					});
				},
				sched: sched
			)
		}, {
			//time == 0: just reschedule at sched
			if(time == 0, {
				this.setDurAtSched(value, sched)
			}, {
				this.interpolateDurParamAtSched(\dur, value, time, shape, resync, reset, sched)
			});
		});
	}

	//Alias
	interpDur { | value, time, shape, resync, reset, sched |
		this.interpolateDur(value, time, shape, resync, reset, sched)
	}

	//Alias
	interpolateDelta { | value, time, shape, resync, reset, sched |
		this.interpolateDur(value, time, shape, resync, reset, sched)
	}

	//Alias
	interpDelta { | value, time, shape, resync, reset, sched |
		this.interpolateDur(value, time, shape, resync, reset, sched)
	}

	//Like AlgaPattern: retriggers at specific sched
	interpolateStretch { | value, time, shape, resync, reset, sched |
		if(sched.isAlgaStep, {
			//sched == AlgaStep
			this.addAction(
				func: {
					beingStopped = true;
					algaReschedulingEventStreamPlayer.rescheduleAtQuant(0, {
						stretch = value.algaAsStream;
						beingStopped = false;
					});
				},
				sched: sched
			)
		}, {
			//time == 0: just reschedule at sched
			if(time == 0, {
				this.setStretchAtSched(value, sched)
			}, {
				this.interpolateDurParamAtSched(\stretch, value, time, shape, resync, reset, sched)
			});
		});
	}

	//Alias
	interpStretch { | value, time, shape, resync, reset, sched |
		this.interpolateStretch(value, time, shape, resync, reset, sched)
	}

	//Interpolate a dur param
	interpolateDurParamAtSched { | param, value, time, shape, resync, reset, sched |
		var algaPseg;

		//Check validity of value
		if((value.isNumberOrArray.not).and(value.isPattern.not), {
			"AlgaPatternPlayer: only Numbers, Arrays and Patterns are supported for 'dur' interpolation".error;
			^this
		});

		//Dispatch: locked on function call, not on addAction
		case
		{ param == \dur }     { algaPseg = durAlgaPseg }
		{ param == \stretch } { algaPseg = stretchAlgaPseg };

		//Check time
		time = time ? timeInner;

		//Time in AlgaPseg is in beats: it needs to be scaled to seconds
		time = if(tempoScaling.not, { time * this.clock.tempo });

		//If time is still 0, go to setAtSched
		if(time == 0, {
			case
			{ param == \dur }     {
				^this.setDurAtSched(value, sched)
			}
			{ param == \stretch } {
				^this.setStretchAtSched(value, sched)
			};
		});

		//Check sched
		sched = sched ? schedInner;

		//Check resync
		resync = resync ? durInterpResync;

		//Check reset
		reset = reset ? durInterpReset;

		//Get shape
		shape = shape.algaCheckValidEnv(false) ? Env([0, 1], 1);

		//Add to scheduler
		this.addAction(
			condition: { this.algaInstantiated },
			func: {
				var newAlgaPseg;

				//Stop previous one. \dur and \stretch stop each other too
				if(algaPseg.isAlgaPseg, { algaPseg.stop });
				case
				{ param == \dur } {
					if(stretchAlgaPseg.isAlgaPseg, { stretchAlgaPseg.extStop });
				}
				{ param == \stretch } {
					if(durAlgaPseg.isAlgaPseg, { durAlgaPseg.extStop });
				};

				//Create new one
				newAlgaPseg = shape.asAlgaPseg(
					time: time,
					clock: this.clock,
					onDone: {
						if(resync, {
							this.resync(value, reset, if(param == \stretch, { true }, { false }))
						})
					}
				);

				//Start the new one
				newAlgaPseg.start;

				//Perform interpolation
				case
				{ param == \dur } {
					durAlgaPseg = newAlgaPseg;
					dur = dur.blend(
						value,
						durAlgaPseg
					).algaAsStream;
				}
				{ param == \stretch } {
					stretchAlgaPseg = newAlgaPseg;
					stretch = stretch.blend(
						value,
						stretchAlgaPseg
					).algaAsStream;
				};
			},
			sched: sched,
			topPriority: true
		)
	}

	//Resync pattern to sched
	resync { | value, reset, resetStretch, sched |
		sched = sched ? schedResync; //Check for schedResync
		resetStretch = resetStretch ? false;
		this.setDurAtSched(value, sched, reset, resetStretch);
	}

	//Re-assign correctly
	reassignAlgaReaderPfunc { | algaReaderPfunc, algaPatternPlayerParam |
		var params = algaReaderPfunc.params;
		if(params.includes(algaPatternPlayerParam), {
			var keyOrFunc = algaReaderPfunc.keyOrFunc;
			var repeats = algaReaderPfunc.repeats;

			//Temporarily .next the stream so that results[] have a valid value ASAP...
			//Should this be done without copy?
			var lastID = entries[algaPatternPlayerParam][\lastID];
			var tempEntryStream = entries[algaPatternPlayerParam][\entries][lastID].deepCopy;
			results[algaPatternPlayerParam][lastID] = tempEntryStream.next(currentEnvironment);

			//Re-build the AlgaReaderPfunc
			case
			{ keyOrFunc.isSymbol } { ^this.at(keyOrFunc, repeats); }
			{ keyOrFunc.isFunction } { ^this.read(keyOrFunc, repeats) };
		});
		^algaReaderPfunc //fallback
	}

	//Go through an AlgaTemp looking for things to re-assing to let
	//AlgaReaderPfunc work correctly
	reassignAlgaTemp { | algaTemp, algaPatternPlayerParam |
		var def = algaTemp.def;
		if(def.isEvent, {
			def.keysValuesDo({ | key, entry |
				def[key] = this.reassignAlgaReaderPfuncs(entry, algaPatternPlayerParam)
			});
		});
		^algaTemp;
	}

	//Go through AlgaTemp / ListPattern / FilterPattern looking for things
	//to re-assing to let AlgaReaderPfunc work correctly
	reassignAlgaReaderPfuncs { | value, algaPatternPlayerParam |
		var isAlgaReaderPfuncOrAlgaTemp = false;

		case
		{ value.isAlgaReaderPfunc } {
			value = this.reassignAlgaReaderPfunc(value, algaPatternPlayerParam);
			isAlgaReaderPfuncOrAlgaTemp = true;
		}
		{ value.isAlgaTemp } {
			value = this.reassignAlgaTemp(value, algaPatternPlayerParam);
			isAlgaReaderPfuncOrAlgaTemp = true;
		};

		//Pattern
		if(isAlgaReaderPfuncOrAlgaTemp.not, {
			parser.parseGenericObject(value,
				func: { | val |
					this.reassignAlgaReaderPfuncs(val, algaPatternPlayerParam)
				},
				replace: false
			)
		});

		^value;
	}

	//Special case: FX is an Event
	reassignFXEvent { | fxEvent, algaPatternPlayerParam |
		if(fxEvent.isEvent, {
			fxEvent.keysValuesDo({ | key, value |
				if(key != \def, {
					fxEvent[key] = this.reassignAlgaReaderPfuncs(value, algaPatternPlayerParam)
				});
			});
		});
		^fxEvent
	}

	//Implement from {}
	fromInner { | sender, param = \in, time, shape, sched |
		//New ID
		var uniqueID = UniqueID.next;
		var lastID = entries[param][\lastID];

		//Create new entries / results if needed
		if(entries[param] == nil, {
			entries[param] = IdentityDictionary();
			entries[param][\entries] = IdentityDictionary();
			results[param] = IdentityDictionary();
		});

		//New ID - sender
		entries[param][\lastID] = uniqueID;
		entries[param][\entries][uniqueID] = sender.algaAsStream;

		//Get shape
		shape = shape.algaCheckValidEnv ? Env([0, 1], 1);

		//Re-trigger interpolation on connected AlgaPattern entries. Note the use of sched
		algaPatternEntries.keysValuesDo({ | algaPattern, algaPatternParams |
			var algaPatternPlayers = algaPattern.players;
			algaPatternParams.do({ | algaPatternParam |
				var algaPatternPlayerParamsAtParam = algaPatternPlayers[algaPatternParam][this];
				if(algaPatternPlayerParamsAtParam != nil, {
					//Find match with algaPatternPlayers
					if(algaPatternPlayerParamsAtParam.includes(param), {
						//Find the current entry plugged in the AlgaPattern at param
						var algaPatternEventEntryAtParam = algaPattern.defPreParsing[algaPatternParam].copy;
						if(algaPatternEventEntryAtParam != nil, {
							var reassignedEntry;
							if(algaPatternParam == \fx, {
								//Special case: \fx
								reassignedEntry = this.reassignFXEvent(
									fxEvent: algaPatternEventEntryAtParam,
									algaPatternPlayerParam: param
								)
							}, {
								//Re-assign the AlgaReaderPfuncs
								reassignedEntry = this.reassignAlgaReaderPfuncs(
									value: algaPatternEventEntryAtParam,
									algaPatternPlayerParam: param
								);
							});

							//Re-trigger from { }
							algaPattern.from(
								sender: reassignedEntry,
								param: algaPatternParam,
								time: time,
								shape: shape,
								sched: sched
							)
						});
					});
				});
			});
		});

		//Free old lastID stuff after time + 1 (for certainty)
		this.addAction(
			func: {
				if(lastID != nil, {
					fork {
						(time + 1).wait;
						entries[param].removeAt(lastID);
						entries[param][\entries].removeAt(lastID);
					}
				});
			},
			sched: sched
		)
	}

	//This will also trigger interpolation on all registered AlgaPatterns
	from { | sender, param = \in, time, shape, sched |
		//Parse the sender looking for AlgaTemps
		var isDurOrStretch = false;
		var senderAndFunctionSynthDefDict = this.parseParam(sender);
		var functionSynthDefDict;

		//Unpack
		sender = senderAndFunctionSynthDefDict[0];
		functionSynthDefDict = senderAndFunctionSynthDefDict[1];

		//Set time / sched accordingly
		time = time ? timeInner;
		time = time ? 0;
		sched = sched ? schedInner;
		sched = sched ? 0;

		// \dur / \stretch
		case
		{ (param == \dur).or(param == \delta) } {
			this.interpolateDur(
				value: sender, time: time, shape: shape, sched: sched
			);
			isDurOrStretch = true;
		}
		{ param == \stretch } {
			this.interpolateStretch(
				value: sender, time: time, shape: shape, sched: sched
			);
			isDurOrStretch = true;
		};

		//Other params
		if(isDurOrStretch.not, {
			//If needed, it will compile the AlgaSynthDefs of AlgaTemps.
			//Otherwise, it will just execute func
			this.compileFunctionSynthDefDictIfNeeded(
				func: {
					this.fromInner(
						sender: sender,
						param: param,
						time: time,
						shape: shape,
						sched: sched
					)
				},
				functionSynthDefDict: functionSynthDefDict
			)
		});
	}

	<< { | sender, param = \in |
		this.from(sender, param)
	}

	clock {
		^(actionScheduler.scheduler.clock)
	}

	scheduler {
		^(actionScheduler.scheduler)
	}

	isAlgaPatternPlayer { ^true }
}

//Alias
AlgaPlayer : AlgaPatternPlayer {}

//Alias
APP : AlgaPatternPlayer {}

//Used under the hood in AlgaPattern to read from an AlgaPatternPlayer
AlgaReader {
	var <entry;

	*new { | entry |
		^super.newCopyArgs(entry)
	}

	isAlgaReader { ^true }
}

//Allows to pass parameters from AlgaPatternPlayer to AlgaPattern via .read / .at
AlgaReaderPfunc : Pfuncn {
	var <>patternPlayer, <>params, <>keyOrFunc;

	isAlgaReaderPfunc { ^true }
}
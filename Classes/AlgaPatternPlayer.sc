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
	var <timeInner = 0, <schedInner = 1, <shapeInner;
	var <schedResync = 1;
	var <durInterpResync = true;
	var <durInterpReset = false;
	var <tempoScaling = false;

	var <entriesOrder;
	var <entries;
	var <algaNextSchedTimes;
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

	var <>player;

	*initClass {
		StartUp.add({ this.addAlgaPatternPlayerEventType });
	}

	*addAlgaPatternPlayerEventType {
		Event.addEventType(\algaPatternPlayer, #{
			var algaPatternPlayer = ~algaPatternPlayer;
			var entries = algaPatternPlayer.entries;
			var entriesOrder = algaPatternPlayer.entriesOrder;
			var results = algaPatternPlayer.results;
			var algaNextSchedTimes = algaPatternPlayer.algaNextSchedTimes;
			var algaPatterns = algaPatternPlayer.algaPatterns;
			var beats = algaPatternPlayer.clock.beats;

			//scheduledStepActionsPre
			algaPatternPlayer.advanceAndConsumeScheduledStepActions(false);

			//Advance entries and results' pointers
			if(algaPatternPlayer.beingStopped.not, {
				entriesOrder.do({ | key |
					var value = entries[key];
					//For interpolation, value can be IdentityDictionary(UniqueID -> entry)
					if(key != \dur, {
						value[\entries].keysValuesDo({ | uniqueID, entry |
							//Check for the presence of an already .algaNext state at current clock time
							if(algaNextSchedTimes[key][uniqueID] != beats, {
								//Advance patterns
								var entryVal = entry.algaNext(currentEnvironment);
								entryVal.algaAdvance(currentEnvironment);

								//Store value for Pfunc / Pkey retrieval
								//However, this doesn't work when triggering interpolation:
								//it will only consider the lastID one
								if(entryVal.isEvent.not, {
									if(uniqueID == value[\lastID], {
										currentEnvironment[key] = entryVal
									});
								});

								//Assign results
								results[key][uniqueID] = entryVal;
							});

							//algaNextSchedTimes is assigned in reassignAlgaReaderPfunc
							//Here it needs to be cleared (already consumed)
							if(algaNextSchedTimes[key] != nil, {
								algaNextSchedTimes[key].removeAt(uniqueID)
							});
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

	*new { | def, player, server |
		^super.new.init(def, player, server)
	}

	init { | argDef, argPlayer, argServer |
		var scheduler;
		var patternPairs = Array.newClear;
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

		//Assign player
		player = argPlayer;

		//Create AlgaActionScheduler
		actionScheduler = AlgaActionScheduler(this, scheduler);

		//Create AlgaParser
		parser = AlgaParser(this);

		//Create vars
		results = IdentityDictionary();
		entries = IdentityDictionary();
		algaNextSchedTimes = IdentityDictionary();
		entriesOrder = Array.newClear();
		algaPatterns = IdentitySet();
		algaPatternEntries = IdentityDictionary();

		//1) Add support for alphabetical retrieval of parameters with Pkey / Pfunc

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
					entries[key][\entries][uniqueID] = entry.algaAsStream; //.algaNext support
				});

				//Add to entriesOrder
				entriesOrder = entriesOrder.add(key);
			});
		}, {
			("AlgaPatternPlayer: Invalid 'def': " ++ argDef.class.asString).error;
			^nil;
		});

		//If player is AlgaPatternPlayer, dur is ALWAYS manual
		if(player.isAlgaPatternPlayer, {
			player.addAlgaPattern(this);
			manualDur = true;
		});

		//Add reschedulable \dur
		if(foundDurOrDelta, {
			if(manualDur.not, {
				patternPairs = patternPairs.add(\dur).add(Pfunc { | e | dur.algaNext(e) });
			});
		});

		//Add reschedulable \stretch
		patternPairs = patternPairs.add(\stretch).add(Pfunc { | e | stretch.algaNext(e) });

		//Order entries alphabetically
		entriesOrder = entriesOrder[entriesOrder.order];

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
	addAction { | condition, func, sched = 0, topPriority = false, preCheck = true |
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

	interpTime { ^this.time }

	interpTime_ { | value |
		this.time_(value)
	}

	it { ^this.time }

	it_ { | value |
		this.time_(value)
	}

	shape { ^shapeInner }

	shape_ { | value |
		if(value.isKindOf(Env).not, {
			"AlgaPatternPlayer: 'shape' can only be an Env".error;
			^this;
		});
		shapeInner = value
	}

	interpShape { ^this.shape }

	interpShape_ { | value |
		this.shape_(value)
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

	//Exec function when interpStreams are valid
	runFuncOnValidPatternAsStream { | func, sched = 0 |
		this.addAction(
			condition: {
				patternAsStream != nil
			},
			func: {
				func.value(patternAsStream, algaReschedulingEventStreamPlayer, sched)
			},
			preCheck: true
		)
	}

	//Run the pattern
	play { | sched = 1 |
		var func = { | patternAsStreamArg, algaReschedulingEventStreamPlayerArg, schedArg |
			if(manualDur.not, {
				this.addAction(
					func: {
						beingStopped = false;
						algaReschedulingEventStreamPlayer =
						patternAsStreamArg.newAlgaReschedulingEventStreamPlayer.play(
							this.clock,
							false
						)
					},
					sched: schedArg
				);
			});
		};

		sched = sched ? schedInner;
		sched = sched ? 0;

		this.runFuncOnValidPatternAsStream(func, sched);
	}

	//Stop the pattern
	stop { | sched = 0 |
		var func = { | patternAsStreamArg, algaReschedulingEventStreamPlayerArg, schedArg |
			if(schedArg.isAlgaStep, {
				if(algaReschedulingEventStreamPlayerArg != nil, {
					this.addAction(
						func: {
							if(schedArg.post.not, { beingStopped = true });
							algaReschedulingEventStreamPlayerArg.stop
						},
						sched: schedArg
					)
				});
			}, {
				if(algaReschedulingEventStreamPlayerArg != nil, {
					algaReschedulingEventStreamPlayerArg.stopAtTopPriority(
						schedArg,
						this.clock
					)
				});
			});
		};

		sched = sched ? schedInner;
		sched = sched ? 0;

		this.runFuncOnValidPatternAsStream(func, sched);
	}

	//Restart the pattern
	restart { | sched = 0 |
		var func = { | patternAsStreamArg, algaReschedulingEventStreamPlayerArg, schedArg |
			if(schedArg.isAlgaStep, {
				this.addAction(
					func: {
						if(algaReschedulingEventStreamPlayerArg != nil, {
							if(schedArg.post.not, {
								beingStopped = true
							});
							algaReschedulingEventStreamPlayerArg.rescheduleAtQuant(
								quant: 0,
								func: {
									this.resetEntries;
									beingStopped = false;
								},
								clock: this.clock
							);
						});
					},
					sched: schedArg
				)
			}, {
				if(algaReschedulingEventStreamPlayerArg != nil, {
					algaReschedulingEventStreamPlayerArg.rescheduleAtQuant(
						quant: schedArg,
						func: { this.resetEntries },
						clock: this.clock
					);
				});
			});
		};

		sched = sched ? schedInner;
		sched = sched ? 0;

		this.runFuncOnValidPatternAsStream(func, sched);
	}

	//Reset the pattern
	reset { | sched = 0 |
		var func = { | patternAsStreamArg, algaReschedulingEventStreamPlayerArg, schedArg |
			this.addAction(
				func: {
					if(algaReschedulingEventStreamPlayerArg != nil, {
						this.resetEntries
					});
				},
				sched: schedArg
			)
		};

		sched = sched ? schedInner;
		sched = sched ? 0;

		this.runFuncOnValidPatternAsStream(func, sched);
	}

	//Reset all entries
	resetEntries {
		entries.do({ | value |
			value[\entries].do({ | entry |
				entry.reset;
			});
		});

		dur.reset;
		stretch.reset;
	}

	//Manually advance the pattern. 'next' as function name won't work as it's reserved, apparently
	advance { | sched = 0 |
		sched = sched ? schedInner;
		sched = sched ? 0;
		if(patternAsStream != nil, {
			//If sched is 0, go right away: user might have its own scheduling setup
			if(sched == 0, {
				//Empty event as protoEvent!
				patternAsStream.algaNext(()).play;
			}, {
				this.addAction(
					//Empty event as protoEvent!
					func: { patternAsStream.algaNext(()).play },
					sched: sched
				);
			});
		});
	}

	//Alias of advance
	step { | sched = 0 | this.advance(sched) }

	//Add an AlgaPattern
	addAlgaPattern { | algaPattern |
		algaPatterns.add(algaPattern);
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
		shape = shape.algaCheckValidEnv(false, server) ? Env([0, 1], 1);

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
	reassignAlgaReaderPfunc { | algaReaderPfunc, algaPatternPlayerParam, sched = 0 |
		var params = algaReaderPfunc.params;
		if(params.includes(algaPatternPlayerParam), {
			var keyOrFunc = algaReaderPfunc.keyOrFunc;
			var repeats = algaReaderPfunc.repeats;

			//Temporarily .algaNext the stream so that results[] have a valid value ASAP
			var lastID = entries[algaPatternPlayerParam][\lastID];
			var entryStream = entries[algaPatternPlayerParam][\entries][lastID];
			results[algaPatternPlayerParam][lastID] = entryStream.algaNext(currentEnvironment);

			//Assign algaNextSchedTimes. This gets cleared in the Event type. This avoids duplications of triggers
			algaNextSchedTimes[algaPatternPlayerParam] = algaNextSchedTimes[algaPatternPlayerParam] ? IdentityDictionary();
			if(schedInSeconds, {
				algaNextSchedTimes[algaPatternPlayerParam][lastID] = this.clock.algaGetScheduledTimeInSeconds(sched)
			}, {
				algaNextSchedTimes[algaPatternPlayerParam][lastID] = this.clock.algaGetScheduledTime(sched)
			});

			//Re-build the AlgaReaderPfunc
			case
			{ keyOrFunc.isSymbol } { ^this.at(keyOrFunc, repeats); }
			{ keyOrFunc.isFunction } { ^this.read(keyOrFunc, repeats) };
		});
		^algaReaderPfunc //fallback
	}

	//Go through an AlgaTemp looking for things to re-assing to let
	//AlgaReaderPfunc work correctly
	reassignAlgaTemp { | algaTemp, algaPatternPlayerParam, sched = 0 |
		var def = algaTemp.def;
		if(def.isEvent, {
			def.keysValuesDo({ | key, entry |
				def[key] = this.reassignAlgaReaderPfuncs(entry, algaPatternPlayerParam, sched)
			});
		});
		^algaTemp;
	}

	//Go through AlgaTemp / ListPattern / FilterPattern looking for things
	//to re-assing to let AlgaReaderPfunc work correctly
	reassignAlgaReaderPfuncs { | value, algaPatternPlayerParam, sched = 0 |
		var isAlgaReaderPfuncOrAlgaTemp = false;

		case
		{ value.isAlgaReaderPfunc } {
			value = this.reassignAlgaReaderPfunc(value, algaPatternPlayerParam, sched);
			isAlgaReaderPfuncOrAlgaTemp = true;
		}
		{ value.isAlgaTemp } {
			value = this.reassignAlgaTemp(value, algaPatternPlayerParam, sched);
			isAlgaReaderPfuncOrAlgaTemp = true;
		};

		//Pattern
		if(isAlgaReaderPfuncOrAlgaTemp.not, {
			parser.parseGenericObject(value,
				func: { | val |
					this.reassignAlgaReaderPfuncs(val, algaPatternPlayerParam, sched)
				},
				replace: false
			)
		});

		^value;
	}

	//Special case: FX is an Event
	reassignFXEvent { | fxEvent, algaPatternPlayerParam, sched = 0 |
		if(fxEvent.isEvent, {
			fxEvent.keysValuesDo({ | key, value |
				if(key != \def, {
					fxEvent[key] = this.reassignAlgaReaderPfuncs(value, algaPatternPlayerParam, sched)
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

			//Re-order
			entriesOrder = entriesOrder ? Array.newClear;
			entriesOrder = entriesOrder.add(param);
			entriesOrder = entriesOrder[entriesOrder.order];
		});

		//New ID - sender
		entries[param][\lastID] = uniqueID;
		entries[param][\entries][uniqueID] = sender.algaAsStream;

		//Get shape
		shape = shape.algaCheckValidEnv(server: server) ? Env([0, 1], 1);

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
									algaPatternPlayerParam: param,
									sched: sched
								)
							}, {
								//Re-assign the AlgaReaderPfuncs
								reassignedEntry = this.reassignAlgaReaderPfuncs(
									value: algaPatternEventEntryAtParam,
									algaPatternPlayerParam: param,
									sched: sched
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
						results[param].removeAt(lastID);
						if(algaNextSchedTimes[param] != nil, {
							algaNextSchedTimes[param].removeAt(lastID)
						});
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
		time = (time ? timeInner) ? 0;
		sched = (sched ? schedInner) ? 0;
		shape = shape ? shapeInner;

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
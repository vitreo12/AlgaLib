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
	var <timeInner = 0, <schedInner = 1;
	var <schedResync = 1;
	var <durInterpResync = true;
	var <durInterpReset = false;

	var <entries;
	var <results;
	var <algaPatterns;
	var <algaPatternEntries;
	var <algaPatternsPrevFunc;
	var <server, <scheduler;

	var <beingStopped = false;
	var <schedInSeconds = false;
	var <scheduledStepActionsPre, <scheduledStepActionsPost;

	var <dur = 1, <durAlgaPseg;
	var <stretch = 1, <stretchAlgaPseg;
	var <manualDur = false;

	/*****************************************************************************************/
	// Utilities copied over from AlgaNode / AlgaPattern. These should really be modularized //
	// in their own class and used both here and in AN / AP.                                 //
	/*****************************************************************************************/

	//Add an action to scheduler. This takes into account sched == AlgaStep
	addAction { | condition, func, sched = 0, topPriority = false, preCheck = false |
		if(sched.isAlgaStep, {
			this.addScheduledStepAction(
				step: sched,
				condition: condition,
				func: func
			);
		}, {
			//Normal scheduling (sched is a number or AlgaQuant)
			scheduler.addAction(
				condition: condition,
				func: func,
				sched: sched,
				topPriority: topPriority,
				preCheck: preCheck,
				schedInSeconds: schedInSeconds
			)
		});
	}

	//Creates a new AlgaStep with set condition and func
	addScheduledStepAction { | step, condition, func |
		//A new action must be created, otherwise, if two addActions are being pushed
		//with the same AlgaStep, only one of the action would be executed (the last one),
		//as the entry would be overwritten in the OrderedIdentitySet
		var newStep = step.copy;
		var post = step.post;
		var scheduledStepActions;
		newStep.condition = condition ? { true };
		newStep.func = func;

		//Create if needed
		if(post.not, {
			scheduledStepActionsPre = scheduledStepActionsPre ? OrderedIdentitySet(10);
			scheduledStepActions = scheduledStepActionsPre;
		}, {
			scheduledStepActionsPost = scheduledStepActionsPost ? OrderedIdentitySet(10);
			scheduledStepActions = scheduledStepActionsPost;
		});

		//Add
		scheduledStepActions.add(newStep)
	}

	//Iterate through all scheduledStepActions and execute them accordingly
	advanceAndConsumeScheduledStepActions { | post = false |
		var stepsToRemove = IdentitySet();

		//Pre or post
		var scheduledStepActions;
		if(post.not, {
			scheduledStepActions = scheduledStepActionsPre;
		}, {
			scheduledStepActions = scheduledStepActionsPost;
		});

		//Go ahead with the advancing + removal
		if(scheduledStepActions.size > 0, {
			scheduledStepActions.do({ | step |
				var condition = step.condition;
				var func = step.func;
				var retryOnFailure = step.retryOnFailure;
				var tries = step.tries;
				var stepCount = step.step;

				if(stepCount <= 0, {
					if(condition.value, {
						func.value;
						stepsToRemove.add(step);
					}, {
						if(retryOnFailure.not, {
							stepsToRemove.add(step);
						}, {
							if(tries <= 0, {
								stepsToRemove.add(step);
							}, {
								step.tries = tries - 1;
							});
						});
					});
				});

				step.step = stepCount - 1;
			});
		});

		//stepsToRemove is needed or it won't execute two consecutive true
		//functions if remove was inserted directly in the call earlier
		if(stepsToRemove.size > 0, {
			stepsToRemove.do({ | step | scheduledStepActions.remove(step) })
		});
	}

	//Parse an AlgaTemp
	parseAlgaTempParam { | algaTemp, functionSynthDefDict, topAlgaTemp |
		var validAlgaTemp = false;
		var def = algaTemp.def;
		var defDef;

		if(def == nil, {
			"AlgaPattern: AlgaTemp has a nil 'def' argument".error;
			^nil;
		});

		case

		//Symbol
		{ def.isSymbol } {
			defDef = def;
			algaTemp.checkValidSynthDef(defDef); //check \def right away
			if(algaTemp.valid.not, { defDef = nil });
			validAlgaTemp = true;
		}

		//Event
		{ def.isEvent } {
			defDef = def[\def];
			if(defDef == nil, {
				"AlgaPattern: AlgaTemp's 'def' Event does not provide a 'def' entry".error;
				^nil
			});

			case

			//Symbol: check \def right away
			{ defDef.isSymbol } {
				algaTemp.checkValidSynthDef(defDef); //check \def right away
				if(algaTemp.valid.not, { defDef = nil });
			}

			//Substitute \def with the new symbol
			{ defDef.isFunction } {
				var defName = ("alga_" ++ UniqueID.next).asSymbol;

				//AlgaTemp can be sampleAccurate in AlgaPatterns!
				functionSynthDefDict[defName] = [
					AlgaSynthDef.new_inner(
						defName,
						defDef,
						sampleAccurate: algaTemp.sampleAccurate,
						makeFadeEnv: false,
						makePatternDef: false,
						makeOutDef: false
					),
					algaTemp
				];

				defDef = defName;
				def[\def] = defName;
			};

			//Loop around the event entries and use as Stream, substituting entries
			def.keysValuesDo({ | key, entry |
				if(key != \def, {
					var parsedEntry = this.parseParam_inner(entry, functionSynthDefDict);
					if(parsedEntry == nil, { ^nil });
					//Finally, replace in place
					def[key] = parsedEntry.algaAsStream;
				});
			});

			validAlgaTemp = true;
		}

		//Function: subsitute \def with the new symbol
		{ def.isFunction } {
			var defName = ("alga_" ++ UniqueID.next).asSymbol;

			//AlgaTemp can be sampleAccurate in AlgaPatterns!
			functionSynthDefDict[defName] = [
				AlgaSynthDef.new_inner(
					defName,
					def,
					sampleAccurate: algaTemp.sampleAccurate,
					makeFadeEnv: false,
					makePatternDef: false,
					makeOutDef: false
				),
				algaTemp
			];

			defDef = defName;
			algaTemp.setDef(defName); //Substitute .def with the Symbol
			validAlgaTemp = true;
		};

		//Check validity
		if(validAlgaTemp.not, {
			("AlgaPattern: AlgaTemp's 'def' argument must either be a Symbol, Event or Function").error;
			^nil
		});

		//Check if actually a symbol now
		if(defDef.class != Symbol, {
			("AlgaPattern: Invalid AlgaTemp's definition: '" ++ defDef.asString ++ "'").error;
			^nil
		});

		//Return the modified algaTemp (in case of Event / Function)
		^algaTemp;
	}

	//Parse a ListPattern
	parseListPatternParam { | listPattern, functionSynthDefDict |
		listPattern.list.do({ | listEntry, i |
			listPattern.list[i] = this.parseParam_inner(listEntry, functionSynthDefDict);
		});
		^listPattern;
	}

	//Parse a FilterPattern
	parseFilterPatternParam { | filterPattern, functionSynthDefDict |
		var pattern = filterPattern.pattern;
		filterPattern.pattern = this.parseParam_inner(pattern, functionSynthDefDict);
		^filterPattern;
	}

	//Parse a param looking for AlgaTemps and ListPatterns
	parseParam_inner { | value, functionSynthDefDict |
		//Dispatch
		case
		{ value.isAlgaTemp } {
			value = this.parseAlgaTempParam(value, functionSynthDefDict);
			if(value == nil, { ^nil });
		}
		{ value.isListPattern } {
			value = this.parseListPatternParam(value, functionSynthDefDict);
			if(value == nil, { ^nil });
		}
		{ value.isFilterPattern } {
			value = this.parseFilterPatternParam(value, functionSynthDefDict);
			if(value == nil, { ^nil });
		}
		/* { value.isAlgaReaderPfunc } {
			if(this.isAlgaPattern, { this.assignAlgaReaderPfunc(value) });
		} */
		{ value.isPattern } {
			//Fallback: generic Pattern
			value = this.parseGenericPatternParam(value, functionSynthDefDict);
			if(value == nil, { ^nil });
		};

		//Returned parsed element
		^value;
	}

	//Parse an entry
	parseParam { | value, functionSynthDefDict |
		//Used in from {}
		var returnBoth = false;
		if(functionSynthDefDict == nil, {
			returnBoth = true;
			functionSynthDefDict = IdentityDictionary();
		});

		//Reset paramContainsAlgaReaderPfunc, and latestPlayers recursivePatternList
		//if(this.isAlgaPattern, { this.resetPatternParsingVars });

		//Actual parsing
		value = this.parseParam_inner(value, functionSynthDefDict);

		//Used in from {}
		if(returnBoth, {
			^[value, functionSynthDefDict]
		}, {
			^value
		})
	}

	//If needed, it will compile the AlgaSynthDefs in functionSynthDefDict and wait before executing func.
	//Otherwise, it will just execute func
	compileFunctionSynthDefDictIfNeeded { | func, functionSynthDefDict |
		//If functionSynthDefDict has elements, it means that there are AlgaSynthDefs with Functions to be waited for
		if(functionSynthDefDict != nil, {
			if(functionSynthDefDict.size > 0, {
				var wait = Condition();

				fork {
					//Loop over and compile Functions and AlgaTemps
					functionSynthDefDict.keysValuesDo({ | name, sdef |
						//AlgaTemp
						if(sdef.isArray, {
							var algaTemp = sdef[1];
							sdef = sdef[0];
							sdef.sendAndAddToGlobalDescLib(server);
							algaTemp.checkValidSynthDef(name);
						}, {
							//Just AlgaSynthDef
							sdef.sendAndAddToGlobalDescLib(server);
						})
					});

					//Unlock condition
					server.sync(wait);
				};

				this.addAction(
					condition: { wait.test == true },
					func: { func.value },
					preCheck: true //execute right away if possible (most unlikely)
				);

				^this
			});
		});

		//No Functions to consume
		^func.value;
	}

	//Check env
	checkValidEnv { | value |
		var levels, times;

		if(value == nil, { ^nil });

		if(value.isKindOf(Env).not, {
			("AlgaNode: invalid interpShape: " ++ value.class).error;
			^nil
		});

		levels = value.levels;
		if(levels.size > AlgaStartup.maxEnvPoints, {
			("AlgaNode: interpShape's Env can only have up to " ++ AlgaStartup.maxEnvPoints ++ " points.").error;
			^nil
		});
		if(levels.first != 0, {
			("AlgaNode: interpShape's Env must always start from 0").error;
			^nil
		});
		if(levels.last != 1, {
			("AlgaNode: interpShape's Env must always end at 1").error;
			^nil
		});
		levels.do({ | level |
			if(((level >= 0.0).and(level <= 1.0)).not, {
				("AlgaNode: interpShape's Env can only contain values between 0 and 1").error;
				^nil
			});
		});

		times = value.times;
		if(times.sum == 0, {
			("AlgaNode: interpShape's Env cannot have its times sum up to 0").error;
			^nil
		});

		^value
	}

	/*****************************************************************************************/
	/*****************************************************************************************/
	/*****************************************************************************************/

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
							entry = entry.next;
							entry.algaAdvance;

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
					patternPairs = patternPairs.add(key).add(entry);
				});
			});
		}, {
			("AlgaPatternPlayer: Invalid 'def': " ++ argDef.class.asString).error;
			^nil;
		});

		//Ass reschedulable \stretch
		patternPairs = patternPairs.add(\stretch).add(
			Pfunc({ stretch.next })
		);

		//Add reschedulable \dur
		if(foundDurOrDelta, {
			if(manualDur.not, {
				patternPairs = patternPairs.add(\dur).add(
					Pfunc({ dur.next })
				);
			});
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
		time = time * this.clock.tempo;
		//time = if(tempoScaling.not, { time * this.clock.tempo });

		//Check sched
		sched = sched ? schedInner;

		//Check resync
		resync = resync ? durInterpResync;

		//Check reset
		reset = reset ? durInterpReset;

		//Get shape
		shape = this.checkValidEnv(shape) ? Env([0, 1], 1);

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
			results[algaPatternPlayerParam][lastID] = tempEntryStream.next;

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

	//Loop through ListPattern (looking for AlgaTemps)
	reassignListPattern { | listPattern, algaPatternPlayerParam |
		listPattern.list.do({ | listEntry, i |
			listPattern.list[i] = this.reassignAlgaReaderPfuncs(listEntry, algaPatternPlayerParam)
		});
		^listPattern;
	}

	//Loop through ListPattern (looking for AlgaTemps)
	reassignFilterPattern { | filterPattern, algaPatternPlayerParam |
		filterPattern.pattern = this.reassignAlgaReaderPfuncs(filterPattern.pattern, algaPatternPlayerParam);
		^filterPattern;
	}

	//Try to reassign a class entry (if possible)
	reassignGenericPattern { | classInst, algaPatternPlayerParam |
		classInst.class.instVarNames.do({ | instVarName |
			try {
				var getter = classInst.perform(instVarName);
				var value = this.reassignAlgaReaderPfuncs(getter, algaPatternPlayerParam);
				//Set the newly calculated AlgaReaderPfunc.
				//Honestly, this "setter" method is not that safe, as it assumes that the
				//Class implements a setter for the specific instance variable.
				//The AlgaReaderPfunc should instead be modified in place
				if(value.isAlgaReaderPfunc, {
					classInst.perform((instVarName ++ "_").asSymbol, value);
				});
			} { | error |
				//Catch setter errors
				if(error.isKindOf(DoesNotUnderstandError), {
					if(error.selector.asString.endsWith("_"), {
						("AlgaPatternPlayer: could not reassign the AlgaReaderPfunc for '" ++
							classInst.class ++ "." ++ error.selector ++
							"'. The Class does implement its setter method."
						).error
					});
				})
			}
		});
		^classInst;
	}

	//Go through AlgaTemp / ListPattern / FilterPattern looking for things
	//to re-assing to let AlgaReaderPfunc work correctly
	reassignAlgaReaderPfuncs { | value, algaPatternPlayerParam |
		case
		{ value.isAlgaTemp } {
			value = this.reassignAlgaTemp(value, algaPatternPlayerParam);
		}
		{ value.isListPattern } {
			value = this.reassignListPattern(value, algaPatternPlayerParam);
		}
		{ value.isFilterPattern } {
			value = this.reassignFilterPattern(value, algaPatternPlayerParam);
		}
		{ value.isAlgaReaderPfunc } {
			value = this.reassignAlgaReaderPfunc(value, algaPatternPlayerParam);
		}
		{ value.isPattern } {
			//Fallback
			this.reassignGenericPattern(value, algaPatternPlayerParam);
		};

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
	fromInner { | sender, param = \in, time, sched |
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
								sched: sched
							)
						});
					});
				});
			});
		});

		//Free old lastID stuff after time + 2 (for certainty)
		this.addAction(
			func: {
				if(lastID != nil, {
					fork {
						(time + 2).wait;
						entries[param].removeAt(lastID);
						entries[param][\entries].removeAt(lastID);
					}
				});
			},
			sched: sched
		)
	}

	//This will also trigger interpolation on all registered AlgaPatterns
	from { | sender, param = \in, time, sched |
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
			this.interpolateDur(value: sender, time: time, sched: sched);
			isDurOrStretch = true;
		}
		{ param == \stretch } {
			this.interpolateStretch(value: sender, time: time, sched: sched);
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

	clock { ^(scheduler.clock) }

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
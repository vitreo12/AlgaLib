// AlgaLib: SuperCollider implementation of Alga, an interpolating live coding environment.
// Copyright (C) 2020-2021 Francesco Cameli.

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
	var <entries;
	var <results;
	var <algaPatterns;
	var <algaPatternsPrevFunc;
	var <server, <scheduler;

	var <beingStopped = false;
	var <schedInSeconds = false;
	var <scheduledStepActionsPre, <scheduledStepActionsPost;

	var <dur = 1;
	var <manualDur = false;

	/*****************************************************************************************/
	// Utilities copied over from AlgaNode / AlgaPattern. These should really be modularized //
	//   in their own class and used both here and in AN / AP.                               //
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
			//Normal scheduling (sched is a number)
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
	parseAlgaTempParam { | algaTemp, functionSynthDefDict |
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
					var parsedEntry = entry;
					case
					{ entry.isListPattern } {
						parsedEntry = this.parseListPatternParam(parsedEntry, functionSynthDefDict);
						if(parsedEntry == nil, { ^nil })
					}
					{ entry.isFilterPattern } {
						parsedEntry = this.parseFilterPatternParam(parsedEntry, functionSynthDefDict);
						if(parsedEntry == nil, { ^nil })
					}
					{ entry.isAlgaTemp } {
						parsedEntry = this.parseAlgaTempParam(parsedEntry, functionSynthDefDict);
						if(parsedEntry == nil, { ^nil })
					};
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
			case
			{ listEntry.isListPattern } {
				listPattern.list[i] = this.parseListPatternParam(listEntry, functionSynthDefDict)
			}
			{ listEntry.isFilterPattern } {
				listPattern.list[i] = this.parseFilterPatternParam(listEntry, functionSynthDefDict)
			}
			{ listEntry.isAlgaTemp } {
				listPattern.list[i] = this.parseAlgaTempParam(listEntry, functionSynthDefDict)
			};
		});
		^listPattern;
	}

	//Parse a FilterPattern
	parseFilterPatternParam { | filterPattern, functionSynthDefDict |
		var pattern = filterPattern.pattern;
		case
		{ pattern.isListPattern } {
			filterPattern.pattern = this.parseListPatternParam(pattern, functionSynthDefDict)
		}
		{ pattern.isFilterPattern } {
			filterPattern.pattern = this.parseFilterPatternParam(pattern, functionSynthDefDict)
		}
		{ pattern.isAlgaTemp } {
			filterPattern.pattern = this.parseAlgaTempParam(pattern, functionSynthDefDict)
		};
		^filterPattern;
	}

	//Parse a param looking for AlgaTemps and ListPatterns
	parseAlgaTempListPatternParam { | value, functionSynthDefDict |
		//Used in from {}
		var returnBoth = false;
		if(functionSynthDefDict == nil, {
			returnBoth = true;
			functionSynthDefDict = IdentityDictionary();
		});

		case
		{ value.isAlgaTemp } {
			value = this.parseAlgaTempParam(value, functionSynthDefDict)
		}
		{ value.isListPattern } {
			value = this.parseListPatternParam(value, functionSynthDefDict)
		}
		{ value.isFilterPattern } {
			value = this.parseFilterPatternParam(value, functionSynthDefDict)
		};

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

		//1) Add support for arrays to keep ordering of execution of params!

		if(argDef.isEvent, {
			patternPairs = patternPairs.add(\type).add(\algaPatternPlayer);
			patternPairs = patternPairs.add(\algaPatternPlayer).add(this);
			argDef.keysValuesDo({ | key, entry |
				//Found \dur or \delta
				if((key == \dur).or(key == \delta), {
					if((entry.isSymbol).or(entry.isNil), { manualDur = true });
					foundDurOrDelta = true;
					dur = entry.algaAsStream;
				}, {
					var uniqueID = UniqueID.next;
					//Parse entry for AlgaTemps
					entry = this.parseAlgaTempListPatternParam(entry, functionSynthDefDict);
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

		//Add reschedulable \dur
		if(foundDurOrDelta, {
			if(manualDur.not, {
				patternPairs = patternPairs.add(\dur).add(
					Pfuncn( { dur.next }, inf)
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

	timeInner_ { | value |
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
						clock: scheduler.clock
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

	//Wrap result in AlgaReader
	at { | key, repeats = inf |
		var result;

		//Lock ID (needs to be out of Pfunc: locked on creation)
		var id = entries[key][\lastID];
		if(id == nil, {
			("AlgaPattern: undefined parameter in AlgaPatternPlayer: '" ++ key ++ "'").error;
		});

		//Create the AlgaReaderPfunc, wrapping the indexing of the result
		result = AlgaReaderPfunc({
			AlgaReader(this.results[key][id]);
		}, repeats ? inf);

		//Assign patternPlayer
		result.patternPlayer = this;
		result.keyOrFunc = key;
		result.params = [ key ]; //it expects Array later on

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
				var result = this.results[argName][id];
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

	//Like AlgaPattern: retriggers at specific sched
	interpolateDur { | sender, sched |
		if(sched.isAlgaStep, {
			//sched == AlgaStep
			this.addAction(
				func: {
					beingStopped = true;
					algaReschedulingEventStreamPlayer.rescheduleAtQuant(0, {
						dur = sender.algaAsStream;
						beingStopped = false;
					});
				},
				sched: sched
			)
		}, {
			//sched == number
			algaReschedulingEventStreamPlayer.rescheduleAtQuant(sched, {
				dur = sender.algaAsStream;
			});
		});
	}

	//Implement from {}
	fromInner { | sender, param = \in, time, sched |
		this.addAction(
			func: {
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

				//Re-trigger interpolation on AlgaPatterns
				algaPatterns.do({ | algaPattern |
					algaPattern.asString.warn;
					// .....
				});

				//Free old lastID stuff after time + 2 (for certainty)
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
		var senderAndFunctionSynthDefDict = this.parseAlgaTempListPatternParam(sender);
		var functionSynthDefDict;

		//Unpack
		sender = senderAndFunctionSynthDefDict[0];
		functionSynthDefDict = senderAndFunctionSynthDefDict[1];

		//Set time / sched accordingly
		time = time ? timeInner;
		time = time ? 0;
		sched = sched ? schedInner;
		sched = sched ? 0;

		// \dur or other params
		if((param == \dur).or(param == \delta), {
			this.interpolateDur(sender, sched);
		}, {
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
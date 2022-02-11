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
	var <pattern, <algaReschedulingEventStreamPlayer;
	var <timeInner = 0, <schedInner = 1;
	var <entries;
	var <results;
	var <algaPatterns;
	var <algaPatternsPrevFunc;
	var <server, <scheduler;

	var <beingStopped = false;
	var <schedInSeconds = false;
	var <scheduledStepActionsPre, <scheduledStepActionsPost;

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

		//Reset vars
		results = IdentityDictionary();
		entries = IdentityDictionary();
		algaPatterns = IdentitySet();

		//Run parser for what's needed! Is it only AlgaTemps?
		//Parse AlgaTemps like AlgaPattern!

		case
		{ argDef.isArray } {
			patternPairs = patternPairs.add(\type).add(\algaPatternPlayer);
			patternPairs = patternPairs.add(\algaPatternPlayer).add(this);
		}
		{ argDef.isEvent } {
			argDef.keysValuesDo({ | key, entry |
				if((key != \dur).and(key != \delta), {
					var uniqueID = UniqueID.next;
					results[key] = IdentityDictionary();
					entries[key] = IdentityDictionary();
					entries[key][\lastID] = uniqueID;
					entries[key][\entries] = IdentityDictionary();
					entries[key][\entries][uniqueID] = entry.algaAsStream; //.next support
				}, {
					entries[\dur] = entry
				});
			});
			argDef[\type] = \algaPatternPlayer;
			argDef[\algaPatternPlayer] = this;
			argDef.keysValuesDo({ | key, value |
				patternPairs = patternPairs.add(key).add(value);
			});
		};

		pattern = Pbind(*patternPairs);
	}

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

	run { | sched = 1 |
		//Check sched
		sched = sched ? schedInner;
		sched = sched ? 0;
		this.addAction(
			func: {
				beingStopped = false;
				algaReschedulingEventStreamPlayer = pattern.playAlgaRescheduling(
					clock: scheduler.clock
				);
			},
			sched: sched
		);
	}

	stop { | sched = 1 |
		//Check sched
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

	addPattern { | algaPattern |
		if(algaPattern.isAlgaPattern, {
			algaPatterns.add(algaPattern)
		});
	}

	removePattern { | algaPattern, sched |
		//Check sched
		sched = sched ? schedInner;
		sched = sched ? 0;
		algaPattern.player = nil;
		this.addAction(
			func: {
				algaPatterns.remove(algaPattern);
			},
			sched: sched
		)
	}

	//Wrap result in AlgaReader
	at { | key |
		//Lock ID (needs to be out of Pfunc: locked on creation)
		var id = entries[key][\lastID];
		if(id == nil, {
			("AlgaPattern: undefined parameter in AlgaPatternPlayer: '" ++ key ++ "'").error;
		});
		^(Pfunc {
			AlgaReader(
				this.results[key][id]
			)
		})
	}

	//Execute func and wrap result in AlgaReader
	read { | func |
		var argNames, argVals, retriever;
		var ids;

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

		//Perform func and wrap result in AlgaReader
		^(Pfunc {
			AlgaReader(
				func.performWithEnvir(\value, retriever.value)
			)
		})
	}

	value { | func | ^this.read(func) }

	//Like AlgaPattern: retriggers at specific sched
	interpolateDur { }

	//This will also trigger interpolation on all registered AlgaPatterns
	from { | sender, param = \in, time, sched |
		if((param == \dur).or(param == \delta), {

		}, {
			this.addAction(
				func: {
					//New ID - sender
					var uniqueID = UniqueID.next;
					var lastID = entries[param][\lastID];
					entries[param][\lastID] = uniqueID;
					entries[param][\entries][uniqueID] = sender.algaAsStream;

					//Re-trigger interpolation... To achieve so, 2 things must have been stored:
					// 1) the algaPattern -> oldEntry
					// 2) the param used in the algaPattern
					algaPatterns.do({ | algaPattern |

					});

					//Free old lastID stuff after time + 2 (for certainty)
					fork {
						(time + 2).wait;
						entries[param].removeAt(lastID);
						entries[param][\entries].removeAt(lastID);
					}
				},
				sched: sched
			)
		});
	}

	<< { | sender, param = \in |
		this.from(sender, param)
	}

	isAlgaPatternPlayer { ^true }
}

//alias
AlgaPlayer : AlgaPatternPlayer {}

//alias
APP : AlgaPatternPlayer {}

//Used under the hood in AlgaPattern to read from an AlgaPatternPlayer
AlgaReader {
	var <entry;

	*new { | entry |
		^super.newCopyArgs(entry)
	}

	isAlgaReader { ^true }
}
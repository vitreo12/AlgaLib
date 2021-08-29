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

AlgaThread {
	classvar <>verbose = false;
	var <name, <server, <clock, <task;

	*new { | server, clock, autostart = true |
		var argServer = server ? Server.default;
		var argName = argServer.name;
		var argClock = clock ? TempoClock.default;
		^super.newCopyArgs(argName, argServer, argClock).init(autostart);
	}

	init { | autostart = true |
		task = Routine({
			if(verbose, { ("AlgaThread" + name + "starts.").postcln });
			this.run;
		}, 16384);

		if(autostart, { this.start });
	}

	//Overwrite this!
	run {
		"AlgaThread must be inherited to be used!".error;
	}

	//Overwrite this (if needed)
	clearFunc { }

	cmdPeriod {
		this.clear; //stop and clear the previous task
		this.init(autostart: true); //re-init the task
	}

	start {
		if(task.isPlaying, {
			if(verbose, { ("AlgaThread" + name + "already playing.").postcln });
			^this;
		});
		task.reset.play(clock);
		CmdPeriod.add(this);
		if(verbose, { ("AlgaThread" + name + "started.").postcln });
	}

	play { this.start }

	stop {
		CmdPeriod.remove(this);
		if(verbose, { ("AlgaThread" + name + "stopping.").postcln });
		task.stop;
	}

	clear {
		CmdPeriod.remove(this);
		task.stop;
		this.clearFunc;
		task.clear;
		if(verbose, { ("AlgaThread" + name + "cleared.").postcln });
	}
}

AlgaScheduler : AlgaThread {
	var <>interval = 0.05; //5ms tick
	var <>maxSpinTime = 5;  //5s before erroring out

	var <cascadeMode = false;
	var <switchCascadeMode = false;
	var <scheduling = false;
	var <>interruptOnSched = false;

	var semaphore;

	var <actions, <spinningActions;

	var <interruptOnSchedActions;
	var <interruptOnSchedRunning = false;

	//sclang is single threaded, there won't ever be data races here ;)
	var currentExecAction;
	var currentExecActionOffset = 0;
	var spinningActionsCount = 0;

	*new { | server, clock, cascadeMode = false, autostart = true, interruptOnSched = false |
		var argServer = server ? Server.default;
		var argName = argServer.name;
		var argClock = clock ? TempoClock(1, queueSize:16834).permanent_(true); //big queue size
		^super.newCopyArgs(argName, argServer, argClock).init(cascadeMode, autostart, interruptOnSched);
	}

	init { | argCascadeMode = false, autostart = true, argInterruptOnSched = false |
		if(actions == nil, {
			actions = List(10);
		});

		if(spinningActions == nil, {
			spinningActions = IdentityDictionary(10);
		});

		if(semaphore == nil, {
			semaphore = Condition();
		});

		cascadeMode = argCascadeMode;
		interruptOnSched = argInterruptOnSched;

		super.init(autostart);
	}

	cascadeMode_ { | val |
		if(scheduling, { "Can't set cascadeMode while scheduling events".error; ^this });
		cascadeMode = val;
	}

	switchCascadeMode_ { | val |
		if(scheduling, { "Can't set switchCascadeMode_ while scheduling events".error; ^this });
		switchCascadeMode = val;
	}

	//Normal case: just push to bottom of the List
	pushAction { | action |
		if(interruptOnSchedRunning.not, {
			//Normal case: add to actions
			actions.add(action);
		}, {
			//A scheduled action is happening with the interruptOnSched mode: add to interruptOnSchedActions
			if(interruptOnSchedActions != nil, {
				interruptOnSchedActions.add(action);
			});
		});
	}

	//Add action after currentExecAction, with correct offset currentExecActionOffset.
	//This is essential for actions that trigger multiple other actions
	pushActionAfterCurrentExecAction { | action |
		if(interruptOnSchedRunning.not, {
			//Normal case: add to actions
			actions.insertAfterEntry(currentExecAction, currentExecActionOffset, action);
		}, {
			//A scheduled action is happening with the interruptOnSched mode: add to interruptOnSchedActions
			if(interruptOnSchedActions != nil, {
				interruptOnSchedActions.insertAfterEntry(currentExecAction, currentExecActionOffset, action);
			});
		});

		//Increase offset. This is needed for nested calls!
		currentExecActionOffset = currentExecActionOffset + 1;
	}

	removeAction { | action |
		if(action != nil, {
			actions.removeAtEntry(action);
			spinningActions.removeAt(action);
		});
	}

	accumTimeAtAction { | action, condition, exceededMaxSpinTime |
		//Else, check how much time the specific action is taking
		var accumTime = (spinningActions[action]) + interval;

		if(accumTime >= maxSpinTime, {
			(
				"AlgaScheduler: the condition of caller in '" ++
				condition.def.context ++
				"' exceeded maximum wait time " ++
				maxSpinTime
			).error;

			//remove the culprit action
			this.removeAction(action);

			//if cascadeMode, gotta exit the condition too
			if(cascadeMode, {
				exceededMaxSpinTime[0] = true;
			});

			^nil;
		});

		//Update timing
		spinningActions[action] = accumTime;
	}

	executeFunc { | action, func, sched |
		if(verbose, {
			(
				"AlgaScheduler: executing function from context '" ++
				func.def.context ++ "'"
			).postcln;
		});

		//Update currentExecAction (so it's picked in func.value for child addAction)
		currentExecAction = action;
		currentExecActionOffset = 0; //reset it here, so that nested calls have proper index offset

		//execute and remove action
		server.bind({ func.value });
		this.removeAction(action);

		//Reset currentExecAction (so it's reset for new stage)
		currentExecAction = nil;
		currentExecActionOffset = 0; //needs resetting here (for next calls)
	}

	hangSemaphore {
		semaphore.hang;
	}

	unhangSemaphore {
		if(semaphore.test.not, {
			semaphore.unhang;
		});
	}

	loopFunc { | action |
		//Unpack things
		var condition = action[0];
		var func = action[1];

		if(condition.value, {
			//If condition, execute it and remove from the List
			this.executeFunc(
				action,
				func,
			);

		}, {
			//enter one of the two cascadeModes
			if(cascadeMode, {
				//cascadeMode == true
				//concurrent waiting

				var exceededMaxSpinTime = [false]; //use [] to pass by reference

				while({ condition.value.not }, {
					this.accumTimeAtAction(
						action,
						condition,
						exceededMaxSpinTime
					);

					if(exceededMaxSpinTime[0], {
						condition = true; //exit the while loop
					});

					//Block the execution: spin around this action
					interval.wait;
				});

				//If finished spinning, execute func
				if(exceededMaxSpinTime[0].not, {
					this.executeFunc(
						action,
						func,
					)
				})
			}, {
				//cascadeMode == false
				//parallel spinning

				//Just accumTime, while letting it go for successive actions.
				//exceededMaxSpinTime is here nil
				this.accumTimeAtAction(
					action,
					condition,
					nil
				);

				//Or, this action is spinning
				spinningActionsCount = spinningActionsCount + 1;

				//Before adding, check if this was last entry in the list.
				//If that's the case, spin
				if((spinningActionsCount == (actions.size)).or(actions.size == 1), {
					spinningActionsCount = 0; //reset
					interval.wait; //spin
				});
			});
		});
	}

	run {
		loop {
			if(actions.size > 0, {
				//Reset spinningActionsCount
				spinningActionsCount = 0;

				//To protect cascadeMode_
				scheduling = true;

				//Bundle all the actions of this interval tick together
				//This will be the core of clock / server syncing of actions
				//Consume actions (they are ordered thanks to OrderedIdentitySet)
				while({ actions.size > 0 }, {
					var action, sched;

					if(cascadeMode, {
						//if cascading, pop action from top of the list.
						//this will always be the next action available
						action = actions[0];
					}, {
						//Otherwise, just get the next available action

						//Make sure of boundaries (it happens on addActions that add more addActions)
						if(spinningActionsCount >= actions.size, {
							spinningActionsCount = actions.size - 1
						});

						action = actions[spinningActionsCount];
					});

					//Individual sched for the action
					sched = action[2];

					//Found a sched value (run in the future on the clock)
					if(sched > 0, {
						if(interruptOnSched, {
							//Interrupt here until clock releases.
							//New actions will be pushed to interruptOnSchedActions

							//Remove the scheduled action
							this.removeAction(action);

							//Need to deep copy
							interruptOnSchedActions = actions.copy;

							//With this, new actions will be pushed to interruptOnSchedActions instead
							interruptOnSchedRunning = true;

							//Remove all actions (so that scheduler won't be triggered)
							actions.clear;
							spinningActions.clear;

							//Sched the unhanging in the future
							clock.algaSchedAtQuantOnce(sched, {
								//If condition is met, execute the scheduled action
								if(action[0].value, {
									this.executeFunc(
										action,
										action[1]
									);
								}, {
									//Else, push it to list with sched 0 (== execute ASAP)
									this.addAction(action[0], action[1], 0);
								});

								//Copy all the actions back in.
								//Use .add in case new actions were pushed to interruptOnSchedActions meanwhile
								interruptOnSchedActions.do({ | interruptOnSchedAction |
									actions.add(interruptOnSchedAction);
									spinningActions[interruptOnSchedAction] = 0; //reset spins too
								});

								//Add new actions back to actions from now on (and not interruptOnSchedActions)
								interruptOnSchedRunning = false;

								//Release the reference to the GC (it will be updated with new copies anyway)
								interruptOnSchedActions = nil;

								//Unhang if needed
								this.unhangSemaphore;
							});
						}, {
							//Only remove the one action and postpone it in the future.
							//Other actions would still go on!
							this.removeAction(action);

							//In sched time, execute the function!
							clock.algaSchedAtQuantOnce(sched, {
								//If condition is met, execute the scheduled action
								if(action[0].value, {
									this.executeFunc(
										action,
										action[1]
									);
								}, {
									//Else, push it to list with sched 0 (== execute ASAP)
									this.addAction(action[0], action[1], 0);
								});

								//Unhang if needed
								this.unhangSemaphore;
							});
						});
					}, {
						//Actual loop function, sched == 0
						this.loopFunc(action);
					});
				});
			});

			//All actions are completed: reset currentExecAction
			currentExecAction = nil;

			//If switchCascadeMode... This is used for AlgaPatch
			if(switchCascadeMode, {
				if(verbose, { "AlgaPatch: switching mode".postcln; });
				if(cascadeMode, {
					cascadeMode = false;
				}, {
					cascadeMode = true;
				});
			});

			//Done scheduling
			scheduling = false;

			//No actions to consume, hang
			if(verbose, { ("AlgaScheduler" + name + "hangs").postcln; });
			this.hangSemaphore;
		}
	}

	//Clear everything
	clearFunc {
		actions.clear;
		spinningActions.clear;
	}

	//Default condition is just { true }, just execute it when its time comes on the scheduler
	addAction { | condition, func, sched = 0 |
		var action;

		condition = condition ? { true };

		sched = sched ? 0;
		if(sched < 0, { sched = 0 });

		if((condition.isFunction.not).or(func.isFunction.not), {
			"AlgaScheduler: addAction only accepts Functions as both the condition and the func arguments".error;
			^this;
		});

		if(sched.isNumber.not, {
			"AlgaScheduler: addAction only accepts Numbers as sched arguments".error;
			^this;
		});

		//new action
		action = [condition, func, sched];

		//We're in a callee situation: add this node after the index of currentExecAction
		if(currentExecAction != nil, {
			if(verbose, {
				("AlgaScheduler: adding function:" + func.def.context.asString +
					"as child of function:" + currentExecAction[1].def.context.asString).postcln;
			});

			//Add action after currentExecAction (with correct offset)
			this.pushActionAfterCurrentExecAction(action);
		}, {
			//Normal case: add action to bottom of the List
			this.pushAction(action);
		});

		//set to 0 the accumulator of spinningActions
		spinningActions[action] = 0;

		//New action pushed to actions (not interruptOnSchedActions).
		if(interruptOnSchedRunning.not, {
			this.unhangSemaphore;
		});
	}
}

//Run things concurrently in the scheduler.
//Each event waits for the previous one to be completed...
//NOTE that this behaviour is broken when using an AlgaNode with a Function,
//as the server.sync call screws up the ordering of its buildFromSynthDef call
AlgaPatch {
	*new { | func, server |
		var scheduler;
		server = server ? Server.default;
		scheduler = Alga.schedulers[server];
		if(scheduler != nil, {
			if(scheduler.cascadeMode, {
				//If already cascadeMode
				scheduler.addAction(func: func);
			}, {
				//Make cascadeMode true and switch back to false when done
				scheduler.cascadeMode = true;
				scheduler.switchCascadeMode = true;
				scheduler.addAction(func: func);
			});
		}, {
			("Alga is not booted on server" + server.name).error;
		});
		^nil;
	}
}

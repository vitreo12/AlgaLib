AlgaThread {
	classvar <>verbose = false;
	var <name, <server, <clock, <task;

	*new { | server, clock, autostart = true |
		var argServer = server ? Server.default;
		var argName = argServer.name;
		var argClock = clock ? SystemClock;
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
	clearFunc {

	}

	cmdPeriod {
		this.clear; //stop and clear the previous task
		this.init(true); //re-init the task
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
		this.clearFunc;
		task.stop;
		task.clear;
		if(verbose, { ("AlgaThread" + name + "cleared.").postcln });
	}
}

AlgaScheduler : AlgaThread {
	var <>interval = 0.01;
	var <>maxSpinTime = 2;

	var <>cascadeMode = false;
	var <>switchCascadeMode = false;

	var semaphore;

	var <actions, <spinningActions;

	//sclang is single threaded, there won't ever be data races here ;)
	var currentExecAction;
	var spinningActionsCount = 0;

	*new { | server, clock, cascadeMode = false, autostart = true |
		var argServer = server ? Server.default;
		var argName = argServer.name;
		var argClock = clock ? SystemClock;
		^super.newCopyArgs(argName, argServer, argClock).init(cascadeMode, autostart);
	}

	init { | argCascadeMode = false, autostart = true |
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

		super.init(autostart);
	}

	removeAction { | action |
		actions.removeAtEntry(action);
		spinningActions.removeAt(action);
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

		//execute and remove action
		server.bind({ func.value });
		this.removeAction(action);

		//Reset currentExecAction
		currentExecAction = nil;
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
				//parallell spinning

				//Just accumTime, while letting it go for successive actions.
				//exceededMaxSpinTime is here nil
				this.accumTimeAtAction(
					action,
					condition,
					nil
				);

				//("Hanging at func" + condition.def.context).postln;

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

					if(sched > 0, {
						//remove it or it clogs the scheduler!
						this.removeAction(action);

						//reset sched (so it will be executed right away)
						action[2] = 0;

						clock.sched(sched, {
							//Add the action back up the scheduled time.
							//This will trigger the scheduler loop
							actions.add(action);
							spinningActions[action] = 0;
							semaphore.unhang;
						});
					}, {
						//Actual loop function :)
						this.loopFunc(action);
					});
				});
			});

			//All actions are completed: reset currentExecAction
			currentExecAction = nil;

			cascadeMode.postln;

			//If switchCascadeMode, revert to cascadeMode = false
			if(switchCascadeMode, {
				"Switching mode".postln;
				if(cascadeMode, {
					cascadeMode = false;
				}, {
					cascadeMode = true;
				});
			});

			cascadeMode.postln;

			//No actions to consume, hang
			if(verbose, { ("AlgaScheduler" + name + "hangs").postcln; });
			semaphore.hang;
		}
	}

	//Clear everything
	clearFunc {
		actions.clear;
		spinningActions.clear;
	}

	//Default condition is just { true }, just execute it when its time comes on the scheduler
	addAction { | condition, func, sched = 0, inheritable = true |
		var action;

		condition = condition ? { true };

		if((condition.isFunction.not).or(func.isFunction.not), {
			"AlgaScheduler: addAction only accepts Functions as both the condition and the func arguments".error;
			^this;
		});

		if(sched.isNumber.not, {
			"AlgaScheduler: addAction only accepts Numbers as sched arguments".error;
			^this;
		});

		//If condition is true already, execute the func right away...
		//This, however, invalidates sched!
		/*
		if(condition.value, {
			this.executeFunc(
				nil,
				func,
				sched
			);

			^this;
		});
		*/

		//new action
		action = [condition, func, sched];

		/*
		"\nBefore".postln;
		actions.do({|bubu|
			bubu[0].def.context.postln;
		});
		"".postln;
		*/

		//We're in a callee situation: add this node after the index of currentExecAction
		if(currentExecAction != nil, {
			if(verbose, {
				("AlgaScheduler: adding function:" + func.def.context.asString +
					"as child of function:" + currentExecAction[1].def.context.asString).warn;
			});

			//Add after the currentExecAction
			actions.insertAfterEntry(currentExecAction, action);

			//Reset currentExecAction to nil
			currentExecAction = nil;
		}, {
			//Normal case: just push to bottom of the List
			actions.add(action);
		});

		/*
		"After".postln;
		actions.do({|bubu|
			bubu[0].def.context.postln;
		});
		"".postln;
		*/

		//set to 0 the accumulator of spinningActions
		spinningActions[action] = 0;

		//New action! Unlock the semaphore
		if(semaphore.test.not, {
			semaphore.unhang;
		});
	}
}

//Run things concurrently in the scheduler.
//Each event waits for the previous one to be completed.
AlgaPatch {
	*new { | func, server |
		var algaScheduler;
		server = server ? Server.default;
		algaScheduler = Alga.schedulers[server];
		if(algaScheduler != nil, {
			if(algaScheduler.cascadeMode, {
				//If already cascadeMode
				algaScheduler.addAction(func: func);
			}, {
				algaScheduler.cascadeMode = true;
				algaScheduler.switchCascadeMode = true;
				algaScheduler.addAction(func: func);
			});
		}, {
			("Alga is not booted on server" + server.name).error;
		});
		^nil;
	}
}
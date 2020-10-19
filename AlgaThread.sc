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
	var isAlgaPatch = false;

	var semaphore;

	var <actions, <spinningActions;

	//sclang is single threaded, there won't ever be data races here ;)
	var <currentExecAction;

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
		});

		//Update timing
		spinningActions[action] = accumTime;
	}

	//If sched, exec func and send bundle after that time. Otherwise, exec func and send bundle now
	execFuncOnSched { | action, func, sched |
		if(sched > 0, {
			//Update maxSpinTime if it's longer than sched
			if(sched > maxSpinTime, {
				maxSpinTime = sched + interval;
			});

			clock.sched(sched, {
				server.bind({
					func.value;

					//Action completed
					this.removeAction(action);
				});
			});
		}, {
			server.bind({
				func.value;

				//Action completed
				this.removeAction(action);
			});
		});
	}

	executeFunc { | action, func, sched |
		if(verbose, {
			(
				"AlgaScheduler: executing function from context '" ++
				func.def.context ++ "'"
			).postcln;
		});

		//Update currentExecAction
		currentExecAction = action;

		//Should I just send the func to clock and execute it on sched?
		this.execFuncOnSched(action, func, sched);
	}

	run {
		loop {
			if(actions.size > 0, {
				//Bundle all the actions of this interval tick together
				//This will be the core of clock / server syncing of actions
				//Consume actions (they are ordered thanks to OrderedIdentitySet)
				while({ actions.size > 0 }, {
					var action, condition, func, sched;

					//if cascading, pop action from top of the list
					if(cascadeMode, {
						action = actions[0];
					}, {
						//Otherwise, just get the next action
					});

					condition = action[0];
					func = action[1];
					sched = action[2];

					if(condition.value, {
						//If condition, execute it and remove from the OrderedIdentitySet
						this.executeFunc(
							action,
							func,
							sched,
						)
					}, {
						//if cascadeMode, spin here (so the successive actions won't be
						//done until this one is). If it errors out, it will move to the next action
						if(cascadeMode, {
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

								interval.wait;
							});

							//If finished spinning (or it errors out), execute func
							if(exceededMaxSpinTime[0].not, {
								this.executeFunc(
									action,
									func,
									sched,
								)
							})
						}, {
							//Just accumTime, while letting it go for successive actions.
							//exceededMaxSpinTime is here nil
							this.accumTimeAtAction(
								action,
								condition,
								nil
							)
						});
					});
				});

				//Still actions to consume, spin
				interval.wait;
			});

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
	addAction { | condition, func, sched = 0 |
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

		//If condition is true already, execute the func right away
		if(condition.value, {
			this.executeFunc(
				nil,
				func,
				sched,
				nil
			);

			^this;
		});

		//new action
		action = [condition, func, sched];

		//We're in a callee situation: add this node after the index of currentExecAction
		if(currentExecAction != nil, {
			//Add after the currentExecAction
			actions.insertAfterEntry(currentExecAction, action);

			//Reset currentExecAction to nil
			currentExecAction = nil;
		}, {
			//Normal case: just push to bottom of the List
			actions.add(action);
		});

		//set to 0 the accumulator of spinningActions
		spinningActions[action] = 0;

		//New action! Unlock the semaphore
		if(semaphore.test.not, {
			semaphore.unhang;
		});
	}

	executeAlgaPatch { | func |
		isAlgaPatch = true;
		this.addAction(action:func);

		//actions need to be stack (so that an action within an action is in the same stack)
	}
}

//Run things concurrently in the scheduler.
//Each event waits for the previous one to be completed.
AlgaPatch {
	*new { | func, server |
		var algaScheduler;
		server = server ? Server.default;

		"AlgaPatch is not implemented yet".error;

		//algaScheduler = Alga.newAlgaPatchScheduler(server);
		//algaScheduler.executeAlgaPatch(func);
	}
}
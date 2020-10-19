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
	var semaphore;

	var <>cascadeMode = false;

	var <actions, <spinningActions;

	//sclang is single threaded, there won't ever be data races here ;)
	var <currentExecFunc;

	var isAlgaPatch = false;

	*new { | server, clock, cascadeMode = false, autostart = true |
		var argServer = server ? Server.default;
		var argName = argServer.name;
		var argClock = clock ? SystemClock;
		^super.newCopyArgs(argName, argServer, argClock).init(cascadeMode, autostart);
	}

	init { | argCascadeMode = false, autostart = true |
		if(actions == nil, {
			actions = OrderedIdentitySet(10); //needs to be ordered so that they're consumed first-in first-out
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

	accumTimeAtAction { | action, condition, exceededMaxSpinTime, consumedActions |
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
			consumedActions.add(action);

			//if cascadeMode, gotta exit the condition too
			if(cascadeMode, {
				exceededMaxSpinTime[0] = true;
			});
		});

		//Update timing
		spinningActions[action] = accumTime;
	}

	//If sched, send bundle after that time. Otherwise, send bundle now
	sendBundleOnSched { | sched, func |
		if(sched > 0, {
			clock.sched(sched, {
				server.bind({ func.value }); // == server.makeBundle(server.latency, { func.value });
			});
		}, {
			server.bind({ func.value }); // == server.makeBundle(server.latency, { func.value });
		});
	}

	executeFunc { | action, func, sched, consumedActions |
		if(verbose, {
			(
				"AlgaScheduler: executing function from context '" ++
				func.def.context ++ "'"
			).postcln;
		});

		//Update currentExecFunc
		currentExecFunc = func;

		//Should I just send the func to clock and execute it on sched?
		this.sendBundleOnSched(sched, func);

		//Or generate the bundle now and send it at scheduled time?
		//The problem is that, however, func is executed, and values thus updated
		//in the metadata of AlgaNodes...

		/*
		//Generate bundle just for this func
		funcBundle = server.makeBundle(false, { func.value });

		//Send bundle out at the specific time frame.
		//Wrap it in an array so that algaSchedBundleArrayOnClock
		//schedules all the OSC funcs on same sched
		if(funcBundle.isEmpty.not, {
			[sched].algaSchedBundleArrayOnClock(
				clock,
				[funcBundle],
				server,
				server.latency
			);
		});
		*/

		//Action completed: add to consumedActions
		if(consumedActions != nil, {
			consumedActions.add(action);
		});
	}

	run {
		loop {
			if(actions.size > 0, {
				var consumedActions = IdentitySet();

				//Bundle all the actions of this interval tick together
				//This will be the core of clock / server syncing of actions
				//Consume actions (they are ordered thanks to OrderedIdentitySet)
				actions.do({ | action |
					var condition = action[0];
					var func = action[1];
					var sched = action[2];

					if(condition.value, {
						//If condition, execute it and remove from the OrderedIdentitySet
						this.executeFunc(
							action,
							func,
							sched,
							consumedActions,
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
									exceededMaxSpinTime,
									consumedActions
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
									consumedActions,
								)
							})
						}, {
							//Just accumTime, while letting it go for successive actions.
							//exceededMaxSpinTime is here nil
							this.accumTimeAtAction(
								action,
								condition,
								nil,
								consumedActions
							)
						});
					});
				});

				//Needs to be outside to remove actions
				consumedActions.do({ | action, i |
					actions.remove(action);
					spinningActions.removeAt(action);
				});
			});

			//Check the size again here,
			//as actions are getting removed at the end of the previous if statement
			if(actions.size > 0, {
				//Still actions to consume, spin
				interval.wait;
			}, {
				//No actions to consume, hang
				if(verbose, { ("AlgaScheduler" + name + "hangs").postcln; });
				semaphore.hang;
			});
		}
	}

	//Clear everything
	clearFunc {
		actions.clear;
		spinningActions.clear;
	}

	//Default condition is just { true }, just execute it when its time comes on the scheduler
	addAction { | condition, action, sched = 0 |
		var conditionActionSched;

		condition = condition ? { true };

		if((condition.isFunction.not).or(action.isFunction.not), {
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
				action,
				sched,
				nil
			);

			^this;
		});

		conditionActionSched = [condition, action, sched];

		if(currentExecFunc != nil, {
			("caller func: " ++ currentExecFunc.def.context.asString).error;
			("adding func: " ++ action.def.context.asString).error;
		});

		//If cascadeMode, actions within actions should be correctly placed right after the other.
		//this will make the whole AlgaPatch concept work

		actions.add(conditionActionSched);
		spinningActions[conditionActionSched] = 0; //set to 0 the accumulator of spinningActions

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

/*
+ Function {
	algaPatch {
		^AlgaPatch(this);
	}
}
*/
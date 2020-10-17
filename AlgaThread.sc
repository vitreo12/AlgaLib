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
	var <>maxSpinTime = 1;
	var semaphore;

	var <>cascadeMode = false;

	var <actions, <spinningActions;

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

	executeFunc { | action, condition, func, sched, bundle, consumedActions, scheds |
		var funcBundle;

		if(verbose, (
			"AlgaScheduler: executing function from context '" ++
			condition.def.context ++ "'"
		).postcln);

		//Generate bundle just for this func
		funcBundle = server.makeBundle(false, { func.value });

		//Add this func's bundle to global bundle
		bundle[0] = bundle[0].add(funcBundle);
		//Add timing information for the bundle generated in this func
		scheds[0] = scheds[0].add(sched);

		//Action completed: add to consumedActions
		consumedActions.add(action);
	}

	run {
		loop {
			if(actions.size > 0, {
				var consumedActions = IdentitySet();
				var scheds = [Array()]; //use [] to pass by reference
				var bundle = [Array()]; //use [] to pass by reference

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
							condition,
							func,
							sched,
							bundle,
							consumedActions,
							scheds
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
									condition,
									func,
									sched,
									bundle,
									consumedActions,
									scheds
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

				if(bundle[0].size > 0, {

					//Print bundles out
					if(verbose, { ("AlgaScheduler: sending bundle " ++ bundle[0].asString).warn });
					if(verbose, { ("AlgaScheduler: scheds array: " ++ scheds[0].asString).warn });

					//Send the bundle with each entry at specific scheduled time on clock
					scheds[0].algaSchedBundleArrayOnClock(
						clock,
						bundle[0],
						server,
						server.latency
					);
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

		conditionActionSched = [condition, action, sched];
		actions.add(conditionActionSched);
		spinningActions[conditionActionSched] = 0; //set to 0 the accumulator of spinningActions

		//New action! Unlock the semaphore
		if(semaphore.test.not, {
			semaphore.unhang;
		});
	}
}
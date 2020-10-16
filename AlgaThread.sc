AlgaThread {
	classvar <>verbose = false, <defaultClock;
	var <name, <clock, <task;

	*new { | name = "anon", clock, autostart = true |
		^super.newCopyArgs(name, clock).init(autostart);
	}

	*initClass {
		defaultClock = SystemClock;
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
		if( verbose ) { ("AlgaThread" + name + "is back up.").postcln };
	}

	start {
		if(task.isPlaying, {
			if(verbose, { ("AlgaThread" + name + "already playing.").postcln });
			^this;
		});
		task.reset.play(clock ? defaultClock);
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
	var <>maxSpinTime = 3;
	var semaphore;

	var cascadeMode = false;

	var <actions, <spinningActions;

	*new { | clock, cascadeMode = false, autostart = true |
		var name = UniqueID.next.asString;
		^super.newCopyArgs(name, clock).init(cascadeMode, autostart);
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
				"AlgaScheduler: the condition '" ++
				condition.def.sourceCode ++
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

	executeFunc { | action, func, consumedActions |
		func.value;
		consumedActions.add(action);
	}

	run {
		loop {
			if(actions.size > 0, {
				var consumedActions = IdentitySet();

				//Consume actions (they are ordered thanks to OrderedIdentitySet)
				actions.do({ | action |
					var condition = action[0];
					var func = action[1];

					if(condition.value, {
						//If condition, execute it and remove from the OrderedIdentitySet
						this.executeFunc(action, func, consumedActions)
					}, {
						//if cascadeMode, spin here (so the successive actions won't be
						//done until this one is). If it errors out, it will move to the next action
						if(cascadeMode, {
							var exceededMaxSpinTime = [false]; //use array for a "pass by reference" equivalent

							while({ condition.value.not }, {
								this.accumTimeAtAction(action, condition, exceededMaxSpinTime, consumedActions);
								if(exceededMaxSpinTime[0], {
									condition = true; //exit the while loop
								});
								interval.wait;
							});

							//If finished spinning (or it errors out), execute func
							if(exceededMaxSpinTime[0].not, {
								this.executeFunc(action, func, consumedActions)
							});
						}, {
							//Just accumTime, while letting it go for successive actions
							this.accumTimeAtAction(action, condition, nil, consumedActions);
						});
					});
				});

				//Needs to be outside to remove actions
				consumedActions.do({ | action |
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

	clearFunc {
		actions.clear;
		spinningActions.clear;
	}

	addAction { | condition, func |
		var action = [condition, func];
		if((condition.isFunction.not).or(func.isFunction.not), {
			"AlgaScheduler: addAction only accepts Functions as both the condition and the func arguments".error;
			^this;
		});
		actions.add(action);
		spinningActions[action] = 0;

		//New actions! Unlock the semaphore
		if(semaphore.test.not, {
			semaphore.unhang;
		});
	}
}

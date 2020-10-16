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
	var <actions, <spinningActions;

	*new { | clock, autostart = true |
		var name = UniqueID.next.asString;
		^super.newCopyArgs(name, clock).init(autostart);
	}

	init { | autostart = true |
		if(actions == nil, {
			actions = OrderedIdentitySet(10);
		});

		if(spinningActions == nil, {
			spinningActions = IdentityDictionary(10);
		});

		if(semaphore == nil, {
			semaphore = Condition();
		});

		super.init(autostart);
	}

	run {
		loop {
			if(actions.size > 0, {
				var consumedActions = IdentitySet();

				//Consume actions (they are ordered thansk to OrderedIdentitySet)
				actions.do({ | action |
					var condition = action[0];
					var func = action[1];

					if(condition.value, {
						//If condition, execute it and remove from the Set
						func.value;
						consumedActions.add(action);
					}, {
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
						});

						//Update timing
						spinningActions[action] = accumTime;
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
		actions.add(action);
		spinningActions[action] = 0;
		if(semaphore.test.not, {
			semaphore.unhang;
		});
	}
}

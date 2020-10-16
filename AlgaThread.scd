(
AlgaThread.verbose = true;

~interval = 0.01;
~maxSpinTime = 3;

~actions = OrderedIdentitySet();
~spinningActions = IdentityDictionary();

~semaphore = Condition();

~addAction = { | condition, func |
	var action = [condition, func];
	~actions.add(action);
	~spinningActions[action] = 0;
	if(~semaphore.test.not, {
		~semaphore.unhang;
	});
};

~scheduler = AlgaThread({
	loop {
		if(~actions.size > 0, {
			var consumedActions = IdentitySet();

			//Consume actions (they are ordered thansk to OrderedIdentitySet)
			~actions.do({ | action |
				var condition = action[0];
				var func = action[1];

				if(condition.value, {
					//If condition, execute it and remove from the Set
					func.value;
					consumedActions.add(action);
				}, {
					//Else, check how much time the specific action is taking
					var accumTime = (~spinningActions[action]) + ~interval;

					if(accumTime >= ~maxSpinTime, {
						(
							"AlgaScheduler: the condition '" ++
							condition.def.sourceCode ++
							"' exceeded maximum wait time " ++
							~maxSpinTime
						).error;

						//remove the culprit action
						consumedActions.add(action);
					});

					//Update timing
					~spinningActions[action] = accumTime;
				});
			});

			//Needs to be outside to remove actions
			consumedActions.do({ | action |
				~actions.remove(action);
				~spinningActions.removeAt(action);
			});
		});

		//Check the size again here,
		//as ~actions are getting removed at the end of the previous
		if(~actions.size > 0, {
			//Still actions to consume, spin
			~interval.wait;
		}, {
			//No actions to consume, hang
			"hang".postln;
			~semaphore.hang;
		});
	}
})
)

(
~addAction.({ c == 0 }, { "yay".postln});
~addAction.({ 0 == 0 }, { "yaey".postln});
)

d = 0

~actions
~spinningActions
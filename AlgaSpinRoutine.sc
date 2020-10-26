AlgaSpinRoutine {
	*waitFor { | condition, onComplete, interval = 0.01, maxTime = 3 |
		//First, check if condition is true already, no need to go into a routine if that's the case
		if(condition.value, {
			onComplete.value;
		}, {
			//Spin around condition, then execute onComplete, if not exceeding maximum wait time
			fork {
				var accumTime = 0;
				var exceededMaxTime = false;

				while( { condition.value.not }, {
					interval.wait;
					accumTime = accumTime + interval;
					if(accumTime >= maxTime, {
						"AlgaSpinRoutine: exceeded maximum wait time".error;
						exceededMaxTime = true;
						condition = { true };
					});
				});

				if(exceededMaxTime.not, {
					onComplete.value;
				});
			}
		});
	}
}

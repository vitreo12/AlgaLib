AlgaSpinRoutine {

	*waitFor { | condition, onComplete, time = 0.01 |
		/*
		if(condition.class != Function, {
			"waitFor only accepts a function as condition".error;
			^nil;
		});

		if(onComplete.class != Function, {
			"waitFor only accepts a function as onComplete".error;
			^nil;
		});
		*/

		//Spin around condition, then execute onComplete
		fork {
			while( { condition.value.not }, {
				time.wait;
			});

			onComplete.value;
		}
	}

}
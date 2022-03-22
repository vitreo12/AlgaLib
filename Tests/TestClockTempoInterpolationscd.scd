(
Alga.boot({
	a = AP({ FreeSelf.kr(Impulse.ar(0)) }).play(chans:2);
	~tempo = 1;
});
)

(
~target = 5;
//~wasDone = true;

//Stop previous one
if(r != nil, {
	//Check if it wasn't done already.
	//When done, the .next would return nil
	//if(r.next != nil, { ~wasDone = false });

	//Stop previous one
	r.stop;
});

//Re-assign the new routine
r = Routine.run({
	var env = Env([0, 1, 0, 1], [1, 2, 3]);
	var time = 5;
	var delta = 0.1;
	var counter = 0;
	var done = false;
	var timesSum = env.times.sum;
	var start = ~tempo ? 1; //Lock current tempo

	//Scale time from seconds to interval
	time = 1.0 / time;

	//Start the counter from delta if the previous Routine wasn't done!
	//Maybe this is not a good idea because then the time it will take willbe
	//time - delta !!
	//if(~wasDone.not, { counter = delta * time });

	//Advance time and retrieve values from the Env
	while { done.not } {
		var envVal = env[counter * timesSum];
		~tempo = start.blend(~target, envVal);
		~tempo.postln;
		if(a != nil, { a.clock.tempo = ~tempo });
		counter = counter + (delta * time);
		if(counter >= (1.0 + (delta * time)), { done = true });
		delta.wait;
	}
}, clock: SystemClock)
)

Routine
Alga.interpTempo

sched

schedInSeconds

(
Alga.boot({
	a = AP({ FreeSelf.kr(Impulse.ar(0)) }).play(chans:2);
	~tempo = 1;
});
)

(
~target = 5;
if(r != nil, { r.stop }); //Stop previous one
r = Routine.run({
	var env = Env([0, 1, 0, 1], [1, 2, 3]);
	var time = 5;
	var delta = 0.1;
	var curTime = 0;
	var done = false;
	var timesSum = env.times.sum;
	var start = ~tempo ? 1; //lock tempo
	time = 1.0 / time;
	while { done.not } {
		var envVal = env[curTime * timesSum];
		~tempo = start.blend(~target, envVal);
		~tempo.postln;
		a.clock.tempo = ~tempo;
		curTime = curTime + (delta * time);
		if(curTime >= (1.0 + (delta * time)), { done = true });
		delta.wait;
	}
}, clock: SystemClock)
)
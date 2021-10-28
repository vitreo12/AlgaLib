(
Alga.maxIO=2;
Alga.initSynthDefs;
Alga.boot({
	a = AP((
		def: { SinOsc.ar(\freq.ar(10)) * EnvGen.ar(Env.perc(releaseTime:\rel.kr(1)), doneAction:2) },
		freq: 440,
		rel: 2,
		dur: 0.25
	)).play;
	//s.plotTree
});
)

//Fix this
a.from(100, \freq, time:0, sched:0)

a.from(100, \freq, time:0, sched:1)


a.from(100, \freq, time:0.5, sched:0)

a.from(400, \freq, time:1, sched:0)


a.from(1000, \freq, time:6, sched:1)

a.from(44, \freq, time:0.02, sched:1)

//Repeat this:
//Note that even the old hanging ones will interpolate to the new value. I don't want that.
//Only the new ones should. The old ones, however, need to be kept alive for their
//whole duration, INTERPOLATION TOO! or they will go to 0 if the interpolation is shorter
//than their remaining length!k
a.from(AlgaTemp({ LFNoise1.ar(1000) }, scale:Pseq([200, 500], inf)), \freq, time:5, sched:1)

a.interpolateAllActiveSynths = true
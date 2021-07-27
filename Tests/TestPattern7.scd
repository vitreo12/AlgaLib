(
Alga.boot({

	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441])) * EnvGen.ar(Env.perc(0.01, 0.5), doneAction:2)
	}).add;

	AlgaSynthDef(\test2, {
		Saw.ar(\freq.ar([220, 221]) + \fm.ar(10)) * EnvGen.ar(Env.perc(0.01, 0.5), doneAction:2) * 0.5
	}).add;

	s.sync;

	a = AlgaPattern((
		def: Pseq([\test, \test2], inf),
		dur: 0.25,
		freq: Pseq([440, 220], inf),
	), playTime:2).play;
});
)

a.from(\test, \def, time:2, sched:1)

a.from(\test2, \def, time:3, sched:1).play

a.from(Pseq(\test, \test2, \test2], inf), \def, time:2, sched:1)

//Not working yet
a.from(Pseq([Pseq([\test, \test2]), \test2, \test2], inf), \def, time:2, sched:1)
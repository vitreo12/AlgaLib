(
Alga.boot({

	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441])) * EnvGen.ar(Env.perc(0.01, 0.5), doneAction:2)
	}).add;

	s.sync;

	a = AlgaPattern((
		def: \test,
		dur: 0.25,
		freq: 880,
	), playTime:2).play;
});
)

a.resetParam(\freq, time:3, sched:1)

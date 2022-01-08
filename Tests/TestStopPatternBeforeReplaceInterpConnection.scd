(
Alga.boot({
	AlgaSynthDef(\test, {
		SinOsc.ar(\f.kr(440)) * EnvPerc.ar(release:2)
	}).add;

	AlgaSynthDef(\test2, {
		Saw.ar(\f.kr(440)) * EnvPerc.ar(release:2) * 0.5
	}).add;

	s.sync;

	a = AP((
		def: \test,
		dur: 0.5
	), playTime:2 );

	b = AN({ \in.ar }, [\in, a], interpTime: 2).play
})
)

//stopPatternBeforeReplace doesn't work with interp connections
a.replace(\test2, sched:1)
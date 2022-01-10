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
	//s.plotTree;
})
)

a.from(220, \f, sched: AlgaStep(2))
a.from(440, \f, sched: AlgaStep(0))

//a.stopPatternBeforeReplace=true doesn't work
a.replace(\test2, sched:AlgaStep(1))
a.replace(\test, sched:AlgaStep(1))


//Now fix the fade in play time!!!!
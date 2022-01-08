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
	), playTime:2 ).play;

	//b = AN({ \in.ar }, [\in, a]).play
	//s.plotTree;
})
)

a.from(220, \f, sched: AlgaStep(2))

//The fadein is play's fault!
//a.stopPatternBeforeReplace=true doesn't work
a.replace(\test, sched:AlgaStep(2))


//exec again: AlgaStep(2) will be AlgaStep(0) bug
a.from(440, \f, sched: AlgaStep(2))

a.stopPatternBeforeReplace=true
a.replace(\test2, sched:1)
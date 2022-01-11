(
Alga.boot({
	AlgaSynthDef(\test, {
		SinOsc.ar(\f.kr(440)) * EnvPerc.ar(release:2) * 0.5
	}).add;

	AlgaSynthDef(\test2, {
		Saw.ar(\f.kr(440)) * EnvPerc.ar(release:2) * 0.5
	}).add;

	s.sync;

	a = AP((
		def: \test,
		dur: 1
	), playTime:2 );

	b = AN({ \in.ar }, [\in, a], interpTime: 2).play
	//s.plotTree;
})
)

a.from(220, \f, sched: AlgaStep(2))
a.from(440, \f, sched: AlgaStep(0))

(
a.from(220, \f, sched: AlgaStep(1));
a.replace(\test2, sched:AlgaStep(1));
)

(
a.replace(\test2, sched:AlgaStep(1));
a.from(220, \f, sched: AlgaStep(1));
)


a.from(Pseq([0.25, 0.5], inf), \dur, sched: AlgaStep(2))
a.from(1, \dur, sched: AlgaStep(0))

s.scope


//When merge, support ALgaStep for sustain / legato / stretch too!!!!

//Now fix the fade in play time!!!!
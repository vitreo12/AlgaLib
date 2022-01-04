(
Alga.boot({
	a = AP( (
		def: { SinOsc.ar(\f.kr(440)) * EnvPerc.ar * 0.5 },
		dur: 2
	)).play;

	s.scope
})
)

//The inner version is the one that works, as it would be scheduled rn
a.addScheduledStepAction({ a.makeConnectionInner(220, \f, time:1) }, 2);

//
(
AlgaSynthDef(\test, {
	SinOsc.ar(\f.kr(440)) * EnvPerc.ar * 0.5
}).add;

AlgaSynthDef(\test2, {
	Saw.ar(\f.kr(440)) * EnvPerc.ar * 0.5
}).add
)

//There's a weird "fadein" happening with replace
a.addScheduledStepAction({ a.replaceInner((def: \test2)) }, 0);

//Also, this is a bug:
a.addScheduledStepAction({ a.replaceInner((def: \test)) }, 0);


//Fade in happens with this method too
a.replace(\test2, sched:2)
a.replace(\test, sched:2)
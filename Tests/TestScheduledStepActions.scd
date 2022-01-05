(
Alga.boot({
	AlgaSynthDef(\test, {
		(SinOsc.ar(\f.kr(440)) * EnvPerc.ar * 0.5).poll
	}).add;

	AlgaSynthDef(\test2, {
		Saw.ar(\f.kr(440)) * EnvPerc.ar * 0.5
	}).add;

	s.sync;

	a = AP( (
		def: \test,
		dur: 1
	)).play;

	s.scope
})
)

//The inner version is the one that works, as it would be scheduled rn
a.addScheduledStepAction({ a.makeConnectionInner(220, \f, time:1) }, 2);

//There's a weird "fadein" happening with replace
a.addScheduledStepAction({ a.replace((def: \test2)) }, 0);

//Also, this is a bug: the second trigger onwards will DC
a.addScheduledStepAction({ a.replace((def: \test)) }, 0);



//the issue of fadein is in .play
b = AN({\in.ar}).play;
a >> b;
a.stop;

//no more fade in
a.replace(\test2, sched:2)
a.replace(\test, sched:2)

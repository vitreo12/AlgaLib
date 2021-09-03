(
Alga.boot({
	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.kr(440)) * EnvGen.ar(Env.perc, doneAction:2) * 0.5
	}).add;

	s.sync;

	a = AP((
		def: \test
	)).play
});
)

a.replace((def:\test, freq:220), sched:1);

a.replace((def:\test, freq:440), sched:1);

s.scope
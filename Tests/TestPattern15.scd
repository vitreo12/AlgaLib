(
Alga.boot({
	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.kr(440)) * EnvGen.ar(Env.perc(releaseTime: 0.2), doneAction:2) * 0.5
	}).add;

	s.sync;

	a = AlgaPattern((
		def: \test,
		dur: 0.5
	)).play;
});
)

a.replace((def:\test, freq:220), sched:1);

a.replace((def:\test, freq:440), sched:1);

a.pt = 0.5

a.replace((def:\test, freq:220), sched:1);

a.replace((def:\test, freq:440), sched:1);
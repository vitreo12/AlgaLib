(
Alga.boot({

	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441])) * EnvGen.ar(Env.perc(0.01, 1), doneAction:2)
	}).add;

	s.sync;

	b = AlgaNode({SinOsc.kr([1, 2]).range(10, 1000)});
	a = AlgaPattern((
		def: \test,
		dur: 1,
		freq: Pwhite(400, 1000)
	)).play;

	s.plotTree
});
)

a.makeConnection(\freq, b)
a.makeConnection(\freq, Pseq([0.1, 1]), 2, 1000)

a.makeConnection(\dur, 0.1, 3)

a.inNodes
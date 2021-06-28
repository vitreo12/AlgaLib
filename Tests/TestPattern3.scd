(
Alga.boot({

	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441])) /* * \amp.ar(1) */ * EnvGen.ar(Env.perc(0.01, 1), doneAction:2)
	}).add;

	s.sync;

	a = AlgaPattern((
		def: \test,
		dur: 0.5,
		freq: Pwhite(400, 1000)
	)).play;

	s.plotTree
});
)

a.from(440, \freq);

a.from(Pseq([Pseq([100, [100, 300]]), 2000], inf), \freq)

Alga.initSynthDefs

s.scope

AlgaNode

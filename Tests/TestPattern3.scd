(
Alga.boot({

	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441])) /* * \amp.ar(1) */ * EnvGen.ar(Env.perc(0.01, 1), doneAction:2)
	}).add;

	s.sync;

	a = AlgaPattern((
		def: \test,
		dur: 0.5,
		freq: Pwhite([400, 200], 1000)
	)).play;

	b = AN({ SinOsc.ar([100, 200]).range(200, 300) });

	s.plotTree
});
)

a.from(440, \freq, time:1);

a.from(Pseq([220, 440, 880], inf), \freq, time:5)

a.from(b, \freq, time:5);
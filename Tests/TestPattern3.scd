(
Alga.boot({

	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441])) /* * \amp.ar(1) */ * EnvGen.ar(Env.perc(0.01, 1), doneAction:2)
	}).add;

	AlgaSynthDef(\test2, {
		Saw.ar(\freq.ar([440, 441])) * EnvGen.ar(Env.perc(0.01, 1), doneAction:2) * DC.ar(0.4)
	}).add;

	s.sync;

	a = AlgaPattern((
		def: \test,
		dur: 0.5,
		freq: Pwhite([400, 200], 1000)
	)).play;

	b = AN({ SinOsc.ar([100, 200]) });

	s.plotTree
});
)

a.from(440, \freq, time:1);

a.from(Pseq([220, 440, 880], inf), \freq, time:5)

//scale?
a.from(b, \freq, time:5, scale:[330, 1000]);

//chansMapping?

//replace?
(
a.replace((
	def: \test2,
	dur: 0.5,
	freq: Pwhite([400, 200], 1000)
))
)

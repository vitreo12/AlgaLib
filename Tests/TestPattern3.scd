(
Alga.boot({

	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441])) /* * \amp.ar(1) */ * EnvGen.ar(Env.perc(0.01, 1), doneAction:2)
	}).add;

	s.sync;

	a = AlgaPattern((
		def: \test,
		dur: 1,
		freq: Pwhite([400, 200], 1000)
	)).play;

	//s.plotTree
});
)

a.from(440, \freq, time:1);

a.from(Pseq([220, 440, 880], inf), \freq, time:10)

a.inNodes

b = AN({ SinOsc.ar([100, 200]).range(200, 300) })

a.from(b, \freq, time:20)

Alga.initSynthDefs

s.scope

a.activeInterpSynths

a.interpStreams.interpBusses
a.interpStreams.interpSynths
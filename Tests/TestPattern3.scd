(
Alga.boot({

	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441])) * EnvGen.ar(Env.perc(0.01, 1), doneAction:2)
	}).add;

	s.sync;

	a = AlgaPattern((
		def: \test,
		dur: 1,
		freq: Pwhite(400, 1000)
	)).play;

	//s.plotTree
});
)

a.makeConnection(\freq, Pseq([Pseq([100, 200]), 2000], inf))
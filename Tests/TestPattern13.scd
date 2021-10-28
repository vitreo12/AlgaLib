(
Alga.boot({
	AlgaSynthDef(\test, { SinOsc.ar(\freq.kr(440)) * EnvGen.ar(Env.perc, doneAction:2) }).add;
	s.sync;
	a = AlgaPattern((
		def: \test,
		dur: \manual,
		freq: Pseq([440, 880], inf)
	)).play;
	b = AlgaPattern((
		def: \test,
		freq: Pseq([330, 660], inf)
	)).play
})
)

a.advance(1)
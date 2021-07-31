(
Alga.boot({
	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441, 442])) * EnvGen.ar(Env.perc(0.01, 0.5), doneAction:2)
	}, sampleAccurate:true).add;

	AlgaSynthDef(\test2, {
		Saw.ar(\freq.ar(440)) * EnvGen.ar(Env.perc(0.01, 0.5), doneAction:2) * 0.5
	}, sampleAccurate:true).add;

	s.sync;

	a = AlgaPattern((
		def: Pseq([\test, \test2], inf),
		dur: 0.5,
		freq: Pwhite([100, 220], [440, 880])
	), playTime:1).play
});
)

s.scope
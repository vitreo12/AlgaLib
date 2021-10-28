(
Alga.boot({

	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441])) * EnvGen.ar(Env.perc(0.01, 0.5), doneAction:2)
	}).add;

	AlgaSynthDef(\test2, {
		Saw.ar(\freq.kr([440, 441]) + \fm.kr(10)) * EnvGen.ar(Env.perc(0.01, 0.5), doneAction:2) * 0.5
	}).add;

	s.sync;

	a = AlgaPattern((
		def: \test,
		dur: 0.25,
		freq: 440
	), playTime:2).play;
});
)


a.from(220, \freq, time:2, sampleAndHold:true)
(
Alga.boot({

	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441])) * EnvGen.ar(Env.perc(0.01, 0.1), doneAction:2) * DC.ar(0.5)
	}).add;

	AlgaSynthDef(\test2, {
		Saw.ar(\freq.kr([440, 441])) * EnvGen.ar(Env.perc(0.01, 1), doneAction:2) * DC.ar(0.4)
	}).add;

	s.sync;

	a = AlgaPattern((
		def: \test,
		dur: 1,
		freq: 440
	));

	b = AN({ \in.ar([0, 0]) }, [\in, a], connectionTime:2).play
});
)

//Replace a definition. b should take 2 secs to connect
a.replace(\test2)





//This is AlgaNode's behaviour with replace
(
Alga.boot({
	a = AN({SinOsc.ar});
	c = AN({\in.ar}, [\in, a], connectionTime:1).play(chans:2);
	2.wait;
	a.replace({Saw.ar * 0.5})
});
)

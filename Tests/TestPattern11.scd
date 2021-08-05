(
Alga.boot({
	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441])) * EnvGen.ar(Env.perc(0.01, 0.5), doneAction:2)
	}, sampleAccurate:true).add;

	s.sync;

	b = AN({ [(\in.ar * 3).tanh * 0.5, Silent.ar] });
	c = AN({ [Silent.ar, FreeVerb.ar(\in.ar, 0.8) * 5] });

	d = AN({ Saw.ar(LFNoise0.kr(3).range(50, 300)) * 0.5 });

	b << d;
	c << d;

	a = AlgaPattern((
		def: \test,
		dur: 1,
		freq: Pwhite([100, 220], [440, 880]),
		out: Pseq([
			AlgaOut(
				node: b,
				param: \in,
				scale: Pseq([1, 2], inf),
				chans: Pseq([1, 2], inf),
				time: 1
			),
			AlgaOut(
				node: c,
				param: \in,
				scale: Pseq([1, 2], inf),
				chans: Pseq([1, 2], inf),
				time: 1
			),
			\none
		], inf)
	));
});
)
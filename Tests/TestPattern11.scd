(
Alga.boot({
	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441])) * EnvGen.ar(Env.perc(0.01, 0.5), doneAction:2)
	}, sampleAccurate:true).add;

	s.sync;

	a = AlgaPattern((
		def: \test,
		dur: 1,
		freq: Pwhite([100, 220], [440, 880]),
		out: Pseq([
			(
				node: Pseq([b, c], inf),
				param: \in,
				scale: Pseq([1, 2], inf),
				chans: Pseq([1, 2], inf),
				time: 1 //Gets applied to b, then to c too. It's per-algaNode
			),
			Pseq([b, c], inf),
			\none
		], inf)
	));

	b = AN({ (\in.ar * 3).tanh * 0.5 }).play;
	c = AN({ FreeVerb.ar(\in.ar, 0.8) }).play;
});
)
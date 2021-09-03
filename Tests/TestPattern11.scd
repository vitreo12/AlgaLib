(
Alga.boot({
	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441])) * EnvGen.ar(Env.perc(0.01, 1), doneAction:2)
	}, sampleAccurate:true).add;

	AlgaSynthDef(\test2, {
		Saw.ar(\freq.ar([440, 441])) * EnvGen.ar(Env.perc(0.01, 1), doneAction:2) * 0.5
	}, sampleAccurate:true).add;

	s.sync;

	b = AN({ [(\in.ar * 3).tanh * 0.5, Silent.ar] });
	c = AN({ [Silent.ar, FreeVerb.ar(\in.ar, 0.8) * 10] });

	d = AN({ Saw.ar(LFNoise0.kr(3).range(50, 300)) * 0.01 });
	b << d;
	c << d;

	//mixer to avoid all things with playTime
	h = AN({\in.ar([0, 0])}).play;
	h << b; h <<+c;

	a = AlgaPattern((
		def: \test,
		dur: 0.25,
		freq: Pwhite([100, 220], [440, 880]),
		out: Pseq([
			AlgaOut(
				node: b,
				param: \in
			),
			b,
			\none,
			AlgaOut(
				node: c,
				param: \in
			),
			c
		], inf)
	));

	//s.plotTree;
	//s.scope;
});
)

//New connection
a.from(AlgaOut(b, \in), \out, time:2, sched:1)

//Replacing the pattern
a.replace(\test2, time:3, sched:1)

//New connection
a.from(PSeq([b, c]), \out, time:2, sched:1)

//Replacing a receiver
b.replace({ [(\in.ar * 5).clip * 0.5, Silent.ar] }, time:5)
b.replace({ [\in.ar * 3, Silent.ar] }, time:10)
(
Alga.boot({
	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441])) * EnvGen.ar(Env.perc(0.01, 1), doneAction:2)
	}, sampleAccurate:true).add;

	AlgaSynthDef(\test2, {
		Saw.ar(\freq.ar([440, 441])) * EnvGen.ar(Env.perc(0.01, 1), doneAction:2) * 0.5
	}, sampleAccurate:true).add;

	SynthDef(\debug, {
		\in.ar(0).poll
	}).add;

	AlgaSynthDef(\playOut, {
		[\in.ar(0), DC.ar(1)]
	}).add;

	s.sync;

	b = AN({ [(\in.ar * 3).tanh * 0.5, Silent.ar] }).play;
	c = AN({ [Silent.ar, FreeVerb.ar(\in.ar, 0.8) * 10] }).play;

	//b = AN({ [\in.ar, Silent.ar] }).play;
	//c = AN({ [Silent.ar, \in.ar] }).play;

	//d = AN({ Saw.ar(LFNoise0.kr(3).range(50, 300)) * 0.5 });
	//b << d;
	//c << d;

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

	s.plotTree;
	s.scope;
});
)

//With a new connection, what should happen? How to handle a fadeIn / fadeOut?
a.from(AlgaOut(b, \in), \out, time:2, sched:1)

//When replacing, what should happen? How to handle a fadeIn / fadeOut?
a.replace(\test2, time:3, sched:1)

//When replacing and resetting?
a.replace(\test, time:3, sched:1, reset:true)

//When replacing one of the receivers?
b.replace({ (\in.ar * 3).clip * 0.5 });
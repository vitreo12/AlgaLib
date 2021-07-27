(
Alga.boot({

	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441])) * EnvGen.ar(Env.perc(0.01, 0.5), doneAction:2)
	}).add;

	AlgaSynthDef(\tanh,{
		\in.ar(0).tanh * \dist.ar(1)
	}).add;

	AlgaSynthDef(\reverb, {
		var sig = FreeVerb.ar(\in.ar(0), 1, \room.kr(0.5));
		DetectSilence.ar(sig, doneAction:2);
		sig
	}).add;

	s.sync;

	a = AlgaPattern((
		def: \test,
		dur: 0.5,
		freq: Pseq([440, 220], inf),
		fx: Pseq([
			(def: \tanh, \dist: Pseq([1, 2], inf)),
			(def: \reverb, \room: Pseq([0.3, 0.6, 1], inf))
		], inf)
	), playTime:2).play;

	b = AN({ SinOsc.kr(5) });
});
)

a.from((def:\tanh, \dist:Prand([1, 2], inf)), \fx, time:2, sched:1)

a.from((def:\reverb, \room:Prand([0.2, 1], inf)), \fx, time:2, sched:1)

(
a.from(
	Prand([
		(def:\reverb, \room:Prand([0.2, 1], inf)),
		(def:\tanh, \dist:Prand([1, 2], inf)),
		\none //dry
	], inf), \fx, time:2, sched:1)
)

//Modulation with other Nodes
(
a.from(
	(
		def: \reverb,
		\room: Pseq([
			AlgaPatternArg(b, scale:[0, 1]),
			b
		], inf)
	),
	\fx, time:2, sched:1
)
)
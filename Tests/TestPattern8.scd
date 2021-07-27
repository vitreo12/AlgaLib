(
Alga.boot({

	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441])) * EnvGen.ar(Env.perc(0.01, 0.5), doneAction:2)
	}).add;

	AlgaSynthDef(\tanh,{
		(\in.ar(0) * \dist.kr(2)).tanh * 0.5
	}).add;

	AlgaSynthDef(\comb, {
		var sig = CombC.ar(\in.ar(0), delaytime:\dt.kr(0.1));
		DetectSilence.ar(sig, doneAction:2);
		sig
	}).add;

	s.sync;

	a = AlgaPattern((
		def: \test,
		dur: 0.25,
		freq: Pwhite([100, 220], [440, 880]),
		fx: Pseq([
			(def: \tanh, \dist: Pseq([1, 2, 4, 10], inf)),
			(def: \comb, \dt: Pseq([0.01, 0.05, 0.1], inf))
		], inf)
	), playTime:2).play;

	s.plotTree;

	b = AN({ SinOsc.kr(5) });
});
)

a.from((def:\tanh, \dist:Prand([1, 2], inf)), \fx, time:2, sched:1)

a.from((def:\comb, \dt:Prand([0.01, 0.2], inf)), \fx, time:2, sched:1)

(
a.from(
	Prand([
		(def:\comb, \dt:Prand([0.01, 0.2], inf)),
		(def:\tanh, \dist:Prand([1, 2], inf)),
		\none //dry
	], inf), \fx, time:2, sched:1)
)

//Modulation with other Nodes
(
a.from(
	(
		def: \tanh,
		\dist: Pseq([
			AlgaPatternArg(b, scale:[1, 3]),
			b
		], inf)
	),
	\fx, time:2, sched:1
)
)
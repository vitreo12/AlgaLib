(
Alga.boot({
	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441])) * EnvGen.ar(Env.perc(0.01, 0.5), doneAction:2)
	}, sampleAccurate:true).add;

	AlgaSynthDef(\tanh,{
		(\in.ar([0, 0]) * \dist.ar([2, 2])).tanh * 0.5
	}).add;

	AlgaSynthDef(\comb, {
		var sig = CombC.ar(\in.ar([0, 0, 0]), delaytime:\dt.kr(0.1));
		FreeSelf.kr(DetectSilence.ar(sig).product);
		sig;
	}).add;

	AlgaSynthDef(\noise1, {
		LFNoise1.ar(\freq.kr([1, 2]))
	}).add;

	s.sync;

	a = AlgaPattern((
		def: \test,
		dur: 0.1,
		freq: Pwhite([100, 220], [440, 880]),
		fx: Pseq([
			(def: \tanh, dist: Pseq([1, 2, 4, 10], inf)),
			(def: \comb, dt: Pseq([0.01, 0.05, 0.1], inf))
		], inf)
	), playTime:1).play;

	//s.scope;

	b = AN({ SinOsc.kr(5) });
});
)

s.plotTree

a.from((def:\tanh, dist:Prand([1, 2, 5], inf)), \fx, time:0, sched:1)
a.from((def:\comb, dt:Prand([0.01,  0.01], inf)), \fx, time:0, sched:1)

(
a.from(
	Prand([
		(def:\comb, dt:Prand([0.01, 0.2], inf)),
		(def:\tanh, dist:Prand([1, 2], inf)),
		\none //dry
	], inf), \fx, time:2, sched:1)
)

//Modulation with other Nodes
(
a.from(
	(
		def: \tanh,
		dist: Pseq([
			AlgaArg(b, scale:[1, 10]),
			b
		], inf)
	),
	\fx, time:2, sched:1
)
)

//Modulation with AlgaTemp
(
a.from(
	(
		def: \tanh,
		dist: AlgaTemp((
			def: \noise1,
			freq: Pwhite([10, 20], [100, 200])
		), scale: [1, 7])
	),
	\fx, time:2, sched:1
)
)

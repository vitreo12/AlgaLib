(
Alga.boot({
	AlgaSynthDef(\sine, {
		SinOsc.ar(\freq.ar(440)) * EnvPerc.ar(release: 0.5)
	}, sampleAccurate: true).add;

	AlgaSynthDef(\tanh,{
		(\in.ar(0) * \dist.kr(3)).tanh
	}).add;

	AlgaSynthDef(\comb, {
		var comb = CombC.ar(\in.ar(0), delaytime: \dt.kr(0.1));
		FreeSelf.kr(DetectSilence.ar(comb));
		comb;
	}).add;

	AlgaSynthDef(\noise1, {
		LFNoise1.kr(\freq.kr(1))
	}).add;

	s.sync;

	a = AlgaPattern((
		def: \sine,
		dur: 0.5,
		freq: Pwhite(220, 880),
		fx: (
			def: \tanh,
			dist: Pseq([1, 2, 4, 6],inf)
		)
	)).play(chans: 2);
});
)

(
a.from(
	Pseq([
		(def: \tanh, dist: Pseq([1, 2, 4, 10], inf)),
		(def: \comb, dt: Pseq([0.01, 0.05, 0.1], inf)),
		\dry
	], inf),
	\fx,
	time: 2,
	sched: 1
)
)

b = AN({ SinOsc.kr(2) });

//Modulation with other Nodes
(
a.from((
	def: \tanh,
	dist: Pseq([
		AlgaArg(b, scale:[1, 10]),
		Pwhite(1, 3, 1)
	], inf)
),
\fx, time: 2, sched: 1
)
)

//Modulation with AlgaTemp
(
a.from((
	def: \tanh,
	dist: AlgaTemp((
		def: \noise1,
		freq: Pwhite([10, 20], [100, 200])
	), scale: [1, 7])
),
\fx, time: 2, sched: 1
)
)

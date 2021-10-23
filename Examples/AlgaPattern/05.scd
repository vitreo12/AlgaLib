(
Alga.boot({
	//Simple sine perc
	AlgaSynthDef(\sine, {
		SinOsc.ar(\freq.ar(440)) * EnvPerc.ar(release: 0.5)
	}, sampleAccurate: true).add;

	//Tanh distortion
	AlgaSynthDef(\tanh,{
		(\in.ar(0) * \dist.kr(3)).tanh
	}).add;

	//Comb filtering
	AlgaSynthDef(\comb, {
		var comb = CombC.ar(\in.ar(0), delaytime: \dt.kr(0.1));
		FreeSelf.kr(DetectSilence.ar(comb));
		comb;
	}).add;

	//A simple stepped noise
	AlgaSynthDef(\noise0, {
		LFNoise0.kr(\freq.kr(1))
	}).add;

	//A simple linear noise
	AlgaSynthDef(\noise1, {
		LFNoise1.kr(\freq.kr(1))
	}).add;

	//Wait
	s.sync;

	//Create a pattern using the special 'fx' key.
	//'fx' allows to specify an effect node to be applied at the trigger of each pattern instance.
	a = AlgaPattern((
		def: \sine,
		dur: 0.5,
		freq: Pwhite(220, 880),
		fx: (
			def: \tanh,
			dist: Pseq([1, 2, 4, 6],inf)
		)
	)).play(chans: 2);

	s.plotTree
});
)

//Modifying the 'fx' key triggers 'replace'.
//The Pseq will apply tanh, comb and then let the original sound through (\dry).
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

//Declare an AlgaNode
b = AN({ SinOsc.kr(10) });

//'fx' parameter can be modulated just like any other AlgaPattern parameter.
//In this case, the \dist is getting modulated by b.
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

//Again, just like any other AlgaPattern, 'fx' parameters can be modulated using AlgaTemps.
(
a.from((
	def: {
		var sig = Pluck.ar(\in.ar, delaytime: \dt.ar(0.1));
		FreeSelf.kr(DetectSilence.ar(sig));
		sig
	},
	dt: Pseq([
		AlgaTemp(
			{ SinOsc.ar(1) },
			scale: [0.0001, 0.01]
		),

		AlgaTemp((
			def: \noise0,
			freq: Pwhite([1, 5], [5, 10])
		), scale: [0.0001, 0.02]),

		AlgaTemp((
			def: \noise1,
			freq: Pwhite([10, 20], [30, 40])
		), scale: [0.0001, 0.03])
	], inf)
),
\fx, time: 2, sched: 1
)
)

//BUG: old ones are kept alive!
a.replace({ Saw.ar(\freq.kr(440)) * EnvPerc.ar }, time: 1, sched: 1)
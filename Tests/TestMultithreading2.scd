(
Alga.boot({
	//Simple sine perc
	AlgaSynthDef(\sine, {
		Mix(Array.fill(100, { SinOsc.ar(\freq.kr(440)) })) * 0.01 * EnvPerc.ar(release: 0.5)
	}, sampleAccurate: true).add;

	//Tanh distortion
	AlgaSynthDef(\tanh,{
		(\in.ar(0) * \dist.kr(3)).tanh
	}).add;

	//Comb filtering
	AlgaSynthDef(\comb, {
		var comb = LeakDC.ar(CombC.ar(\in.ar(0), delaytime: \dt.kr(0.1)));
		AlgaDetectSilence.ar(comb);
		comb;
	}).add;

	//A simple stepped noise (used later for modulation)
	AlgaSynthDef(\noise0, {
		LFNoise0.ar(\freq.kr(1))
	}).add;

	//A simple linear noise (used later for modulation)
	AlgaSynthDef(\noise1, {
		LFNoise1.ar(\freq.kr(1))
	}).add;

	//Wait
	s.sync;

	//Create a pattern using the special 'fx' key.
	//'fx' allows to specify an effect node to be applied at the trigger of each pattern instance.
	a = AlgaPattern((
		def: \sine,
		dur: 0.00625,
		freq: Pwhite(220, 880),
		fx: (
			def: \tanh,
			dist: Pseq([1, 2, 4, 6],inf)
		)
	)).play(chans: 2);

	fork {
		loop {
			s.avgCPU.postln;
			1.wait;
		}
	};
},
//Comment this line for scsynth
algaServerOptions: AlgaServerOptions(supernova: true)
);
)

/*
scsynth: 44.4

supernova: 15.3

gain: 2.9
*/

(
a.from((
	def: {
		var pluck = LeakDC.ar(Pluck.ar(\in.ar, delaytime: \dt.ar(0.1)));
		AlgaDetectSilence.ar(pluck);
		pluck
	},
	dt: Pseq([
		AlgaTemp(
			{ SinOsc.ar(1) },
			scale: [0.001, 0.03]
		),

		AlgaTemp((
			def: \noise0,
			freq: Pwhite([1, 5], [5, 10])
		), scale: [0.001, 0.02]),

		AlgaTemp((
			def: \noise1,
			freq: Pwhite([10, 20], [30, 40])
		), scale: [0.001, 0.01])
	], inf)
),
\fx, time: 2, sched: 1
)
)

/*
scsynth: GLITCHES

supernova: 24

gain: inf :)

*/

a.stopPattern
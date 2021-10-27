(
Alga.boot({
	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441])) * EnvGen.ar(Env.perc(0.01, 0.5), doneAction:2)
	}, sampleAccurate:true).add;

	AlgaSynthDef(\noise0, {
		LFNoise0.ar(\freq.kr([1, 2, 3]))
	}).add;

	AlgaSynthDef(\noise1, {
		LFNoise1.ar(\freq.kr([1, 2, 3, 4]))
	}).add;

	AlgaSynthDef(\consts, {
		DC.ar([1,2,3])
	}).add;

	s.sync;

	a = AlgaPattern((
		def: \test,
		dur: 1,
		freq: Pseq([
			AlgaTemp((
				def: \noise0,
				freq: Pwhite([1, 1], [100, 100])
			), scale: [300, 1000]),
			AlgaTemp((
				def: \noise1,
				freq: Pwhite([1, 1], [100, 100])
			), scale: [100, 400]),
			Pwhite(300, 500, 1)
		], inf);
	), playTime:1).play;
});
)

//AlgaTemp can contain other AlgaTemps
(
a.from(
	AlgaTemp((
		def: \noise1,
		freq: AlgaTemp((
			def: \noise0,
			freq: Pwhite([10, 100])
		), scale: [5, 5])
	), scale: Pseq([[250, 500], [330, 1331]], inf)),
	\freq,
	time: 5
)
)
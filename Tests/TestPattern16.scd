//Funcs with def
(
Alga.boot({
	a = AlgaPattern((
		def: Pseq([
			{ SinOsc.ar(\freq.kr(440)) * EnvGen.ar(Env.perc, doneAction: 2) },
			{ Saw.ar(\freq.kr(440)) * 0.5 * EnvGen.ar(Env.perc, doneAction: 2) }
		], inf
		),
		dur: Pwhite(0.05, 0.4),
		freq: Pwhite(220, 660)
	)).play
})
)

//Funcs with AlgaTempss
(
Alga.boot({
	a = AlgaPattern((
		def: Pseq([
			{ SinOsc.ar(\freq.kr(440)) * EnvGen.ar(Env.perc, doneAction: 2) },
			{ Saw.ar(\freq.kr(440)) * 0.5 * EnvGen.ar(Env.perc, doneAction: 2) }
		], inf
		),
		dur: 1,
		freq: Pseq([
			AlgaTemp(
				{ LFNoise0.kr(5) },
				scale: [200, 500]
			),
			AlgaTemp(
				{ LFNoise1.kr(8)},
				scale: [100, 1000]
			),
			AlgaTemp((
				def: { SinOsc.kr(\freq.kr(1)) },
				freq: Pseq([1, 5], inf)
			), scale: [500, 2000])
		], inf)
	)).play
})
)
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

//Funcs with AlgaTemps
(
Alga.boot({
	a = AlgaPattern((
		def: Prand([
			{ SinOsc.ar(\freq.kr(440)) * EnvGen.ar(Env.perc(releaseTime: \rel.kr(1)), doneAction: 2) },
			{ Saw.ar(\freq.kr(440)) * 0.7 * EnvGen.ar(Env.perc(releaseTime: \rel.kr(1)), doneAction: 2) }
		], inf
		),
		dur: Pwhite(0.01, 0.3),
		rel: Pwhite(0.02, 0.5),
		freq: Prand([
			AlgaTemp(
				{ LFNoise0.kr(5) },
				scale: Prand([[200, 500], [100, 420]])
			),
			AlgaTemp(
				{ LFNoise1.kr(8) },
				scale: [100, 1000]
			),
			AlgaTemp((
				def: { SinOsc.kr(\freq.kr(1)) },
				freq: Pwhite(1, 5)
			), scale: Prand([[250, 650], [100, 3500]]))
		], inf)
	)).play(chans:2)
})
)

//Support this too
scale:[Pwhite(10, 20), Pwhite(200, 40)]

//Funcs with \fx
(
Alga.boot({
	a = AlgaPattern((
		def: Prand([
			{ SinOsc.ar(\freq.kr(440)) * EnvGen.ar(Env.perc(releaseTime: \rel.kr(1)), doneAction: 2) },
			{ Saw.ar(\freq.kr(440)) * 0.5 * EnvGen.ar(Env.perc(releaseTime: \rel.kr(1)), doneAction: 2) }
		], inf
		),

		fx: Pseq([
			{ (\in.ar * 10).tanh * 0.5 },
			(
				def: { CombC.ar(\in.ar, delaytime:\dt.kr(0.05)) },
				dt: Pwhite(0.001, 0.2)
			)
		], inf),

		freq: Pwhite(100, 300),

		dur: Pwhite(0.1, 1)
	)).play(chans:2)
})
)
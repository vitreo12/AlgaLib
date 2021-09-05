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
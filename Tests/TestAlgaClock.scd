(
Alga.boot({
	a = AP({ SinOsc.ar * EnvGen.kr(Env.perc, doneAction:2) }).play
})
)

Alga.clock.tempo = 1
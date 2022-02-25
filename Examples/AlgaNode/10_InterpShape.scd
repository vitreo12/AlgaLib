(
Alga.boot({
	a = AlgaNode(
		{ SinOsc.ar(\freq.kr(440)) },
		interpTime: 3,
		interpShape: Env([0, 1, 0.5, 1], [1, 0.5, 1])
	).play(chans: 2)
})
)

//The connection will use the Env declared in interpShape
a <<.freq 220;

//Temporary Env (standard ramp)
a.from(880, \freq, shape: Env([0, 1]))

//Using the original interpShape
a <<.freq 440;
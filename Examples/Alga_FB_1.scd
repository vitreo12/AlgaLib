(
//First argument of Alga.boot can be a function to be executed
//as soon as server boots, similarly to Server.waitForBoot
Alga.boot({
	a = AlgaNode({ SinOsc.ar(\freq.kr(50) + \fm.ar(0)) });
	b = AlgaNode({ Saw.ar(\freq.kr(3) + \fm.ar(0)) });
	a.from(b, \fm, scale:[-200, 200]);
	b.from(a, \fm, scale:[-50, 50]); //feedback connection!
	a.play(chans:2); //play stereo
})
)


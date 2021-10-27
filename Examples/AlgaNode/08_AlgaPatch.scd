//Boot Alga, executing an AlgaPatch as soon as server boots
(
Alga.boot({
	AlgaPatch({
		a = AlgaNode({ SinOsc.ar(\freq.kr(220) + \fm.ar(0)) }, interpTime:5, playTime:0.5);
		b = AlgaNode({ SinOsc.ar(\freq.kr(134) + \fm.ar(0)) }, interpTime:3);
		a.from(b, \fm, scale:[-100, 100]);
		b.from(a, \fm, scale:[-50, 50]); //feedback connection!

		a.play(chans:2); //play stereo

		//Change root frequencies
		b <<.freq 25;
		a <<.freq 103;

		//Change the oscillator implementation, using a Saw wave instead
		b.replace({ Saw.ar(\freq.kr + \fm.ar) })
	});
});
)


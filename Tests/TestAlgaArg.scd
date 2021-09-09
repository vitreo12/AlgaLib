(
Alga.boot({
	b = AN({ SinOsc.ar(10) });

	a = AN({ SinOsc.ar(\freq.ar(440)) }, [\freq, AlgaArg(b, scale:[450, 1000])]).play;
})
)

c = AN({ LFNoise1.ar(100) });

a.from(AlgaArg(c, scale:[100, 700]), time:2);

a.pt = 2; a.replace({ Saw.ar(\freq.ar(440)) });

a.reset(\freq, 2);
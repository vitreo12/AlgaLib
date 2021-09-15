(
Alga.boot({
	b = AN({ SinOsc.ar(1) });

	a = AN({ SinOsc.ar(\freq.ar(440)) * 0.5 }, [\freq, AlgaArg(b, scale:[450, 590])]).play;
})
)

c = AN({ LFNoise1.ar(100) });

a.from(AlgaArg(c, scale:[100, 700]), \freq, time:2);

a.pt = 2; a.replace({ Saw.ar(\freq.ar(440)) * 0.2 });

a.reset(\freq, 2);
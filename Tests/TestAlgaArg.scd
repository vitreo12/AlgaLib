(
Alga.boot({
	b = AN({ SinOsc.ar(100) });
	a = AN({ SinOsc.ar(\freq.ar(440)) * 0.5 }, [\freq, AlgaArg(b, scale:[450, 590])]).play(chans:2);
})
)

c = AN({ LFNoise0.ar(1) });

a.mixFrom(c, \freq, scale:[100, 700], time:2);

a.pt = 2; a.replace({ Saw.ar(\freq.ar(440)) * 0.2 });

a.reset(\freq, time:2);

b.clear(2)

a.reset(\freq, time:2);

a.clear(2)
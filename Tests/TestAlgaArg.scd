(
Alga.boot({
	b = AN({ SinOsc.ar(10) });

	a = AN({ SinOsc.ar(\freq.ar(440)) }, [\freq, AlgaArg(b, scale:[450, 1000])]).play;
})
)
(
Alga.boot({
	a = AN.debug({ SinOsc.ar(\freq.ar(440)) }, name: \a);
	b = AN.debug({ SinOsc.ar(\freq.ar(440)) }, name: \b);
	a >> b;
});
)

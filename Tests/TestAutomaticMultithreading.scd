(
Alga.boot({
	a = AN.debug({ SinOsc.ar(\freq.ar(440)) }, name: \a);
	b = AN.debug({ SinOsc.ar(\freq.ar(440)) }, name: \b);
	a >>.freq b;
});
)

a
b

b.blockIndex
a.blockIndex
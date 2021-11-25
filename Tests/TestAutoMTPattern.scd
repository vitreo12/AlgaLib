(
Alga.boot({
	a = AN.debug({ DC.ar }, name: \a);
	b = AN.debug({ DC.ar }, name: \b);
	c = AN.debug({ DC.ar }, name: \c);

	d = AP.debug((
		def: { DC.ar(\in.ar) },
		in: Pseq([a, b], inf)
	), interpTime: 3, name: \d);
})
)

d.from(c)
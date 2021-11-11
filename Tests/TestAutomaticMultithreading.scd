//Network
(
Alga.boot({
	a = AN.debug({ DC.ar(\in.ar) }, name: \a);
	b = AN.debug({ DC.ar(\in.ar) }, name: \b);
	c = AN.debug({ DC.ar(\in.ar) }, name: \c);
	d = AN.debug({ DC.ar(\in.ar) }, name: \d);
	e = AN.debug({ DC.ar(\in.ar) }, name: \e);
	f = AN.debug({ DC.ar(\in.ar) }, name: \f);
	g = AN.debug({ DC.ar(\in.ar) }, name: \g);
	h = AN.debug({ DC.ar(\in.ar) }, name: \h);
	i = AN.debug({ DC.ar(\in.ar) }, name: \i);
	l = AN.debug({ DC.ar(\in.ar) }, name: \l);
	k = AN.debug({ DC.ar(\in.ar) }, name: \k);

	a >>+ f;
	d >>+ b;
	d >>+ e;
	b >>+ c;
	e >>+ f;
	c >>+ f;
	f >>+ g;
	f >>+ h;
	h >>+ l;
	e >>+ i;
	e >>+ k;
	k >>+ l;
	i >>+ l;
});
)

//If feedback, its position should be determined by the OTHER inputs the node has.
//If all inputs are feedback, it does not really matter if it's before or after the other node,
//it only matters that they are NOT in the same Group.

//Multi-feedback network
(
Alga.boot({
	a = AN.debug({ DC.ar(\in.ar) }, name: \a);
	b = AN.debug({ DC.ar(\in.ar) }, name: \b);
	c = AN.debug({ DC.ar(\in.ar) }, name: \c);

	a >>+ b;
	b >>+ a;
	c >>+ a;
	c >>+ b;
	b >>+ c;
	a >>+ c;
});
)
//Execute this multiple times: sometimes g gets removed as it's never reached.
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
	b >>+ f;
	c >>+ f;
	d >>+ e;
	e >>+ f;
	f >>+ g;
	e >>+ i;
	e >>+ k;
	f >>+ h;
	h >>+ l;
	i >>+ l;
	/* k >>+ l;
	k >>+ d;
	g >>+ a;
	l >>+ b; */

	//This is to test freeUnusedFeedbacks
	//h <| \in;
	//l >>+ b;

	//s.plotTree;
},
);
)
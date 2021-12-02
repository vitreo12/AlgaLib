(
Alga.boot({
	a = AN.debug({ DC.ar(\in.ar) }, name: \a);
	b = AN.debug({ DC.ar(\in.ar) }, interpTime: 1, name: \b);
	c = AN.debug({ DC.ar(\in.ar) }, interpTime: 2, name: \c);

	a >> b;
	b >> c;
});
)

//This is a bug: \b should be kept inside of the same group and processed
//as long as the interpolation goes (interpTime = 3), despite it not having inNodes anymore
//as \a was disconnected. As of now, \b could be repatched (see next example) to another block,
//without considering the position of \a (which is still being processed for 3 seconds)
(
b.disconnect;
a >> c;
)

//Bug if ran again
a >> c;

//This can also be applied when 2 different blocks are involved
(
Alga.boot({
	a = AN.debug({ DC.ar(\in.ar) }, name: \a);
	b = AN.debug({ DC.ar(\in.ar) }, interpTime: 1, name: \b);
	c = AN.debug({ DC.ar(\in.ar) }, name: \c);
	d = AN.debug({ DC.ar(\in.ar) }, interpTime: 2, name: \d);
	e = AN.debug({ DC.ar(\in.ar) }, name: \e);

	//block 1
	a >> b;

	//block 2
	c >> d;
});
)

//Now, despite \b being repatched to \d, the disconnection from \a should last 3 seconds.
//As of now, that's ignored and \b is taken out of block 1 and put into block 2 right away.
//If the positions of the blocks in the server is different, this can cause glitches as
// \b is actually still reading from \a being its input for 3 more seconds
(
b.disconnect;

b >>+ d;

//block 3 (try with fb too)
a >> e;
e >> a;
)


//BUG with activeInNodes
(
Alga.boot({
	a = AN.debug({ DC.ar(\in.ar) }, name: \a);
	e = AN.debug({ DC.ar(\in.ar) }, name: \e);

	a >> e;
	e >> a;
})
)

a >> e;
e >> a;

a.disconnect

//Inf loop
e.disconnect


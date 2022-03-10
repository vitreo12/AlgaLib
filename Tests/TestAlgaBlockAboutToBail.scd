(
Alga.boot({
	Alga.debug = true;

	a = AN.debug({\in.ar}, name: \a);
	b = AN.debug({\in.ar}, name: \b, interpTime: 2);
	c = AN.debug({\in.ar}, name: \c);
	d = AN.debug({\in.ar}, name: \d);

	e = AN.debug({\in.ar}, name: \e);

	a >> b;
	b >> c;
	c >>+ b;
	b >> d;
})
)

(
a.blockIndex.postln;
b.blockIndex.postln;
c.blockIndex.postln;
d.blockIndex.postln;
)

(
d.disconnect;
b.disconnect;
c.disconnect;
)

//Without the aboutToBail check in orderNodes, this fails
//as the c node is actually not feedback anymore, but still present
//in the block.
(
b >> c;
b >> d;
c >>+ b;
a >> b;
)

Alga.boot({ s.plotTree });
Alga.boot;

(
b = AlgaNode({SinOsc.ar(134 + \fm.ar(0))}, connectionTime:2);
a = AlgaNode({SinOsc.ar(220 + \fm.ar(0))}, connectionTime:3);
c = AlgaNode({\in.ar(0).range(-100, 100)}, connectionTime:2);
d = AlgaNode({\in.ar(0).range(-50, 50)}, connectionTime:4);

o = AlgaNode({\in.ar(0).dup}, connectionTime:3, playTime:3).play;

c << b;
a <<.fm c;

d << a;
b <<.fm d;

a >> o;
)

(
//Changes FT when replacing c / d
b.ct = 3;
a.ct = 5;

//Changes FT when replacing a / b
c.ct = 3;
d.ct = 4;
)

d.replace({\in.ar(0).range(-10.12312, 10.23)})

c.replace({\in.ar(0).range(-30, rrand(10, 100))});

(
fork {
	loop {
		c.replace({\in.ar(0).range(-30, rrand(10, 100))});
		3.wait;
	}
}
)

s.plotTree

b.replace({SinOsc.ar(1 + \fm.ar(0))})

a.replace({SinOsc.ar(1 + \fm.ar(0))})

b.replace({Saw.ar(1 + \fm.ar(0))})
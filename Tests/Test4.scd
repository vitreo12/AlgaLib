Alga.boot;

s.plotTree;

(
b = AlgaNode({SinOsc.ar(134 + \fm.ar(0))}, fadeTime:1);
a = AlgaNode({SinOsc.ar(220 + \fm.ar(0))}, fadeTime:3);
c = AlgaNode({\in.ar(0).range(-100, 100)}, fadeTime:2);
d = AlgaNode({\in.ar(0).range(-50, 50)}, fadeTime:4);

i = AlgaNode({\in.ar(0).dup}, fadeTime:1);
o = AlgaNode({\in.ar(0).dup}, fadeTime:1).play;

c << b;
a <<.fm c;

d << a;
b <<.fm d;

a >> i;

i >> o;
)

(
//Changes FT when replacing c / d
b.ft = 3;
a.ft = 5;

//Changes FT when replacing a / b
c.ft = 3;
d.ft = 4;

//Changes a FT in terms of output
i.ft = 4;
)

c.ft = 20

s.plotTree

d.replace({\in.ar(0).range(-13.12312, 50.23)})

c.replace({\in.ar(0).range(-30, rrand(10, 230))});

(
fork {
	loop {
		c.replace({\in.ar(0).range(-30, rrand(10, 100))});
		4.wait;
	}
}
)

b.replace({SinOsc.ar(1 + \fm.ar(0))})

a.replace({SinOsc.ar(1 + \fm.ar(0))})

b.replace({Saw.ar(1 + \fm.ar(0))})


a >> i
b >> i
a >> i

b.outNodes //b.outNodes @[i] should've been removed...
a.outNodes
i.inNodes
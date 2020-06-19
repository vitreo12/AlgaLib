Alga.boot({ s.plotTree });

(
b = AlgaNode({SinOsc.ar(134 + \fm.ar(0))}, fadeTime:1);
a = AlgaNode({SinOsc.ar(220 + \fm.ar(0))}, fadeTime:3);
c = AlgaNode({\in.ar(0).range(-100, 100)});
d = AlgaNode({\in.ar(0).range(-50, 50)});

o = AlgaNode({\in.ar(0).dup}, fadeTime:1).play;

c << b;
a <<.fm c;

d << a;
b <<.fm d;

a >> o;
)

d.replace({\in.ar(0).range(-10.12312, 102.23)})

b.replace({SinOsc.ar(10 + \fm.ar(0))})

a.replace({SinOsc.ar(220 + \fm.ar(0))})

b.replace({Saw.ar(\f.ar(222) + \fm.ar(0))})

s.plotTree

(
o.group.postln;
a.group.postln;
b.group.postln;
c.group.postln;
d.group.postln;
)

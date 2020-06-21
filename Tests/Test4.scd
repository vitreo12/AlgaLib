Alga.boot;

(
b = AlgaNode({SinOsc.ar(134 + \fm.ar(0))}, fadeTime:1);
a = AlgaNode({SinOsc.ar(220 + \fm.ar(0))}, fadeTime:3);
c = AlgaNode({\in.ar(0).range(-100, 100)});
d = AlgaNode({\in.ar(0).range(-50, 50)});

i = AlgaNode({\in.ar(0).dup}, fadeTime:1);
o = AlgaNode({\in.ar(0).dup}, fadeTime:1).play;

c << b;
a <<.fm c;

d << a;
b <<.fm d;

a >> i;

i >> o;
)

//Changes FT when replacing c / d
b.ft = 3
a.ft = 5

//Changes FT when replacing a / b
c.ft = 3
d.ft = 4

//Changes a FT in terms of output
i.ft = 4

d.replace({\in.ar(0).range(-1.12312, 10.23)})

c.replace({\in.ar(0).range(-100, 10)})

b.replace({SinOsc.ar(1 + \fm.ar(0))})

a.replace({SinOsc.ar(220 + \fm.ar(0))})

b.replace({Saw.ar(1 + \fm.ar(0))})

s.plotTree

(
o.group.postln;
a.group.postln;
b.group.postln;
c.group.postln;
d.group.postln;
)

AlgaBlocksDict.blocksDict[1022].nodesDict
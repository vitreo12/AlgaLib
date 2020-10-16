Alga.boot({s.plotTree})

(
AlgaStartup.algaMaxIO=3;
Alga.initSynthDefs;
)

//Verbose to print out stuff from AlgaScheduler (debug)
AlgaScheduler.verbose = false;

(
Alga.boot({
	//s.plotTree;
	a = AlgaNode({SinOsc.ar(\f.kr(440)).dup * 0.2}, connectionTime:1).play;
	b = AlgaNode({DC.kr(220)});
	c = AlgaNode({DC.kr(100)});
	d = AlgaNode({DC.kr(10)});

	a <<.f b;
	a <<+.f c;
})
)

a <<.f b;

a <<+.f b;

a.replaceMix(\f, c, d);

a.from(b, \f, \out3);

a.inNodes
b.outNodes
c.outNodes

a.disconnect(\f, b)

a.interpSynths

d.replace({DC.kr(100)})

a.disconnect(\f, d);

b.outNodes
c.outNodes
d.outNodes

b.replace({LFNoise0.kr(1).range(400,1000)})

a <| \f

a.debug;

s.plotTree

a.play

(
a.ct=1;
a <<.f 323;
)

a <<+.f 10

a.debug
s.scope
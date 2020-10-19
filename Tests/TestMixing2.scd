Alga.boot({s.plotTree})

(
AlgaStartup.algaMaxIO=3;
Alga.initSynthDefs;
)


(
AlgaStartup.algaMaxIO=3;
AlgaScheduler.verbose = true;
//AlgaScheduler.verbose = false;

Alga.boot({

	//s.plotTree;
	a = AlgaNode({SinOsc.ar(\f.kr(440)).dup * 0.2}, connectionTime:1).play;
	b = AlgaNode({DC.kr(220)});
	c = AlgaNode({DC.kr(100)});
	d = AlgaNode({DC.kr(200)});

	a <<.f b;
	a <<+.f c;
	a.replaceMix(\f, c, d);

	d.replace({DC.kr(1000)});
	b.replace({DC.kr(2020)});
	a <<.f b;
})
)

s.scope

(
AlgaStartup.algaMaxIO=3;
AlgaScheduler.verbose = true;
//AlgaScheduler.verbose = false;

Alga.boot({

	//s.plotTree;
	a = AlgaNode({SinOsc.ar(\f.kr(440)).dup * 0.2}, connectionTime:1);
	b = AlgaNode({DC.kr(220)});
	a <<.f b;
})
)

b.replace({DC.kr(440)});









d.replace({DC.kr(1000)});
b.replace({DC.ar(2020)});

a <<.f b;


a <<+.f b;

a.algaScheduler.actions




a <<.f b;
a <<+.f c;
a.replaceMix(\f, c, d);

s.scope

//Run multiple times, bug: no envelope scaling!
d.replace({DC.kr(100)})


//ok this still works
a <<.f b;
b.replace({DC.ar(220)})



a <<.f b;

a <<+.f d;



a.from(b, \f, \out3);

a.inNodes
b.outNodes
c.outNodes

a.disconnect(\f, b)

a.interpSynths

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
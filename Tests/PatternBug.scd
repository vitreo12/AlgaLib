(
AlgaStartup.algaMaxIO=2;
Alga.initSynthDefs;

AlgaScheduler.verbose = true;

Alga.boot({

	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar(440)) * EnvGen.ar(Env.triangle(0.5), doneAction:2)
	}).add;

	s.sync;

	a = AlgaPattern((
		def: \test,
		dur: Pseq([0.25]),
		freq: Pwhite(100, 400)
	))

	//s.plotTree;
});
)

(
l = AlgaNode({\in.ar(0)}).play;
e = AlgaNode({\in.ar(0)}).play;
f = AlgaNode({\in.ar(0)}).play;
c = AlgaNode({\in.ar(0)}).play;
c << a;
)

s.scope

c.interpBusses[\in][\default].numChannels

l.debug
(
AlgaStartup.algaMaxIO=2;
Alga.initSynthDefs;

AlgaScheduler.verbose = true;

Alga.boot({

	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441])) * EnvGen.ar(Env.perc, doneAction:2)
	}).add;

	s.sync;

	a = AlgaPattern((
		def: \test,
		dur: Pseq([0.25]),
		freq: Pwhite(100, 400)
	));
});
)

//wtf is this click?
c = AlgaNode({\in.ar([0, 0])}).play

//goes away when a is cleared...
a.clear
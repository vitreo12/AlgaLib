(
Alga.boot({
	AlgaSynthDef(\test, {
		Mix(Array.fill(100, { | i | SinOsc.ar(\freq.kr(100) * i) })) * DC.ar(0)
	}).add;

	s.sync;

	//a = AlgaEffect({ \in.ar });

	//Note the CPU diff!
	a = AlgaNode.debug({ \in.ar }, name:\a);

	25.do({
		b = AlgaNode.debug(\test, name:\b);
		a <<+ b;
	});

	25.do({
		d = AlgaNode.debug(\test, name:\d);
		a <<+ d;
	});

	fork {
		loop {
			s.avgCPU.postln;
			1.wait;
		}
	}
},
algaServerOptions: AlgaServerOptions(blockSize: 64, supernova: true, supernovaThreads: 6)
//algaServerOptions: AlgaServerOptions(blockSize: 64)
)
)

/*
scsynth: 39

supernova: 13.6
*/
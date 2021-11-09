(
Alga.boot({
	AlgaSynthDef(\test, {
		Mix(Array.fill(100, { | i | SinOsc.ar(\freq.kr(100) * i) })) * DC.ar(0)
	}).add;

	s.sync;

	a = AN({ \in.ar });
	50.do({
		b = AlgaNode(\test);
		a << b;
	});

	c = AN({ \in.ar });
	50.do({
		d = AlgaNode(\test);
		c << d;
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
scsynth: 90

supernova: 50
*/
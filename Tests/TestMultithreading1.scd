(
Alga.boot({
	50.do({
		AlgaNode({ Mix(Array.fill(100, { | i | SinOsc.ar(\freq.kr(100) * i) })) * 0.01 });
	});

	fork {
		loop {
			s.avgCPU.postln;
			1.wait;
		}
	}
},
algaServerOptions: AlgaServerOptions(blockSize: 64, supernova: true)
//algaServerOptions: AlgaServerOptions(blockSize: 64)
)
)

/*
scsynth: 27

supernova: 7.7

gain: 3.5
*/
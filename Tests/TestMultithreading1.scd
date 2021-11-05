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
//Comment this line for scsynth
algaServerOptions: AlgaServerOptions(supernova: true)
)
)
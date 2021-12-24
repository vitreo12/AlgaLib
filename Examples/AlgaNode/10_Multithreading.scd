//Boot Alga with the supernova server
//Alga will automatically spread the load across multiple CPU cores.
(
Alga.boot({
	var reverb;

	//A bank of 50 sines
	AlgaSynthDef(\sines, {
		Mix.ar(Array.fill(50, { SinOsc.ar(Rand(200, 1000)) * 0.005 }))
	}).add;

	s.sync;

	//A reverb effect
	reverb = AlgaNode({ FreeVerb1.ar(\in.ar) }).play(chans: 2);

	//50 separated banks of 50 sine oscillators.
	//Their load will be spread across multiple CPU cores.
	50.do({
		var bank = AlgaNode(\sines);
		reverb <<+ bank;
	});

	//Print CPU usage
	fork {
		loop {
			("CPU: " ++ s.avgCPU.asStringPrec(4) ++ " %").postln;
			1.wait;
		}
	}
}, algaServerOptions: AlgaServerOptions(supernova: true, latency: 1));
)

//Boot Alga with the standard scsynth server.
//Note the higher CPU usage as the load is only on one CPU core.
(
Alga.boot({
	var reverb;

	//A bank of 50 sines
	AlgaSynthDef(\sines, {
		Mix.ar(Array.fill(50, { SinOsc.ar(Rand(200, 1000)) * 0.005 }))
	}).add;

	s.sync;

	//A reverb effect
	reverb = AlgaNode({ FreeVerb1.ar(\in.ar) }).play(chans: 2);

	//50 separated banks of 50 sine oscillators.
	//Their load will be spread across multiple threads.
	50.do({
		var bank = AlgaNode(\sines);
		reverb <<+ bank;
	});

	//Print CPU usage
	fork {
		loop {
			("CPU: " ++ s.avgCPU.asStringPrec(4) ++ " %").postln;
			1.wait;
		}
	}
}, algaServerOptions: AlgaServerOptions(latency: 1))
)
(
Alga.boot({
	//Poly sequencer featuring AlgaTemps modulating at audio rate
	~seq = AlgaSequencer((
		freq: (
			rate: \audio,
			chans: 2,
			in: Pseq([
				AlgaTemp({ SinOsc.ar([Rand(1, 100), Rand(1, 100)]) }, scale: [Pwhite(220, 440), Pwhite(440, 880)]),
				[440, 660],
				880
			], inf),
			time: Pwhite(0.01, 1)
		),
		amp: (
			rate: \audio,
			in: AlgaTemp({ SinOsc.ar(Rand(1, 1000)) }),
			time: Pwhite(0.01, 1),
		),
		dur: Prand([0.5, 1, 2], inf)
	));

	//The AlgaNode to modulate using both FM and AM
	~sine = AlgaNode({ SinOsc.ar(\freq.ar(440!2)) }, [\freq, ~seq.freq, \amp, ~seq.amp]).play;
});
)

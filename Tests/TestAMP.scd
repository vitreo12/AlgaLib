(
Alga.boot({
	a = AN({ SinOsc.ar(\freq.ar(440!2)) }).play;

	b = AMP((
		rate: \audio,
		chans: 2,
		time: Pseq([0.05, 0.1, 1],inf),
		in: Pseq([
			AlgaTemp({ SinOsc.kr([Rand(1, 100), Rand(1, 100)]) }, scale: [Pwhite(220, 440), Pwhite(440, 880)]),
			[440, 660],
			880
		], inf),
		dur: 0.5
	));

	a <<.freq b;

	s.plotTree
}, algaServerOptions: AlgaServerOptions(supernova: false))
)
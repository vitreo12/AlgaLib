(
Alga.boot({
	a = AN({ SinOsc.ar(\freq.ar(440!2)) });

	b = AMP((
		rate: \audio,
		chans: 2,
		time: Pwhite(0.01, 1),
		in: Pseq([
			AlgaTemp({ SinOsc.ar([Rand(1, 100), Rand(1, 100)]) }, scale: [Pwhite(220, 440), Pwhite(440, 880)]),
			[440, 660],
			880
		], inf),
		dur: Prand([0.125, 0.25, 0.5, 1], inf)
	), start: false);

	c = AMP((
		rate: \audio,
		time: Pwhite(0.01, 1),
		in: AlgaTemp({ SinOsc.ar(Rand(1, 1000)) }),
		dur: Prand([0.5, 1, 2], inf)
	), start: false);

	//Define connections as usual
	a <<.freq b;
	a <<.amp c;

	//Make sure everything is instantiated
	0.5.wait;

	//Play patterns and output
	b.playPattern(sched: 1);
	c.playPattern(sched: 1);
	a.play(time: 0.1, sched: 1);
})
)
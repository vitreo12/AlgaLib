//1
(
Alga.boot({
	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.kr(440))
	}, sampleAccurate: true).add;

	s.sync;

	//a = AN(\test).play;

	b = AP((
		def: \test,
		amp: AlgaTemp({
			EnvPerc.ar(\atk.kr(0.01), \rel.kr(0.25), \curve.kr(-2), 0)
		}, sampleAccurate: true),
		dur: 0.5,
	)).play(chans:2)
});
)

//2
(
Alga.boot({
	y = AP((
		def: { SinOsc.ar(\freq.kr(440)) * \amp.kr(1) },
		amp: AlgaTemp({
			EnvPerc.kr(\atk.kr(0.01), \rel.kr(0.25), \curve.kr(-2), 0)
		}),
		dur: 0.5,
	)).play(chans:2)
})
)

//3
(
Alga.boot({
	y = AP((
		def: { SinOsc.ar(\freq.kr(440)) },
		amp: Pseq([
			AlgaTemp({
				EnvPerc.ar(\atk.kr(0.01), \rel.kr(0.25), \curve.kr(-2), 0) * 0.25
			}, sampleAccurate: true),
			AlgaTemp({
				EnvPerc.ar(\atk.kr(0.001), \rel.kr(0.1), \curve.kr(-2), 0) * 0.25
			}, sampleAccurate: true),
		], inf),
		freq: Pwhite(440, 880),
		dur: Pwhite(0.01, 0.2),
	)).play(chans:2)
})
)
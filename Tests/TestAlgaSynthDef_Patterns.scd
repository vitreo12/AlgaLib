//1
(
Alga.boot({
	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.kr(440))
	}, sampleAccurate: true).add;

	s.sync;

	a = AN(\test).play;

	b = AP((
		def: \test,
		amp: AlgaTemp({
			EnvPerc.kr(\atk.kr(0.01), \rel.kr(0.25), \curve.kr(-2))
		}, sampleAccurate: true),
		dur: 1,
	));
});
)

//2
(
Alga.boot({
	y = AP((
		def: { SinOsc.ar(\freq.kr(440)) * \amp.kr(1) },
		amp: AlgaTemp({
			EnvPerc.kr(\atk.kr(0.01), \rel.kr(0.25), \curve.kr(-2))
		}, sampleAccurate: true),
		dur: 0.5,
	)).play
})
)
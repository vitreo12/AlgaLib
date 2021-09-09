(
Alga.boot({
	AlgaSynthDef(\test, { LFNoise2.ar(1000) }).add;

	b = AN({ SinOsc.ar(10) });

	a = AN({ SinOsc.ar(\freq.ar(440)) }, [\freq, AlgaTemp({ LFNoise0.ar(1) }, scale:[450, 1000])], playTime:2 ).play;

	//a.from(b, \freq, scale:[250, 1000], time:2);
})
)

//Func
a.from({ LFNoise1.ar(100) }, \freq, scale:[100, 500], time:2)

//AlgaTemp
a.from(AlgaTemp({ LFNoise0.ar(1) }, scale:[450, 1000]), \freq, time:2)

//Multiple AlgaTemps
(
a.from(
	AlgaTemp((
		def: { LFNoise1.ar(\freq.kr(10)) },
		freq: AlgaTemp({ LFNoise0.kr(10) }, scale: [250, 1000])
	),
	scale:[100, 1000]),
	\freq, time:2)
)

//Symbol
a.from(\test, \freq, scale:[100, 5000], time:2)

//Replace
a.replace({ Saw.ar(\freq.ar(440)) })

//Reset
a.resetParam(\freq, time:2)

//bye
a.clear(2)
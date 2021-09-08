(
Alga.boot({
	AlgaSynthDef(\test, { LFNoise2.ar(1000) }).add;

	//a = AN({ SinOsc.ar(\freq.ar(440)) }, [\freq, AlgaTemp({ SinOsc.ar(100) }, scale: [-100, 100])]).play
	a = AN({ SinOsc.ar(\freq.ar(440)) }).play;

	//s.plotTree
})
)

//Func
a.from({ LFNoise1.ar(100) }, \freq, scale:[50, 250], time:2)

//AlgaTemp
a.from(AlgaTemp({ LFNoise0.ar(1) }, scale:[100, 300]), \freq, time:2)

//Multiple AlgaTemps
(
a.from(
	AlgaTemp((
		def: { LFNoise1.ar(\freq.kr(10).poll(0.5)).poll(0.5) },
		freq: AlgaTemp({ LFNoise0.kr(10) }, scale: [10, 100])
	),
	scale:[50, 250]),
	\freq, time:2)
)

//Symbol
a.from(\test, \freq, scale:[-100, 100], time:2)

//Replace
a.replace({ Saw.ar(\freq.ar(440)) })

s.plotTree

SynthDef(\d, { In.ar(\bus.kr(8)).poll }).add

Synth(\d, addAction: \addToTail)
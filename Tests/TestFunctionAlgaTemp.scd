(
Alga.boot({
	AlgaSynthDef(\test, { LFNoise2.ar(1000) }).add;

	//a = AN({ SinOsc.ar(\freq.ar(440)) }, [\freq, AlgaTemp({ SinOsc.ar(100) }, scale: [-100, 100])]).play
	a = AN({ SinOsc.ar(\freq.ar(440)) }).play;
})
)

//Func
a.from({LFNoise1.ar(100)}, \freq, scale:[-100, 100], time:2)

//AlgaTemp
a.from(AlgaTemp({LFNoise1.ar(100)}, \freq, scale:[-100, 100], time:2))

//Symbol
a.from(\test, \freq, scale:[-100, 100], time:2)

//Replace
a.replace({ Saw.ar(\freq.ar(440)) })
(
Alga.boot({
	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.kr(440))
	}).add;
});
)

(
a = AlgaPattern(
	\test,
	\dur, Pseq([0.25, 0.5, 1], inf),
	\freq, Pwhite(300, 1000)
).play;
)

a <<.freq Pseq([440, 880], inf)

a <<.dur Pwhite(0.01, 1);
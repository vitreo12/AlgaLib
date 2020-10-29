(
Alga.boot({
	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.kr(440))
	}).add;

	AlgaSynthDef(\test2, {
		Saw.ar(\freq.kr(440)) * 0.5
	}).add;
});
)

(
a = AlgaPattern(
	\test,
	\dur, Pseq([0.25, 0.5, 1]),
	\freq, Pwhite(300, 1000)
)
)

a <<.freq Pseq([440, 880], inf)

a <<.dur Pwhite(0.01, 1);

a.replace(\test2);

(
a.replace(
	\test,
	\dur, Pseq([0.01, 0.2])
)
)
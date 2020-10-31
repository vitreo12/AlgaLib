(
Alga.boot({
	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.kr(440)) * EnvGen.ar(Env.perc, doneAction:2)
	}).add;

	AlgaSynthDef(\test2, {
		Saw.ar(\freq.kr(440)) * EnvGen.ar(Env.perc, doneAction:2)
	}).add;

	s.sync;

	a = AlgaPattern((
		synth: \test,
		dur: Pseq([0.25, 0.5, 1]),
		freq: Pwhite(300, 1000)
	))
});
)

(
a = AlgaPattern((
	synth: \test,
	dur: Pseq([0.25, 0.5, 1]),
	freq: Pwhite(300, 1000)
))
)

a <<.freq Pseq([440, 880])

a <<.dur Pwhite(0.01, 1);

a.replace(\test2);

(
a.replace((
	synth: \test,
	dur: Pseq([0.01, 0.2])
));
)



(
AlgaScheduler.verbose = true;

a = AlgaPattern((
	synth: \test
));

Pbind(
	\type, \algaNote,
	\algaPattern, a,
	\freq, Pwhite([200, 400], 300),
	\dur, Pseq([0.5, 1])
).play;
)
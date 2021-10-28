//Bug
(
s.waitForBoot({
	SynthDef(\test, {
		Out.ar(\out.ir(0), { SinOsc.ar(2) * EnvGen.ar(Env([1, 1], [0.2]), doneAction:2) })
	}).add;

	s.sync;

	~bus = Bus.audio(s);

	Pbind(
		\instrument, \test,
		\dur, 1,
		\out, ~bus.index,
		\addAction, \addToHead
	).play;

	{ \in.ar }.play(args:[ \in, 'a' ++ ~bus.index]);

	{ In.ar(0) }.plot(0.5);
})
)

//Bug InFeedback
(
s.waitForBoot({
	SynthDef(\test, {
		Out.ar(\out.ir(0), { SinOsc.ar(2) * EnvGen.ar(Env([1, 1], [0.2]), doneAction:2) })
	}).add;

	s.sync;

	~bus = Bus.audio(s);

	Pbind(
		\instrument, \test,
		\dur, 1,
		\out, ~bus.index,
		\addAction, \addToHead
	).play;

	{ InFeedback.ar(\in.ir) }.play(args:[ \in, ~bus.index]);

	{ In.ar(0) }.plot(0.5);
})
)


//In
(
s.waitForBoot({
	SynthDef(\test, {
		Out.ar(\out.ir(0), { SinOsc.ar(2) * EnvGen.ar(Env([1, 1], [0.2]), doneAction:2) })
	}).add;

	s.sync;

	~bus = Bus.audio(s);

	Pbind(
		\instrument, \test,
		\dur, 1,
		\out, ~bus.index,
		\addAction, \addToHead
	).play;

	{ In.ar(\in.ir) }.play(args:[ \in, ~bus.index]);

	{ In.ar(0) }.plot(0.5);
})
)

//Solution (\addToHead)
(
s.waitForBoot({
	SynthDef(\test, {
		Out.ar(\out.ir(0), { SinOsc.ar(2) * EnvGen.ar(Env([1, 1], [0.2]), doneAction:2) })
	}).add;

	s.sync;

	~bus = Bus.audio(s);

	Pbind(
		\instrument, \test,
		\dur, 1,
		\out, ~bus.index,
		\addAction, \addToHead
	).play;

	{ AlgaInFeedback.ar(\in.ir) }.play(args:[ \in, ~bus.index ]);

	{ In.ar(0) }.plot(0.5);
})
)

//Solution (\addToTail)
(
s.waitForBoot({
	SynthDef(\test, {
		Out.ar(\out.ir(0), { SinOsc.ar(2) * EnvGen.ar(Env([1, 1], [0.2]), doneAction:2) })
	}).add;

	s.sync;

	~bus = Bus.audio(s);

	Pbind(
		\instrument, \test,
		\dur, 1,
		\out, ~bus.index,
		\addAction, \addToTail
	).play;

	{ AlgaInFeedback.ar(\in.ir) }.play(args:[ \in, ~bus.index ]);

	{ In.ar(0) }.plot(0.5);
})
)

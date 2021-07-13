(
Alga.boot({

	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441])) /* * \amp.ar(1) */ * EnvGen.ar(Env.perc(0.01, 1), doneAction:2)
	}).add;

	AlgaSynthDef(\test2, {
		Saw.ar(\freq.ar([440, 441])) * EnvGen.ar(Env.perc(0.01, 1), doneAction:2) * DC.ar(0.4)
	}).add;

	s.sync;

	a = AlgaPattern((
		def: \test,
		dur: 0.5,
		freq: Pwhite([400, 200], 1000)
	)).play;

	b = AN({ SinOsc.ar([100, 200]) });

	//s.plotTree
});
)

a <<.dur 0.5

a.from(1, \dur, sched:1)

a.from(0.25, \dur, sched:1)

a.from(Pseq([1, 2, 0.1], inf), \dur, sched:1)


//sometimes with 0.25 (or faster),
//going from the synth to 440 has some "wrong ones" at the end: investigate!
a.from(0.25, \dur, sched:1)

a.from(440, \freq, time:1);

a.from(Pseq([220, 440, 880], inf), \freq, time:5)

a.from(b, \freq, time:5, scale:[330, 1000]);




//chansMapping?


//replace?
(
a.replace((
	def: \test2,
	dur: 0.5,
	freq: Pwhite([400, 200], 1000)
))
)

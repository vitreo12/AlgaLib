(
Alga.boot({

	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar(440)) * EnvGen.ar(Env.perc(0.01, 1), doneAction:2)
	}).add;

	AlgaSynthDef(\test2, {
		Saw.ar(\freq.ar([440, 441])) * EnvGen.ar(Env.perc(0.01, 1), doneAction:2) * DC.ar(0.4)
	}).add;

	s.sync;

	a = AlgaPattern((
		def: \test,
		dur: 1,
		freq: 440
	), sched:1).play;

	b = AN({ SinOsc.ar([100, 200]) });

	c = AN({ Saw.ar([100, 200]) });

	//s.plotTree
});
)

//Test mid pattern
a.from(300, \freq)

a.from(Pseq([440, 880, 1960], inf), \freq, sched:1)

a <<.dur 0.5

a.from(1, \dur, sched:1)

a.from(0.5, \dur, sched:1)

a.from(0.25, \dur, sched:1)

a.from(Pseq([1, 2, 0.1], inf), \dur, sched:1)

a.from(0.1, \dur, sched:1)

a.from(440, \freq, time:1);

a.from(Pseq([220, 660, [885, 880]], inf), \freq, time:5)

//scale
a.from(b, \freq, time:5, scale:[[330, 220], [1000, 1200]]);

//pattern support
a.from(b, \freq, time:3, scale:Pseq([[[330, 220], [1000, 1200]], [[110, 120], [500, 600]]], inf));

//chans
a.from(b, \freq, time:1, scale:[[330, 220], [1000, 1200]], chans:\out2)

//pattern support
a.from(b, \freq, time:1, scale:[[330, 220], [1000, 1200]], chans:Pseq([\out1, \out2], inf))


//AlgaArg
(
a.from(
	AlgaArg(b, scale:Pseq([[[330, 220], [1000, 1200]], [[110, 120], [500, 600]]], inf), chans:Pseq([\out1, \out2], inf)),
	\freq,
	time:2
)
)

(
a.from(
	Pseq([
		AlgaArg(b, scale:Pseq([[[330, 220], [1000, 1200]], [[110, 120], [500, 600]]], inf), chans:Pseq([\out1, \out2], inf)),
		AlgaArg(c, scale:Pseq([[[120, 223], [450, 600]], [[720, 200], [1200, 820]]], inf))
	], inf),
	\freq,
	time:2
)
)

//replace
(
a.replace((
	def: \test2,
	dur: 0.5,
	freq: Pwhite([400, 200], 1000)
))
)

//clear
a.clear

//ListPattern (check inNodes)
a.from(Pseq([b, c], inf), \freq, scale:[100, 600], time:1);
a.inNodes.postln;

//args
(
Alga.boot({
	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441])) * EnvGen.ar(Env.perc(0.01, 1), doneAction:2)
	}).add;

	s.sync;

	b = AN({ SinOsc.kr(1).range(100, 440) });

	a = AP((\def: \test, \dur: 0.5), [\freq, b], connectionTime:3).play
})
)
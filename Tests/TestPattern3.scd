(
Alga.maxIO = 2;
Alga.initSynthDefs;
Alga.boot({

	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441]).poll) * EnvGen.ar(Env.perc(0.01, 0.1), doneAction:2) * DC.ar(0.5)
	}).add;

	AlgaSynthDef(\testImpulse, {
		//FreeSelf.kr(Impulse.kr(0));
		EnvGen.kr(Env.perc(0.01, 0.5), doneAction:2);
		(Impulse.ar(0) * \amp.kr(1)).poll
	}).add;

	AlgaSynthDef(\test2, {
		Saw.ar(\freq.kr([440, 441])) * EnvGen.ar(Env.perc(0.01, 1), doneAction:2) * DC.ar(0.4)
	}).add;

	AlgaSynthDef(\testBus, {
		\in.ar(0).poll(label:"testBus") * DC.ar(0);
	}).add;

	s.sync;

	a = AlgaPattern((
		def: \test,
		dur: 1,
		freq: 440
	)).play;

	/*
	a = AlgaPattern((
		def: \testImpulse,
		dur: 1,
		amp: 1
	)).play;
	*/

	b = AN({ SinOsc.ar([100, 200]) });

	c = AN({ Saw.ar([100, 200]) });

	d = AN({ DC.ar(440) });

	//s.plotTree
});
)

s.scope

a.from(300, \freq)

a.from(Pseq([440, 880, 1760], inf), \freq, sched:1)


a <<.dur 0.5

a.from(1, \dur, sched:1)

a.from(0.5, \dur, sched:1)

a.from(0.25, \dur, sched:1)

a.from(0.125, \dur, sched:1)

a.from(0.1, \dur, sched:1)

a.from(Pwhite(0.01, 0.1), \dur, sched:1)

a.from(440, \freq, time:1)

a.from(Pseq([440, 660, [1000, 880]], inf), \freq, time:3)

s.plotTree


s.scope

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

a.inNodes

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
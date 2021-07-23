(
Alga.boot({

	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441])) * EnvGen.ar(Env.perc(0.01, 0.5), doneAction:2)
	}).add;

	AlgaSynthDef(\test2, {
		Saw.ar(\freq.kr([440, 441]) + \fm.kr(10)) * EnvGen.ar(Env.perc(0.01, 0.5), doneAction:2) * 0.5
	}).add;

	s.sync;

	a = AlgaPattern((
		def: \test,
		dur: 0.25,
		freq: 440
	), playTime:1).play;

	b = AN({ SinOsc.kr(1) });

	//b = AN({ \in.ar([0, 0]) }, [\in, a], connectionTime:1).play;

	//s.plotTree
});
)

//Replace a definition. b should take 1 sec to connect
(
a.replace((
	def:\test2,
	dur: 0.125,
	//freq: 123
), sched:1)
)

(
a.replace((
	def:\test,
	dur: 0.125,
	//freq: 440
), sched:1)
)

a.from(b, \freq, scale:[120, 440], time:1)



//CLEAR
a.clear

a.replace(\test2);

//Doesn't work?
a.clear


a.replace(\test2);

//Not working?
a.clear
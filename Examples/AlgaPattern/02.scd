(
Alga.boot({
	//Declare a simple sine AlgaSynthDef
	AlgaSynthDef(\sine, {
		SinOsc.ar(\freq.kr(440)) * EnvPerc.ar
	}).add;

	//Declare a simple saw AlgaSynthDef
	AlgaSynthDef(\saw, {
		Saw.ar(\freq.kr(440)) * EnvPerc.ar * 0.4
	}).add;

	//Wait
	s.sync;

	//Create and play the AlgaPattern
	a = AlgaPattern((
		def: \sine,
		dur: 0.5
	)).play;
});
)

//Replace the definition in the AlgaPattern
a.replace(\saw, time: 2, sched: 1);

//Note that 'def' also supports ListPatterns
a.from(Prand([\sine, \saw], inf), \def, time: 2, sched: 1);

//Declare an AlgaNode: a simple LFO
b = AN({ SinOsc.kr(1) });

//Connect b to a's \freq parameter, scaling accordingly.
//Note that all the next connections don't have scheduling, meaning they will start as soon as possible (mid-pattern).
a.from(b, \freq, scale: [120, 440], time: 3)

//Connect b to a's \freq parameter using a Pseq to control the scaling
a.from(b, \freq, scale: Pseq([[120, 440], [240, 880]], inf), time: 3)

//Declare another AlgaNode: a simple stepped noise
c = AN({ LFNoise0.kr(10) });

//Using AlgaArg to alternate between the two AlgaNodes.
//AlgaArg allows to have individual 'scale' and 'chans' control.
(
a.from(
	Pseq([
		AlgaArg(b, scale: Pseq([[120, 440], [240, 880]], inf)),
		AlgaArg(c, scale: Pseq([[330, 660], [500, 1000]], inf)),
	], inf),
	\freq,
	time: 3
)
)

//Bye bye
(
a.clear({
	b.clear;
	c.clear;
},
time: 2);
)
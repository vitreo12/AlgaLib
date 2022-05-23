(
Alga.boot({
	//Declare a simple sine AlgaSynthDef
	AlgaSynthDef(\sine, {
		SinOsc.ar(\freq.kr(440)) * EnvPerc.ar
	}, sampleAccurate: true).add;

	//Declare a simple saw AlgaSynthDef
	AlgaSynthDef(\saw, {
		Saw.ar(\freq.kr(440)) * EnvPerc.ar * 0.4
	}, sampleAccurate: true).add;

	//Wait
	s.sync;

	//Create and play the AlgaPattern
	a = AlgaPattern((
		def: \sine,
		dur: 0.5
	)).play(chans: 2, scale: 0.7);
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

//Using AlgaTemp to create temporary nodes, instead of relying on 'running' ones.
//AlgaTemp allows to have individual 'scale' and 'chans' control.
(
a.from(
	Pseq([
		AlgaTemp({ LFNoise0.kr(20) }, scale: [440, 880]),
		AlgaTemp({ SinOsc.kr(5) }, scale: [330, 660])
	], inf),
	\freq,
	time: 3
)
)

//AlgaTemps support other AlgaTemps!
(
a.from(
	AlgaTemp((
		def: { LFNoise1.kr(\freq.kr(1)) },
		freq: AlgaTemp((
			def: { LFNoise0.kr(\freq.kr(1)) },
			freq: Pseq([10, 100], inf)
		), scale: [5, 50])
	), scale: Pseq([[440, 880], [330, 660]], inf)),
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
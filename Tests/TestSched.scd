(
s.latency = 0.5;
AlgaScheduler.verbose = true;
Alga.boot({
	AlgaSynthDef(\t, { SinOsc.ar(Rand(220, 2000)) * EnvGen.ar(Env.perc) * 0.1 } ).add;
	AlgaSynthDef(\t_const, { SinOsc.ar(Rand(220, 2000)) * 0.01 } ).add;
});
)

//Note: this is bugged because of the .sync in dispatchFunction
//which screws the count!
100.do { AN({ SinOsc.ar(Rand(220, 2000)) * EnvGen.ar(Env.perc) * 0.1 }, sched:1).play };

//This in fact works as expected
100.do { AN(\t, sched:1).play };

s.plotTree

(
s.latency = 0.3;
AlgaScheduler.verbose = true;
Alga.boot({
	AlgaSynthDef(\t, { SinOsc.ar(Rand(220, 2000)) * EnvGen.ar(Env.perc) * 0.1 } ).add;
	AlgaSynthDef(\t_const, { SinOsc.ar(Rand(220, 2000)) * 0.01 } ).add;
	AlgaSynthDef(\bpass, { \in.ar } ).add;
	//s.plotTree;
});
)

(
a = AN(\bpass).play;
3.do { |i| b = AN(\t_const); a.from(b, time:1); };
)

//Causes problems
(
a = AN({ \in.ar }).play;
100.do { |i| b = AN(\t_const); a.mixFrom(b, time:0.3); };
)

//Works fine
(
a = AN(\bpass).play;
100.do { |i| b = AN(\t_const); a.mixFrom(b, time:1); };
)

100.do { b = AN(\t_const); }

//Todo:
// 1) algaInstantiated with param
// 2) better condition for .play / .stop

s.options.maxNodes
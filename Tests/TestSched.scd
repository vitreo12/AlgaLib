(
s.latency = 0.5;
AlgaScheduler.verbose = true;
Alga.boot({
	AlgaSynthDef(\t, { SinOsc.ar(Rand(220, 2000)) * EnvGen.ar(Env.perc) * 0.1 } ).add;
	AlgaSynthDef(\t_const, { SinOsc.ar(Rand(220, 2000)) * 0.01 } ).add;
	AlgaSynthDef(\bypass, { \in.ar } ).add;
});
)

//This is bugged because of the .sync in dispatchFunction, which screws ordering!
100.do { AN({ SinOsc.ar(Rand(220, 2000)) * EnvGen.ar(Env.perc) * 0.1 }, sched:1).play };

//This in fact works as expected
100.do { AN(\t, sched:1).play };

(
s.latency = 0.5;
AlgaScheduler.verbose = true;
Alga.boot({
	AlgaSynthDef(\t, { SinOsc.ar(Rand(220, 2000)) * EnvGen.ar(Env.perc) * 0.1 } ).add;
	AlgaSynthDef(\t_const, { SinOsc.ar(Rand(400, 800)) * 0.01 } ).add;
	AlgaSynthDef(\bypass, { \in.ar } ).add;
});
)

(
a = AN(\bypass).play;
5.do { |i| b = AN(\t_const); a.mixFrom(b, time:2); };
)

//This is bugged because of the .sync in dispatchFunction, which screws ordering!
(
a = AN({ \in.ar }).play;
100.do { |i| b = AN(\t_const); a.mixFrom(b, time:0.3); };
)

//This in fact works as expected
(
a = AN(\bypass).play;
100.do { |i| b = AN(\t_const); a.mixFrom(b, time:1); };
)

//Todo:
// 1) algaInstantiated with param
// 2) better condition for .play / .stop
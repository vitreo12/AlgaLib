(
s.latency = 0.5;
//AlgaScheduler.verbose = true;
Alga.boot({
	AlgaSynthDef(\t, { SinOsc.ar(Rand(220, 2000)) * EnvGen.ar(Env.perc) * 0.1 } ).add;
	AlgaSynthDef(\t_const, { SinOsc.ar(Rand(220, 2000)) * 0.1 } ).add;
});
)

//Note: this is bugged because of the .sync in dispatchFunction
//which screws the count!
100.do { AN({ SinOsc.ar(Rand(220, 2000)) * EnvGen.ar(Env.perc) * 0.1 }, sched:1).play };

//This in fact works as expected
100.do { AN(\t, sched:1).play };


(
a = AN({ \in.ar }).play;
2.do { |i| b = AN(\t_const); a.mixFrom(b); fork { loop { ("a" ++ i ++ ": " ++ a.algaInstantiated).postln; ("b" ++ i ++ ": " ++ b.algaInstantiated).postln; 0.1.wait } } };
)

//Todo:
// 1) algaInstantiated with param
// 2) better condition for .play / .stop 
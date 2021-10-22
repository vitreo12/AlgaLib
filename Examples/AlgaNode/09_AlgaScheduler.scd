(
s.latency = 0.5;
Alga.boot({
	AlgaSynthDef(\sine_const, { SinOsc.ar(Rand(220, 2000)) * 0.01 } ).add;
	AlgaSynthDef(\bypass, { \in.ar } ).add;
});
)

//All OSC messages will have same timestamp
(
a = AlgaNode(\bypass).play(chans:2);
100.do { |i| b = AlgaNode(\sine_const); a.mixFrom(b, time:1); };
)


(
Alga.boot({
	AlgaSynthDef(\sine_const, { SinOsc.ar(Rand(220, 2000)) * 0.01 } ).add;
	AlgaSynthDef(\bypass, { \in.ar } ).add;
}, algaServerOptions: AlgaServerOptions(latency: 1));
)

//All OSC messages will have same timestamp
(
a = AlgaNode(\bypass).play(chans:2);
100.do { |i|
	var sine = AlgaNode(\sine_const);
	a.mixFrom(sine, time:1);
};
)
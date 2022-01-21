//Boot Alga with the AlgaProxySpace being pushed as currentEnvironment
p = AlgaProxySpace.boot;

//A simple node
~a = { SinOsc.ar(100) };

//Use it as FM input for another node
~b.play(chans:2);
~b.interpTime = 2;
~b.playTime = 0.5;
~b = { SinOsc.ar(\freq.ar(~a).range(200, 400)) };

//Replace
~a = { SinOsc.ar(440) };

//New connection as usual
~b.from({ LFNoise1.ar(100) }, \freq, time:3)

//Reset state
p.clear(time:2);

//AlgaPattern
~c.playTime = 3;
~c.play(chans:2);
~c = (def: { SinOsc.ar * EnvPerc.ar }, dur: 0.5)

//Replace the pattern
~c = { Saw.ar * EnvPerc.ar * 0.7 }

//Clear
~c.clear(time:2);

//AlgaPattern: differential
~d.interpTime = 3
~d.playTime = 2
~d.sched = 1;
~d.play(chans:2)

//Change the 'freq:' parameter to your liking and re-evaluate.
//The AlgaProxySpace will only consider the "differential" changes to the definition
(
~d = (
	def: { SinOsc.ar(\freq.kr(440)) * EnvPerc.ar },
	dur: 0.5,
	freq: Pseq([440, 880], inf)
)
)
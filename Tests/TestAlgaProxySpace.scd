p = AlgaProxySpace.boot;

//AlgaNode
~a = { SinOsc.ar(100) }

//Test AlgaArg as default
~b.play(chans:2);
~b.interpTime = 2;
~b.playTime = 0.5
~b = { SinOsc.ar(\freq.ar(AlgaArg(~a, scale: [200, 400]))) }

//Replace
~a = { SinOsc.ar(440) }

//New connections as usual
~b.from({ LFNoise1.ar(100) }, \freq, time:3, scale: [100, 600])


//AlgaPattern: replaces
~c.playTime = 3;
~c.play(chans:2);
~c = (def: {SinOsc.ar * EnvPerc.ar(release: 3)}, dur:1)
~c = (def: {Saw.ar * EnvPerc.ar(release: 3) * 0.7}, dur:1)

//Test difference!
~c.stopPatternBeforeReplace = false
~c = (def: {SinOsc.ar * EnvPerc.ar(release: 3)}, dur:1)
~c = (def: {Saw.ar * EnvPerc.ar(release: 3) * 0.7}, dur:1)


//AlgaPattern: differential
~d.interpTime = 4
~d.playTime = 2
~d.play
(
~d = (
	def: { SinOsc.ar(\freq.kr(440)) * EnvPerc.ar * 0.1 },
	dur: 0.1,
	freq: Pseq([440, 880], inf)
)
)
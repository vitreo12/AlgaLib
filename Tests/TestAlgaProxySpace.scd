p = AlgaProxySpace.boot;

~a = { SinOsc.ar(100) }

~b.play;
~b.interpTime = 2;
~b.playTime = 0.5
~b = { SinOsc.ar(\freq.ar(~a).range(100, 400)) }

~a = { SinOsc.ar(440) }

~b.from({ LFNoise1.ar(100) }, \freq, time:3)


~c.playTime = 1;
~c.play
~c = (def: {SinOsc.ar * EnvPerc.ar(release: 3)}, dur:1)
~c = (def: {Saw.ar * EnvPerc.ar(release: 3) * 0.7}, dur:1)

~c.stopPattern
~c.resumePattern

s.scope

/*
AP:

1) copy all relevant stuff (playTime, interpTime, interpShape, etc...)
to the new AP and return that

2) perform differential checks

*/

//Replacing while playing the tail
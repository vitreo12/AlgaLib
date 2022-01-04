p = AlgaProxySpace.boot;

~a = { SinOsc.ar(100) }

~b.play;
~b.interpTime = 2;
~b.playTime = 0.5
~b = { SinOsc.ar(\freq.ar(~a).range(100, 400)) }

~a = { SinOsc.ar(440) }

~b.from({ LFNoise1.ar(100) }, \freq, time:3)


~c.playTime = 3;
~c.play
~c = (def: {SinOsc.ar * EnvPerc.ar(release: 3)}, dur:1)
~c = (def: {Saw.ar * EnvPerc.ar(release: 3) * 0.7}, dur:1)

//Test difference!
~c.stopPatternBeforeReplace = false

~c = (def: {SinOsc.ar * EnvPerc.ar(release: 3)}, dur:1)
~c = (def: {Saw.ar * EnvPerc.ar(release: 3) * 0.7}, dur:1)


~d.interpTime = 4
~d.playTime = 2
~d.play
(
~d = (
	def: { SinOsc.ar(\freq.kr(440)) * EnvPerc.ar * 2 },
	dur: 0.1,
	freq: Pseq([440, 880], inf)
)
)



( hello: Pwhite() ).asString == ( hello: Pwhite(3) ).code

{ Pwhite(3) }.def.code == { Pwhite(3) }.def.code

{ Pwhite(3) }.def.code == { 2 }.def.code


Pwhite(3, 1).asCompileString
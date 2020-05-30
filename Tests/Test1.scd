(
s.waitForBoot({
	SynthDef(\sine, {Out.ar(0, {AlgaEnvGate.ar(0, \gate.kr(1), \fadeTime.kr(0)) * SinOsc.ar(\freq.kr(440))})}).add;
	SynthDef(\noise, {Out.ar(0, {AlgaEnvGate.ar(0, \gate.kr(1), \fadeTime.kr(0)) * WhiteNoise.ar.dup * 0.2})}).add;
});
)

s.plotTree;

a = AlgaNode(\sine, fadeTime:1)

a.replace(\noise)
a.replace(\sine)

a.fadeTime = 5

a.clear
(
s.waitForBoot({
	AlgaSynthDef(\sine, {SinOsc.ar(\freq.kr(440))}).add;
	AlgaSynthDef(\noise, {WhiteNoise.ar.dup * 0.2}).add;
});
)

s.plotTree;

a = AlgaNode(\sine, fadeTime:1)

a.replace(\noise)
a.replace(\sine)

a.fadeTime = 5

a.clear

(
Alga.boot({
	AlgaSynthDef(\sine, {SinOsc.ar(\freq.kr(440))}).add;
	AlgaSynthDef(\noise, {WhiteNoise.ar.dup * 0.2}).add;
});
)

s.plotTree
a = AlgaNode(\sine).play;
b = AlgaNode({DC.kr(234)});

a.ft = 3

b >>.freq a

b.replace({DC.kr(330)});
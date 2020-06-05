(
Alga.boot({
	AlgaSynthDef(\sine, {SinOsc.ar(\freq.kr(440))}).add;
	AlgaSynthDef(\noise, {WhiteNoise.ar.dup * 0.2}).add;
	s.plotTree;
});
)

a = AlgaNode(\sine).play
b = AlgaNode({DC.kr(234)});
c = AlgaNode({DC.kr(450)});

a.ft = 1

a <<.freq b
c >>.freq b
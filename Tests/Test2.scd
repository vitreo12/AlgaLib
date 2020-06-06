(
Alga.boot({
	AlgaSynthDef(\sine, {SinOsc.ar(\freq.kr(440))}).add;
	AlgaSynthDef(\noise, {WhiteNoise.ar.dup * 0.2}).add;
	0.3.wait;
	s.plotTree;
});
)

a = AlgaNode(\sine, fadeTime:2).play
a = AlgaNode(\sine, fadeTime:2)
b = AlgaNode({DC.kr(290)});
c = AlgaNode({DC.kr(450)});

a <<.freq b
c >>.freq a


(
Alga.boot({
	AlgaSynthDef(\sine, {SinOsc.ar(\freq.kr(440))}).add;
	AlgaSynthDef(\dc200, {DC.kr(200)}).add;
	AlgaSynthDef(\dc400, {DC.kr(440)}).add;

	0.1.wait;
	//s.plotTree;
});
)

a = AlgaNode(\sine, fadeTime:1).play
b = AlgaNode({DC.kr(290)});
a <<.freq b
b.replace({DC.ar(440)});


a.replace({Saw.ar(\freq.kr(440))});





a = AlgaNode(\sine, fadeTime:1).play
b = AlgaNode(\dc200);
a <<.freq b
b.replace(\dc400);
b.replace(\dc200);

(
AlgaLib.boot({
	AlgaSynthDef(\sine, {SinOsc.ar(\freq.kr(440))}).add;
	AlgaSynthDef(\noise, {WhiteNoise.ar.dup * 0.2}).add;
});
)


a = AlgaNode(\sine);
b = AlgaNode({DC.kr(220)});

b >>.freq a
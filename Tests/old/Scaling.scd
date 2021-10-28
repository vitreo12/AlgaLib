(
Alga.boot({
	a = AlgaNode({SinOsc.ar(\f.kr([440, 441]))}, connectionTime:2, playTime:1).play;
	b = AlgaNode({SinOsc.ar(1)});
	c = AlgaNode({LFNoise1.ar([100, 1000])});

	//scale: per channel scaling
	a.from(b, \f, scale:[[300, 10],  500]);
});
)

a.mixFrom(b, \f, scale:[-1, 1, 50, 1000]);

a.mixFrom(c, \f, scale:[-1, 1, 50, 1000]);

a.removeMix(\f, b)

a.removeMix(\f, c)

a.replace({SinOsc.ar(\f.ar([440, 441]))})

a.from(b, \f, scale:[[20, 201], [201, 32]])

b.replace({SinOsc.ar(\f.kr(100))})

a.inNodes

a.clear

a.interpBusses[\f][c]

s.scope

a.interpSynths
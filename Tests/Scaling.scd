(
AlgaStartup.algaMaxIO = 4;
Alga.initSynthDefs;
)

(
AlgaStartup.algaMaxIO = 2;
Alga.boot({
	a = AlgaNode({SinOsc.ar(\f.kr([440, 441]))}, connectionTime:2, playTime:1).play;
	b = AlgaNode({SinOsc.ar(1)});
	c = AlgaNode({DC.kr(0)});

	//scale: per channel scaling
	a.from(b, \f, scale:[[300, 10],  500]);
});
)

a.mixFrom(c, \f, scale:[0, 1, 100, 500]);

a.replaceMix(\f, c, c, scale:[0, 1, 200, 1000]);

b.replace({SinOsc.ar(\f.kr(100))})

a.from(b, \f, scale:[[200, 201], [201, 202]])

a.replace({SinOsc.ar(\f.ar([440, 441]))})

a.pt = 1

a.inNodes

a.clear
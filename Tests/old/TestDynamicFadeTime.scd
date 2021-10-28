(
AlgaStartup.algaMaxIO = 2;
Alga.initSynthDefs;
Alga.boot({s.plotTree});
)

(
AlgaStartup.algaMaxIO = 2;
Alga.initSynthDefs;
Alga.boot;
)

a = AlgaNode({SinOsc.ar(\f.kr(440))}).play

a.from(1000, \f).ct_(10)
a.from(500, \f).ct_(0.01);

a.activeInterpSynths

//Now you can do this

a.from(1000, \f, time:5);
a.from(100, \f, time:0.1);

s.scope
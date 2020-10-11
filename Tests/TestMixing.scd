Alga.boot;

a = AlgaNode({SinOsc.ar(\f.kr(440))});
b = AlgaNode({DC.kr(220)});

a <<+.f b

b.replace({DC.kr(441)})

a.replace({Saw.ar(\f.kr(440)) * 0.3})

c = AlgaNode({LFNoise1.kr(1).range(200, 1000)});

a <<+.f c

a.disconnect(\f, c);
a.disconnect(\f, b);
Alga.boot;

(
s.plotTree;
a = AlgaNode({SinOsc.ar(\f.kr(440)) * 0.2}).play;
b = AlgaNode({DC.kr(220)});
c = AlgaNode({LFNoise1.kr(1).range(200, 1000)});
)

a <<.f b //a <<+.f b would be the same: i don't wanna keep the default when stacking!

a <<+.f c

a.debug;

b.replace({DC.kr(441)})

a.replace({Saw.ar(\f.kr(440)) * 0.3})

c.replace({LFNoise0.kr(1).range(300, 1000)});

//Disconnect individual parameters
a.disconnect(\f, c);
a.disconnect(\f, b);

a.debug;

(
//Reconnect
a <<+.f b;
a <<+.f c;
)

a.debug;

//Remove all and go back to default
a <| \f;

a.debug;
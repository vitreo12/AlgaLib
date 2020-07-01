Alga.boot({ s.plotTree }, s, AlgaServerOptions(numOutputBusChannels:3));
Alga.boot;

(
b = AlgaNode({SinOsc.ar(134 + \fm.ar(0))}, connectionTime:2);
a = AlgaNode({SinOsc.ar(220 + \fm.ar(0))}, connectionTime:3);
c = AlgaNode({\in.ar(0).range(-100, 100)}, connectionTime:2);
d = AlgaNode({\in.ar(0).range(-50, 50)}, connectionTime:4);

o = AlgaNode({\in.ar(0).tanh.dup}, connectionTime:3, playTime:3).play;

c << b;
a <<.fm c;

d << a;
b <<.fm d;

a >> o;
)

(
//Changes FT when replacing c / d
b.ct = 3;
a.ct = 5;

//Changes FT when replacing a / b
c.ct = 3;
d.ct = 4;
)

d.replace({\in.ar(0).range(-100.12312, 120.23)})

c.ct = 3;

c.connectionTimeOutNodes

a.ct = 5

c.longestWaitTime
a.longestWaitTime
d.longestWaitTime
b.longestWaitTime

c.replace({\in.ar(0).range(-30, rrand(10, 100))});

(
fork {
	loop {
		c.replace({\in.ar(0).range(-30, rrand(10, 100))});
		3.wait;
	}
}
)

b.ct_(1).replace({SinOsc.ar(1 + \fm.ar(0))})

a.replace({SinOsc.ar(1 + \fm.ar(0))})

b.replace({Saw.ar(1 + \fm.ar(0))})

b.replace({Gendy1.ar(minfreq: 100 - \fm.ar(0), maxfreq: 100)})
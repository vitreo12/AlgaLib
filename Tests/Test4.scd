Alga.boot;
Alga.boot(algaServerOptions: AlgaServerOptions(blockSize:512, supernova:true));

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

d.replace({\in.ar(0).range(-1.12312, 10000.23)})

c.ct = 3;

c.connectionTimeOutNodes

a.ct = 5

c.replace({\in.ar(0).range(-30, rrand(10, 10))});

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

b.replace({Pulse.ar(1 + \fm.ar(0))})

~delay = AlgaNode({OmniDelay2.ar(\in.ar, \dt.ar(0.3), \fb.kr(0.9), \dp.kr(0.8), 1).dup * 0.3})

~delay.play

a >> ~delay

~delay.ct = 4

~delay <<.dt 0.1

~delay <<.dp 0.9

~delay <<.fb 0.4

~dt = AlgaNode({DC.ar(0.1)})

~dt.replace({LFNoise0.ar(SinOsc.kr(LFNoise1.kr(1).range(0.1, 10)).range(0.1, 100)).range(0.001, 1).lag(0.1)})

~delay <<.dt ~dt

c << b

~delay.stop
o.stop
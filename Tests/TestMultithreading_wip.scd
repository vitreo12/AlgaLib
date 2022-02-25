(
Alga.boot({
	AlgaSynthDef(\test, {
		Mix(Array.fill(1000, { | i | SinOsc.ar(\freq.kr(100) * i) })) * DC.ar(0)
	}).add;

	4.do { AlgaNode(\test) };

	fork {
		loop {
			s.avgCPU.postln;
			1.wait;
		}
	}
},
algaServerOptions: AlgaServerOptions(blockSize: 64, supernova: true, supernovaThreads: 6)
)
)

15

(
Server.supernova;
s.options.blockSize = 64;
s.waitForBoot({
	AlgaSynthDef(\test, {
		Mix(Array.fill(1000, { | i | SinOsc.ar(\freq.kr(100) * i) })) * DC.ar(0)
	}).add;
})
)

(
a = Group(s.defaultGroup);
b = Group(a);
c = Group(b);
d = Group(b);
e = Group(b);
Synth(\test, target:b);
Synth(\test, target:c);
Synth(\test, target:d);
Synth(\test, target:e);
)

28

(
a = ParGroup(s.defaultGroup);
b = Group(a);
c = Group(b);
d = Group(b);
e = Group(b);
Synth(\test, target:b);
Synth(\test, target:c);
Synth(\test, target:d);
Synth(\test, target:e);
)

28

(
a = ParGroup(s.defaultGroup);
b = Group(a);
c = Group(a);
d = Group(a);
e = Group(a);
Synth(\test, target:b);
Synth(\test, target:c);
Synth(\test, target:d);
Synth(\test, target:e);
)

12

(
a = ParGroup(s.defaultGroup);
b = Group(a);
c = ParGroup(b);
d = ParGroup(b);
e = ParGroup(b);
Synth(\test, target:b);
Synth(\test, target:c);
Synth(\test, target:d);
Synth(\test, target:e);
)

28

(
a = ParGroup(s.defaultGroup);
b = Group(a);
c = Group(a);
d = Group(b);
e = Group(b);
Synth(\test, target:b);
Synth(\test, target:c);
Synth(\test, target:d);
Synth(\test, target:e);
)

22

(
a = Group(s.defaultGroup);
b = ParGroup(a);
c = Group(b);
d = Group(b);
e = Group(b);
Synth(\test, target:b);
Synth(\test, target:c);
Synth(\test, target:d);
Synth(\test, target:e);
)

12
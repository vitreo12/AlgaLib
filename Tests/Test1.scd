(
s.waitForBoot({
	AlgaSynthDef(\sine, {SinOsc.ar(\freq.kr(440))}).add;
	AlgaSynthDef(\noise, {WhiteNoise.ar.dup * 0.2}).add;
});
)

AlgaLib.boot

s.plotTree

a = AlgaNode(\sine, fadeTime:1)

b = AlgaNode({SinOsc.ar(\f.ar(330))})

b.normBusses

a.replace(\noise)
a.replace(\sine)

a.replace({SinOsc.ar(\f.ar(330))})

a.fadeTime = 5
a.clear


//Audio/Control rate mapping!
(
fork {
	a = AlgaBus(s);

	AlgaSynthDef(\sine, { SinOsc.ar(\freq.ar(440)) }).add;

	s.sync;

	AlgaSynthDef(\setbus, { Out.ar(a, SinOsc.ar.range(1, 100) * MouseX.kr(1, 100)) }).add;

	s.sync;

	Synth(\setbus);
	Synth(\sine, [\freq, a.bus.asMap]);
}
)


AlgaSynthDef(\sine, {SinOsc.ar(\freq.kr(440))}).add

a = SynthDescLib.global.at(\sine).controls

(
t = 0;
a.do({ | controlName, index |
	var argName = controlName.name;
	if((argName != \out).and(argName != \fadeTime).and(argName != \gate), {
		argName.postln;
	}, {
		a.removeAt(index - t);
		t = t + 1;
	});
});
)

a
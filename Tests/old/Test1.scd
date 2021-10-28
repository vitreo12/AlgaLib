(
Alga.boot({
	AlgaSynthDef(\sine, {SinOsc.ar(\freq.kr(440))}).add;
	AlgaSynthDef(\noise, {WhiteNoise.ar.dup * 0.2}).add;
});
)

s.plotTree

a = AlgaNode(\sine, fadeTime:1)
a.fadeTime = 3
a.play
a.clear

a.interpSynths[\freq].set(\in, 500)

a.synthBus.free

Alga.boot;
b = AlgaNode({SinOsc.ar(\f.ar(440)) * \a.kr(0.7)}).play

b.interpSynths[\a].set(\in, 0.5)
b.interpSynths[\f].set(\in, 440)

b.fadeTime = 4
b.clear


a << b


b.normBusses
b.normSynths
b.interpSynths

a.replace(\noise)
a.replace(\sine)

a.replace({SinOsc.ar(\f.ar(330))})

a.fadeTime = 5
a.clear

//Audio/Control rate mapping!
(
fork {
	a = AlgaBus(s, 2);

	AlgaSynthDef(\sine, { SinOsc.ar(\freq.ar([440, 441])) }).add;

	s.sync;

	AlgaSynthDef(\setbus, { Out.ar(\out.ar(0), SinOsc.ar([440, 441]).range(1, 100) * MouseX.kr(1, 100)) }).add;

	s.sync;

	Synth(\setbus, [\out, a.index]);
	Synth(\sine, [\freq, a.busArg]);
}
)

NamedControl


(
a = AlgaBus(s, 2, \audio);
AlgaSynth(\algaInterp_audio1_audio1,
	[\in, 0.5, \fadeTime, 0.5, \out, a.index];
);
{Out.ar(0, InBus.ar(a.bus, 2))}.play;
a.bus.plot(1);
)

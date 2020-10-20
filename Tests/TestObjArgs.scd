(
AlgaScheduler.verbose=true;
Alga.boot({
	AlgaSynthDef(\something, {
		SinOsc.ar(\freq.kr(440))
	}).add;

	AlgaSynthDef(\somethingElse, {
		Saw.ar(\freq.kr(440))
	}).add;

	s.sync;

	b = AlgaNode({SinOsc.kr(1).range(100, 2000)});

	a = AlgaNode(\something, [\freq, b], 1, 1).play;
});
)

a.replace(\somethingElse);

a <<.freq 1

a <<.freq b

a <| \freq;

a.inNodes

b.clear
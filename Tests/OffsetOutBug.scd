(
s.waitForBoot({
	SynthDef(\1, {
		Out.ar(\out.ir(0), DC.ar([1, 1]))
	}).add;

	SynthDef(\2, {
		\in.ar([0, 0]).poll
	}).add;

	s.sync;

	s.bind {
		var bus, s1, s2;
		bus = Bus.audio(s, 2);
		s1 = Synth(\1, [\out, bus.index]);
		s2 = Synth(\2, [\in, bus.busArg], addAction:\addToTail);
	}
});
)

(
s.waitForBoot({
	SynthDef(\1, {
		OffsetOut.ar(\out.ir(0), DC.ar([1, 1]))
	}).add;

	SynthDef(\2, {
		\in.ar([0, 0]).poll
	}).add;

	s.sync;

	s.bind {
		var bus, s1, s2;
		bus = Bus.audio(s, 2);
		s1 = Synth(\1, [\out, bus.index]);
		s2 = Synth(\2, [\in, bus.busArg], addAction:\addToTail);
	}
});
)
(
~dur = 1;
~stream = Pbind(
	\dur, Pfuncn( { ~dur.next }, inf),
	\degree, Pseries(0, 1) % 20
).asStream;

~condition = Condition(false);

~clock = TempoClock.default;

~routine = Routine({
	loop {
		~player !? _.stop(); // stop the old one, if there is an old one
		~player = EventStreamPlayer(~stream, Event.parentEvents.default);
		~player.play(~clock, quant:1);
		~condition.hang;
	}
}).play(~clock);
)

(
~dur = 2;
~condition.unhang;
)

(
~ft = 1;
~blend = Pseg([0, 1, 1], [~ft, inf], \lin);
~what = 1;
~dur = (~dur.blend(~what, ~blend)).asStream;
//~condition.unhang;
)

(
~player = EventStreamPlayer(~stream, Event.parentEvents.default);
~player.play(~clock, quant:1);
)

(
~ft = 3;
~blend = Pseg([0, 1, 1], [~ft, inf], \lin);
~what = 2;
~dur = (~dur.blend(~what, ~blend)).asStream;
)

(
Pbind(\dur, 1, \amp, 0.1, \freq, 5000).play(~clock, quant:1)
)
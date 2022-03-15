(
Alga.boot({
	~dur = 0.5;
	t = TempoClock.default;

	//Pattern
	a = AP((
		def: { SinOsc.ar * EnvPerc.ar(release: 0.1) * 0.5 },
		dur: Pfunc { var val = ~dur.next; val.postln; val }
	)).play(chans:2);

	//Click
	b = AP((
		def: { EnvPerc.ar(release: SampleDur.ir); Impulse.ar(0) },
		dur: 1
	)).play(chans:2);
})
)

//Use AlgaPseg's onDone to resync the pattern
(
~ft = 5 * t.tempo;
if(~pseg != nil, { ~pseg.stop });
~what = 0.1;
~pseg = AlgaPseg([0, 1, 0.5, 1, 1], [2, 3, 4, inf], \lin, onDone: {
	a.from(Pfunc { var val = ~dur.next; val.postln; val }, \dur, sched: 1);
});
~dur = (~dur.blend(~what, ~pseg)).asStream;
)

(
~ft = 5 * t.tempo;
if(~pseg != nil, { ~pseg.stop });
~what = 0.5;
~pseg = AlgaPseg([0, 1, 1], [~ft, inf], \lin, onDone: {
	a.from(Pfunc { var val = ~dur.next; val.postln; val }, \dur, sched: 1)
});
~dur = (~dur.blend(~what, ~pseg)).asStream;
)

(
~ft = 5 * t.tempo;
if(~pseg != nil, { ~pseg.stop });
~what = Pseq([0.25, 0.25, 0.125], inf);
~pseg = AlgaPseg([0, 1, 1], [~ft, inf], \lin, onDone: {
	a.from(Pfunc { var val = ~dur.next; val.postln; val }, \dur, sched: 1);

	//This will effectively reset the sequence. It is to be executed as same sched as
	//the re-syncing, but at topPriority in order to be executed beforehand
	a.addAction(func: { ~dur = ~what.asStream }, sched: 1, topPriority: true);
});
~dur = (~dur.blend(~what, ~pseg)).asStream;
)

//If changing this, it takes the same amount of time once triggered
TempoClock.default.tempo = 10
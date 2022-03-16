(
Alga.boot({
	~dur = 0.5;

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

	~resync = { | sched = 1, reset = false |
		//Resched execution
		a.from(Pfunc { var val = ~dur.next; val.postln; val }, \dur, sched: sched);

		//This will effectively reset the sequence. It is to be executed as same sched as
		//the re-syncing, but at topPriority in order to be executed beforehand
		if(reset, {
			a.addAction(func: { ~dur = ~what.asStream }, sched: 1, topPriority: true);
		})
	}
})
)

//Use AlgaPseg's onDone to resync the pattern
(
a.addAction(func: {
	~ft = 5 * a.clock.tempo;
	if(~pseg != nil, { ~pseg.stop });
	~what = 0.1;
	~pseg = AlgaPseg([0, 1, 0.5, 1, 1], [2, 3, 4, inf], \lin, clock: a.clock, onDone: {
		("Drift: " ++ ~pseg.drift).warn;
		~resync.();
	});
	~pseg.start;
	~dur = (~dur.blend(~what, ~pseg)).asStream;
}, sched: 1, topPriority: true) //sched at same time!
)

(
~ft = 3 * a.clock.tempo;
if(~pseg != nil, { ~pseg.stop });
~what = 0.5;
~pseg = AlgaPseg([0, 1, 1], [~ft, inf], \lin, clock: a.clock, onDone: {
	("Drift: " ++ ~pseg.drift).warn;
	~resync.();
});
~pseg.start;
~dur = (~dur.blend(~what, ~pseg)).asStream;
)

(
~ft = 3 * a.clock.tempo;
if(~pseg != nil, { ~pseg.stop });
~what = Pseq([0.25, 0.25, 0.125], inf);
~pseg = AlgaPseg([0, 1, 1], [~ft, inf], \lin, clock: a.clock, onDone: {
	("Drift: " ++ ~pseg.drift).warn;
	~resync.(reset: true);
});
~pseg.start;
~dur = (~dur.blend(~what, ~pseg)).asStream;
)

//If changing this, it takes the same amount of time once triggered
a.clock.tempo = 1
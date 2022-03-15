(
Alga.boot({
	~dur = 1;
	t = TempoClock.default;

	//Pattern
	a = AP((
		def: { SinOsc.ar * EnvPerc.ar(release: 0.1) * 0.5 },
		dur: Pfunc { ~dur.next }
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
if(~blend != nil, { ~blend.stop });
~blend = AlgaPseg([0, 1, 1], [~ft, inf], \lin, onDone: { a.from(Pfunc { ~dur.next }, \dur, sched: 1) });
~what = 0.1;
~dur = (~dur.blend(~what, ~blend)).asStream;
)

(
~ft = 5 * t.tempo;
if(~blend != nil, { ~blend.stop });
~blend = AlgaPseg([0, 1, 1], [~ft, inf], \lin, onDone: { a.from(Pfunc { ~dur.next }, \dur, sched: 1) });
~what = 0.5;
~dur = (~dur.blend(~what, ~blend)).asStream;
)

(
~ft = 5 * t.tempo;
if(~blend != nil, { ~blend.stop });
~blend = AlgaPseg([0, 1, 1], [~ft, inf], \lin, onDone: { a.from(Pfunc { ~dur.next }, \dur, sched: 1) });
~what = Pseq([0.25, 0.25, 0.125], inf);
~dur = (~dur.blend(~what, ~blend)).asStream;
)

//If changing this, it takes the same amount of time once triggered
TempoClock.default.tempo = 10
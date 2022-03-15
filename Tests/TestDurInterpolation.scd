(
s.waitForBoot({
	SynthDef(\imp, {
		Out.ar(0, Impulse.ar(0).dup * EnvPerc.ar(release: 0.01));
	}).add;

	s.sync;

	~dur = 1;
	t = TempoClock.default;

	d = Pbind(
		\type, \note,
		\instrument, \imp,
		\dur, Pfunc { var val = ~dur.next; val.postln; val }
	).play(clock: t)
})
)

//The .stop for AlgaPseg allows to "stop" timing of the previous blend, freezing it!
(
~ft = 5 * t.tempo; //tempo agnostic, as Pseg only works with beats
if(~blend != nil, { ~blend.stop });
~blend = AlgaPseg([0, 1, 1], [~ft, inf], \lin, onDone: { "0.1 DONE".error });
~what = 0.1;
~dur = (~dur.blend(~what, ~blend)).asStream;
)

(
~ft = 5 * t.tempo; //tempo agnostic, as Pseg only works with beats
if(~blend != nil, { ~blend.stop });
~blend = AlgaPseg([0, 1, 1], [~ft, inf], \lin, onDone: { "0.5 DONE".error });
~what = 0.5;
~dur = (~dur.blend(~what, ~blend)).asStream;
)

(
~ft = 5 * t.tempo; //tempo agnostic, as Pseg only works with beats
if(~blend != nil, { ~blend.stop });
~blend = AlgaPseg([0, 1, 1], [~ft, inf], \lin, onDone: { "Pseq DONE".error });
~what = Pseq([0.1, 0.5, 0.33], inf);
~dur = (~dur.blend(~what, ~blend)).asStream;
)

//If changing this, it takes the same amount of time once triggered
TempoClock.default.tempo = 10
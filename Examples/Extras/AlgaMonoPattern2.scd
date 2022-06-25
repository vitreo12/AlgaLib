/*
Dev info:

In the future, this should be abstracted in one class: AlgaMonoPattern.
This should also handle PROPER interpolation / smoothing across retriggers.
The smoothing value must always be less than dur, so only the previous Synth
needs to be taken care of. The previous Synth should be freed with its time
being the new smooth value, and a descending interpolation. The new Synth
would perform the ascending one.

For this new behaviour, an additional Bus per Synth is needed to retrieve
these values.
*/

(
p = APS.boot({
	//ReplaceOut is needed in order to always overwrite on new trigger
	AlgaSynthDef(\seq1_ar, {
		var startVal = Latch.kr(InFeedback.ar(\out.ir), 1);
		FreeSelf.kr(1 - \gate.kr(1));
		AlgaSmoother.ar(startVal, \val.ar, \smooth.ir(0));
	},
	replaceOut: true).add
}, algaServerOptions: AlgaServerOptions(supernova: true));
p.sched = 0;
)

//Node to play
(
~a = AN({ SinOsc.ar(\freq.ar(440)) });
~a.play(2);
)

//Sequencer
(
~b = (
	def: \seq1_ar,
	smooth: Pseq([0.05, 0.1, 0.2], inf),
	val: Pseq([
		220,
		AlgaTemp({ SinOsc.ar(Rand(5, 150)) }, scale: [220, 1000]),
		880
	], inf),
	dur: 0.25,
	sustain: Pkey(\dur)
);

//Connect sequencer
~a <<.freq ~b;
)

//Interpolation still works
(
~b.it = 5;
~b <<.val Pwhite(220, 880);
)

//Latest values are not preserved with the audio rate Bus
~b.stopPattern;
~b.resumePattern;
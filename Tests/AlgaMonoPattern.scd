(
p = APS.boot({
	//ReplaceOut is needed in order to always overwrite on new trigger
	AlgaSynthDef(\seq1, {
		FreeSelf.kr(1 - \gate.kr(1));
		\val.kr
	},
	replaceOut: true).add
});
p.sched = 0;
)

//Node to play
(
~a = AN({ SinOsc.ar(\freq.kr(440)) });
~a.play(2);
)

//Sequencer
(
~b = (
	def: \seq1,
	val: Pseq([220, 440, 880], inf),
	dur: 0.25,
	sustain: Pkey(\dur)
);

//Connect sequencer
~a <<.freq ~b;
)

//Interpolation still works
(
~b.it = 5;
~b <<.val Pseq([220, 440, 880], inf);
)

//Latest values are preserved
~b.stopPattern;
~b.resumePattern;

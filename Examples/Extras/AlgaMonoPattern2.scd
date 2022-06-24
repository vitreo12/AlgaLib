(
p = APS.boot({
	//ReplaceOut is needed in order to always overwrite on new trigger
	AlgaSynthDef(\seq1_ar, {
		FreeSelf.kr(1 - \gate.kr(1));
		\val.ar
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
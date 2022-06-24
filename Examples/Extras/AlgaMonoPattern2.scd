(
p = APS.boot({
	//ReplaceOut is needed in order to always overwrite on new trigger
	AlgaSynthDef(\seq1_ar, {
		var startVal = Latch.kr(InFeedback.ar(\b.ir), 1);
		var val = AlgaSmoother.ar(1, startVal, \val.ar, \smooth.ir(0));
		ReplaceOut.ar(\b.ir, val);
		FreeSelf.kr(1 - \gate.kr(1));
		Silent.ar;
	},
	allowOut: true).add
}, algaServerOptions: AlgaServerOptions(supernova: true));
p.sched = 0;
)

//Node to play
(
b = Bus.audio(s);
~a = AN({ \seq.ar; SinOsc.ar(\freq.ar(440)) }, [\freq, AlgaTemp({ In.ar(b.index) })]);
~a.play(2);
)

//Sequencer
(
~b = (
	def: \seq1_ar,
	b: b.index,
	smooth: Pseq([0, 0.1, 0.2], inf),
	val: Pseq([
		220,
		AlgaTemp({ SinOsc.ar(Rand(5, 150)) }, scale: [220, 1000]),
		880
	], inf),
	dur: 0.25,
	sustain: Pkey(\dur),
	out: AlgaOut(~a, \seq) //Trick to maintain node ordering. This is crucial for supernova
);
)

//Interpolation still works
(
~b.it = 5;
~b <<.val Pwhite(220, 880);
)

//Latest values are not preserved with the audio rate Bus
~b.stopPattern;
~b.resumePattern;
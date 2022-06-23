(
p = APS.boot({
	//ReplaceOut is needed in order to always overwrite on new trigger
	AlgaSynthDef(\seq1, {
		ReplaceOut.kr(\b.ir, \val.kr);
		FreeSelf.kr(Impulse.kr(0))
	},
	allowOut: true).add
});
p.sched = 0;
)

//Node to play
(
b = Bus.control(s);
~a = AN({ \seq.kr; SinOsc.ar(\freq.kr(440)) }, [\freq, AlgaTemp({ In.kr(b.index) })]);
~a.play;
)

//Sequencer
(
~b = (
	def: \seq1,
	b: b.index,
	val: Pseq([220, 440, 880], inf),
	dur: 0.25,
	out: AlgaOut(~a, \seq) //Trick to maintain node ordering. This is crucial for supernova
);
)

//Interpolation still works
(
~b.it = 5;
~b <<.val Pwhite(220, 880);
)

//Latest values are preserved
~b.stopPattern;
~b.resumePattern;

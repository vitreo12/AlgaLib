(
p = APS.boot({
	//ReplaceOut is needed in order to always overwrite on new trigger
	AlgaSynthDef(\seq1, {
		ReplaceOut.kr(\b.ir(-1), \val.kr(440)); FreeSelf.kr(Impulse.kr(0)) },
	allowOut: true).add
});
p.sched = 0;
)

//Node to play
(
b = Bus.control(s);
~a = AN({ SinOsc.ar(\freq.kr(440)) }, [\freq, AlgaTemp({ In.kr(b.index) })]);
~a.play;
)

//Sequencer
~b = (def: \seq1, b: b.index, val: Pseq([220, 440, 880], inf), dur: 0.25)
~b <<.dur 0.125
~b <<.dur 0.0625

//Latest value preserved
~b.stopPattern
~b.resumePattern

//Interp still works
(
~b.it = 5;
~b <<.val Pwhite(220, 880);
)

~b <<.val Pseq([220, 440, 880], inf);
//Standard
(
Alga.boot({
	a = AP((
		def: { SinOsc.ar(\freq.kr([440, 441])) * \amp.kr(1) * EnvGen.kr(Env.perc, doneAction:2) },
		freq: Pseq([ 440, [220, 330, 661] ], inf),
		amp: Pseq([[0.5, 1], 1], inf),
		dur: 0.5
	)).play
})
)

a.from(Pseq([[550, 120, 660], 400], inf), \freq, time:5, sched:1)

//fx
(
Alga.boot({
	a = AP((
		def: { SinOsc.ar(\freq.kr(440)) * \amp.kr(1) * EnvGen.kr(Env.perc, doneAction:2) },
		fx: [
			{ (\in.ar * 3).tanh },
			{ (\in.ar * 3).clip }
		],
		dur: 1
	)).play
})
)


//out
(
Alga.boot({
	b = AN({ [(\in.ar * 3).tanh, Silent.ar] }).play;
	c = AN({ [Silent.ar, (\in.ar * 3).clip] }).play;
	a = AP((
		def: { SinOsc.ar(\freq.kr(440)) * \amp.kr(1) * EnvGen.kr(Env.perc, doneAction:2) },
		out: Pseq([ [b, c], b], inf),
		dur: 1
	))
})
)

//Complex usage? AlgaTemp? AlgaArg?
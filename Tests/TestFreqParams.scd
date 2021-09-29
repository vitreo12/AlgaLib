(
Alga.boot({
	a = AP((
		def: { SinOsc.ar(\freq.kr(440)) * EnvGen.ar(Env.perc, doneAction:2) },
		//degree: Pseq([1, 2], inf),
		//detune: Pseq([100, [200, 400], 300], inf),
		freq: Pseq([[440, 880], 220], inf),
		dur: 0.5
	)).play
});
)

a.from(Pseq([1000, 300], inf), \freq, time:0, sched:1)

a.from(Pseq([2000, 300], inf), \freq, time:0, sched:1)

a.replace({ Saw.ar(\freq.kr(440)) * 0.5 * EnvGen.ar(Env.perc, doneAction:2) }, time:2)

a.resetParam(\detune, time:2)
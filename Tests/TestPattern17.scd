(
Alga.boot({
	a = AlgaPattern((
		def: { SinOsc.ar(\freq.kr(223)) * EnvGen.ar(Env.perc(releaseTime:0.5), doneAction:2) }
	)).play;
})
)

a.replace({Saw.ar(\freq.kr(440)) * 0.7 * EnvGen.ar(Env.perc(releaseTime:0.5), doneAction:2)}, sched:1)

a.from(AlgaTemp({LFNoise0.kr(10)}), \freq, scale:[Pseq([300, 400], inf), Pseq([800, 600], inf)], sched:1)
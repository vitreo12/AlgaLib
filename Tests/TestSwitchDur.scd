(
Alga.boot({
	a = AP((
		def: { SinOsc.ar * EnvPerc.ar(release: 0.1) * 0.5 },
		dur: 1
	)).play(chans:2);
})
)

a.from(0.5, \dur, sched: 1)

a.from(\none, \dur, sched: 1)

a.step


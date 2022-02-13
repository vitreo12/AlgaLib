(
Alga.boot({
	~player = AlgaPatternPlayer((
		dur: 0.5,
		freq: AlgaTemp({ DC.kr(220) })
	)).play(sched:1);

	~pattern = AP((
		def: { SinOsc.ar(\freq.kr + \freq2.kr) * EnvPerc.ar(release: 0.5) },
		freq: AT((
			def: { SinOsc.ar(\freq.kr) },
			freq: ~player[\freq]
		), scale: [200, 500])
	), player: ~player).play(chans:2);
})
)

~player.from(AT({DC.kr(100)}), \freq, time:5)


~pattern.from(400, \freq, time:2)
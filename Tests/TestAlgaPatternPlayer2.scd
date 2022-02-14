(
Alga.boot({
	~player = AlgaPatternPlayer((
		dur: 0.5,
		freq: AlgaTemp({ DC.kr(220) }),
		freq2: AlgaTemp({ DC.kr(440) })
	)).play(sched:1);

	~pattern = AP((
		def: { SinOsc.ar(\freq.kr.poll) * EnvPerc.ar(release: 0.1) },
		freq: AT((
			def: { \in.kr },
			in: Pseq([~player.at(\freq, 1), ~player.at(\freq2, 1)], inf)
		))
	), player: ~player).play(chans:2);
})
)
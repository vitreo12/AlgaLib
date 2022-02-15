(
Alga.boot({
	~player = AlgaPatternPlayer((
		dur: 0.5,
		freq: AlgaTemp({ DC.kr(220) }),
		freq2: AlgaTemp({ DC.kr(440) })
	)).play(sched:1);

	~pattern = AP((
		def: {
			SinOsc.ar(\freqPat.kr.poll(0.5, label:"freq1") + \freqPat2.kr.poll(0.5, label:"freq2")) * EnvPerc.ar(release: 0.1)
		},
		freqPat: AT((
			def: { \in.kr },
			in: Pseq([~player.at(\freq, 1), ~player.at(\freq2, 1)], inf)
		)),
		freqPat2: ~player[\freq2]
	), player: ~player).play(chans:2);
})
)

~player.from(AlgaTemp({DC.kr(100)}), \freq, time:5)
~player.from(AlgaTemp({DC.kr(1000)}), \freq, time:5)
~player.from(AlgaTemp({DC.kr(1000)}), \freq2, time:5)

~pattern <<.freqPat ~player[\freq]

~pattern <<.freqPat2 440

~player.algaPatternEntries

~pattern.players
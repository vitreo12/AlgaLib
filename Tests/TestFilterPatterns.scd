(
Alga.boot({
	a = AP(
		(
			def: Pdup(Pwhite(1, 4), Prand([
				{ SinOsc.ar(\freq.kr(440)) * \amp.kr(1) },
				{ LFTri.ar(\freq.kr(440)) * \amp.kr(1) },
				{ LFSaw.ar(\freq.kr(440)) * \amp.kr(1) },
				{ LFPulse.ar(\freq.kr(440)) * \amp.kr(1) },
			], inf)),
			dur: Pseq([0.5, 0.3, 0.3, 0.5], inf),
			freq: Pdup(Pseq((1..4) ++ [3, 2], inf), Pseq([500, 700, 350], inf)),
			amp: AlgaTemp(
				(
					def: { EnvPerc.kr(\att.kr(0.01), \rel.kr(0.6), \curve.kr(-4), 0) * \amp.kr(1) },
					att: Pwhite(0.01, 0.1),
					rel: Pmeanrand(0.4, 0.8),
					curve: Pwhite(-1, -6),
					amp: Pwhite(0.7, 1.0),
				)
			),

		)
	).play(chans: 2)
})
)
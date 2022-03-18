(
Alga.boot({
	a = AP((
		def: { SinOsc.ar(\f.kr(440)) * EnvPerc.ar(release: 0.1) },
		dur: 0.5
	)).play(chans:2);
})
)

//Without latestReplace, this would trigger 5 times
(
5.do { | i |
	a.replace({ Saw.ar(Rand(220, 440)) * EnvPerc.ar(release: 0.1) * 0.3 }, sched: 1)
}
)

//Without latestReplace, this would trigger 5 times
(
5.do { | i |
	(i * 200).postln;
	a.from(i * 200, \f, sched: 1)
}
)

(
Alga.boot({
	a = AN({ SinOsc.ar(\f.kr(440).poll)}).play(chans:2);
})
)

//Without latestReplace, this would trigger 5 times
(
5.do { | i |
	a.replace({ Saw.ar(i * 200) * 0.3 }, time:3)
}
)

//Without latestReplace, this would trigger 5 times
(
5.do { | i |
	b = AN({DC.kr(i * 200)});
	a.mixFrom(b, \f, sched: 0, time:2)
}
)
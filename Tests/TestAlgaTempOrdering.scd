//AN
(
Alga.boot({
	AlgaSynthDef(\sin, {SinOsc.ar(\f.kr(440).dup)}).add;
	AlgaSynthDef(\noise, {LFNoise0.kr(10).range(100, 400).dup}).add;
	s.sync;
	a = AN({ \in.ar }).play;
	a << AT((def:\sin, f: AT(\noise)));
	a <<.amp AT({SinOsc.ar(100)});
	s.plotTree
})
)

//AP 1
(
Alga.boot({
	b = AP((
		def: { SinOsc.ar(\freq.ar.poll(1)) * EnvPerc.ar(release: 0.5) },
		freq: AlgaTemp((
			def: { \in.ar },
			in: AlgaTemp( { DC.ar(880) })
		)),
		amp: AlgaTemp( { DC.ar(1) })
	)).play;
	s.plotTree;
})
)

//AP 2
(
Alga.boot({
	b = AP((
		def: { SinOsc.ar(\freq.ar.poll(1)) * EnvPerc.ar(release: 0.5) },
		freq: AlgaTemp( { DC.ar(880) } )
	)).play;
	s.plotTree;
})
)

//AP 3
(
Alga.boot({
	a = AN({ DC.ar(550) });
	b = AP((
		def: { SinOsc.ar(\freq.ar.poll(1)) * EnvPerc.ar(release: 0.5) },
		freq: AlgaTemp((
			def: { \in.ar },
			in: a
		))
	)).play;
	//a.moveBefore(b); //This is a bug for now... AlgaTemps using AlgaNodes don't reorder
	s.plotTree;
})
)
(
Alga.boot({
	//If using a Symbol as 'dur', the pattern can be advanced manually
	a = AlgaPattern((
		def: { SinOsc.ar(\freq.kr(440)) * EnvPerc.ar(release: 0.5) },
		dur: \manual,
		freq: Pseq([440, 880], inf)
	)).play(chans: 2);
})
)

//Schedule at next beat
a.advance(1);

//Schedule now
a.advance;

//Manually triggers
(
fork {
	loop {
		a.advance;
		0.5.wait;
	}
}
)
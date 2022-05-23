(
Alga.boot({
	//Pattern to modify
	a = AP((
		def: { SinOsc.ar * EnvPerc.ar(release: 0.1) * 0.1 },
		dur: 0.5
	)).play(chans:2);

	//Metronome
	b = AP((
		def: { EnvPerc.ar(release: SampleDur.ir); Impulse.ar(0) },
		dur: 1
	)).play(chans:2);
})
)

//Using Env and resync
a.interpDur(0.1, time: 5, shape: Env([0, 1, 0.5, 1], [2, 3, 4]))

//No resync
a.interpDur(0.5, time: 3, resync: false)

//With resync + reset
a.interpDur(Pseq([0.25, 0.25, 0.125], inf), time: 5, reset: true)

//Other time params work too!
a.interpStretch(0.5, time: 3, shape: Env([0, 1, 0.5, 1], [2, 3, 4]))

(
Alga.boot({
	//Pattern to modify
	a = AP((
		def: { SinOsc.ar * EnvPerc.ar(release: 0.1) * 0.5 },
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
a.interpDur(0.5, time: 3, resync: true)

//With resync + reset
a.interpDur(Pseq([0.25, 0.25, 0.125], inf), time: 5, reset: true)

//Other time params work too!
a.interpStretch(0.5, time: 3, shape: Env([0, 1, 0.5, 1], [2, 3, 4]))


//Using APP
(
Alga.boot({
    //Define and start an AlgaPatternPlayer
    ~player = AlgaPatternPlayer((
        dur: Pwhite(0.1, 0.5),
        freq: Pseq([440, 880], inf)
    )).play;

    //Use ~player for both indexing values and triggering the pattern
    ~pattern = AP((
		def: { SinOsc.ar(\freq.kr + \freq2.kr) * EnvPerc.ar(release: 0.1) * 0.3 },
        freq: ~player[\freq],
        freq2: ~player.read({ | freq |
            if(freq == 440, { freq * 2 }, { 0 })
        }),
    ), player: ~player).play(chans: 2);

	b = AP((
		def: { EnvPerc.ar(release: SampleDur.ir); Impulse.ar(0) },
		dur: 1
	)).play(chans:2);
})
)

//Using Env and resync
~player.interpDur(0.1, time: 5, shape: Env([0, 1, 0.5, 1], [2, 3, 4]))

//No resync
~player.interpDur(0.5, time: 1, resync: false)

//With resync + reset
~player.interpDur(Pseq([0.25, 0.25, 0.125], inf), time: 5, reset: true)

//Other time params work too!
~player.interpStretch(0.5, time: 3, shape: Env([0, 1, 0.5, 1], [2, 3, 4]))

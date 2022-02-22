(
Alga.boot({
    //Define and start an AlgaPatternPlayer
    ~player = AlgaPatternPlayer((
        dur: Pwhite(0.1, 0.5),
        freq: Pseq([440, 880], inf)
    )).play;

    //Use ~player for both indexing values and triggering the pattern
    ~pattern = AP((
        def: { SinOsc.ar(\freq.kr + \freq2.kr) * EnvPerc.ar },
        freq: ~player[\freq],
        freq2: ~player.read({ | freq |
            if(freq == 440, { freq * 2 }, { 0 })
        }),
    ), player: ~player).play(chans: 2);
})
)

//Interpolation still works
~pattern.from(~player.({ | freq | freq * 0.5 }), \freq, time: 5) //.value == .read
~pattern.from(~player.({ | freq | freq * 2 }), \freq2, time: 5)

//Modify dur
~player.from(0.5, \dur, sched: AlgaStep(3))

//If modifying player, the interpolation is still triggered on the children
~player.from(Pseq([330, 660], inf), \freq, time: 5)

//Removing a player stops the pattern triggering
~pattern.removePlayer;
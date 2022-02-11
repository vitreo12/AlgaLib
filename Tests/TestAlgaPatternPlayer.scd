//AlgaPlayer ideas. Expanded from Examples/PatternPlayer
(
Alga.boot({
	//Starts the pattern on creation.
	//Note that [] allows the entries to be executed sequentially,
	//allowing things like Pkey to work!
	~player = AlgaPatternPlayer((
		dur: Pwhite(0.2, 0.7),
		freq: Pseq([440, 880], inf)
	)).run;

	//\dur is not used with a player
	~pattern = AP((
		def: { SinOsc.ar(\freq.kr + \freq2.kr) * EnvPerc.ar },
		freq: ~player[\freq],
		freq2: ~player.read({ | freq |
			if(freq == 440, { freq * 2 }, { 0 })
		}),
	), player: ~player).play;
})
)

//Interpolation still works
~pattern.from(~player.({ | freq | freq * 0.5 }), \freq, time: 5) //.value == .read
~pattern.from(~player.({ | freq | freq * 2 }), \freq2, time: 5)

//Modify dur
~player.from(0.5, \dur, sched: AlgaStep(3))

//This should trigger interpolation on readers
~player.from(Pseq([330, 660], inf), \freq, time: 5)

//Removing a player stops the pattern triggering
~pattern.removePlayer;

// !!!!!!!!!!!!
//Run parser for what's needed! Is it only AlgaTemps?
//Parse AlgaTemps like AlgaPattern!

// !!!!!!!!!!!
//Implement Array
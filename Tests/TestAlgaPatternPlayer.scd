//AlgaPlayer ideas. Expanded from Examples/PatternPlayer
(
Alga.boot({
	//Starts the pattern on creation.
	//Note that [] allows the entries to be executed sequentially,
	//allowing things like Pkey to work!
	~player = AlgaPatternPlayer([
		dur: Pwhite(0.2, 0.7),
		freq: Pseq([440, 880], inf
	], sched: 1);

	//\dur is not used with a player
	~pattern = AP((
		def: { SinOsc.ar(\freq.kr + \freq2.kr) * EnvPerc.ar },
		freq: ~player[\freq], //single key access (returns Pfunc)
		freq2: ~player.read({ | freq |
			if(freq == 440, { freq * 2 }, { 0 }) //function access (also returns Pfunc)
		}),
	), player: ~player).play;
})
)

//Interpolation still works
~pattern.from(~player.({ | freq | freq * 0.5 }), \freq, time: 5) //.value == .read
~pattern.from(~player.({ | freq | freq * 2 }), \freq2, time: 5)

//Modify time
~player.from(0.5, \dur, sched: AlgaStep(3))

//This should trigger interpolation on readers
~player.from(Pseq([330, 660], inf), \freq, time: 5)

//Removing a player stops the pattern triggering
~pattern.removePlayer;
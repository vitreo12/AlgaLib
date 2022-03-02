//Inspired by http://sccode.org/1-5fG#c992
(
Alga.boot({
	//Starting dur
	~dur = 0.5;

	//Pattern to modify
	a = AP((
		def: { SinOsc.ar * EnvPerc.ar(release: 0.1) * 0.5 },
		dur: Pfunc { ~dur.next }
	)).play(chans:2);

	//Click
	b = AP((
		def: { Impulse.ar(0) },
		dur: 1
	)).play(chans:2);

	//Scale over a set number of beats
	~change_clock_tempo = {|clock, tempo, beats=1|
		var oldDur = ~dur;
		var newDur = 1.0 / tempo;
		beats.do {|beat|
			var stepDur = beat.linexp(0, beats-1, oldDur, newDur);
			clock.algaSchedAtQuantOnceWithTopPriority(beat, {
				~dur = stepDur;
				~dur.postln;
				//On last beat, resync
				if(beat == (beats-1), { a.from(Pfunc { ~dur.next }, \dur, sched: 0) })
			});
		};
	};
})
);

//Double speed over 8 beats
~change_clock_tempo.value(a.clock, 240/60, 8);

//Same
~change_clock_tempo.value(a.clock, 120/60, 8);

//Half
~change_clock_tempo.value(a.clock, 60/60, 8);
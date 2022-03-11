//Inspired by http://sccode.org/1-5fG#c992
(
Alga.boot({
	~change_clock_tempo = {|clock, tempo, beats=1|
		beats.do {|beat|
			var stepTempo = beat.linexp(0, beats-1, clock.tempo, tempo);
			clock.schedAbs(clock.nextBar + beat, {
				"New Tempo: ".post; (stepTempo*60).post; " BPM".postln;
				clock.tempo_(stepTempo);
			});
		};
		nil;
	};

	a = AP((
		def: { SinOsc.ar(\freq.kr(440)) * EnvPerc.ar(release: 0.1) },
		dur: 0.5,
		freq: Pwhite(440, 500)
	)).play
});
)

~change_clock_tempo.value(a.clock, 200/60, 16);
~change_clock_tempo.value(a.clock, 40/60, 8);
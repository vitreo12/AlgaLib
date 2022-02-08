//Using a second pattern as a "player" to trigger custom sequences and have
//interpolation working with Pif, etc...
(
Alga.boot({
	~freqStream = Pseq([440, 880], inf).asStream;

	~pattern = AP((
		def: { SinOsc.ar(\freq.kr + \freq2.kr) * EnvPerc.ar },
		dur: \manual,
		freq: Pfunc { ~freq },
		freq2: Pif(Pfunc { ~freq == 440 }, Pfunc { ~freq * 2 }, 0),
		time: Pfunc { ("pattern - " ++ ~pattern.clock.seconds.asString).postln; }
	)).play;

	~player = AP((
		def: { Silent.ar * EnvPerc.ar(release: SampleDur.ir) },
		dur: Pwhite(0.2, 0.7),
		trig: Pfunc {
			//This avoids overlaps caused by changing \dur in the player!
			~time = ~player.clock.seconds;
			if(~time != ~oldTime, {
				~freq = ~freqStream.next;
				~pattern.step;
			});
			~oldTime = ~time;
		},
		time: Pfunc { ("player - " ++ ~player.clock.seconds.asString).postln; }
	))
})
)

//Interpolation still works
~pattern.from(Pfunc { ~freq * 0.5 }, \freq, time: 5)
~pattern.from(Pfunc { ~freq * 2 }, \freq2, time: 5)

//Thanks to ~time, there's no double trigger caused by rescheduling!
~player.from(0.5, \dur, sched: AlgaStep(3))
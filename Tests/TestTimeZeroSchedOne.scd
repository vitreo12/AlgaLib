(
Alga.maxIO = 2;
Alga.initSynthDefs;
Alga.boot({
	a = AP((
		def: { SinOsc.ar(\freq.ar(440).poll) * EnvGen.ar(Env.perc, doneAction:2) },
		freq: Pseq([440, 220], inf),
		dur: 0.5
	)).play
});
)

a.from(Pseq([1000, 300], inf), \freq, time:0, sched:1)


a.from(Pseq([1000, 300], inf), \freq, time:0, sched:0)


//What about that sound hole ??
a.from(Pseq([1000, 300], inf), \freq, time:0.3, sched:1)

a.from(Pseq([440, 300], inf), \freq, time:1, sched:1)

a.from(0.5, \dur, sched:1)

(
fork {
	loop {
		"new".postln;
		a.from(Pseq([1000, 300], inf), \freq, time:0, sched:1);
		rrand(0.5, 3).wait;
	}
}
)
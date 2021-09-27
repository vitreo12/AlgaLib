(
Alga.boot({
	a = AP((
		def: { SinOsc.ar(\freq.ar(10)) * EnvGen.ar(Env.perc, doneAction:2) },
		freq: 440,
		dur: 0.5
	)).play
});
)

a.from(100, \freq, time:0.02, sched:1)

a.from(44, \freq, time:0.02, sched:1)

//Repeat this:
//Note that even the old hanging ones will interpolate to the new value. I don't want that.
//Only the new ones should. The old ones, however, need to be kept alive for their
//whole duration, INTERPOLATION TOO! or they will go to 0 if the interpolation is shorter
//than their remaining length!k
a.from(AlgaTemp({ DC.ar(1) }, scale:Pseq([200, 500], inf)), \freq, time:0.2, sched:1)
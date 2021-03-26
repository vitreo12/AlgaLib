Alga.boot(clock:SystemClock)

a = AN({RLPF.ar(WhiteNoise.ar) * \amp.ar(0.5)}).play;

(
a.it = 4;

fork {
	var sched = 5;
	a.from(0, \amp, sched:sched);
	sched.wait;
	"starting".postln;
	a.it.wait;
	"end".postln;
}
)
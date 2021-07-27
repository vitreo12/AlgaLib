(
Alga.boot({

	AlgaSynthDef(\test, {
		PlayBuf.ar(2, \buf.kr(0), BufRateScale.kr(\buf.kr), doneAction:Done.freeSelf)
	}).add;

	b = Buffer.read(s, "C:/Users/frank/Desktop/kick.wav");

	c = Buffer.read(s, "C:/Users/frank/Desktop/snare.wav");

	s.sync;

	a = AlgaPattern((
		def: \test,
		dur: 0.25,
		buf: b
	), playTime:2).play;
});
)

a.pt = 0

a.from(c, \buf, time:2, sched:1)

a.from(b, \buf, time:2, sched:1)

a.stop

a.from(Pseq([b, c], inf), \buf, time:0, sched:1)

a.from(0.5, \dur, time:2, sched:1)

a.from(0.25, \dur, time:2, sched:1)

a.from(b, \buf, time:0, sched:1)

a.from(c, \buf, time:0, sched:1)

d = AN({ FreeVerb.ar(\in.ar([0, 0]), 1) }).play

d << a

a.play

d.stop(2)
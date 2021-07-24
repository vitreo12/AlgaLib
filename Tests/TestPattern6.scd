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
		dur: 0.2,
		buf: b
	), playTime:2).play;
});
)


a.from(c, \buf, time:2, sched:1)

a.from(b, \buf, time:2, sched:1)

a.from(Pseq([b, c], inf), \buf, time:3, sched:1)
(
Alga.boot({

	AlgaSynthDef(\test, {
		PlayBuf.ar(2, \buf.kr(0), BufRateScale.kr(\buf.kr), doneAction:2)
	}).add;

	b = Buffer.read(s, File.realpath(Alga.class.filenameSymbol).dirname.withTrailingSlash ++ "../Examples/Samples/kick.wav");

	c = Buffer.read(s, File.realpath(Alga.class.filenameSymbol).dirname.withTrailingSlash ++ "../Examples/Samples/snare.wav");

	s.sync;

	a = AlgaPattern((
		def: \test,
		dur: 0.25,
		buf: b
	)).play;
});
)

a.from(c, \buf, time:2, sched:1)

a.from(b, \buf, time:2, sched:1)

a.from(Pseq([b, c], inf), \buf, time:0, sched:1)

a.from(0.5, \dur, time:2, sched:1)

a.from(0.25, \dur, time:2, sched:1)

a.from(b, \buf, sched:1)

a.from(c, \buf, sched:1)

d = AN({ FreeVerb.ar(\in.ar([0, 0]), 1) }).play

d << a

d.stop(2)
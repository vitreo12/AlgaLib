(
Alga.boot({
	//Simple Buffer player
	AlgaSynthDef(\playBuf, {
		PlayBuf.ar(2, \buf.kr(0), BufRateScale.kr(\buf.kr), doneAction:2)
	}, sampleAccurate: true).add;

	//Read a kick and a snare
	b = Buffer.read(s, File.realpath(Alga.class.filenameSymbol).dirname.withTrailingSlash ++ "../Examples/Samples/kick.wav");
	c = Buffer.read(s, File.realpath(Alga.class.filenameSymbol).dirname.withTrailingSlash ++ "../Examples/Samples/snare.wav");

	//Wait
	s.sync;

	//Start by playing the kick
	a = AlgaPattern((
		def: \playBuf,
		dur: 0.5,
		buf: b
	)).play;
});
)

//Play the snare instead. A Buffer parameter won't interpolate, but will 'replace'
a.from(c, \buf, time: 2, sched: 1);

//Play both kick and snare
a.from(Pseq([b, c], inf), \buf, time: 0, sched: 1);

//Go faster
a.from(0.25, \dur, time: 2, sched: 1);

//Declare a reverb
d = AN({ FreeVerb.ar(\in.ar([0, 0]), 1) }).play;

//Connect the two
d.from(a, time: 2);

//Bye bye
(
a.clear({
	d.clear;
}, time: 2)
)
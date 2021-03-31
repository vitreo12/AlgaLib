(
Alga.boot({

	b = Buffer.read(s, Platform.resourceDir +/+ "sounds/a11wlk01.wav");

	c = Buffer.read(s,File.realpath(FluidAmpGate.class.filenameSymbol).dirname.withTrailingSlash ++ "../AudioFiles/Nicol-LoopE-M.wav");

	s.sync;

	a = AN({PlayBuf.ar(1, \b.kr(b), BufRateScale.kr(\b.kr(b)) * \s.kr(1), loop:1)}, [\s, 2])
})
)

p = AN({\in.ar([0, 0])}, connectionTime:1).play

p << a;

//\s is maintained!
a.from(c, \b);

a.from(0.4, \s, time:1);

a.mixFrom(0.7, \s, time:2)

a.from(b, \b);

//Keeps 0.7
a.replace( {PlayBuf.ar(1, \b.kr(b), BufRateScale.kr(\b.kr(b)) * \s.kr(1), loop:1) } )

//Now 2 is used again
a.replace( {PlayBuf.ar(1, \b.kr(b), BufRateScale.kr(\b.kr(b)) * \s.kr(1), loop:1) }, [\s, 2])

d = AN({DC.kr(1)})

a.from(d, \s, time:2)

a.from(c, \b)

a.disconnect(\s, time:2)

a.clear(2);
p.clear(2);
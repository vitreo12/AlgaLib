(
APS.boot({
	~a.it = 5;
	~a.play(chans:2, scale:0.8);
	~a = { LeakDC.ar(BMoog.ar(\in.ar, \freq.kr(440), \q.kr(0.5))) };
	~b.it = 3;
	~b = { Saw.ar(\freq.kr(50)) };
	~b >> ~a;
}, algaServerOptions: AlgaServerOptions(blockSize: 64));
)

~a.mixFrom(~a, scale:0.5) //care with the fb here
~a <<.freq { LFNoise1.kr(LFNoise0.kr(3).range(1, 10)).range(50, 1500) }

~b <<.freq { LFNoise0.kr(LFNoise1.kr(2).range(1, 20)).range(0.1, 30) }

s.scope
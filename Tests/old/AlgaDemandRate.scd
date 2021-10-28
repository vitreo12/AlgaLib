Alga.boot

a = AlgaNode({LFNoise0.ar(\f.kr(1)) > 0.5})

b = AlgaNode({Demand.ar(\in.ar, 1, Dseq([440, 880], inf))})

a >> b

b.replace({Demand.ar(\in.ar, 1, Dseq([Dseq([44, 88, 99, 101]), Dwhite(100, 200, 1)], inf))})

c = AlgaNode({SinOscFB.ar(\f.ar, \fb.ar(0.9)).dup * SinOsc.ar(LFNoise1.kr(4).range(0.1, 1)) * 0.5}, playTime:2).play

d = AlgaNode({Demand.ar(\in.ar, 1, Dwhite(0, 1))})

f = AlgaNode({OmniDelay3.ar(\in.ar, \dt.kr(0.5), \fb.kr(0.8), \dp.kr(0.8), 1.0).dup * \amp.ar(0.1)}, playTime:2).play

c >> f

a >> d

d >>.fb c

b >>.f c

a.ct = 5

a <<.f 5

f.ct = 2

f <<.dt 0.6

f <<.fb 0.99

f <<.dp 0.9

f <<.amp 0.2
Alga.boot

s.plotTree

a = AN({SinOsc.ar(\f.ar(440))}).play

b = AM({SinOsc.ar(\f.kr(2))})

a.from(b, \f, scale:[330, 440])

c = AM({LFNoise0.kr(2)})

b.from(c, \f, scale:[1, 20])

d = AE({(\in.ar * 3).tanh})

f = AE({FreeVerb.ar(\in.ar, 0.8)})

a.stop

d.from(a)

f.from(d)

f.play

g = AN({Saw.ar(\f.ar)})

g.from(a, \f, scale:[20, 300])

a.blockIndex
b.blockIndex
c.blockIndex
d.blockIndex
f.blockIndex
g.blockIndex
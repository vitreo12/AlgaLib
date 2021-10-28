Alga.boot

a = AlgaNode({\in.ar(0)}).ft_(2)
b = AlgaNode({\in.ar(0)})
//c = AlgaNode({\in.ar(0)})

a >> b
b >> a
//c >> a

a.replace({\in.ar(0)})

s.plotTree

CmdPeriod.run()

Alga.boot

a = AN({SinOsc.ar(\f.ar(1)*440)}, fadeTime:2)
b = AN({SinOsc.ar(\f.ar(1)*440)})

a.play

b >>.f a

//a >>.f b

a.ft = 1
b.replace({SinOsc.ar(\f.ar(1)*2000)})
b.longestFadeTime


a.ft = 1
b.replace({DC.ar(1)})

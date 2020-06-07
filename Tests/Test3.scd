Alga.boot

a = AlgaNode({\in.ar(0)})
b = AlgaNode({\in.ar(0)})
//c = AlgaNode({\in.ar(0)})

a >> b
b >> a
//c >> a

a.replace({\in.ar(0)})

s.plotTree

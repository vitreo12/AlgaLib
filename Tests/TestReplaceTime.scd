Alga.boot

a = AlgaNode({ SinOsc.ar([220, 440, 880, 1760])}, outsMapping: [\one, [0, 1], \two, [2, 3], \three, 2, \four, 3])
b = AlgaNode({ \in.ar([0, 0]) }).play  //a simple 2 channel bypass
b.from(a, \in, chans: \one)  //connects \one == [0, 1]
b.from(a, chans: \two, time:1)   //connects \two == [2, 3]. note that \in is the default param name

b.pt = 2
b.replace({ \in.ar([0, 0]) })
b.stop
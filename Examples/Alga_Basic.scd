//boot Alga
Alga.boot

// \one will have 2 channels,  [0, 1]
// \two will have 2 channels,  [2, 3]
// \three will have 1 channel, [2]
// \four will have 1 channel,  [3]
(
a = AlgaNode(
    { SinOsc.ar([220, 440, 880, 1760]) },
    outsMapping: [\one, [0, 1], \two, [2, 3], \three, 2, \four, 3]
)
)

//a simple 2 channel bypass
b = AlgaNode({ \in.ar([0, 0]) }).play

//connects \one == [0, 1]
b.from(a, \in, chans: \one)

//connects \two == [2, 3]. Note that \in is the default param name
b.from(a, chans: \two)



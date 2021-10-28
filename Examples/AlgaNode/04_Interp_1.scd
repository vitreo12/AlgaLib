//Boot Alga
Alga.boot

//3 seconds of interpolation time
a = AlgaNode({ SinOsc.ar(\freq.kr(440)) }, interpTime:3).play(chans:2)

//Now it will interpolate
a <<.freq 220

//Random linear noise
b = AlgaNode({ LFNoise1.kr(2) })

//if 2 values, scale assumes the range -1 to 1
a.from(b, \freq, scale:[220, 440])

//Disconnect, it will go back to 440, the default value
a <| \freq


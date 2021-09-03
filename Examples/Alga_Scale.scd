//boot Alga
Alga.boot

//one parameter, \freq at control rate
a = AlgaNode({ SinOsc.ar(\freq.kr(440)) }).play

//a simple LFO
b = AlgaNode({ SinOsc.kr(1) })

//Why doesn't it work? Cause b's output is -1 / +1
a <<.freq b

//With 'from / to' it's possible to 'scale' a parameter
a.from(b, \freq, scale:[-1, 1, 220, 440])

//a faster LFO
c = AlgaNode({ SinOsc.kr(10) })

//add to mix
a.mixFrom(c, \freq, scale:[-1, 1, 20, 100])

//remove the c AlgaNode from the \freq mix
a.disconnect(\freq, c)

//remove all connections at the specific param,
//resetting to the default val
a <| \freq


//boot Alga
Alga.boot

//2 sines. \freq has two control rate channels
a = AlgaNode({ SinOsc.ar(\freq.kr([220, 440])) }).play

//5 LFOs at audio rate
b = AlgaNode({ SinOsc.ar([1, 2, 3, 4, 5]) })

//rate conversion ( ar -> kr )
//channels conversion ( 5 -> 2 )
//per-channel scaling with arrays
a.from(b, \freq, scale:[-1, 1, [100, 200], [1000, 2000]], time: 2)


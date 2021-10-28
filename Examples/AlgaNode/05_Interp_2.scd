//Boot Alga
Alga.boot;

//Sine
a = AlgaNode({ SinOsc.ar(\freq.kr(440)) }, interpTime:3).play(chans:2)

//LFO
b = AlgaNode({ SinOsc.kr(2) })

//Connect. Notice the interpolation from 440 to the LFO over 3 seconds
a.from(b, \freq, scale:[330, 550])

//Replace b from an LFO to a random ramp generator. Notice how now a's \freq parameter is getting
//interpolated from the LFO to the newly set random ramp generator, with the same scaling
b.replace({ LFNoise1.kr(5) })

//Now replace it with a Phasor. Again, it will interpolate from the noise generator to the
//new Phasor over 3 seconds, which is a's interpTime. Same scaling is applied
b.replace({ LFSaw.kr(5) })

//Let's change scaling
a.from(b, \freq, scale:[100, 1000])

//And replace back with a faster noise than before
b.replace({ LFNoise1.kr(10) })


/* All possible values for an AlgaNodeProxy: */

//Function
~a = {SinOsc.ar}

//Symbol (SynthDef)
~b = \default

//Array
~c = [440, 441]

//Pbind instrument: when \instrument is provided (to be patched as audio)
~d = Pbind(\instrument, \default, \dur, 0.5)

//Pbind control: when \instrument is not provided (to be patched as control)
~e = Pbind(\dur, 0.5)

//Standard pattern (to be patched in Pbinds (both instrument and control)
~f = Pseq([0, 1, 2], inf)


/* Connections should work for all, plus Functions and Arrays */

~a <=.freq {SinOsc.ar.range(10, 100)}

~b <=.freq [440, 441]
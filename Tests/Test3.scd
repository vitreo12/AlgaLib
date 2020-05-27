/* All possible values for an AlgaNodeProxy: */

//Function WORKS
~a = {SinOsc.ar}

//Symbol (SynthDef) WORKS
~b = \default

//Array
~c = [440, 441]

//Pbind instrument: when \instrument is provided (to be patched as audio)     WIP
~d = Pbind(\instrument, \default, \dur, 0.5)

//Pbind control: when \instrument is not provided (to be patched as control)  WIP
~e = Pbind(\dur, 0.5)

//Standard pattern (to be patched in Pbinds (both instrument and control)     WIP
~f = Pseq([0, 1, 2], inf)


/* Connections should work for all, plus Functions, Arrays, Pbinds*/

~a <=.freq {SinOsc.ar.range(10, 100)}

~a <=.freq [440, 441]

~a <=.freq Pbind(\dur, Pseq([1, 2], inf), \value, Pwhite(10, 100))
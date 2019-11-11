p = VitreoProxySpace.new.push(s.boot)

//Problem 1:
~a = {SinOsc.ar}

//Needs brackets if using this syntax, or it will chain the operators...
~b <= (~a * 0.2)
(~a * 0.2) => ~b

//Should this be supported too? It doesn't work if ddwChuckLib is installed
~b <= {~a * 0.1}

~b.play

//Problem 2:
//This doesn't insure ~b to be before ~a
~b = ~a * 0.1

//While this ensures ~b before ~c
~c <= ~b
~b => ~c

//Problem 3:
//FUNCTION
~d = {~a * SinOsc.ar(\a.kr(2)) * \b.kr(0.5)}

//How can I find if ~d internally refers to another NodeProxy?
~d.source.def.sourceCode

//If using another proxy, it's in the constants
~d.source.def.constants

//This is the space where I could look for those constants
VitreoProxySpace.findSpace(~d)

//If it's a VNdef:
VNdef.all.at(\localhost)

//Problem 4:
//Array
~a = {SinOsc.ar(\freq.kr([440, 441]))}
~lfo1 = {SinOsc.kr(1).range(10, 500)}
~lfo2 = {SinOsc.kr(2).range(10, 500)}

~a <=.freq [~lfo1, ~lfo2]

~c = [~lfo1, ~lfo2]




//Problem 5:
//Pbinds, generally

~c = Pbind(\dur, 1)










//Lesser problems:

//Problem 6:
//With symbols:
(
SynthDef(\smt, {
	var a = 1;

	Out.ar(\out.kr, ~a * SinOsc.ar(a));
}).add;
)

~f = \smt;

~f.source.class

SynthDescLib.global.at(~f.source).def.func.def.constants
p = VitreoProxySpace.new.push(s.boot)

p.clear

//Problem 1:
(
~a = {SinOsc.ar};
~b = {\in.ar(0)};
~b.play;
)

//Needs brackets if using this syntax, or it will chain the operators...
~b <= (~a * ~a)
(~a.tanh * ~a.tanh * ~a) => ~b


//Problem 2
~b = {\in.ar(0)}

//This should be allowed (connection before definition of the modulator (modulated too maybe)?
~c => ~b

~c.group

~c = {SinOsc.ar(\freq.kr(100))}

~b.play

~c <=.freq {rrand(300, 1000)}

~c.fadeTime = 5

~c.reshaping

~c <=.freq 440

~c.play


//Problem 3:
//This doesn't insure ~b to be before ~a
~b = ~a * 0.1

//While this ensures ~b before ~c
~c <= ~b
~b => ~c

//Problem 34
//FUNCTION
~d = {~a.ar * ~b.ar}

~d = {~a.ar * 2}

~d[1] = {~b.ar * 2}

~d.inProxies
~a.outProxies
~b.outProxies

~d = \default


c = ProxySpace.new.push(s.boot)





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
//Have a specialized inProxies.do() function that spits out Array entries too..


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
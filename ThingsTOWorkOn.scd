(
s.options.sampleRate = 44100;
s.options.inDevice = "Built-in Microph";
s.options.outDevice = "Built-in Output";
)

p = VitreoProxySpace.new.push(s.boot)

//Problem 1:
(
~a = {SinOsc.ar};
~b = {\in.ar(0)};
~b.play
)

//Needs brackets if using this syntax, or it will chain the operators...
~b <= (~a * 0.1)
(~a * ~a * ~a) => ~b


//Doesn't switch over anymore??? wtf
~b <= ~a


~b.fadeTime = 4

~a.outProxies
~b.inProxies


(
~a = {SinOsc.ar(\freq.kr(300))};
~a.play;

~b = {DC.ar(100)};
~c = {DC.ar(400)};
)

~a.fadeTime = 5;

~a <=.freq (~b * 1.0)
~a <=.freq (~c * 1.0)


c = ProxySpace.new.push(s.boot);

(
~a = {SinOsc.ar};
~b = {\in.ar(0)};
)

~c = (~a * 0.6)
~b <<> ~c
~b.play






//Should this be supported too? It doesn't work if ddwChuckLib is installed
~b <= {~a * 0.1}

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
//Have a specialized inProxies.do() function that spits out Array entries too..


//Problem 5:
//Pbinds, generally

~c = Pbind(\dur, 1)



//Problem 6:
//Mapping the same NodeProxy to different params doesn't work right.
//Its outParams will just show the nodeProxy. this screws up connections if one of the
//multiple connections is unset, it will unset them all for the block situation

~a = {SinOsc.ar(mul: \amp1.kr(1) * \amp2.kr(1))}

~c = {DC.ar(1)}

~c =>.amp1 ~a <=.amp2 ~c

//Then, it won't be registered that ~c is still used for amp1 in its outProxies
~a <| \amp1;



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
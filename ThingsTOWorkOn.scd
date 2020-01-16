p = AlgaProxySpace.new.push(s.boot)

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

(
~a = {SinOsc.ar};
//~b = {\in.ar(0)};
~b.play;
)

//This doesn't insure ~b to be before ~a
~b = ~a * 0.1

~b.inProxies

//While this ensures ~b before ~c
~c <= ~b
~b => ~c




//Problem 3
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
AlgaProxySpace.findSpace(~d)

//If it's a VNdef:
VNdef.all.at(\localhost)






//Problem 4:
//Array
~a = {SinOsc.ar(\freq.kr([440, 441]))}
~lfo1 = {SinOsc.kr(1).range(10, 500)}
~lfo2 = {SinOsc.kr(2).range(10, 500)}

~a <=.freq [~lfo1, ~lfo2]

~c = [~lfo1, ~lfo2]



reshaping


//Problem 5: MULTICHANNEL STUFF
/*
Things to look into:

reshaping = \elastic

mold

*/

p = AlgaProxySpace.new.push(s.boot);

p.clear

~out  = { \in.ar([0, 0]) };
~out.play

~s1 = {SinOsc.ar([440, 500])}

//Binops broken with new changes...
~s2 = (~s1 * 0.5)

//Problem is interpolationProxies, which now just defaults as 1 channel!
~s1 => ~out

~s2 => ~out




~out <= {SinOsc.ar}

p = ProxySpace.new.push(s.boot);

p.clear

~out = { \in.ar([0, 0]) };
~out.play

~s1 = {SinOsc.ar([440, 447])}

~s1 <>> ~out






//Problem 6:
//Patterns, generally

p = AlgaProxySpace.new.push(s.boot)

(
~b = {SinOsc.kr(0.3).range(0.01, 0.3)};
~c = Pbind(\degree, Pseq([1, 3, 5], inf), \dur, 1);
~c.play;
~c.ft = 4;
)


/* MODIFYING PATTERNS' PARAMETERS */

/* 1: Modifying pattern with another pattern. Needs to be passed in with .asStream to work */
//~c <=.dur Pseq([1, 0.2, 0.3], inf)
~c <<>.dur Pseq([1, 0.2, 0.3], inf).asStream


/* 2: Using a .kr VNProxy. It should be wrapped in Pkr and passed as .asStream too */
//~c <=.dur ~b
~c <<>.dur PAlgakr(~b).asStream


/* 3: Using Functions and AbstractOpPlugs.. should be turned to PAlgakr */
//~c <=.dur {LFNoise0.kr(1).range(0.1, 1)}
//~c <=.dur {~b * SinOsc.kr(1).range(0.1, 1)}
//etc...


/* MODIFYING PATTERNS' SYNTH PARAMETERS */

//Appears to be working already, just for .kr stuff though...
~c <=.freq {LFNoise0.kr(0.4).range(300, 1000)}




/* USING PATTERNS TO MODIFY ALREADY RUNNING PROXIES.. It's better to use Demand rate stuff */
(
~a = {SinOsc.ar(\freq.kr(440))};
~a.play;
)


~a <=.freq {Demand.kr(Impulse.kr(1), 0, Dseq([440, 880], inf))}


PatternProxy




































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
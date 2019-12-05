p = AlgaProxySpace.new.push(s.boot);

p.clear;

(
~a = {SinOsc.ar(\freq.kr(440))};
~c = {Saw.ar(\freq.kr(440))  * 0.2};
~lfo = {LFNoise0.kr(1).range(100, 500)};
~b = {\in.ar(0)};
~b.play;
)


//Problem 1, general behaviours:
~b <= ~a

~c => ~b

~a <=.freq 500

~b <= ~a

~a.ft = 2

//Error here
~a <=.freq ~lfo


// ?????
~c <=.freq ~lfo

~c => ~b

~c <=.freq 500



(
ANdef(\a,{SinOsc.ar(\freq.kr(440))});
ANdef(\c, {Saw.ar(\freq.kr(440)) * 0.2});
ANdef(\lfo, {LFNoise0.kr(\speed.kr(1)).range(100, 500)});
ANdef(\d, {\in.ar(0)});
ANdef(\b, {(\in.ar(0) * 0.5) + (\in2.ar(0) * 0.5)}).play;
)


ANdef(\d).ft = 3;

ANdef(\b) <= ANdef(\d);

ANdef(\a) <=.freq ANdef(\lfo)

//Problem 1, general behaviours:
ANdef(\d) <= ANdef(\a);

ANdef(\c) <=.freq ANdef(\lfo)

//Why does it click here??? Cause ANdef(\a)'s group gets moved and it's not done yet..
//ANdef(\a) here should be removed from the block though...
ANdef(\c) => ANdef(\d);

ANdef(\lfo).ft = 2
ANdef(\lfo) <=.speed 2


ANdef(\a) <=.freq 500;

ANdef(\b) <= ANdef(\a);


ANdef(\c) => ANdef(\b)

ANdef(\a) =>.in2 ANdef(\b)

ANdef(\c) <=.freq 500

ANdef(\lfo).outProxies

ANdef.clear

ANdef(\a).outProxies

(
Ndef(\a,{SinOsc.ar(\freq.kr(440))});
Ndef(\c, {Saw.ar(\freq.kr(440))  * 0.2});
Ndef(\lfo, {LFNoise0.kr(1).range(100, 500)});
Ndef(\b, {\in.ar(0)}).play;
)

//Problem 2, Funcions:
//This multiple times, crashes.
~b <= {~a * ~a}



contains
a = Array.new

a.add(2)

a



//Problem 3: MULTICHANNEL STUFF
p = AlgaProxySpace.new.push(s.boot);

p.clear

~out  = { \in.ar([0, 0]) };
~out.play

~s1 = {SinOsc.ar([440, 447])}

//Problem is interpolationProxies, which now just defaults as 1 channel!
~s1 => ~out

//Back to 1
~s1 = {SinOsc.ar};

~s1.numChannels;
~out.interpolationProxies[\in].numChannels;



p = AlgaProxySpace.new.push

~a = { SinOsc.ar };
~b = { \in.ar(0) };

~b.play;

~a => ~b

~a.reshaping = \expanding;
~a = { SinOsc.ar([440, 442]) }

~b.reshaping = \expanding;
~b = { \in.ar([0, 0]) }


(
ANdef(\a, { SinOsc.ar });
ANdef(\b, { \in.ar(0) }).play;
ANdef(\a) => ANdef(\b);
)

ANdef(\a, { SinOsc.ar([440, 441]) });

(
ANdef(\a, { SinOsc.ar([440, 446]) });
ANdef(\b, { \in.ar([0, 0]) }).play;
ANdef(\a) => ANdef(\b);
)



p = ProxySpace.new.push(s.boot);

p.clear

~out = { \in.ar([0, 0]) };
~out.play

~s1 = {SinOsc.ar([440, 447])}

~s1 <>> ~out






//Problem 4:
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
~c <<>.dur Pkr(~b).asStream


/* 3: Using Functions and AbstractOpPlugs */
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


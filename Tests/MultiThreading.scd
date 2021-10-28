(
Server.supernova;
s.reboot;
)

s.plotTree;

~num = 1000;
~num = 1;

(
SynthDef(\testSine, {
	var sig = SinOsc.ar(\freq.kr(440) + \fm.ar(0).linlin(-1, 1, \low.kr(-100), \hi.kr(100)));
	Out.ar(\outFm.ir(0), sig); //fm output
	Out.ar(\out.ir(0), sig.dup * \scale.kr(1));
}).add;
)

//This will work! The node ordering of the b Groups is respected
//while they will be executed on different threads
(
a = ParGroup(s); //Multithread groups!
~num.do {
	b = Group(a); //Each Group will be spread to a different thread :)

	d = AlgaBus(s, 1);
	f = AlgaBus(s, 1);

	x = Synth(\testSine, [
		\scale, 0, //don't output to speakers
		\fm, f.busArg,
		\outFm, d.index,
		\low, rrand(-10.0, -100.0),
		\hi, rrand(10.0, 100.0),
		\freq, rrand(1.0, 100.0)
	],
	b, \addToHead);

	y = Synth(\testSine, [
		\scale, 1.0 / ~num,
		\fm, d.busArg,
		\outFm, f.index,
		\low, rrand(-10.0, -100.0),
		\hi, rrand(10.0, 100.0),
		\freq, rrand(100.0, 1000.0)
	],
	b, \addToTail);
}
)


//This will lead to dropouts!
//Test with ~num=1 to hear them
//Since b is a ParGroup too, its nodes will be alsospread across threads, and don't respect
//the ordering, thus the reading/writing of the busses will fuck up
(
a = ParGroup(s); //Multithread groups!
~num.do {
	b = ParGroup(a); //Each b group will spread its content to a different thread

	d = AlgaBus(s, 1);
	f = AlgaBus(s, 1);

	x = Synth(\testSine, [
		\scale, 0, //don't output to speakers
		\fm, f.busArg,
		\outFm, d.index,
		\low, rrand(-10.0, -100.0),
		\hi, rrand(10.0, 100.0),
		\freq, rrand(1.0, 100.0)
	],
	b, \addToHead);

	y = Synth(\testSine, [
		\scale, 1.0 / ~num,
		\fm, d.busArg,
		\outFm, f.index,
		\low, rrand(-10.0, -100.0),
		\hi, rrand(10.0, 100.0),
		\freq, rrand(100.0, 1000.0)
	],
	b, \addToTail);
}
)

//Normal behaviour: compare CPU usage with first case
(
a = Group(s); //Single thread groups!
~num.do {
	b = Group(a);

	d = AlgaBus(s, 1);
	f = AlgaBus(s, 1);

	x = Synth(\testSine, [
		\scale, 0, //don't output to speakers
		\fm, f.busArg,
		\outFm, d.index,
		\low, rrand(-10.0, -100.0),
		\hi, rrand(10.0, 100.0),
		\freq, rrand(1.0, 100.0)
	],
	b, \addToHead);

	y = Synth(\testSine, [
		\scale, 1.0 / ~num,
		\fm, d.busArg,
		\outFm, f.index,
		\low, rrand(-10.0, -100.0),
		\hi, rrand(10.0, 100.0),
		\freq, rrand(100.0, 1000.0)
	],
	b, \addToTail);
}
)
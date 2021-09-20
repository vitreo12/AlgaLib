(
Alga.boot({
	b = AN({ SinOsc.ar(100) });
	a = AN({ SinOsc.ar(\freq.ar(440).poll(0.5)) * 0.5 }, [\freq, AlgaArg(b, scale:[-10, 10])]).play(chans:2);
	c = AN({ LFNoise0.ar(1).poll(0.5) });
	a.mixFrom(c, \freq, scale:[100, 700], time:2);
	s.plotTree;
})
)

//Remove the initial mix entry by addressing the node of the AlgaArg directly
a.removeMix(\freq, b, time:2); //1

a.pt = 2; a.replace({ SinOsc.ar(\freq.ar(440)) * 0.2 });

//Not supported for now: a pain to figure out correctly
a.mixFrom(AlgaArg(c, scale:[100, 700]), \freq, time:2);

//This doesn't work either: re-doing mix after replace
a.mixFrom(c, \freq, scale:[100, 700], time:2);

a.reset(\freq, time:2);

b.clear(2)

a.reset(\freq, time:2);

a.clear(2)
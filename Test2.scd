p = ProxySpace.new.push(s.boot)

~a = { SinOsc.ar };
~b = { \in.ar(0) };

~b.play;

~a <>> ~b

~a.reshaping = \expanding;
~a = { SinOsc.ar([440, 442]) }

~b.reshaping = \expanding;
~b = { \in.ar([0, 0]) }


(
ANdef(\a, { SinOsc.ar });
ANdef(\b, { \in.ar(0) }).play;
ANdef(\a) => ANdef(\b);
)


ANdef.clear;


AlgaBlocksDict.blocksDict


ANdef(\a, { SinOsc.ar([440, 446]) });

ANdef(\b, { \in.ar([0, 0]) });


s.latency = 0.05

//Without s.bind, this sometimes works, sometimes doesn't...
(
//s.bind({
	Task({
		50.do({
			ANdef(\a, { SinOsc.ar([440, 446]) });
			ANdef(\b, { \in.ar([0, 0]) }).play;
			ANdef(\a) => ANdef(\b);

			0.1.wait;

			ANdef.clear;

			0.01.wait;
		})
	}).start;
//})
)
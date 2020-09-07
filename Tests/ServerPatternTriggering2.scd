(
SynthDef(\defaultP, { arg out=0, freqBuffersIndex=0, freqBuffers=0, amp=0.1, pan=0, time = 2;
	var z;
	var freq = BufRd.kr(1, freqBuffers, freqBuffersIndex, 0, 0);
	z = LPF.ar(
		Mix.new(VarSaw.ar(freq + [0, Rand(-0.4,0.0), Rand(0.0,0.4)], 0, 0.3, 0.3)),
		XLine.kr(Rand(4000,5000), Rand(2500,3200), 1)
	) * Linen.kr(Trig1.kr(Impulse.kr(0), time), 0.01, 0.7, 0.3, doneAction: Done.freeSelf);
	OffsetOut.ar(out, Pan2.ar(z, pan, amp));
}, [\ir]).add;
)

(
Routine.run({
	~numSynths = 10;

	~freqBuffers = Buffer.alloc(s, ~numSynths, 1);

	s.sync;

	~synths = ~numSynths.collect({ | index |
		Synth.newPaused(\defaultP, [\freqBuffersIndex, index, \freqBuffers, ~freqBuffers])
	});

	s.sync;

	~nodeIDs = Buffer.sendCollection(s, ~synths.collect(_.nodeID), 1);

	~dummy = { Silent.ar(1) }.play;

	s.sync;

	"Done".postln;
});
)

~freqBuffers.plot

(
c = { | bufnum, neutralID, freqBuffers |
	var trig = Dust.kr(1.5);  // here's the server-side trigger
	var num = BufFrames.kr(bufnum);
	var bufSeries1 = Dseries(0, 1, num);
	var bufSeries2 = Dseries(0, 1, num);
	var idSource = Demand.kr(trig, 0, Dbufrd(bufnum, bufSeries1, loop: 0));
	var stop = FreeSelfWhenDone.kr(idSource);
	var id = Select.kr((trig > 0), [neutralID, idSource]);

	Demand.kr(trig, 0, Dbufwr(Dseq([440, 880, 1000], inf), freqBuffers, bufSeries2, loop:0));

	//[trig, id].poll(trig);  // can remove, just for display
	Pause.kr(trig, id);
}.play(outbus: 1000, args: [bufnum: ~nodeIDs, freqBuffers: ~freqBuffers, neutralID: ~dummy.nodeID]);
)

~dummy.free; b.free;

// in case something broke, stop the others here
a.do(_.free); c.free;

s.plotTree
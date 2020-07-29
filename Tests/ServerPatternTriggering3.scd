(
SynthDef(\defaultP, { arg out=0, freq=440, amp=0.1, pan=0, time = 2;
	var z;
	z = LPF.ar(
		Mix.new(VarSaw.ar(freq + [0, Rand(-0.4,0.0), Rand(0.0,0.4)], 0, 0.3, 0.3)),
		XLine.kr(Rand(4000,5000), Rand(2500,3200), 1)
	) * Linen.kr(Trig1.kr(Impulse.kr(0), time), 0.01, 0.7, 0.3, doneAction: Done.freeSelf);
	OffsetOut.ar(out, Pan2.ar(z, pan, amp));
}, [\ir]).add;
)

(
~numSynths = 10;

~freqBusses = Bus.control(s, ~numSynths);

~synths = ~numSynths.collect({ | index |
	Synth.newPaused(\defaultP, [\freq, ~freqBusses.subBus(index, 1).asMap])
});

~nodeIDs = Buffer.sendCollection(s, ~synths.collect(_.nodeID), 1);

~dummy = { Silent.ar(1) }.play;
)

(
~scheduler = { | bufnum, neutralID, freqBusses |
	var trig = Dust.kr(2);  // here's the server-side trigger
	var num = BufFrames.kr(bufnum);
	var series = Demand.kr(trig, 0, Dseries(0, 1, num));
	var idSource = BufRd.kr(1, bufnum, series, 0, 0);
	var stop = FreeSelfWhenDone.kr(idSource);
	var id = Select.kr((trig > 0), [neutralID, idSource]);

	var freq = Demand.kr(trig, 0, Dseq([440, 880, 1200], inf));
	Out.kr((freqBusses + series), freq); //Shift bus index by series

	[trig, id].poll(trig);  // can remove, just for display
	Pause.kr(trig, id);
}.play(outbus: 1000, args: [bufnum: ~nodeIDs, neutralID: ~dummy.nodeID, freqBusses: ~freqBusses]);
)

~dummy.free; ~nodeIDs.free;

// in case something broke, stop the others here
~synths.do(_.free); ~scheduler.free;

s.plotTree
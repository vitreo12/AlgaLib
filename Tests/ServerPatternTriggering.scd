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
a = [-7, -1, 0, 2, 1, 5, 4, 8, 5, 6].collect { |degree|
	Synth.newPaused(\defaultP, [freq: (degree.degreeToKey(Scale.major) + 60).midicps]);
};

b = Buffer.sendCollection(s, a.collect(_.nodeID), 1);

~dummy = { Silent.ar(1) }.play;
)

(
c = { |bufnum, neutralID|
	var trig = Dust.kr(1.5);  // here's the server-side trigger
	var num = BufFrames.kr(bufnum);
	var idSource = Demand.kr(trig, 0, Dbufrd(bufnum, Dseries(0, 1, num), loop: 0));
	var stop = FreeSelfWhenDone.kr(idSource);

	var id = Select.kr((trig > 0), [neutralID, idSource]);
	[trig, id].poll(trig);  // can remove, just for display
	Pause.kr(trig, id);
}.play(outbus: 1000, args: [bufnum: b, neutralID: ~dummy.nodeID]);
)

~dummy.free; b.free;

// in case something broke, stop the others here
a.do(_.free); c.free;

s.plotTree
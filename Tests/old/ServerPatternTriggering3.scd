(
SynthDef(\sinePerc, {
	var durTrig = Trig.kr(Delta.kr(\trig.kr(0), 0.000000001), \dur.kr(2));

	//Either free going (mapping) or sample and hold according to trigger
	var freq = Select.kr(\freq_mapOrLatch.kr(1), [
		\freq.kr([440, 441]),
		Latch.kr(\freq.kr, durTrig)
	]);

	var x = SinOsc.ar(freq);

	var env = EnvGen.ar(Env.perc(releaseTime: \dur.kr), durTrig, doneAction: Done.pauseSelf);

	OffsetOut.ar(\out.ir(0), x * env * \amp.kr(0.1));
}).add;
)

(
fork {
	~numSynths = 1;

	//Create a control Bus with ~numSynths channels
	~freqBusses = Bus.control(s, ~numSynths * 2);
	~gateBusses = Bus.control(s, ~numSynths);

	//Create synths and connect \freq to the specific channels of the Bus
	~synths = ~numSynths.collect({ | index |
		Synth.newPaused(\sinePerc, [
			\freq, ~freqBusses.subBus(index * 2, 2).busArg, //need multichannel busArg trick in AlgaBus
			\trig, ~gateBusses.subBus(index, 1).busArg,
			\dur, 0.1
		])
	});

	~nodeIDs = Buffer.sendCollection(s, ~synths.collect(_.nodeID), 1);

	~dummy = { Silent.ar(1) }.play;

	s.sync;

	~scheduler = { | nodeIDs, neutralID, freqBusses, gateBusses |
		//var trig = Dust.kr(10) > 0;  // here's the server-side trigger
		var trig = Impulse.kr(100) > 0;
		var num = BufFrames.kr(nodeIDs); //~numSynths
		var series = Demand.kr(trig, 0, Dseries(0, 1, inf) % num);
		var idSource = BufRd.kr(1, nodeIDs, series, 1, 0);
		var id = Select.kr(trig, [neutralID, idSource]); // needed when trig is 0

		//var stop = FreeSelfWhenDone.kr(idSource);

		var freq = Demand.kr(trig, 0, [Dwhite(500, 1000), Dwhite(500, 1000)]);
		var gate = Demand.kr(trig, 0, Dwhite(0.0, 1.0)); //A random number, so that gate will be reset

		var freqBusIndex = freqBusses + (series * 2); //Shift bus index by series
		var gateBusIndex = gateBusses + series;

		Out.kr(freqBusIndex, freq); //multichannel
		Out.kr(gateBusIndex, gate);

		//[series, id].poll(trig);
		//freq.poll(trig);

		//Actual unpausing of the synth by ID
		Pause.kr(trig, id);

		//Output silence
		Silent.ar;

	}.play(args: [
		nodeIDs: ~nodeIDs,
		neutralID: ~dummy.nodeID,
		freqBusses: ~freqBusses,
		gateBusses: ~gateBusses
	]);
}
)

~dummy.free; ~nodeIDs.free;

// in case something broke, stop the others here
~synths.do(_.free); ~scheduler.free;

s.plotTree
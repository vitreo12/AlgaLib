(
SynthDef(\sinePerc, {
	var durTrig = Trig.kr(Delta.kr(\trig.kr(0), 0.000000001), \dur.kr(2));

	//Still latch it, in order to maintain same dur value as the trig throughout the execution!
	var dur = Latch.kr(\dur.kr, durTrig);

	//Either free going (mapping) or sample and hold according to trigger... This won't work for interpolation
	var freq = Select.kr(\freq_mapOrLatch.kr(1), [
		\freq.kr([440, 441]),
		Latch.kr(\freq.kr, durTrig)
	]);

	//Either free going (mapping) or sample and hold according to trigger... This won't work for interpolation
	var amp = Select.kr(\amp_mapOrLatch.kr(1), [
		\amp.kr(0.1),
		Latch.kr(\amp.kr, durTrig)
	]);

	var x = SinOsc.ar(freq);

	var env = EnvGen.ar(Env.perc(releaseTime: dur), durTrig, doneAction: Done.pauseSelf);

	//\dur.kr.poll(label:"durkr");
	//dur.poll(label:"dur");

	OffsetOut.ar(\out.ir(0), x * env * amp);
}).add;
)

(
fork {
	~numSynths = 10;

	//Create a control Bus with ~numSynths channels
	~freqBusses = Bus.control(s, ~numSynths * 2);
	~ampBusses = Bus.control(s, ~numSynths);
	~trigBusses = Bus.control(s, ~numSynths);
	~durBusses = Bus.control(s, ~numSynths);

	//Create synths and connect \freq to the specific channels of the Bus
	~synths = ~numSynths.collect({ | index |
		Synth.newPaused(\sinePerc, [
			\freq, ~freqBusses.subBus(index * 2, 2).busArg, //need multichannel busArg trick in AlgaBus
			\amp, ~ampBusses.subBus(index, 1).busArg,
			\trig, ~trigBusses.subBus(index, 1).busArg,
			\dur, ~durBusses.subBus(index, 1).busArg
		])
	});

	~nodeIDs = Buffer.sendCollection(s, ~synths.collect(_.nodeID), 1);

	~dummy = { Silent.ar(1) }.play;

	s.sync;

	~scheduler = { | nodeIDs, neutralID, freqBusses, ampBusses, trigBusses, durBusses |
		var trig = Dust.kr(5) > 0;  // here's the server-side trigger
		//var trig = Impulse.kr(10) > 0;
		var num = BufFrames.kr(nodeIDs); //~numSynths
		var series = Demand.kr(trig, 0, Dseries(0, 1, inf) % num);
		var idSource = BufRd.kr(1, nodeIDs, series, 1, 0);
		var id = Select.kr(trig, [neutralID, idSource]); // needed when trig is 0

		//var stop = FreeSelfWhenDone.kr(idSource);

		var freq = Demand.kr(trig, 0, [Dwhite(500, 1000), Dwhite(500, 1000)]); //multichannel
		var gate = Demand.kr(trig, 0, Dwhite(0.0, 1.0)); //A random number, so that gate will be reset
		var amp = Demand.kr(trig, 0, Dwhite(0.1, 0.3));
		var dur = Demand.kr(trig, 0, Dwhite(0.01, 0.4, inf));

		var freqBusIndex = freqBusses + (series * 2); //Shift bus index by series
		var trigBusIndex = trigBusses + series;
		var ampBusIndex  = ampBusses + series;
		var durBusIndex  = durBusses + series;

		Out.kr(freqBusIndex, freq); //multichannel
		Out.kr(ampBusIndex, amp);
		Out.kr(trigBusIndex, gate);
		Out.kr(durBusIndex, dur);

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
		ampBusses: ~ampBusses,
		trigBusses: ~trigBusses,
		durBusses: ~durBusses
	]);
}
)

~dummy.free; ~nodeIDs.free;

// in case something broke, stop the others here
~synths.do(_.free); ~scheduler.free;

s.plotTree
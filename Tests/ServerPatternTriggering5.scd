(
s.waitForBoot({
	SynthDef(\sinePerc, {
		var x = SinOsc.ar(\freq.kr([440, 441]));
		var env = EnvGen.ar(Env.perc(releaseTime: \dur.kr(1)), \trig.kr(0), doneAction: Done.pauseSelf);
		OffsetOut.ar(\out.ir(0), x * env * \amp.kr(0.5));
	}).add;

	//Writes to the correct busses... This allows for interplation to work correctly even when switching modes,
	//as the interpolation will correctly happen at the right busses
	SynthDef(\sinePerc_writer, {
		var trig = Trig.kr(Changed.kr(\trig.kr(0)), \dur.kr(2));

		//Always latch dur, in order to maintain same dur value as the trig throughout the execution!
		var dur = Latch.kr(\dur.kr, trig);

		//Either free going (mapping) or sample and hold according to trigger
		var freq = Select.kr(\freq_mapOrLatch.kr(1), [
			\freq.kr([440, 441]),
			Latch.kr(\freq.kr, trig)
		]);

		//Either free going (mapping) or sample and hold according to trigger
		var amp = Select.kr(\amp_mapOrLatch.kr(1), [
			\amp.kr(0.1),
			Latch.kr(\amp.kr, trig)
		]);

		Out.kr(\trigBusIndex.ir(-1), trig);
		Out.kr(\durBusIndex.ir(-1), dur);
		Out.kr(\freqBusIndex.ir(-1), freq);
		Out.kr(\ampBusIndex.ir(-1), amp);
	}).add;

	s.sync;

	~numSynths = 10;

	//These are the actual busses that modify parameters for each triggered synth
	~trigBusses = Bus.control(s, ~numSynths);
	~durBusses = Bus.control(s, ~numSynths);
	~freqBusses = Bus.control(s, ~numSynths * 2);
	~ampBusses = Bus.control(s, ~numSynths);

	//These are busses used to generate mapped or latched values
	~writerTrigBusses = Bus.control(s, ~numSynths);
	~writerDurBusses = Bus.control(s, ~numSynths);
	~writerFreqBusses = Bus.control(s, ~numSynths * 2);
	~writerAmpBusses = Bus.control(s, ~numSynths);

	~synthsGroup = Group(s);
	~writersGroup = Group.before(~synthsGroup);

	~synths = ~numSynths.collect({ | index |
		Synth.newPaused(\sinePerc, [
			\trig, ~trigBusses.subBus(index, 1).busArg,
			\dur, ~durBusses.subBus(index, 1).busArg,
			\freq, ~freqBusses.subBus(index * 2, 2).busArg, //need multichannel busArg trick in AlgaBus
			\amp, ~ampBusses.subBus(index, 1).busArg
		], ~synthsGroup)
	});

	~writers = ~numSynths.collect({ | index |
		Synth.newPaused(\sinePerc_writer, [
			\trig, ~writerTrigBusses.subBus(index, 1).busArg,
			\dur, ~writerDurBusses.subBus(index, 1).busArg,
			\freq, ~writerFreqBusses.subBus(index * 2, 2).busArg, //need multichannel busArg trick in AlgaBus
			\amp, ~writerAmpBusses.subBus(index, 1).busArg,

			\trigBusIndex, ~trigBusses.subBus(index, 1).index,
			\durBusIndex,  ~durBusses.subBus(index, 1).index,
			\freqBusIndex, ~freqBusses.subBus(index * 2, 2).index,
			\ampBusIndex, ~ampBusses.subBus(index, 1).index
		], ~writersGroup)
	});

	~synthsIDs = Buffer.sendCollection(s, ~synths.collect(_.nodeID), 1);
	~writersIDs = Buffer.sendCollection(s, ~writers.collect(_.nodeID), 1);

	//This is needed in order for 0 in select to choose the right entry from BufRd
	~neutralSynth = { Silent.ar(1) }.play;

	s.sync;

	~scheduler = { | synthsIDs, writersIDs, neutralID, freqBusses, ampBusses, trigBusses, durBusses |
		var trig = Dust.kr(10) > 0;  // here's the server-side trigger
		//var trig = Impulse.kr(100) > 0;

		var num = BufFrames.kr(synthsIDs); //~numSynths
		var series = Demand.kr(trig, 0, Dseries(0, 1, inf) % num);

		var synthIDSource = BufRd.kr(1, synthsIDs, series, 1, 0);
		var writerIDSource = BufRd.kr(1, writersIDs, series, 1, 0);

		//neutral ID is needed to select correct ID only on triggers
		var synthID = Select.kr(trig, [neutralID, synthIDSource]);
		var writerID = Select.kr(trig, [neutralID, writerIDSource]);

		//This won't be triggered anyway: it's an inf loop
		//var stop = FreeSelfWhenDone.kr(synthIDSource);

		var freq = Demand.kr(trig, 0, [Dwhite(500, 1000), Dwhite(500, 1000)]); //multichannel
		var gate = Demand.kr(trig, 0, Dwhite(0.0, 1.0)); //A random number, so that gate will be reset
		var amp = Demand.kr(trig, 0, Dwhite(0.1, 0.3));
		var dur = Demand.kr(trig, 0, Dwhite(0.01, 0.4, inf));

		var freqBusIndex = freqBusses + (series * 2); //Shift bus index by series
		var trigBusIndex = trigBusses + series;
		var ampBusIndex  = ampBusses + series;
		var durBusIndex  = durBusses + series;

		//Output to the writer
		Out.kr(freqBusIndex, freq);
		Out.kr(ampBusIndex, amp);
		Out.kr(trigBusIndex, gate);
		Out.kr(durBusIndex, dur);

		//Actual unpausing of the synth and writer by ID
		Pause.kr(trig, writerID);
		Pause.kr(trig, synthID);

		//Output silence
		Silent.ar;

	}.play(args: [
		synthsIDs: ~synthsIDs,
		writersIDs: ~writersIDs,
		neutralID: ~neutralSynth.nodeID,

		freqBusses: ~writerFreqBusses,
		ampBusses: ~writerAmpBusses,
		trigBusses: ~writerTrigBusses,
		durBusses: ~writerDurBusses
	]);
})
)
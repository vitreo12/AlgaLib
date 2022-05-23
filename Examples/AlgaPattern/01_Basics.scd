(
//Boot Alga
Alga.boot({
	//Declare a simple AlgaSynthDef.
	//Note that for it to be used in an AlgaPattern it must free itself.
	//Also, note the 'sampleAccurate' argument. This allows the AlgaSynthDef to use OffsetOut instead of Out
	//for sample accurate retriggering.
	AlgaSynthDef(\sine, {
		SinOsc.ar(\freq.kr(440)) * EnvPerc.ar
	}, sampleAccurate: true).add;

	//Wait for definition to be sent to server
	s.sync;

	//Create an AlgaPattern and play it.
	//Unlike Pbind, AlgaPatterns use an Event to describe the parameter -> value mapping.
	a = AlgaPattern((
		def: \sine,
		dur: 0.5
	)).play(chans: 2, scale: 0.7);
});
)

//Interpolate over the new Pseq. 'sched: 1' triggers it at the next beat
a.from(Pseq([220, 440, 880], inf), \freq, time: 3, sched: 1);

//Interpolate over the new Pwhite.
a.from(Pwhite(220, 880), \freq, time: 3, sched: 1);

//Interpolation can be sampled and held instead of being dynamic
a.from(Pseq([220, 440, 880], inf), \freq, sampleAndHold: true, time: 3, sched: 1);

//Bye bye
a.clear(time: 2);
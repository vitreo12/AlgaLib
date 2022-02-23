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
	)).play(chans: 2);
});
)

//Interpolate over the new Pseq. 'sched: 1' triggers it at the next beat
a.from(Pseq([220, 440, 880], inf), \freq, time: 3, sched: 1);

//Interpolate over the new Pwhite.
a.from(Pwhite(220, 880), \freq, time: 3, sched: 1);

//Interpolation can be sampled and held instead of being dynamic
a.from(Pseq([220, 440, 880], inf), \freq, sampleAndHold: true, time: 3, sched: 1);

//Change \dur does not trigger interpolation (yet), but it applies the changes directly
a.from(Pwhite(0.03, 0.5), \dur, sched: 1);

//Alternatively, the 'replaceDur' option can be used to trigger a 'replace' call, allowing for volume transitions
a.replaceDur = true;

//'The stopPatternBeforeReplace' option permits to either just stop the pattern before replacing (true),
//or to wait for the crossfade to finish before stopping the old one (false)
a.stopPatternBeforeReplace = false;

//Now it will 'replace'
a.from(0.25, \dur, time: 3, sched: 1);

//Bye bye
a.clear(time: 2);
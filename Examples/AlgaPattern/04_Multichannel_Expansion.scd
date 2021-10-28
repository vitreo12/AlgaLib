(
Alga.boot({
	//3 sine waves
	AlgaSynthDef(\sine3, {
		Mix(SinOsc.ar(\freq.kr([220, 440, 880]))) * EnvPerc.ar(release: 0.5)
	}, sampleAccurate:true).add;

	//1 saw wave
	AlgaSynthDef(\saw, {
		Saw.ar(\freq.kr(440)) * EnvPerc.ar(release: 0.5) * 0.7
	}, sampleAccurate:true).add;

	//Wait
	s.sync;

	//Note how multichannel is handled:
	// 1) \sine3 will receive the 3 elements Array and trigger a single instance.
	// 2) \saw will behave like a multichannel expanded pattern element, triggering 3 instances simultaneously.
	a = AlgaPattern((
		def: Pseq([\sine3, \saw], inf),
		dur: 0.5,
		freq: Pwhite([100, 200, 300], [400, 800, 1200])
	)).play(chans: 2)
});
)

//Change the multichannel mode. Now \saw will behave like an AlgaNode, only taking the first channel
a.useMultiChannelExpansion = false;
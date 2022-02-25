(
Alga.boot({
	//Simple sine
	AlgaSynthDef(\sine, {
		SinOsc.ar(\freq.kr(440)) * EnvPerc.ar(release: 0.5)
	}, sampleAccurate: true).add;

	//Simple saw
	AlgaSynthDef(\saw, {
		Saw.ar(\freq.kr(440)) * EnvPerc.ar(release: 0.5) * 0.5
	}, sampleAccurate: true).add;

	//Wait
	s.sync;

	//2 channels: tanh on the left and silence on the right
	b = AN({ [(\in.ar * 10).tanh * 0.5, Silent.ar] }).play;

	//2 channels: silence on the left and reverb on the right
	c = AN({ [Silent.ar, FreeVerb.ar(\in.ar, 0.8) * 10] }).play;

	//Declare the pattern using the 'out' parameter.
	//'out' allows to route the output of the pattern to other AlgaNodes, using the AlgaOut Class.
	a = AlgaPattern((
		def: \sine,
		dur: 0.5,
		freq: Pwhite(220, 440),
		out: Pseq([
			AlgaOut(
				node: b,
				param: \in
			),
			AlgaOut(
				node: c,
				param: \in
			)
		], inf)
	)).stopPatternBeforeReplace_(false);
});
)

//New connection
a.from(AlgaOut(b, \in), \out, time: 2, sched: 1);

//Using a saw instead
a.replace(\saw, time: 3, sched: 1);

//New connection: if no AlgaOut, it will connect to \in by default
a.from(Pseq([b, c], inf), \out, time: 2, sched: 1);

//Replacing a receiver
b.replace({ [(\in.ar * 20).clip(-1, 1) * 0.5, Silent.ar] }, time: 2);

//Bye bye
(
b.clear({ a.clear }, time: 2);
c.clear(time: 1);
)
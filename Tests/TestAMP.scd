(
//WIP:
//1) Multichannel
//2) Variable shapes

Alga.boot({
	AlgaSynthDef.new_inner_inner(\algaMonoPattern_control, {
		var env = AlgaDynamicIEnvGenBuf.kr(
			\envShape.ir(-1),
			\fadeTime.kr(0),
			maxSize: AlgaStartup.maxEnvPoints,
			release: \t_release.tr(0)
		);
		[\in.kr * env, env]
	}, makeFadeEnv: false, sampleAccurate: true, ignoreOutWarning: true, ignoreAmp: true).add;

	AlgaSynthDef.new_inner_inner(\algaMonoPattern_audio, {
		var env = AlgaDynamicIEnvGenBuf.ar(
			\envShape.ir(-1),
			\fadeTime.kr(0),
			maxSize: AlgaStartup.maxEnvPoints,
			release: \t_release.tr(0)
		);
		[\in.ar * env, env]
	}, makeFadeEnv: false, sampleAccurate: true, ignoreOutWarning: true, ignoreAmp: true).add;

	s.sync;

	a = AN({ SinOsc.ar(\freq.ar(440)) }).play;

	b = AMP((
		rate: \control,
		time: Pseq([0.05, 0.1, 1],inf),
		in: Pseq([220, 440, 880], inf),
		dur: 0.5
	));

	a <<.freq b;

	s.plotTree
}, algaServerOptions: AlgaServerOptions(supernova: false))
)

b <<.time 0

Server.killAll


s.scope

(
fork {
	SynthDef(\test, { Out.ar(0, SinOsc.ar(\freq.kr(440))) }).add;
	s.sync;
	Pmono(\test, \freq, Pseq([220, 440, 880], inf), \dur, 0.5).play
}
)
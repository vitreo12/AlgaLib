(
s.waitForBoot {
	SynthDef(\ienvgen, {
		AlgaDynamicIEnvGen.ar(
			//envelope: \envShape.ir(Env.newClear(0).algaAsArray),
			envelope: \envShape.kr(Env.newClear(AlgaStartup.maxEnvPoints).algaAsArray),
			fadeTime: \fadeTime.kr(0),
			updateEnv: \t_release.tr(0)
		);
	}).add;

	SynthDef(\interp, {
		var in = 0, env = 0, out = 0, outMultiplier = 0, outScale = 0, outs = 0;
		in = \in.ar;
		out = Select.ar(\indices.ir(0), in);
		outMultiplier = \outMultiplier.ir(1);
		outScale = out.algaLinCurve(
			\lowMin.ir(0),
			\lowMax.ir(1),
			\highMin.ir(0),
			\highMax.ir(1),
			\scaleCurve.ir(0)
		);
		out = Select.ar(\useScaling.ir(0), [out, outScale]);
		env = AlgaDynamicIEnvGen.ar(
			\envShape.kr(Env.newClear(AlgaStartup.maxEnvPoints).algaAsArray),
			\fadeTime.kr(0),
			\t_release.tr(0)
		);
		out = out * outMultiplier;
		out = out * env;
		outs = Array.newClear(2);
		outs[0] = out;
		outs[1] = env;
		outs;
	}).add;

	SynthDef(\norm, {
		Sanitize.ar(\in1.ar(1), \in2.ar(1))
	}).add;

	1.wait;

	1000.do { Synth(\ienvgen) };
	//100.do { Synth(\interp) };
	//100.do { Synth(\norm) };
}
)

//~ 12.9 / 13

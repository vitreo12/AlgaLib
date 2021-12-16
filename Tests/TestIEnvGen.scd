(
s.waitForBoot({
	SynthDef(\test, {
		var dynamicEnvGateReset = AlgaDynamicEnvGateReset.ar(\t_release.tr, \ft.kr(1));
		var phasor = dynamicEnvGateReset[0], endPoint = dynamicEnvGateReset[1];

		var env = \env.kr(Env.newClear(128).algaAsArray);
		var envGen = AlgaIEnvGen.ar(env, phasor * \envSum.kr(1), one_t_release);
		var invEnvGen = (1 - envGen) * endPoint;

		var finalEnv = Select.ar(ToggleFF.kr(one_t_release), [envGen, invEnvGen]);

		Out.ar(0, finalEnv)
	}).add;

	s.scope;
});
)

(
~env1 = Env([0, 1], 1);
a = Synth(\test, [\env, ~env1.algaConvertEnv, \envSum, ~env1.times.sum, \ft, 2])
)

(
~env2 = Env([0, 0.5, 1, 0, 1], [1, 3, 1, 1], [0, 5, -100, 1]);
a.set(\env, ~env2.algaConvertEnv, \envSum, ~env2.times.sum, \t_release, 1, \ft, 2);
)
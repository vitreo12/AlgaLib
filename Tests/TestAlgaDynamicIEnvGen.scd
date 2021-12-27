(
s.waitForBoot({
	SynthDef(\test_ar, {
		var env = \env.kr(Env.newClear(128).algaAsArray);
		var envGen = AlgaDynamicIEnvGen.ar(env, \ft.kr(1), \t_updateEnv.tr(0));
		Out.ar(0, envGen)
	}).add;

	SynthDef(\test_kr, {
		var env = \env.kr(Env.newClear(128).algaAsArray);
		var envGen = AlgaDynamicIEnvGen.kr(env, \ft.kr(1), \t_updateEnv.tr(0));
		Out.kr(0, envGen)
	}).add;

	s.scope;
});
)

//AR
(
~env1 = Env([0, 1, 0, 1, 0, 1], [1, 2, 0.5, 1, 0.25]);
a = Synth(\test_ar, [\env, ~env1.algaConvertEnv, \ft, 1])
)

(
~env2 = Env([0, 0.5, 1, 0, 1], [1, 3, 1, 1], [0, 5, -100, 1]);
a.set(\env, ~env2.algaConvertEnv, \t_updateEnv, 1, \ft, 3);
)

//KR
(
~env1 = Env([0, 1], 1);
a = Synth(\test_kr, [\env, ~env1.algaConvertEnv, \ft, 0])
)

(
~env2 = Env([0, 0.5, 1, 0, 1], [1, 3, 1, 1], [0, 5, -100, 1]);
a.set(\env, ~env2.algaConvertEnv, \t_updateEnv, 1, \ft, 3);
)

Env([0, 1, 0, 1, 0, 1], [1, 2, 0.5, 1, 0.25]).plot
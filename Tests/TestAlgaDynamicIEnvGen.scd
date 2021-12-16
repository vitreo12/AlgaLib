(
s.waitForBoot({
	SynthDef(\test, {
		var env = \env.kr(Env.newClear(128).algaAsArray);
		var envGen = AlgaDynamicIEnvGen.ar(/* ... */);
		Out.ar(0, envGen)
	}).add;

	s.scope;
});
)

(
~env1 = Env([0, 1], 1);
a = Synth(\test, [\env, ~env1.algaConvertEnv, \envSum, ~env1.times.sum, \ft, 2])
)

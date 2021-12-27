(
s.waitForBoot({
	SynthDef(\testIEnvGen, {
		//Make it max 128 points.
		//This must be set in the SynthDef as IEnvGen needs to be instantiated with a max of num of inputs!
		var env = \env.kr(Env.newClear(128).algaAsArray);
		Out.ar(\out.ir(0), AlgaIEnvGen.ar(env, (LFSaw.ar(1).range(0, 1) * \envTimesSum.kr(1)), updateEnv: \updateEnv.tr(0)))
	}).add;

	s.sync;

	s.scope;

	~enva = Env([0, 1], 1);
	a = Synth(\testIEnvGen, [\env, ~enva.algaConvertEnv, \envTimesSum, ~enva.times.sum]);

	~envb = Env([0, 0.5, 1, 0, 1], [1, 3, 1, 1], [0, 5, -100, 1]);
	b = Synth(\testIEnvGen, [\out, 1, \env, ~envb.algaConvertEnv, \envTimesSum, ~envb.times.sum]);
})
)

(
//Modify Env on the fly!
~envb = Env([0, 1], 1);
b.set(\env, ~envb.algaConvertEnv, \envTimesSum, ~envb.times.sum, \updateEnv, 1);
)
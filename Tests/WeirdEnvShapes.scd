(
s.waitForBoot({
	~enva = Env([0, 0.5, 1], [1, 3], [0, 5]);
	~envb = Env([0, 1, 0, 1], [1, 2, 1]);

	s.scope;

	a = { AlgaIEnvGen.ar(~enva, AlgaDynamicEnvGate.ar(\t_release.tr(0), \ft.kr(5)) * ~enva.times.sum).poll }.play
})
)

~enva.times

(
a.set(\t_release, 1);
b = { [0, AlgaIEnvGen.ar(~envb, AlgaDynamicEnvGate.ar(\t_release.tr(0), \ft.kr(5)) * ~envb.times.sum)] }.play
)

(
~enva.plot;
~envb.plot;
)

//Sam as default behavior
(
s.waitForBoot({
	~enva = Env([0, 1], 1, \sin);

	s.scope;

	a = { AlgaIEnvGen.ar(~enva, AlgaDynamicEnvGate.kr(\t_release.tr(0), \ft.kr(5)).poll * ~enva.times.sum).poll }.play
})
)

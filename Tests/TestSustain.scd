//1
(
Alga.boot({
	a = AP((
		def: { SinOsc.ar * EnvGen.ar(Env.adsr, \gate.kr) }, //user can use \gate
		sustain: Pseq([1, 2], inf), //reserved keyword
		dur: 3
	)).play
})
)

//2
(
Alga.boot({
	a = AP((
		def: { SinOsc.ar },
		amp: AlgaTemp({ EnvGen.ar(Env.adsr, \gate.kr) }, sampleAccurate: true),
		sustain: Pseq([1, 2], inf),
		dur: 3
	)).play
})
)
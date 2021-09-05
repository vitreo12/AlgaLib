(
Alga.boot({
	//AN({SinOsc.ar}).play;

	a = AlgaPattern((
		def: Pseq([
			{ SinOsc.ar * EnvGen.ar(Env.perc, doneAction: 2) },
			{ Saw.ar * 0.5 * EnvGen.ar(Env.perc, doneAction: 2) }
		], inf
		),
		dur: 0.5
	)).play

})
)

Server.killAll

SynthDescLib.global.at(\alga_1007)

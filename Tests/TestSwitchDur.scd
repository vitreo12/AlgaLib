(
Alga.boot({
	a = AP((
		def: { SinOsc.ar * EnvPerc.ar(release: 0.1) * 0.5 },
		dur: inf
	)).play(chans:2);
})
)

a.step

a.advance

//Going from manual to automatic dur !!!!
//And for APP too

Server.killAll
(
Alga.boot({
	a = AP((
		def: { SinOsc.ar(\freq.kr(440).poll(1)) * EnvPerc.ar },
		freq: Pif( Pfunc { 0.5.coin },
			AT({ LFNoise0.kr(10) }, scale: [440, 880]),
			Pseq([ AT { DC.kr(220) }, AT { DC.kr(440) }], inf)
		)
	)).play(chans:2)
})
)

(
Alga.boot({
	a = AP((
		def: Pseq([
			{ SinOsc.ar(\freq.kr(440).poll(1)) * EnvPerc.ar },
			{ Saw.ar(\freq.kr(440).poll(1)) * EnvPerc.ar * 0.5 }
		], inf),
		freq: Pif( Pfunc { 0.5.coin },
			AT({ LFNoise0.kr(10) }, scale: [440, 880]),
			Pseq([ AT { DC.kr(220) }, AT { DC.kr(440) }], inf)
		)
	)).play(chans:2)
})
)

(
Alga.boot({
	AlgaSynthDef(\test, { SinOsc.ar(\freq.kr(440).poll(1)) * EnvPerc.ar * 0.5 }).add;
	s.sync;
	a = AP((
		def: Pif(Pfunc( { 0.5.coin }.lf ), //.lf is needed or it's interpreted as a UGen func
			{ Saw.ar(\freq.kr(440).poll(1)) * EnvPerc.ar },
			Pfunc( { \test }.lf ) //This is not actually parsed, but since it's using same parameters it works
		),
		freq: Pif( Pfunc { 0.5.coin },
			AT({ LFNoise0.kr(10) }, scale: [440, 880]),
			Pseq([ AT { DC.kr(220) }, AT { DC.kr(440) }], inf)
		)
	)).play(chans:2)
})
)
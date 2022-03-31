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
	AlgaSynthDef(\test, { SinOsc.ar(\freq.kr(440).poll(1)) * EnvPerc.ar}).add;
	s.sync;
	a = AP((
		def: Pif(Pfunc( { 0.5.coin }.lf ), //.lf is needed or it's interpreted as a UGen func
			{ Saw.ar(\freq.kr(440).poll(1)) * EnvPerc.ar * 0.5 },
			Pfunc( { \test }.lf ) //This is not actually parsed, but since it's using same parameters it works
		),
		freq: Pif( Pfunc { 0.5.coin },
			AT({ LFNoise0.kr(10) }, scale: [440, 880]),
			Pseq([ AT { DC.kr(220) }, AT { DC.kr(440) }], inf)
		)
	)).play(chans:2)
})
)

(
Alga.boot({
	AlgaSynthDef(\test, { SinOsc.ar(\freq.kr(440).poll(1)) * EnvPerc.ar}).add;
	AlgaSynthDef(\test2, { Saw.ar(\freq.kr(440).poll(1)) * EnvPerc.ar * 0.5 }).add;
	s.sync;
	a = AP((
		//Trick to force the parameters / rate / num channels of another function (\test in this case),
		//even if it's not going to actually be used. This is quite an ugly workaround, but effective!
		def: Pfunc( { \test2 }.lf ) <> \test,
		freq: Pif( Pfunc { 0.5.coin },
			AT({ LFNoise0.kr(10) }, scale: [440, 880]),
			Pseq([ AT { DC.kr(220) }, AT { DC.kr(440) }], inf)
		)
	)).play(chans:2)
})
)
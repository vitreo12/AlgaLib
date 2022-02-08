/*
IMPLEMENTATION:
Each norm has its own group, to which AlgaKeys are added if referring to that norm

1) direct AlgaKeys (e.g. freq: AlgaKey(\freq1))
2) AlgaTemps with AlgaKeys (e.g. freq: AlgaTemp((def: { \in.kr }, in: AlgaKey(\freq1)))
3) AlgaKeys referring to other AlgaKeys (e.g. anotherFreq: AlgaKey(\freq) (where \freq is another AlgaKey))

All active params must be checked while interpolation in order to be assigned to the correct Group that they need to read from.

INTERP:
    freq1

NORM:
    freq1:
        freq1Norm

        freq:
            AlgaKey
            AlgaTemp
            freqInterp
            freqNorm

            anotherFreq (reading from freq):
                AlgaKey
                AlgaTemp
                anotherFreqInterp
                anotherFreqNorm

        amp:
            AlgaKey
            AlgaTemp
            ampInterp
            ampNorm
*/

//Ex.0
(
Alga.boot({
	a = AP((
		def: { SinOsc.ar(\freq1.kr.poll(label:"freq1") + \freq2.kr.poll(label:"freq2")) * EnvPerc.ar },
		dur: 1,
		freq1: 400,
		freq2: AlgaKey(\freq1),
	)).play
});
)

//Interpolation is maintained: AlgaKey reads directly from the AlgaBus
a.from(300, \freq1, time:3);

//Ex.1
//Gotta do logic on the server to keep interpolators
(
Alga.boot({
	a = AP((
		def: { var freq2 = \freq2.kr; SinOsc.ar(\freq1.kr) * EnvPerc.ar },
		dur: 1,
		freq1: 400,
		freq2: Pseq([100, 1000], inf),
		amp: AlgaTemp((
			def: { SinOsc.ar(\freq.kr.poll) },
			freq: AlgaTemp((
				def: { \freq1.kr + \freq2.kr },
				freq1: AlgaKey(\freq1),
				freq2: AlgaKey(\freq2)
			))
		)),
	)).play
});
)

//Interpolation is maintained: AlgaKey reads directly from the AlgaBus
a.from(800, \freq1, time:3)

//Ex.2
//Complex logic: using AlgaTemps for complex interactions (must be on the server)
(
Alga.boot({
	a = AP((
		def: { var freq2 = \freq2.kr; SinOsc.ar(\freq1.kr) * EnvPerc.ar },
		dur: 1,
		freq1: 400,
		freq2: Pseq([100, 1000], inf),
		amp: AlgaTemp((
			def: {
				Select.ar(\freq.kr <= 500, [
					SinOsc.ar(100),
					Saw.ar(100)
				])
			},
			freq: AlgaTemp((
				def: { \freq1.kr + \freq2.kr },
				freq1: AlgaKey(\freq1),
				freq2: AlgaKey(\freq2)
			))
		)),
	)).play
});
)

//Ex.3
//Precedence: will look into AlgaTemp first
(
Alga.boot({
	a = AP((
		def: { var freq2 = \freq2.kr;  SinOsc.ar(\freq1.kr) * EnvPerc.ar },
		dur: 1,
		freq1: 400,
		freq2: Pseq([100, 1000], inf),
		amp: AlgaTemp((
			def: { SinOsc.ar(\freq.kr * \freq1.kr) },
			freq1: 800, //Same name as AlgaPattern's
			freq: AlgaTemp((
				def: { \freq1.kr + \freq2.kr },
				freq1: AlgaKey(\freq1), //Will use AlgaTemp's
				freq2: AlgaKey(\freq2)  //Will use AlgaPattern's
			))
		)),
	)).play
});
)
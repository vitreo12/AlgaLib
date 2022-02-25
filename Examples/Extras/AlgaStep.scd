(
Alga.boot({
    a = AlgaPattern((
        def: { SinOsc.ar(\freq.kr(440)) * EnvPerc.ar },
        dur: 1
	)).play(chans: 2)
})
)

//One trigger from now
a.from(220, \freq, sched: AlgaStep(1));

//Two triggers from now
a.replace({ Saw.ar(\freq.kr(440)) * EnvPerc.ar * 0.5}, sched: AlgaStep(2));

//Next trigger
a.from(440, \freq, sched: AlgaStep(0));
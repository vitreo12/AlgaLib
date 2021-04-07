//Simple feedback example
(
Alga.boot({
	a = AlgaNode({ SinOsc.ar(\freq.kr(220) + \fm.ar(0)) }, connectionTime:5, playTime:2);
	b = AlgaNode({ SinOsc.ar(\freq.kr(134) + \fm.ar(0)) }, connectionTime:3);
	a.from(b, \fm, scale:[-100, 100]);
	b.from(a, \fm, scale:[-50, 50]); //Feedback connection!
	a.play(chans:2); //play stereo
})
)

(
//Randomly change a's fm scale every 3 seconds
fork {
	loop {
		a.from(b, \fm, scale:[rrand(-200, -30), rrand(10, 250)]);
		3.wait;
	}
};

//Randomly change b's fm scale every 2 seconds
fork {
	loop {
		b.from(a, \fm, scale:[rrand(-150, -10), rrand(3, 30)]);
		2.wait;
	}
}
)

//Change root frequencies
(
b <<.freq 25;
a <<.freq 103;
)

//Replace b's definition with a sawtooth wave.
//Make sure to have the same parameters so they keep the mappings
b.replace({ Saw.ar(\freq.kr + \fm.ar) })

//The errors on console are the routines
//that are still trying to make connections
(
b.clear(2);
a.clear(2);
)
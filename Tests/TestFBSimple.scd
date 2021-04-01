(
Alga.boot({
	b = AlgaNode({ SinOsc.ar(\freq.kr(134) + \fm.ar(0)) }, connectionTime:3);
	a = AlgaNode({ SinOsc.ar(\freq.kr(220) + \fm.ar(0)) }, connectionTime:5, playTime:2);
	a.from(b, \fm, scale:[-100, 100]);
	b.from(a, \fm, scale:[-50, 50]);
	a.play(chans:[0, 0]);
})
)

s.scope

(
//Randomly change a's fm scale every 3 seconds
fork {
	loop {
		a.from(b, \fm, scale:[rrand(-10, -50), rrand(10, 100)]);
		3.wait;
	}
};

//Randomly change b's fm scale every 2 seconds
fork {
	loop {
		b.from(a, \fm, scale:[rrand(-50, -100), rrand(60, 200)]);
		2.wait;
	}
}
)

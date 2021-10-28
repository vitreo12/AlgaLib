(
Alga.boot({
	a = AN({ SinOsc.ar(\f.kr(440)) }).play;
	0.5.wait;
	a <<.f 330;
	0.5.wait;
	a.replace({ Saw.ar(\f.kr(600).poll) }, reset:false);
})
)

(
Alga.boot({
	a = AN({ SinOsc.ar(\f.kr(440)) }).play;
	0.5.wait;
	a <<.f 330;
	0.5.wait;
	a.replace({ Saw.ar(\f.kr(600).poll) }, reset:true);
})
)

(
Alga.boot({
	a = AN({ SinOsc.ar(\f.kr(440)) }).play;
	0.5.wait;
	a <<.f 330;
	0.5.wait;
	a.replace({ Saw.ar(\f.kr(600).poll) }, reset:[\f]);
})
)
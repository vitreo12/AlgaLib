(
Alga.boot({
    a = AlgaNode.debug({ SinOsc.ar(\freq.kr(220) + \fm.ar(0)) }, interpTime: 5, playTime:2, name:\a);
    b = AlgaNode.debug({ SinOsc.ar(\freq.kr(134) + \fm.ar(0)) }, interpTime: 3, name:\b);
    a.from(b, \fm, scale:[-100, 100]);
    b.from(a, \fm, scale:[-50, 50]); //Feedback connection!
    a.play(chans:2); //play stereo
},
//algaServerOptions: AlgaServerOptions(supernova: true)
)
)

(
//Randomly change a's fm scale every 3 seconds
fork {
    loop {
        a.from(b, \fm, scale:[100, 200]);
        0.5.wait;
    }
};

//Randomly change b's fm scale every 2 seconds
fork {
    loop {
        b.from(a, \fm, scale:[-40, 70]);
        0.5.wait;
    }
}
)

//Note how it clicks!
//It's because b.from... will invert them every time!
a.from(b, \fm, scale:[100, 20]);
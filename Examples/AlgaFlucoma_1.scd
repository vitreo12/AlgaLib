(
Alga.boot({
	//Sine oscillator with frequency modulation
	a = AN({ SinOsc.ar(\freq.kr(10) + \fm.ar(0)) }).play(chans:2);

	//Sine oscillator
	b = AN({ SinOsc.ar(\freq.kr(440)) });

	//Pitch analyser
	c = AN({ FluidPitch.kr(\in.ar) });

	//Patch first oscillator into the pitch analyser.
	a >> c;

	//Patch the pitch analyser into the \freq input of the second oscillator
	c >>.freq b;

	//Create feedback: patch b's output to a's \fm input, mapping -1 / 1 to -200 / 300
	a.from(b, \fm, scale:[-200, 300], time:0.5);
});
)

//Try with different scalings (change the values)
a.from(b, \fm, scale:[-1432, 679], time:3);

//Try new base frequencies (change the value)
a.from(100, \freq, time:2);

//Bye bye
a.clear(time:3);
//This example requires the FluCoMa tools to be installed from::
//https://www.flucoma.org/download/

(
Alga.boot({
	//Sine oscillator with frequency modulation
	a = AN({ SinOsc.ar(\freq.kr(10) + \fm.ar(0)) }).play(chans:2);

	//Sine oscillator
	b = AN({ SinOsc.ar(\freq.kr(440)) });

	//Loudness analyser
	c = AN({ FluidLoudness.kr(\in.ar).dbamp });

	//Patch first oscillator into the pitch analyser.
	a >> c;

	//Patch the loudness analyser into the \freq input of the second oscillator
	b.from(c, \freq, scale:[0, 1, 10, 2000]);

	//Create feedback: patch b's output to a's \fm input, mapping -1 / 1 to -200 / 300
	a.from(b, \fm, scale:[-200, 300], time:0.5);
});
)

//Change mapping for the loudness (change the values)
b.from(c, \freq, scale:[0, 1, -123, 210], time:6);

//Try with different scalings (change the values)
a.from(b, \fm, scale:[-1432, 679], time:3);

//Try new base frequencies (change the value)
a.from(100, \freq, time:2);

//Bye bye
(
a.clear({
	b.clear;
	c.clear;
}, time:3)
);
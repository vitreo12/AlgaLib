(
AlgaProxySpace.boot({
	//Define a Moog LPF and play it to speakers
	~filter = { LeakDC.ar(BMoog.ar(\in.ar, \freq.kr(440), \q.kr(0.5))) };
	~filter.playSafety = \tanh; //\tanh safety when out of bounds
	~filter.play(chans: 2, scale: 0.7);
	~filter.it = 5; //5 secs of interp time

	//Define a Sawtooth
	~saw = { Saw.ar(\freq.kr(50)) };
	~saw.it = 3; //3 secs of interp time

	//Patch the Sawtooth into the filter
	~saw >> ~filter;
}, algaServerOptions: AlgaServerOptions(blockSize: 64)); //This FB sounds better at 64 samples
)

//Introduce feedback in the filter
~filter.mixFrom(~filter, scale: 0.5)

//Modulate the filter's frequency with some smooth noise in the 50 - 2000 hz range
~filter <<.freq { LFNoise1.kr(LFNoise0.kr(3).range(1, 10)).range(50, 2000) }

//Modulate the saw's frequency with some stepped noise in the 0.1 - 30 hz range
~saw <<.freq { LFNoise0.kr(LFNoise1.kr(2).range(1, 20)).range(0.1, 30) }

////////////////////////////////////////////////////////
// Let's introduce distortion in the feedback loop... //
////////////////////////////////////////////////////////

//First, remove the old feedback
~filter.removeMix(oldSender: ~filter);

//Define a new node with distortion
~distortion = { tanh(\in.ar * \amt.kr(1)) };
~distortion.it = 3; //3 secs of interp time

//Patch filter to distortion and distortion to filter
~filter >> ~distortion;
~filter.mixFrom(~distortion, scale: 0.5);

//Now let's modify the distortion amount
~distortion <<.amt 5

//We can change the distortion too
~distortion = { (\in.ar * \amt.kr(1)).clip(-1, 1) };
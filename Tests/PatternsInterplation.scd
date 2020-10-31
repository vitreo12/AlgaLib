(
Server.supernova;
s.options.useSystemClock = false;
s.reboot;
)

(
SynthDef(\imp, {
	Out.ar(\out.ir(0), EnvGen.ar(Env([1, 0], SampleDur.ir * 10), doneAction:2).dup);
}).add;

//Freq setter
SynthDef(\sinePerc_freq, {
	var env = EnvGen.ar(Env.perc, doneAction:2);
	Bus.free(\out.ir(-1));
	Out.kr(\out.ir(-1), \in.kr(440));
}).add;

SynthDef(\sinePerc_freq_noise, {
	var env = EnvGen.ar(Env.perc, doneAction:2);
	Bus.free(\out.ir(-1));
	Out.kr(\out.ir(-1), LFNoise0.kr(3).range(200, 500));
}).add;

SynthDef(\sinePerc, {
	var x = SinOsc.ar(\freq.kr(440));
	var env = EnvGen.ar(Env.perc, doneAction:2);
	OffsetOut.ar(\out.ir(0), x.dup * env * \amp.kr(1));
}).add;
)

(
//This doesn't work with arrays [440, 660] yet (needs to trigger 2 synths). Check eventTypes -> note in Event.sc
//This doesn't work with scheduling, as it's not calling ~schedBundleArray (as \note does).
//All of this will be possible by turningall the below code into bundle messages, and send those.
Event.addEventType(\algaNote, { | server |
	//Create a temp AlgaNode, make connection between ~param (that comes from Pbind) and the AlgaNodes...
	//Perhaps, simply, the AlgaNode stuff, to make interp with other AlgaNodes, can be dynamically set.
	//No need to create it for every triggered synth, but just when new connections will be set with other AlgaNodes!

	var bundle = server.makeBundle(false, {
		var bus = Bus.control(s, 1);
		var setter = Synth(\sinePerc_freq, [\out, bus.index, \in, ~freq]);
		//var setter = Synth(\sinePerc_freq_noise, [\out, bus.index]);
		var sine = Synth.after(setter, \sinePerc, [\freq, bus.asMap, \amp, ~amp]);

		//Free the bus on Synth's end
		var instantiated = OSCFunc.newMatching({ | msg |
			if(bus != nil, {
				bus.free;
			});
		}, '/n_end', server.addr, argTemplate:[sine.nodeID]).oneShot;
	});

	//Syncs to the thisThread.clock. It will be Pbind's clock
	~schedBundleArray.(
		~lag,
		~timingOffset,
		server,
		bundle,
		~latency
	);
});
)

(
~params = IdentityDictionary();
~params[\freq] = Pwhite(440, 880).asStream;

Pbind(
	\type, \algaNote,
	\freq, Pfuncn({ ~params[\freq].next }, inf);,
	\dur, 0.05,
	\amp, 0.01
).play;

~interp = { | param, what, ft = 1 |
	var blend = Pseg([0, 1, 1], [ft, inf], \lin);
	~params[param] = (~params[param].blend(what, blend)).asStream;
};
)


~interp.(\freq, Pseq([220, 880], inf), 4);
~interp.(\freq, Pwhite(220, 1000, inf), 3);



//To trace lang's incoming OSC messages
OSCFunc.trace;

//To trace server's incoming OSC messages
s.dumpOSC(1);




(
~dur = 0.4;

d = Pbindef(
	\d,
	\type, \note,
	\instrument, \imp,
	\amp, 0.8,
	\dur, Pfuncn({ ~dur.next }, inf);
).play
)

(
~ft = 10;
~blend = Pseg([0, 1, 1], [~ft, inf], \lin);
//~what = Pseq([0.01, 0.1], inf);
~what = 0.05;
~dur = (~dur.blend(~what, ~blend)).asStream;
fork {
	~ft.wait;
	"done".postln;
}
)

(
~ft = 1;
~blend = Pseg([0, 1, 1], [~ft, inf], \lin);
~what = 0.5;
~dur = (~dur.blend(~what, ~blend)).asStream;
fork {
	~ft.wait;
	"done".postln;
}
)



(
~params = IdentityDictionary();
~params[\dur] = 0.4;
~params[\freq] = 440;

~sineOut = Bus.control(s);
{Out.kr(~sineOut, SinOsc.kr(0.1).range(0.03, 0.3))}.play;

~sineOut2 = Bus.control(s);
{Out.kr(~sineOut2, SinOsc.kr(1).range(400, 1000))}.play;

d = Pbindef(
	\d,
	\instrument, \sinePerc,
	\amp, 0.2,
	\freq, Pfuncn({ ~params[\freq].next }, inf),
	\dur, Pfuncn({ ~params[\dur].next }, inf)
).quant_(0).play;


~interp = { | param, what, ft = 1 |
	var blend = Pseg([0, 1, 1], [ft, inf], \lin);
	~params[param] = (~params[param].blend(what, blend)).asStream;
}
)

//It works!!!
~interp.(\freq, Pseq([220, 440, 880], inf), 4);
~interp.(\freq, Pwhite(220, 880, inf), 3);
~interp.(\freq, Pseq([220, [220, 440, 660], 880], inf), 4); //Even with multiples!!

~interp.(\dur, Pkr(~sineOut), 3);
~interp.(\dur, 0.3, 3);

//What about audio? then patterns again?

d.stop
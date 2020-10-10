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

SynthDef(\sinePerc, {
	var x = SinOsc.ar(\freq.kr(440));
	var env = EnvGen.ar(Env.perc, doneAction:2);
	OffsetOut.ar(\out.ir(0), x.dup * env * \amp.kr(1));
}).add;
)

(
Event.addEventType(\algaNote, { | server |
	var bus, setter, sine;

	//Create a temp AlgaNode, make connection between ~param (that comes from Pbind) and the AlgaNodes...
	//Perhaps, simply, the AlgaNode stuff, to make interp with other AlgaNodes, can be dynamically set.
	//No need to create it for every triggered synth, but just when new connections will be set with other AlgaNodes!
	server.bind({
		bus = Bus.control(s, 1);
		setter = Synth(\sinePerc_freq, [\out, bus.index, \in, ~freq]);
		sine = Synth.after(setter, \sinePerc, [\freq, bus.asMap, \amp, ~amp]);
	});

	//Free the bus after some time
	fork {
		(~dur + 1).wait;
		if(bus != nil, {
			bus.free;
		});
	}
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
	var blend = Pseg([0, 1, 1], [ft, inf], \lin, 1);
	~params[param] = (~params[param].blend(what, blend)).asStream;
};
)


~interp.(\freq, Pseq([220, 880], inf), 4);
~interp.(\freq, Pwhite(220, 1000, inf), 3);







(
~dur = 0.4;

d = Pbindef(
	\d,
	\type, \note,
	\instrument, \imp,
	\amp, 0.8,
	\dur, Pfuncn({ ~dur.next }, inf);
).quant_(0).play
)

(
~blend = Pseg([0, 1, 1], [5, inf], \lin, 1);
~what = Pseq([0.01, 0.1], inf);
~dur = (~dur.blend(~what, ~blend)).asStream
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
	var blend = Pseg([0, 1, 1], [ft, inf], \lin, 1);
	~params[param] = (~params[param].blend(what, blend)).asStream;
}
)

//It works!!!
~interp.(\freq, Pseq([220, 440, 880], inf), 4);
~interp.(\freq, Pwhite(220, 880, inf), 3);
~interp.(\freq, ~sineOut2, 2);

Pbus

~interp.(\dur, Pkr(~sineOut), 3);
~interp.(\dur, 0.3, 3);

//What about audio? then patterns again?

d.stop
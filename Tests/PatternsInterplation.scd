(
SynthDef(\imp, {
	Out.ar(\out.ir(0), EnvGen.ar(Env([1, 0], SampleDur.ir * 10), doneAction:2).dup);
}).add;

SynthDef(\sinePerc, {
	var x = SinOsc.ar(\freq.kr(440));
	var env = EnvGen.ar(Env.perc, doneAction:2);
	OffsetOut.ar(\out.ir(0), x * env * \amp.kr(1));
}).add;
)


(
~dur = 0.4;

d = Pbindef(
	\d,
	\instrument, \imp,
	\amp, 0.8,
	\dur, Pfuncn({ ~dur.next }, inf);
).quant_(0).play
)

(
~blend = Pseg([0, 1, 1], [10, inf], \lin, 1);
~what = Pseq([0.1, 0.2], inf);
~dur = (~dur.blend(~what, ~blend)).asStream
)



(
~params = IdentityDictionary();
~params[\dur] = 0.4;
~params[\freq] = 440;

~sineOut = Bus.control(s);
{Out.kr(~sineOut, SinOsc.kr(0.1).range(0.03, 0.3))}.play;

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

~interp.(\dur, Pkr(~sineOut), 3);
~interp.(\dur, 0.3, 3);

d.stop
(
~dur = 1;
p = Pbind(
	\degree, Pwhite(0, 7, inf),
	\dur, Pfuncn({ ~dur.next }, inf)
).playRescheduling(quant:1)
)

p.reschedule(2);

p.algaSchedOnce(1, { "ye".postln; ~dur = 0.3 })

p.algaSchedOnce(0.1, { "ye".postln; ~dur = 0.5 })
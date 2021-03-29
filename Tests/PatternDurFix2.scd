(
~dur = 1;
p = Pbind(
	\degree, Pwhite(0, 7, inf),
	\dur, Pfuncn({ var a = ~dur.next; a.postln; a }, inf)
).playRescheduling(quant:1)
)

p.reschedule(2);

p.algaSchedAtQuantOnce(1, { "ye".postln; ~dur = 0.5 })

p.algaSchedOnce(0.1, { "ye".postln; ~dur = 0.5 })
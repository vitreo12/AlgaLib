(
a = TempoClock.default;
a.algaTempoClockSchedAtQuant(1, {"me first".postln});
a.algaSchedAtQuantOnceWithTopPriority(1, {"no, me first".postln});
a.algaSchedAtQuantOnceWithTopPriority(1, {"nono, me first".postln});
a.algaSchedAtQuantOnceWithTopPriority(2, {"mhmh".postln});
a.algaSchedAtQuantOnceWithTopPriority(1, {"yay".postln});
a.queue.postln;
)

(
a = TempoClock.default;
a.algaSched(1, {"me first".postln});
a.algaTempoClockSchedWithTopPriority(1, {"no, me first".postln});
a.algaTempoClockSchedWithTopPriority(1, {"nono, me first".postln});
a.algaTempoClockSchedWithTopPriority(2, {"mhmh".postln});
a.algaTempoClockSchedWithTopPriority(5, 5);
a.queue.postln;
)

(
a = TempoClock.default;
a.sched(1, {"me first".postln});
a.sched(1, {"no, me first".postln});
a.sched(1, {"nono, me first".postln});
a.sched(2, {"mhmh".postln});
a.sched(5, 5);
a.queue.postln;
)
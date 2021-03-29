(
a = TempoClock.default;
a.algaTempoClockSchedAtQuant(1, {"me first".postln; a.seconds.postln;});
a.algaSchedAtQuantOnceWithTopPriority(1, {"no, me first".postln; a.seconds.postln;});
a.algaSchedAtQuantOnceWithTopPriority(1, {"nono, me first".postln; a.seconds.postln;});
a.algaSchedAtQuantOnceWithTopPriority(2, {"mhmh".postln; a.seconds.postln;});

a.algaSchedAtQuantOnceWithTopPriority(1, {
	//emulate EventStreamPlayer.play
	a.play({
		thisThread.clock.sched(0, { //this is pushed at the bottom, timing is the same as the other calls tho!
			"last".postln;
			a.seconds.postln;
			nil;
		});
		a.seconds.postln;
		a.queue.postln;
		nil;
	}, 0.asQuant);
});

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
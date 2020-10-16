(
AlgaThread.verbose = true;
~scheduler = AlgaScheduler();
)

(
~scheduler.addAction({ a == 0 }, { "yay".postln});
~scheduler.addAction({ 0 == 0 }, { "yaey".postln});
)

a = 0


(
AlgaThread.verbose = true;
~scheduler2 = AlgaScheduler(cascadeMode:true);
)

(
~scheduler2.addAction({ b == 0 }, { "yay".postln});
~scheduler2.addAction({ 0 == 0 }, { "yaey".postln});
)

b = 0
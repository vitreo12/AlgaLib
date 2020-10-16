(
AlgaThread.verbose = true;
~scheduler = AlgaScheduler();
)

(
~scheduler.addAction({ c == 0 }, { "yay".postln});
~scheduler.addAction({ 0 == 0 }, { "yaey".postln});
)

//Then here
c = 0
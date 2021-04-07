//floor
(
t = TempoClock.default;
q = 2;
t.beats.postln;
t.schedAbs(t.beats.floor + q, { t.beats.postln; "".postln; nil });
)

//or ceil?
(
t = TempoClock.default;
q = 2;
t.beats.postln;
t.schedAbs(t.beats.ceil + q, { t.beats.postln; "".postln; nil });
)

(
t = TempoClock.default;
q = 2;
t.beats.postln;
t.algaSchedAtQuantOnce(q, { t.beats.postln; "".postln; });
)

(
t = TempoClock.default;
q = 1 / 4;
t.beats.postln;
t.algaSchedAtQuantOnce(q, { t.beats.postln; "".postln; });
)
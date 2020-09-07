b = Buffer.alloc(s, 20, 1);

(
b.set(0, 1);
b.set(1, 0.5);
b.set(2, 0.25);
b.set(3, 0.125);
b.set(4, 0.5);
b.set(5, 0.123);
)

a = { TDuty.ar(Dbufrd(b, (Dseries(0, 1, inf) % \len.kr(4)).dpoll, 1)).dup }.play

a.set(\len, 5)
a.set(\len, 6)

a.set(\len, 4)
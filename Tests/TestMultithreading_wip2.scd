(
Server.supernova;
s.options.blockSize = 64;
s.boot;
)

(
p = ParGroup();
z = ParGroup();
fork {
    loop {
        (
            degree: rrand(0.0, 12.0).round(1/4),
            group: p,
            db: rrand(-30, -25),
            pan: rrand(-1.0, 1.0)
        ).play;
        0.002.wait;
    }
};

fork {
    loop {
        (
            degree: rrand(0.0, 12.0).round(1/4),
            group: z,
            db: rrand(-30, -25),
            pan: rrand(-1.0, 1.0)
        ).play;
        0.02.wait;
    }
};
fork {
	loop {
		s.avgCPU.postln;
		1.wait;
	}
}
)

26

(
p = Group();
z = Group();
fork {
    loop {
        (
            degree: rrand(0.0, 12.0).round(1/4),
            group: p,
            db: rrand(-30, -25),
            pan: rrand(-1.0, 1.0)
        ).play;
        0.002.wait;
    }
};

fork {
    loop {
        (
            degree: rrand(0.0, 12.0).round(1/4),
            group: z,
            db: rrand(-30, -25),
            pan: rrand(-1.0, 1.0)
        ).play;
        0.02.wait;
    }
};

fork {
	loop {
		s.avgCPU.postln;
		1.wait;
	}
}
)

50

(
a = ParGroup(s.defaultGroup);
b = Group(a);
c = Group(b);
d = Group(c);
f = Group(d);
p = Group(f);
z = Group(f);
fork {
    loop {
        (
            degree: rrand(0.0, 12.0).round(1/4),
            group: p,
            db: rrand(-30, -25),
            pan: rrand(-1.0, 1.0)
        ).play;
        0.002.wait;
    }
};

fork {
    loop {
        (
            degree: rrand(0.0, 12.0).round(1/4),
            group: z,
            db: rrand(-30, -25),
            pan: rrand(-1.0, 1.0)
        ).play;
        0.02.wait;
    }
};

fork {
	loop {
		s.avgCPU.postln;
		1.wait;
	}
}
)

50

(
a = ParGroup(s.defaultGroup);
b = Group(a);
p = ParGroup(b);
c = Group(a);
z = ParGroup(c);
fork {
    loop {
        (
            degree: rrand(0.0, 12.0).round(1/4),
            group: p,
            db: rrand(-30, -25),
            pan: rrand(-1.0, 1.0)
        ).play;
        0.002.wait;
    }
};

fork {
    loop {
        (
            degree: rrand(0.0, 12.0).round(1/4),
            group: z,
            db: rrand(-30, -25),
            pan: rrand(-1.0, 1.0)
        ).play;
        0.02.wait;
    }
};

fork {
	loop {
		s.avgCPU.postln;
		1.wait;
	}
}
)

20
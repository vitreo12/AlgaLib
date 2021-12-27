# 1.2.0

- Added the `interpShape` option. This allows to specify an interpolation shape in the form of an
  `Env`. All connection methods have also been updated to receive a `shape` argument to set the `Env` 
  for the specific connection. Check the `Examples/AlgaNode/11_InterpShape.scd` below:

    ```SuperCollider
    (
    Alga.boot({
        a = AlgaNode(
            { SinOsc.ar(\freq.kr(440)) },
            interpTime: 3,
            interpShape: Env([0, 1, 0.5, 1], [1, 0.5, 1])
        ).play
    })
    )

    //The connection will use the Env declared in interpShape
    a <<.freq 220;

    //Temporary Env (standard ramp)
    a.from(880, \freq, shape: Env([0, 1]))

    //Using the original interpShape
    a <<.freq 440;
    ```

- Added multithreading support. Now, if booting `Alga` with `supernova`, it will automatically
  parallelize the arrangement of the nodes over the CPU cores. Check the
  `Examples/AlgaNode/10_Multithreading.scd` below:

    ```SuperCollider
    //Boot Alga with the supernova server
    //Alga will automatically spread the load across multiple CPU cores.
    (
    Alga.boot({
        var reverb;

        //Definition of a bank of 50 sines
        AlgaSynthDef(\sines, {
            Mix.ar(Array.fill(50, { SinOsc.ar(Rand(200, 1000)) * 0.005 }))
        }).add;

        s.sync;

        //A reverb effect
        reverb = AlgaNode({ FreeVerb1.ar(\in.ar) }).play(chans: 2);

        //50 separated banks of 50 sine oscillators.
        //Their load will be spread across multiple CPU cores.
        50.do({
            var bank = AlgaNode(\sines);
            reverb <<+ bank;
        });

        //Print CPU usage
        fork {
            loop {
                ("CPU: " ++ s.avgCPU.asStringPrec(4) ++ " %").postln;
                1.wait;
            }
        }
    }, algaServerOptions: AlgaServerOptions(supernova: true, latency: 1));
    )

    //Boot Alga with the standard scsynth server.
    //Note the higher CPU usage as the load is only on one CPU core.
    (
    Alga.boot({
        var reverb;

        //Definition of a bank of 50 sines
        AlgaSynthDef(\sines, {
            Mix.ar(Array.fill(50, { SinOsc.ar(Rand(200, 1000)) * 0.005 }))
        }).add;

        s.sync;

        //A reverb effect
        reverb = AlgaNode({ FreeVerb1.ar(\in.ar) }).play(chans: 2);

        //50 separated banks of 50 sine oscillators.
        //Their load will be spread across multiple threads.
        50.do({
            var bank = AlgaNode(\sines);
            reverb <<+ bank;
        });

        //Print CPU usage
        fork {
            loop {
                ("CPU: " ++ s.avgCPU.asStringPrec(4) ++ " %").postln;
                1.wait;
            }
        }
    }, algaServerOptions: AlgaServerOptions(latency: 1))
    )
    ```

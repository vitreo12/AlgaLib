AlgaLib 
=======

This is the *SuperCollider* implementation of *Alga*.

*Alga* is a new language for live coding that focuses on the creation and connection of sonic
modules. Unlike other audio software environments, the act of connecting *Alga* modules together is
viewed as an essential component of music composing and improvising, and not just as a mean towards
static audio patches. In *Alga*, the definition of a new connection between the output of a module
and the input of another does not happen instantaneously, but it triggers a process of *parameter
interpolation* over a specified window of time.

## Installation

To install *AlgaLib*, you can either:

1. Use the `Quarks` system: `Quarks.install("https://github.com/vitreo12/AlgaLib")`

2. `git clone` this repository to your `Platform.userExtensionDir`.

### AlgaAudioControl

This *UGen* fixes some synchronization issues that may result in audio glitches for short enveloped
sounds. After installing it, no further action is required: *Alga* will detect it and use it
internally. To install the `AlgaAudioControl` *UGen* follow these simple instructions:

1. Download it from https://github.com/vitreo12/AlgaAudioControl/releases/tag/v1.0.0

2. Unzip it to your *SuperCollider*'s `Platform.userExtensionDir`. 

## Examples

For usage and examples, check the *Help files* and the *Examples* folder. 

### AlgaNode

```SuperCollider
//Boot Alga and declare an AlgaSynthDef
(
Alga.boot({
    AlgaSynthDef(\sine, {
        SinOsc.ar(\freq.ar(440))
    }).add;
});
)

//Declare a node and play to stereo output
a = AlgaNode(\sine, interpTime:2).play(chans:2);

//Change \freq parameter. Note how it interpolates to new value over 2 seconds
a <<.freq 220;

//Declare a square oscillator with variable frequency 1 to 100
b = AlgaNode({ Pulse.ar(LFNoise1.kr(1).range(1, 100)) });

//Map the b node to a's \freq parameter, scaling the -1 to 1 range to 100 to 1000.
//The interpolation will take 5 seconds to complete
a.from(b, \freq, scale: [100, 1000], time:5);

//Declare a new Sine oscillator to use as LFO
c = AlgaNode(\sine, [\freq, 2]);

//Also connect c to a's \freq, mixing with what was already there.
//This will use a's interpTime, 2
a.mixFrom(c, \freq, scale:[-100, 100]);

//Clear over 3 seconds. Note how the interpolation will bring us back
//to just having b modulating a's frequency parameter
c.clear(time:3);

//Clear over 5 seconds. No more connections:
//now we'll interpolate back to a's \freq default value, 440
b.clear(time:5);

//Bye bye
a.clear(time:3);
```

### AlgaPattern

```SuperCollider
(
//Boot Alga
Alga.boot({
    //Declare a simple AlgaSynthDef.
    //Note that for it to be used in an AlgaPattern it must free itself.
    //Also, note the 'sampleAccurate' argument. This allows the AlgaSynthDef to use OffsetOut instead of Out
    //for sample accurate retriggering.
    AlgaSynthDef(\sinePerc, {
        SinOsc.ar(\freq.kr(440)) * EnvPerc.ar
    }, sampleAccurate: true).add;

    //Wait for definition to be sent to server
    s.sync;

    //Create an AlgaPattern and play it.
    //Unlike Pbind, AlgaPatterns use an Event to describe the parameter -> value mapping.
    a = AlgaPattern((
        def: \sinePerc,
        dur: 0.5
    )).play(chans: 2);
});
)

//Interpolate over the new Pseq. 'sched: 1' triggers it at the next beat
a.from(Pseq([220, 440, 880], inf), \freq, time: 3, sched: 1);

//Interpolate over the new Pwhite.
a.from(Pwhite(220, 880), \freq, time: 3, sched: 1);

//Interpolation can be sampled and held instead of being dynamic
a.from(Pseq([220, 440, 880], inf), \freq, sampleAndHold: true, time: 3, sched: 1);

//Change \dur does not trigger interpolation (yet), but it applies the changes directly
a.from(Pwhite(0.03, 0.5), \dur, sched: 1);

//Alternatively, the 'replaceDur' option can be used to trigger a 'replace' call, allowing for volume transitions
a.replaceDur = true;

//Now it will 'replace'
a.from(0.25, \dur, time: 3, sched: 1);

//Bye bye
a.clear(time: 2);
```

AlgaBus {
	var <server;
	var <bus;
	var busArg; // cache for "/s_new" bus arg
	var <rate = nil, <numChannels = 0;

	*new { | server, numChannels = 1, rate = \audio |
		^super.new.init(server, numChannels, rate);
	}

	init { | argServer, argNumChannels = 1, argRate = \audio |
		server = argServer;
		this.newBus(argNumChannels, argRate);
	}

	newBus { | argNumChannels = 1, argRate = \audio |
		rate = argRate;
		numChannels = argNumChannels;
		bus = Bus.alloc(rate, server, numChannels);
		this.makeBusArg;
	}

	free { | clear = false |
		if(bus != nil, { bus.free(clear) });
		bus  = nil;
		rate = nil;
		numChannels = 0;
		busArg = nil;
	}

	//Define getter
	busArg { ^busArg ?? { this.makeBusArg } }

	//This allows multichannel bus to be used when patching them with .busArg !
	makeBusArg {
		var index, prefix;
		if(bus == nil) { ^busArg = "" }; // still neutral
		prefix = if(rate == \audio) { "\a" } { "\c" };
		index = bus.index;
		^busArg = if(numChannels == 1) {
			prefix ++ index
		} {
			{ |i| prefix ++ (index + i) }.dup(numChannels)
		}
	}

	asMap {
		^busArg;
	}

	asUGenInput {
		if(bus == nil, { ^nil });
		^bus.index;
	}

	index {
		if(bus == nil, { ^nil });
		^bus.index;
	}

	//Create an output synth and output sound to it
	play { | target=0, outbus, fadeTime, addAction=\addToTail |
		bus.play(target, outbus, fadeTime, addAction);
	}
}

+Bus {
	busArg {
		^mapSymbol ?? {
			if(index.isNil) { MethodError("bus not allocated.", this).throw };
			mapSymbol = if(rate == \control) { "c" } { "a" };
			if(numChannels == 1) {
				mapSymbol = (mapSymbol ++ index).asSymbol;
			} {
				{ |i| mapSymbol ++ (index + i) }.dup(numChannels)
			}
		}
	}
}
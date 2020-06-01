AlgaBus {
	var <>server;
	var <>bus;
	var busArg; // cache for "/s_new" bus arg
	var <>rate = nil, <>numChannels = 0;

	*new { | server, numChannels = 1, rate = \audio |
		^super.new.init(server, numChannels, rate);
	}

	init { | server, numChannels = 1, rate = \audio |
		this.server = server;
		this.newBus(numChannels, rate);
	}

	newBus { | numChannels = 1, rate = \audio |
		this.rate = rate;
		this.numChannels = numChannels;
		this.bus = Bus.alloc(rate, this.server, numChannels); //Should I wait on this alloc?
		this.makeBusArg;
	}

	free {
		if(this.bus != nil, {
			this.bus.free(true);
		});
		this.rate = nil;
		this.numChannels = 0;
		busArg = nil;
	}

	busArg { ^busArg ?? { this.makeBusArg } }

	//This allows multichannel bus to be used when patching them with .busArg !
	makeBusArg {
		var index, numChannels, prefix;
		if(this.bus.isNil) { ^busArg = "" }; // still neutral
		prefix = if(this.rate == \audio) { "\a" } { "\c" };
		index = this.index;
		numChannels = this.numChannels;
		^busArg = if(numChannels == 1) {
			prefix ++ index
		} {
			{ |i| prefix ++ (index + i) }.dup(numChannels)
		}
	}

	asMap {
		^this.busArg;
	}

	asUGenInput {
		^this.bus.index;
	}

	index {
		^this.bus.index;
	}

	play {
		this.bus.play;
	}
}
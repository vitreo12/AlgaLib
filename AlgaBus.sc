AlgaBus {
	var <>server;
	var <>bus;
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
		this.server.postln;
		this.bus = Bus.alloc(rate, this.server, numChannels); //Should I wait on this alloc?
	}

	free {
		if(this.bus != nil, {
			this.bus.free(true);
		});
		this.rate = nil;
		this.numChannels = 0;
	}

	play {
		this.bus.play;
	}

	asUGenInput {
		^this.bus.index;
	}
}
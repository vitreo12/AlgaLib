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
		this.freeBus;
		this.rate = rate;
		this.numChannels = numChannels;
		this.server.postln;
		this.bus = Bus.alloc(rate, this.server, numChannels); //Should I wait on this alloc?
	}

	freeBus {
		this.bus.free(true);
		this.rate = nil;
		this.numChannels = 0;
	}

	asUGenInput {
		^this.bus.index;
	}
}
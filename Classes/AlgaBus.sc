// AlgaLib: SuperCollider implementation of the Alga live coding language
// Copyright (C) 2020-2021 Francesco Cameli

// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.

// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.

// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

AlgaBus {
	var <server;
	var <bus;
	var busArg;
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

	//FUNDAMENTAL: clear = true fixes all issues with control busses needed to be reset!
	free { | clear = true |
		if(bus != nil, { bus.free(clear) });
		bus  = nil;
		rate = nil;
		numChannels = 0;
		busArg = nil;
	}

	//set to 0
	setAll { | val |
		bus.setAll(val)
	}

	//Define getter
	busArg { ^(busArg ?? { this.makeBusArg }) }

	//This allows multichannel bus to be used when patching them with .busArg !
	makeBusArg {
		var index, prefix;
		if(bus == nil) { ^busArg = nil }; // still neutral
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
	busArg { ^(mapSymbol ?? { this.makeBusArg }) }

	makeBusArg {
		mapSymbol = if(rate == \audio) { "\a" } { "\c" };
		mapSymbol = if(numChannels == 1) {
			mapSymbol ++ index
		} {
			{ |i| mapSymbol ++ (index + i) }.dup(numChannels)
		};
		^mapSymbol;
	}
}

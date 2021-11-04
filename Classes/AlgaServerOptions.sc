// AlgaLib: SuperCollider implementation of Alga, an interpolating live coding environment.
// Copyright (C) 2020-2021 Francesco Cameli.

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

AlgaServerOptions {
	var <>sampleRate, <>blockSize, <>memSize, <>numBuffers;
	var <>numAudioBusChannels, <>numControlBusChannels, <>maxNodes;
	var <>maxSynthDefs, <>numWireBufs, <>numInputBusChannels, <>numOutputBusChannels;
	var <>supernova, <>supernovaThreads, <>supernovaUseSystemClock, <protocol = \tcp, <>latency;

	*new { | sampleRate, blockSize, numInputBusChannels, numOutputBusChannels,
		memSize=524288, numAudioBusChannels=24576, numControlBusChannels=24576,
		numBuffers=24576, maxNodes=32768, maxSynthDefs=32768, numWireBufs=32768,
		supernova=false, supernovaThreads, supernovaUseSystemClock=true, latency |

		^super.new.init(
			sampleRate, blockSize, numInputBusChannels, numOutputBusChannels,
			memSize, numAudioBusChannels, numControlBusChannels,
			numBuffers, maxNodes, maxSynthDefs, numWireBufs,
			supernova, supernovaThreads, supernovaUseSystemClock, latency
		);
	}

	init { | argSampleRate, argBlockSize, argNumInputBusChannels, argNumOutputBusChannels,
		argMemSize=524288, argNumAudioBusChannels=24576, argNumControlBusChannels=24576,
		argNumBuffers=24576, argMaxNodes=32768, argMaxSynthDefs=32768, argNumWireBufs=32768,
		argSupernova=false, argSupernovaThreads, argSupernovaUseSystemClock=true, argLatency |

		sampleRate = argSampleRate ? Server.default.options.sampleRate;
		blockSize = argBlockSize ? Server.default.options.blockSize;
		numInputBusChannels = argNumInputBusChannels ? Server.default.options.numInputBusChannels;
		numOutputBusChannels = argNumOutputBusChannels ? Server.default.options.numOutputBusChannels;
		memSize = argMemSize;
		numAudioBusChannels = argNumAudioBusChannels;
		numControlBusChannels = argNumControlBusChannels;
		numBuffers = argNumBuffers;
		maxNodes = argMaxNodes;
		maxSynthDefs = argMaxSynthDefs;
		numWireBufs = argNumWireBufs;
		supernova = argSupernova;
		supernovaThreads = argSupernovaThreads ? Server.default.options.threads;
		supernovaUseSystemClock = argSupernovaUseSystemClock;
		latency = argLatency ? Server.default.latency;
	}
}

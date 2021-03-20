AlgaServerOptions {
	var <>sampleRate, <>blockSize, <>memSize, <>numBuffers;
	var <>numAudioBusChannels, <>numControlBusChannels, <>maxSynthDefs;
	var <>numWireBufs, <>numInputBusChannels, <>numOutputBusChannels, <>supernova;
	var <>supernovaThreads, <>supernovaUseSystemClock, <>latency;

	*new { | sampleRate, blockSize, memSize=(8192*64), numBuffers=(1024*24),
		numAudioBusChannels=(1024*24), numControlBusChannels=(1024*24), maxSynthDefs=16384,
		numWireBufs=16384, numInputBusChannels, numOutputBusChannels,
		supernova=false, supernovaThreads, supernovaUseSystemClock=true, latency |

		^super.new.init(
			sampleRate, blockSize, memSize, numBuffers,
			numAudioBusChannels, numControlBusChannels,  maxSynthDefs, numWireBufs,
			numInputBusChannels, numOutputBusChannels, supernova, supernovaThreads,
			supernovaUseSystemClock, latency
		);
	}

	init { | argSampleRate, argBlockSize, argMemSize=(8192*64), argNumBuffers=(1024*24),
		argNumAudioBusChannels=(1024*24), argNumControlBusChannels=(1024*24), argMaxSynthDefs=16384,
		argNumWireBufs=16384, argNumInputBusChannels, argNumOutputBusChannels,
		argSupernova=false, argSupernovaThreads, argSupernovaUseSystemClock=true, argLatency |

		sampleRate = argSampleRate ? Server.default.options.sampleRate;
		blockSize = argBlockSize ? Server.default.options.blockSize;
		memSize = argMemSize;
		numBuffers = argNumBuffers;
		numAudioBusChannels = argNumAudioBusChannels;
		numControlBusChannels = argNumControlBusChannels;
		maxSynthDefs = argMaxSynthDefs;
		numWireBufs = argNumWireBufs;
		numInputBusChannels = argNumInputBusChannels ? Server.default.options.numInputBusChannels;
		numOutputBusChannels = argNumOutputBusChannels ? Server.default.options.numOutputBusChannels;
		supernova = argSupernova;
		supernovaThreads = argSupernovaThreads ? Server.default.options.threads;
		supernovaUseSystemClock = argSupernovaUseSystemClock;
		latency = argLatency ? Server.default.latency;
	}
}
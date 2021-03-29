AlgaServerOptions {
	var <>sampleRate, <>blockSize, <>memSize, <>numBuffers;
	var <>numAudioBusChannels, <>numControlBusChannels, <>maxNodes;
	var <>maxSynthDefs, <>numWireBufs, <>numInputBusChannels, <>numOutputBusChannels;
	var <>supernova, <>supernovaThreads, <>supernovaUseSystemClock, <>protocol, <>latency;

	*new { | sampleRate, blockSize, memSize=(8192*64), numBuffers=(1024*24),
		numAudioBusChannels=(1024*24), numControlBusChannels=(1024*24), maxNodes=32768,
		maxSynthDefs=32768, numWireBufs=32768, numInputBusChannels, numOutputBusChannels,
		supernova=false, supernovaThreads, supernovaUseSystemClock=true, protocol = \tcp, latency |

		^super.new.init(
			sampleRate, blockSize, memSize, numBuffers,
			numAudioBusChannels, numControlBusChannels, maxNodes, maxSynthDefs, numWireBufs,
			numInputBusChannels, numOutputBusChannels, supernova, supernovaThreads,
			supernovaUseSystemClock, protocol, latency
		);
	}

	init { | argSampleRate, argBlockSize, argMemSize=(8192*64), argNumBuffers=(1024*24),
		argNumAudioBusChannels=(1024*24), argNumControlBusChannels=(1024*24), argMaxNodes=32768,
		argMaxSynthDefs=32768, argNumWireBufs=32768, argNumInputBusChannels, argNumOutputBusChannels,
		argSupernova=false, argSupernovaThreads, argSupernovaUseSystemClock=true, argProtocol = \tcp, argLatency |

		sampleRate = argSampleRate ? Server.default.options.sampleRate;
		blockSize = argBlockSize ? Server.default.options.blockSize;
		memSize = argMemSize;
		numBuffers = argNumBuffers;
		numAudioBusChannels = argNumAudioBusChannels;
		numControlBusChannels = argNumControlBusChannels;
		maxNodes = argMaxNodes;
		maxSynthDefs = argMaxSynthDefs;
		numWireBufs = argNumWireBufs;
		numInputBusChannels = argNumInputBusChannels ? Server.default.options.numInputBusChannels;
		numOutputBusChannels = argNumOutputBusChannels ? Server.default.options.numOutputBusChannels;
		supernova = argSupernova;
		supernovaThreads = argSupernovaThreads ? Server.default.options.threads;
		supernovaUseSystemClock = argSupernovaUseSystemClock;
		protocol = argProtocol;
		latency = argLatency ? Server.default.latency;
	}
}
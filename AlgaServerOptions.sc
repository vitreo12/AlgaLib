AlgaServerOptions {
	var <>sampleRate, <>blockSize, <>memSize, <>numBuffers;
	var <>numAudioBusChannels, <>numControlBusChannels, <>maxSynthDefs;
	var <>numWireBufs, <>numInputBusChannels, <>numOutputBusChannels, <>supernova;
	var <>supernovaThreads, <>supernovaUseSystemClock, <>latency;

	*new { | sampleRate=48000, blockSize=64, memSize=524288/*8192*64*/, numBuffers=(1024*24),
		numAudioBusChannels=(1024*24), numControlBusChannels=(1024*24), maxSynthDefs=16384,
		numWireBufs=8192, numInputBusChannels=2, numOutputBusChannels=2,
		supernova=false, supernovaThreads=8, supernovaUseSystemClock=true, latency=0.1 |

		^super.new.init(
			sampleRate, blockSize, memSize, numBuffers,
			numAudioBusChannels, numControlBusChannels,  maxSynthDefs, numWireBufs,
			numInputBusChannels, numOutputBusChannels, supernova, supernovaThreads,
			supernovaUseSystemClock, latency
		);
	}

	init { | argSampleRate=48000, argBlockSize=64, argMemSize=524288/*8192*64*/, argNumBuffers=(1024*24),
		argNumAudioBusChannels=(1024*24), argNumControlBusChannels=(1024*24), argMaxSynthDefs=16384,
		argNumWireBufs=8192, argNumInputBusChannels=2, argNumOutputBusChannels=2,
		argSupernova=false, argSupernovaThreads=8, argSupernovaUseSystemClock=true, argLatency=0.1 |

		sampleRate = argSampleRate;
		blockSize = argBlockSize;
		memSize = argMemSize;
		numBuffers = argNumBuffers;
		numAudioBusChannels = argNumAudioBusChannels;
		numControlBusChannels = argNumControlBusChannels;
		maxSynthDefs = argMaxSynthDefs;
		numWireBufs = argNumWireBufs;
		numInputBusChannels = argNumInputBusChannels;
		numOutputBusChannels = argNumOutputBusChannels;
		supernova = argSupernova;
		supernovaThreads = argSupernovaThreads;
		supernovaUseSystemClock = argSupernovaUseSystemClock;
		latency = argLatency;
	}
}
/*
THINGS TO DO:

1) Create all the interpolationProxies for every param AT AlgaNodeProxy instantiation (in the "put" function)

2) Supernova ParGroups used by default in Patterns?

3) Multiple servers connection.
   (boot servers with a lot of I/O and stream bettween them, with a block size difference OFC).
   (Also, servers must be linked in Jack)

X) Make "Restoring previous connections!" actually work

X) Make SURE that all connections work fine, ensuring that interpolationProxies are ALWAYS before the modulated
proxy and after the modulator. This gets screwed up with long chains.

X) When using clear / free, interpolationProxies should not fade

*/

AlgaLib {

	*boot {
		arg server = Server.local, algaServerOptions = AlgaServerOptions();
		server.sampleRate = algaServerOptions.sampleRate;
		server.bufSize = algaServerOptions.bufSize;
		server.memSize = algaServerOptions.memSize;
		server.numBuffers = algaServerOptions.numBuffers;
		server.numAudioBusChannels = algaServerOptions.numAudioBusChannels;
		server.numControlBusChannels = algaServerOptions.numControlBusChannels;
		server.maxSynthDefs = algaServerOptions.maxSynthDefs;
		server.numWireBufs = algaServerOptions.numWireBufs;
		server.numInputBusChannels = algaServerOptions.numInputBusChannels;
		server.numOutputBusChannels = algaServerOptions.numOutputBusChannels;
		if(algaServerOptions.numOutputBusChannels, {Server.supernova}, {Server.scsynth});
		server.threads = algaServerOptions.threads;
		server.latency = algaServerOptions.latency;

		server.quit;

		server.waitForBoot({
			AlgaStartup.initSynthDefs(server);
			AlgaProxySpace.new.push;
		});
	}

}

AlgaServerOptions {
	var <sampleRate, <bufSize, <memSize, <numBuffers, <numAudioBusChannels, <numControlBusChannels, <maxSynthDefs, <numWireBufs, <numInputBusChannels, <numOutputBusChannels, <supernova, <threads, <latency;

	*new {
		arg sampleRate=48000, bufSize=256, memSize=8192*256, numBuffers=1024*8, numAudioBusChannels=1024*8, numControlBusChannels=1024*8,  maxSynthDefs=16384, numWireBufs=1024, numInputBusChannels=2, numOutputBusChannels=2, supernova=false, threads=12, latency=0.1;

		^super.new.init(sampleRate, bufSize, memSize, numBuffers, numAudioBusChannels, numControlBusChannels,  maxSynthDefs, numWireBufs, numInputBusChannels, numOutputBusChannels, supernova, threads, latency);
	}

	init {
		this.sampleRate = sampleRate;
		this.bufSize = bufSize;
		this.memSize = memSize;
		this.numBuffers = numBuffers;
		this.numAudioBusChannels = numAudioBusChannels;
		this.numControlBusChannels = numControlBusChannels;
		this.maxSynthDefs = maxSynthDefs;
		this.numWireBufs = numWireBufs;
		this.numInputBusChannels = numInputBusChannels;
		this.numOutputBusChannels = numOutputBusChannels;
		this.supernova = supernova;
		this.threads = threads;
		this.latency = latency;
	}

}
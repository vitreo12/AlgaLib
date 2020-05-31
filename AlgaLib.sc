AlgaServerOptions {
	var <>sampleRate, <>blockSize, <>memSize, <>numBuffers, <>numAudioBusChannels, <>numControlBusChannels, <>maxSynthDefs, <>numWireBufs, <>numInputBusChannels, <>numOutputBusChannels, <>supernova, <>threads, <>latency;

	*new {
		arg sampleRate=48000, blockSize=256, memSize=8192*256, numBuffers=1024*8, numAudioBusChannels=1024*8, numControlBusChannels=1024*8,  maxSynthDefs=16384, numWireBufs=1024, numInputBusChannels=2, numOutputBusChannels=2, supernova=false, threads=12, latency=0.1;

		^super.new.init(sampleRate, blockSize, memSize, numBuffers, numAudioBusChannels, numControlBusChannels,  maxSynthDefs, numWireBufs, numInputBusChannels, numOutputBusChannels, supernova, threads, latency);
	}

	init {
		arg sampleRate=48000, blockSize=256, memSize=8192*256, numBuffers=1024*8, numAudioBusChannels=1024*8, numControlBusChannels=1024*8,  maxSynthDefs=16384, numWireBufs=1024, numInputBusChannels=2, numOutputBusChannels=2, supernova=false, threads=12, latency=0.1;

		this.sampleRate = sampleRate;
		this.blockSize = blockSize;
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

AlgaLib {

	*boot {
		arg server = Server.default, algaServerOptions = AlgaServerOptions();

		server.options.sampleRate = algaServerOptions.sampleRate;
		server.options.blockSize = algaServerOptions.blockSize;
		server.options.memSize = algaServerOptions.memSize;
		server.options.numBuffers = algaServerOptions.numBuffers;
		server.options.numAudioBusChannels = algaServerOptions.numAudioBusChannels;
		server.options.numControlBusChannels = algaServerOptions.numControlBusChannels;
		server.options.maxSynthDefs = algaServerOptions.maxSynthDefs;
		server.options.numWireBufs = algaServerOptions.numWireBufs;
		server.options.numInputBusChannels = algaServerOptions.numInputBusChannels;
		server.options.numOutputBusChannels = algaServerOptions.numOutputBusChannels;
		if(algaServerOptions.supernova, {Server.supernova}, {Server.scsynth});
		server.options.threads = algaServerOptions.threads;
		server.latency = algaServerOptions.latency;

		server.quit;

		//Add to SynthDescLib in order for .add to work... Find a leaner solution.
		SynthDescLib.global.addServer(server);

		server.waitForBoot({
			AlgaStartup.initSynthDefs(server);
		});
	}
}
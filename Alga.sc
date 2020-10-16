Alga {
	classvar <>algaSchedulers;

	*initSynthDefs {
		AlgaStartup.initSynthDefs;
	}

	*initClass {
		algaSchedulers = IdentityDictionary();
	}

	*clearAllSchedulers {
		if(algaSchedulers != nil, {
			algaSchedulers.do({ | algaScheduler |
				algaScheduler.clear;
			});

			algaSchedulers.clear;
		});
	}

	*boot { | onBoot, server, algaServerOptions |

		server = server ? Server.default;
		algaServerOptions = algaServerOptions ? AlgaServerOptions();

		//quit server if it was on
		server.quit;

		//set options
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

		//Check AlgaSynthDef folder exists...
		if(File.existsCaseSensitive(AlgaStartup.algaSynthDefPath) == false, {
			("Could not retrieve the AlgaSyntDef folder. Running 'Alga.initSynthDefs' now...").warn;
			this.initSynthDefs;
		});

		//Add to SynthDescLib in order for .add to work... Find a leaner solution.
		SynthDescLib.global.addServer(server);

		//Clear all previous schedulers, if present
		this.clearAllSchedulers;

		//Boot
		server.waitForBoot({
			server.initTree;

			//Create an AlgaScheduler on current server (using SystemClock for now...)
			//starting it here so printing happens after server boot.
			algaSchedulers[server] = AlgaScheduler(server, cascadeMode:true);

			onBoot.value;
		});
	}
}

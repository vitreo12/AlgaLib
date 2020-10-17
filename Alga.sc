Alga {
	classvar <>schedulers;

	*initSynthDefs {
		AlgaStartup.initSynthDefs;
	}

	*initClass {
		schedulers = IdentityDictionary();
	}

	*clearAllSchedulers {
		if(schedulers != nil, {
			schedulers.do({ | scheduler |
				scheduler.clear;
			});

			schedulers.clear;
		});
	}

	*newScheduler { | server, clock, cascadeMode = true |
		server = server ? Server.default;
		clock = clock ? TempoClock; //use tempo clock as default
		schedulers[server] = AlgaScheduler(server, clock, cascadeMode);
	}

	*getScheduler { | server |
		var scheduler = schedulers[server];
		if(scheduler.isNil, { ("No AlgaScheduler initialized for server " ++ server.asString).error });
		^scheduler;
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

			//Create an AlgaScheduler on current server (using TempoClock for now...)
			//starting it here so printing happens after server boot.
			this.newScheduler(server, cascadeMode:true);

			onBoot.value;
		});
	}
}

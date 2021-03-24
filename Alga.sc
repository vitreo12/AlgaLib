Alga {
	classvar <schedulers;
	classvar <servers;

	*initSynthDefs {
		AlgaStartup.initSynthDefs;
	}

	*initClass {
		schedulers = IdentityDictionary(1);
		servers = IdentityDictionary(1);
	}

	*clearScheduler { | server |
		var scheduler = schedulers[server];
		if(scheduler != nil, {
			scheduler.clear;
			schedulers.removeAt(server);
		});
	}

	*clearServer { | server, prevServerQuit |
		var tempServer = servers[server];
		if(tempServer != nil, {
			if(tempServer.serverRunning, {
				tempServer.quit(onComplete: { prevServerQuit[0] = true });
			}, {
				prevServerQuit[0] = true;
			});
			servers.removeAt(tempServer);
		}, {
			prevServerQuit[0] = true;
		});
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
		clock = clock ? TempoClock.default;
		schedulers[server] = AlgaScheduler(server, clock, cascadeMode);
	}

	*getScheduler { | server |
		var scheduler = schedulers[server];
		if(scheduler.isNil, { ("No AlgaScheduler initialized for server " ++ server.asString).error });
		^scheduler;
	}

	*boot { | onBoot, server, algaServerOptions |
		var prevServerQuit = [false]; //pass by reference: use Array

		server = server ? Server.default;
		algaServerOptions = algaServerOptions ? AlgaServerOptions();

		//set options
		server.options.sampleRate = algaServerOptions.sampleRate;
		server.options.blockSize = algaServerOptions.blockSize;
		server.options.memSize = algaServerOptions.memSize;
		server.options.numBuffers = algaServerOptions.numBuffers;
		server.options.numAudioBusChannels = algaServerOptions.numAudioBusChannels;
		server.options.numControlBusChannels = algaServerOptions.numControlBusChannels;
		server.options.maxNodes = algaServerOptions.maxNodes;
		server.options.maxSynthDefs = algaServerOptions.maxSynthDefs;
		server.options.numWireBufs = algaServerOptions.numWireBufs;
		server.options.numInputBusChannels = algaServerOptions.numInputBusChannels;
		server.options.numOutputBusChannels = algaServerOptions.numOutputBusChannels;
		if(algaServerOptions.supernova, {Server.supernova}, {Server.scsynth});
		server.options.threads = algaServerOptions.supernovaThreads;
		server.options.useSystemClock = algaServerOptions.supernovaUseSystemClock;
		server.options.protocol = algaServerOptions.protocol;
		server.latency = algaServerOptions.latency;

		//Check AlgaSynthDef folder exists...
		if(File.existsCaseSensitive(AlgaStartup.algaSynthDefIOPath) == false, {
			("Could not retrieve the correct AlgaSyntDef/IO folder. Running 'Alga.initSynthDefs' now...").warn;
			this.initSynthDefs;
		});

		//Add to SynthDescLib in order for SynthDef.add to work
		SynthDescLib.global.addServer(server);

		//Run CmdPeriod
		CmdPeriod.run;

		//clear scheduler @server if present
		this.clearScheduler(server);

		//clear server @server if present, also quit it
		this.clearServer(server, prevServerQuit);

		//Add the server
		servers[server] = server;

		//Create an AlgaScheduler on current server (using TempoClock for now...)
		this.newScheduler(server, cascadeMode:false);
		//this.newScheduler(server, cascadeMode:true);

		//Boot
		AlgaSpinRoutine.waitFor( { prevServerQuit[0] == true }, {
			server.waitForBoot({
				//Make sure to init everything
				server.initTree;

				//Execute onBoot function
				onBoot.value;
			});
		});
	}
}

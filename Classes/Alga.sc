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

Alga {
	classvar <schedulers;
	classvar <servers;
	classvar <clocks;
	classvar <oldSynthDefDir;

	*initSynthDefs {
		AlgaStartup.initSynthDefs;
	}

	*initClass {
		servers = IdentityDictionary(1);
		schedulers = IdentityDictionary(1);
		clocks = IdentityDictionary(1);

		//Make sure to reset it
		"SC_SYNTHDEF_PATH".unsetenv;
	}

	*maxIO {
		^AlgaStartup.algaMaxIO;
	}

	*maxIO_ { | value |
		AlgaStartup.algaMaxIO = value
	}

	*setAlgaSynthDefsDir {
		oldSynthDefDir = "SC_SYNTHDEF_PATH".getenv;
		"SC_SYNTHDEF_PATH".setenv(AlgaStartup.algaSynthDefPath);
	}

	*restoreSynthDefsDir {
		"SC_SYNTHDEF_PATH".setenv(oldSynthDefDir);
	}

	*newServer { | server |
		server = server ? Server.default;
		servers[server] = server;
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

	*newScheduler { | server, clock, cascadeMode = false |
		schedulers[server] = AlgaScheduler(server, clock, cascadeMode);
	}

	*getScheduler { | server |
		var scheduler = schedulers[server];
		if(scheduler.isNil, { ("No AlgaScheduler initialized for server " ++ server.asString).error });
		^scheduler;
	}

	*clearScheduler { | server |
		var scheduler = schedulers[server];
		if(scheduler != nil, {
			scheduler.clear;
			schedulers.removeAt(server);
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

	*newClock { | server, clock |
		clock = clock ? TempoClock(1, queueSize:16834).permanent_(true);
		clocks[server] = clock;
		^clock
	}

	*clock { | server |
		if(server.isNil, { server = Server.default });
		^clocks[server]
	}

	*boot { | onBoot, server, algaServerOptions, clock |
		var prevServerQuit = [false]; //pass by reference: use Array

		server = server ? Server.default;
		algaServerOptions = algaServerOptions ? AlgaServerOptions();

		if(algaServerOptions.class != AlgaServerOptions, {
			"Alga: Use an AlgaServerOptions instance as the algaServerOptions argument".error;
			^this;
		});

		//AlgaServerOptions
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
		if(algaServerOptions.supernova, { Server.supernova }, { Server.scsynth });
		server.options.threads = algaServerOptions.supernovaThreads;
		server.options.useSystemClock = algaServerOptions.supernovaUseSystemClock;
		server.options.protocol = algaServerOptions.protocol;
		server.latency = algaServerOptions.latency;

		//Check AlgaSynthDef/IO folder exists...
		if(File.exists(AlgaStartup.algaSynthDefIO_numberPath) == false, {
			("Could not retrieve the correct 'AlgaSyntDefs/IO_...' folder. Running 'Alga.initSynthDefs' now...").warn;
			this.initSynthDefs;
		});

		//Use AlgaSynthDefs as folder for SynthDefs
		this.setAlgaSynthDefsDir;

		//Add to SynthDescLib in order for SynthDef.add to work
		SynthDescLib.global.addServer(server);

		//Run CmdPeriod
		CmdPeriod.run;

		//Clear scheduler @server if present
		this.clearScheduler(server);

		//Clear server @server if present, also quit it
		this.clearServer(server, prevServerQuit);

		//Add the server
		this.newServer(server);

		//Add the clock. Creates a new TempoClock if clock is nil
		clock = this.newClock(server, clock);

		//Create an AlgaScheduler @ the server
		this.newScheduler(server, clock);

		//Boot
		AlgaSpinRoutine.waitFor( { prevServerQuit[0] == true }, {
			server.waitForBoot({
				//Make sure to init groups
				server.initTree;

				//Execute onBoot function
				onBoot.value;
			});
		});
	}

	*quit { | onQuit, server |
		var prevServerQuit = [false]; //pass by reference: use Array
		server = server ? Server.default;
		this.clearScheduler(server);
		this.clearServer(server, prevServerQuit);
		this.restoreSynthDefsDir;
		AlgaSpinRoutine.waitFor( { prevServerQuit[0] == true }, {
			onQuit.value;
		});
	}
}

AlgaProxySpace : ProxySpace {

	/*

	makeProxy {

		//AlgaProxySpace's default is a one channel audio proxy
		var proxy = AlgaNodeProxy.new(server, \audio, 1);

		this.initProxy(proxy);

		//Change reshaping to be elastic by default
		proxy.reshaping = \elastic;

		^proxy
	}

	*/

	makeProxy {
		var proxy = AlgaNodeProxy.new(server);

		this.initProxy(proxy);

		//Change reshaping to be elastic by default
		proxy.reshaping = \elastic;

		//default ft / ct
		proxy.ft = 0;
		proxy.ct = 0;

		^proxy
	}

	makeMasterClock { | tempo = 1.0, beats, seconds |
		var clock, proxy;
		proxy = envir[\tempo];
		if(proxy.isNil) { proxy = AlgaNodeProxy.control(server, 1); envir.put(\tempo, proxy); };
		proxy.fadeTime = 0.0;
		proxy.put(0, { |tempo = 1.0| tempo }, 0, [\tempo, tempo]);
		this.clock = AlgaTempoBusClock.new(proxy, tempo, beats, seconds).permanent_(true);
		if(quant.isNil) { this.quant = 1.0 };
	}

	makeSlaveClock { | masterProxySpace |
		var masterClock, proxy, tempo;

		if(masterProxySpace.class != AlgaProxySpace, {
			"A AlgaProxySpace is required as a master proxy space".warn;
			^nil;
		});

		masterClock = masterProxySpace.clock;

		if(masterClock.class != AlgaTempoBusClock, {
			"A AlgaProxySpace with a running AlgaTempoBusClock is required".warn;
			^nil;
		});

		tempo = masterClock.tempo;

		proxy = envir[\tempo];
		if(proxy.isNil) { proxy = AlgaNodeProxy.control(server, 1); envir.put(\tempo, proxy); };
		proxy.fadeTime = 0.0;
		proxy.put(0, { |tempo = 1.0| tempo }, 0, [\tempo, tempo]);

		//Add slave control to this ProxySpace's ~tempo proxy
		masterClock.slavesControl.put(proxy, proxy);

		//Set tempo and quant
		this.clock = masterClock;
		this.quant = masterProxySpace.quant;
	}

	clear { |fadeTime|
		//Call ProxySpace's clear
		super.clear;

		//Remove this AlgaProxySpace from Clock's slaves
		if(this.clock.class == AlgaTempoBusClock, {
			this.clock.slavesControl.removeAt(this.envir[\tempo]);
		});
	}

	ft_ { | dur |
		this.fadeTime_(dur);
	}

	ft {
		^this.fadeTime;
	}
}

//Alias
APSpace : AlgaProxySpace {

}
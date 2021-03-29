AlgaSynth : Synth {
	//Need the setter to "synth.algaInstantiated = false" in *new, to reset state
	var <>algaInstantiated = false;

	*new { | defName, args, target, addAction=\addToHead, waitForInst = true |
		var synth, server, addActionID;
		target = target.asTarget;
		server = target.server;
		addActionID = addActions[addAction];
		synth = this.basicNew(defName, server);
		synth.group = if(addActionID < 2) { target } { target.group };

		synth.algaInstantiated = false;

		//oneshot function that waits for initialization
		if(waitForInst, {
			synth.waitForInstantiation(synth.nodeID);
		}, {
			synth.algaInstantiated = true;
		});

		//actually send synth to server
		server.sendMsg(9, //"s_new"
			defName, synth.nodeID, addActionID, target.nodeID,
			*(args.asOSCArgArray)
		);

		^synth;
	}

	waitForInstantiation { | nodeID |
		var oscfunc = OSCFunc.newMatching({ | msg |
			algaInstantiated = true;
		}, '/n_go', this.server.addr, argTemplate:[nodeID]).oneShot;

		//If fails to respond in 3 seconds, assume it's instantiated...
		//This unfortunaly happens due to SC's weak OSC responding with \udp
		//Alga uses \tdp by default!
		if(server.options.protocol == \udp, {
			SystemClock.sched(3, {
				if(algaInstantiated.not, {
					("Using a server with the UDP protocol, use the TCP one instead. Instantiation packet for AlgaSynth " ++ nodeID ++ " has been lost. Setting algaInstantiated to true").warn;
					algaInstantiated = true;
					oscfunc.free;
				})
			})
		});
	}
}
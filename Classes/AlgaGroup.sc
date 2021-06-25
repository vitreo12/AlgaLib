AlgaGroup : Group {
	//Need the setter to "synth.algaInstantiated = false" in *new, to reset state
	var <>algaInstantiated = false;

	*new { arg target, addAction = \addToHead, waitForInst = true;
		var group, server, addActionID;
		target = target.asTarget;
		server = target.server;
		group = this.basicNew(server);
		addActionID = addActions[addAction];
		group.group = if(addActionID < 2) { target } { target.group };

		group.algaInstantiated = false;

		//oneshot function that waits for initialization
		if(waitForInst, {
			group.waitForInstantiation(group.nodeID);
		}, {
			group.algaInstantiated = true
		});

		//actually send group to server
		server.sendMsg(
			this.creationCmd, group.nodeID,
			addActionID, target.nodeID
		);

		^group
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
					("Using a server with the UDP protocol, use the TCP one instead. Instantiation packet for AlgaGroup " ++ nodeID ++ " has been lost. Setting algaInstantiated to true").warn;
					algaInstantiated = true;
					oscfunc.free;
				})
			})
		});
	}
}
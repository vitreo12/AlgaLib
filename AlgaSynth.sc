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

		//If fails to respond in 3 seconds, free the OSCFunc
		SystemClock.sched(3, {
			if(algaInstantiated.not, {
				oscfunc.free;
			})
		})
	}
}
AlgaSynth : Synth {
	//Need the setter to "synth.instantiated = false" in *new, to reset state
	var <>instantiated = false;

	*new { | defName, args, target, addAction=\addToHead, waitForInst = true |
		var synth, server, addActionID;
		target = target.asTarget;
		server = target.server;
		addActionID = addActions[addAction];
		synth = this.basicNew(defName, server);
		synth.group = if(addActionID < 2) { target } { target.group };

		synth.instantiated = false;

		//oneshot function that waits for initialization
		if(waitForInst, {
			synth.waitForInstantiation(synth.nodeID);
		}, {
			synth.instantiated = true;
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
			instantiated = true;
		}, '/n_go', this.server.addr, argTemplate:[nodeID]).oneShot;

		//If fails to respond in 3 seconds, free the OSCFunc
		SystemClock.sched(3, {
			if(instantiated.not, {
				oscfunc.free;
			})
		})
	}
}
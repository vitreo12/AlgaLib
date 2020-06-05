AlgaSynth : Synth {
	//Need the setter to "synth.instantiated = false" in *new
	var <>instantiated = false;

	*new { | defName, args, target, addAction=\addToHead |
		var synth, server, addActionID;
		target = target.asTarget;
		server = target.server;
		addActionID = addActions[addAction];
		synth = this.basicNew(defName, server);
		synth.group = if(addActionID < 2) { target } { target.group };

		synth.instantiated = false;

		//oneshot function that waits for initialization
		synth.waitForInstantiation(synth.nodeID);

		//actually send synth to server
		server.sendMsg(9, //"s_new"
			defName, synth.nodeID, addActionID, target.nodeID,
			*(args.asOSCArgArray)
		);

		^synth;
	}

	//Would this be an overkill for Pattern based stuff??
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
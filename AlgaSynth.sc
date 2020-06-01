AlgaSynth : Synth {
	var <>instantiated = false;

	//SynthDescLib.global.at(\sine).controls

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
			this.instantiated = true;
		}, '/n_go', this.server.addr, argTemplate:[nodeID]).oneShot;

		SystemClock.sched(2, {
			if(this.instantiated.not, {
				oscfunc.free;
			})
		})
	}



	//Queries instantiation of the synth
	/*
	queryInstantiation {
		var oscfunc = OSCFunc({
			arg msg;
			var numChildren = msg[3];
			if(numChildren > 0, {
				this.instantiated = true;
			});
		}, '/g_queryTree.reply', this.server.addr).oneShot;

		server.sendMsg("/g_queryTree", this.group.nodeID);

		SystemClock.sched(2, {
			if(this.instantiated.not, {
				oscfunc.free;
			})
		})
	}
	*/
}
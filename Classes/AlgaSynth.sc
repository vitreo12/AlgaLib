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

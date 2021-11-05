// AlgaLib: SuperCollider implementation of Alga, an interpolating live coding environment.
// Copyright (C) 2020-2021 Francesco Cameli.

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

AlgaParGroup : AbstractGroup {
	*creationCmd { ^ParGroup.creationCmd }
}
// AlgaLib: SuperCollider implementation of Alga, an interpolating live coding environment.
// Copyright (C) 2020-2022 Francesco Cameli.

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

//From: https://github.com/supercollider/supercollider/pull/4801#issuecomment-596066972
//This allows slightly off scheduled entries (due to float precision) to be resynced.
//Simple case to test this is to removoe the debugged comments and try with dur: 1/3
+EventStreamPlayer {
	prNext { arg inTime;
		var nextTime;
		var outEvent = stream.next(event.copy);
		if (outEvent.isNil) {
			streamHasEnded = stream.notNil;
			cleanup.clear;
			this.removedFromScheduler;
			^nil
		}{
			var roundedBeat;
			var deltaFromRounded;
			nextTime = outEvent.playAndDelta(cleanup, muteCount > 0);
			if (nextTime.isNil) { this.removedFromScheduler; ^nil };
			nextBeat = inTime + nextTime;	// inval is current logical beat
			// >>> Glen's fix
			//[inTime.asStringPrec(17), nextBeat.asStringPrec(17)].debug("inTime, nextBeat");
			roundedBeat = nextBeat.round;
			deltaFromRounded = roundedBeat - nextBeat;
			if (deltaFromRounded.abs < 1e-14 and: { deltaFromRounded != 0 }) {
				nextBeat = roundedBeat;
				nextTime = nextTime + deltaFromRounded;
				//nextTime.asStringPrec(17).debug("corrected time");
			};
			// <<< Glen's fix
			^nextTime
		};
	}
}
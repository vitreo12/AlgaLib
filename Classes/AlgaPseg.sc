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

//Like Pseg but stoppable
AlgaPseg : Pstep {
	var <>curves;
	var hold = false;
	var time = 0;

	*new { arg levels, durs = 1, curves = \lin,  repeats = 1 ;
		^super.new(levels, durs, repeats).curves_(curves)
	}

	stop { hold = true }

	embedInStream { arg inval;
		var valStream, durStream, curveStream, startVal, val, dur, curve;
		var env;
		var startTime, curTime;
		repeats.value(inval).do {
			valStream = list.asStream;
			durStream = durs.asStream;
			curveStream = curves.asStream;
			val = valStream.next(inval) ?? {^inval};
			thisThread.endBeat = thisThread.endBeat ? thisThread.beats min: thisThread.beats;
			while {
				startVal = val;
				val = valStream.next(inval);
				dur = durStream.next(inval);
				curve = curveStream.next(inval);

				val.notNil and: { dur.notNil and: { curve.notNil } }
			} {
				startTime = thisThread.endBeat;
				thisThread.endBeat = thisThread.endBeat + dur;
				if (startVal.isArray) {
					env = [startVal,val, dur, curve].flop.collect { | args |
						Env([args[0], args[1]], [args[2]], args[3]) };
					while { thisThread.endBeat > curTime = thisThread.beats } {
						inval = yield(env.collect{ | e |
							time = if(hold, { time } , { e.at(curTime - startTime) });
							e.at(time)
						})
					}
				} {
					env = Env([startVal, val], [dur], curve);
					while { thisThread.endBeat > curTime = thisThread.beats } {
						time = if(hold, { time } , { env.at(curTime - startTime) });
						time.asString.error;
						inval = yield(time);
					}
				}
			}
		};
		^inval
	}
	storeArgs {
		^[list, durs, curves, repeats]
	}
}
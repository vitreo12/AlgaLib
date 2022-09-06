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

//A sequence of AlgaMonoPatterns
AlgaSequencer {
	var <monoPatterns;

	init { | defs, interpTime, interpShape, sched = 1,
		start = true, schedInSeconds = false, tempoScaling = false, player, server |
		var dur;
		if(defs.isEvent.not, {
			"AlgaSequence: 'defs' must be an Event".error;
			^this
		});
		monoPatterns = IdentityDictionary();
		dur = (defs[\dur] ? defs[\delta]) ? 1;
		defs[\dur] = nil; defs[\delta] = nil;
		defs.keysValuesDo({ | key, entry |
			case
			{ entry.isAlgaMonoPattern } {
				monoPatterns[key] = entry
			}
			{ entry.isEvent } {
				entry[\dur] = dur;
				monoPatterns[key] = AlgaMonoPattern(
					def: entry,
					interpTime: interpTime,
					interpShape: interpShape,
					sched: sched,
					start: start,
					schedInSeconds: schedInSeconds,
					tempoScaling: tempoScaling,
					player: player,
					server: server
				);
			};
		});
	}

	*new { | defs, interpTime, interpShape, sched = 1,
		start = true, schedInSeconds = false, tempoScaling = false, player, server |
		^super.new.init(
			defs: defs,
			interpTime: interpTime,
			interpShape: interpShape,
			sched: sched,
			start: start,
			schedInSeconds: schedInSeconds,
			tempoScaling: tempoScaling,
			player: player,
			server: server
		);
	}

	//Indexing
	at { | key |
		^monoPatterns[key]
	}

	//Defaults to \dur
	<< { | sender, param = \dur |
		monoPatterns.do({ | monoPattern |
			monoPattern.from(sender, param)
		});
	}

	//Name collision with Object
	clear { | onClear, time, sched |
		monoPatterns.do({ | monoPattern |
			monoPattern.clear(onClear, time, sched)
		});
	}

	//Alias for stopPattern
	stop { | sched = 0 |
		monoPatterns.do({ | monoPattern |
			monoPattern.stopPattern(sched)
		});
	}

	//Alias for playPattern
	play { | sched = 0 |
		monoPatterns.do({ | monoPattern |
			monoPattern.playPattern(sched)
		});
	}

	//Use . syntax to retrieve entries, dispatch a method or throw error
	doesNotUnderstand { | selector ...args |
		var latestDispatchedVal;

		//Retrieve entry
		var monoPattern = monoPatterns[selector];
		if(monoPattern != nil, { ^monoPattern });

		//OR dispatch method to all entries
		monoPatterns.do({ | monoPattern |
			if(monoPattern.respondsTo(selector), {
				latestDispatchedVal = monoPattern.perform(selector, *args);
			});
		});
		if(latestDispatchedVal != nil, { ^latestDispatchedVal });

		//OR error out
		DoesNotUnderstandError(this, selector, args).throw;
	}
}

//Alias
ASeq : AlgaSequencer {}
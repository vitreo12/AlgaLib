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

//Play and dispatch streams to registered AlgaPatterns
AlgaPatternPlayer {
	var <pattern, <timeInner = 0, <schedInner = 1;
	var <entries;
	var <readers;
	var <algaPatterns;

	*initClass {
		StartUp.add({ this.addAlgaPatternPlayerEventType });
	}

	*addAlgaPatternPlayerEventType {
		Event.addEventType(\algaPatternPlayer, #{
			var algaPatternPlayer = ~algaPatternPlayer;
			var entries = algaPatternPlayer.entries;
			var readers = algaPatternPlayer.readers;
			var algaPatterns = algaPatternPlayer.algaPatterns;

			//Advance entries and readers' pointers
			entries.keysValuesDo({ | key, value |
				//For interpolation, value can be IdentityDictionary(UniqueID -> entry)
			});

			//Dispatch children's triggering
			algaPatterns.do({ | algaPattern |
				algaPattern.advance;
			});
		});
	}

	*new { | def, sched = 1|
		^super.new.init(def, sched)
	}

	init { | argDef, argSched = 1 |
		var patternPairs = Array.newClear;

		readers = IdentityDictionary();
		algaPatterns = IdentitySet();

		//Run parser for what's needed! Is it only AlgaTemps?
		//Parse AlgaTemps like AlgaPattern!

		case
		{ argDef.isArray } {
			patternPairs = patternPairs.add(\type).add(\algaPatternPlayer);
			patternPairs = patternPairs.add(\algaPatternPlayer).add(this);
		}
		{ argDef.isEvent } {
			entries = argDef;
			argDef[\type] = \algaPatternPlayer;
			argDef[\algaPatternPlayer] = this;
			argDef.keysValuesDo({ | key, value |
				patternPairs = patternPairs.add(key).add(value);
			});
		};

		pattern = Pbind(*patternPairs);
		pattern.play;
	}

	at { }

	read { }

	value { }

	//Like AlgaPattern: retriggers at specific sched
	interpolateDur { }

	//This will trigger interpolation on all registered AlgaPatterns
	from { }

	<< { }

	isAlgaPatternPlayer { ^true }
}

//alias
AlgaPlayer : AlgaPatternPlayer {}

//alias
APP : AlgaPatternPlayer {}

//Used under the hood in AlgaPattern to read from an AlgaPatternPlayer
AlgaReader {
	var <entry;

	*new {

	}

	init {

	}

	isAlgaReader { ^true }
}
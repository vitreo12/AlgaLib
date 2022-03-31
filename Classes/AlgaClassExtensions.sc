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

+Object {
	isAlgaNode { ^false }
	isAlgaPattern { ^false }
	isAlgaEffect { ^false }
	isAlgaMod { ^false }
	isAlgaArg { ^false }
	isAlgaNodeOrAlgaArg { ^((this.isAlgaNode).or(this.isAlgaArg)) }
	isAlgaOut { ^false }
	isAlgaTemp { ^false }
	isAlgaStep { ^false }
	isAlgaQuant { ^false }
	isAlgaPatternPlayer { ^false }
	isAlgaReader { ^false }
	isAlgaReaderPfunc { ^false }
	isBuffer { ^false }
	isPattern { ^false }
	isStream { ^false }
	isSymbol { ^false }
	isEvent { ^false }
	isListPattern { ^false }
	isFilterPattern { ^false }
	isTempoClock { ^false }
	isSet { ^false }
	def { ^nil }
	isNumberOrArray { ^((this.isNumber).or(this.isArray)) }

	//AlgaNode / AlgaPattern support
	algaInstantiated { ^true }
	algaInstantiatedAsSender { ^true }
	algaInstantiatedAsReceiver { | param, sender, mix | ^true }
	algaCleared { ^false }
	algaToBeCleared { ^false }
	algaCanFreeSynth { ^false }
	algaAdvance { }
	algaAdvanceArrayScaleValues { }

	//Fallback for AlgaBlock
	blockIndex { ^(-1) }

	//Like asStream, but also converts inner elements of an Array
	algaAsStream { ^(this.asStream) }

	//Fallback on AlgaSpinRoutine if trying to addAction to a non-AlgaScheduler
	addAction { | condition, func, sched = 0, topPriority = false, schedInSeconds = false, preCheck = false |
		AlgaSpinRoutine.waitFor(
			condition:condition,
			func:func
		);
	}

	//Like throw without stacktrace (used in AlgaSynthDef)
	algaThrow {
		if (Error.handling) {
			error("throw during error handling!\n");
			this.dump;
			^this
		};
		thisThread.algaHandleError(this);
	}

	//Parser valid classes to inspect.
	//These can be passed as an Array of Classes.
	algaValidParserClass { | validClasses |
		validClasses = validClasses ? [
			Collection,
			Pattern
		];

		^(
			validClasses.collect({ | class |
				this.isKindOf(class)
			}).includes(true)
		);
	}

	//Loop through every instance variable of an Object and execute func, reassigning the entry
	algaParseObject { | func, validClasses, replace = true |
		if(this.isKindOf(Nil).not, {
			if(this.algaValidParserClass(validClasses), {
				this.slotsDo { | slot |
					var val = this.slotAt(slot);
					var return = if(func.isFunction, { func.(val) }) ? nil;
					if((replace).and(return != nil), {
						try {
							this.slotPut(slot, return)
						}
						{ | error | } //Might return error: ignore such cases
					});
				}
			});
		});
	}
}

//Like handleError without stacktrace
+Thread {
	algaHandleError { | error |
		(exceptionHandler ? parent).algaHandleError(error)
	}
}

//Like handleError without stacktrace
+Function {
	algaHandleError { | error |
		error.errorString.postln;
		this.halt;
	}
}

//Essential for 'c5' / 'a16' busses not to be interpreted as an Array!
+String {
	isNumberOrArray { ^false } //isArray would be true!!
}

//Fix lincurve with .ir arg
+UGen {
	algaLinCurve { arg inMin = 0, inMax = 1, outMin = 0, outMax = 1, curve = -4, clip = \minmax;
		var grow, a, b, scaled, curvedResult;
		if (curve.isNumber and: { abs(curve) < 0.125 }) {
			^this.linlin(inMin, inMax, outMin, outMax, clip)
		};
		grow = exp(curve);
		a = outMax - outMin / (1.0 - grow);
		b = outMin + a;
		scaled = (this.prune(inMin, inMax, clip) - inMin) / (inMax - inMin);

		curvedResult = b - (a * pow(grow, scaled));

		^Select.perform(this.methodSelectorForRate, abs(curve) >= 0.125, [
			this.linlin(inMin, inMax, outMin, outMax, clip),
			curvedResult
		])
	}
}

//Needed for AlgaIEnvGen / IEnvGen to work
+Env {
	//Used in the AlgaSynthDef
	algaAsArray {
		^this.asArrayForInterpolation.unbubble;
	}

	//Used on .set to change Env
	algaConvertEnv {
		^this.asArrayForInterpolation.collect(_.reference).unbubble;
	}
}

//Better than checking .class == Symbol
+Symbol {
	isSymbol { ^true }
}

//Bettern than checking .class == Event
+Event {
	isEvent { ^true }
}

//For Array lincurve
+SequenceableCollection {
	algaLinCurve { arg ... args; ^this.multiChannelPerform('algaLinCurve', *args) }

	//Also converts each inner element to Streams recursively
	algaAsStream {
		this.do({ | entry, i |
			this[i] = entry.algaAsStream
		});
	}

	//Also consider algaCanFreeSynth
	algaCanFreeSynth {
		^this.any({ | item |
			(item.canFreeSynth).or(item.algaCanFreeSynth)
		})
	}
}

//PlayBuf bug with canFreeSynth
+PlayBuf {
	algaCanFreeSynth { ^inputs.at(6).isNumber.not or: { inputs.at(6) > 1 } }
}

+Nil {
    //Fundamental for bug-prone trying to index a nil 'nil[0]'
    //for example when dealing with nested IdentityDictionaries
    at { | index | ^nil }

	//As before (.do is already implemented)
	keysValuesDo { | key, value | ^nil }

	//Needed for scaleCurve (reduce code boilerplate)
	clip { | min, max | ^nil }

	//This avoids many problems when .clearing a node used in connections
	busArg { ^nil }

	//When APP is invalid
	run { }

	//Like handleError without stacktrace
	algaHandleError { | error |
		error.errorString.postln;
		this.halt;
	}
}

+SynthDef {
	//Like .store but without sending to server: algaStore is executed before Alga.boot
	algaStore { | libname=\global, dir(synthDefDir), completionMsg, mdPlugin |
		var lib = SynthDescLib.getLib(libname);
		var file, path = dir ++ name ++ ".scsyndef";
		if(metadata.falseAt(\shouldNotSend)) {
			protect {
				var bytes, desc;
				file = File(path, "w");
				bytes = this.asBytes;
				file.putAll(bytes);
				file.close;
				lib.read(path);
				desc = lib[this.name];
				desc.metadata = metadata;
				SynthDesc.populateMetadataFunc.value(desc);
				desc.writeMetadata(path, mdPlugin);
			} {
				file.close
			}
		} {
			lib.read(path);
			lib.servers.do { arg server;
				this.loadReconstructed(server, completionMsg);
			};
		};
	}
}

+Set {
	isSet { ^true }
}

+Dictionary {
	//Loop over a Dict, unpacking IdentitySet.
	//It's used in AlgaBlock to unpack inNodes of an AlgaNode
	nodesLoop { | function |
		this.keysValuesDo({
			arg key, value, i;
			if(value.isSet, {
				value.do({ | entry |
					function.value(entry, i);
				});
			}, {
				function.value(value, i);
			});
		});
	}
}

+Pattern {
	isPattern { ^true }

	playAlgaRescheduling { | clock, protoEvent, quant |
		clock = clock ? TempoClock.default;
		^AlgaReschedulingEventStreamPlayer(
			this.asStream,
			protoEvent
		).play(clock, false, quant)
	}
}

+Stream {
	isStream { ^true }
}

+ListPattern {
	isListPattern { ^true }
}

+FilterPattern {
	isFilterPattern { ^true }
}

//List extensions are used in AlgaScheduler to manage actions
+List {
	//object equality!
	indexOf { | entry |
		this.do({ | item, i |
			if(item === entry, { ^i });
		});
		^nil;
	}

	insertAfterEntry { | entry, offset, what |
		var index = this.indexOf(entry);
		if(index != nil, {
			index = index + 1 + offset;
			if(index < this.size, {
				this.insert(index, what);
			}, {
				this.add(what);
			});
		}, {
			//If nil entry, just add at bottom
			this.add(what);
		});
	}

	removeAtEntry { | entry |
		var index = this.indexOf(entry);
		if(index != nil, {
			this.removeAt(index);
		});
	}
}

//Add support for >> and >>+
+Number {

}

//Add support for >> and >>+
+SequenceableCollection {

}

//Add support for >> and >>+
+Buffer {
	isBuffer { ^true }
}

+Clock {
	*tempo { ^(1.0) }

	tempo { ^(1.0) }

	algaSchedAtQuant { | quant, task |
		if(this.isTempoClock, {
			this.algaTempoClockSchedAtQuant(quant, task);
		}, {
			this.algaSched(quant, task)
		});
	}

	algaSched { | when, task |
		//if(this.isTempoClock, { "TempoClock.sched will schedule after beats, not time!".warn; });
		this.sched(when, task);
	}

	algaSchedAtQuantOnce { | quant, task |
		var taskOnce = { task.value; nil };
		if(this.isTempoClock, {
			this.algaTempoClockSchedAtQuant(quant, taskOnce);
		}, {
			this.algaSched(quant, taskOnce)
		});
	}

	algaSchedOnce { | when, task |
		var taskOnce = { task.value; nil };
		//if(this.isTempoClock, { "TempoClock.sched will schedule after beats, not time!".warn; });
		this.sched(when, taskOnce);
	}

	algaSchedAtQuantWithTopPriority { | quant, task |
		if(this.isTempoClock, {
			this.algaTempoClockSchedAtQuantWithTopPriority(quant, task);
		}, {
			"Clock is not a TempoClock. Can't schedule with top priority".warn;
			this.algaSched(quant, task)
		});
	}

	algaSchedWithTopPriority { | when, task |
		if(this.isTempoClock, {
			this.algaTempoClockSchedWithTopPriority(when, task);
		}, {
			"Clock is not a TempoClock. Can't schedule with top priority".warn;
			this.algaSched(when, task)
		});
	}

	algaSchedAtQuantOnceWithTopPriority { | quant, task |
		var taskOnce = { task.value; nil };
		if(this.isTempoClock, {
			this.algaTempoClockSchedAtQuantWithTopPriority(quant, taskOnce);
		}, {
			"Clock is not a TempoClock. Can't schedule with top priority".warn;
			this.algaSched(quant, taskOnce)
		});
	}

	algaSchedOnceWithTopPriority { | when, task |
		var taskOnce = { task.value; nil };
		if(this.isTempoClock, {
			this.algaTempoClockSchedWithTopPriority(when, taskOnce);
		}, {
			"Clock is not a TempoClock. Can't schedule with top priority".warn;
			this.algaSched(when, taskOnce)
		});
	}

	algaSchedInSeconds { | when, task |
		this.schedAbs(this.secs2beats(this.seconds + when), task);
	}

	algaSchedInSecondsOnce { | when, task |
		var taskOnce = { task.value; nil };
		this.schedAbs(this.secs2beats(this.seconds + when), taskOnce);
	}

	algaSchedInSecondsWithTopPriority { | when, task |
		if(this.isTempoClock, {
			this.algaTempoClockSchedInSecondsWithTopPriority(when, task);
		}, {
			"Clock is not a TempoClock. Can't schedule with top priority".warn;
			this.algaSchedInSeconds(when, task);
		});
	}

	algaSchedInSecondsOnceWithTopPriority { | when, task |
		var taskOnce = { task.value; nil };
		if(this.isTempoClock, {
			this.algaTempoClockSchedInSecondsWithTopPriority(when, taskOnce);
		}, {
			"Clock is not a TempoClock. Can't schedule with top priority".warn;
			this.algaSchedInSeconds(when, taskOnce);
		});
	}
}

+SystemClock {
	//If using the SystemClock in AlgaScheduler, just schedule as if quant is time
	*algaSchedAtQuantOnce { | quant, task |
		var taskOnce = { task.value; nil };
		this.sched(quant, taskOnce)
	}

	//If using the SystemClock in AlgaScheduler, just schedule as if quant is time
	*algaSchedAtQuantOnceWithTopPriority { | quant, task |
		var taskOnce = { task.value; nil };
		"SystemClock is not a TempoClock. Can't schedule with top priority".warn;
		this.sched(quant, taskOnce)
	}
}

+TempoClock {
	algaTempoClockSchedAtQuant { | quant = 1, task |
		var time;

		case
		//Beat syncing
		{ quant.isNumber } {
			if(quant < 1, {
				//Sync to next availbale grid time
				//quant = 1.25
				//this.beats = 43.2345
				//time = 44.25
				time = this.nextTimeOnGrid(quant)
			}, {
				//Sync to beats in the future
				//quant = 1.25
				//this.beats = 43.62345
				//time = 44.25
				time = this.beats.floor + quant;
			});
		}
		//Bar syncing
		{ quant.isAlgaQuant } {
			var nextBar = this.nextBar;
			var beatsPerBar = this.beatsPerBar;
			var algaQuantQuant = quant.quant;
			var algaQuantWrapPhase = quant.wrapPhase;
			var algaQuantPhase = if(algaQuantWrapPhase,
				{ quant.phase % beatsPerBar },
				{ quant.phase }
			);

			//Sync to the next available bar, shifting by phase - within the bar if wrapping
			time = (nextBar + ((algaQuantQuant - 1) * beatsPerBar)) + algaQuantPhase;
		};

		if(time != nil, { this.schedAbs(time, task) });
	}

	algaTempoClockSchedAtQuantWithTopPriority { | quant, task |
		//add to clock
		this.algaTempoClockSchedAtQuant(quant, task);

		//schedule it at top priority
		this.algaTempoClockSchedAtTopPriority(task);
	}

	algaTempoClockSchedWithTopPriority { | when, task |
		//add to clock
		this.algaSched(when, task);

		//schedule it at top priority
		this.algaTempoClockSchedAtTopPriority(task);
	}

	algaTempoClockSchedInSecondsWithTopPriority { | when, task |
		//add to clock
		this.algaSchedInSeconds(when, task);

		//schedule it at top priority
		this.algaTempoClockSchedAtTopPriority(task);
	}

	algaTempoClockSchedAtTopPriority { | task |
		//indices for each of the unique times
		var indices = IdentityDictionary();

		//entries at unique times
		var entries = IdentityDictionary();

		//loop over the queue, it appears just like a PriorityQueue object
		forBy(1, queue.size-1, 3) { | i |
			var currentTime  = queue[i];
			var currentEntry = queue[i + 1]; //entries are at + 1 position. queue.postln if in doubt

			//update the entries
			if(indices[currentTime] == nil, {
				indices[currentTime] = Array().add(i);
				entries[currentTime] = Array().add(currentEntry);
			}, {
				indices[currentTime] = indices[currentTime].add(i);
				entries[currentTime] = entries[currentTime].add(currentEntry);
			});

			//task will always be the last entry: it's just been added.
			//test for object equality (must be same exact entry!)
			if(currentEntry === task, {
				//Find the first occurrence of this time
				var firstTimeIndex = indices[currentTime][0];

				//if not first occurance, push the entries back
				if(firstTimeIndex != i, {
					var entriesSize = entries[currentTime].size;

					//Order the entries by pushing the last entry, task, to the first index
					entries[currentTime] = entries[currentTime].move(
						entriesSize - 1,
						0
					);

					//Now re-order the queue entries according to the correct index / entry pairs
					indices[currentTime].do({ | index, y |
						queue[index + 1] = entries[currentTime][y];
					});
				});
			});
		}
	}

	isTempoClock { ^true }
}


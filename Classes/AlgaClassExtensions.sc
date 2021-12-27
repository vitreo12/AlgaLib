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

+Object {
	isAlgaNode { ^false }
	isAlgaPattern { ^false }
	isAlgaArg { ^false }
	isAlgaOut { ^false }
	isAlgaTemp { ^false }
	isBuffer { ^false }
	isPattern { ^false }
	isStream { ^false }
	isSymbol { ^false }
	isEvent { ^false }
	isListPattern { ^false }
	isTempoClock { ^false }
	def { ^nil }
	isNumberOrArray { ^((this.isNumber).or(this.isArray)) }

	//AlgaNode / AlgaPattern support
	algaInstantiated { ^true }
	algaInstantiatedAsSender { ^true }
	algaInstantiatedAsReceiver { | param, sender, mix | ^true }
	algaCleared { ^false }
	algaToBeCleared { ^false }
	algaCanFreeSynth { ^false }

	//Like asStream, but also converts inner elements of an Array
	algaAsStream { ^(this.asStream) }

	//Fallback on AlgaSpinRoutine if trying to addAction to a non-AlgaScheduler
	addAction { | condition, func, sched = 0 |
		if(sched != 0, {
			"AlgaSpinRoutine: sched is not a valid argument".error;
		});

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

//Essential for 'a16' busses not to be interpreted as an Array!
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

+Dictionary {
	//Loop over a Dict, unpacking IdentitySet.
	//It's used in AlgaBlock to unpack inNodes of an AlgaNode
	nodesLoop { | function |
		this.keysValuesDo({
			arg key, value, i;
			if(value.class == IdentitySet, {
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
	algaSchedAtQuant { | quant, task |
		if(this.isTempoClock, {
			this.algaTempoClockSchedAtQuant(quant, task);
		}, {
			this.algaSched(quant, task)
		});
	}

	algaSched { | when, task |
		if(this.isTempoClock, { "TempoClock.sched will schedule after beats, not time!".warn; });
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
		if(this.isTempoClock, { "TempoClock.sched will schedule after beats, not time!".warn; });
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
		// Below one is beat-timing. Sync to the closest one
		var time;
		if(quant < 1, {
			time = this.nextTimeOnGrid(quant)
		}, {
			// Above one is bar-timing. Sync to closest one depending on current beat time
			// quant = 1.25
			// this.beats = 43.2345
			// time = 44.25

			// should it be .ceil for this case ?
			// quant = 1.25
			// this.beats = 43.62345
			// time = 44.25

			time = this.beats.floor + quant;
		});
		this.schedAbs(time, task)
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
						entriesSize - 1, 0
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


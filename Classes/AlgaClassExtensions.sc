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
	isAlgaMonoPattern { ^false }
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
	isAlgaPseg { ^false }
	isBuffer { ^false }
	isPattern { ^false }
	isStream { ^false }
	isSymbol { ^false }
	isEvent { ^false }
	isListPattern { ^false }
	isFilterPattern { ^false }
	isTempoClock { ^false }
	isSet { ^false }
	isLiteralFunction { ^false }
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

	//Like asStream, but easily overloadable
	algaAsStream { ^(this.asStream) }

	//Like asStream, but easily overloadable
	algaNext { | e | ^this.next(e) }

	//Fallback on AlgaSpinRoutine if trying to addAction to a non-AlgaScheduler
	addAction { | condition, func, sched = 0, topPriority = false,
		schedInSeconds = false, preCheck = false |
		AlgaSpinRoutine.waitFor(
			condition: condition,
			func: func
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

	//Check env
	algaCheckValidEnv { | algaIEnvGen = true, server |
		var levels, times;

		if(this == nil, { ^nil });

		if(this.isKindOf(Env).not, {
			("Alga: invalid interpShape: " ++ this.class).error;
			^nil
		});

		levels = this.levels;
		if(algaIEnvGen, {
			if(levels.size > AlgaStartup.maxEnvPoints, {
				("Alga: interpShape's Env can only have up to " ++ AlgaStartup.maxEnvPoints ++ " points.").error;
				^nil
			});
		});
		if(levels.first != 0, {
			("Alga: interpShape's Env must always start from 0").error;
			^nil
		});
		if(levels.last != 1, {
			("Alga: interpShape's Env must always end at 1").error;
			^nil
		});
		levels.do({ | level |
			if(((level >= 0.0).and(level <= 1.0)).not, {
				("Alga: interpShape's Env can only contain values between 0 and 1").error;
				^nil
			});
		});

		times = this.times;
		if(times.sum == 0, {
			("Alga: interpShape's Env cannot have its times sum up to 0").error;
			^nil
		});

		//Add to library and push the Buffer
		if(algaIEnvGen, {
			server = server ? Server.default;
			AlgaDynamicEnvelopes.add(this, server)
		});

		^this
	}

	//To be overloaded
	algaResetParsingVars { }

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
	algaParseObject { | func, validClasses, replace = false |
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

	//Wrapper around Dictionary.loadSamples
	loadSamples { | server |
		^Dictionary.loadSamples(this, server)
	}
}

//Wrapper around Dictionary.loadSamples
+PathName {
	loadSamples { | server |
		^Dictionary.loadSamples(this, server)
	}
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
	//Used for AlgaMonoPattern's shape. asStream is already parsing the Env instead
	algaAsStream { ^this }

	//Used in the AlgaSynthDef
	algaAsArray {
		^this.asArrayForInterpolation.unbubble;
	}

	//Used on .set to change Env
	algaConvertEnv {
		^this.asArrayForInterpolation.collect(_.reference).unbubble;
	}

	//Used for dur interpolation
	asAlgaPseg { | time, clock, onDone |
		var c = if(curves.isSequenceableCollection.not) { curves } { Pseq(curves) };
		^AlgaPseg(
			levels: Pseq(levels ++ 1),
			durs: Pseq((times.normalizeSum * time) ++ [inf]),
			curves: c,
			clock: clock,
			onDone: onDone
		)
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

//Reverse items of an OrderedIdentitySet
+OrderedIdentitySet {
	reverse {
		if(items.isSequenceableCollection, {
			items = items.reverse
		})
	}
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

	//Support .next for [Pattern, Pattern, etc...]
	algaNext { | e |
		var result = Array.newClear(this.size);
		this.do { | entry, i | result[i] = entry.algaNext(e) };
		^result
	}
}

//Converts all Set entries to stream
+Set {
	isSet { ^true }

	algaAsStream {
		this.do({ | entry |
			this.remove(entry);
			this.add(entry.algaAsStream)
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

	//When group is invalid
	moveToHead { "Calling 'moveToHead' for nil.".error }
	moveToTail { "Calling 'moveToTail' for nil.".error }

	//Like handleError without stacktrace
	algaHandleError { | error |
		error.errorString.postln;
		this.halt;
	}
}

+SynthDef {
	//Like .store but without sending to server: algaStore is executed before Alga.boot
	algaStore { | libname=\alga, dir(synthDefDir), completionMsg, mdPlugin |
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

//Read all SynthDefs in a path recursively
+SynthDescLib {
	readAllInner { | path, server, beginsWithExclude = "IO" |
		var strPath = path.fullPath.withoutTrailingSlash;
		this.readDef(strPath, server);
		path.folders.do({ | folder |
			var folderName = folder.folderName.asString;
			if(folderName.beginsWith(beginsWithExclude).not, {
				this.readAllInner(folder, server, beginsWithExclude);
			});
		});
	}

	readAll { | path, server, beginsWithExclude = "IO" |
		var strPath;
		server = server ? Server.default;
		path = path ? SynthDef.synthDefDir; //Defaults to SC one. Alga will use AlgaSynthDefs instead
		beginsWithExclude = beginsWithExclude ? "";
		beginsWithExclude = beginsWithExclude.withoutTrailingSlash;
		if(path.isString, {
			path = PathName(path.standardizePath)
		});
		if(path.isKindOf(PathName).not, {
			"SynthDescLib: Path must be a String or PathName".error;
			^nil;
		});
		strPath = path.fullPath.withoutTrailingSlash;
		path = PathName(strPath);
		if((File.exists(strPath).not).or(path.isFolder.not), {
			"SynthDescLib: Path does not exist or it's not a folder".error;
			^nil;
		});
		this.readAllInner(path, server, beginsWithExclude);
	}

	algaRead { | path, beginsWithExclude = "IO" |
		this.readAll(path, beginsWithExclude)
	}

	readDefInner { | file, server |
		var name = file.fileNameWithoutExtension;
		//Read algaPattern and algaPatternTempOut too
		if((file.extension == "scsyndef").and(
			(name.endsWith("_algaPattern").or(name.endsWith("_algaPatternTempOut"))).not), {
			var mdFile = file.pathOnly ++ name ++ ".scsyndefmd";
			var algaPatternFile = file.pathOnly ++ name ++ "_algaPattern.scsyndef";
			var algaPatternTempOutFile = file.pathOnly ++ name ++ "_algaPatternTempOut.scsyndef";
			var algaPatternFileExists = File.exists(algaPatternFile);
			var algaPatternTempOutFileExists = File.exists(algaPatternTempOutFile);

			//Read scsyndef
			this.read(file.fullPath);

			//Read algaPatternFile and algaPAtternTempOutFile
			if(algaPatternFileExists, { this.read(algaPatternFile) });
			if(algaPatternTempOutFileExists, { this.read(algaPatternTempOutFile) });

			//Send to server too
			if(server.isKindOf(Server), {
				if(server.serverRunning, {
					server.sendMsg("/d_load", file.fullPath);
					if(algaPatternFileExists, {
						server.sendMsg("/d_load", algaPatternFile)
					});
					if(algaPatternTempOutFileExists, {
						server.sendMsg("/d_load", algaPatternTempOutFile)
					});
				});
			});

			//Read md file once
			if(File.exists(mdFile), {
				var synthDesc = this[name.asSymbol];
				if(synthDesc != nil, {
					synthDesc.def = Object.readArchive(mdFile)
				});
			});
		});
	}

	readDef { | path, server |
		server = server ? Server.default;
		if(path.isString, {
			path = PathName(path.standardizePath.withoutTrailingSlash);
		});
		if(path.isKindOf(PathName).not, {
			"path must be a String or PathName".error;
			^nil;
		});
		case
		{ path.isFolder } {
			path.files.do({ | file |
				this.readDefInner(file, server)
			});
		}
		{ path.isFile } {
			this.readDefInner(path, server);
		};
	}

	algaReadDef { | path, server |
		this.readDef(path, server)
	}

	*alga {
		^this.getLib(\alga)
	}
}

+Dictionary {
	//loadSamples implementation
	*loadSamplesInner { | path, dict, server, folderCount, post = true |
		var folderName = path.fileName.asSymbol;
		var newDict;

		//Inner calls
		if(folderCount != nil, {
			newDict = this.new();
			dict[folderCount.asSymbol] = newDict;
			dict[folderName] = newDict;
			if(post, {
				("\n- " ++ folderCount ++ ": " ++ path.folderName ++
					"/" ++ folderName ++ "/ \n").postln;
			});
		}, {
			//First call: use top dict
			newDict = dict;
			if(post, {
				("\n- " ++ folderName ++ "/ \n").postln;
			});
		});

		if(path.files.size > 0, {
			//Not filesDo, which would be recursive. Recursiveness is already handled
			path.files.do({ | file, i |
				var fileName = file.fileName.asString;
				var fileNameNoExt = file.fileNameWithoutExtension;
				var fileNameNoExtSym = fileNameNoExt.asSymbol;
				if((file.extension == "wav").or(file.extension == "aiff"), {
					var buffer = Buffer.read(server, file.fullPath);

					if(post, {
						(i.asString ++ ": '" ++ fileName ++ "'").postln;
					});

					//Symbol with no extension ( \kick )
					newDict[fileNameNoExtSym] = buffer;
					//Int index ( 0, 1, etc... )
					newDict[i] = buffer;
					//Full string name with extension ( "kick" )
					newDict[fileName] = buffer;
					//Full string name with no extension ( "kick.wav" )
					newDict[fileNameNoExt] = buffer;
				});
			});
		});

		path.folders.do({ | folder, i |
			folder = PathName(folder.fullPath.withoutTrailingSlash);
			this.loadSamplesInner(folder, newDict, server, i, post: post)
		});
	}

	//Load samples of a path to a dict, recursively
	*loadSamples { | path, server, post = false |
		server = server ? Server.default;
		if(server.serverRunning, {
			var dict = this.new();
			var strPath;
			if(path.isString, {
				path = PathName(path.standardizePath)
			});
			if(path.isKindOf(PathName).not, {
				"path must be a String or PathName".error;
				^nil;
			});
			strPath = path.fullPath.withoutTrailingSlash;
			path = PathName(strPath);
			if((File.exists(strPath).not).or(path.isFolder.not), {
				"Path does not exist or it's not a folder".error;
				^nil;
			});
			fork {
				"Loading...".postln;
				this.loadSamplesInner(path, dict, server, post: post);
				server.sync;
				"Done!".postln;
			};
			^dict;
		}, {
			"Server is not running. Cannot load samples.".warn
			^nil
		});
	}

	//Load samples on an already declared dict
	loadSamples { | path, server, post = false |
		server = server ? Server.default;
		if(server.serverRunning, {
			var strPath;
			if(path.isString, {
				path = PathName(path.standardizePath)
			});
			if(path.isKindOf(PathName).not, {
				"path must be a String or PathName".error;
				^this;
			});
			strPath = path.fullPath.withoutTrailingSlash;
			path = PathName(strPath);
			if((File.exists(strPath).not).or(path.isFolder.not), {
				"Path does not exist or it's not a folder".error;
				^this;
			});
			Dictionary.loadSamplesInner(path, this, server, post: post);
		}, {
			"Server is not running. Cannot load samples.".warn
			^this
		});
	}

	//Free all samples
	freeSamples {
		this.keysValuesDo({ | key, value |
			if(value.isKindOf(Dictionary), { value.freeSamples });
			if(value.isBuffer, { value.free });
			this.removeAt(key);
		});
	}

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

	//Convert all dict entries to streams
	algaAsStream {
		this.keysValuesDo({ | key, entry |
			this[key] = entry.algaAsStream
		});
	}
}

+Pattern {
	isPattern { ^true }
}

+Stream {
	isStream { ^true }

	newAlgaReschedulingEventStreamPlayer { | event  |
		^AlgaReschedulingEventStreamPlayer(this, event)
	}
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

	//Same as sendCollection but force fork (instead of forkIfNeeded).
	//forkIfNeeded is buggy if used within AlgaPattern!
	*algaSendCollection { arg server, collection, numChannels = 1, wait = -1, action;
		var buffer = this.new(server, ceil(collection.size / numChannels), numChannels);
		fork {
			buffer.alloc;
			server.sync;
			buffer.sendCollection(collection, 0, wait, action);
		}
		^buffer
	}

	//To debug .sendCollection used for Envs
	/*
	streamCollection { arg collstream, collsize, startFrame = 0, wait = -1, action;
		var bundsize, pos;
		// wait = -1 allows an OSC roundtrip between packets
		// wait = 0 might not be safe in a high traffic situation
		// maybe okay with tcp
		pos = collstream.pos;
		while { pos < collsize } {
			var msg = ['/b_setn', bufnum, pos + startFrame, bundsize]
				++ Array.fill(bundsize, { collstream.next });
			msg.asString.error;
			// 1626 max size for setn under udp
			bundsize = min(1626, collsize - pos);
			server.listSendMsg(msg);
			pos = collstream.pos;
			//if(wait >= 0) { wait.wait } { server.sync };
		};
	}
	*/
}

+Clock {
	*tempo { ^(1.0) }

	*tempo_ { }

	tempo { ^(1.0) }

	tempo_ { }

	//SystemClock, just do no error out
	*interpolateTempo { }
	*interpTempo { }

	//Only works for TempoClock / LinkClock
	interpolateTempo { | tempo = 1, time = 0, shape,
		delta = 0.1, schedInSeconds = false, sched = 1 |

		//Stop previous routine
		var newRoutine;
		var previousRoutine;
		var algaTempoRoutines = Alga.interpTempoRoutines;
		if(algaTempoRoutines == nil, {
			"Alga: Clock is not being used by Alga. Cannot run 'interpTempo' on it".error;
			^this
		});
		previousRoutine = algaTempoRoutines[this];
		if(previousRoutine != nil, { previousRoutine.stop });

		//Check tempo
		if(tempo.isNumber.not, {
			"Alga: 'tempo' must be a number".error;
			^this
		});

		//Check shape
		shape = shape.algaCheckValidEnv(false) ? Env([0, 1], 1);

		//If time is 0 or less than delta, just set tempo on sched
		if((time == 0).or(time <= delta), {
			if(schedInSeconds, {
				if(sched.isAlgaQuant, { sched = sched.quant + sched.phase });
				this.algaSchedInSecondsOnceWithTopPriority(sched, {
					this.tempo = tempo
				});
			}, {
				this.algaSchedAtQuantOnceWithTopPriority(sched, {
					this.tempo = tempo
				});
			});
		});

		//Finally, define the new routine
		newRoutine = Routine({
			var counter = 0;
			var done = false;
			var timesSum = shape.times.sum;
			var startTempo = this.tempo; //Needs to be locked on Routine start
			var timeInv = time.reciprocal; //1 / time

			//Advance time and retrieve values from the Env
			while { done.not } {
				var envVal = shape[counter * timesSum];
				this.tempo = startTempo.blend(tempo, envVal);
				counter = counter + (delta * timeInv);
				if(counter >= (1.0 + (delta * timeInv)), { done = true });
				delta.wait;
			}
		});

		//Assign the routine to Alga's dict
		Alga.interpTempoRoutines[this] = newRoutine;

		//If sched is 0, just start right now
		if(sched == 0, {
			newRoutine.play(clock: SystemClock);
			^this;
		});

		//Schedule the playing for the right sched value
		if(schedInSeconds, {
			if(sched.isAlgaQuant, { sched = sched.quant + sched.phase });
			this.algaSchedInSecondsOnceWithTopPriority(sched, {
				newRoutine.play(clock: SystemClock)
			});
		}, {
			this.algaSchedAtQuantOnceWithTopPriority(sched, {
				newRoutine.play(clock: SystemClock)
			});
		});
	}

	//Alias
	interpTempo { | tempo = 1, time = 0, shape,
		delta = 0.1, schedInSeconds = false, sched = 1 |
		^this.interpolateTempo(
			tempo: tempo,
			time: time,
			shape: shape,
			delta: delta,
			schedInSeconds: schedInSeconds,
			sched: sched
		)
	}

	algaGetScheduledTimeInSeconds { | seconds = 0 |
		^(this.secs2beats(this.seconds + seconds))
	}

	algaSchedNum { | quant = 1 |
		if(quant < 1, {
			//Sync to next availbale grid time
			//quant = 1.25
			//this.beats = 43.2345
			//time = 44.25
			^(this.nextTimeOnGrid(quant));
		}, {
			//Sync to beats in the future
			//quant = 1.25
			//this.beats = 43.62345
			//time = 44.25
			^(this.beats.floor + quant);
		});
	}

	algaGetScheduledTime { | quant = 1 |
		case
		//Beat syncing
		{ quant.isNumber } {
			^this.algaSchedNum(quant)
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
			if(algaQuantQuant > 0, {
				//Sync to the next available bar, shifting by phase - within the bar if wrapping
				^((nextBar + ((algaQuantQuant - 1) * beatsPerBar)) + algaQuantPhase);
			}, {
				//quant = 0: sched just with phase
				^(this.algaSchedNum(algaQuantPhase))
			});
		};
	}

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
		this.schedAbs(this.algaGetScheduledTimeInSeconds(when), task);
	}

	algaSchedInSecondsOnce { | when, task |
		var taskOnce = { task.value; nil };
		this.schedAbs(this.algaGetScheduledTimeInSeconds(when), taskOnce);
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
		var time = this.algaGetScheduledTime(quant);
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


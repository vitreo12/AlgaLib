+Object {
	isAlgaNode { ^false }
	isAlgaPattern { ^false }
	isPattern { ^false }
	isListPattern { ^false }
	isTempoClock { ^false }
	isNumberOrArray { ^((this.isNumber).or(this.isSequenceableCollection)) }

	//AlgaNode / AlgaPattern support
	algaInstantiated { ^true }
	algaCleared { ^false }
	algaToBeCleared { ^false }

	//Fallback on AlgaSpinRoutine if trying to addAction to a non-AlgaScheduler
	addAction { | condition, func, sched = 0 |
		if(sched > 0, {
			"AlgaSpinRoutine: sched is not a valid argument".error;
		});

		AlgaSpinRoutine.waitFor(
			condition:condition,
			func:func
		);
	}
}

+Dictionary {
	//Loop over a Dict, unpacking Set. It's used in AlgaBlock to unpack inNodes of an AlgaNode
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

	playRescheduling { | clock, protoEvent, quant |
		clock = clock ? TempoClock.default;
		^ReschedulingEventStreamPlayer(this.asStream, protoEvent)
		.play(clock, false, quant)
	}
}

+ListPattern {
	isListPattern { ^true }
}

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

//default Pseq to inf
+Pseq {
	*new { arg list, repeats=inf, offset=0;
		^super.new(list, repeats).offset_(offset)
	}
}

//Add support for >> and >>+
+Pattern {

}

//Add support for >> and >>+
+Number {

}

//Add support for >> and >>+
+SequenceableCollection {

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

	algaSchedOnceAtQuant { | quant, task |
		var taskOnce = {task.value; nil};
		if(this.isTempoClock, {
			this.algaTempoClockSchedAtQuant(quant, taskOnce);
		}, {
			this.algaSched(quant, taskOnce)
		});
	}

	algaSchedOnce { | when, task |
		var taskOnce = {task.value; nil};
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

	algaSchedOnceAtQuantWithTopPriority { | quant, task |
		var taskOnce = {task.value; nil};
		if(this.isTempoClock, {
			this.algaTempoClockSchedAtQuantWithTopPriority(quant, taskOnce);
		}, {
			"Clock is not a TempoClock. Can't schedule with top priority".warn;
			this.algaSched(quant, taskOnce)
		});
	}

	algaSchedOnceWithTopPriority { | when, task |
		var taskOnce = {task.value; nil};
		if(this.isTempoClock, {
			this.algaTempoClockSchedWithTopPriority(when, taskOnce);
		}, {
			"Clock is not a TempoClock. Can't schedule with top priority".warn;
			this.algaSched(when, taskOnce)
		});
	}
}

+TempoClock {
	algaTempoClockSchedAtQuant { | quant = 1, task |
		this.schedAbs(quant.nextTimeOnGrid(this), task)
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

		//loop over the queue
		forBy(1, queue.size-1, 3) { | i |
			var currentEntry = queue[i + 1];
			var currentTime  = queue[i];

			//collect times, only on first occurence
			if(indices[currentTime] == nil, {
				indices[currentTime] = Array().add(i);
				entries[currentTime] = Array().add(currentEntry);
			}, {
				indices[currentTime] = indices[currentTime].add(i);
				entries[currentTime] = entries[currentTime].add(currentEntry);
			});

			//task will always be the last entry: it's just been added.
			//test for EXACT equality (same function!)
			if(currentEntry === task, {
				//Find the first occurrence of this time
				var firstTimeIndex = indices[currentTime][0];

				//if not first occurance, push the entries back
				if(firstTimeIndex != i, {
					var entriesSize = entries[currentTime].size;

					//Order the entries by pushing them back
					entries[currentTime] = entries[currentTime].move(
						entriesSize - 1, 0
					);

					//Put them back in the queue, ordered, at the right indices
					indices[currentTime].do({ | index, y |
						queue[index + 1] = entries[currentTime][y];
					});
				});
			});
		}
	}

	isTempoClock { ^true }
}

//Debug purposes (used in the s.bind calls in AlgaScheduler)
+BundleNetAddr {
	closeBundle { arg time;
		var bundleList, lastBundles;
		if(time != false) {
			if(async.not) {
				if(AlgaScheduler.verbose, {
					("Server: latency: " ++ time).warn;
					("Server: msg bundle: " ++ bundle).warn;
				});
				saveAddr.sendClumpedBundles(time, *bundle);
				^bundle;
			};

			forkIfNeeded {
				bundleList = this.splitBundles(time);
				lastBundles = bundleList.pop;
				bundleList.do { |bundles|
					var t = bundles.removeAt(0);
					saveAddr.sync(nil, bundles, t); // make an independent condition.
				};
				saveAddr.sendClumpedBundles(*lastBundles);  // time ... args
			}
		};
		^bundle
	}
}

/*
//Just as schedBundleArrayOnClock, but it also supports array of array bundles.
//This is used for AlgaPatterns in order to send all synths together in a single bundle
+SequenceableCollection {
	algaSchedBundleArrayOnClock { | clock, bundleArray, server, latency, lag = 0 |

		// "this" is an array of delta times for the clock (usually in beats)
		// "lag" is a value or an array of tempo independent absolute lag times (in seconds)

		var sendBundle;

		latency = latency ? server.latency;

		sendBundle = { |i|
			//this could either be an array of array, or just array.
			//the star makes sure of "unpacking" things to send correctly!
			var bundle = bundleArray.wrapAt(i);
			server.algaSendClumpedBundle(latency, *bundle) //this star here fixes it all!
		};

		if(lag == 0, {
			this.do({ |delta, i|
				if(delta != 0, {
					// schedule only on the clock passed in
					clock.sched(delta, { sendBundle.value(i) })
				}, {
					// send directly
					sendBundle.value(i)
				});
			});
		}, {
			lag = lag.asArray;

			this.do({ |delta, i|
				if(delta != 0, {
					// schedule on both clocks
					clock.sched(delta, {
						SystemClock.sched(lag.wrapAt(i), { sendBundle.value(i) })
					})
				}, {
					// schedule only on the system clock
					SystemClock.sched(lag.wrapAt(i), { sendBundle.value(i) })
				});
			});
		});
	}
}

//This is used for AlgaPatterns in order to send all synths together in a single bundle
+Server {
	algaSendClumpedBundle { | time ... msgs |
		if(AlgaScheduler.verbose, {
			("Server: latency: " ++ time).warn;
			("Server: msg bundle: " ++ msgs).warn;
		});

		addr.sendClumpedBundles(time, *msgs); //Better than sendBundle, as it checks for msg size!
	}
}
*/
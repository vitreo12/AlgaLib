+Dictionary {
	//Loop over a Dict, unpacking Set. It's used in AlgaBlock
	//to unpack inNodes of an AlgaNode
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

+Object {
	isAlgaNode { ^false }
	instantiated { ^true }
	isNumberOrArray { ^((this.isNumber).or(this.isSequenceableCollection)) }
}

//Just as schedBundleArrayOnClock, but it also supports array of array bundles
+ SequenceableCollection {
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

+ Server {
	algaSendClumpedBundle { | time ... msgs |
		if(AlgaScheduler.verbose, {
			("Server: latency: " ++ time).warn;
			("Server: msg bundle: " ++ msgs).warn;
		});

		addr.sendClumpedBundles(time, *msgs); //Better than sendBundle, as it checks for msg size!
	}
}
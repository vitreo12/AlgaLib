//Connection rules for AlgaNodeProxySynth
+ AlgaNodeProxy {

	synth_connectToInterpProxy {
		//Pass interpolationProxy as argument to save CPU cycles of retrieving it from dict
		arg param = \in, interpolationProxy = nil, prevProxy, useXSet = true;

		var controlName, paramRate, paramNumberOfChannels, canBeMapped;
		var prevProxyRate, isPrevProxyAProxy, prevProxyNumChannels;

		isPrevProxyAProxy = (prevProxy.class == AlgaNodeProxy).or(
			prevProxy.class.superclass == AlgaNodeProxy).or(
			prevProxy.class.superclass.superclass == AlgaNodeProxy);

		if(prevProxy.isNil) { ^this.unmap(param) };

		controlName = this.defaultControlNames[param];

		if(controlName == nil, {
			("ERROR: Could not find param " ++ param).warn;
			^prevProxy;
		});

		//If nil, try to retrieve it from the dict
		if(interpolationProxy == nil, {

			interpolationProxy = this.interpolationProxies[param];

			//If still nil, exit
			if(interpolationProxy == nil, {
				("ERROR: Could not find interpolationProxy for " ++ param).warn;
				^prevProxy;
			});
		});

		paramRate = controlName.rate;
		paramNumberOfChannels = controlName.numChannels;

		//If it's a proxy, use previousProxy's stuff
		if(isPrevProxyAProxy, {
			prevProxyRate = prevProxy.rate;
			prevProxyNumChannels = prevProxy.numChannels;
		}, {

			//Otherwise, use param's ones
			prevProxyRate = paramRate;
			prevProxyNumChannels = paramNumberOfChannels
		});

		//prevProxyRate.postln;
		//prevProxyNumChannels.postln;

		//Init prev proxy's out bus!
		canBeMapped = prevProxy.initBus(prevProxyRate, prevProxyNumChannels);

		if(canBeMapped) {
			//Init interpolationProxy's in bus with same values!
			if(interpolationProxy.isNeutral) { interpolationProxy.defineBus(prevProxyRate, prevProxyNumChannels) };

			if(useXSet, {
				interpolationProxy.xset(\in, prevProxy);
			}, {
				interpolationProxy.set(\in, prevProxy);
			});
		} {
			"Could not link node proxies, no matching input found.".warn
		};

		^prevProxy;
	}

	synth_setInterpProxy {
		arg prevProxy, param = \in, src = nil, reorderBlock = true, newlyCreatedInterpProxyNorm = false;

		//Check if there already was an interpProxy for the parameter
		var interpolationProxyEntry = this.interpolationProxies[param];
		var interpolationProxyNormalizerEntry = this.interpolationProxiesNormalizer[param];

		//Returns nil with a Pbind.. this could be problematic for connections, rework it!
		var paramRate, prevProxyRate;

		var controlName;

		var paramNumberOfChannels, prevProxyNumberOfChannels;

		var isThisProxyInstantiated = true;
		var isPrevProxyInstantiated = true;

		//This is the connection that is in place with the interpolation NodeProxy.
		var previousParamEntry = this.inProxies[param];

		//This is used to discern the different behaviours
		var prevProxyClass = prevProxy.class;

		var isPrevProxyAProxy = (prevProxyClass == AlgaNodeProxy).or(
			prevProxyClass.superclass == AlgaNodeProxy).or(
			prevProxyClass.superclass.superclass == AlgaNodeProxy);

		this.interpolationProxies.postln;

		if((interpolationProxyEntry == nil).or(interpolationProxyNormalizerEntry == nil), {
			("Invalid interpolation proxy: " ++ param).warn;
			^this;
		});


		if(this.group == nil, {
			("This proxy hasn't been instantiated yet!!!").warn;
			isThisProxyInstantiated = false;

			//^this;
		});

		if(isPrevProxyAProxy, {
			if(prevProxy.group == nil, {
				("prevProxy hasn't been instantiated yet!!!").warn;
				isPrevProxyInstantiated = false;

				//^this;
			});
		});

		controlName = defaultControlNames[param];

		if(controlName != nil, {
			paramRate = controlName.rate;
		}, {
			("Can't retrieve parameter rate for " ++ param).warn;
			^nil;
		});

		if(controlName != nil, {
			paramNumberOfChannels = controlName.numChannels;
		}, {
			("Can't retrieve parameter number of channels for " ++ param).warn;
			^nil;
		});

		if(isPrevProxyAProxy, {
			prevProxyNumberOfChannels = prevProxy.numChannels;
			prevProxyRate = prevProxy.rate;

		}, {
			//If not a proxy (but, like, a number), use same number of channels and rate
			prevProxyNumberOfChannels = paramNumberOfChannels;
			prevProxyRate = paramRate;

		});

		//Free previous connections to the this, if there were any
		this.freePreviousConnection(param);

		//Just switch the function
		//if(src != nil, {
		//	interpolationProxyEntry.source = src;
		//});

		//previousParamEntry.postln;
		//prevProxy.postln;

		//REVIEW!
		//if(previousParamEntry != prevProxy, {
		//if(true, {
		Routine.run({

			var changeInterpProxySymbol = false, interpolationProxySymbol, interpolationProxyNormalizesSymbol;

			//Previous interpProxy
			var interpolationProxySource = interpolationProxyEntry.source;
			var interpolationProxySourceString = interpolationProxySource.asString;

			var previousInterpProxyIns = 1, previousInterpProxyOuts = 1;

			if(interpolationProxySourceString.beginsWith("\proxyIn"), {
				previousInterpProxyIns = interpolationProxySourceString[10..11];
				previousInterpProxyOuts = interpolationProxySourceString[interpolationProxySourceString.size-2..interpolationProxySourceString.size-1];

				//strip < 10 in/outs count
				if(previousInterpProxyIns[1].asString == "_", { previousInterpProxyIns = previousInterpProxyIns[0].asString; });
				if(previousInterpProxyOuts[0].asString == "r", { previousInterpProxyOuts = previousInterpProxyOuts[1].asString; });
			});

			//Don't use param indexing for outs, as this proxy could be linked
			//to multiple proxies with same param names
			if(isPrevProxyAProxy, {
				this.inProxies.put(param, prevProxy);
				prevProxy.outProxies.put(this, this);
			});

			//re-instantiate source if not correct, here is where rate conversion and multichannel connectons happen.
			if(((interpolationProxySourceString.beginsWith("\proxyIn").not).or(
				previousInterpProxyOuts != paramNumberOfChannels).or(
				previousInterpProxyIns != prevProxyNumberOfChannels).or(
				paramNumberOfChannels != prevProxyNumberOfChannels).or(
				paramRate != prevProxyRate)),
			{
				var prevProxyRateString;
				var paramRateString;

				/*
				("previousInterpProxyOuts: " ++ previousInterpProxyOuts).postln;
				("paramNumberOfChannels: " ++ paramNumberOfChannels).postln;
				("previousInterpProxyIns: " ++ previousInterpProxyIns).postln;
				("prevProxyNumberOfChannels: " ++ prevProxyNumberOfChannels).postln;
				("paramNumberOfChannels: " ++ paramNumberOfChannels).postln;
				("prevProxyNumberOfChannels: " ++ prevProxyNumberOfChannels).postln;
				("paramRate: " ++ paramRate).postln;
				("prevProxyRate: " ++ prevProxyRate).postln;
				*/

				if(paramRate == \audio, {
					paramRateString = "ar";
				}, {
					paramRateString = "kr";
				});

				if(prevProxyRate == \audio, {
					prevProxyRateString = "ar";
				}, {
					if(prevProxyRate == \control, {
						prevProxyRateString = "kr";
					});
				});

				interpolationProxySymbol = ("proxyIn_" ++ prevProxyRateString ++ prevProxyNumberOfChannels
					++ "_" ++ paramRateString ++ paramNumberOfChannels).asSymbol;

				//changeInterpProxySymbol = true;

				//interpolationProxyNormalizesSymbol = ("interpProxyNorm_" ++ paramRateString ++ paramNumberOfChannels).asSymbol;

				//free previous ones?
				//interpolationProxyEntry.free(interpolationProxyEntry.fadeTime, true, true);

				//REVIEW!
				//Should .source be modified like this OR should a new interpolationProxy be created
				//on a new group and freeing this one after fadeTime? This is done in createInterpProxy, check
				//it there!
				interpolationProxyEntry.source = interpolationProxySymbol;

				//Wait for instantiation of interpProxy
				while(
					{(interpolationProxyEntry.instantiated.not)}, {
						0.01.wait;
						"Waiting for interp proxy instantiation".warn;
						interpolationProxyEntry.queryInstantiation;
				});

				//interpolationProxyNormalizerEntry.source = interpolationProxyNormalizesSymbol;
			});

			//interpolationProxyEntry.outProxies remains the same, connected to this!
			if(isPrevProxyAProxy, {
				interpolationProxyEntry.inProxies.put(\in, prevProxy);
			});

			//Only rearrange block if both proxies are actually instantiated.
			if(reorderBlock.and(isThisProxyInstantiated.and(isPrevProxyInstantiated)), {
				AlgaBlocksDict.reorderBlock(this.blockIndex, server);
			});

			//sync server (so it's sure that .source got executed before making the connections)
			server.sync;

			//If param is a number or an array, needs to make sure that the thing passed through
			//accounts for all the channels of the param:
			if(isPrevProxyAProxy.not, {
				if(prevProxy.class == Array, {
					prevProxy = prevProxy.reshape(paramNumberOfChannels);
				}, {
					//Number
					if(paramNumberOfChannels > 1, {
						prevProxy = Array.fill(paramNumberOfChannels, prevProxy);
					});
				});
			});

			//REVIEW!
			//Make connection to the normalizer
			if(newlyCreatedInterpProxyNorm.not, {
				//This is executed with normal connections. It will set the parameter
				//right now (the interpolation happens in the interpProxy, not interpProxyNorm).
				//For successive connections, the same interpNorm will be utilized, effectively making
				//this .set useless after the first stable connection.
				this.set(param, interpolationProxyNormalizerEntry);

				//Make connection to the interpolationProxy. Values are here interpolated using xset.
				this.synth_connectToInterpProxy(param, interpolationProxyEntry, prevProxy);
			}, {
				//Make connection to the interpolationProxy. No need of interpolating as it's happening already
				//between the two different interpNorms.
				//This should not make much of a difference, as interpolation values are scaled in the interpNorm anyway.
				this.synth_connectToInterpProxy(param, interpolationProxyEntry, prevProxy, useXSet:false);

				//This means that the previous interpNorm has been replaced by re-instantiating.
				//interpolation between the two is needed to switch states.
				this.xset(param, interpolationProxyNormalizerEntry);
			});

			//If prevProxy is not a NodeProxy, also run the unset command.
			//The connection to prevProxy (if it was like, a number) has already been set anyway.
			//this just helps removing connectons that are actually not in place anymore
			if(isPrevProxyAProxy.not, {
				//"Unsetting param".postln;
				this.unset(param);
			});


			//Actually change the source
			//if(changeInterpProxySymbol, {
			//interpolationProxyEntry.source = interpolationProxySymbol;
			//});
		});
	}
}
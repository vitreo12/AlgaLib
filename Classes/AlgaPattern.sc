// AlgaLib: SuperCollider implementation of the Alga live coding language
// Copyright (C) 2020-2021 Francesco Cameli

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

AlgaPatternInterpStreams {
	var <algaPattern;
	var <server;

	var <entries;
	var <interpSynths;
	var <interpBusses;
	var <interpBussesToFree;

	var <scaleArraysAndChans;

	*new { | algaPattern |
		^super.new.init(algaPattern)
	}

	init { | argAlgaPattern |
		entries             = IdentityDictionary(10);
		interpSynths        = IdentityDictionary(10);
		interpBusses        = IdentityDictionary(10);
		interpBussesToFree  = IdentitySet();
		scaleArraysAndChans = IdentityDictionary(10);
		algaPattern         = argAlgaPattern;
		server              = algaPattern.server;
	}

	//Free all active interpSynths. This triggers the onFree action that's executed in
	//addActiveInterpSynthOnFree
	freeActiveInterpSynthsAtParam { | paramName, time = 0 |
		var interpSynthsAtParam = interpSynths[paramName];

		if(interpSynthsAtParam != nil, {
			interpSynthsAtParam.keysValuesDo({ | uniqueID, interpSynth |
				//Trigger the release of the interpSynth. When freed, the onFree action
				//will be triggered. This is executed thanks to addActiveInterpSynthOnFree...
				//Note that only the first call to \t_release will be used as trigger, while
				//\fadeTime will always be set on any consecutive call (even after the first trigger of \t_release).
				interpSynth.set(
					\t_release, 1,
					\fadeTime, time,
				);
			});
		});
	}

	//Each param has its own interpSynth and bus. These differ from AlgaNode's ones,
	//as they are not embedded with the interpolation behaviour itself, but they are external.
	//This allows to separate the per-tick pattern triggering from the interpolation process.
	createPatternInterpSynthAndBusAtParam { | paramName, paramRate, paramNumChannels,
		entry, uniqueID, time = 0 |

		var interpGroup = algaPattern.interpGroup;
		var interpBus, interpSynth;
		var scaleArray;

		//Holds no paramNumChannels infos
		var interpSymbol = (
			"alga_pattern_interp_env_" ++
			paramRate
		).asSymbol;

		//Retrieve all active interpSynths at the current param.
		var interpSynthsAtParam = interpSynths[paramName];

		//alga_pattern_interp_env_... outputs one channel only
		interpBus = AlgaBus(server, 1, paramRate);

		//Each param / entry combination has its own interpSynth and interpBus!
		//This behaviour is different from AlgaNode, which dynamically replaces the previous one.
		//However, pattern synths are created on the fly, so the interpSynths need to be kept alive until
		//interpolation has finished. In a nutshell, patternSynths and interpSynths are decoupled.
		if(interpSynthsAtParam == nil, {
			//If it's the first synth, fadeTime is 0.
			//This only happens when first creating the AlgaPattern!
			interpSynth = AlgaSynth(
				interpSymbol,
				[\out, interpBus.index, \fadeTime, 0],
				interpGroup
			);
			interpSynths[paramName] = IdentityDictionary().put(uniqueID, interpSynth);
			interpBusses[paramName] = IdentityDictionary().put(uniqueID, interpBus);
		}, {
			//If it's not the first synth, fadeTime is time
			interpSynth = AlgaSynth(
				interpSymbol,
				[\out, interpBus.index, \fadeTime, time],
				interpGroup
			);
			interpSynths[paramName].put(uniqueID, interpSynth);
			interpBusses[paramName].put(uniqueID, interpBus);
		});

		//Add entries to algaPattern too.These are needed for algaInstantiatedAsReceiver.
		//Note: no mixing yet
		algaPattern.interpSynths[paramName][\default] = interpSynth;
		algaPattern.interpBusses[paramName][\default] = interpBus;

		//Add interpSynth to the current active ones for specific param / sender combination.
		//Also add a "onFree" routine that deletes unused entries from Dictionaries. This function
		//is called on freeing the interpSynth.
		//Note: no mixing yet
		algaPattern.addActiveInterpSynthOnFree(paramName, \default, interpSynth, {
			var entriesAtParam              = entries[paramName];
			var interpSynthsAtParam         = interpSynths[paramName];
			var interpBussesAtParam         = interpBusses[paramName];
			var scaleArraysAndChansAtParam  = scaleArraysAndChans[paramName];

			//Remove entry from entries and deal with inNodes / outNodes for both receiver and sender
			if(entriesAtParam != nil, {
				var entryAtParam = entriesAtParam[uniqueID];
				entriesAtParam.removeAt(uniqueID);
				if(entryAtParam != nil, {
					this.removeInOutNodesDictAtParam(entryAtParam, paramName);
				});
			});

			//Remove scaleArray and chans
			if(scaleArraysAndChansAtParam != nil, {
				var scaleArrayAndChansAtParam = scaleArraysAndChansAtParam[uniqueID];
				scaleArraysAndChansAtParam.removeAt(uniqueID);
				if(scaleArrayAndChansAtParam != nil, {
					algaPattern.removeScaling(paramName, uniqueID);
				});
			});

			//Remove entry from interpSynths
			if(interpSynthsAtParam != nil, { interpSynthsAtParam.removeAt(uniqueID) });

			//Remove entry from interpBusses and add entry to interpBussesToFree.
			//IMPORTANT: interpBusAtParam CAN'T be freed here, as dangling synths can still
			//be executed, and they would be (wrongly) pointing to the interpBus, causing
			//wrong results at the end of the interpolation process. interpBusAtParam needs
			//to be freed in the pattern loop.
			if(interpBussesAtParam != nil, {
				var interpBusAtParam = interpBussesAtParam[uniqueID];
				interpBussesAtParam.removeAt(uniqueID);
				//interpBussesToFree are freed in the pattern loop when patternSynth gets freed.
				//This is essential for the interpolation process to correctly only free busses
				//when completely unused on both lang and server!
				//interpBusAtParam can't be freed here as it can still be used if the patternSynth
				//takes longer to free itself (perhaps a long envelope) than the interpolation synth.
				interpBussesToFree.add(interpBusAtParam);
			});
		});
	}

	//Wrapper around AlgaNode's addInOutNodesDict
	addInOutNodesDictAtParam { | sender, param, mix = false |
		if(sender.isAlgaNode, {
			algaPattern.addInOutNodesDict(sender, param, mix:false);
		}, {
			//If ListPattern, loop over and only add AlgaNodes
			if(sender.isListPattern, {
				sender.list.do({ | listEntry |
					if(listEntry.isAlgaNode, {
						algaPattern.addInOutNodesDict(listEntry, param, mix:false);
					});
				});
			});
		});
	}

	//Wrapper around AlgaNode's removeInOutNodesDict
	removeInOutNodesDictAtParam { | oldSender, param |
		if(oldSender.isAlgaNode, {
			algaPattern.removeInOutNodesDict(oldSender, param);
		}, {
			//If ListPattern, loop over and only remove AlgaNodes
			if(oldSender.isListPattern, {
				oldSender.list.do({ | listEntry |
					if(listEntry.isAlganode, {
						algaPattern.removeInOutNodesDict(listEntry, param)
					});
				});
			});
		});
	}

	//add a scaleArray and chans
	addScaleArrayAndChans { | paramName, paramNumChannels, uniqueID, chans, scale |
		var scaleArraysAndChansAtParam = scaleArraysAndChans[paramName];

		//Using uniqueID as sender (so mixing will work in the future)
		var scaleArray = algaPattern.calculateScaling(paramName, uniqueID, paramNumChannels, scale);

		if(scaleArraysAndChansAtParam == nil, {
			scaleArraysAndChans[paramName] = IdentityDictionary().put(uniqueID, [scaleArray, chans]);
		}, {
			scaleArraysAndChans[paramName].put(uniqueID, [scaleArray, chans]);
		});
	}

	//Add a new sender interpolation to the current param
	add { | entry, controlName, chans, scale, time = 0 |
		var paramName, paramRate, paramNumChannels;
		var uniqueID;
		var entriesAtParam;

		if(controlName == nil, {
			"AlgaPatternInterpStreams: Invalid controlName".error
		});

		paramName = controlName.name;
		paramRate = controlName.rate;
		paramNumChannels = controlName.numChannels;

		//Retrieve all the current entries for the paramName:
		//entries are all the active senders for this AlgaPattern
		entriesAtParam = entries[paramName];

		//Interpret entry asStream
		entry = entry.asStream;

		//Use an unique id as index as it's more reliable than using the entry as key:
		//entry could very well be a number (like 440), screwing things up in IdentityDict.
		uniqueID = UniqueID.next;

		//Either create a new Dict for the param, or add to existing one
		if(entriesAtParam == nil, {
			entries[paramName] = IdentityDictionary().put(uniqueID, entry);
		}, {
			entries[paramName].put(uniqueID,entry);
		});

		//Add the scaleArray and chans
		this.addScaleArrayAndChans(paramName, paramNumChannels, uniqueID, chans, scale);

		//Add proper inNodes / outNodes / connectionTimeOutNodes ...
		this.addInOutNodesDictAtParam(entry, paramName, false);

		//Trigger the interpolation process on all the other active interpSynths.
		//This must always be before createPatternInterpSynthAndBusAtParam
		this.freeActiveInterpSynthsAtParam(
			paramName,
			time
		);

		//Create the interpSynth and interpBus for the new sender and
		//activate the interpolation processon all the other active interpSynths.
		this.createPatternInterpSynthAndBusAtParam(
			paramName: paramName,
			paramRate: paramRate,
			paramNumChannels: paramNumChannels,
			entry: entry,
			uniqueID: uniqueID,
			time: time
		);
	}
}

AlgaPattern : AlgaNode {
	/*
	Todos:

	1) What about inNodes for an AlgaPattern, especially with ListPatterns as \def? (WIP)

	2) How to connect an AlgaNode to an AlgaPattern parameter? What about kr / ar? DONE

	3) Continuous or SAH interpolation (both in Patterns and AlgaNodes) (WIP)

	4) \dur implementation: doesn't work cause it's not time accurate: there's no way
	   of syncing multiple patterns, as the interpolation process with Pseg will end up out
	   of phase. Right now, \dur just sets the requested value AFTER time.

	5) Can an AlgaNode connect to \dur? Only if it's \control rate (using AlgaPkr)
	*/

	/*
	Maybes:

	1) fx: (def: Pseq([\delay, \tanh]), delayTime: Pseq([0.2, 0.4]))

	// Things to do:
	// 1) Check the def exists
	// 2) Check it has an \in parameter
	// 3) Check it can free itself (like with DetectSilence).
	//    If not, it will be freed with interpSynths
	// 4) Route synth's audio through it
	// 5) Use the "wet" result as output
	// 6) Can't be interpolated

	2) out: (node: Pseq([a, b], time: 1, scale: 1)

	// Things to do:
	// 1) Check if it is an AlgaNode or a ListPattern of AlgaNodes
	// 2) It would just connect to nodes with \in param, mixing ( mixTo / mixFrom )
	// 3) Can't be interpolated (but the connection itself can)
	*/

	//The actual Patterns to be manipulated
	var <pattern;

	//The \dur entry (special case)
	var <dur;

	//The Event input
	var <eventPairs;

	//The AlgaReschedulingEventStreamPlayer
	var <algaReschedulingEventStreamPlayer;

	//Dict of all interpolation streams
	var <>interpStreams;

	//Add the \algaNote event to Event
	*initClass {
		//StartUp.add is needed: Event class must be compiled first
		StartUp.add({
			this.addAlgaNoteEventType;
		});
	}

	//Doesn't have args and outsMapping like AlgaNode
	*new { | def, connectionTime = 0, playTime = 0, server, sched = 0 |
		^super.new(
			def: def,
			connectionTime: connectionTime,
			playTime: playTime,
			server: server,
			sched: sched
		);
	}

	//Add the \algaNote event type
	*addAlgaNoteEventType {
		Event.addEventType(\algaNote, #{
			//The final OSC bundle
			var bundle;

			//The AlgaSynthDef
			var algaSynthDef = ~synthDefName.valueEnvir;

			//AlgaPattern and its server / clock
			var algaPattern = ~algaPattern;
			var algaPatternServer = ~algaPatternServer;
			var algaPatternClock = ~algaPatternClock;

			//Other things for pattern syncing / clocking / scheduling
			var offset = ~timingOffset;
			var lag = ~lag;

			//Needed ?
			~isPlaying = true;

			//Create the bundle with all needed Synths for this Event.
			bundle = algaPatternServer.makeBundle(false, {
				~algaPattern.createEventSynths(
					algaSynthDef
				)
			});

			//Send bundle to server using the same server / clock as the AlgaPattern
			//Note that this does not go through the AlgaScheduler directly, but it uses its same clock!
			schedBundleArrayOnClock(
				offset,
				algaPatternClock,
				bundle,
				lag,
				algaPatternServer
			);
		});
	}

	//Set dur asStream for it to work within Pfuncn
	setDur { | value |
		dur = value.asStream
	}

	//Free all unused busses from interpStreams
	freeUnusedInterpBusses {
		var interpBussesToFree = interpStreams.interpBussesToFree;
		interpBussesToFree.do({ | interpBus |
			interpBus.free;
			interpBussesToFree.remove(interpBus);
		});
	}

	//Create all pattern synths per-param ...
	//What about multi-channel expansion? As of now, multichannel works with the
	//same conversion rules as AlgaNode, but perhaps it's more ideomatic to implement
	//native channel expansion. It should be easy: just pop new synths and route them
	//to the same patternInterpSumBus.
	//However, mind of the overhead of \alga_pattern_ ... functions, which are expensive
	//due to scalings, etc... Perhaps they should be reworked to just "bypass" the input
	//and route it correctly to the bus that interpolation will be using.
	createPatternParamSynths { | paramName, paramNumChannels, paramRate,
		paramDefault, patternInterpSumBus, patternBussesAndSynths, interpStreamsAtParam, scaleArraysAndChansAtParam |

		//Core of the interpolation behaviour for AlgaPattern !!
		if(interpStreamsAtParam != nil, {
			interpStreamsAtParam.keysValuesDo({ | uniqueID, interpStreamAtParam | //indexed by uniqueIDs
				var validParam = false;
				var paramVal = interpStreamAtParam; //paramVal is the interpStreamAtParam
				var sender, senderNumChannels, senderRate;

				//Unpack Pattern value
				if(paramVal.isStream, {
					paramVal = paramVal.next;
				});

				//Fallback sender (modified for AlgaNode, needed for chansMapping)
				sender = paramVal;

				//Valid values are Numbers / Arrays / AlgaNodes
				case

				//Number / Array
				{ paramVal.isNumberOrArray } {
					if(paramVal.isSequenceableCollection, {
						//an array
						senderNumChannels = paramVal.size;
						senderRate = "control";
					}, {
						//a num
						senderNumChannels = 1;
						senderRate = "control";
					});

					validParam = true;
				}

				//AlgaNode
				{ paramVal.isAlgaNode } {
					sender = paramVal; //essential for chansMapping (paramVal gets modified)
					if(paramVal.algaInstantiated, {
						//if algaInstantiated, use the rate, numchannels and bus arg from the alga bus
						senderRate = paramVal.rate;
						senderNumChannels = paramVal.numChannels;
						paramVal = paramVal.synthBus.busArg;
					}, {
						//otherwise, use default
						senderRate = "control";
						senderNumChannels = paramNumChannels;
						paramVal = paramDefault;
						("AlgaPattern: AlgaNode wasn't algaInstantiated yet. Using default value for " ++ paramName).warn;
					});

					validParam = true;
				};

				if(validParam, {
					//Get the bus where interpolation envelope is written to...
					//REMEMBER that for AlgaPattern, interpSynths are actually JUST the
					//interpolation envelope, which is then passed through this individual synths!
					// ... Now, I need to keep track of all the active interpBusses instead, not retrievin
					//from interpBusses, which gets replaced in language, but should implement the same
					//behaviour of activeInterpSynths and get busses from there.
					var patternParamEnvBus = interpStreams.interpBusses[paramName][uniqueID];

					if(patternParamEnvBus != nil, {
						var patternParamSynth;

						var patternParamSymbol = (
							"alga_pattern_" ++
							senderRate ++
							senderNumChannels ++
							"_" ++
							paramRate ++
							paramNumChannels
						).asSymbol;

						var patternParamSynthArgs = [
							\in, paramVal,
							\env, patternParamEnvBus.busArg,
							\out, patternInterpSumBus.index,
							\fadeTime, 0
						];

						//get correct scaleArray and chans
						var scaleArrayAndChansAtParam = scaleArraysAndChansAtParam[uniqueID];

						//add scaleArray to args
						if(scaleArrayAndChansAtParam != nil, {
							var scaleArray = scaleArrayAndChansAtParam[0]; //0 == scaleArray
							var chansMapping = scaleArrayAndChansAtParam[1]; //1 == chans

							if(scaleArray != nil, {
								patternParamSynthArgs = patternParamSynthArgs.addAll(scaleArray);
							});

							//only apply chansMapping to AlgaNodes
							if((chansMapping != nil).and(sender.isAlgaNode), {
								var indices = this.calculateSenderChansMappingArray(
									paramName,
									sender,
									chansMapping,
									senderNumChannels,
									paramNumChannels,
									false //don't update the AlgaNode's chans dict
								);

								patternParamSynthArgs = patternParamSynthArgs.add(\indices).add(indices);
							});
						});

						patternParamSynth = AlgaSynth(
							patternParamSymbol,
							patternParamSynthArgs,
							interpGroup,
							\addToTail,
							waitForInst: false
						);

						//Register patternParamSynth to be freed
						patternBussesAndSynths.add(patternParamSynth);
					});
				}, {
					("AlgaPattern: Invalid class " ++ paramVal.class ++ " input for parameter " ++ paramName.asString).error;
				});
			});
		});
	}

	//Create all needed Synths for this Event. This is triggered by the \algaNote Event
	createEventSynths { | algaSynthDef |
		//These will be populated and freed when the patternSynth is released
		var patternBussesAndSynths = IdentitySet(controlNames.size * 2);

		//args to patternSynth
		var patternSynthArgs = [
			\gate, 1,
			\out, synthBus.index
		];

		//The actual synth that will be created
		var patternSynth;

		//Loop over controlNames and create as many Busses and Synths as needed,
		//also considering interpolation / normalization
		controlNames.do({ | controlName |
			var paramName = controlName.name;
			var paramNumChannels = controlName.numChannels;
			var paramRate = controlName.rate;
			var paramDefault = controlName.defaultValue;

			//This is the interpBus for this param that all patternParamSynths will write to.
			//This will then be used for the actual normalization that happens in the normSynth
			//As with AlgaNode's, it needs one extra channel for the separate env.
			var patternInterpSumBus = AlgaBus(server, paramNumChannels + 1, paramRate);

			//This is the normBus that the normSynth will write to, and patternSynth will read from
			var patternParamNormBus = AlgaBus(server, paramNumChannels, paramRate);

			//Symbol for normSynth
			var patternParamNormSynthSymbol = (
				"alga_norm_" ++
				paramRate ++
				paramNumChannels
			).asSymbol;

			//Args for normSynth
			var patternParamNormSynthArgs = [
				\args, patternInterpSumBus.busArg,
				\out, patternParamNormBus.index,
				\fadeTime, 0
			];

			//The actual normSynth for this specific param.
			//The patternSynth will read from its output.
			var patternParamNormSynth = AlgaSynth(
				patternParamNormSynthSymbol,
				patternParamNormSynthArgs,
				normGroup,
				waitForInst: false
			);

			//All the current interpStreams for this param. Retrieve the entries,
			//which store all the senders to this param for the interpStream.
			var interpStreamsAtParam = interpStreams.entries[paramName];

			//scaleArray and chans
			var scaleArraysAndChansAtParam = interpStreams.scaleArraysAndChans[paramName];

			//Create all interp synths for current param
			this.createPatternParamSynths(
				paramName, paramNumChannels, paramRate,
				paramDefault, patternInterpSumBus, patternBussesAndSynths,
				interpStreamsAtParam, scaleArraysAndChansAtParam
			);

			//Read from patternParamNormBus
			patternSynthArgs = patternSynthArgs.add(paramName).add(patternParamNormBus.busArg);

			//Register normBus, normSynth, interSumBus to be freed
			patternBussesAndSynths.add(patternParamNormBus);
			patternBussesAndSynths.add(patternParamNormSynth);
			patternBussesAndSynths.add(patternInterpSumBus);
		});

		//This synth writes directly to synthBus
		patternSynth = AlgaSynth(
			algaSynthDef,
			patternSynthArgs,
			synthGroup,
			waitForInst: false
		);

		//Free all normBusses, normSynths, interpBusses and interpSynths on patternSynth's release
		patternSynth.onFree( {
			patternBussesAndSynths.do({ | entry |
				//free works both for AlgaSynths and AlgaBusses
				entry.free;

				//IMPORTANT: free the unused interpBusses of the interpStreams.
				//This needs to happen on patternSynth's free as that's the notice
				//that no other synths will be using them. Also, this fixes the case where
				//patternSynth takes longer than \dur. We want to wait for the end of patternSynth
				//to free all used things!
				this.freeUnusedInterpBusses;
			});
		});
	}

	//dispatchNode: first argument is an Event
	dispatchNode { | def, args, initGroups = false, replace = false,
		keepChannelsMapping = false, outsMapping, keepScale = false, sched = 0 |

		//def: entry
		var defEntry = def[\def];
		if(defEntry == nil, {
			"AlgaPattern: no 'def' entry in the Event".error;
			^this;
		});

		//Store the Event
		eventPairs = def;

		//Store class of the synthEntry
		defClass = defEntry.class;

		//If there is a synth playing, set its algaInstantiated status to false:
		//this is mostly needed for .replace to work properly and wait for the new synth
		//to be algaInstantiated!
		if(synth != nil, { synth.algaInstantiated = false });

		case
		{ defClass == Symbol } {
			^this.dispatchSynthDef(defEntry, initGroups, replace,
				keepChannelsMapping:keepChannelsMapping,
				keepScale:keepScale,
				sched:sched
			);
		}
		{ def.isListPattern } {
			^this.dispatchListPattern;
		}
		{ defClass == Function } {
			^this.dispatchFunction;
		};

		("AlgaPattern: class '" ++ defClass ++ "' is an invalid 'def'").error;
	}

	//Overloaded function
	buildFromSynthDef { | initGroups = false, replace = false,
		keepChannelsMapping = false, keepScale = false, sched = 0 |

		//Retrieve controlNames from SynthDesc
		var synthDescControlNames = synthDef.asSynthDesc.controls;
		this.createControlNamesAndParamsConnectionTime(synthDescControlNames);

		numChannels = synthDef.numChannels;
		rate = synthDef.rate;

		sched = sched ? 0;

		//Detect if AlgaSynthDef can be freed automatically. Otherwise, error!
		if(synthDef.explicitFree.not, {
			("AlgaPattern: AlgaSynthDef '" ++ synthDef.name.asString ++ "' can't free itself: it doesn't implement any DoneAction.").error;
			^this
		});

		//Generate outsMapping (for outsMapping connectinons)
		this.calculateOutsMapping(replace, keepChannelsMapping);

		//Create groups if needed
		if(initGroups, { this.createAllGroups });

		//Create synthBus for output
		//interpBusses are taken care of in createPatternInterpSynthAndBus.
		this.createSynthBus;

		//Create the actual pattern, pushing action to scheduler
		scheduler.addAction(
			func: { this.createPattern },
			sched: sched
		);
	}

	//Support Function in the future
	dispatchFunction {
		"AlgaPattern: Functions as \def are not supported yet".error;
	}

	//Support multiple SynthDefs in the future,
	//only if expressed with ListPattern subclasses (like Pseq, Prand, etc...):
	//(def: Pseq([\synthDef1, Pseq([\synthDef2, \synthDef3]))
	//This will collect ALL controlnames for each of the synthDefs in order
	//to correctly instantiate the interpStreams... I know it's quite the overhead,
	//but, for now, it's just the easier solution.
	dispatchListPattern {
		"AlgaPattern: ListPatterns as \def are not supported yet".error;
	}

	//Build the actual pattern
	createPattern {
		var foundDurOrDelta = false;
		var patternPairs = Array.newClear;

		//Loop over the Event input from the user
		eventPairs.keysValuesDo({ | paramName, value |
			if((paramName == \dur).or(paramName == \delta), {
				foundDurOrDelta = true;
				this.setDur(value);
			});

			if(paramName == \def, {
				//Add \def key as \instrument
				patternPairs = patternPairs.add(\instrument).add(value);
			});
		});

		//If no dur or delta, default to 1
		if(foundDurOrDelta.not, {
			this.setDur(1);
		});

		//Add all the default entries from SynthDef that the user hasn't set yet
		controlNames.do({ | controlName |
			var paramName = controlName.name;
			var paramValue = eventPairs[paramName];

			//if not set explicitly yet
			if(paramValue == nil, {
				paramValue = this.getDefaultOrArg(controlName, paramName);
			});

			//Add to interpStreams (which also creates interpBus / interpSynth)
			interpStreams.add(
				entry: paramValue,
				controlName: controlName,
				time: 0
			);
		});

		//Add \type, \algaNode, and all things related to
		//the context of this AlgaPattern
		patternPairs = patternPairs.addAll([
			\type, \algaNote,
			\dur, Pfuncn( { dur.next }, inf), //Pfunc allows to modify the value
			\algaPattern, this,
			\algaPatternServer, server,
			\algaPatternClock, this.clock
		]);

		//Create the Pattern by calling .next from the streams
		pattern = Pbind(*patternPairs);

		//start the pattern right away. quant?
		algaReschedulingEventStreamPlayer = pattern.playAlgaRescheduling(
			clock: this.clock
		);
	}

	//interpolate dur (not yet)
	interpolateDur { | value, sched |
		if(sched == nil, { sched = 0 });
		("AlgaPattern: 'dur' interpolation is not supported yet. Rescheduling 'dur' at the " ++ sched ++ " quantization.").warn;
		this.setDur(value);
		//should this be add to scheduler with sched 0? Shouldn't be necessary,
		//as both are using the same clock...
		algaReschedulingEventStreamPlayer.rescheduleAtQuant(sched);
	}

	//<<, <<+ and <|
	makeConnectionInner { | param = \in, sender, senderChansMapping, scale, time = 0 |
		var paramConnectionTime = paramsConnectionTime[param];
		if(paramConnectionTime == nil, { paramConnectionTime = connectionTime });
		if(paramConnectionTime < 0, { paramConnectionTime = connectionTime });
		time = time ? paramConnectionTime;

		if((sender.isAlgaNode.not).and(sender.isPattern.not).and(sender.isNumberOrArray.not), {
			"AlgaPattern: makeConnection only works with AlgaNodes, AlgaPatterns, Patterns, Numbers and Arrays".error;
			^this;
		});

		//Add to interpStreams (which also creates interpBus / interpSynth)
		interpStreams.add(
			entry: sender,
			controlName: controlNames[param],
			chans: senderChansMapping,
			scale: scale,
			time: time
		);
	}

	//<<, <<+ and <|
	makeConnection { | sender, param = \in, replace = false, mix = false,
		replaceMix = false, senderChansMapping, scale, time, sched |

		//delta == dur
		if(param == \delta, {
			param = \dur
		});

		//Special case, \dur
		if(param == \dur, {
			this.interpolateDur(sender, sched);
			^this;
		});

		//All other cases
		if(this.algaCleared.not.and(sender.algaCleared.not).and(sender.algaToBeCleared.not), {
			scheduler.addAction(
				condition: { (this.algaInstantiatedAsReceiver(param, sender, false)).and(sender.algaInstantiatedAsSender) },
				func: {
					this.makeConnectionInner(
						param: param,
						sender: sender,
						senderChansMapping: senderChansMapping,
						scale: scale,
						time: time
					)
				},
				sched: sched
			)
		});
	}

	//Only from is needed: to already calls into makeConnection
	from { | sender, param = \in, chans, scale, time, sched |
		//Force pattern dispatch
		if(sender.isPattern, {
			^this.makeConnection(sender, param, senderChansMapping:chans,
				scale:scale, time:time, sched:sched
			);
		});

		//All the other cases apply to AlgaNode too.
		//AlgaPattern.makeConnection is getting called anyways.
		^super.from(sender, param, chans, scale, time, sched);
	}

	// <<| \param (goes back to defaults)
	//previousSender is the mix one, in case that will be implemented in the future
	resetParam { | param = \in, previousSender = nil, time |
        "AlgaPattern: resetParam is not supported yet".error;
	}

	//replace entries.
	// options:
	// 1) replace the entire AlgaPattern with a new one (like AlgaNode.replace)
	// 2) replace just the SynthDef with either a new SynthDef or a ListPattern with JUST SynthDefs.
	//    This would be equivalent to <<.def \newSynthDef
	//    OR <<.def Pseq([\newSynthDef1, \newSynthDef2])
	/*
	replace { | def, time, keepChannelsMappingIn = true, keepChannelsMappingOut = true,
		keepInScale = true, keepOutScale = true |
        "AlgaPattern: replace is not supported yet".error;
	}
	*/

	//Don't support <<+ for now
	mixFrom { | sender, param = \in, inChans, scale, time |
		"AlgaPattern: mixFrom is not supported yet".error;
	}

	//Don't support >>+ for now
	mixTo { | receiver, param = \in, outChans, scale, time |
		"AlgaPattern: mixTo is not supported yet".error;
	}

	//stop and reschedule in the future
	reschedule { | sched = 0 |
		algaReschedulingEventStreamPlayer.reschedule(sched);
	}

	//There is no way to check individual synths.
	//So, let's at least check that the group must be insantiated
	algaInstantiated {
		^(group.algaInstantiated);
	}

	//To send signal... algaInstantiatedAsReceiver is same as AlgaNode
	algaInstantiatedAsSender {
		^((this.algaInstantiated).and(synthBus != nil));
	}

	isAlgaPattern { ^true }
}

//Alias
AP : AlgaPattern {}

//Implements Pmono behaviour
AlgaMonoPattern : AlgaPattern {}

//Alias
AMP : AlgaMonoPattern {}

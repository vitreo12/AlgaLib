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

	*new { | algaPattern |
		^super.new.init(algaPattern)
	}

	init { | argAlgaPattern |
		entries      = IdentityDictionary(10);
		interpSynths = IdentityDictionary(10);
		interpBusses = IdentityDictionary(10);
		algaPattern  = argAlgaPattern;
		server       = algaPattern.server;
	}

	//Each param has its own interpSynth and bus. These differ from AlgaNode's ones,
	//as they are not embedded with the interpolation behaviour itself, but they are external.
	//This allows to separate the per-tick pattern triggering from the interpolation process.
	createPatternInterpSynthAndBus { | paramName, paramRate, paramNumChannels, entry, uniqueID |
		var interpGroup = algaPattern.interpGroup;
		var interpBus, interpSynth;

		var interpSymbol = (
			"alga_pattern_interp_env_" ++
			paramRate
		).asSymbol;

		var interpSynthsAtParam = interpSynths[paramName];

		interpBus = AlgaBus(server, paramNumChannels + 1, paramRate);

		interpSynth = AlgaSynth(
			interpSymbol,
			[\out, interpBus.index, \fadeTime, 0],
			interpGroup
		);

		//Each param / entry combination has its own interpSynth and interpBus!
		//This behaviour is different from AlgaNode, which dynamically replaces the previous one.
		//However, pattern synths are created on the fly, so these things need to be re-used until
		//interpolation has finished
		if(interpSynthsAtParam == nil, {
			interpSynths[paramName] = IdentityDictionary().put(uniqueID, interpSynth);
			interpBusses[paramName] = IdentityDictionary().put(uniqueID, interpBus);
		}, {
			interpSynths[paramName].put(uniqueID, interpSynth);
			interpBusses[paramName].put(uniqueID, interpBus);
		});

		//Add interpSynth to the current active ones for specific param / sender combination
		//algaPattern.addActiveInterpSynthOnFree(paramName, \default, interpSynth);

		//interpBus should have a similar mechanism here !!!

		//How to normalize ???

		//Add entries to algaPattern too ... These are needed for algaInstantiatedAsReceiver ...
		//This does not take in account mixing yet!
		algaPattern.interpSynths[paramName][\default] = interpSynth;
		algaPattern.interpBusses[paramName][\default] = interpBus;
	}

	add { | entry, controlName |
		var paramName, paramRate, paramNumChannels;
		var uniqueID;
		var entriesAtParam;

		if(controlName == nil, {
			"AlgaPatternInterpStreams: Invalid controlName".error
		});

		paramName = controlName.name;
		paramRate = controlName.rate;
		paramNumChannels = controlName.numChannels;

		entriesAtParam = entries[paramName];
		entry = entry.asStream;

		//Use an unique id as index as it's more reliable than entry:
		//entry could very well be a number, screwing things up in IdentityDict.
		uniqueID = UniqueID.next;

		if(entriesAtParam == nil, {
			entries[paramName] = IdentityDictionary().put(uniqueID, entry);
		}, {
			entries[paramName].put(uniqueID,entry);
		});

		this.createPatternInterpSynthAndBus(
			paramName, paramRate, paramNumChannels,
			entry, uniqueID
		);
	}

	remove { | param = \in |
		entries.removeAt(param);
		interpSynths.removeAt(param);
		interpBusses.removeAt(param);
	}

	removeEntryAtParam { | param = \in, entry |
		entries[param].remove(entry);
		interpSynths[param].remove(entry);
		interpBusses[param].remove(entry);
	}

	removeEntryAtParamOnSynthFree { | synth, param = \in, entry |
		synth.onFree({
			this.removeEntryAtParam(param, entry);
		});
	}
}

AlgaPattern : AlgaNode {
	/*
	Todos:

	1) What about inNodes for an AlgaPattern, especially with ListPatterns as \def? (WIP)

	2) How to connect an AlgaNode to an AlgaPattern parameter? What about kr / ar? (WIP)

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

	//Create all pattern synths per-param ...
	//What about multi-channel expansion? As of now, multichannel works with the
	//same conversion rules as AlgaNode, but perhaps it's more ideomatic to implement
	//native channel expansion. It should be easy: just pop new synths and route them
	//to the same patternInterpSumBus.
	//However, mind of the overhead of \alga_pattern_ ... functions, which are expensive
	//due to scalings, etc... Perhaps they should be reworked to just "bypass" the input
	//and route it correctly to the bus that interpolation will be using.
	createPatternParamSynths { | paramName, paramNumChannels, paramRate,
		paramDefault, patternInterpSumBus, patternBussesAndSynths |

		//All the current interpStreams for this param. Retrieve the entries,
		//which store all the senders to this param for the interpStream.
		var interpStreamsAtParam = interpStreams.entries[paramName];

		//Core of the interpolation behaviour for AlgaPattern !!
		if(interpStreamsAtParam != nil, {
			interpStreamsAtParam.keysValuesDo({ | uniqueID, interpStreamAtParam | //indexed by uniqueIDs
				var validParam = false;
				var paramVal = interpStreamAtParam; //paramVal is the interpStreamAtParam
				var senderNumChannels, senderRate;

				//Unpack Pattern value
				if(paramVal.isStream, {
					paramVal = paramVal.next;
				});

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

					var patternParamSynth = AlgaSynth(
						patternParamSymbol,
						patternParamSynthArgs,
						interpGroup,
						\addToTail,
						waitForInst: false
					);

					//Register patternParamSynth to be freed
					patternBussesAndSynths.add(patternParamSynth);
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
				\out, patternParamNormBus.index
			];

			//The actual normSynth for this specific param.
			//The patternSynth will read from its output.
			var patternParamNormSynth = AlgaSynth(
				patternParamNormSynthSymbol,
				patternParamNormSynthArgs,
				normGroup,
				waitForInst: false
			);

			//Create all interp synths for current param
			this.createPatternParamSynths(
				paramName, paramNumChannels, paramRate,
				paramDefault, patternInterpSumBus, patternBussesAndSynths
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
				//.free works both for AlgaSynths and AlgaBusses
				entry.free;
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
			});

			if(paramName != \def, {
				//delta == dur
				if(paramName == \delta, {
					paramName = \dur;
				});

				//Add \dur to array
				if(paramName == \dur, {
					patternPairs = patternPairs.add(\dur).add(value);
				});
			}, {
				//Add \def key as \instrument
				patternPairs = patternPairs.add(\instrument).add(value);
			});
		});

		//If no dur or delta, default to 1
		if(foundDurOrDelta.not, {
			patternPairs = patternPairs.add(\dur).add(1)
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
			interpStreams.add(paramValue, controlName, this);
		});

		//Add \type, \algaNode, and all things related to
		//the context of this AlgaPattern
		patternPairs = patternPairs.addAll([
			\type, \algaNote,
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

	//<<, <<+ and <|
	makeConnectionInner { | param = \in, sender, time = 0, scale  |
		var paramConnectionTime = paramsConnectionTime[param];
		if(paramConnectionTime == nil, { paramConnectionTime = connectionTime });
		if(paramConnectionTime < 0, { paramConnectionTime = connectionTime });
		time = time ? paramConnectionTime;

		//delta == dur
		if(param == \delta, {
			param = \dur
		});

		if((sender.isAlgaNode.not).and(sender.isPattern.not).and(sender.isNumberOrArray.not), {
			"AlgaPattern: makeConnection only works with AlgaNodes, Patterns, Numbers and Arrays".error;
			^this;
		});

		//Special case, \dur
		if(param == \dur, {
			"AlgaPattern: \dur interpolation is not supported yet".error;
			^this;
		});

		//Add to interpStreams (which also creates interpBus / interpSynth)
		interpStreams.add(sender, controlNames[param]);
	}

	//<<, <<+ and <|
	makeConnection { | sender, param = \in, replace = false, mix = false,
		replaceMix = false, senderChansMapping, scale, time, sched = 0 |
		if(this.algaCleared.not.and(sender.algaCleared.not).and(sender.algaToBeCleared.not), {
			scheduler.addAction(
				condition: { (this.algaInstantiatedAsReceiver(param, sender, false)).and(sender.algaInstantiatedAsSender) },
				func: { this.makeConnectionInner(param, sender, time, scale) },
				sched: sched
			)
		});
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
	replace { | def, time, keepChannelsMappingIn = true, keepChannelsMappingOut = true,
		keepInScale = true, keepOutScale = true |
        "AlgaPattern: replace is not supported yet".error;
	}

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

	//Since can't check synth, just check if the group is instantiated
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

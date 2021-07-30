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

//This class holds all data for currently active interpSynths / interpBusses and relative
//metadata for an individual AlgaPattern. It's used on the pattern loop to retrieve
//which synths to play, and their current state.
AlgaPatternInterpStreams {
	var <algaPattern;
	var <server;

	//The \dur entry
	var <>dur;

	//Store it for .replace
	var <algaReschedulingEventStreamPlayer;

	//Store it for .replace
	var <>algaSynthBus;

	//Needed to free algaSynthBus / interpSynths / interpBusses on release:
	//there's no other way to know how long a patternSynth would last.
	//Even if the pattern stopped there could be a last tick of 10s length, or whatever.
	//algaPatternSynths will also contain the fxSynths, which can definitely last longer!!!
	var <algaPatternSynths;

	var <entries;
	var <interpSynths;
	var <interpBusses;
	var <interpBussesToFree;

	var <scaleArraysAndChans;
	var <sampleAndHolds;

	*new { | algaPattern |
		^super.new.init(algaPattern)
	}

	init { | argAlgaPattern |
		entries             = IdentityDictionary(10);
		interpSynths        = IdentityDictionary(10);
		interpBusses        = IdentityDictionary(10);
		interpBussesToFree  = IdentitySet();
		algaPatternSynths   = IdentitySet(10);
		scaleArraysAndChans = IdentityDictionary(10);
		sampleAndHolds      = IdentityDictionary(10);
		algaPattern         = argAlgaPattern;
		server              = algaPattern.server;
	}

	//Called from freeAllSynthsOnReplace
	freeAllInterpSynthsAndBusses {
		if(interpSynths != nil, {
			interpSynths.do({ | interpSynthsAtParam |
				interpSynthsAtParam.do({ | interpSynth |
					if(interpSynth != nil, { interpSynth.free })
				});
			});
		});

		if(interpBusses != nil, {
			interpBusses.do({ | interpBussesAtParam |
				interpBussesAtParam.do({ | interpBus |
					if(interpBus != nil, { interpBus.free })
				});
			});
		});
	}

	//Free the algaSynthBus only if all patternSynths are done. This requires .stop to have been
	//first called on the algaReschedulingEventStreamPlayer, otherwise this mechanism will fail!
	freeAllSynthsAndBussesOnReplace {
		AlgaSpinRoutine.waitFor(
			condition: { algaPatternSynths.size == 0 }, //wait for all algaPatternSynths to be done
			func: {
				this.freeAllInterpSynthsAndBusses; //Free all interpSynths / Busses for the stream
				if(algaSynthBus != nil, { algaSynthBus.free }); //Finally, free the algaSynthBus too
			},
			interval: 0.5, //Check every 0.5, not to overload scheduling
			maxTime: nil //maxTime == nil means no maxTime, it will keep going
		)
	}

	//Free all active interpSynths. This triggers the onFree action that's executed in
	//addActiveInterpSynthOnFree
	freeActiveInterpSynthsAtParam { | paramName, time = 0 |
		var interpSynthsAtParam = interpSynths[paramName];
		if(interpSynthsAtParam != nil, {
			interpSynthsAtParam.keysValuesDo({ | uniqueID, interpSynth |
				//Trigger the release of the interpSynth. When freed, the onFree action
				//will be triggered. This is executed thanks to addActiveInterpSynthOnFree.
				//Note that only the first call to \t_release will be used as trigger, while
				//\fadeTime will always be set on any consecutive call,
				//even after the first trigger of \t_release.
				interpSynth.set(
					\t_release, 1,
					\fadeTime, time,
				);
			});
		});
	}

	//This creates a temporary patternSynth for mid-pattern interpolation.
	//It will be freed at the start of the NEXT paramSynth.
	createTemporaryPatternParamSynthAtParam { | entry, uniqueID, paramName, paramNumChannels, paramRate, paramDefault |
		var scaleArraysAndChansAtParam = scaleArraysAndChans[paramName];
		var patternInterpSumBus = algaPattern.currentPatternInterpSumBus;  //Use currentPatternInterpSumBus
		if(patternInterpSumBus != nil, {
			//FUNDAMENTAL: check that the bus is still actually valid and hasn't been freed yet.
			//In case it's been freed, it means the synths have already been freed
			if(patternInterpSumBus.bus != nil, {
				algaPattern.createPatternParamSynth(
					entry: entry,
					uniqueID: uniqueID,
					paramName: paramName,
					paramNumChannels: paramNumChannels,
					paramRate: paramRate,
					paramDefault: paramDefault,
					patternInterpSumBus: patternInterpSumBus,
					patternBussesAndSynths: nil,
					scaleArraysAndChansAtParam: scaleArraysAndChansAtParam,
					sampleAndHold: false,
					algaPatternInterpStreams: this,
					isTemporary: true
				)
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
			var sampleAndHoldAtParam        = sampleAndHolds[paramName];

			//Remove entry from entries and deal with inNodes / outNodes for both receiver and sender
			if(entriesAtParam != nil, {
				var entryAtParam = entriesAtParam[uniqueID];
				entriesAtParam.removeAt(uniqueID);
			});

			//Remove scaleArray and chans
			if(scaleArraysAndChansAtParam != nil, {
				var scaleArrayAndChansAtParam = scaleArraysAndChansAtParam[uniqueID];
				scaleArraysAndChansAtParam.removeAt(uniqueID);
				if(scaleArrayAndChansAtParam != nil, {
					algaPattern.removeScaling(paramName, uniqueID);
				});
			});

			//Remove sampleAndHold
			if(sampleAndHoldAtParam != nil, { sampleAndHolds.removeAt(paramName) });

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
				if(interpBusAtParam != nil, { interpBussesToFree.add(interpBusAtParam) });
			});
		});
	}

	//Wrapper around AlgaNode's addInOutNodesDict.
	//If entry is a ListPattern, loop around it and add each entry that is an AlgaNode.
	addInOutNodesDictAtParam { | sender, param, mix = false |
		algaPattern.addInOutNodesDict(sender, param, mix)
	}

	//Wrapper around AlgaNode's removeInOutNodesDict.
	//If entry is a ListPattern, loop around it and remove each entry that is an AlgaNode.
	removeAllInOutNodesDictAtParam { | paramName |
		algaPattern.removeInOutNodesDict(nil, paramName) //nil removes them all
	}

	//add a scaleArray and chans
	addScaleArrayAndChans { | paramName, paramNumChannels, uniqueID, chans, scale |
		var scaleArraysAndChansAtParam = scaleArraysAndChans[paramName];

		//Pattern support
		chans = chans.asStream;
		scale = scale.asStream;

		if(scaleArraysAndChansAtParam == nil, {
			scaleArraysAndChans[paramName] = IdentityDictionary().put(uniqueID, [scale, chans]);
		}, {
			scaleArraysAndChans[paramName].put(uniqueID, [scale, chans]);
		});
	}

	//Add entry / entryOriginal to dictionaries
	addEntry { | entry, paramName, uniqueID |
		var  entriesAtParam = entries[paramName];
		//Either create a new Dict for the param, or add to existing one
		if(entriesAtParam == nil, {
			entries[paramName] = IdentityDictionary().put(uniqueID, entry);
			^true; //first entry
		}, {
			entries[paramName].put(uniqueID, entry);
			^false; //not first entry
		});
		^false;
	}

	//Add sampleAndHold at param. It's generic for that param!
	addSampleAndHold { | paramName, sampleAndHold |
		sampleAndHolds[paramName] = sampleAndHold
	}

	//Add a new sender interpolation to the current param
	add { | entry, controlName, chans, scale, sampleAndHold, time = 0 |
		var paramName, paramRate, paramNumChannels, paramDefault;
		var uniqueID;

		var entryOriginal = entry; //Original entry, not .asStream. Needed for addInOutNodesDictAtParam
		var isFirstEntry;

		if(controlName == nil, {
			("AlgaPatternInterpStreams: Invalid controlName for param '" ++ paramName ++ "'").error
		});

		paramName = controlName.name;
		paramRate = controlName.rate;
		paramNumChannels = controlName.numChannels;
		paramDefault = controlName.defaultValue;

		//If entry is nil, use paramDefault (used for .reset)
		if(entry == nil, { entry = paramDefault; entryOriginal = paramDefault });

		//Interpret entry asStream
		entry = entry.asStream;

		//Use an unique id as index as it's more reliable than using the entry as key:
		//entry could very well be a number (like 440), screwing things up in IdentityDict.
		uniqueID = UniqueID.next;

		//Add entry to dict
		isFirstEntry = this.addEntry(entry, paramName, uniqueID);

		//Add the scaleArray and chans
		this.addScaleArrayAndChans(paramName, paramNumChannels, uniqueID, chans, scale);

		//Add sampleAndHold
		this.addSampleAndHold(paramName, sampleAndHold);

		//Remove all older inNodes / outNodes... if not mix, in theory.
		this.removeAllInOutNodesDictAtParam(paramName);

		//Add proper inNodes / outNodes / connectionTimeOutNodes. Use entryOriginal in order
		//to retrieve if it is a ListPattern.
		this.addInOutNodesDictAtParam(entryOriginal, paramName, false);

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

		//Create a temporary pattern param synth to start the interpolation process with.
		//Only one per-change is needed, as the interpolation process will continue as soon as the pattern
		//triggers new synth. This just avoids to have silences and gaps when modifying a param mid-pattern.
		//This must come AFTER createPatternInterpSynthAndBusAtParam.
		if((isFirstEntry.not).and(sampleAndHold.not), {
			this.createTemporaryPatternParamSynthAtParam(
				entry: entry,
				uniqueID: uniqueID,
				paramName: paramName,
				paramNumChannels: paramNumChannels,
				paramRate: paramRate,
				paramDefault: paramDefault
			);
		});
	}

	//Play a pattern as an AlgaReschedulingEventStreamPlayer and return it
	playAlgaReschedulingEventStreamPlayer { | pattern, clock |
		algaReschedulingEventStreamPlayer = pattern.playAlgaRescheduling(clock:clock)
	}
}

//This class is used to specify individual parameters of a pattern argument.
//It can be used to dynamically set parameters of a connected Node (like scale and chans).
//This of course only works with the "from" construct
AlgaPatternArg {
	var <sender, <chans, <scale;

	*new { | sender, chans, scale |
		^super.new.init(sender, chans, scale)
	}

	init { | argSender, argChans, argScale |
		sender = argSender.asStream; //Pattern support
		chans  = argChans.asStream;  //Pattern support
		scale  = argScale.asStream;  //Pattern support
	}

	isAlgaPatternArg { ^true }
}

//Alias for AlgaPatternArg
AlgaArg : AlgaPatternArg {}

//AlgaPattern
AlgaPattern : AlgaNode {
	/*
	TODOs:

	1) channels / rate conversion before synthBus?

	2) mixFrom() / mixTo()

	3) out: (See next)

	- out: (node: Pseq([a, b], time: 1, scale: 1)

	// Things to do:
	// 1) Check if it is an AlgaNode or a ListPattern of AlgaNodes
	// 2) It would just connect to nodes with the \in param, mixing ( mixTo / mixFrom )
	// 3) Can't be interpolated (but the connection itself can)
	*/

	//The actual Patterns to be manipulated
	var <pattern;

	//The Event input
	var <eventPairs;

	//interpStreams. These varies on .replace
	var <interpStreams;

	//Set \dur interpolation behaviour. Either run .replace or change at sched.
	var <replaceDur = false;

	//IdentitySet of paramSynths that get temporarily created for mid-pattern interpolation
	var <>temporaryParamSynths;

	//Keep track of the CURRENT patternInterpSumBus AlgaBus. This is updated every pattern trigger.
	//It is fundamental to have the current one in order to add do it a mid-pattern synth when user
	//changes a param mid-pattern.
	var <currentPatternInterpSumBus;

	//Current fx
	var <currentFX;

	//Add the \algaNote event to Event
	*initClass {
		//StartUp.add is needed: Event class must be compiled first
		StartUp.add({
			this.addAlgaNoteEventType;
		});
	}

	//Doesn't have args and outsMapping like AlgaNode. Default sched to 1 (so it plays on clock)
	*new { | def, args, connectionTime = 0, playTime = 0, sched = 1, server |
		^super.new(
			def: def,
			args: args,
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

			//AlgaPattern, the synthBus and its server / clock
			var algaPattern = ~algaPattern;
			var algaSynthBus = ~algaSynthBus;
			var algaPatternServer = ~algaPatternServer;
			var algaPatternClock = ~algaPatternClock;

			//fx
			var fx = ~fx;

			//The interpStreams the Pattern is using
			var algaPatternInterpStreams = ~algaPatternInterpStreams;

			//Other things for pattern syncing / clocking / scheduling
			var offset = ~timingOffset;
			var lag = ~lag;

			//Needed ?
			~isPlaying = true;

			//Create the bundle with all needed Synths for this Event.
			bundle = algaPatternServer.makeBundle(false, {
				~algaPattern.createEventSynths(
					algaSynthDef,
					algaSynthBus,
					algaPatternInterpStreams,
					fx
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
	setDur { | value, newInterpStreams |
		if(newInterpStreams == nil, {
			interpStreams.dur = value.asStream
		}, {
			newInterpStreams.dur = value.asStream
		});
	}

	//Set replaceDur
	replaceDur_ { | value = false |
		if((value != false).and(value != true), {
			"AlgaPattern: replaceDur only supports boolean values. Setting it to false".error
			^nil
		});
		replaceDur = value
	}

	//Free all unused busses from interpStreams
	freeUnusedInterpBusses { | algaPatternInterpStreams |
		var interpBussesToFree = algaPatternInterpStreams.interpBussesToFree.copy;
		algaPatternInterpStreams.interpBussesToFree.clear;

		//For safety reasons, wait a little bit longer to actually .free the interpBusses.
		//There are cases with very quick durs where the sync is wrong, but this fixes it,
		//albeit being non elegant at all (find a better solution, perhaps).
		fork {
			0.5.wait;
			interpBussesToFree.do({ | interpBus | interpBus.free });
		}
	}

	//Create one pattern synth with the entry / uniqueID pair at paramName
	//This is the core of the interpolation behaviour for AlgaPattern !!
	createPatternParamSynth { | entry, uniqueID, paramName, paramNumChannels, paramRate,
		paramDefault, patternInterpSumBus, patternBussesAndSynths, scaleArraysAndChansAtParam,
		sampleAndHold, algaPatternInterpStreams, isFX = false, isTemporary = false |

		var sender, senderNumChannels, senderRate;
		var chansMapping, scale;
		var validParam = false;

		//Unpack Pattern value
		if(entry.isStream, { entry = entry.next });

		//Check if it's an AlgaPatternArg. Unpack it.
		if(entry.isAlgaPatternArg, {
			chansMapping = entry.chans;
			scale        = entry.scale;
			entry        = entry.sender;
			//Unpack Pattern value
			if(entry.isStream, { entry = entry.next });
		});

		//Fallback sender (modified for AlgaNode, needed for chansMapping)
		sender = entry;

		//Valid values are Numbers / Arrays / AlgaNodes
		case

		//Number / Array
		{ entry.isNumberOrArray } {
			if(entry.isSequenceableCollection, {
				//an array
				senderNumChannels = entry.size;
				senderRate = "control";
			}, {
				//a num
				senderNumChannels = 1;
				senderRate = "control";
			});

			validParam = true;
		}

		//AlgaNode
		{ entry.isAlgaNode } {
			sender = entry; //essential for chansMapping (entry gets modified)
			if(entry.algaInstantiated, {
				if((entry.algaCleared).or(entry.algaToBeCleared), {
					("AlgaPattern: can't connect to an AlgaNode that's been cleared").error;
				}, {
					if(entry.synthBus != nil, {
						if(entry.synthBus.bus != nil, {
							senderRate = entry.rate;
							senderNumChannels = entry.numChannels;
							entry = entry.synthBus.busArg;
							validParam = true;
						}, {
							("AlgaPattern: can't connect to an AlgaNode with an invalid synthBus").error;
						});
					}, {
						("AlgaPattern: can't connect to an AlgaNode with an invalid synthBus").error;
					});
				});
			}, {
				//otherwise, use default
				senderRate = "control";
				senderNumChannels = paramNumChannels;
				entry = paramDefault;
				("AlgaPattern: AlgaNode wasn't algaInstantiated yet. Using default value for " ++ paramName).warn;
				validParam = true;
			});
		}

		//Buffer
		{ entry.isBuffer } {
			entry = entry.bufnum;
			senderNumChannels = 1;
			senderRate = "control";
			validParam = true;
		}

		//Nil, use default
		{ entry.isNil } {
			senderRate = "control";
			senderNumChannels = paramNumChannels;
			entry = paramDefault;
			validParam = true;
		};

		if(validParam, {
			//Get the bus where interpolation envelope is written to...
			//REMEMBER that for AlgaPattern, interpSynths are actually JUST the
			//interpolation envelope, which is then passed through this individual synths!
			// ... Now, I need to keep track of all the active interpBusses instead, not retrievin
			//from interpBusses, which gets replaced in language, but should implement the same
			//behaviour of activeInterpSynths and get busses from there.
			var patternParamEnvBus;

			if(isFX.not, {
				//Not \fx parameter: retrieve correct envelope bus
				patternParamEnvBus = algaPatternInterpStreams.interpBusses[paramName][uniqueID]
			}, {
				//\fx parameter, envelope is not used
				patternParamEnvBus = 0 //still != nil, just to get it through. It's not used in FX.
			});

			if(patternParamEnvBus != nil, {
				var patternParamSynth;
				var patternParamSymbol;
				var patternParamSynthArgs;
				var scaleArrayAndChansAtParam;

				if(isFX.not, {
					//Args for patternParamSynth
					patternParamSynthArgs = [
						\in, entry,
						\env, patternParamEnvBus.busArg,
						\out, patternInterpSumBus.index,
						\fadeTime, 0
					];

					//get correct scaleArray and chans
					scaleArrayAndChansAtParam = scaleArraysAndChansAtParam[uniqueID];

					//If AlgaPatternArg's is nil, use argument's one (if defined)
					if(scale == nil, {
						if(scaleArrayAndChansAtParam != nil, { scale = scaleArrayAndChansAtParam[0] }); //0 == scaleArray
					});
				}, {
					//FX has no env
					patternParamSynthArgs = [
						\in, entry,
						\out, patternInterpSumBus.index,
						\fadeTime, 0
					];
				});

				if(scale != nil, {
					//Pattern support
					if(scale.isStream, { scale = scale.next });

					if(scale != nil, {
						var scaleArray = this.calculateScaling(
							paramName,
							sender,
							paramNumChannels,
							scale,
							false //don't update the AlgaNode's scalings dict
						);

						patternParamSynthArgs = patternParamSynthArgs.addAll(scaleArray);
					});
				});

				//\fx parameter does not use global scaleArrayAndChans
				if(isFX.not, {
					//If AlgaPatternArg's is nil, use argument's one (if defined)
					if(chansMapping == nil, {
						if(scaleArrayAndChansAtParam != nil, { chansMapping = scaleArrayAndChansAtParam[1] }); //1 == chans
					});
				});

				//only apply chansMapping to AlgaNodes
				if((chansMapping != nil).and(sender.isAlgaNode), {
					//Pattern support
					if(chansMapping.isStream, { chansMapping = chansMapping.next });

					if(chansMapping != nil, {
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

				//sampleAndHold (use == true) as sampleAndHold could be nil too
				if(sampleAndHold == true, {
					patternParamSynthArgs = patternParamSynthArgs.add(\sampleAndHold).add(1)
				});

				if(isFX.not, {
					//Standard case
					patternParamSymbol = (
						"alga_pattern_" ++
						senderRate ++
						senderNumChannels ++
						"_" ++
						paramRate ++
						paramNumChannels
					).asSymbol;
				}, {
					//\fx parameter case
					patternParamSymbol = (
						"alga_pattern_" ++
						senderRate ++
						senderNumChannels ++
						"_" ++
						paramRate ++
						paramNumChannels ++
						"_fx"
					).asSymbol;
				});

				//Actual synth for the param
				patternParamSynth = AlgaSynth(
					patternParamSymbol,
					patternParamSynthArgs,
					interpGroup,
					\addToTail,
					waitForInst: false
				);

				//If NOT temporary, register patternParamSynth to be freed
				if(isTemporary.not, {
					patternBussesAndSynths.add(patternParamSynth)
				}, {
					//Else, register its deletion to the NEXT trigger
					temporaryParamSynths.add(patternParamSynth)
				});
			});
		}, {
			("AlgaPattern: Invalid class '" ++ entry.class ++ "' for parameter '" ++ paramName.asString ++ "'").error;
		});
	}

	//Create all pattern synths per-param
	createPatternParamSynths { | paramName, paramNumChannels, paramRate,
		paramDefault, patternInterpSumBus, patternBussesAndSynths,
		interpStreamsEntriesAtParam, scaleArraysAndChansAtParam, sampleAndHold,
		algaPatternInterpStreams, fx |

		if(interpStreamsEntriesAtParam != nil, {
			interpStreamsEntriesAtParam.keysValuesDo({ | uniqueID, entry | //indexed by uniqueIDs
				this.createPatternParamSynth(
					entry: entry,
					uniqueID: uniqueID,
					paramName: paramName,
					paramNumChannels: paramNumChannels,
					paramRate: paramRate,
					paramDefault: paramDefault,
					patternInterpSumBus: patternInterpSumBus,
					patternBussesAndSynths: patternBussesAndSynths,
					scaleArraysAndChansAtParam: scaleArraysAndChansAtParam,
					sampleAndHold: sampleAndHold,
					algaPatternInterpStreams: algaPatternInterpStreams
				)
			});
		});
	}

	//Free all temporary synths
	freeAllTemporaryParamSynths {
		temporaryParamSynths.do({ | paramSynth | paramSynth.free });
		temporaryParamSynths.clear;
	}

	//Create all needed Synths and Busses for an FX
	createFXSynthAndPatternSynth { | fx, algaSynthDef, algaSynthBus, algaPatternInterpStreams,
		patternSynthArgs, patternBussesAndSynths |
		var patternBussesAndSynthsFx = IdentitySet();
		var def = fx[\def];
		var explicitFree = fx[\explicitFree];
		var controlNamesAtFX = fx[\controlNames];

		//NumChannels / rate of the \in param of fx
		var fxInNumChannels = fx[\inNumChannels];
		var fxInRate = fx[\inRate];

		//NumChannels / rate of the fx
		var fxNumChannels = fx[\numChannels];
		var fxRate = fx[\rate];

		//Create the Bus patternSynth will write to, using the final numChannels / rate combination
		var patternBus = AlgaBus(server, numChannels, rate);

		//Create the conversion Bus from patternInterpSynth and fxSynth
		var patternInterpInBus = AlgaBus(server, fxInNumChannels, fxInRate);

		//Create the Bus fxSynth will write to
		var fxBus = AlgaBus(server, fxNumChannels, fxRate);

		//Args to patternInterpInSynth
		var patternInterpInSynthArgs = [
			\in, patternBus.busArg,
			\out, patternInterpInBus.index
		];

		//Args to fxSynth
		var fxSynthArgs = [
			\in, patternInterpInBus.busArg,
			\out, fxBus.index
		];

		//Args to fxInterpSynth
		var fxInterpSynthArgs = [
			\in, fxBus.busArg,
			\out, algaSynthBus.index
		];

		//Synth that converts \in from patternSynth
		var patternInterpInSynthSymbol;
		var patternInterpInSynth;

		//Actual fxSynth
		var fxSynthSymbol;
		var fxSynth;

		//Synth that converts \out from fxSynth
		var fxInterpSynthSymbol;
		var fxInterpSynth;

		//patternSynth -> \in
		patternInterpInSynthSymbol = (
			"alga_pattern_" ++
			rate ++
			numChannels ++
			"_" ++
			fxInRate ++
			fxInNumChannels ++
			"_fx"
		).asSymbol;

		//The user's def
		fxSynthSymbol = def;

		//fxSynth -> algaSynthBus
		fxInterpSynthSymbol = (
			"alga_pattern_" ++
			fxRate ++
			fxNumChannels ++
			"_" ++
			rate ++
			numChannels ++
			"_fx"
		).asSymbol;

		//Unpack parameters (same behaviour as createPatternParamSynth's unpacking)
		controlNamesAtFX.do({ | controlName |
			var paramName = controlName.name;
			var paramNumChannels = controlName.numChannels;
			var paramRate = controlName.rate;
			var paramDefault = controlName.defaultValue;
			var entry = fx[paramName];

			//Ignore static params
			if((paramName != '?').and(paramName != \instrument).and(
				paramName != \def).and(paramName != \out).and(
				paramName != \gate).and(paramName != \in), {

				//Temporary bus that the patternParamSynth for the fx will write to
				var patternParamBus = AlgaBus(server, paramNumChannels, paramRate);

				//Add bus to fxSynth at correct paramName
				fxSynthArgs = fxSynthArgs.add(paramName).add(patternParamBus.busArg);

				//Register bus to be freed
				patternBussesAndSynthsFx.add(patternParamBus);

				//Create a patternParamSynth for the fx param
				this.createPatternParamSynth(
					entry: entry,
					uniqueID: nil,
					paramName: paramName,
					paramNumChannels: paramNumChannels,
					paramRate: paramRate,
					paramDefault: paramDefault,
					patternInterpSumBus: patternParamBus, //pass the new Bus
					patternBussesAndSynths: patternBussesAndSynthsFx,
					isFX: true
				)
			});
		});

		//Create patternInterpInSynth
		patternInterpInSynth = AlgaSynth(
			patternInterpInSynthSymbol,
			patternInterpInSynthArgs,
			synthGroup,
			\addToTail,
			false
		);

		//Create fxSynth, addToTail
		fxSynth = AlgaSynth(
			fxSynthSymbol,
			fxSynthArgs,
			synthGroup,
			\addToTail,
			false
		);

		//Create fxInterpSynth
		fxInterpSynth = AlgaSynth(
			fxInterpSynthSymbol,
			fxInterpSynthArgs,
			synthGroup,
			\addToTail,
			false
		);

		//Add all to patternBussesAndSynthsFx (except fxSynth)
		patternBussesAndSynthsFx.add(patternBus);
		patternBussesAndSynthsFx.add(patternInterpInBus);
		patternBussesAndSynthsFx.add(fxBus);
		patternBussesAndSynthsFx.add(patternInterpInSynth);
		patternBussesAndSynthsFx.add(fxInterpSynth);

		//If not explicitFree, add fxSynth to the free mechanism, otherwise
		//it will be handled by itself
		if(explicitFree.not, {
			patternBussesAndSynths.add(fxSynth);
		});

		//FUNDAMENTAL: add fxSynth to the algaPatternSynths,
		//so that algaSynthBus is kept alive for the WHOLE duration of the fx too.
		algaPatternInterpStreams.algaPatternSynths.add(fxSynth);

		//Free all relative synths / busses on fxSynth free.
		//fxSynth is either freed by itself (if explicitFree),
		//or when patternSynth is freed.
		fxSynth.onFree({
			//Free synths and busses
			patternBussesAndSynthsFx.do({ | synthOrBus | synthOrBus.free });

			//Remove fxSynth from algaPatternSynths
			algaPatternInterpStreams.algaPatternSynths.remove(fxSynth);
		});

		//Use patternBus as \out for patternSynth
		patternSynthArgs = patternSynthArgs.add(\out).add(patternBus.index);

		//Return patternSynthArgs
		^patternSynthArgs;
	}

	//Create all needed Synths for this Event. This is triggered by the \algaNote Event
	createEventSynths { | algaSynthDef, algaSynthBus, algaPatternInterpStreams, fx |
		//These will be populated and freed when the patternSynth is released
		var patternBussesAndSynths = IdentitySet(controlNames.size * 2);

		//args to patternSynth
		var patternSynthArgs = [ \gate, 1 ];

		//The actual synth that will be created
		var patternSynth;

		//FIRST THING, free all dangling temporary synths. These are created to perform
		//mid-pattern interpolation of parameters.
		this.freeAllTemporaryParamSynths;

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
			var interpStreamsEntriesAtParam = algaPatternInterpStreams.entries[paramName];

			//scaleArray and chans
			var scaleArraysAndChansAtParam = algaPatternInterpStreams.scaleArraysAndChans[paramName];

			//sampleAndHold
			var sampleAndHold = algaPatternInterpStreams.sampleAndHolds[paramName];
			sampleAndHold = sampleAndHold ? false;

			//Create all interp synths for current param
			this.createPatternParamSynths(
				paramName: paramName,
				paramNumChannels: paramNumChannels,
				paramRate: paramRate,
				paramDefault: paramDefault,
				patternInterpSumBus: patternInterpSumBus,
				patternBussesAndSynths: patternBussesAndSynths,
				interpStreamsEntriesAtParam: interpStreamsEntriesAtParam,
				scaleArraysAndChansAtParam: scaleArraysAndChansAtParam,
				sampleAndHold: sampleAndHold,
				algaPatternInterpStreams: algaPatternInterpStreams
			);

			//Read from patternParamNormBus
			patternSynthArgs = patternSynthArgs.add(paramName).add(patternParamNormBus.busArg);

			//Register normBus, normSynth, interSumBus to be freed
			patternBussesAndSynths.add(patternParamNormBus);
			patternBussesAndSynths.add(patternParamNormSynth);
			patternBussesAndSynths.add(patternInterpSumBus);

			//Current patternInterpSumBus
			currentPatternInterpSumBus = patternInterpSumBus;
		});

		//If fx, deal with it
		if((fx != nil).and(fx.class == Event), {
			//This returns the patternSynthArgs with correct bus to write to (the fx one)
			patternSynthArgs = this.createFXSynthAndPatternSynth(
				fx: fx,
				algaSynthDef: algaSynthDef,
				algaSynthBus: algaSynthBus,
				algaPatternInterpStreams: algaPatternInterpStreams,
				patternSynthArgs: patternSynthArgs,
				patternBussesAndSynths: patternBussesAndSynths
			);
		}, {
			//NO FX
			//Add \out bus of patternSynth: directly to algaSynthBus
			patternSynthArgs = patternSynthArgs.add(\out).add(algaSynthBus.index);
		});

		//The actual patternSynth according to the user's def
		patternSynth = AlgaSynth(
			algaSynthDef,
			patternSynthArgs,
			synthGroup,
			waitForInst: false
		);

		//Add pattern synth to algaPatternSynths, and free it when patternSynth gets freed
		algaPatternInterpStreams.algaPatternSynths.add(patternSynth);

		//Free all normBusses, normSynths, interpBusses and interpSynths on patternSynth's release
		patternSynth.onFree( {
			//Free all Synths and Busses
			patternBussesAndSynths.do({ | synthOrBus | synthOrBus.free });

			//IMPORTANT: free the unused interpBusses of the interpStreams.
			//This needs to happen on patternSynth's free as that's the notice
			//that no other synths will be using them. Also, this fixes the case where
			//patternSynth takes longer than \dur. We want to wait for the end of patternSynth
			//to free all used things!
			this.freeUnusedInterpBusses(algaPatternInterpStreams);

			//Remove the entry from algaPatternSynths
			algaPatternInterpStreams.algaPatternSynths.remove(patternSynth);
		});
	}

	//dispatchNode: first argument is an Event or SynthDef
	dispatchNode { | def, args, initGroups = false, replace = false,
		keepChannelsMapping = false, outsMapping, keepScale = false, sched = 0 |

		var defEntry;

		//Just a Symbol for a SynthDef: wrap it in an Event
		if(def.class == Symbol, { def = (\def: def) });

		//def: entry
		defEntry = def[\def];
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

		//Create args dict
		this.createDefArgs(args);

		case
		{ defClass == Symbol } {
			^this.dispatchSynthDef(
				def: defEntry,
				initGroups: initGroups,
				replace: replace,
				keepChannelsMapping:keepChannelsMapping,
				keepScale:keepScale,
				sched:sched
			)
		}
		{ defEntry.isListPattern } {
			^this.dispatchListPattern(
				def: defEntry,
				initGroups: initGroups,
				replace: replace,
				keepChannelsMapping:keepChannelsMapping,
				keepScale:keepScale,
				sched:sched
			)
		}
		{ defClass == Function } {
			^this.dispatchFunction(
				def: defEntry,
				initGroups: initGroups,
				replace: replace,
				keepChannelsMapping:keepChannelsMapping,
				outsMapping:outsMapping,
				keepScale:keepScale,
				sched:sched
			)
		};

		("AlgaPattern: class '" ++ defClass ++ "' is an invalid 'def'").error;
	}

	//Overloaded function
	buildFromSynthDef { | initGroups = false, replace = false,
		keepChannelsMapping = false, keepScale = false, sched = 0 |

		var synthDescControlNames;

		//Detect if AlgaSynthDef can be freed automatically. Otherwise, error!
		if(synthDef.explicitFree.not, {
			("AlgaPattern: AlgaSynthDef '" ++ synthDef.name.asString ++ "' can't free itself: it doesn't implement any Done action.").error;
			^this
		});

		//Retrieve controlNames from SynthDesc
		synthDescControlNames = synthDef.asSynthDesc.controls;

		//Create controlNames
		this.createControlNamesAndParamsConnectionTime(synthDescControlNames);

		//Retrieve channels and rate
		numChannels = synthDef.numChannels;
		rate = synthDef.rate;

		//Sched must be a num
		sched = sched ? 0;

		//Generate outsMapping (for outsMapping connectinons)
		this.calculateOutsMapping(replace, keepChannelsMapping);

		//Create groups if needed
		if(initGroups, { this.createAllGroups });

		//Create synthBus for output
		//interpBusses are taken care of in createPatternInterpSynthAndBus.
		this.createSynthBus;

		//Create the actual pattern.
		this.createPattern(replace, keepChannelsMapping, keepScale, sched);
	}

	//Build spec from ListPattern
	buildFromListPattern { | initGroups = false, replace = false,
		keepChannelsMapping = false, keepScale = false, sched = 0 |

		//Sched must be a num
		sched = sched ? 0;

		//Generate outsMapping (for outsMapping connectinons)
		if(this.calculateOutsMapping(replace, keepChannelsMapping) == nil, { ^this });

		//Create groups if needed
		if(initGroups, { this.createAllGroups });

		//Create synthBus for output
		//interpBusses are taken care of in createPatternInterpSynthAndBus.
		this.createSynthBus;

		//Create the actual pattern.
		this.createPattern(replace, keepChannelsMapping, keepScale, sched);
	}

	//Check rates, numChannels, Symbols and controlNames
	checkListPatternValidityAndReturnControlNames { | listPattern |
		var numChannelsCount, rateCount;
		var controlNamesDict = IdentityDictionary();
		var controlNamesSum = Array.newClear;

		listPattern.list.do({ | entry |
			var synthDescEntry, synthDefEntry;
			var controlNamesEntry;
			var controlNamesListPatternDefaultsEntry;

			//If another ListPattern, recursively add stuff
			if(entry.isListPattern, {
				controlNamesSum = controlNamesSum.addAll(this.checkListPatternValidityAndReturnControlNames(entry));
			}, {
				//Only support Symbols (not Functions, too much of a PITA)
				if(entry.class != Symbol, {
					"AlgaPattern: the ListPattern defining 'def' can only contain Symbols pointing to valid AlgaSynthDefs".error;
					^nil;
				});

				synthDescEntry = SynthDescLib.global.at(entry);

				if(synthDescEntry == nil, {
					("AlgaPattern: Invalid AlgaSynthDef: '" ++ entry.asString ++ "'").error;
					^nil;
				});

				synthDefEntry = synthDescEntry.def;

				if(synthDefEntry.class != AlgaSynthDef, {
					("AlgaPattern: Invalid AlgaSynthDef: '" ++ entry.asString ++"'").error;
					^nil;
				});

				if(synthDefEntry.explicitFree.not, {
					("AlgaPattern: AlgaSynthDef '" ++ synthDefEntry.name.asString ++ "' can't free itself: it doesn't implement any Done action.").error;
					^nil
				});

				if(numChannelsCount == nil, { numChannelsCount = synthDefEntry.numChannels });
				if(rateCount == nil, { rateCount = synthDefEntry.rate });

				if(synthDefEntry.numChannels != numChannelsCount, {
					("AlgaPattern: the '" ++ entry.asString ++ "' def has a different channels count than the other entries. Got " ++ synthDefEntry.numChannels ++ " but expected " ++ numChannelsCount).error;
					^nil;
				});

				if(synthDefEntry.rate != rateCount, {
					("AlgaPattern: the '" ++ entry.asString ++ "' def has a different rate than the other entries. Got '" ++ synthDefEntry.rate ++ "' but expected '" ++ rateCount ++ "'").error;
					^nil;
				});

				numChannelsCount = synthDefEntry.numChannels;
				rateCount = synthDefEntry.rate;

				numChannels = numChannelsCount; //global
				rate = rateCount; //global

				controlNamesEntry = synthDescEntry.controls;

				controlNamesEntry.do({ | controlName |
					var name = controlName.name;
					if((name != \fadeTime).and(
						name != \out).and(
						name != \gate).and(
						name != '?'), {
						if(controlNamesDict[name] == nil, {
							controlNamesDict[name] = controlName;
							controlNamesSum = controlNamesSum.add(controlName);
						}, {
							var rate = controlNamesDict[name].rate;
							var numChannels = controlNamesDict[name].numChannels;
							var newRate = controlName.rate;
							var newNumChannels = controlName.numChannels;
							if(rate != newRate, {
								("AlgaPattern: rate mismatch of SynthDef '" ++ entry ++ "' for parameter '" ++ name ++ "'. Expected '" ++ rate ++ "' but got '" ++ newRate ++ "'").error;
								^nil
							});
							if(numChannels != newNumChannels, {
								("AlgaPattern: channels mismatch of SynthDef '" ++ entry ++ "' for parameter '" ++ name ++ "'. Expected " ++ numChannels ++ " but got " ++ newNumChannels).error;
								^nil
							});
						});
					});
				});
			});
		});

		^controlNamesSum;
	}

	//Multiple Symbols over a ListPattern
	dispatchListPattern { | def, initGroups = false, replace = false,
		keepChannelsMapping = false, keepScale = false, sched = 0 |

		//Check rates, numChannels, Symbols and controlNames
		var controlNamesSum = this.checkListPatternValidityAndReturnControlNames(def);
		if(controlNamesSum == nil, { ^this });

		//Create controlNames
		this.createControlNamesAndParamsConnectionTime(controlNamesSum);

		//synthDef will be the ListPattern
		synthDef = def;

		this.buildFromListPattern(
			initGroups, replace,
			keepChannelsMapping:keepChannelsMapping,
			keepScale:keepScale,
			sched:sched
		);
	}

	//Parse a single \fx event
	parseFXEvent { | value |
		var foundInParam = false;
		var synthDescFx, synthDefFx, controlNamesFx;
		var def;

		//If any other class but Event, return. This value will be ignored in createEventSynths.
		//This can be used to pass Symbols like \none or \dry to just passthrough the sound
		if(value.class != Event, { ^value });

		//Find \def
		def = value[\def];
		if(def == nil, {
			("AlgaPattern: no 'def' entry in 'fx': '" ++ value.asString ++ "'").error;
			^nil
		});

		//Don't support ListPatterns for now...
		if(def.class != Symbol, {
			("AlgaPattern: 'fx' only supports symbols as 'def'").error;
			^nil
		});

		//Check that \def is valid
		synthDescFx = SynthDescLib.global.at(def);

		if(synthDescFx == nil, {
			("AlgaPattern: Invalid AlgaSynthDef in 'fx': '" ++ def.asString ++ "'").error;
			^nil;
		});

		synthDefFx = synthDescFx.def;

		if(synthDefFx.class != AlgaSynthDef, {
			("AlgaPattern: Invalid AlgaSynthDef in 'fx': '" ++ def.asString ++"'").error;
			^nil;
		});

		controlNamesFx = synthDescFx.controls;

		controlNamesFx.do({ | controlName |
			if(controlName.name == \in, {
				foundInParam = true;
				//Pass numChannels / rate of in param to Event
				value[\inNumChannels] = controlName.numChannels;
				value[\inRate] = controlName.rate;
			})
		});

		if(foundInParam.not, {
			("AlgaPattern: Invalid AlgaSynthDef in 'fx': '" ++ def.asString ++ "': It does not provide an 'in' parameter").error;
			^nil;
		});

		//Pass controlNames / numChannels / rate to Event
		value[\controlNames] = controlNamesFx;
		value[\numChannels] = synthDefFx.numChannels;
		value[\rate] = synthDefFx.rate;

		//Pass explicitFree to Event
		value[\explicitFree] = synthDefFx.explicitFree;

		//Loop over the event
		value.keysValuesDo({ | key, entry |
			//Use .asStream for each entry
			value[key] = entry.asStream
		});

		^value;
	}

	//Parse the \fx key
	parseFX { | value |
		case
		//Single Event
		{ value.class == Event } {
			^this.parseFXEvent(value);
		}

		//ListPattern of Events
		{ value.isListPattern } {
			value.list.do({ | listEntry, i |
				var result;
				result = this.parseFXEvent(listEntry);
				if(result == nil, { ^nil });
				value.list[i] = result;
			});
			^value;
		};
	}

	//Build the actual pattern
	createPattern { | replace = false, keepChannelsMapping = false, keepScale = false, sched |
		var foundDurOrDelta = false;
		var foundFX = false;
		var parsedFX;
		var patternPairs = Array.newClear;

		//Create new interpStreams. NOTE that the Pfunc in dur uses this, as interpStreams
		//will be overwritten when using replace. This allows to separate the "global" one
		//from the one that's being created here.
		var newInterpStreams = AlgaPatternInterpStreams(this);

		//Loop over the Event input from the user
		eventPairs.keysValuesDo({ | paramName, value |
			//Add \def key as \instrument
			if(paramName == \def, {
				patternPairs = patternPairs.add(\instrument).add(value);
			});

			//Found \dur or \delta
			if((paramName == \dur).or(paramName == \delta), {
				foundDurOrDelta = true;
				this.setDur(value, newInterpStreams);
			});

			//Add \fx key (parsing everything correctly)
			if(paramName == \fx, {
				parsedFX = this.parseFX(value);
				if(parsedFX != nil, {
					patternPairs = patternPairs.add(\fx).add(parsedFX);
					foundFX = true;
				});
			});
		});

		//Store current FX for replaces
		if(foundFX, {
			currentFX = parsedFX;
		}, {
			parsedFX = nil;
		});

		//If no dur and replace, get it from previous interpStreams
		if(replace, {
			if(foundDurOrDelta.not, {
				if((interpStreams == nil).or(algaWasBeingCleared), {
					this.setDur(1, newInterpStreams)
				}, {
					this.setDur(interpStreams.dur, newInterpStreams)
				});
			});

			//No \fx from user, use currentFX if available
			if(foundFX.not, {
				if(currentFX != nil, {
					patternPairs = patternPairs.add(\fx).add(currentFX);
				});
			});
		}, {
			//Else, default it to 1
			if(foundDurOrDelta.not, { this.setDur(1, newInterpStreams) });
		});

		//Add all the default entries from SynthDef that the user hasn't set yet
		controlNames.do({ | controlName |
			var paramName = controlName.name;
			var paramValue = eventPairs[paramName];
			var chansMapping, scale;

			//if not set explicitly yet
			if(paramValue == nil, {
				//When replace, getDefaultOrArg will return LATEST set parameter, via replaceArgs
				paramValue = this.getDefaultOrArg(controlName, paramName, replace);
			});

			//If replace, check if keeping chans / scale mappings
			if(replace, {
				if(keepChannelsMapping, {
					chansMapping = this.getParamChansMapping(paramName, paramValue)
				});
				if(keepScale, { scale = this.getParamScaling(paramName, paramValue) });
			});

			//Add to interpStream (which also creates interpBus / interpSynth).
			//These are unscheduled, as it's best to just create them asap.
			//Only the pattern synths need to be scheduled
			newInterpStreams.add(
				entry: paramValue,
				controlName: controlName,
				chans: chansMapping,
				scale: scale,
				sampleAndHold: false,
				time: 0
			);
		});

		//Set the correct synthBus in newInterpStreams!!!
		//This is fundamental for the freeing mechanism in stopPattern to work correctly
		//with the freeAllSynthsAndBussesOnReplace function call
		newInterpStreams.algaSynthBus = this.synthBus;

		//Add \type, \algaNode, and all things related to
		//the context of this AlgaPattern
		patternPairs = patternPairs.addAll([
			\type, \algaNote,
			\dur, Pfuncn( { newInterpStreams.dur.next }, inf), //Pfunc allows to modify the value
			\algaPattern, this,
			\algaSynthBus, this.synthBus, //Lock current one: will work on .replace
			\algaPatternServer, server,
			\algaPatternClock, this.clock,
			\algaPatternInterpStreams, newInterpStreams //Lock current one: will work on .replace
		]);

		//Create the Pattern by calling .next from the streams
		pattern = Pbind(*patternPairs);

		//Schedule the start of the pattern on the AlgaScheduler. All the rest in this
		//createPattern function is non scheduled as it it better to create it right away.
		scheduler.addAction(
			func: {
				newInterpStreams.playAlgaReschedulingEventStreamPlayer(pattern, this.clock)
			},
			sched: sched
		);

		//Update latest interpStreams
		interpStreams = newInterpStreams;
	}

	//Get valid synthDef name
	getSynthDef {
		if(synthDef.class == AlgaSynthDef, {
			//Normal synthDef
			^synthDef.name
		}, {
			//ListPatterns, mainly
			^synthDef
		});
	}

	//Set dur at sched
	setDurAtSched { | value, sched |
		//Set new \dur
		this.setDur(value);

		//Add to scheduler just to make cascadeMode work
		scheduler.addAction(
			condition: { this.algaInstantiated },
			func: {
				var algaReschedulingEventStreamPlayer = interpStreams.algaReschedulingEventStreamPlayer;
				if(algaReschedulingEventStreamPlayer != nil, {
					algaReschedulingEventStreamPlayer.rescheduleAtQuant(sched);
				})
			}
		);
	}

	//Interpolate dur (not yet)
	interpolateDur { | value, time, sched |
		if(replaceDur, {
			("AlgaPattern: 'dur' interpolation is not supported yet. Running .replace instead.").warn;
			^this.replace(
				def: (def: this.getSynthDef, dur: value),
				time: time,
				sched: sched
			);
		}, {
			if(sched == nil, { sched = 0 });
			("AlgaPattern: 'dur' interpolation is not supported yet. Rescheduling 'dur' at the " ++ sched ++ " quantization.").warn;
			^this.setDurAtSched(value, sched);
		});
	}

	//Interpolate def == replace
	interpolateDef { | def, time, sched |
		^this.replace(
			def: (def: def),
			time: time,
			sched: sched
		);
	}

	//Buffer == replace
	interpolateBuffer { | sender, param, time, sched |
		var args = [ param, sender ]; //New buffer connection... Should it be set in the def? (param: sender)?
		"AlgaPattern: changing a Buffer parameter. This will trigger 'replace'.".warn;
		^this.replace(
			def: (def: this.getSynthDef),
			args: args,
			time: time,
			sched: sched
		);
	}

	//Interpolate fx == replace
	interpolateFX { | value, time, sched |
		^this.replace(
			def: (def: this.getSynthDef, fx: value),
			time: time,
			sched: sched
		);
	}

	//ListPattern that contains Buffers
	patternOrAlgaPatternArgContainsBuffers { | pattern |
		if(pattern.isAlgaPatternArg, { if(pattern.sender.isBuffer, { ^true }) });

		if(pattern.isListPattern, {
			pattern.list.do({ | entry |
				if(entry.isBuffer, { ^true });
				if(entry.isAlgaPatternArg, { if(entry.sender.isBuffer, { ^true }) });
			});
		});

		^false
	}

	//<<, <<+ and <|
	makeConnectionInner { | param = \in, sender, senderChansMapping, scale, sampleAndHold, time = 0 |
		var isDefault = false;
		var paramConnectionTime = paramsConnectionTime[param];
		if(paramConnectionTime == nil, { paramConnectionTime = connectionTime });
		if(paramConnectionTime < 0, { paramConnectionTime = connectionTime });
		time = time ? paramConnectionTime;

		//If sender == nil, still pass through. In this case, use default value for param.
		//This is used in .reset
		if(sender == nil, { isDefault = true });

		if(isDefault.not, {
			if((sender.isAlgaNode.not).and(sender.isPattern.not).and(
				sender.isAlgaPatternArg.not).and(
				sender.isNumberOrArray.not).and(sender.isBuffer.not), {
				"AlgaPattern: makeConnection only works with AlgaNodes, AlgaPatterns, AlgaPatternArgs, Patterns, Numbers, Arrays and Buffers".error;
				^this;
			});
		});

		//Add scaling to Dicts
		if(scale != nil, { this.addScaling(param, sender, scale) });

		//Add to interpStreams (which also creates interpBus / interpSynth)
		interpStreams.add(
			entry: sender,
			controlName: controlNames[param],
			chans: senderChansMapping,
			scale: scale,
			sampleAndHold: sampleAndHold,
			time: time
		);
	}

	//<<, <<+ and <|
	makeConnection { | sender, param = \in, replace = false, mix = false,
		replaceMix = false, senderChansMapping, scale, sampleAndHold, time, sched |

		//Default to false
		sampleAndHold = sampleAndHold ? false;

		//Check if it's boolean
		if((sampleAndHold != true).and(sampleAndHold != false), {
			"AlgaPattern: sampleAndHold must be a boolean value".error;
			^this
		});

		//Special case: ListPattern with Buffers
		if(this.patternOrAlgaPatternArgContainsBuffers(sender), {
			^this.interpolateBuffer(sender, param, time, sched)
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
						sampleAndHold: sampleAndHold,
						time: time
					)
				},
				sched: sched
			)
		});
	}

	//Only from is needed: to already calls into makeConnection
	from { | sender, param = \in, chans, scale, sampleAndHold, time, sched |
		//delta == dur
		if(param == \delta, {
			param = \dur
		});

		//Special case, \dur
		if(param == \dur, {
			^this.interpolateDur(sender, time, sched);
		});

		//Special case, \def
		if(param == \def, {
			^this.interpolateDef(sender, time, sched);
		});

		//Special case, \fx
		if(param == \fx, {
			^this.interpolateFX(sender, time, sched);
		});

		//Buffer == replace
		if(sender.isBuffer, {
			^this.interpolateBuffer(sender, param, time, sched)
		});

		//Force pattern and AlgaPatternArg dispatch
		if((sender.isPattern).or(sender.isAlgaPatternArg), {
			^this.makeConnection(sender, param, senderChansMapping:chans,
				scale:scale, sampleAndHold:sampleAndHold, time:time, sched:sched
			);
		});

		//Standard cases
		if(sender.isAlgaNode, {
			if(this.server != sender.server, {
				("AlgaPattern: trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
			this.makeConnection(sender, param, senderChansMapping:chans,
				scale:scale, time:time, sampleAndHold:sampleAndHold, sched:sched
			);
		}, {
			//sender == symbol is used for \def
			if(sender.isNumberOrArray, {
				this.makeConnection(sender, param, senderChansMapping:chans,
					scale:scale, time:time, sampleAndHold:sampleAndHold, sched:sched
				);
			}, {
				("AlgaPattern: trying to enstablish a connection from an invalid class: " ++ sender.class).error;
			});
		});
	}

	// <<| \param (goes back to defaults)
	//When sender is nil in makeConnection, the default value will be used
	resetParam { | param = \in, sampleAndHold, time, sched |
		this.makeConnection(
			sender: nil,
			param: param,
			sampleAndHold: sampleAndHold,
			time: time,
			sched: sched
		)
	}

	//Alias for resetParam
	reset { | param = \in, sampleAndHold, time, sched |
		this.resetParam(param, sampleAndHold, time, sched)
	}

	//replace:
	// 1) replace the entire AlgaPattern with a new one (like AlgaNode.replace)
	// 2) replace just the SynthDef with either a new SynthDef or a ListPattern with JUST SynthDefs.
	//    This would be equivalent to <<.def \newSynthDef
	//    OR <<.def Pseq([\newSynthDef1, \newSynthDef2])

	//IMPORTANT: this function must be empty. It's called from replaceInner, but synthBus is actually
	//still being used by the pattern. It should only be freed when the pattern is freed, as it's done
	//in the stopPattern function. LEAVE THIS FUNCTION EMPTY, OR FAST PATTERNS WILL BUG OUT!!!
	freeAllBusses { | now = false, time | }

	//Called from replaceInner. freeInterpNormSynths is not used for AlgaPatterns
	freeAllSynths { | useConnectionTime = true, now = true, time |
		this.stopPattern(now, time);
	}

	//Used when replacing. Free synths and stop the current running pattern
	stopPattern { | now = true, time |
		if(interpStreams != nil, {
			if(now, {
				interpStreams.algaReschedulingEventStreamPlayer.stop;
				//freeAllSynthsAndBussesOnReplace MUST come after algaReschedulingEventStreamPlayer.stop!
				interpStreams.freeAllSynthsAndBussesOnReplace;
			}, {
				var interpStreamsOld = interpStreams;
				var algaReschedulingEventStreamPlayerOld = interpStreams.algaReschedulingEventStreamPlayer;
				if(time == nil, { time = longestWaitTime });
				if(algaReschedulingEventStreamPlayerOld != nil, {
					fork {
						(time + 1.0).wait;
						algaReschedulingEventStreamPlayerOld.stop;
						//freeAllSynthsAndBussesOnReplace MUST come after algaReschedulingEventStreamPlayer.stop!
						interpStreamsOld.freeAllSynthsAndBussesOnReplace;
					}
				});
			});
		});
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
		interpStreams.algaReschedulingEventStreamPlayer.reschedule(sched);
	}

	//Add entry to inNodes
	addInNodeAlgaNode { | sender, param = \in, mix = false |
		//Empty entry OR not doing mixing, create new IdentitySet. Otherwise, add to existing
		if((inNodes[param] == nil).or(mix.not), {
			inNodes[param] = IdentitySet[sender];
		}, {
			inNodes[param].add(sender);
		})
	}

	//Wrapper for addInNode
	addInNode { | sender, param = \in, mix = false |
		//First of all, remove the outNodes that the previous sender had with the
		//param of this node, if there was any. Only apply if mix==false (no <<+ / >>+)
		if(mix == false, {
			var oldSenderSet = inNodes[param];
			if(oldSenderSet != nil, {
				oldSenderSet.do({ | oldSender |
					oldSender.outNodes.removeAt(this);
				});
			});
		});

		//If AlgaArg or ListPattern, loop around entries and add each of them
		if(sender.isAlgaNode, {
			this.addInNodeAlgaNode(sender, param, mix);
		}, {
			if(sender.isAlgaPatternArg, {
				this.addInNodeAlgaNode(sender.sender, param, mix);
			}, {
				if(sender.isListPattern, {
					sender.list.do({ | listEntry |
						if(listEntry.isAlgaPatternArg, { listEntry = listEntry.sender });
						if(listEntry.isAlgaNode, {
							if(inNodes.size == 0, {
								this.addInNodeAlgaNode(listEntry, param, mix:false);
							}, {
								this.addInNodeAlgaNode(listEntry, param, mix:true);
							});
						});
					});
				});
			});
		});

		//Use replaceArgs to set LATEST parameter, for retrieval after .replace ...
		//AlgaNode only uses this for number parameters, but AlgaPattern uses it for any
		//kind of parameter, including AlgaNodes and AlgaArgs.
		replaceArgs[param] = sender;
	}

	//Called from clearInner
	resetAlgaPattern {
		temporaryParamSynths.clear;
		currentPatternInterpSumBus = nil;
		interpStreams = nil;
	}

	//Called from clearInner
	freeAllGroups { | now = false, time |
		this.freeAllSynths(false, now, time);
		super.freeAllGroups(now, time);
	}

	//There is no way to check individual synths.
	//So, let's at least check that the group must be insantiated
	algaInstantiated {
		if(algaCleared, { ^false });
		^(group.algaInstantiated);
	}

	//To send signal. algaInstantiatedAsReceiver is same as AlgaNode
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

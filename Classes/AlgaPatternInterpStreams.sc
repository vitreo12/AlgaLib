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

//This class holds all data for currently active interpSynths / interpBusses and relative
//metadata for an individual AlgaPattern. It's used on the pattern loop to retrieve
//which synths to play, and their current state.
AlgaPatternInterpStreams {
	var <algaPattern;
	var <server;

	//The time entries
	var <>dur, <>sustain, <>stretch, <>legato;

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

	//Used for AlgaPattern.stopPatternBeforeReplace
	var <>beingStopped = false;

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
	freeActiveInterpSynthsAtParam { | paramName, time = 0, shape |
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
					\envShape, shape.algaConvertEnv
				);
			});
		});
	}

	//This creates a temporary patternSynth for mid-pattern interpolation.
	//It will be freed at the start of the NEXT paramSynth.
	createTemporaryPatternParamSynthAtParam { | entry, uniqueID, paramName, paramNumChannels,
		paramRate, paramDefault, time = 0 |

		var clock = algaPattern.clock;

		//It's essential that this is scheduled at the bottom of the Clock.
		//This allows this action to always be executed AFTER the pattern triggers.
		clock.algaSchedAtQuantOnce(
			quant: 0, //Execute right now at the bottom of the TempoClock stack (after all eventual pattern triggers)
			task: {
				//The scale and chans of the interpStream
				var scaleArraysAndChansAtParam = scaleArraysAndChans[paramName];

				//These belong to the latest patternSynth created. Since the action was executed with
				//top priority, the latest bus will be the one created by the latest patternSynth.
				//This will then be used to write to if scheduling happens mid-pattern.
				var latestPatternInterpSumBusAtParam = algaPattern.latestPatternInterpSumBusses[paramName];
				var latestPatternTime = algaPattern.latestPatternTime;

				//These belong to all active patternSynths
				var patternBussesAndSynths = algaPattern.currentPatternBussesAndSynths[latestPatternInterpSumBusAtParam];
				var activePatternInterpSumBusses;

				//FUNDAMENTAL:
				//Only schedule if the same pattern HAS NOT been triggered at this very time.
				//This solves all scheduling issues, and allows schedule times that are not in sync to work
				if(latestPatternTime != clock.seconds, {
					if((latestPatternInterpSumBusAtParam != nil).and(patternBussesAndSynths != nil), {
						//FUNDAMENTAL: check that the bus is still actually valid and hasn't been freed yet.
						//In case it's been freed, it means the synths have already been freed
						if(latestPatternInterpSumBusAtParam.bus != nil, {
							algaPattern.createPatternParamSynth(
								entry: entry,
								uniqueID: uniqueID,
								paramName: paramName,
								paramNumChannels: paramNumChannels,
								paramRate: paramRate,
								paramDefault: paramDefault,
								patternInterpSumBus: latestPatternInterpSumBusAtParam,
								patternBussesAndSynths: patternBussesAndSynths,
								scaleArraysAndChansAtParam: scaleArraysAndChansAtParam,
								sampleAndHold: false,
								algaPatternInterpStreams: this,
								isTemporary: true
							)
						});
					});
				});

				//Retrieve active ones (including the newly created one)
				activePatternInterpSumBusses = algaPattern.currentActivePatternInterpSumBusses;

				//Add temporary patterns for all active patternInterpBusses at paramName
				if(activePatternInterpSumBusses != nil, {
					var activePatternInterpSumBussesAtParam = activePatternInterpSumBusses[paramName];
					if(activePatternInterpSumBussesAtParam != nil, {
						activePatternInterpSumBussesAtParam.do({ | patternInterpBusAtParam |
							//Don't do it for the patternInterpBusAtParam that's just been created!
							if(patternInterpBusAtParam != latestPatternInterpSumBusAtParam, {
								if(patternInterpBusAtParam.bus != nil, {
									//Simply "stop" the envelopes of all active patternSynths.
									//This allows them to be kept alive even though
									//the interpSynth might have been freed meanwhile. This will "freeze" the
									//interpolation of running patternSynths.
									var currentActivePatternSynthsAtInterpBus =
									algaPattern.currentActivePatternParamSynths[patternInterpBusAtParam];
									if(currentActivePatternSynthsAtInterpBus != nil, {
										currentActivePatternSynthsAtInterpBus.do({ | patternParamSynth |
											patternParamSynth.set(
												\sampleAndHold, 1,
												\t_sah, 1
											);
										});
									});
								});
							});
						});
					});
				});
			}
		);
	}

	//Each param has its own interpSynth and bus. These differ from AlgaNode's ones,
	//as they are not embedded with the interpolation behaviour itself, but they are external.
	//This allows to separate the per-tick pattern triggering from the interpolation process.
	createPatternInterpSynthAndBusAtParam { | paramName, paramRate, paramNumChannels,
		entry, entryOriginal, uniqueID, time = 0, shape |

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
			//If it's the first synth, fadeTime is 0 and envShape is default
			//This only happens when first creating the AlgaPattern!
			interpSynth = AlgaSynth(
				interpSymbol,
				[
					\out, interpBus.index,
					\fadeTime, 0,
					\envShape, Env([0, 1], 1).algaConvertEnv
				],
				interpGroup
			);
			interpSynths[paramName] = IdentityDictionary().put(uniqueID, interpSynth);
			interpBusses[paramName] = IdentityDictionary().put(uniqueID, interpBus);
		}, {
			//If it's not the first synth, fadeTime is time and envShape is shape
			interpSynth = AlgaSynth(
				interpSymbol,
				[
					\out, interpBus.index,
					\fadeTime, time,
					\envShape, shape.algaConvertEnv
				],
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
		algaPattern.addActiveInterpSynthOnFree(paramName, entryOriginal, \default, interpSynth, {
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
		algaPattern.addInOutNodesDict(sender, param, mix);
	}

	//Wrapper around AlgaNode's removeInOutNodesDict.
	//If entry is a ListPattern, loop around it and remove each entry that is an AlgaNode.
	removeAllInOutNodesDictAtParam { | sender, paramName |
		//This is then picked up in addInNode in AlgaPattern. This must come before removeInOutNodesDict
		algaPattern.connectionAlreadyInPlace = algaPattern.checkConnectionAlreadyInPlace(sender);
		algaPattern.removeInOutNodesDict(nil, paramName) //nil removes them all
	}

	//add a scaleArray and chans
	addScaleArrayAndChans { | paramName, paramNumChannels, uniqueID, chans, scale |
		var scaleArraysAndChansAtParam = scaleArraysAndChans[paramName];

		//Pattern support
		chans = chans.algaAsStream;
		scale = scale.algaAsStream;

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
	add { | entry, controlName, chans, scale, sampleAndHold, time = 0, shape |
		var paramName, paramRate, paramNumChannels, paramDefault;
		var uniqueID;

		var entryOriginal = entry; //Original entry, not as Stream. Needed for addInOutNodesDictAtParam
		var isFirstEntry;

		if(controlName == nil, {
			("AlgaPatternInterpStreams: Invalid controlName for param '" ++ paramName ++ "'").error
		});

		//Unpack
		paramName = controlName.name;
		paramRate = controlName.rate;
		paramNumChannels = controlName.numChannels;
		paramDefault = controlName.defaultValue;

		//If entry is nil, use paramDefault (used for .reset)
		if(entry == nil, { entry = paramDefault; entryOriginal = paramDefault });

		//Interpret entry as Stream
		entry = entry.algaAsStream;

		//Use an unique id as index as it's more reliable than using the entry as key:
		//entry could very well be a number (like 440), screwing things up in IdentityDict.
		uniqueID = UniqueID.next;

		//Add entry to dict
		isFirstEntry = this.addEntry(entry, paramName, uniqueID);

		//Add the scaleArray and chans
		this.addScaleArrayAndChans(paramName, paramNumChannels, uniqueID, chans, scale);

		//Add sampleAndHold
		this.addSampleAndHold(paramName, sampleAndHold);

		//Remove all older inNodes / outNodes... Doesn't work with mix yet
		this.removeAllInOutNodesDictAtParam(entryOriginal, paramName);

		//Add proper inNodes / outNodes / connectionTimeOutNodes. Use entryOriginal in order
		//to retrieve if it is a ListPattern.
		this.addInOutNodesDictAtParam(entryOriginal, paramName, false);

		//Get shape
		shape = algaPattern.checkValidEnv(shape) ? algaPattern.getInterpShape(paramName);

		//Trigger the interpolation process on all the other active interpSynths.
		//This must always be before createPatternInterpSynthAndBusAtParam
		this.freeActiveInterpSynthsAtParam(
			paramName: paramName,
			time: time,
			shape: shape
		);

		//Create the interpSynth and interpBus for the new sender
		this.createPatternInterpSynthAndBusAtParam(
			paramName: paramName,
			paramRate: paramRate,
			paramNumChannels: paramNumChannels,
			entry: entry,
			entryOriginal: entryOriginal,
			uniqueID: uniqueID,
			time: time,
			shape: shape
		);

		//Create a temporary pattern param synth to start the interpolation process with.
		//Only one per-change is needed, as the interpolation process will continue as soon as the pattern
		//triggers new synth. This just avoids to have silences and gaps when modifying a param mid-pattern.
		//This must come AFTER createPatternInterpSynthAndBusAtParam.
		if((isFirstEntry.not).and(sampleAndHold.not), {
			//NOTE: this doesn't support MC yet
			this.createTemporaryPatternParamSynthAtParam(
				entry: entry,
				uniqueID: uniqueID,
				paramName: paramName,
				paramNumChannels: paramNumChannels,
				paramRate: paramRate,
				paramDefault: paramDefault,
				time: time
			);
		});
	}

	//Play a pattern as an AlgaReschedulingEventStreamPlayer and return it
	playAlgaReschedulingEventStreamPlayer { | pattern, clock |
		algaReschedulingEventStreamPlayer = pattern.playAlgaRescheduling(
			clock: clock
		)
	}
}

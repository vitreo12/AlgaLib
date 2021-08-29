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
AlgaPatternArg {
	var <sender, <chans, <scale;

	*new { | node, chans, scale |
		^super.new.init(node, chans, scale)
	}

	init { | argSender, argChans, argScale |
		sender = argSender.asStream; //Pattern support
		chans  = argChans.asStream;  //Pattern support
		scale  = argScale.asStream;  //Pattern support
	}

	isAlgaPatternArg { ^true }
}

//This class is used for the \out parameter
AlgaOut {
	var <node, <param, <chans, <scale;

	*new { | node, param, chans, scale |
		^super.new.init(node, param, chans, scale)
	}

	init { | argNode, argParam, argChans, argScale |
		node   = argNode.asStream;  //Pattern support
		param  = argParam.asStream; //Pattern support
		chans  = argChans.asStream; //Pattern support
		scale  = argScale.asStream; //Pattern support
	}

	isAlgaOut { ^true }
}

//Alias for AlgaPatternArg
AlgaArg : AlgaPatternArg {}

//This class is used to create a temporary AlgaNode for a parameter in an AlgaPattern
AlgaTemp {
	var <def, <chans, <scale;
	var <controlNames;
	var <numChannels, <rate;
	var <valid = false;

	*new { | def, chans, scale |
		^super.new.init(def, chans, scale)
	}

	init { | argDef, argChans, argScale |
		def    = argDef;
		chans  = argChans.asStream;  //Pattern support
		scale  = argScale.asStream;  //Pattern support
	}

	checkValidSynthDef { | def |
		var synthDesc = SynthDescLib.global.at(def);
		var synthDef;

		if(synthDesc == nil, {
			("AlgaTemp: Invalid AlgaSynthDef: '" ++ def.asString ++ "'").error;
			^nil;
		});

		synthDef = synthDesc.def;

		if(synthDef.class != AlgaSynthDef, {
			("AlgaTemp: Invalid AlgaSynthDef: '" ++ def.asString ++"'").error;
			^nil;
		});

		numChannels = synthDef.numChannels;
		rate = synthDef.rate;
		controlNames = synthDesc.controls;

		//Set validity
		if((numChannels != nil).and(rate != nil).and(controlNames != nil), { valid = true });
	}

	isAlgaTemp { ^true }
}

//AlgaPattern
AlgaPattern : AlgaNode {
	/*
	TODOs:

	1) out: (See next)

	2) allow Functions in AlgaTemp and ListPatterns for 'def'

	3) detune / scale / ... / -> freq (https://doc.sccode.org/Classes/Event.html)

	4) mixFrom()

	- out: (
	node: Pseq([a, b], inf),
	param: \freq,
	scale: Pseq([[20, 400], [30, 500]], inf),
	chans: Pseq([1, 2], inf).
	time: 1
	)

	- out: Pseq([a, b], inf)
	*/

	//The actual Patterns to be manipulated
	var <pattern;

	//The pattern as stream
	var <patternAsStream;

	//The Event input
	var <eventPairs;

	//interpStreams. These varies on .replace
	var <interpStreams;

	//numChannels when using a ListPattern as \def
	var <numChannelsList;

	//rate when using a ListPattern as \def
	var <rateList;

	//controlNames when using a ListPattern as \def
	var <controlNamesList;

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

	//Current \out
	var <currentOut;

	//Current nodes of used in currentOut
	var <currentOutNodes;

	//Current time used for \out replacement
	var <currentOutTime;

	//Needed needed to store reset for various alga params (\out, \fx, etc...)
	var <currentReset;

	//Add the \algaNote event to Event
	*initClass {
		//StartUp.add is needed: Event class must be compiled first
		StartUp.add({ this.addAlgaNoteEventType });
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

			//The interpStreams the Pattern is using
			var algaPatternInterpStreams = ~algaPatternInterpStreams;

			//fx
			var fx = ~fx;

			//out
			var algaOut = ~algaOut;

			//Other things for pattern syncing / clocking / scheduling
			var offset = ~timingOffset;
			var lag = ~lag;

			//Needed ?
			~isPlaying = true;

			//Create the bundle with all needed Synths for this Event.
			bundle = algaPatternServer.makeBundle(false, {
				~algaPattern.createEventSynths(
					algaSynthDef: algaSynthDef,
					algaSynthBus: algaSynthBus,
					algaPatternInterpStreams: algaPatternInterpStreams,
					fx: fx,
					algaOut: algaOut
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
			"AlgaPattern: 'replaceDur' only supports boolean values. Setting it to false".error
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

	//Create a temporary synth according to the specs of the AlgaTemp
	createAlgaTempSynth { | algaTemp, patternBussesAndSynths |
		var tempBus, tempSynth;
		var tempSynthArgs = Array.newClear;
		var tempNumChannels = algaTemp.numChannels;
		var tempRate = algaTemp.rate;
		var def, params, controlNames;

		//Check AlgaTemp validity
		if(algaTemp.valid.not, {
			"AlgaPattern: Invalid AlgaTemp, using default parameter".error;
			^nil
		});

		//Unpack relevant params
		params = algaTemp.def;
		def = params[\def];
		controlNames = algaTemp.controlNames;

		//Loop around the controlNames to set relevant parameters
		controlNames.do({ | controlName |
			var paramName = controlName.name;
			var paramNumChannels = controlName.numChannels;
			var paramRate = controlName.rate;
			var paramDefault = controlName.defaultValue;
			var entry = params[paramName];

			//If entry is nil, the tempSynth will already use the default value
			if(entry != nil, {
				//Ignore static params
				if((paramName != '?').and(paramName != \instrument).and(
					paramName != \def).and(paramName != \out).and(
					paramName != \gate).and(paramName != \fadeTime), {

					//Temporary bus that the patternParamSynth for the fx will write to
					var patternParamBus = AlgaBus(server, paramNumChannels, paramRate);

					//Add bus to tempSynth at correct paramName
					tempSynthArgs = tempSynthArgs.add(paramName).add(patternParamBus.busArg);

					//Register bus to be freed
					patternBussesAndSynths.add(patternParamBus);

					//Create a patternParamSynth for the temp param
					this.createPatternParamSynth(
						entry: entry,
						uniqueID: nil,
						paramName: paramName,
						paramNumChannels: paramNumChannels,
						paramRate: paramRate,
						paramDefault: paramDefault,
						patternInterpSumBus: patternParamBus,
						patternBussesAndSynths: patternBussesAndSynths,
						isFX: true //use isFX (it's the same behaviour)
					)
				});
			});
		});

		//The AlgaBus the tempSynth will write to
		tempBus = AlgaBus(server, tempNumChannels, tempRate);

		//Write output to tempBus
		tempSynthArgs = tempSynthArgs.add(\out).add(tempBus.index);

		//The actual AlgaSynth
		tempSynth = AlgaSynth(
			def,
			tempSynthArgs,
			interpGroup,
			\addToTail,
			false
		);

		//Add Bus and Synth to patternBussesAndSynths
		patternBussesAndSynths.add(tempBus);
		patternBussesAndSynths.add(tempSynth);

		//Return the AlgaBus that the tempSynth writes to
		^tempBus.busArg;
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

		//Check if it's an AlgaTemp. Create it
		if(entry.isAlgaTemp, {
			var algaTemp = entry;

			//If valid, this returns the AlgaBus that the tempSynth will write to
			entry = this.createAlgaTempSynth(
				algaTemp: algaTemp,
				patternBussesAndSynths: patternBussesAndSynths
			);

			if(entry.isNil.not, {
				//Valid AlgaTemp. entry is an AlgaBus now
				chansMapping      = algaTemp.chans;
				scale             = algaTemp.scale;
				senderRate        = algaTemp.rate;
				senderNumChannels = algaTemp.numChannels;
				validParam        = true;
			}, {
				//entry == nil
				senderRate = "control";
				senderNumChannels = paramNumChannels;
				entry = paramDefault;
				validParam = true;
			});
		});

		//Fallback sender (modified for AlgaNode, needed for chansMapping)
		sender = entry;

		//Valid values are Numbers / Arrays / AlgaNodes / Buffers / Nils
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
				var indices;

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

				//Always calculate chansMapping for the modulo around paramNumChannels!
				if(chansMapping.isStream, { chansMapping = chansMapping.next }); //Pattern support
				indices = this.calculateSenderChansMappingArray(
					paramName,
					sender,
					chansMapping,
					senderNumChannels,
					paramNumChannels,
					false //don't update the AlgaNode's chans dict
				);

				patternParamSynthArgs = patternParamSynthArgs.add(\indices).add(indices);

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
	createFXSynthAndPatternSynth { | fx, numChannelsToUse, rateToUse,
		algaSynthDef, algaSynthBus, algaPatternInterpStreams, patternSynthArgs, patternBussesAndSynths |

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

		//Args to fxSynth
		var fxSynthArgs = Array.newClear;

		//Actual fxSynth
		var fxSynthSymbol;
		var fxSynth;

		//If channels mismatch, these will be used to create final conversion
		//from fxSynth to algaSynthBus.
		var fxInterpSynthSymbol, fxInterpSynthArgs, fxInterpSynth;

		//Create the Bus patternSynth will write to.
		//It uses numChannelsToUse and rateToUse, which are set accordingly to the \def specs
		var patternBus = AlgaBus(server, numChannelsToUse, rateToUse);

		//Add patternBus to patternBussesAndSynthsFx
		patternBussesAndSynthsFx.add(patternBus);

		//Unpack parameters (same behaviour as createPatternParamSynth's unpacking)
		controlNamesAtFX.do({ | controlName |
			var paramName = controlName.name;
			var paramNumChannels = controlName.numChannels;
			var paramRate = controlName.rate;
			var paramDefault = controlName.defaultValue;
			var entry = fx[paramName];

			//If entry is nil, the tempSynth will already use the default value
			if(entry != nil, {
				//Ignore static params
				if((paramName != '?').and(paramName != \instrument).and(
					paramName != \def).and(paramName != \out).and(
					paramName != \gate).and(paramName != \in).and(
					paramName != \fadeTime), {

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
		});

		//If numChannels or rate mismatch between patternSynth -> fxSynth
		if((numChannelsToUse != fxInNumChannels).or(rateToUse != fxInRate), {
			//patternSynth -> \in
			var patternInterpInSynthSymbol = (
				"alga_pattern_" ++
				rateToUse ++
				numChannelsToUse ++
				"_" ++
				fxInRate ++
				fxInNumChannels ++
				"_fx"
			).asSymbol;

			//new Bus
			var patternInterpInBus = AlgaBus(server, fxInNumChannels, fxInRate);

			//Args to patternInterpInSynth
			var patternInterpInSynthArgs = [
				\in, patternBus.busArg,
				\out, patternInterpInBus.index
			];

			//Create patternInterpInSynth
			var patternInterpInSynth = AlgaSynth(
				patternInterpInSynthSymbol,
				patternInterpInSynthArgs,
				synthGroup,
				\addToTail,
				false
			);

			//Add Bus and Synth to patternBussesAndSynthsFx
			patternBussesAndSynthsFx.add(patternInterpInBus);
			patternBussesAndSynthsFx.add(patternInterpInSynth);

			//Add correct \in for fxSynth
			fxSynthArgs = fxSynthArgs.add(\in).add(patternInterpInBus.busArg)
		}, {
			//Same channels / rate: read from patternBus
			fxSynthArgs = fxSynthArgs.add(\in).add(patternBus.busArg)
		});

		//If numChannels or rate mismatch between fxSynth -> algaSynthBus
		if((fxNumChannels != numChannels).or(fxRate != rate), {
			//Create the Bus fxSynth will write to
			var fxBus = AlgaBus(server, fxNumChannels, fxRate);

			//fxSynth -> algaSynthBus (that's why it uses rate / numChannels)
			fxInterpSynthSymbol = (
				"alga_pattern_" ++
				fxRate ++
				fxNumChannels ++
				"_" ++
				rate ++
				numChannels ++
				"_fx"
			).asSymbol;

			//Set correct args
			fxInterpSynthArgs = [
				\in, fxBus.busArg,
				\out, algaSynthBus.index
			];

			//Add Bus to patternBussesAndSynthsFx
			patternBussesAndSynthsFx.add(fxBus);

			//Add correct \out for fxSynthArgs to fxBus
			fxSynthArgs = fxSynthArgs.add(\out).add(fxBus.index)
		}, {
			//Same channels / rate: write to algaSynthBus
			fxSynthArgs = fxSynthArgs.add(\out).add(algaSynthBus.index)
		});

		//The user's def
		fxSynthSymbol = def;

		//Create fxSynth, addToTail
		fxSynth = AlgaSynth(
			fxSynthSymbol,
			fxSynthArgs,
			synthGroup,
			\addToTail,
			false
		);

		//If not explicitFree, add fxSynth to the free mechanism.
		//If explicitFree, it will handle it by itself
		if(explicitFree.not, { patternBussesAndSynths.add(fxSynth) });

		//FUNDAMENTAL: add fxSynth to the algaPatternSynths so that
		//algaSynthBus is kept alive for the WHOLE duration of the fx too.
		algaPatternInterpStreams.algaPatternSynths.add(fxSynth);

		//If there was conversion, create the fxInterpSynth (needs to come after fxSynth !!!)
		if(fxInterpSynthSymbol != nil, {
			fxInterpSynth = AlgaSynth(
				fxInterpSynthSymbol,
				fxInterpSynthArgs,
				synthGroup,
				\addToTail,
				false
			);

			//Add to patternBussesAndSynthsFx
			patternBussesAndSynthsFx.add(fxInterpSynth);
		});

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

	//Create a temporary synth for \out connection
	createOutConnection { | algaOut, algaSynthBus, outTempBus, patternBussesAndSynths |
		case
		{ algaOut.isAlgaNode } {
			//Only if instantiated (or it will click)
			if(algaOut.algaInstantiatedAsReceiver, {
				algaOut.receivePatternOutTempSynth(
					algaPattern: this,
					algaSynthBus: algaSynthBus,
					outTempBus: outTempBus,
					algaNumChannels: numChannels,
					algaRate: rate,
					patternBussesAndSynths: patternBussesAndSynths
				)
			});
		}
		{ algaOut.isAlgaOut } {
			var node  = algaOut.node;
			var param = algaOut.param;
			var scale = algaOut.scale;
			var chans = algaOut.chans;

			if(node.isAlgaNode, {
				//Only if instantiated (or it will click)
				if(node.algaInstantiatedAsReceiver, {
					node.receivePatternOutTempSynth(
						algaPattern: this,
						algaSynthBus: algaSynthBus,
						outTempBus: outTempBus,
						algaNumChannels: numChannels,
						algaRate: rate,
						param: param,
						patternBussesAndSynths: patternBussesAndSynths,
						chans: chans,
						scale: scale
					)
				});
			});
		};
	}

	//Create all needed Synths for this Event. This is triggered by the \algaNote Event
	createEventSynths { | algaSynthDef, algaSynthBus,
		algaPatternInterpStreams, fx, algaOut |
		//Used to check whether using a ListPattern of \defs
		var numChannelsToUse  = numChannels;
		var rateToUse = rate;
		var controlNamesToUse = controlNames;

		//Flag to check for mismatches (and needed conversions) for numChannels / rates
		var numChannelsOrRateMismatch = false;

		//These will be populated and freed when the patternSynth is released
		var patternBussesAndSynths = IdentitySet(controlNames.size * 2);

		//args to patternSynth
		var patternSynthArgs = [ \gate, 1 ];

		//The actual synth that will be created
		var patternSynth;

		//
		var validFX = false;
		var validOut = false;

		//FIRST THING, free all dangling temporary synths. These are created to perform
		//mid-pattern interpolation of parameters.
		this.freeAllTemporaryParamSynths;

		//Check if it's a ListPattern and retrieve correct controlNames
		if(controlNamesList != nil, {
			controlNamesToUse = controlNamesList[algaSynthDef];
			if(controlNamesToUse == nil, { controlNamesToUse = controlNames });
		});

		//Loop over controlNames and create as many Busses and Synths as needed,
		//also considering interpolation / normalization
		controlNamesToUse.do({ | controlName |
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

		//If ListPattern, retrieve correct numChannels
		if(numChannelsList != nil, {
			numChannelsToUse = numChannelsList[algaSynthDef];
			if(numChannelsToUse == nil, { numChannelsToUse = numChannels });
		});

		//If ListPattern, retrieve correct rate
		if(rateList != nil, {
			rateToUse = rateList[algaSynthDef];
			if(rateToUse == nil, { rateToUse = rate });
		});

		//If there is a mismatch between numChannels / numChannelsToUse OR rate / rateToUse,
		//use numChannelsToUse and rateToUse to determine patternSynth's output.
		//Then, convert it to be used with algaSynthBus.
		if((numChannels != numChannelsToUse).or(rate != rateToUse), {
			numChannelsOrRateMismatch = true
		});

		//Check validFX
		if((fx != nil).and(fx.class == Event), { validFX = true });

		//Check validOut
		if((algaOut != nil).and((algaOut.isAlgaOut).or(algaOut.isAlgaNode)), { validOut = true });

		//If fx
		if(validFX, {
			//This returns the patternSynthArgs with correct bus to write to (the fx one)
			patternSynthArgs = this.createFXSynthAndPatternSynth(
				fx: fx,
				numChannelsToUse: numChannelsToUse,
				rateToUse: rateToUse,
				algaSynthDef: algaSynthDef,
				algaSynthBus: algaSynthBus,
				algaPatternInterpStreams: algaPatternInterpStreams,
				patternSynthArgs: patternSynthArgs,
				patternBussesAndSynths: patternBussesAndSynths
			);
		}, {
			//Channels mismatch: pop a converter
			if(numChannelsOrRateMismatch, {
				var converterBus = AlgaBus(server, numChannelsToUse, rateToUse);
				var patternSynthConverterSymbol = (
					"alga_pattern_" ++
					rateToUse ++
					numChannelsToUse ++
					"_" ++
					rate ++
					numChannels ++
					"_fx"
				).asSymbol;
				var patternSynthConverter = AlgaSynth(
					patternSynthConverterSymbol,
					[ \in, converterBus.busArg, \out, algaSynthBus.index ],
					synthGroup,
					\addToTail,
					false
				);

				//Add the converter Bus / Synth to patternBussesAndSynths
				patternBussesAndSynths.add(converterBus);
				patternBussesAndSynths.add(patternSynthConverter);

				//patternSynth will write to converterBus
				patternSynthArgs = patternSynthArgs.add(\out).add(converterBus.index);
			}, {
				//Standard case: write directly to algaSynthBus
				patternSynthArgs = patternSynthArgs.add(\out).add(algaSynthBus.index);
			});
		});

		//Valid out
		if(validOut, {
			//New temp bus
			var outTempBus = AlgaBus(server, numChannels, rate);

			//Add temp bus to patternBussesAndSynths
			patternBussesAndSynths.add(outTempBus);

			//Create connection synth with the target
			this.createOutConnection(algaOut, algaSynthBus, outTempBus, patternBussesAndSynths);

			//Update the synthDef symbol to use the patternTempOut version
			algaSynthDef = (algaSynthDef.asString ++ "_patternTempOut").asSymbol;

			//Add patternTempOut writing to patternSynthArgs
			patternSynthArgs = patternSynthArgs.add(\patternTempOut).add(outTempBus.index);
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
	dispatchNode { | def, args, initGroups = false, replace = false, reset = false,
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

		//Parse reset
		this.parseResetOnReplace(reset);

		//Also store reset as it's needed to reset algaParams (\out, \fx, etc...)
		currentReset = reset;

		//Create args dict
		this.createDefArgs(args);

		//Reset all ListPattern vars (in case of dispatchListPattern, they will be created anew)
		numChannelsList = nil;
		rateList = nil;
		controlNamesList = nil;

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

		listPattern.list.do({ | entry, i |
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

				if(numChannelsCount == nil, {
					numChannelsCount = synthDefEntry.numChannels;
					numChannels = numChannelsCount
				});

				if(rateCount == nil, {
					rateCount = synthDefEntry.rate;
					rate = rateCount
				});

				//Use the highest count of numChannels as "main" one, using that one entry's rate
				if(synthDefEntry.numChannels > numChannelsCount, {
					numChannels = synthDefEntry.numChannels;
					rate = synthDefEntry.rate
				});

				//Store for next iteration
				numChannelsCount = synthDefEntry.numChannels;
				rateCount = synthDefEntry.rate;

				//Add numChannels and rate to numChannelsList and rateList
				numChannelsList[entry.asSymbol] = numChannelsCount;
				rateList[entry.asSymbol] = rateCount;

				//Retrieve controlNames
				controlNamesEntry = synthDescEntry.controls;

				//Create entry for controlNamesList
				controlNamesList[entry.asSymbol] = IdentityDictionary();

				//Check for duplicates and add correct controlName to controlNamesList[entry.asSymbol]
				controlNamesEntry.do({ | controlName |
					var name = controlName.name;
					if((name != \fadeTime).and(
						name != \out).and(
						name != \gate).and(
						name != '?'), {

						//Just check for duplicate names: we only need one entry per param name
						//for controlNamesSum.
						if(controlNamesDict[name] == nil, {
							controlNamesDict[name] = controlName;
							controlNamesSum = controlNamesSum.add(controlName);
						});

						//Add to IdentityDict for specific def / name combination
						controlNamesList[entry.asSymbol][name] = controlName;
					});
				});
			});
		});

		^controlNamesSum;
	}

	//Multiple Symbols over a ListPattern
	dispatchListPattern { | def, initGroups = false, replace = false,
		keepChannelsMapping = false, keepScale = false, sched = 0 |
		var controlNamesSum;

		//Create numChannelsList, rateList and controlNamesList (they're nil otherwise)
		numChannelsList  = IdentityDictionary();
		rateList         = IdentityDictionary();
		controlNamesList = IdentityDictionary();

		//Check rates, numChannels, Symbols and controlNames
		controlNamesSum = this.checkListPatternValidityAndReturnControlNames(def);
		if(controlNamesSum == nil, { ^this });

		//Create controlNames from controlNamesSum
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

		//Loop over the event and parse listPatterns / algaTemps. Also use .asStream for the final entry.
		value.keysValuesDo({ | key, entry |
			var parsedEntry = entry;
			if(parsedEntry.isListPattern, { parsedEntry = this.parseListPatternParam(parsedEntry) });
			if(parsedEntry.isAlgaTemp, { parsedEntry = this.parseAlgaTempParam(parsedEntry) });
			value[key] = parsedEntry.asStream
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
				var result = this.parseFX(listEntry); //recursive, so it picks up other ListPatterns if needed
				if(result == nil, { ^nil });
				value.list[i] = result;
			});
			^value;
		}

		//This can be used to pass Symbols like \none or \dry to just passthrough the sound
		{ value.class == Symbol } {
			^value
		};
	}

	//Parse a single \out event
	parseOutAlgaOut { | value, alreadyParsed |
		var node = value.node;
		var param = value.param;

		if(param == nil, param = \in);
		if(param.class != Symbol, {
			"AlgaPattern: the 'param' argument in AlgaOut can only be a Symbol. Using 'in'".error;
			param = \in
		});

		case
		{ node.isAlgaNode } {
			if(alreadyParsed[node] == nil, {
				if(node.isAlgaPattern, {
					"AlgaPattern: the 'out' parameter only supports AlgaNodes, not AlgaPatterns".error;
					^nil
				});
				alreadyParsed[node] = true;
				currentOutNodes.add([node, param]);
			});
		}
		{ node.isListPattern } {
			node.list.do({ | listEntry, i |
				node.list[i] = this.parseOut(listEntry, alreadyParsed)
			});
		};

		^value.asStream;
	}

	//Parse the \out key
	parseOut { | value, alreadyParsed |
		alreadyParsed = alreadyParsed ? IdentityDictionary();

		case
		{ value.isAlgaNode } {
			if(alreadyParsed[value] == nil, {
				if(value.isAlgaPattern, {
					"AlgaPattern: the 'out' parameter only supports AlgaNodes, not AlgaPatterns".error;
					^nil
				});
				alreadyParsed[value] = true;
				currentOutNodes.add([value, \in]);
			});
			^value
		}
		{ value.isAlgaOut } {
			^this.parseOutAlgaOut(value, alreadyParsed);
		}
		{ value.isListPattern } {
			value.list.do({ | listEntry, i |
				var result = this.parseOut(listEntry, alreadyParsed); //recursive, so it picks up other ListPatterns if needed
				if(result == nil, { ^nil });
				value.list[i] = result;
			});
			^value.asStream; //return the stream
		}
		//This can be used to pass Symbols like \none or \dry to just passthrough the sound
		{ value.class == Symbol } {
			^value
		};
	}

	//Parse an AlgaTemp
	parseAlgaTempParam { | algaTemp |
		var def = algaTemp.def;
		var defDef;

		if(def == nil, {
			"AlgaPattern: AlgaTemp has a nil 'def' argument".error;
			^nil;
		});

		if(def.class == Symbol, {
			defDef = def
		}, {
			if(def.class != Event, {
				"AlgaPattern: AlgaTemp's 'def' argument must either be a Symbol or an Event".error;
				^nil
			});
			defDef = def[\def];
			if(defDef == nil, {
				"AlgaPattern: AlgaTemp's 'def' Event does not provide a 'def' entry".error;
				^nil
			})
		});

		//Check if the synthDef is valid
		algaTemp.checkValidSynthDef(defDef);

		//Loop around the event entries and use .asStream
		if(def.class == Event, {
			def.keysValuesDo({ | key, entry |
				if(key != \def, {
					var parsedEntry = entry;
					if(entry.isListPattern, { parsedEntry = this.parseListPatternParam(parsedEntry) });
					if(entry.isAlgaTemp, { parsedEntry = this.parseAlgaTempParam(parsedEntry) });
					def[key] = parsedEntry.asStream;
				});
			});
		});

		^algaTemp;
	}

	//Parse a ListPattern
	parseListPatternParam { | listPattern |
		listPattern.list.do({ | listEntry, i |
			if(listEntry.isListPattern, {
				listPattern.list[i] = this.parseListPatternParam(listEntry)
			});
			if(listEntry.isAlgaTemp, {
				listPattern.list[i] = this.parseAlgaTempParam(listEntry)
			});
		});
		^listPattern;
	}

	//Create out: receivers
	createPatternOutReceivers { | prevOutNodes |
		var time = currentOutTime ? 0;

		//Fade out (also old synths)
		if(prevOutNodes != nil, {
			prevOutNodes.do({ | outNodeAndParam |
				var outNode = outNodeAndParam[0];
				var param = outNodeAndParam[1];
				scheduler.addAction(
					condition: { outNode.algaInstantiatedAsReceiver },
					func: {
						outNode.removePatternOutsAtParam(
							algaPattern: this,
							param: param,
							time: time
						)
					}
				);
			});
		});

		//Fade in (also new synths)
		if(currentOutNodes != nil, {
			currentOutNodes.do({ | outNodeAndParam |
				var outNode = outNodeAndParam[0];
				var param = outNodeAndParam[1];
				scheduler.addAction(
					condition: { outNode.algaInstantiatedAsReceiver },
					func: {
						outNode.receivePatternOut(
							algaPattern: this,
							param: param,
							time: time
						)
					}
				)
			});
		});

		//Reset currentOutTime
		currentOutTime = 0;
	}

	//Reset specific algaParams (\out, \fx, etc...)
	parseResetOnReplaceAlgaParams {
		case
		{ currentReset.isArray } {
			^currentReset.as(IdentitySet);
		}
		{ currentReset == true } {
			^[\fx, \out, \dur].as(IdentitySet);
		};
		^nil
	}

	//Build the actual pattern
	createPattern { | replace = false, keepChannelsMapping = false, keepScale = false, sched |
		var foundDurOrDelta = false;
		var manualDur = false;
		var foundFX = false;
		var parsedFX;
		var parsedOut;
		var prevOutNodes = currentOutNodes.copy;
		var foundOut = false;
		var patternPairs = Array.newClear;

		//Create new interpStreams. NOTE that the Pfunc in dur uses this, as interpStreams
		//will be overwritten when using replace. This allows to separate the "global" one
		//from the one that's being created here.
		var newInterpStreams = AlgaPatternInterpStreams(this);

		//Loop over controlNames and retrieve which parameters the user has set explicitly.
		//All other parameters will be dealt with later.
		controlNames.do({ | controlName |
			var paramName = controlName.name;
			var paramValue = eventPairs[paramName]; //Retrieve it directly from eventPairs
			var chansMapping, scale;

			//Parse a ListPattern
			if(paramValue.isListPattern, {
				paramValue = this.parseListPatternParam(paramValue);
			});

			//Parse an AlgaTemp
			if(paramValue.isAlgaTemp, {
				paramValue = this.parseAlgaTempParam(paramValue);
			});

			//if not set explicitly yet
			if(paramValue == nil, {
				//When replace, getDefaultOrArg will return LATEST set parameter, via replaceArgs
				paramValue = this.getDefaultOrArg(controlName, paramName, replace);
			});

			//If replace, check if keeping chans / scale mappings
			if(replace, {
				if(keepChannelsMapping, { chansMapping = this.getParamChansMapping(paramName, paramValue) });
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

			//Remove param entry from eventPairs
			eventPairs[paramName] = nil;
		});

		//Loop over all other input from the user, setting all entries that are not part of controlNames
		eventPairs.keysValuesDo({ | paramName, value |
			var isAlgaParam = false;

			//Add \def key as \instrument
			if(paramName == \def, {
				patternPairs = patternPairs.add(\instrument).add(value);
				isAlgaParam = true;
			});

			//Found \dur or \delta
			if((paramName == \dur).or(paramName == \delta), {
				if(value.class == Symbol, {
					//Using a symbol (like, \manual) as \dur key
					manualDur = true
				}, {
					foundDurOrDelta = true;
					this.setDur(value, newInterpStreams);
				});
				isAlgaParam = true;
			});

			//Add \fx key (parsing everything correctly)
			if(paramName == \fx, {
				parsedFX = this.parseFX(value);
				if(parsedFX != nil, {
					patternPairs = patternPairs.add(\fx).add(parsedFX);
					foundFX = true;
				});
				isAlgaParam = true;
			});

			//Add \out key
			if(paramName == \out, {
				//Reset currentOutNodes
				currentOutNodes = IdentitySet();
				parsedOut = this.parseOut(value);
				if(parsedOut != nil, {
					patternPairs = patternPairs.add(\algaOut).add(parsedOut); //can't use \out
					foundOut = true;
				});
				isAlgaParam = true;
			});

			//All other entries that user wants to set and retrieve from within the pattern.
			//This includes things like \lag and \timingOffset
			if(isAlgaParam.not, {
				patternPairs = patternPairs.add(paramName).add(value);
			});
		});

		//Store current FX for replaces
		if(foundFX, { currentFX = parsedFX });

		//Store current out for replaces
		if(foundOut, { currentOut = parsedOut });

		//If no dur and replace, get it from previous interpStreams
		if(replace, {
			//Check reset
			var resetSet = this.parseResetOnReplaceAlgaParams;
			if(resetSet != nil, {
				//reset \fx
				if((foundFX.not).and(resetSet.findMatch(\fx) != nil), {
					parsedFX = nil; currentFX = nil
				});

				//reset \out
				if((foundOut.not).and(resetSet.findMatch(\out) != nil), {
					parsedOut = nil; currentOut = nil; currentOutNodes = nil
				});

				//reset \dur
				if((foundDurOrDelta.not).and(resetSet.findMatch(\dur) != nil), {
					interpStreams = nil;
				});
			});

			//Set dur according to previous one
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

			//No \out from user, use currentOut if available
			if(foundOut.not, {
				if(currentOut != nil, {
					patternPairs = patternPairs.add(\algaOut).add(currentOut);
				});
			});
		}, {
			//Else, default it to 1
			if(foundDurOrDelta.not, { this.setDur(1, newInterpStreams) });
		});

		//Set the correct synthBus in newInterpStreams!!!
		//This is fundamental for the freeing mechanism in stopPattern to work correctly
		//with the freeAllSynthsAndBussesOnReplace function call
		newInterpStreams.algaSynthBus = this.synthBus;

		//Add \type, \algaNode, and all things related to
		//the context of this AlgaPattern
		patternPairs = patternPairs.addAll([
			\type, \algaNote,
			\algaPattern, this,
			\algaSynthBus, this.synthBus, //Lock current one: will work on .replace
			\algaPatternServer, server,
			\algaPatternClock, this.clock,
			\algaPatternInterpStreams, newInterpStreams //Lock current one: will work on .replace
		]);

		//Manual or automatic dur management
		if(manualDur.not, {
			//Pfuncn allows to modify the value
			patternPairs = patternPairs.add(\dur).add(
				Pfuncn( { newInterpStreams.dur.next }, inf)
			);
		});

		//Create the Pattern by calling .next from the streams
		pattern = Pbind(*patternPairs);
		patternAsStream = pattern.asStream; //Needed for things like dur: \none

		//Determine if \out interpolation is required
		this.createPatternOutReceivers(prevOutNodes);

		//Schedule the start of the pattern on the AlgaScheduler. All the rest in this
		//createPattern function is non scheduled as it it better to create it right away.
		if(manualDur.not, {
			scheduler.addAction(
				func: {
					newInterpStreams.playAlgaReschedulingEventStreamPlayer(pattern, this.clock);
				},
				sched: sched
			);
		});

		//Update latest interpStreams
		interpStreams = newInterpStreams;
	}

	//Manually advance the pattern. 'next' as name won't work as it's reserved, apparently
	advance { | sched |
		sched = sched ? 0;
		if(patternAsStream != nil, {
			scheduler.addAction(
				func: {
					patternAsStream.next(()).play; //Empty event as protoEvent!
				},
				sched: sched
			);
		});
	}

	//Alias of advance
	step { | sched | this.advance(sched) }

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

	//Interpolate dur, either replace OR substitute at sched
	interpolateDur { | value, time, sched |
		if(replaceDur, {
			("AlgaPattern: 'dur' interpolation is not supported yet. Running 'replace' instead.").warn;
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
		"AlgaPattern: changing the 'def' key. This will trigger 'replace'.".warn;
		^this.replace(
			def: (def: def),
			time: time,
			sched: sched
		);
	}

	//Buffer == replace
	interpolateBuffer { | sender, param, time, sched |
		//var args = [ param, sender ]; //New buffer connection... Should it be set in the def? (param: sender)?
		("AlgaPattern: changing a Buffer parameter: '" + param.asString ++ ". This will trigger 'replace'.").warn;
		^this.replace(
			def: (def: this.getSynthDef, param: sender),
			//args: args,
			time: time,
			sched: sched
		);
	}

	//Interpolate fx == replace
	interpolateFX { | value, time, sched |
		"AlgaPattern: changing the 'fx' key. This will trigger 'replace'.".warn;
		^this.replace(
			def: (def: this.getSynthDef, fx: value),
			time: time,
			sched: sched
		);
	}

	//Interpolate out == replace
	interpolateOut { | value, time, sched |
		"AlgaPattern: changing the 'out' key. This will trigger 'replace'.".warn;
		^this.replace(
			def: (def: this.getSynthDef, out: value),
			time: time,
			sched: sched
		);
	}

	//Interpolate a parameter that is not in controlNames (like \lag)
	interpolateGenericParam { | sender, param, time, sched |
		("AlgaPattern: changing the '" ++ param.asString ++ "' key. This will trigger 'replace'.").warn;
		^this.replace(
			def: (def: this.getSynthDef, (param): sender), //escape param with ()
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

		//Check valid class
		if(isDefault.not, {
			if((sender.isAlgaNode.not).and(sender.isPattern.not).and(
				sender.isAlgaPatternArg.not).and(
				sender.isAlgaTemp.not).and(
				sender.isNumberOrArray.not).and(sender.isBuffer.not), {
				"AlgaPattern: makeConnection only works with AlgaNodes, AlgaPatterns, AlgaPatternArgs, AlgaTemps, Patterns, Numbers, Arrays and Buffers".error;
				^this;
			});
		});

		//Add scaling to Dicts
		if(scale != nil, { this.addScaling(param, sender, scale) });

		//Parse ListPattern
		if(sender.isListPattern, {
			sender = this.parseListPatternParam(sender)
		});

		//Parse AlgaTemp
		if(sender.isAlgaTemp, {
			sender = this.parseAlgaTempParam(sender)
		});

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
			param = \dur;
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

		//Special case, \out
		if(param == \out, {
			^this.interpolateOut(sender, time, sched);
		});

		//Entry is a Buffer == replace
		if(sender.isBuffer, {
			^this.interpolateBuffer(sender, param, time, sched);
		});

		//Param is not in controlNames. Probably setting another kind of parameter (like \lag)
		if(controlNames[param] == nil, {
			^this.interpolateGenericParam(sender, param, time, sched);
		});

		//Force Pattern / AlgaPatternArg / AlgaTemp dispatch
		if((sender.isPattern).or(sender.isAlgaPatternArg).or(sender.isAlgaTemp), {
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
		time.asString.warn;
		this.stopPattern(now, time);
	}

	//Used when replacing. Free synths and stop the current running pattern
	stopPattern { | now = true, time |
		currentOutTime = time; //store time for \out
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
		});

		//Update blocks too
		AlgaBlocksDict.createNewBlockIfNeeded(this, sender)
	}

	//Add entries to inNodes
	addInNodeListPattern { | sender, param = \in |
		sender.list.do({ | listEntry |
			if(listEntry.isAlgaPatternArg, { listEntry = listEntry.sender });
			if(listEntry.isAlgaNode, {
				if(inNodes.size == 0, {
					this.addInNodeAlgaNode(listEntry, param, mix:false);
				}, {
					this.addInNodeAlgaNode(listEntry, param, mix:true);
				});
			});
			if(listEntry.isListPattern, {
				this.addInNodeListPattern(listEntry)
			});
		});
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
					this.addInNodeListPattern(sender, param);
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

//Extension to support out: from AlgaPattern
+AlgaNode {
	//Add a node to patternOutNodes (used in AlgaBlock)
	addPatternOutNode { | algaPattern, param = \in |
		if(patternOutNodes == nil, { patternOutNodes = IdentityDictionary() });
		if(patternOutNodes[param] == nil, { patternOutNodes[param] = IdentitySet() });
		patternOutNodes[param].add(algaPattern);
	}

	//Remove a node from patternOutNodes (used in AlgaBlock)
	removePatternOutNode { | algaPattern, param = \in |
		var patternOutNodesAtParam;
		if(patternOutNodes == nil, { ^this });
		patternOutNodesAtParam = patternOutNodes[param];
		if(patternOutNodesAtParam == nil, { ^this });
		patternOutNodesAtParam.remove(algaPattern);
	}

	//Triggered when the connection is made
	receivePatternOut { | algaPattern, param = \in, time = 0 |
		var controlNamesAtParam, paramRate, paramNumChannels;
		var patternOutEnvBussesAtParam, patternOutEnvSynthsAtParam;
		var patternOutEnvBussesAtParamAlgaPattern, patternOutEnvSynthsAtParamAlgaPattern;
		var envBus, envSymbol, envSynth;
		var interpBusAtParam, interpBus;
		var isFirstConnection = false;
		var algaSynthBus = algaPattern.synthBus;

		//Get interpBus at param / sender combination
		interpBusAtParam = interpBusses[param];
		if(interpBusAtParam == nil, { ("AlgaNode: invalid interp bus at param '" ++ param ++ "'").error; ^this });

		//ALWAYS use the \default interpBus, which is connected to the \default normSynth.
		//Use the algaSynthBus as index, so that it can safely be removed in remove removePatternOutsAtParam.
		//Using algaPattern as index would not work on .replaces (it would remove it mid-replacement)
		interpBus = interpBusAtParam[algaSynthBus];
		if(interpBus == nil, {
			interpBus = interpBusAtParam[\default];
			if(interpBus == nil, {
				(
					"AlgaNode: invalid interp bus at param '" ++
					param ++ "' and node " ++ algaPattern.asString
				).error;
				^this
			});
			interpBusAtParam[algaSynthBus] = interpBus;
		});

		//Check if patternOutEnvBusses needs to be init
		if(patternOutEnvBusses == nil, { patternOutEnvBusses = IdentityDictionary() });

		//Check if patternOutEnvSynths needs to be init
		if(patternOutEnvSynths == nil, { patternOutEnvSynths = IdentityDictionary() });

		//Check entries at param
		patternOutEnvBussesAtParam = patternOutEnvBusses[param];
		patternOutEnvSynthsAtParam = patternOutEnvSynths[param];

		//Create dict at param
		if(patternOutEnvBussesAtParam == nil, {
			patternOutEnvBusses[param] = IdentityDictionary();
			patternOutEnvBussesAtParam = patternOutEnvBusses[param]; //update pointer
		});

		//Create dict at param
		if(patternOutEnvSynthsAtParam == nil, {
			patternOutEnvSynths[param] = IdentityDictionary();
			patternOutEnvSynthsAtParam = patternOutEnvSynths[param]; //update pointer
		});

		//Check entries at algaPattern
		patternOutEnvBussesAtParamAlgaPattern = patternOutEnvBussesAtParam[algaPattern];
		patternOutEnvSynthsAtParamAlgaPattern = patternOutEnvSynthsAtParam[algaPattern];

		//Create dict at param
		if(patternOutEnvBussesAtParamAlgaPattern == nil, {
			patternOutEnvBussesAtParam[algaPattern] = IdentityDictionary();
			patternOutEnvBussesAtParamAlgaPattern = patternOutEnvBussesAtParam[algaPattern]; //update pointer
		});

		//Create dict at param
		if(patternOutEnvSynthsAtParamAlgaPattern == nil, {
			patternOutEnvSynthsAtParam[algaPattern] = IdentityDictionary();
			patternOutEnvSynthsAtParamAlgaPattern = patternOutEnvSynthsAtParam[algaPattern]; //update pointer
		});

		//Get controlNames
		controlNamesAtParam = controlNames[param];
		if(controlNamesAtParam == nil, { ^this });

		//Get numChannels / rate of param
		paramNumChannels = controlNamesAtParam.numChannels;
		paramRate = controlNamesAtParam.rate;

		//Check if first connection with the specific algaSynthBus
		isFirstConnection = patternOutEnvSynthsAtParamAlgaPattern[algaSynthBus] == nil;

		//First connection
		if(isFirstConnection, {
			//envBus
			envBus = AlgaBus(server, 1, paramRate);
			patternOutEnvBussesAtParamAlgaPattern[algaSynthBus] = envBus; //add entry for algaSynthBus

			//envSymbol
			envSymbol = (
				"alga_patternOutEnv_" ++
				paramRate ++
				paramNumChannels
			).asSymbol;

			//envSynth:
			//This outputs both to interp bus in the form of [0, 0, 0, ..., env]
			//and both to envBus. envBus is then used as multiplier for the tempSynths later on.
			//The output to interpBus is fundamental in order for the envelope to be constant, and not
			//jittery across different triggering of synths (especially if overlapping)
			envSynth = AlgaSynth(
				envSymbol,
				[ \out, interpBus.index, \env_out, envBus.index, \fadeTime, time ],
				interpGroup,
				waitForInst:false
			);
			patternOutEnvSynthsAtParamAlgaPattern[algaSynthBus] = envSynth; //add entry for algaSynthBus
		});

		//Update patternOutNodes
		this.addPatternOutNode(algaPattern, param);

		//Update blocks
		AlgaBlocksDict.createNewBlockIfNeeded(this, algaPattern)
	}

	//Trigger the release of all active out: synths at specific param for a specific algaPattern.
	//This is called everytime a connection is removed
	removePatternOutsAtParam { | algaPattern, param = \in, time |
		var interpBusAtParamAtAlgaPattern = interpBusses[param][algaPattern];
		var patternOutEnvSynthsAtParamAlgaPattern = patternOutEnvSynths[param][algaPattern];
		var patternOutEnvBussesAtParamAlgaPattern = patternOutEnvBusses[param][algaPattern];

		if(patternOutEnvSynthsAtParamAlgaPattern != nil, {
			patternOutEnvSynthsAtParamAlgaPattern.keysValuesDo({ | algaSynthBus, patternOutEnvSynth |
				var patternOutEnvBus = patternOutEnvBussesAtParamAlgaPattern[algaSynthBus];
				//Free bus and entries when synth is done.
				//It's still used while fade-out interpolation is happening
				patternOutEnvSynth.set(\t_release, 1, \fadeTime, time);
				patternOutEnvSynth.onFree({
					//Add the patternOutEnvBus to patternOutEnvBussesToBeFreed.
					//It must be a Dictionary cause of the Array key: needs to be checked by value...
					//This simply frees time of creating multiple IdentityDictionaries instead
					if(patternOutEnvBussesToBeFreed == nil, { patternOutEnvBussesToBeFreed = Dictionary() });
					patternOutEnvBussesToBeFreed[[param, algaPattern, algaSynthBus]] = patternOutEnvBus;

					//Remove entries from patternOutEnvSynths and patternOutEnvBusses
					patternOutEnvSynths[param][algaPattern].removeAt(algaSynthBus);
					patternOutEnvBusses[param][algaPattern].removeAt(algaSynthBus);

					//Remove interpBusAtParam for algaSynthBus but don't free it: it's still used as \default
					if(interpBusAtParamAtAlgaPattern != nil, { interpBusses[param].removeAt(algaSynthBus) });
				});
			});
		});

		//Update patternOutNodes
		this.removePatternOutNode(algaPattern, param);
	}

	//Triggered every patternSynth. algaSynthBus is only used as a "indexer"
	receivePatternOutTempSynth { | algaPattern, algaSynthBus, outTempBus, algaNumChannels, algaRate,
		param = \in, patternBussesAndSynths, chans, scale |

		var envBus;
		var controlNamesAtParam, paramNumChannels, paramRate;
		var interpBusAtParam, interpBus;
		var tempSynthSymbol;
		var tempSynthArgs, tempSynth;

		//Retrieve envBus from patternOutEnvBusses
		envBus = patternOutEnvBusses[param][algaPattern][algaSynthBus];
		if(envBus == nil, { ("AlgaNode: invalid envBus at param '" ++ param ++ "'").error; ^this });

		//Get controlNames
		controlNamesAtParam = controlNames[param];
		if(controlNamesAtParam == nil, { ^this });

		//Get channels / rate of param
		paramNumChannels = controlNamesAtParam.numChannels;
		paramRate = controlNamesAtParam.rate;

		//Calculate scale / chans
		chans = chans.next; //Pattern support
		scale = scale.next; //Pattern support
		scale = this.calculateScaling(
			param,
			nil,
			paramNumChannels,
			scale,
			false //don't update the AlgaNode's scalings dict
		);
		chans = this.calculateSenderChansMappingArray(
			param,
			nil,
			chans,
			algaNumChannels,
			paramNumChannels,
			false //don't update the AlgaNode's chans dict
		);

		//Get interpbus at param / sender combination
		interpBusAtParam = interpBusses[param];
		if(interpBusAtParam == nil, { ("AlgaNode: invalid interp bus at param '" ++ param ++ "'").error; ^this });

		//Get the interpBus: it was declared in receivePatternOut
		interpBus = interpBusAtParam[algaSynthBus];
		if(interpBus == nil, { ("AlgaNode: invalid interp bus at param '" ++ param ++ "'").error; ^this });

		//Symbol. Don't use the fx version as \env is needed
		tempSynthSymbol = (
			"alga_pattern_" ++
			algaRate ++
			algaNumChannels ++
			"_" ++
			paramRate ++
			paramNumChannels ++
			"_out"
		).asSymbol;

		//Read from outTempBus and envBus, write to interpBus
		tempSynthArgs = [
			\in, outTempBus.busArg,
			\out, interpBus.index,
			\fadeTime, 0,
			\env, envBus.busArg
		];

		//Add scaling and chans (scale is an array already containing the symbol)
		tempSynthArgs = tempSynthArgs.addAll(scale).add(\indices).add(chans);

		//Create the tempSynth
		tempSynth = AlgaSynth(
			tempSynthSymbol,
			tempSynthArgs,
			interpGroup,
			\addToTail,
			waitForInst:false
		);

		//Add Synth to activeInterpSynthsAtParam
		this.addActiveInterpSynthOnFree(param, algaPattern, tempSynth);

		//Add Synth to patternBussesAndSynths
		patternBussesAndSynths.add(tempSynth);

		//Free dangling patternEnvBusses related to this [param, algaPattern, algaSynthBus] combo
		tempSynth.onFree({
			if(patternOutEnvBussesToBeFreed != nil, {
				var patternEnvBusAtAlgaSynthBus = patternOutEnvBussesToBeFreed[[param, algaPattern, algaSynthBus]];
				if(patternEnvBusAtAlgaSynthBus != nil, {
					patternEnvBusAtAlgaSynthBus.free;
					patternOutEnvBussesToBeFreed.removeAt([param, algaPattern, algaSynthBus]);
				});
				if(patternOutEnvBussesToBeFreed.size == 0, { patternOutEnvBussesToBeFreed = nil });
			});
		});
	}
}

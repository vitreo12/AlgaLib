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

AlgaPattern : AlgaNode {
	/*
	TODOs:

	1) mixFrom()
	2) \dur - \sustain - \stretch - \legato interpolations
	*/

	//Special groups used in AlgaPattern
	var <>fxGroup;
	var <>fxConvGroup;
	var <>synthConvGroup;

	//The actual Pattern
	var <pattern;

	//The pattern(s) as stream. Thes are used for manual .step
	var <patternsAsStreams;

	//The Event input to be manipulated
	var <eventPairs;

	//The Event input not manipulated
	var <>defPreParsing;

	//Use MC expansion or not
	var <useMultiChannelExpansion = true;

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

	//Keep track of the CURRENT patternInterpSumBus AlgaBus per-param. This is updated every pattern trigger.
	//It is fundamental to have the current one in order to add do it a mid-pattern synth when user
	//changes a param mid-pattern.
	var <>latestPatternInterpSumBusses;

	//Keep track of latest clock time
	var <latestPatternTime;

	//Keep track of latest dur value / stream
	var <latestDur;
	var <latestDurStream;

	//Keep track of ALL active patternParamSynths
	var <>currentActivePatternParamSynths;

	//Keep track of ALL active interpBusses
	var <>currentActiveInterpBusses;

	//Keep track of ALL active patternInterpSumBusses
	var <>currentActivePatternInterpSumBusses;

	//Keep track of ALL active patternBussesAndSynths
	var <>currentPatternBussesAndSynths;

	//Current fx
	var <currentFX;

	//Current \out
	var <currentOut;

	//Current nodes of used in currentOut
	var <>currentPatternOutNodes;
	var <>prevPatternOutNodes;

	//Current time used for \out replacement
	var <currentPatternOutTime;

	//Current shape used for \out replacement
	var <currentPatternOutShape;

	//Needed to store reset for various alga params (\out, \fx, etc...)
	var <currentReset;

	//Needed to store current generic params
	var <scalarAndGenericParams;
	var <scalarAndGenericParamsStreams;
	var <latestScalarUniqueIDs;
	var <scalarsInAlgaStep;

	//This is used for nested AlgaTemps
	var <currentAlgaTempGroup;

	//Skip an iteration
	var skipIteration = false;

	//Skip an iteration for FX
	var skipIterationFX = false;

	//On .replace,
	//If true: stop pattern and only fade out (no fade in)
	//If false: normal play fadeIn / fadeOut mechanism
	var <stopPatternBeforeReplace = true;

	//Used to set sustain's gate
	var <>isSustainTrig = false;

	//The list of IDs of active synths to send \gate,0 to
	var <>sustainIDs;

	//Schedule sustain to the internal clock or seconds
	var <schedSustainInSeconds = false;

	//If false, sustain must be explicitly set by the user.
	//If true, sustain will be: (sustain * stretch) + dur.
	//legato will apply in both cases.
	var <sustainToDur = false;

	//If sampleAccurate for Functions
	var <sampleAccurateFuncs = true;

	//Used for AlgaPatternPlayer
	var <>player;
	var <>players;
	var <>latestPlayersAtParam;
	var <>paramContainsAlgaReaderPfunc = false;

	//Used for parsing Patterns
	var <recursiveObjectList;

	//Add the \algaNote event to Event
	*initClass {
		//StartUp.add is needed: Event class must be compiled first
		StartUp.add({ this.addAlgaNoteEventType });
	}

	//Doesn't have args and outsMapping like AlgaNode. Default sched to 1 (so it plays on clock)
	*new { | def, interpTime, interpShape, playTime, playSafety, sched = 1,
		schedInSeconds = false, tempoScaling = false,
		sampleAccurateFuncs = true, player, server |
		^super.new_ap(
			def: def,
			interpTime: interpTime,
			interpShape: interpShape,
			playTime: playTime,
			playSafety: playSafety,
			sched: sched,
			schedInSeconds: schedInSeconds,
			tempoScaling: tempoScaling,
			sampleAccurateFuncs: sampleAccurateFuncs,
			player: player,
			server: server
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
			var dur = ~dur ? 0; //for \none dur
			var offset = ~timingOffset;
			var lag = ~lag;
			var latency = ~latency;
			var sustain = ~sustain;
			var stretch = ~stretch;
			var legato = ~legato;
			var hasSustain = sustain.isNumber;

			//Needed for Event syncing
			~isPlaying = true;

			//Deal with sustain
			if(hasSustain, {
				//scale by stretch
				sustain = sustain * stretch;

				//If sustainToDur, add to dur. This allows to do things like sustain: -0.5 (from dur).
				//Also, if sustain is 0 here, it will then use \dur
				if(~algaPattern.sustainToDur, {
					sustain = sustain + dur; //dur already includes stretch!
				});

				//scale by legato
				if(legato > 0, { sustain = sustain * legato });

				//Finally, if the result is a positive number, go ahead
				if(sustain > 0, {
					~algaPattern.isSustainTrig = true;
					~algaPattern.sustainIDs = Array();
				}, {
					if(~algaPattern.sustainToDur, {
						("AlgaPattern: your 'sustain' is 0 or less: " ++
							sustain ++ ". This might cause the Synths not to be released").warn;
					});
					hasSustain = false
				});
			});

			//Create the bundle with all needed Synths for this Event.
			bundle = algaPatternServer.makeBundle(false, {
				//First, consume scheduledStepActionsPre if there are any
				~algaPattern.advanceAndConsumeScheduledStepActions(false);

				//Then, create all needed synths
				~algaPattern.createEventSynths(
					algaSynthDef: algaSynthDef,
					algaSynthBus: algaSynthBus,
					algaPatternInterpStreams: algaPatternInterpStreams,
					fx: fx,
					algaOut: algaOut,
					dur: dur,
					sustain: sustain,
					stretch: stretch,
					legato: legato
				);

				//Finally, consume scheduledStepActionsPost if there are any
				~algaPattern.advanceAndConsumeScheduledStepActions(true);

				//Reset scalarsInAlgaStep
				~algaPattern.scalarsInAlgaStep.clear;
			});

			//Send bundle to server using the same server / clock as the AlgaPattern
			//Note that this does not go through the AlgaScheduler directly, but it uses its same clock!
			schedBundleArrayOnClock(
				offset,
				algaPatternClock,
				bundle,
				lag,
				algaPatternServer,
				latency
			);

			//Sched sustain if provided
			if(hasSustain, {
				~algaPattern.scheduleSustain(
					sustain,
					offset,
					lag,
					latency
				);
			});

			//Always reset isSustainTrig
			~algaPattern.isSustainTrig = false;
		});
	}

	//Set dur asStream for it to work within Pfuncn
	setDur { | value, newInterpStreams |
		if(newInterpStreams == nil, {
			interpStreams.dur = value.algaAsStream
		}, {
			newInterpStreams.dur = value.algaAsStream
		});
	}

	//Set sustain asStream for it to work within Pfuncn
	setSustain { | value, newInterpStreams |
		if(newInterpStreams == nil, {
			interpStreams.sustain = value.algaAsStream
		}, {
			newInterpStreams.sustain = value.algaAsStream
		});
	}

	//Set stretch asStream for it to work within Pfuncn
	setStretch { | value, newInterpStreams |
		if(newInterpStreams == nil, {
			interpStreams.stretch = value.algaAsStream
		}, {
			newInterpStreams.stretch = value.algaAsStream
		});
	}

	//Set stretch asStream for it to work within Pfuncn
	setLegato { | value, newInterpStreams |
		if(newInterpStreams == nil, {
			interpStreams.legato = value.algaAsStream
		}, {
			newInterpStreams.legato = value.algaAsStream
		});
	}

	//Set replaceDur
	replaceDur_ { | value = false |
		if(value.isKindOf(Boolean).not, {
			"AlgaPattern: 'replaceDur' only supports boolean values. Setting it to false".error;
			value = false;
		});
		replaceDur = value
	}

	//Set useMultiChannelExpansion
	useMultiChannelExpansion_ { | value = false |
		if(value.isKindOf(Boolean).not, {
			"AlgaPattern: 'useMultiChannelExpansion' only supports boolean values. Setting it to false".error;
			value = false;
		});
		useMultiChannelExpansion = value
	}

	//Alias
	multiChannelExpansion {
		^useMultiChannelExpansion
	}

	//Alias
	multiChannelExpansion_ { | value = false |
		this.useMultiChannelExpansion(value)
	}

	//Set stopPatternBeforeReplace
	stopPatternBeforeReplace_ { | value = true |
		if(value.isKindOf(Boolean).not, {
			"AlgaPattern: 'stopPatternBeforeReplace' only supports boolean values. Setting it to true".error;
			value = true;
		});
		stopPatternBeforeReplace = value
	}

	//Set schedSustainInSeconds
	schedSustainInSeconds_ { | value = false |
		if(value.isKindOf(Boolean).not, {
			"AlgaPattern: 'schedSustainInSeconds' only supports boolean values. Setting it to false".error;
			value = false;
		});
		schedSustainInSeconds = value
	}

	//Set sustainToDur
	sustainToDur_ { | value = false |
		if(value.isKindOf(Boolean).not, {
			"AlgaPattern: 'sustainToDur' only supports boolean values. Setting it to false".error;
			value = false;
		});
		sustainToDur = value
	}

	//Set sampleAccurateFuncs
	sampleAccurateFuncs_ { | value = true |
		if(value.isKindOf(Boolean).not, {
			"AlgaPattern: 'sampleAccurateFuncs' only supports boolean values. Setting it to true".error;
			value = true;
		});
		sampleAccurateFuncs = value
	}

	//Free all unused busses from interpStreams
	freeUnusedInterpBusses { | algaPatternInterpStreams |
		var interpBussesToFree = algaPatternInterpStreams.interpBussesToFree;
		interpBussesToFree.do({ | interpBus |
			//If the identity set is empty, it needs to be freed
			var toBeFreed = currentActiveInterpBusses[interpBus].size == 0;
			if(toBeFreed, {
				interpBus.free;
				interpBussesToFree.remove(interpBus);
				currentActiveInterpBusses.removeAt(interpBus);
			});
		});
	}

	//Schedule sustain
	scheduleSustain { | sustain, offset, lag, latency |
		if(sustainIDs.size > 0, {
			var clock = this.clock;
			if(schedSustainInSeconds, { clock = SystemClock });
			schedBundleArrayOnClock(
				sustain + offset,
				clock,
				[15 /* \n_set */, sustainIDs, \gate, 0].flop,
				lag,
				server,
				latency
			)
		});
	}

	//Advance scheduled actions
	advanceAndConsumeScheduledStepActions { | post = false |
		actionScheduler.advanceAndConsumeScheduledStepActions(post)
	}

	//Create a temporary synth according to the specs of the AlgaTemp
	createAlgaTempSynth { | algaTemp, patternBussesAndSynths, createAlgaTempGroup = false |
		var tempBus, tempSynth;
		var tempSynthArgs = [ \gate, 1 ];
		var tempNumChannels = algaTemp.numChannels;
		var tempRate = algaTemp.rate;
		var def, algaTempDef, tempControlNames;
		var defIsEvent = false;

		//New AlgaTemp at top level: create a new currentAlgaTempGroup.
		//This is used to do AlgaTemps into AlgaTemps into AlgaTemps ...
		if(createAlgaTempGroup, {
			currentAlgaTempGroup = AlgaGroup(tempGroup, waitForInst: false);
			patternBussesAndSynths.add(currentAlgaTempGroup); //Free on synth's free
		});

		//Check AlgaTemp validity
		if(algaTemp.valid.not, {
			"AlgaPattern: Invalid AlgaTemp, using default parameter".error;
			^nil
		});

		//Unpack SynthDef
		algaTempDef = algaTemp.def;
		def = algaTempDef;

		//If Event, SynthDef is under [\def]
		if(algaTempDef.isEvent, {
			def = algaTempDef[\def];
			defIsEvent = true;
		});

		//Unpack controlNames
		tempControlNames = algaTemp.controlNames;

		//Loop around the controlNames to set relevant parameters
		tempControlNames.do({ | controlName |
			var paramName = controlName.name;
			var paramNumChannels = controlName.numChannels;
			var paramRate = controlName.rate;
			var paramDefault = controlName.defaultValue;
			var entry;

			//Retrieve param if entry is Event
			if(defIsEvent, { entry = algaTempDef[paramName] });

			//If entry is nil, the tempSynth will already use the default value
			if(entry != nil, {
				//Ignore static params
				if(this.checkValidControlName(paramName), {
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
						isAlgaTemp: true
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
			currentAlgaTempGroup ? tempGroup, //currentAlgaTempGroup comes from the top AlgaTemp in a nested statement
			\addToTail,
			false
		);

		//Add Bus to patternBussesAndSynths
		patternBussesAndSynths.add(tempBus);

		//The synth is already contained in currentAlgaTempGroup if it's valid
		if(currentAlgaTempGroup == nil, { patternBussesAndSynths.add(tempSynth) });

		//If sustain, add tempSynth's ID to sustains'
		if(isSustainTrig, { sustainIDs = sustainIDs.add(tempSynth.nodeID) });

		//Return the AlgaBus that the tempSynth writes to
		^tempBus.busArg;
	}

	//Add patternParamSynth as child of patternInterpSumBus (used to create temporary synths)
	addActivePatternParamSynth { | patternInterpSumBus, patternParamSynth |
		//Add to currentActivePatternParamSynths
		currentActivePatternParamSynths[patternInterpSumBus] = currentActivePatternParamSynths[patternInterpSumBus] ? IdentitySet();
		currentActivePatternParamSynths[patternInterpSumBus].add(patternParamSynth);
	}

	//Add patternParamSynth as child of a specific patternEnvBus
	addActiveInterpBus { | patternParamEnvBus, patternParamSynth |
		//Add current active currentActiveInterpBusses
		currentActiveInterpBusses[patternParamEnvBus] = currentActiveInterpBusses[patternParamEnvBus] ? IdentitySet();
		currentActiveInterpBusses[patternParamEnvBus].add(patternParamSynth);

		//Remove the entry on patternParamSynth's free
		patternParamSynth.onFree({
			var set = currentActiveInterpBusses[patternParamEnvBus];
			if(set != nil, { set.remove(patternParamSynth) });
		});
	}

	//Create one pattern synth with the entry / uniqueID pair at paramName
	//This is the core of the interpolation behaviour for AlgaPattern !!
	createPatternParamSynth { | entry, uniqueID, paramName, paramNumChannels, paramRate,
		paramDefault, patternInterpSumBus, patternBussesAndSynths, scaleArraysAndChansAtParam,
		sampleAndHold, algaPatternInterpStreams, isFX = false, isAlgaTemp = false, isTemporary = false |

		var sender, senderNumChannels, senderRate;
		var chansMapping, scale;
		var validParam = false;
		var isNotFxAndAlgaTemp = (isFX.not).and(isAlgaTemp.not);

		//Reset currentAlgaTempGroup
		if(isAlgaTemp.not, { currentAlgaTempGroup = nil });

		//Unpack Pattern value
		//Only if not using MC (it's already been unpacked) OR
		//is FX / AlgaTemp / Temporary (the mid-interpolation ones)
		if((useMultiChannelExpansion.not).or(isFX).or(isAlgaTemp).or(isTemporary), {
			if(entry.isStream, { entry = entry.next });
		});

		//Unpack Pattern values for AA, AT and AO
		//Only if not AlgaReader (coming from an AlgaPatternPlayer, which already unpacks)
		if(entry.isAlgaReader.not, {
			entry.algaAdvance
		}, {
			entry = entry.entry //Unpack AlgaReader
		});

		//Check if it's an AlgaArg. Unpack it.
		if(entry.isAlgaArg, {
			chansMapping = entry.chans;
			scale        = entry.scale;
			entry        = entry.sender;
		});

		//Check if it's an AlgaTemp. Create it
		if(entry.isAlgaTemp, {
			var algaTemp = entry;

			//If valid, this returns the AlgaBus that the tempSynth will write to
			entry = this.createAlgaTempSynth(
				algaTemp: algaTemp,
				patternBussesAndSynths: patternBussesAndSynths,
				createAlgaTempGroup: isAlgaTemp.not //Top level AlgaTemp
			);

			//Valid AlgaTemp. entry is an AlgaBus now
			if(entry.isNil.not, {
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
			if(entry.isArray, {
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
					//("AlgaPattern: can't connect to an AlgaNode that's been cleared").error;
					^this;
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
				scale = nil;
				chansMapping = nil;
				("AlgaPattern: AlgaNode wasn't algaInstantiated yet. Using the default value " ++
					entry ++ " for '" ++ paramName ++ "'").warn;
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
			("AlgaPattern: trying to set 'nil' for param '" ++ paramName ++
				"'. Using default value " ++ paramDefault.asString ++" instead").error;
			validParam = true;
		}

		//Symbol (like, \skip or \rest): skip iteration.
		//It's very important not to use Rest() here, as \dur will also pick it up, generating
		//an actual double Rest(). Rest() should only be used in \dur / \delta.
		{ entry.isSymbol } {
			if(isNotFxAndAlgaTemp, { skipIteration = true }, { skipIterationFX = true });
			^this;
		};

		if(validParam, {
			//Get the bus where interpolation envelope is written to...
			//REMEMBER that for AlgaPattern, interpSynths are actually JUST the
			//interpolation envelope, which is then passed through this individual synths!
			// ... Now, I need to keep track of all the active interpBusses instead, not retrievin
			//from interpBusses, which gets replaced in language, but should implement the same
			//behaviour of activeInterpSynths and get busses from there.
			var patternParamEnvBus;
			var validPatternParamEnvBus = true;

			if(isNotFxAndAlgaTemp, {
				//Not \fx parameter: retrieve correct envelope bus
				patternParamEnvBus = algaPatternInterpStreams.interpBusses[paramName][uniqueID];
				validPatternParamEnvBus = patternParamEnvBus != nil;
				if(validPatternParamEnvBus, { validPatternParamEnvBus = patternParamEnvBus.bus != nil });
			});

			if(validPatternParamEnvBus, {
				var patternParamSynth;
				var patternParamSymbol;
				var patternParamSynthArgs;
				var scaleArrayAndChansAtParam;
				var scaleArray;
				var indices;

				if(isNotFxAndAlgaTemp, {
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
						if(scaleArrayAndChansAtParam != nil, {
							scale = scaleArrayAndChansAtParam[0]; //0 == scaleArray
							scale = scale.next; //This has not been advanced yet
						});
					});
				}, {
					//FX has no env
					patternParamSynthArgs = [
						\in, entry,
						\out, patternInterpSumBus.index,
						\fadeTime, 0
					];
				});

				//Calculate scaleArray
				scaleArray = this.calculateScaling(
					paramName,
					sender,
					paramNumChannels,
					scale,
					addScaling: false, //don't update the AlgaNode's scalings dict
				);

				if(scaleArray != nil, {
					patternParamSynthArgs = patternParamSynthArgs.addAll(scaleArray);
				});

				//\fx parameter does not use global scaleArrayAndChans
				if(isNotFxAndAlgaTemp, {
					//If AlgaPatternArg's is nil, use argument's one (if defined)
					if(chansMapping == nil, {
						if(scaleArrayAndChansAtParam != nil, {
							chansMapping = scaleArrayAndChansAtParam[1]; //1 == chans
							chansMapping = chansMapping.next; //This has not been advanced yet
						});
					});
				});

				//Always calculate chansMapping for the modulo around paramNumChannels!
				indices = this.calculateSenderChansMappingArray(
					paramName,
					sender,
					chansMapping,
					senderNumChannels,
					paramNumChannels,
					updateParamsChansMapping: false, //don't update the AlgaNode's chans dict
				);

				//Add \indices (chans)
				patternParamSynthArgs = patternParamSynthArgs.add(\indices).add(indices);

				//sampleAndHold (use == true) as sampleAndHold could be nil too
				if(sampleAndHold == true, {
					patternParamSynthArgs = patternParamSynthArgs.add(\sampleAndHold).add(1).add(\t_sah).add(1);
				});

				if(isNotFxAndAlgaTemp, {
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
					if(isAlgaTemp.not,
						{ interpGroup },
						{ currentAlgaTempGroup ? tempGroup } //currentAlgaTempGroup is used in nested AlgaTemps
					),
					\addToTail,
					waitForInst: false
				);

				//Register patternParamSynth to be freed.
				if(isAlgaTemp.not, {
					patternBussesAndSynths.add(patternParamSynth)
				}, {
					//For AlgaTemps, also check validity of currentAlgaTempGroup
					//If it's valid, the synths would already be freed when that group is freed.
					if(currentAlgaTempGroup == nil, {
						patternBussesAndSynths.add(patternParamSynth)
					});
				});

				//Don't add the FX patternParamSynths: they're already handled
				if(isNotFxAndAlgaTemp,{
					//Add patternParamSynth as child of patternInterpSumBus (used to create temporary synths)
					this.addActivePatternParamSynth(patternInterpSumBus, patternParamSynth);

					//Add patternParamSynth as child of patternEnvBus (used to free patternEnvBus when appropriate)
					this.addActiveInterpBus(patternParamEnvBus, patternParamSynth);
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
		algaPatternInterpStreams, fx, mcSynthNum, mcEntriesAtParam |

		//If MC, loop from mcEntriesAtParam
		if(useMultiChannelExpansion, {
			//MC: loop from mcEntriesAtParam
			if(mcEntriesAtParam != nil, {
				mcEntriesAtParam.keysValuesDo({ | uniqueID, entry | //indexed by uniqueIDs
					if(mcSynthNum != nil, { entry = entry[mcSynthNum] }); //extract from MC array
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
		}, {
			//No MC: loop from interpStreamsEntriesAtParam
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
		});
	}

	//Create all needed Synths and Busses for an FX
	createFXSynthAndPatternSynths { | fx, numChannelsToUse, rateToUse,
		algaSynthDef, algaSynthBus, algaPatternInterpStreams,
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

		//Args to fxSynth
		var fxSynthArgs = [\gate, 1];

		//Actual fxSynth
		var fxSynthSymbol;
		var fxSynth;

		//If channels mismatch, these will be used to create final conversion
		//from fxSynth to algaSynthBus.
		var fxInterpSynthSymbol, fxInterpSynthArgs, fxInterpSynth;

		//Create the Bus patternSynth will write to.
		//It uses numChannelsToUse and rateToUse, which are set accordingly to the \def specs
		var patternBus = AlgaBus(server, numChannelsToUse, rateToUse);

		//The final free func
		var onFXSynthFreeFunc;

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
				//Ignore static params AND \in
				if((this.checkValidControlName(paramName)).and(paramName != \in), {
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
				synthConvGroup,
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

		//If not skipping the iteration
		if(skipIterationFX.not, {
			//Create fxSynth, \addToTail
			fxSynth = AlgaSynth(
				fxSynthSymbol,
				fxSynthArgs,
				fxGroup,
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
					fxConvGroup,
					\addToTail,
					false
				);

				//Add to patternBussesAndSynthsFx
				patternBussesAndSynthsFx.add(fxInterpSynth);
			});
		});

		//Free all relative synths / busses on fxSynth free.
		//fxSynth is either freed by itself (if explicitFree) or when patternSynth is freed.
		onFXSynthFreeFunc = {
			//Free synths and busses
			patternBussesAndSynthsFx.do({ | synthOrBus | synthOrBus.free });

			//Remove fxSynth from algaPatternSynths
			algaPatternInterpStreams.algaPatternSynths.remove(fxSynth);
		};

		//Check if skipping the iteration for FX
		if(skipIterationFX.not, {
			//Execute func on fxSynth's free
			fxSynth.onFree(onFXSynthFreeFunc);

			//Use patternBus as \out for patternSynth
			patternSynthArgs = patternSynthArgs.add(\out).add(patternBus.index);
		}, {
			onFXSynthFreeFunc.value;
		});

		//Reset skipIterationFX
		skipIterationFX = false;

		//Return patternSynthArgs
		^patternSynthArgs;
	}

	//Create a temporary synth for \out connection
	createOutConnection { | algaOut, algaSynthBus, outTempBus, patternBussesAndSynths |
		case
		{ algaOut.isAlgaNode } {
			//Only if instantiated (or it will click)
			if(algaOut.algaInstantiatedAsReceiver(\in), {
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
			var node, param, scale, chans;

			//Advance
			algaOut.algaAdvance;

			//Unpack
			node  = algaOut.node;
			param = algaOut.param;
			scale = algaOut.scale;
			chans = algaOut.chans;

			if(node.isAlgaNode, {
				//Only if instantiated (or it will click)
				if(node.algaInstantiatedAsReceiver(param), {
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

	//Add to currentActivePatternInterpSumBusses[paramName]
	addCurrentActivePatternInterpSumBussesForThisPatternSynth { | paramName, patternInterpSumBus |
		var currentActivePatternInterpSumBussesAtParam = currentActivePatternInterpSumBusses[paramName];
		if(currentActivePatternInterpSumBussesAtParam == nil, {
			currentActivePatternInterpSumBusses[paramName] = IdentitySet();
			currentActivePatternInterpSumBussesAtParam = currentActivePatternInterpSumBusses[paramName];
		});
		currentActivePatternInterpSumBussesAtParam.add(patternInterpSumBus);
	}

	//Remove from currentActivePatternInterpSumBusses[paramName] using latest ones
	removeCurrentActivePatternInterpSumBussesForThisPatternSynth { | patternInterpSumBusses |
		patternInterpSumBusses.keysValuesDo({ | paramName, patternInterpSumBus |
			var currentActivePatternInterpSumBussesAtParam = currentActivePatternInterpSumBusses[paramName];
			if(currentActivePatternInterpSumBussesAtParam != nil, {
				currentActivePatternInterpSumBussesAtParam.remove(patternInterpSumBus)
			});
			currentPatternBussesAndSynths.removeAt(patternInterpSumBus);
		});
	}

	//Create all needed Synths / Busses for an individual patternSynth
	createPatternSynth { | algaSynthDef, algaSynthDefClean, algaSynthBus,
		algaPatternInterpStreams, controlNamesToUse, fx, algaOut,
		mcSynthNum, mcEntries, dur, sustain, stretch, legato |
		//Used to check whether using a ListPattern of \defs
		var numChannelsToUse  = numChannels;
		var rateToUse = rate;

		//Flag to check for mismatches (and needed conversions) for numChannels / rates
		var numChannelsOrRateMismatch = false;

		//These will be populated and freed when the patternSynth is released
		var patternBussesAndSynths = IdentitySet(controlNames.size * 2);

		//args to patternSynth
		var patternSynthArgs = [
			\gate, 1,
			\dur, dur,
			\sustain, sustain,
			\stretch, stretch,
			\legato, legato
		];

		//The actual synth that will be created
		var patternSynth;

		//All the patternInterpSumBusses here created
		var patternInterpSumBussesForThisPatternSynth = IdentityDictionary();

		//Valid \fx our \out
		var validFX = false;
		var validOut = false;

		//The final free func
		var onPatternSynthFreeFunc;

		//Check if it's a ListPattern and retrieve correct controlNames
		if(controlNamesList != nil, {
			controlNamesToUse = controlNamesList[algaSynthDefClean];
			if(controlNamesToUse == nil, { controlNamesToUse = controlNames });
		});

		//Loop over controlNames and create as many Busses and Synths as needed,
		//also considering interpolation / normalization
		controlNamesToUse.do({ | controlName |
			var paramName = controlName.name;
			var paramNumChannels = controlName.numChannels;
			var paramRate = controlName.rate;
			var paramDefault = controlName.defaultValue;

			//
			if((paramRate == \control).or(paramRate == \audio), {
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

				//Used in MC
				var mcEntriesAtParam;

				//scaleArray and chans
				var scaleArraysAndChansAtParam = algaPatternInterpStreams.scaleArraysAndChans[paramName];

				//sampleAndHold
				var sampleAndHold = algaPatternInterpStreams.sampleAndHolds[paramName];
				sampleAndHold = sampleAndHold ? false;

				//MultiChannel: extract from mcEntries
				if(useMultiChannelExpansion, {
					mcEntriesAtParam = mcEntries[paramName]
				});

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
					algaPatternInterpStreams: algaPatternInterpStreams,
					mcSynthNum: mcSynthNum,
					mcEntriesAtParam: mcEntriesAtParam
				);

				//Read from patternParamNormBus
				patternSynthArgs = patternSynthArgs.add(paramName).add(patternParamNormBus.busArg);

				//Register normBus, normSynth, interSumBus to be freed
				patternBussesAndSynths.add(patternParamNormBus);
				patternBussesAndSynths.add(patternParamNormSynth);
				patternBussesAndSynths.add(patternInterpSumBus);

				//Latest patternInterpSumBus
				latestPatternInterpSumBusses[paramName] = patternInterpSumBus;

				//Add to currentPatternBussesAndSynths.
				//These are indexed with patternInterpSumBus in order to be retrieved for temporary synths
				currentPatternBussesAndSynths[patternInterpSumBus] = patternBussesAndSynths;

				//This is used for removal later on
				patternInterpSumBussesForThisPatternSynth[paramName] = patternInterpSumBus;

				//Current active patternInterpSumBusses at paramName
				this.addCurrentActivePatternInterpSumBussesForThisPatternSynth(
					paramName,
					patternInterpSumBus
				);
			}, {
				//Scalar values
				var paramValue, scalarInAlgaStep = false;

				//If active AlgaSched, get value DIRECTLY from scalarAndGenericParamsStreams
				if(scalarsInAlgaStep != nil, {
					if(scalarsInAlgaStep.includes(paramName), {
						paramValue = scalarAndGenericParamsStreams[paramName][
							latestScalarUniqueIDs[paramName]
						].next; //Manual advance !
						scalarInAlgaStep = true
					});
				});

				//Standard case: get value from currentEnvironment (patternPairs)
				if(scalarInAlgaStep.not, {
					paramValue = currentEnvironment[paramName];
				});

				//Add to synth's args
				if(paramValue != nil, {
					patternSynthArgs = patternSynthArgs.add(paramName).add(paramValue)
				});
			});
		});

		//If ListPattern, retrieve correct numChannels
		if(numChannelsList != nil, {
			numChannelsToUse = numChannelsList[algaSynthDefClean];
			if(numChannelsToUse == nil, { numChannelsToUse = numChannels });
		});

		//If ListPattern, retrieve correct rate
		if(rateList != nil, {
			rateToUse = rateList[algaSynthDefClean];
			if(rateToUse == nil, { rateToUse = rate });
		});

		//If there is a mismatch between numChannels / numChannelsToUse OR rate / rateToUse,
		//use numChannelsToUse and rateToUse to determine patternSynth's output.
		//Then, convert it to be used with algaSynthBus.
		if((numChannels != numChannelsToUse).or(rate != rateToUse), {
			numChannelsOrRateMismatch = true
		});

		//Extract fx multichannel
		if(useMultiChannelExpansion, {
			if(fx.isArray, {
				if(mcSynthNum != nil, { fx = fx[mcSynthNum] })
			});
		}, {
			//Or use first entry if not MC
			if(fx.isArray, { fx = fx[0] });
		});

		//Check validFX
		if((fx != nil).and(fx.isEvent), { validFX = true });

		//Extract out multichannel (not valid in FX)
		if(useMultiChannelExpansion, {
			if(algaOut.isArray, {
				if(mcSynthNum != nil, { algaOut = algaOut[mcSynthNum] })
			});
		}, {
			//Or use first entry if not MC
			if(algaOut.isArray, { algaOut = algaOut[0] });
		});

		//Check validOut
		if((algaOut != nil).and((algaOut.isAlgaOut).or(algaOut.isAlgaNode)), { validOut = true });

		//If fx
		if(validFX, {
			//Reset Event's parents to avoid collisions with namespaces! This is fundamental
			//to have \amp parameter to work
			fx.parent = nil; fx.proto = nil;

			//This returns the patternSynthArgs with correct bus to write to (the fx one)
			patternSynthArgs = this.createFXSynthAndPatternSynths(
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
					synthConvGroup,
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
			algaSynthDef = (algaSynthDef.asString.replace("_algaPattern", "_algaPatternTempOut")).asSymbol;

			//Add patternTempOut writing to patternSynthArgs
			patternSynthArgs = patternSynthArgs.add(\patternTempOut).add(outTempBus.index);
		});

		//The actual patternSynth according to the user's def
		if(skipIteration.not, {
			patternSynth = AlgaSynth(
				algaSynthDef,
				patternSynthArgs,
				synthGroup,
				waitForInst: false
			);

			//Add pattern synth to algaPatternSynths, and free it when patternSynth gets freed
			algaPatternInterpStreams.algaPatternSynths.add(patternSynth);

			//If sustain, add patternSynth to sustains'
			if(isSustainTrig, {
				sustainIDs = sustainIDs.add(patternSynth.nodeID)
			});
		});

		//Free all normBusses, normSynths, interpBusses and interpSynths on patternSynth's release
		onPatternSynthFreeFunc = {
			//Free all Synths and Busses
			patternBussesAndSynths.do({ | synthOrBus | synthOrBus.free });

			//Remove the entry from algaPatternSynths
			algaPatternInterpStreams.algaPatternSynths.remove(patternSynth);

			//Remove all busses from currentActivePatternInterpSumBusses
			//and currentPatternBussesAndSynths
			this.removeCurrentActivePatternInterpSumBussesForThisPatternSynth(
				patternInterpSumBussesForThisPatternSynth
			);

			//Remove currentActivePatternParamSynths[patternInterpSumBus]
			patternInterpSumBussesForThisPatternSynth.do({ | patternInterpSumBus_tmp |
				currentActivePatternParamSynths.removeAt(patternInterpSumBus_tmp)
			});

			//IMPORTANT: free the unused interpBusses of the interpStreams.
			//This needs to happen on patternSynth's free as that's the notice
			//that no other synths will be using them. Also, this fixes the case where
			//patternSynth takes longer than \dur. We want to wait for the end of patternSynth
			//to free all used things!
			this.freeUnusedInterpBusses(algaPatternInterpStreams);
		};

		//If not skipping, execute on patternSynth's free.
		if(skipIteration.not, {
			patternSynth.onFree(onPatternSynthFreeFunc)
		}, {
			onPatternSynthFreeFunc.value
		});

		//Reset
		skipIteration = false;
	}

	//Calculate the MC mismatches and return a new array with all the correct settings.
	//Each entry will be used to create an individual patternSynth
	calculateMultiChannelMismatches { | controlNamesToUse, algaPatternInterpStreams, fx, algaOut |
		var numOfSynths = 0;
		var entries = IdentityDictionary();

		//Loop over the actual control names
		controlNamesToUse.do({ | controlName |
			var paramName = controlName.name;
			var paramNumChannels = controlName.numChannels;
			var paramRate = controlName.rate;
			if((paramRate == \control).or(paramRate == \audio), {
				var interpStreamsEntriesAtParam = algaPatternInterpStreams.entries[paramName];
				if(interpStreamsEntriesAtParam != nil, {
					interpStreamsEntriesAtParam.keysValuesDo({ | uniqueID, entry |
						//Unpack Pattern value
						if(entry.isStream, { entry = entry.next });

						//Set entries at paramName
						entries[paramName] = entries[paramName] ? IdentityDictionary();

						//Use uniqueID
						entries[paramName][uniqueID] = [entry, paramNumChannels];

						//Retrieve the highest numOfSynths to spawn
						if(entry.isArray, {
							var arraySize = entry.size;
							if(arraySize > paramNumChannels, {
								numOfSynths = max(
									arraySize - (paramNumChannels - 1),
									numOfSynths
								)
							});
						});
					});
				});
			});
		});

		//Also check \fx
		if(fx.isArray, {
			var arraySize = fx.size;
			entries[\fx] = entries[\fx] ? IdentityDictionary();
			numOfSynths = max(arraySize, numOfSynths);
			entries[\fx] = fx.reshape(numOfSynths);
		});

		//Also check \out
		if(algaOut.isArray, {
			var arraySize = algaOut.size;
			entries[\algaOut] = entries[\algaOut] ? IdentityDictionary();
			numOfSynths = max(arraySize, numOfSynths);
			entries[\algaOut] = algaOut.reshape(numOfSynths);
			if(arraySize > numOfSynths, { //needs to be re-done, overwritten
				entries[\fx] = fx.reshape(numOfSynths);
			});
		});

		//Loop over entries and distribute the values across the synths to spawn
		entries.keysValuesDo({ | paramName, entriesAtParamName |
			if((paramName != \fx).and(paramName != \algaOut), {
				entriesAtParamName.keysValuesDo({ | uniqueID, entryAndParamNumChannels |
					var entry = entryAndParamNumChannels[0];
					var paramNumChannels = entryAndParamNumChannels[1];
					var newEntry = Array.newClear(numOfSynths);
					if(numOfSynths > 0, {
						numOfSynths.do({ | i |
							if(entry.isArray, {
								var subEntry = entry[0..paramNumChannels-1]; //sub entry
								entry = entry[paramNumChannels..entry.size-1]; //shift
								if(subEntry.size == 1, { subEntry = subEntry[0] }); //extract single entries
								newEntry[i] = subEntry;
							}, {
								newEntry[i] = entry;
							});
						});
					}, {
						newEntry = entry;
					});
					entries[paramName][uniqueID] = newEntry;
				});
			});
		});

		//Add numOfSynths
		entries[\numOfSynths] = numOfSynths;

		//Return the correct entries
		^entries;
	}

	//Create all needed Synths for this Event. This is triggered by the \algaNote Event
	createEventSynths { | algaSynthDef, algaSynthBus,
		algaPatternInterpStreams, fx, algaOut, dur, sustain, stretch, legato |

		//Keep the def without _algaPattern
		var algaSynthDefClean = algaSynthDef;

		//Get controlNames and check if it's a ListPattern
		//(in case for multiple SynthDefs). Get the correct one if available.
		var controlNamesToUse = controlNames;
		if(controlNamesList != nil, {
			controlNamesToUse = controlNamesList[algaSynthDefClean];
			if(controlNamesToUse == nil, { controlNamesToUse = controlNames });
		});

		//algaSynthDef with _algaPattern
		algaSynthDef = (algaSynthDef.asString ++ "_algaPattern").asSymbol;

		//If streams being stopped (happens for AlgaStep + stopPatternBeforeReplace)
		//This must be checked here as the eventSynth is already being triggered
		if(algaPatternInterpStreams.beingStopped, { ^this });

		//Check MC mismatches and create patternSynths accordingly
		if(useMultiChannelExpansion, {
			//Each entry will be used to create a single patternSynth
			var entries = this.calculateMultiChannelMismatches(
				controlNamesToUse: controlNamesToUse,
				algaPatternInterpStreams: algaPatternInterpStreams,
				fx: fx,
				algaOut: algaOut
			);

			//Get numOfSynths
			var numOfSynths = entries[\numOfSynths] ? 0;

			//Remove it
			entries.removeAt(\numOfSynths);

			//Get fx
			if(entries[\fx] != nil, {
				fx = entries[\fx];
				entries.removeAt(\fx);
			});

			//Get out
			if(entries[\algaOut] != nil, {
				algaOut = entries[\algaOut];
				entries.removeAt(\algaOut);
			});

			//Create MC expansion. If it fails (numSynths == 0)
			//it will execute the next createPatternSynth, the one with no MC
			if(numOfSynths > 0, {
				numOfSynths.do({ | synthNum |
					this.createPatternSynth(
						algaSynthDef: algaSynthDef,
						algaSynthDefClean: algaSynthDefClean,
						algaSynthBus: algaSynthBus,
						algaPatternInterpStreams: algaPatternInterpStreams,
						controlNamesToUse: controlNamesToUse,
						fx: fx,
						algaOut: algaOut,
						mcSynthNum: synthNum,
						mcEntries: entries,
						dur: dur,
						sustain: sustain,
						stretch: stretch,
						legato: legato
					);
				});

				^this;
			}, {
				//numOfSynths == 0, mcSynthNum is then nil
				^this.createPatternSynth(
					algaSynthDef: algaSynthDef,
					algaSynthDefClean: algaSynthDefClean,
					algaSynthBus: algaSynthBus,
					algaPatternInterpStreams: algaPatternInterpStreams,
					controlNamesToUse: controlNamesToUse,
					fx: fx,
					algaOut: algaOut,
					mcSynthNum: nil,
					mcEntries: entries,
					dur: dur,
					sustain: sustain,
					stretch: stretch,
					legato: legato
				);
			});
		});

		//No MC expansion used: create the single patternSynth with all its params
		this.createPatternSynth(
			algaSynthDef: algaSynthDef,
			algaSynthDefClean: algaSynthDefClean,
			algaSynthBus: algaSynthBus,
			algaPatternInterpStreams: algaPatternInterpStreams,
			controlNamesToUse: controlNamesToUse,
			fx: fx,
			algaOut: algaOut,
			dur: dur,
			sustain: sustain,
			stretch: stretch,
			legato: legato
		)
	}

	//dispatchNode: first argument is an Event or SynthDef
	dispatchNode { | def, args, initGroups = false, replace = false, reset = false,
		keepChannelsMapping = false, outsMapping, keepScale = false, sched = 0 |

		//def: entry
		var defEntry = def[\def];
		if(defEntry == nil, {
			"AlgaPattern: no 'def' entry in the Event".error;
			^this;
		});

		//Store the Event
		eventPairs = def;

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

		//Symbol / Function (already parsed)
		case
		{ defEntry.isSymbol } {
			^this.dispatchSynthDef(
				def: defEntry,
				initGroups: initGroups,
				replace: replace,
				keepChannelsMapping:keepChannelsMapping,
				keepScale:keepScale,
				sched:sched
			)
		}

		//Patterns
		{ defEntry.isPattern } {
			^this.dispatchPattern(
				def: defEntry,
				initGroups: initGroups,
				replace: replace,
				keepChannelsMapping:keepChannelsMapping,
				keepScale:keepScale,
				sched:sched
			)
		};

		//No dispatchFunction, as the parsing has already happened and def contains only Symbols
		("AlgaPattern: '" ++ defEntry.asString ++ "' is an invalid 'def'").error;
	}

	//Overloaded function
	buildFromSynthDef { | initGroups = false, replace = false,
		keepChannelsMapping = false, keepScale = false, sched = 0 |

		var synthDescControlNames;

		//Retrieve controlNames from SynthDesc
		synthDescControlNames = synthDef.asSynthDesc.controls;

		//Create controlNames
		this.createControlNamesAndParamsConnectionTime(synthDescControlNames);

		//Retrieve channels and rate
		numChannels = synthDef.numChannels;
		rate = synthDef.rate;

		//Generate outsMapping (for outsMapping connectinons)
		this.calculateOutsMapping(replace, keepChannelsMapping);

		//Create groups if needed
		if(initGroups, { this.createAllGroups });

		//Create synthBus for output
		//interpBusses are taken care of in createPatternInterpSynthAndBus.
		this.createSynthBus;

		//Create the actual pattern.
		this.createPattern(
			replace: replace,
			keepChannelsMapping: keepChannelsMapping,
			keepScale: keepScale,
			sched: sched
		);
	}

	//Build spec Pattern
	buildFromPattern { | initGroups = false, replace = false,
		keepChannelsMapping = false, keepScale = false, sched = 0 |

		//Generate outsMapping (for outsMapping connectinons)
		if(this.calculateOutsMapping(replace, keepChannelsMapping) == nil, { ^this });

		//Create groups if needed
		if(initGroups, { this.createAllGroups });

		//Create synthBus for output
		//interpBusses are taken care of in createPatternInterpSynthAndBus.
		this.createSynthBus;

		//Create the actual pattern.
		this.createPattern(
			replace: replace,
			keepChannelsMapping: keepChannelsMapping,
			keepScale: keepScale,
			sched: sched
		);
	}

	//Check rates, numChannels, Symbols and controlNames
	checkPatternValidityAndReturnControlNames { | object, controlNamesDict |
		var numChannelsCount, rateCount;
		var controlNamesSum = Array.newClear;
		controlNamesDict = controlNamesDict ? IdentityDictionary();

		//Found a Symbol
		if(object.isSymbol, {
			var synthDescEntry, synthDefEntry;
			var controlNamesEntry;
			var controlNamesListPatternDefaultsEntry;

			synthDescEntry = SynthDescLib.global.at(object);

			if(synthDescEntry == nil, {
				("AlgaPattern: Invalid AlgaSynthDef: '" ++ object.asString ++ "'").error;
				^nil;
			});

			synthDefEntry = synthDescEntry.def;

			if(synthDefEntry.class != AlgaSynthDef, {
				("AlgaPattern: Invalid AlgaSynthDef: '" ++ object.asString ++"'").error;
				^nil;
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
			numChannelsList[object] = numChannelsCount;
			rateList[object] = rateCount;

			//Retrieve controlNames
			controlNamesEntry = synthDescEntry.controls;

			//Create entry for controlNamesList
			controlNamesList[object] = IdentityDictionary();

			//Check for duplicates and add correct controlName to controlNamesList[entry.asSymbol]
			controlNamesEntry.do({ | controlName |
				var name = controlName.name;
				if(this.checkValidControlName(name), {
					//Just check for duplicate names: we only need one entry per param name
					//for controlNamesSum.
					if(controlNamesDict[name] == nil, {
						controlNamesDict[name] = controlName;
						controlNamesSum = controlNamesSum.add(controlName);
					});

					//Add to IdentityDict for specific def / name combination
					controlNamesList[object][name] = controlName;
				});
			});
		}, {
			//Pattern
			if(object != nil, {
				recursiveObjectList.add(object);
				object.algaParseObject(
					func: { | val |
						if((val != nil).and(recursiveObjectList.includes(val).not), {
							controlNamesSum = controlNamesSum.addAll(
								this.checkPatternValidityAndReturnControlNames(val, controlNamesDict)
							);
						});
					},
					replace: false
				);
			});
		});

		//Sanity checks
		if(controlNamesSum.size == 0, { ^nil });
		controlNamesSum.do({ | entry | if(entry == nil, { ^nil }) });

		^controlNamesSum;
	}

	//Multiple Symbols over a Pattern
	dispatchPattern { | def, initGroups = false, replace = false,
		keepChannelsMapping = false, keepScale = false, sched = 0 |
		var controlNamesSum;
		var functionsAndListPattern;
		var functions;

		//Create numChannelsList, rateList and controlNamesList (they're nil otherwise)
		numChannelsList  = IdentityDictionary();
		rateList         = IdentityDictionary();
		controlNamesList = IdentityDictionary();

		//Check rates, numChannels, Symbols and controlNames
		recursiveObjectList.clear;
		controlNamesSum = this.checkPatternValidityAndReturnControlNames(def);
		if(controlNamesSum != nil, {
			//Create controlNames from controlNamesSum
			this.createControlNamesAndParamsConnectionTime(controlNamesSum);

			//synthDef will be the ListPattern
			synthDef = def;

			//Substitute the eventPairs entry with the new ListPattern
			eventPairs[\def] = def;

			//Build pattern from Pattern
			^this.buildFromPattern(
				initGroups: initGroups,
				replace: replace,
				keepChannelsMapping: keepChannelsMapping,
				keepScale: keepScale,
				sched: sched
			);
		});

		//Error
		("AlgaPattern: could not retrieve any parameters from the provided 'def'").error;
		^this;
	}

	//Create out: receivers
	createPatternOutReceivers {
		var time = currentPatternOutTime ? 0;
		var shape = currentPatternOutShape ? Env([0, 1], 1);

		//Fade out (also old synths)
		if(prevPatternOutNodes != nil, {
			prevPatternOutNodes.do({ | outNodeAndParam |
				var outNode = outNodeAndParam[0];
				var param = outNodeAndParam[1];
				this.addAction(
					//condition: { outNode.algaInstantiatedAsReceiver(param) },
					func: {
						outNode.removePatternOutsAtParam(
							algaPattern: this,
							param: param,
							removePatternOutNodeFromDict: true,
							time: time,
							shape: shape
						)
					}
				);
			});
		});

		//Fade in (also new synths)
		if(currentPatternOutNodes != nil, {
			currentPatternOutNodes.do({ | outNodeAndParam |
				var outNode = outNodeAndParam[0];
				var param = outNodeAndParam[1];
				this.addAction(
					condition: { outNode.algaInstantiatedAsReceiver(param) },
					func: {
						outNode.receivePatternOutsAtParam(
							algaPattern: this,
							param: param,
							time: time,
							shape: shape
						)
					}
				)
			});
		});

		//Reset currentPatternOutTime
		currentPatternOutTime = 0;

		//Reset currentPatternOutShape
		currentPatternOutShape = Env([0, 1], 1);
	}

	//Reset specific algaParams (\out, \fx, etc...) and genericParams
	parseResetOnReplaceParams {
		case
		{ currentReset.isArray } {
			^currentReset.as(IdentitySet);
		}
		{ currentReset == true } {
			^true;
		};

		^nil
	}

	//Store generic params for replaces
	addScalarAndGenericParams { | key, value, uniqueID |
		var latestScalarUniqueID;
		latestScalarUniqueIDs = latestScalarUniqueIDs ? IdentityDictionary();
		latestScalarUniqueID  = latestScalarUniqueIDs[key];

		scalarAndGenericParams = scalarAndGenericParams ? IdentityDictionary();
		scalarAndGenericParams[key] = scalarAndGenericParams[key] ? IdentityDictionary();
		scalarAndGenericParams[key][uniqueID ? latestScalarUniqueID] = value;

		scalarAndGenericParamsStreams = scalarAndGenericParamsStreams ? IdentityDictionary();
		scalarAndGenericParamsStreams[key] = scalarAndGenericParamsStreams[key] ? IdentityDictionary();
		scalarAndGenericParamsStreams[key][uniqueID ? latestScalarUniqueID] = value.algaAsStream;

		//Update latest one
		if(uniqueID != nil, { latestScalarUniqueIDs[key] = uniqueID });
	}

	//Build the actual pattern
	createPattern { | replace = false, keepChannelsMapping = false, keepScale = false, sched |
		var foundDurOrDelta = false;
		var foundSustain = false, resetSustain = false;
		var foundStretch = false, resetStretch = false;
		var foundLegato = false, resetLegato = false;
		var manualDur = false;
		var foundFX = false;
		var parsedFX;
		var parsedOut;
		var foundOut = false;
		var foundGenericParams = IdentitySet();
		var patternPairs = Array.newClear;

		//Create new interpStreams. NOTE that the Pfunc in dur uses this, as interpStreams
		//will be overwritten when using replace. This allows to separate the "global" one
		//from the one that's being created here.
		var newInterpStreams = AlgaPatternInterpStreams(this);

		//Check sched
		if(replace.not, { sched = sched ? schedInner });

		//Loop over controlNames and retrieve which parameters the user has set explicitly.
		//All other parameters will be dealt with later.
		controlNames.do({ | controlName |
			var paramName = controlName.name;
			var paramValue = eventPairs[paramName]; //Retrieve it directly from eventPairs
			var defaultValue = controlName.defaultValue;
			var rate = controlName.rate;
			var explicitParam = paramValue != nil;
			var chansMapping, scale;

			//if not set explicitly yet
			if(explicitParam.not, {
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
			if((rate == \control).or(rate == \audio), {
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

			//Add the entry to defaults ONLY if explicit
			if(explicitParam, { defArgs[paramName] = paramValue });
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
				if((value.isSymbol).or(value.isNil), {
					//Using a symbol (like, \manual) as \dur.
					//Nil doesn't work as it won't even add to the Event
					manualDur = true;
				}, {
					foundDurOrDelta = true;
					this.setDur(value, newInterpStreams);
				});
				isAlgaParam = true;
			});

			//Found \sustain
			if(paramName == \sustain, {
				foundSustain = true;
				isAlgaParam = true;
				this.setSustain(value, newInterpStreams);
			});

			//Found \stretch
			if(paramName == \stretch, {
				foundStretch = true;
				isAlgaParam = true;
				this.setStretch(value, newInterpStreams);
			});

			//Found \legato
			if(paramName == \legato, {
				foundLegato = true;
				isAlgaParam = true;
				this.setLegato(value, newInterpStreams);
			});

			//Add \fx key (parsing everything correctly)
			if(paramName == \fx, {
				parsedFX = value;
				if(parsedFX != nil, {
					patternPairs = patternPairs.add(\fx).add(parsedFX);
					foundFX = true;
				});
				isAlgaParam = true;
			});

			//Add \out key
			if(paramName == \out, {
				parsedOut = value;
				if(parsedOut != nil, {
					patternPairs = patternPairs.add(\algaOut).add(parsedOut); //can't use \out
					foundOut = true;
				});
				isAlgaParam = true;
			});

			//Scalar parameters OR all other values
			if(isAlgaParam.not, {
				var uniqueID = UniqueID.next;
				this.addScalarAndGenericParams(paramName, value, uniqueID);
				patternPairs = patternPairs.add(paramName).add(
					Pfunc {
						scalarAndGenericParamsStreams[paramName][uniqueID].next;
					}
				);
				foundGenericParams.add(paramName);
			});
		});

		//Store current FX for replaces
		if(foundFX, { currentFX = parsedFX });

		//Store current out for replaces
		if(foundOut, { currentOut = parsedOut });

		//If replace, find keys in the old pattern if they are not redefined
		if(replace, {
			//Check reset for alga params
			var resetSet = this.parseResetOnReplaceParams;
			if(resetSet != nil, {
				case
				{ resetSet.class == IdentitySet } {
					//reset \fx
					if((foundFX.not).and(resetSet.findMatch(\fx) != nil), {
						parsedFX = nil; currentFX = nil
					});

					//reset \out
					if((foundOut.not).and(resetSet.findMatch(\out) != nil), {
						parsedOut = nil; currentOut = nil; currentPatternOutNodes = nil;
						prevPatternOutNodes = nil;
					});

					//reset \dur
					if((foundDurOrDelta.not).and(resetSet.findMatch(\dur) != nil), {
						interpStreams = nil;
					});

					//reset \sustain
					if((foundSustain.not).and(resetSet.findMatch(\sustain) != nil), {
						resetSustain = true;
					});

					//reset \stretch
					if((foundStretch.not).and(resetSet.findMatch(\stretch) != nil), {
						resetStretch = true;
					});

					//reset \legato
					if((foundLegato.not).and(resetSet.findMatch(\legato) != nil), {
						resetLegato = true;
					});

					//reset generic params
					resetSet.do({ | entry |
						scalarAndGenericParams.removeAt(entry);
						scalarAndGenericParamsStreams.removeAt(entry)
					});
				}
				{ resetSet == true } {
					parsedFX = nil; currentFX = nil; //reset \fx
					parsedOut = nil; currentOut = nil; currentPatternOutNodes = nil; prevPatternOutNodes = nil; //reset \out
					interpStreams = nil; //reset \dur
					resetSustain = true; //reset \sustain
					resetStretch = true; //reset \stretch
					resetLegato = true;  //reset \legato

					//reset all generic params
					if(scalarAndGenericParams != nil, {
						scalarAndGenericParams.clear;
						scalarAndGenericParamsStreams.clear
					});
				};
			});

			//No \dur from user, set to previous one
			if(foundDurOrDelta.not, {
				if((interpStreams == nil).or(algaWasBeingCleared), {
					this.setDur(1, newInterpStreams)
				}, {
					this.setDur(interpStreams.dur, newInterpStreams)
				});
			});

			//No \sustain from user, set to previous one
			if(foundSustain.not, {
				if(resetSustain, {
					this.setSustain(0, newInterpStreams)
				}, {
					this.setSustain(interpStreams.sustain, newInterpStreams)
				});
			});

			//No \stretch from user, set to previous one
			if(foundStretch.not, {
				if(resetStretch, {
					this.setStretch(1, newInterpStreams)
				}, {
					this.setStretch(interpStreams.stretch, newInterpStreams)
				});
			});

			//No \stretch from user, set to previous one
			if(foundLegato.not, {
				if(resetLegato, {
					this.setLegato(0, newInterpStreams)
				}, {
					this.setLegato(interpStreams.legato, newInterpStreams)
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

			//Add old scalarAndGenericParamsStreams
			if(scalarAndGenericParams != nil, {
				if(scalarAndGenericParams.size > 0, {
					scalarAndGenericParams.keysValuesDo({ | key, value |
						//If that specific generic param hasn't been set from user,
						//use the one from addScalarAndGenericParams if available
						if(foundGenericParams.findMatch(key) == nil, {
							//Needs to be copied in order to make a "fresh" one
							var latestValue = scalarAndGenericParams[key][
								latestScalarUniqueIDs[key]
							].copy;

							//This code should be abstracted: it's the same behaviour
							if(latestValue != nil, {
								var uniqueID = UniqueID.next;
								this.addScalarAndGenericParams(key, latestValue, uniqueID);
								patternPairs = patternPairs.add(key).add(
									Pfunc {
										scalarAndGenericParamsStreams[key][uniqueID].next;
									}
								);
							});
						})
					})
				})
			});
		}, {
			//Else, default them
			if(foundDurOrDelta.not, { this.setDur(1, newInterpStreams) });
			if(foundSustain.not, { this.setSustain(0, newInterpStreams) });
			if(foundStretch.not, { this.setStretch(1, newInterpStreams) });
			if(foundLegato.not, { this.setLegato(0, newInterpStreams) });
		});

		//Set the correct synthBus in newInterpStreams!!!
		//This is fundamental for the freeing mechanism in stopPatternAndFreeSynths to work correctly
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

		//Add \sustain
		patternPairs = patternPairs.addAll([
			\sustain, Pfunc( { newInterpStreams.sustain.next } ),
			\stretch, Pfunc( { newInterpStreams.stretch.next } ),
			\legato, Pfunc( { newInterpStreams.legato.next } )
		]);

		//If player is AlgaPatternPlayer, dur is ALWAYS manual
		if(player.isAlgaPatternPlayer, { manualDur = true });

		//Manual or automatic dur management
		if(manualDur.not, {
			//Pfunc allows to modify the value
			patternPairs = patternPairs.add(\dur).add(
				Pfunc( {
					//Only advance when there are no concurrent executions.
					//This allows for .replace to work correctly and not advance twice!
					var currentTime = this.clock.seconds;
					var dur = if(currentTime != latestPatternTime, {
						newInterpStreams.dur.next
					}, {
						//If stream changed, run .next anyway
						if(newInterpStreams.dur != latestDurStream, {
							newInterpStreams.dur.next
						}, {
							//Same time and no stream change: return the
							//dur that was just triggered at this very time
							latestDur
						})
					});

					//Store values
					latestDur = dur;
					latestDurStream = newInterpStreams.dur;
					latestPatternTime = currentTime;

					//Return correct dur
					dur;
				} )
			);
		});

		//Create the Pattern
		pattern = Pbind(*patternPairs);

		//Determine if \out interpolation is required
		this.createPatternOutReceivers;

		//Schedule the start of the pattern on the AlgaScheduler. All the rest in this
		//createPattern function is non scheduled as it it better to create it right away.
		if(manualDur.not, {
			this.addAction(
				func: {
					newInterpStreams.playAlgaReschedulingEventStreamPlayer(
						pattern: pattern,
						clock: this.clock
					);
				},
				sched: sched
			);
		});

		//Update latest interpStreams
		interpStreams = newInterpStreams;

		//Needed for manual .step + .replace
		patternsAsStreams = (patternsAsStreams ? IdentityDictionary());
		patternsAsStreams[interpStreams] = pattern.algaAsStream;
	}

	//Parse a single \fx Event entry
	parseFXEvent { | value, functionSynthDefDict |
		var foundInParam = false;
		var synthDescFx, synthDefFx, controlNamesFx;
		var isFunction = false;
		var def;

		//Find \def
		def = value[\def];
		if(def == nil, {
			("AlgaPattern: no 'def' entry in 'fx': '" ++ value.asString ++ "'").error;
			^nil
		});

		//If it's a Function, send def to server and replace entries.
		if(def.isFunction, {
			var defName = ("alga_" ++ UniqueID.next).asSymbol;

			//The synthDef will be sent later! just use the def and the desc for now
			synthDefFx = AlgaSynthDef.new_inner(
				defName,
				def,
				makeFadeEnv: false,
				makePatternDef: false,
				makeOutDef: false
			);

			//Get the desc
			synthDescFx = synthDefFx.asSynthDesc;

			//Important: NO sampleAccurate (it's only needed for the triggered pattern synths)
			functionSynthDefDict[defName] = synthDefFx;

			//Replace the def: with the symbol
			def = defName;
			value[\def] = defName;

			isFunction = true;
		});

		//Don't support other classes for now
		if(def.isSymbol.not, {
			("AlgaPattern: 'fx' only supports Symbols and Functions as 'def'").error;
			^nil
		});

		//Check that \def is valid
		if(isFunction.not, {
			synthDescFx = SynthDescLib.global.at(def);
		});

		if(synthDescFx == nil, {
			("AlgaPattern: Invalid AlgaSynthDef in 'fx': '" ++ def.asString ++ "'").error;
			^nil;
		});

		if(isFunction.not, {
			synthDefFx = synthDescFx.def;
		});

		if(synthDefFx.class != AlgaSynthDef, {
			("AlgaPattern: Invalid AlgaSynthDef in 'fx': '" ++ def.asString ++"'").error;
			^nil;
		});

		if(synthDefFx.sampleAccurate, {
			("AlgaPattern: AlgaSynthDef in 'fx': '" ++ def.asString ++"' has 'sampleAccurate' on. This causes synchronization errors").warn;
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

		//Not found the \in parameter
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

		//Reset paramContainsAlgaReaderPfunc, latestPlayers and recursiveObjectList
		this.resetPatternParsingVars;

		//Loop over the event and parse ListPatterns / AlgaTemps. Also use as Stream for the final entry.
		value.keysValuesDo({ | key, entry |
			var parsedEntry = this.parseParam_inner(entry, functionSynthDefDict);
			value[key] = parsedEntry.algaAsStream;
		});

		//Check support for AlgaPatternPlayers on \fx
		this.calculatePlayersConnections(\fx);

		//Reset paramContainsAlgaReaderPfunc, latestPlayers and recursiveObjectList again
		this.resetPatternParsingVars;

		^value
	}

	//Parse the \fx key
	parseFX { | value, functionSynthDefDict |
		case

		//Single Event
		{ value.isEvent } {
			^this.parseFXEvent(value, functionSynthDefDict);
		}

		//If individual Symbol, if it's in SynthDescLib, use it as Event.
		//Otherwise, passthrough (like, \none, \dry)
		{ value.isSymbol } {
			if(SynthDescLib.global.at(value) != nil, {
				^this.parseFXEvent((def: value), functionSynthDefDict)
			});
			^value
		}

		//If individual Function, wrap in Event
		{ (value.isFunction).and(value.isLiteralFunction.not) } {
			^this.parseFXEvent((def: value), functionSynthDefDict)
		};

		//Patterns
		if(value != nil, {
			recursiveObjectList.add(value);
			value.algaParseObject(
				func: { | val |
					if((val != nil).and(recursiveObjectList.includes(val).not), {
						this.parseFX(val, functionSynthDefDict)
					});
				},
				replace: false
			)
		});

		^value
	}

	//Parse a single \out AlgaOut entry
	parseOutAlgaOut { | value, alreadyParsed |
		var node = value.nodeOrig;
		var param = value.paramOrig;

		if(param == nil, param = \in);
		if(param.class != Symbol, {
			"AlgaPattern: the 'param' argument in AlgaOut can only be a Symbol. Using 'in'".error;
			param = \in
		});

		//AlgaNode
		if(node.isAlgaNode, {
			if(alreadyParsed[node] == nil, {
				if(node.isAlgaPattern, {
					"AlgaPattern: the 'out' parameter only supports AlgaNodes, not AlgaPatterns".error;
					^nil
				});
				alreadyParsed[node] = true;
				currentPatternOutNodes.add([node, param]);
			});
		}, {
			//Patterns
			if(value != nil, {
				recursiveObjectList.add(value);
				value.algaParseObject(
					func: { | val |
						if((val != nil).and(recursiveObjectList.includes(val).not), {
							this.parseOut(val, alreadyParsed)
						});
					},
					replace: false
				)
			});
		});

		^value.algaAsStream;
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
				currentPatternOutNodes.add([value, \in]);
			});
			^value
		}
		{ value.isAlgaOut } {
			^this.parseOutAlgaOut(value, alreadyParsed);
		};

		//Patterns
		if(value != nil, {
			recursiveObjectList.add(value);
			value.algaParseObject(
				func: { | val |
					if((val != nil).and(recursiveObjectList.includes(val).not), {
						this.parseOut(val, alreadyParsed);
					});
				},
				replace: false
			);
		});

		^value.algaAsStream;
	}

	//Found an AlgaReaderPfunc
	assignAlgaReaderPfunc { | algaReaderPfunc |
		var latestPlayer = algaReaderPfunc.patternPlayer;
		var params = algaReaderPfunc.params;
		if(latestPlayer != nil, {
			paramContainsAlgaReaderPfunc = true;
			latestPlayersAtParam[latestPlayer] = latestPlayersAtParam[latestPlayer] ? Array.newClear;
			latestPlayersAtParam[latestPlayer] = latestPlayersAtParam[latestPlayer].add(params).flatten;
		});
		^algaReaderPfunc;
	}

	//Parse an object
	parseGenericObjectParam { | object, functionSynthDefDict, validClasses |
		if(object != nil, {
			recursiveObjectList.add(object);
			object.algaParseObject(
				func: { | val |
					if((val != nil).and(recursiveObjectList.includes(val).not), {
						this.parseParam_inner(val, functionSynthDefDict);
					});
				},
				replace: true,
				validClasses: validClasses
			);
		});
		^object;
	}

	//Reset vars used in parsing
	resetPatternParsingVars {
		paramContainsAlgaReaderPfunc = false;
		latestPlayersAtParam = IdentityDictionary();
		recursiveObjectList = IdentitySet(10);
	}

	//Parse a Function \def entry
	parseFunctionDefEntry { | def, functionSynthDefDict |
		//New defName
		var defName = ("alga_" ++ UniqueID.next).asSymbol;

		//sampleAccurate is set according to sampleAccurateFuncs
		functionSynthDefDict[defName] = AlgaSynthDef(
			defName,
			def,
			outsMapping: outsMapping,
			sampleAccurate: sampleAccurateFuncs
		); //This returns an AlgaSynthDefSpec (for _algaPattern and _algaPatternTempOut)

		//Return the Symbol
		^defName
	}

	//Parse the \def entry
	parseDefEntry { | def, functionSynthDefDict |
		//Literal functions are non-UGen graph functins!
		if((def.isFunction).and(def.isLiteralFunction.not), {
			^this.parseFunctionDefEntry(def, functionSynthDefDict)
		}, {
			//Pattern
			if(def != nil, {
				recursiveObjectList.add(def);
				def.algaParseObject(
					func: { | val |
						if((val != nil).and(recursiveObjectList.includes(val).not), {
							this.parseDefEntry(val, functionSynthDefDict);
						});
					},
					replace: true
				);
			});
		});
		^def
	}

	//Parse an entire def
	parseDef { | def |
		var defDef;
		var defFX;
		var defOut;

		//Store [defName] -> AlgaSynthDef
		var functionSynthDefDict = IdentityDictionary();

		//Wrap in an Event if not an Event already
		if(def.isEvent.not, { def = (def: def) });

		//Store the original def (needed for AlgaPatternPlayer)
		defPreParsing = def.deepCopy;

		//Retrieve entries that need specific parsing
		defDef = def[\def];
		defFX  = def[\fx];
		defOut = def[\out];

		//Return nil if no def
		if(defDef == nil, {
			"AlgaPattern: the Event does not provide a 'def' entry".error;
			^nil
		});

		//Parse and replace \def
		recursiveObjectList = IdentitySet(10); //reset for parseDefEntry
		def[\def] = this.parseDefEntry(defDef, functionSynthDefDict);

		//Parse \fx
		recursiveObjectList.clear; //reset for parseFX
		if(defFX != nil, { def[\fx] = this.parseFX(defFX, functionSynthDefDict) });

		//Parse \out (reset currentPatternOutNodes too)
		if(defOut != nil, {
			prevPatternOutNodes = currentPatternOutNodes.copy;
			currentPatternOutNodes = IdentitySet();
			recursiveObjectList.clear; //reset for parseOut
			def[\out] = this.parseOut(defOut)
		});

		//Parse all the other entries looking for AlgaTemps / ListPatterns
		def.keysValuesDo({ | key, value |
			if((key != \fx).and(key != \out), {
				value = this.parseParam(value, functionSynthDefDict);
				this.calculatePlayersConnections(key); //Also removes old ones if needed
				def[key] = value;
			});
		});

		^[def, functionSynthDefDict];
	}

	//Get valid synthDef name
	getSynthDef {
		if(synthDef.class == AlgaSynthDef, {
			//Normal synthDef
			^synthDef.name
		}, {
			//ListPatterns
			^synthDef
		});
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
			sched = sched ? schedInner;
			("AlgaPattern: 'dur' interpolation is not supported yet. Rescheduling 'dur' at the " ++ sched ++ " quantization.").warn;
			^this.setDurAtSched(value, sched);
		});
	}

	//Interpolate sustain (uses replaceDur)
	interpolateSustain { | value, time, sched |
		if(replaceDur, {
			("AlgaPattern: 'sustain' interpolation is not supported yet. Running 'replace' instead.").warn;
			^this.replace(
				def: (def: this.getSynthDef, sustain: value),
				time: time,
				sched: sched
			);
		}, {
			sched = sched ? schedInner;
			("AlgaPattern: 'sustain' interpolation is not supported yet. Rescheduling 'sustain' at the " ++ sched ++ " quantization.").warn;
			^this.setSustainAtSched(value, sched);
		});
	}

	//Interpolate stretch (uses replaceDur)
	interpolateStretch { | value, time, sched |
		if(replaceDur, {
			("AlgaPattern: 'stretch' interpolation is not supported yet. Running 'replace' instead.").warn;
			^this.replace(
				def: (def: this.getSynthDef, stretch: value),
				time: time,
				sched: sched
			);
		}, {
			sched = sched ? schedInner;
			("AlgaPattern: 'stretch' interpolation is not supported yet. Rescheduling 'stretch' at the " ++ sched ++ " quantization.").warn;
			^this.setStretchAtSched(value, sched);
		});
	}

	//Interpolate legato (uses replaceDur)
	interpolateLegato { | value, time, sched |
		if(replaceDur, {
			("AlgaPattern: 'legato' interpolation is not supported yet. Running 'replace' instead.").warn;
			^this.replace(
				def: (def: this.getSynthDef, legato: value),
				time: time,
				sched: sched
			);
		}, {
			sched = sched ? schedInner;
			("AlgaPattern: 'legato' interpolation is not supported yet. Rescheduling 'legato' at the " ++ sched ++ " quantization.").warn;
			^this.setLegatoAtSched(value, sched);
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
	interpolateOut { | value, time, shape, sched |
		"AlgaPattern: changing the 'out' key. This will trigger 'replace'.".warn;
		currentPatternOutShape = shape; //set shape!
		^this.replace(
			def: (def: this.getSynthDef, out: value),
			time: time,
			sched: sched
		);
	}

	//Used for AlgaStep + modifying a scalar value with no replace
	addScalarInAlgaStep { | param |
		scalarsInAlgaStep = scalarsInAlgaStep ? IdentitySet();
		scalarsInAlgaStep.add(param);
	}

	//Interpolate a parameter that is not in controlNames (like \lag)
	interpolateGenericParam { | sender, param, time, sched |
		var paramConnectionTime = paramsConnectionTime[param];
		var latestScalarUniqueID = latestScalarUniqueIDs[param];
		if(paramConnectionTime == nil, { paramConnectionTime = connectionTime });
		if(paramConnectionTime < 0, { paramConnectionTime = connectionTime });
		time = time ? paramConnectionTime;
		if((time == 0).and(latestScalarUniqueID != nil), {
			this.addAction(
				func: {
					this.addScalarAndGenericParams(param, sender);
					if(sched.isAlgaStep, { this.addScalarInAlgaStep(param) });
				},
				sched: sched,
				topPriority: true
			)
		}, {
			("AlgaPattern: changing the '" ++ param.asString ++ "' key, which is either scalar or not present in the AlgaSynthDef. This will trigger 'replace'.").warn;
			this.replace(
				def: (def: this.getSynthDef, (param): sender), //escape param with ()
				time: time,
				sched: sched
			);
		});
	}

	//<<, <<+ and <|
	makeConnectionInner { | sender, param = \in, senderChansMapping, scale,
		sampleAndHold = false, time = 0, shape |

		var isDefault = false;
		var paramConnectionTime = paramsConnectionTime[param];
		var controlName;
		if(paramConnectionTime == nil, { paramConnectionTime = connectionTime });
		if(paramConnectionTime < 0, { paramConnectionTime = connectionTime });
		time = time ? paramConnectionTime;

		//This mostly happens on .resetParam. Try to get the default value from defArgs,
		//restoring the original Pattern's parameter. If it will be nil, the SynthDef's
		//default will eventually be used in the pattern loop.
		if(sender == nil, {
			isDefault = true;
			sender = defArgs[param];
		});

		//Check valid class
		if(isDefault.not, {
			if((sender.isAlgaNode.not).and(sender.isPattern.not).and(
				sender.isAlgaArg.not).and(
				sender.isAlgaTemp.not).and(
				sender.isNumberOrArray.not).and(sender.isBuffer.not), {
				"AlgaPattern: makeConnection only works with AlgaNodes, AlgaPatterns, AlgaPatternArgs, AlgaTemps, Patterns, Numbers, Arrays and Buffers".error;
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
			time: time,
			shape: shape
		);
	}

	//<<, <<+ and <|
	makeConnection { | sender, param = \in, replace = false, mix = false,
		replaceMix = false, senderChansMapping, scale, sampleAndHold,
		time, shape, forceReplace = false, sched |

		//Check sched
		if(replace.not, { sched = sched ? schedInner });

		//Default to false
		sampleAndHold = sampleAndHold ? false;

		//Check if it's boolean
		if(sampleAndHold.isKindOf(Boolean).not, {
			"AlgaPattern: sampleAndHold must be a boolean value".error;
			^this
		});

		//Check parameter in controlNames
		if(this.checkParamExists(param).not, {
			("AlgaPattern: '" ++ param ++ "' is not a valid parameter, it is not defined in the AlgaSynthDef.").error;
			^this
		});

		//AlgaPatternPlayer support
		this.calculatePlayersConnections(param);

		//Force a replace
		if(forceReplace, {
			^this.replace(
				def: (def: this.getSynthDef, (param): sender), //escape param with ()
				time: time,
				sched: sched
			);
		});

		//Store latest sender. This is used to only execute the latest .from call.
		//This allows for a smoother live coding experience: instead of triggering
		//every .from that was executed (perhaps the user found a mistake), only
		//the latest one will be considered when sched comes.
		this.addLatestSenderAtParam(sender, param);

		//All other cases
		if(this.algaCleared.not.and(sender.algaCleared.not).and(sender.algaToBeCleared.not), {
			this.addAction(
				condition: {
					(this.algaInstantiatedAsReceiver(param, sender, false)).and(
						sender.algaInstantiatedAsSender)
				},
				func: {
					if(sender == this.getLatestSenderAtParam(sender, param), {
						this.makeConnectionInner(
							sender: sender,
							param: param,
							senderChansMapping: senderChansMapping,
							scale: scale,
							sampleAndHold: sampleAndHold,
							time: time,
							shape: shape
						)
					})
				},
				sched: sched,
				topPriority: true //This is essential for scheduled times to work correctly!
			)
		});
	}

	//Used in AlgaProxySpace
	connectionTriggersReplace { | param = \in |
		if((((param == \delta).or(param == \dur)).and(replaceDur)).or(
			param == \def).or(param == \fx).or(param == \out).or(
			this.isScalarParam(param)), {
			if(controlNames[param] == nil, { ^true });
		});
		^false
	}

	//Check if a param is scalar
	isScalarParam { | paramName |
		var controlName = controlNames[paramName];
		if(controlName != nil, {
			^(controlName.rate == \scalar)
		});
		^false
	}

	//from implementation
	fromInner { | sender, param = \in, chans, scale, sampleAndHold,
		time, shape, forceReplace = false, sched |
		//delta == dur
		if(param == \delta, { param = \dur });

		//Special case, \dur
		if(param == \dur, {
			^this.interpolateDur(sender, time, sched);
		});

		//Special case, \sustain
		if(param == \sustain, {
			^this.interpolateSustain(sender, time, sched);
		});

		//Special case, \stretch
		if(param == \stretch, {
			^this.interpolateStretch(sender, time, sched);
		});

		//Special case, \legato
		if(param == \legato, {
			^this.interpolateLegato(sender, time, sched);
		});

		//Special case, \def
		if(param == \def, {
			^this.interpolateDef(sender, time, sched);
		});

		//Special case, \fx
		if(param == \fx, {
			^this.interpolateFX(sender, time, sched);
		});

		//Special case, \out (the only one using shape)
		if(param == \out, {
			^this.interpolateOut(sender, time, shape, sched);
		});

		//Scalar param OR
		//aram is not in controlNames. Probably setting another kind of parameter (like \lag)
		if((this.isScalarParam(param)).or(controlNames[param] == nil), {
			^this.interpolateGenericParam(sender, param, time, sched);
		});

		//Force Pattern / AlgaArg / AlgaTemp dispatch
		if((sender.isPattern).or(sender.isAlgaArg).or(sender.isAlgaTemp), {
			^this.makeConnection(
				sender: sender, param: param, senderChansMapping: chans,
				scale: scale, sampleAndHold: sampleAndHold, time: time,
				shape: shape, forceReplace: forceReplace, sched: sched
			);
		});

		//Standard cases
		if(sender.isAlgaNode, {
			if(this.server != sender.server, {
				("AlgaPattern: trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
			this.makeConnection(
				sender: sender, param: param, senderChansMapping: chans,
				scale: scale, time: time, shape:shape,
				sampleAndHold: sampleAndHold, forceReplace: forceReplace, sched: sched
			);
		}, {
			//sender == symbol is used for \def
			if(sender.isNumberOrArray, {
				this.makeConnection(
					sender: sender, param: param, senderChansMapping: chans,
					scale: scale, time: time, shape:shape,
					sampleAndHold: sampleAndHold, forceReplace: forceReplace, sched: sched
				);
			}, {
				("AlgaPattern: trying to enstablish a connection from an invalid class: " ++ sender.class).error;
			});
		});
	}

	//Remove an AlgaPatternPlayer connection
	removeAlgaPatternPlayerConnectionIfNeeded { | param |
		if(players != nil, {
			var playersAtParam = players[param];
			if(playersAtParam != nil, {
				playersAtParam.keysValuesDo({ | latestPlayer, latestPlayerParams |
					latestPlayer.removeAlgaPatternEntry(this, param)
				});
				players.removeAt(param);
			});
		});
	}

	//Add an AlgaPatternPlayer connection
	addAlgaPatternPlayerConnectionIfNeeded { | param |
		//Add an AlgaPatternPlayer connection
		if((paramContainsAlgaReaderPfunc).and(latestPlayersAtParam.size > 0), {
			latestPlayersAtParam.keysValuesDo({ | latestPlayer, params |
				latestPlayer.addAlgaPatternEntry(this, param);
			});
			players = players ? IdentityDictionary();
			players[param] = latestPlayersAtParam.copy;
		});
	}

	//Remove AlgaPatternPlayers' connections if there were any.
	//They will be re-assigned if parsing allows it.
	calculatePlayersConnections { | param |
		//Remove old connections at param if needed
		this.removeAlgaPatternPlayerConnectionIfNeeded(param);

		//Enstablish new ones if needed
		this.addAlgaPatternPlayerConnectionIfNeeded(param);
	}

	//Only from is needed: to already calls into makeConnection
	from { | sender, param = \in, chans, scale, sampleAndHold, time,
		shape, forceReplace = false, sched |
		//Must be copied before parsing!!
		var senderCopy = sender.deepCopy;

		//Parse the sender looking for AlgaTemps and ListPatterns
		var senderAndFunctionSynthDefDict = this.parseParam(sender);
		var functionSynthDefDict;

		//Unpack
		sender = senderAndFunctionSynthDefDict[0];
		functionSynthDefDict = senderAndFunctionSynthDefDict[1];

		//If sender is nil, don't do anything
		if(sender == nil, { ^this });

		//Replace entry in defPreParsing (used for AlgaPatternPlayer)
		if(defPreParsing.isEvent, { defPreParsing[param] = senderCopy});

		//If needed, it will compile the AlgaSynthDefs in functionSynthDefDict and wait before executing func.
		//Otherwise, it will just execute func
		^this.compileFunctionSynthDefDictIfNeeded(
			func: {
				this.fromInner(
					sender: sender,
					param: param,
					chans: chans,
					scale: scale,
					sampleAndHold: sampleAndHold,
					time: time,
					shape: shape,
					forceReplace: forceReplace,
					sched: sched
				)
			},
			functionSynthDefDict: functionSynthDefDict
		)
	}

	//Don't support <<+ for now
	mixFrom { | sender, param = \in, inChans, scale, time, shape, sched |
		"AlgaPattern: mixFrom is not supported yet".error;
	}

	// <<| \param (goes back to defaults)
	//When sender is nil in makeConnection, the default value will be used
	resetParam { | param = \in, sampleAndHold, time, shape, sched |
		this.makeConnection(
			sender: nil,
			param: param,
			sampleAndHold: sampleAndHold,
			time: time,
			sched: sched,
			shape: shape
		)
	}

	//Alias for resetParam
	reset { | param = \in, sampleAndHold, time, shape, sched |
		this.resetParam(param, sampleAndHold, time, shape, sched)
	}

	//Replace: run parsing of def before running (so the SynthDefs of Functions are sent right away)
	//Note that AlgaPattern's sched is 1 by default
	replace { | def, args, time, sched = 1, outsMapping, reset = false, keepOutsMappingIn = true,
		keepOutsMappingOut = true, keepScalesIn = true, keepScalesOut = true |

		//Parse the def
		var defAndFunctionSynthDefDict = this.parseDef(def);
		var functionSynthDefDict;

		//Unpack
		def = defAndFunctionSynthDefDict[0];
		functionSynthDefDict = defAndFunctionSynthDefDict[1];

		//If needed, it will compile the AlgaSynthDefs in functionSynthDefDict and wait before executing func.
		//Otherwise, it will just execute func
		this.compileFunctionSynthDefDictIfNeeded(
			func: {
				//The actual replacement
				super.replace(
					def: def,
					args: args,
					time: time,
					sched: sched,
					outsMapping: outsMapping,
					reset: reset,
					keepOutsMappingIn: keepOutsMappingIn,
					keepOutsMappingOut: keepOutsMappingOut,
					keepScalesIn: keepScalesIn,
					keepScalesOut: keepScalesOut
				);

				//Stop the pattern at the same sched as the replace.
				//This allows to catch Events that were supposed to be played at same sched and clear them.
				//The call must happen AFTER replace as both are scheduled at top priority.
				//stopPattern, then, must happen before replace, thus why it's here
				//(top priority will put it on top)
				if(stopPatternBeforeReplace, { this.stopPattern(sched) });
			},
			functionSynthDefDict: functionSynthDefDict
		);

		^this;
	}

	//IMPORTANT: this function must be empty. It's called from replaceInner, but synthBus is actually
	//still being used by the pattern. It should only be freed when the pattern is freed, as it's done
	//in the stopPatternAndFreeSynths function. LEAVE THIS FUNCTION EMPTY, OR FAST PATTERNS WILL BUG OUT!!!
	freeAllBusses { | now = false, time | }

	//Called from replaceInner. freeInterpNormSynths is not used for AlgaPatterns
	freeAllSynths { | useConnectionTime = true, now = true, time |
		this.stopPatternAndFreeInterpNormSynths(now, time);
	}

	//Used when replacing. Stop pattern (if fadeIn / fadeOut mechanism) and free all synths
	stopPatternAndFreeInterpNormSynths { | now = true, time |
		currentPatternOutTime = time; //store time for \out
		if(interpStreams != nil, {
			if(now, {
				if(stopPatternBeforeReplace.not, {
					interpStreams.algaReschedulingEventStreamPlayer.stop;
					patternsAsStreams.removeAt(interpStreams);
				});
				//freeAllSynthsAndBussesOnReplace must come after the stop!
				interpStreams.freeAllSynthsAndBussesOnReplace;
			}, {
				var interpStreamsOld = interpStreams;
				if(time == nil, { time = longestWaitTime });
				if(interpStreamsOld != nil, {
					fork {
						(time + 1.0).wait;
						if(stopPatternBeforeReplace.not, {
							interpStreamsOld.algaReschedulingEventStreamPlayer.stop;
							patternsAsStreams.removeAt(interpStreamsOld);
						});
						//freeAllSynthsAndBussesOnReplace must come after the stop!
						interpStreamsOld.freeAllSynthsAndBussesOnReplace;
					}
				});
			});
		});
	}

	//Manually advance the pattern. 'next' as function name won't work as it's reserved, apparently
	advance { | sched = 0 |
		//Check sched
		sched = sched ? schedInner;
		sched = sched ? 0;

		//Advance all streams. This happens on .replace
		if(patternsAsStreams != nil, {
			patternsAsStreams.do({ | patternAsStream |
				//If sched is 0, go right away: user might have its own scheduling setup
				if(sched == 0, {
					//Empty event as protoEvent!
					patternAsStream.next(()).play;
				}, {
					this.addAction(
						//Empty event as protoEvent!
						func: { patternAsStream.next(()).play },
						sched: sched
					);
				});
			});
		});
	}

	//Alias of advance
	step { | sched = 0 | this.advance(sched) }

	//Alias of advance. This allows pattern.()
	value { | sched = 0 | this.advance(sched) }

	//Stop pattern
	stopPattern { | sched = 0 |
		//Check sched
		sched = sched ? schedInner;
		sched = sched ? 0;
		if(sched.isAlgaStep, {
			if(interpStreams != nil, {
				var interpStreamsLock = interpStreams;
				this.addAction(
					func: {
						//This will be then checked against in createEventSynths!
						if(stopPatternBeforeReplace.and(sched.post.not), { interpStreamsLock.beingStopped = true });
						interpStreamsLock.algaReschedulingEventStreamPlayer.stop;
						patternsAsStreams.removeAt(interpStreamsLock);
					},
					sched: sched
				)
			});
		}, {
			if(interpStreams != nil, {
				if(interpStreams.algaReschedulingEventStreamPlayer != nil, {
					interpStreams.algaReschedulingEventStreamPlayer.stopAtTopPriority(sched);
				});
				patternsAsStreams.removeAt(interpStreams);
			});
		});
	}

	//Resume pattern
	resumePattern { | sched |
		//Check sched
		sched = sched ? schedInner;
		sched = sched ? 0;
		if(sched == 0, {
			interpStreams.algaReschedulingEventStreamPlayer.play
		}, {
			this.addAction(
				func: { interpStreams.algaReschedulingEventStreamPlayer.play },
				sched: sched
			)
		});
	}

	//Remove an AlgaPatternPlayer
	removePlayer { | sched |
		if(player != nil, {
			player.removeAlgaPattern(this, sched)
		});
	}

	//Set dur at sched
	setDurAtSched { | value, sched |
		//Check sched
		sched = sched ? schedInner;

		//Check sched type
		if(sched.isAlgaStep, {
			//sched == AlgaStep: at sched, schedule the set of duration AND
			//the beingStopped = true / false. This allows to just play once (or it would play twice)
			this.addAction(
				condition: { this.algaInstantiated },
				func: {
					var interpStreamsLock = interpStreams;
					var algaReschedulingEventStreamPlayer = interpStreams.algaReschedulingEventStreamPlayer;
					if(algaReschedulingEventStreamPlayer != nil, {
						interpStreamsLock.beingStopped = true;
						algaReschedulingEventStreamPlayer.rescheduleAtQuant(0, {
							this.setDur(value);
							interpStreamsLock.beingStopped = false;
						})
					})
				},
				sched: sched
			);
		}, {
			//sched == number: reschedule the stream in the future
			//Add to scheduler just to make cascadeMode work
			this.addAction(
				condition: { this.algaInstantiated },
				func: {
					var algaReschedulingEventStreamPlayer = interpStreams.algaReschedulingEventStreamPlayer;
					if(algaReschedulingEventStreamPlayer != nil, {
						algaReschedulingEventStreamPlayer.rescheduleAtQuant(sched, { this.setDur(value) });
					})
				}
			);
		})
	}

	//Set sustain at sched
	setSustainAtSched { | value, sched |
		//Check sched
		sched = sched ? schedInner;

		//Execute on scheduler
		this.addAction(
			func: { this.setSustain(value) },
			sched: sched,
			topPriority: true
		);
	}

	//Set stretch at sched
	setStretchAtSched { | value, sched |
		//Check sched
		sched = sched ? schedInner;

		//Execute on scheduler
		this.addAction(
			func: { this.setStretch(value) },
			sched: sched,
			topPriority: true
		);
	}

	//Set leagto at sched
	setLegatoAtSched { | value, sched |
		//Check sched
		sched = sched ? schedInner;

		//Execute on scheduler
		this.addAction(
			func: { this.setLegato(value) },
			sched: sched,
			topPriority: true
		);
	}

	//stop and reschedule in the future
	reschedule { | sched = 0 |
		interpStreams.algaReschedulingEventStreamPlayer.reschedule(sched);
	}

	//Re-connect a param (used in AlgaPatternPlayer)
	reconnectParam { | param = \in, time, sched |
		var entry = defPreParsing[param];
		if(entry != nil, {
			this.from(
				sender: entry,
				param: param,
				time: time,
				sched: sched
			)
		});
	}

	//Check all entries in controlNamesList aswell
	checkParamExists { | param = \in |
		//Standard
		if(controlNamesList == nil, {
			if(controlNames[param] == nil, { ^false });
			^true;
		});

		//ListPattern as 'def'
		controlNamesList.do({ | controlNamesEntry |
			if(controlNames[param] != nil, { ^true });
		});

		^false
	}

	//Add entry to inNodes. Unlike AlgaNodes, inNodes can here contain AlgaArgs, as the .replace
	//mechanism is difference. For AlgaNodes, AlgaArgs can only be used in the args initialization
	addInNodeAlgaNodeAlgaArg { | sender, param = \in, mix = false |
		var connectionToItself;
		if(sender.isAlgaArg, { sender = sender.sender });

		//Detect self connections
		connectionToItself = (this == sender);

		//Empty entry OR not doing mixing, create new OrderedIdentitySet.
		//Otherwise, add to existing
		if((inNodes[param] == nil).or(mix.not), {
			inNodes[param] = OrderedIdentitySet[sender];
		}, {
			inNodes[param].add(sender);
		});

		//Only go through if not connecting with itself
		if(connectionToItself.not, {
			//Add to activeInNodes / activeOutNodes
			this.addActiveInNode(sender, param);
			sender.addActiveOutNode(this, param);

			//Update blocks too (connectionWasAlreadyThere is set in AlgaPatternInterpStreams)
			if(connectionAlreadyInPlace.not, {
				AlgaBlocksDict.createNewBlockIfNeeded(this, sender)
			});
		});
	}

	//Wrapper for addInNode
	addInNode { | sender, param = \in, mix = false, forceMix = false |
		var controlNameAtParam = controlNames[param];
		var defaultValue;
		if(controlNameAtParam != nil, { defaultValue = controlNameAtParam.defaultValue });

		//First of all, remove the outNodes that the previous sender had with the
		//param of this node, if there was any. Only apply if mix == false (no <<+ / >>+)
		if(mix == false, {
			var oldSenderSet = inNodes[param];
			if(oldSenderSet != nil, {
				oldSenderSet.do({ | oldSender |
					oldSender.outNodes.removeAt(this);
				});
			});
		});

		//Reset recursiveObjectList
		recursiveObjectList = IdentitySet(10);

		//AlgaNode or AlgaArg: go through
		if(sender.isAlgaNodeOrAlgaArg, {
			if(forceMix, { mix = true });
			this.addInNodeAlgaNodeAlgaArg(sender, param, mix);
		}, {
			//Pattern
			if(sender != nil, {
				recursiveObjectList.add(sender);
				sender.algaParseObject(
					func: { | val |
						if((val != nil).and(recursiveObjectList.includes(val).not), {

							this.addInNode(val, param, mix, forceMix: true)
						});
					},
					replace: false
				);
			});
		});

		//Use replaceArgs to set LATEST parameter, for retrieval after .replace ...
		//AlgaNode only uses this for number parameters, but AlgaPattern uses it for any
		//kind of parameter, including AlgaNodes and AlgaArgs.
		if(sender != defaultValue, { replaceArgs[param] = sender });

		//Reset connectionAlreadyInPlace
		connectionAlreadyInPlace = false;
	}

	//Called from clearInner
	resetAlgaPattern {
		latestPatternInterpSumBusses.clear;
		currentActivePatternInterpSumBusses.clear;
		currentPatternBussesAndSynths.clear;
		currentActivePatternParamSynths.clear;
		currentActiveInterpBusses.clear;
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
		if(algaCleared, { ^false });
		^((this.algaInstantiated).and(synthBus != nil));
	}

	isAlgaPattern { ^true }
}

//Alias
AP : AlgaPattern {}

/*
//Implements Pmono behaviour
AlgaMonoPattern : AlgaPattern {}

//Alias
AMP : AlgaMonoPattern {}
*/

//Extension to support out: from AlgaPattern
+AlgaNode {
	//Add a node to patternOutNodes
	addPatternOutNode { | algaPattern, param = \in |
		if(patternOutNodes == nil, { patternOutNodes = IdentityDictionary() });
		if(patternOutNodes[param] == nil, { patternOutNodes[param] = OrderedIdentitySet() });
		patternOutNodes[param].add(algaPattern);
	}

	//Remove a node from patternOutNodes
	removePatternOutNode { | algaPattern, param = \in |
		var patternOutNodesAtParam;
		if(patternOutNodes == nil, { ^this });
		patternOutNodesAtParam = patternOutNodes[param];
		if(patternOutNodesAtParam == nil, { ^this });
		patternOutNodesAtParam.remove(algaPattern);
	}

	//Used in AlgaBlock
	isContainedInPatternOut { | sender |
		if(patternOutNodes != nil, {
			patternOutNodes.do({ | algaNode |
				if(algaNode === sender, { ^true })
			})
		});
		^false
	}

	//Free previous out: connections from patterns (called in AlgaNode.replace)
	freeAllPatternOutConnections { | time |
		if(patternOutNodes != nil, {
			patternOutNodes.keysValuesDo({ | param, patternOutNodesAtParam |
				patternOutNodesAtParam.do({ | algaPattern |
					this.removePatternOutsAtParam(
						algaPattern: algaPattern,
						param: param,
						removePatternOutNodeFromDict: false,
						time: time
					);
				});
			});
		});
	}

	//Re-create previous out: connections with patterns (called in AlgaNode.replace)
	createAllPatternOutConnections { | time |
		if(patternOutNodes != nil, {
			patternOutNodes.keysValuesDo({ | param, patternOutNodesAtParam |
				patternOutNodesAtParam.do({ | algaPattern |
					this.addAction(
						condition: { this.algaInstantiatedAsReceiver(param) },
						func: {
							this.receivePatternOutsAtParam(
								algaPattern: algaPattern,
								param: param,
								time: time
							);
						}
					)
				});
			});
		});
	}

	//Lock interpbus
	lockInterpBus { | uniqueID, interpBus |
		if(lockInterpBusses == nil, { lockInterpBusses = IdentityDictionary() });
		lockInterpBusses[uniqueID] = interpBus;
	}

	//Triggered when the connection is made
	receivePatternOutsAtParam { | algaPattern, param = \in, time = 0, shape |
		var controlNamesAtParam, paramRate, paramNumChannels;
		var patternOutEnvBussesAtParamAlgaPattern, patternOutEnvSynthsAtParamAlgaPattern;
		var patternOutUniqueIDsAtParam;
		var envBus, envSymbol, envSynth;
		var interpBusAtParam, interpBus;

		var algaSynthBus = algaPattern.synthBus;
		var uniqueID = UniqueID.next;
		var uniqueIDAlgaSynthBus = [uniqueID, algaSynthBus];
		var paramAlgaPatternAlgaSynthBus = [param, algaPattern, algaSynthBus];
		var paramAlgaPattern = [param, algaPattern];

		//Set time if needed
		time = time ? 0;

		//Get interpBus at param / sender combination
		interpBusAtParam = interpBusses[param];
		if(interpBusAtParam == nil, { ("AlgaNode: 'out': invalid interp bus at param '" ++ param ++ "'").error; ^this });

		//ALWAYS use the \default interpBus, which is connected to the \default normSynth.
		//Use the algaSynthBus as index, so that it can safely be removed in remove removePatternOutsAtParam.
		//Using algaPattern as index would not work on .replaces (it would remove it mid-replacement)
		interpBus = interpBusAtParam[\default];
		if(interpBus == nil, {
			(
				"AlgaNode: 'out': invalid interp bus at param '" ++
				param ++ "' and node " ++ algaPattern.asString
			).error;
			^this
		});

		//Check if patternOutUniqueIDs needs to be init.
		//Using dictionary to index with [param, algaPattern, algaSynthBus]
		if(patternOutUniqueIDs == nil, { patternOutUniqueIDs = Dictionary() });

		//Set of uniqueIDs at specific [param, algaPattern, algaSynthBus]
		if(patternOutUniqueIDs[paramAlgaPatternAlgaSynthBus] == nil, {
			patternOutUniqueIDs[paramAlgaPatternAlgaSynthBus] = OrderedIdentitySet()
		});

		//Add uniqueID to OrderedIdentitySet
		(patternOutUniqueIDs[paramAlgaPatternAlgaSynthBus]).add(uniqueID);

		//Check if patternOutEnvBusses needs to be init.
		//Using dictionary to index with [param, algaPattern]
		if(patternOutEnvBusses == nil, { patternOutEnvBusses = Dictionary() });

		//Check if patternOutEnvSynths needs to be init.
		//Using dictionary to index with [param, algaPattern]
		if(patternOutEnvSynths == nil, { patternOutEnvSynths = Dictionary() });

		//Check entries at [param, algaPattern]
		patternOutEnvBussesAtParamAlgaPattern = patternOutEnvBusses[paramAlgaPattern];
		patternOutEnvSynthsAtParamAlgaPattern = patternOutEnvSynths[paramAlgaPattern];

		//Create dict at [param, algaPattern].
		//Using dictionary to index with [uniqueID, algaSynthBus]
		if(patternOutEnvBussesAtParamAlgaPattern == nil, {
			patternOutEnvBusses[paramAlgaPattern] = Dictionary();
			patternOutEnvBussesAtParamAlgaPattern = patternOutEnvBusses[paramAlgaPattern]; //update pointer
		});

		//Create dict at [param, algaPattern].
		//Using dictionary to index with [uniqueID, algaSynthBus]
		if(patternOutEnvSynthsAtParamAlgaPattern == nil, {
			patternOutEnvSynths[paramAlgaPattern] = Dictionary();
			patternOutEnvSynthsAtParamAlgaPattern = patternOutEnvSynths[paramAlgaPattern]; //update pointer
		});

		//Get controlNames
		controlNamesAtParam = controlNames[param];
		if(controlNamesAtParam == nil, { ^this });

		//Get numChannels / rate of param
		paramNumChannels = controlNamesAtParam.numChannels;
		paramRate = controlNamesAtParam.rate;

		//Get shape
		shape = this.checkValidEnv(shape) ? this.getInterpShape(param);

		//Lock interpBus with uniqueID
		this.lockInterpBus(uniqueID, interpBus);

		//Create the envBus
		envBus = AlgaBus(server, 1, paramRate);

		//Add to patternOutEnvBusses
		patternOutEnvBussesAtParamAlgaPattern[uniqueIDAlgaSynthBus] = envBus;

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
			[
				\out, interpBus.index,
				\env_out, envBus.index,
				\fadeTime, if(tempoScaling, { time / this.clock.tempo }, { time }),
				\envShape, shape.algaConvertEnv
			],
			interpGroup,
			waitForInst:false
		);

		//Add to patternOutEnvSynths
		patternOutEnvSynthsAtParamAlgaPattern[uniqueIDAlgaSynthBus] = envSynth;

		//Add patternOutNode
		this.addPatternOutNode(algaPattern, param);

		//Update activeInNodes / activeOutNodes
		//These are manually cleared in removePatternOutsAtParam...
		//Unlike other connections, this doesn't actually wait for end, as there's no interpSynth involved,
		//but it should be mostly fine...
		this.addActiveInNode(algaPattern, param);
		algaPattern.addActiveOutNode(this, param);

		//Update blocks
		AlgaBlocksDict.createNewBlockIfNeeded(this, algaPattern)
	}

	//Trigger the release of all active out: synths at specific param for a specific algaPattern.
	//This is called everytime a connection is removed
	removePatternOutsAtParam { | algaPattern, param = \in, removePatternOutNodeFromDict = true, time, shape |
		var paramAlgaPattern = [param, algaPattern];
		var patternOutEnvSynthsAtParamAlgaPattern = patternOutEnvSynths[paramAlgaPattern];
		var patternOutEnvBussesAtParamAlgaPattern = patternOutEnvBusses[paramAlgaPattern];

		//Set time if needed
		time = time ? 0;

		//Get shape
		shape = this.checkValidEnv(shape) ? this.getInterpShape(param);

		if(patternOutEnvSynthsAtParamAlgaPattern != nil, {
			patternOutEnvSynthsAtParamAlgaPattern.keysValuesDo({ | uniqueIDAlgaSynthBus, patternOutEnvSynth |
				var uniqueID = uniqueIDAlgaSynthBus[0];
				var algaSynthBus = uniqueIDAlgaSynthBus[1];
				var paramAlgaPatternUniqueID = [param, algaPattern, uniqueID];
				var paramAlgaPatternAlgaSynthBus = [param, algaPattern, algaSynthBus];

				var patternOutEnvBus = patternOutEnvBussesAtParamAlgaPattern[uniqueIDAlgaSynthBus];

				//Free bus and entries when synth is done.
				//It's still used while fade-out interpolation is happening
				patternOutEnvSynth.set(
					\t_release, 1,
					\fadeTime, if(tempoScaling, { time / this.clock.tempo }, { time }),
					\envShape, shape.algaConvertEnv
				);

				//When freed
				patternOutEnvSynth.onFree({
					//Add the patternOutEnvBus to patternOutEnvBussesToBeFreed.
					//It must be a Dictionary cause of the Array key: needs to be checked by value...
					//This simply frees time of creating multiple IdentityDictionaries instead
					if(patternOutEnvBussesToBeFreed == nil, { patternOutEnvBussesToBeFreed = Dictionary() });

					//REVIEW THIS WITH THE NEW DICT MECHANISM: IT CREATES CLICKS AT TIMES!
					patternOutEnvBussesToBeFreed[uniqueIDAlgaSynthBus] = patternOutEnvBus;

					//Remove entries from patternOutEnvSynths and patternOutEnvBusses
					patternOutEnvSynths[paramAlgaPattern].removeAt(uniqueIDAlgaSynthBus);
					patternOutEnvBusses[paramAlgaPattern].removeAt(uniqueIDAlgaSynthBus);

					//Remove entries from lockInterpBusses
					if(lockInterpBusses != nil, { lockInterpBusses.removeAt(uniqueID) });

					//Remove uniqueID from patternOutUniqueIDs
					if(patternOutUniqueIDs != nil, {
						var patternOutUniqueIDsAtParamAlgaPattern = patternOutUniqueIDs[paramAlgaPatternAlgaSynthBus];
						if(patternOutUniqueIDsAtParamAlgaPattern != nil, {
							patternOutUniqueIDs[paramAlgaPatternAlgaSynthBus].remove(uniqueID)
						});
					});
				});
			});
		});

		//Update activeInNodes / activeOutNodes / patternOutNode only if needed.
		//On .replace, this will be false.
		if(removePatternOutNodeFromDict, {
			this.removePatternOutNode(algaPattern, param);
			this.removeActiveInNode(algaPattern, param);
			algaPattern.removeActiveOutNode(this, param);
		});
	}

	//Triggered every patternSynth. algaSynthBus is only used as a "indexer"
	receivePatternOutTempSynth { | algaPattern, algaSynthBus, outTempBus, algaNumChannels, algaRate,
		param = \in, patternBussesAndSynths, chans, scale |

		//Loop around the uniqueIDs for this specific [param, algaPattern] combo.
		//This allows for .replaces of receiver (this AlgaNode) to work.
		if(patternOutUniqueIDs != nil, {
			var paramAlgaPatternAlgaSynthBus = [param, algaPattern, algaSynthBus];
			var patternOutUniqueIDsAtParamAlgaPatternAlgaSynthBus = patternOutUniqueIDs[paramAlgaPatternAlgaSynthBus];
			if(patternOutUniqueIDsAtParamAlgaPatternAlgaSynthBus != nil, {
				patternOutUniqueIDsAtParamAlgaPatternAlgaSynthBus.do({ | uniqueID |
					var envBus;
					var controlNamesAtParam, paramNumChannels, paramRate;
					var interpBusAtParam, interpBus;
					var tempSynthSymbol;
					var tempSynthArgs, tempSynth;

					var uniqueIDAlgaSynthBus = [uniqueID, algaSynthBus];
					var paramAlgaPattern = [param, algaPattern];

					//Retrieve envBus from patternOutEnvBusses
					envBus = patternOutEnvBusses[paramAlgaPattern][uniqueIDAlgaSynthBus];
					if(envBus == nil, { ("AlgaNode: 'out': invalid envBus at param '" ++ param ++ "'").error; ^this });

					//Get controlNames
					controlNamesAtParam = controlNames[param];
					if(controlNamesAtParam == nil, { ^this });

					//Get channels / rate of param
					paramNumChannels = controlNamesAtParam.numChannels;
					paramRate = controlNamesAtParam.rate;

					//Calculate scale / chans
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

					//Get the locked interpBus (which is always \default, as it's the one connected to \default normSynth).
					//However, this will also work across .replace calls.
					interpBus = lockInterpBusses[uniqueID];
					if(interpBus == nil, { ("AlgaNode: 'out': invalid locked interp bus at param '" ++ param ++ "'").error; ^this });

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
						\gate, 1,
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

					//Add tempSynth to activeInterpSynthsAtParam...
					//Make sure "sender" is nil as the activeInNodes mechanism should not be handled here!
					this.addActiveInterpSynthOnFree(param, nil, algaPattern, tempSynth);

					//Add Synth to patternBussesAndSynths
					patternBussesAndSynths.add(tempSynth);

					//Free dangling patternEnvBusses related to this [uniqueID, algaSynthBus] pair
					tempSynth.onFree({
						if(patternOutEnvBussesToBeFreed != nil, {
							var patternEnvBusAtUniqueID = patternOutEnvBussesToBeFreed[uniqueIDAlgaSynthBus];
							if(patternEnvBusAtUniqueID != nil, {
								patternOutEnvBussesToBeFreed.removeAt(uniqueIDAlgaSynthBus);
								//There still is the need of waiting a bit more, or it will click...
								//Just like freeUnusedInterpBusses: it's a syncing problem
								fork {
									1.wait;
									patternEnvBusAtUniqueID.free;
								};
							});
						});
					});
				});
			});
		});
	}
}
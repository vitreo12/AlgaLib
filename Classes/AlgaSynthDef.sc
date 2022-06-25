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

//Stores both synthDef and synthDef with \out control (for AlgaPattern).
//and dispatches the methods to both of them.
AlgaSynthDefSpec {
	var <synthDef, <synthDefPattern, <synthDefPatternOut;

	*new { | synthDef, synthDefPattern, synthDefPatternOut |
		^super.new.init(synthDef, synthDefPattern, synthDefPatternOut)
	}

	init { | argSynthDef, argSynthDefPattern, argSynthDefPatternOut |
		synthDef = argSynthDef;
		synthDefPattern = argSynthDefPattern;
		synthDefPatternOut = argSynthDefPatternOut;
	}

	add { | libname, completionMsg, keepDef = true |
		synthDef.add(libname, completionMsg, keepDef);
		if(synthDefPattern != nil, { synthDefPattern.add(libname, completionMsg, keepDef) });
		if(synthDefPatternOut != nil, { synthDefPatternOut.add(libname, completionMsg, keepDef) });
	}

	send { | server, completionMsg |
		synthDef.send(server, completionMsg.value(server));
		if(synthDefPattern != nil, { synthDefPattern.send(server, completionMsg) });
		if(synthDefPatternOut != nil, { synthDefPatternOut.send(server, completionMsg) });
	}

	sendAndAddToGlobalDescLib { | server, completionMsg |
		synthDef.sendAndAddToGlobalDescLib(server, completionMsg);
		if(synthDefPattern != nil, { synthDefPattern.sendAndAddToGlobalDescLib(server, completionMsg) });
		if(synthDefPatternOut != nil, { synthDefPatternOut.sendAndAddToGlobalDescLib(server, completionMsg) });
	}

	writeDefFile { | dir, overwrite = true, mdPlugin |
		synthDef.writeDefFile(dir, overwrite, mdPlugin);
		//Metadata is only needed for the main synthDef
		if(synthDefPattern != nil, {
			synthDefPattern.writeDefFile(dir, overwrite, mdPlugin, false) }
		);
		if(synthDefPatternOut != nil, {
			synthDefPatternOut.writeDefFile(dir, overwrite, mdPlugin, false)
		});
	}

	//Alias (writeDef can't be used)
	write { | dir, overwrite = true, mdPlugin |
		^this.writeDefFile(dir, overwrite, mdPlugin)
	}

	load { | server, completionMsg, dir |
		synthDef.load(server, completionMsg, dir);
		//Metadata is only needed for the main synthDef
		if(synthDefPattern != nil, {
			synthDefPattern.load(server, completionMsg, dir, false)
		});
		if(synthDefPatternOut != nil, {
			synthDefPatternOut.load(server, completionMsg, dir, false)
		});
	}

	store { | libname=\alga, dir, completionMsg, mdPlugin |
		synthDef.store(libname, dir, completionMsg, mdPlugin);
		//Metadata is only needed for the main synthDef
		if(synthDefPattern != nil, {
			synthDefPattern.store(libname, dir, completionMsg, mdPlugin, false)
		});
		if(synthDefPatternOut != nil, {
			synthDefPatternOut.store(libname, dir, completionMsg, mdPlugin, false)
		});
	}

	asSynthDesc { | libname=\alga, keepDef = true |
		^synthDef.asSynthDesc(libname, keepDef)
	}

	name {
		^synthDef.name
	}

	numChannels {
		^synthDef.numChannels
	}

	rate {
		^synthDef.rate
	}

	explicitFree {
		^synthDef.explicitFree
	}

	outsMapping {
		^synthDef.outsMapping
	}

    //Everything else
	doesNotUnderstand { | selector ...args |
		^synthDef.perform(selector, *args)
	}
}

//Hybrid between a normal SynthDef and a ProxySynthDef (makeFadeEnv).
//makeFadeEnv, however, does not multiply the output, but it is only used for Alga's internal
//freeing mechanism. Furthermore, outsMapping is provided.
AlgaSynthDef : SynthDef {
	var <>rate, <>numChannels;
	var <>canReleaseSynth, <>canFreeSynth;

	var <>explicitFree;
	var <>sampleAccurate;
	var <>outsMapping;

	*readAll { | path, server |
		server = server ? Server.default;
		SynthDescLib.alga.readAll(path, server)
	}

	*read { | path, server |
		server = server ? Server.default;
		SynthDescLib.alga.readDef(path, server)
	}

	//Default sampleAccurate to false. If user needs OffsetOut (for pattern accuracy), he must set it to true.
	*new { | name, func, rates, prependArgs, outsMapping, sampleAccurate = false,
		replaceOut = false, variants, metadata |
		^this.new_inner(
			name: name,
			func: func,
			rates: rates,
			prependArgs: prependArgs,
			outsMapping: outsMapping,
			sampleAccurate: sampleAccurate,
			replaceOut: replaceOut,
			variants: variants,
			metadata: metadata
		)
	}

	*new_inner { | name, func, rates, prependArgs, outsMapping,
		sampleAccurate = false, replaceOut = false, variants,
		metadata, makeFadeEnv = true, makePatternDef = true, makeOutDef = true |
		var defPattern, defOut;
		var result;

		var def = this.new_inner_inner(
			name: name,
			func: func,
			rates: rates,
			prependArgs: prependArgs,
			outsMapping: outsMapping,
			sampleAccurate: sampleAccurate,
			replaceOut: replaceOut,
			variants: variants,
			metadata: metadata,
			makeFadeEnv: makeFadeEnv,
			makeOutDef: false
		);

		if(makePatternDef, {
			var namePattern = (name.asString ++ "_algaPattern").asSymbol;
			defPattern = this.new_inner_inner(
				name: namePattern,
				func: func,
				rates: rates,
				prependArgs: prependArgs,
				outsMapping: outsMapping,
				sampleAccurate: sampleAccurate,
				replaceOut: replaceOut,
				variants: variants,
				metadata: metadata,
				makeFadeEnv: makeFadeEnv,
				makePatternDef: true,
				makeOutDef: false
			);
		});

		if(makeOutDef, {
			var namePatternTempOut = (name.asString ++ "_algaPatternTempOut").asSymbol;
			defOut = this.new_inner_inner(
				name: namePatternTempOut,
				func: func,
				rates: rates,
				prependArgs: prependArgs,
				outsMapping: outsMapping,
				sampleAccurate: sampleAccurate,
				replaceOut: replaceOut,
				variants: variants,
				metadata: metadata,
				makeFadeEnv: makeFadeEnv,
				makePatternDef: makePatternDef, //Can be used here too!
				makeOutDef: true
			);
		});

		if(makePatternDef.or(makeOutDef), { ^AlgaSynthDefSpec(def, defPattern, defOut) });
		^def;
	}

	*new_inner_inner { | name, func, rates, prependArgs, outsMapping,
		sampleAccurate = false, variants, metadata, makeFadeEnv = true,
		makePatternDef = false, makeOutDef = false, replaceOut = false, ignoreOutWarning = false |
		var def, rate, numChannels, output, isScalar, envgen, canFree, hasOwnGate;
		var outerBuildSynthDef = UGen.buildSynthDef;

		def = super.new(name, {
			var out, outCtl, buildSynthDef;
			var ampProvided = false;
			var gateProvided = false;
			var fadeTimeProvided = false;

			//invalid func
			if(func.isFunction.not, {
				Error("AlgaSynthDef: func is not a Function").algaThrow;
			});

			// build the controls from args
			output = SynthDef.wrap(func, rates, prependArgs);
			output = output.asUGenInput;

			//Invalid return value
			if(output == nil, {
				Error("AlgaSynthDef: could not retrieve the return value. Perhaps your function ends with a 'var' declaration?").algaThrow
			});

			// protect from user error
			if(output.isKindOf(UGen) and: { output.synthDef != UGen.buildSynthDef }) {
				Error("AlgaSynthdef: cannot share UGens between AlgaNodes:" + output).algaThrow
			};

			buildSynthDef = UGen.buildSynthDef;

			//Check for invalid controlNames set by user (\out, \gate, ...)
			buildSynthDef.allControlNames.do({ | controlName |
				var error = false;
				var controlNameName = controlName.name;

				//Check if amp was there already
				if(controlNameName == \amp, { ampProvided = true });

				//Check \gate
				if(controlNameName == \gate, {
					if(controlName.rate != \control, {
						Error("AlgaSynthDef: 'gate' can only be a control rate parameter").algaThrow
					});
					gateProvided = true;
				});

				//Check \fadeTime
				if(controlNameName == \fadeTime, {
					if(controlName.rate != \control, {
						Error("AlgaSynthDef: 'fadeTime' can only be a control rate parameter").algaThrow
					});
					fadeTimeProvided = true;
				});

				//Check for invalid names
				if((controlNameName == \out).or(controlNameName == \patternTempOut).or(
					controlNameName == \timingOffset).or(controlNameName == \lag), {
					if(controlName.rate != \scalar, {
						Error("AlgaSynthDef: special parameter '" ++ controlNameName ++ "' must be scalar").algaThrow;
					});
				});

				//Finally, print user for certainety when using any dur key
				if((controlNameName == \dur).or(controlNameName == \delta).or(controlNameName == \sustain).or(
					controlNameName == \stretch).or(controlNameName == \legato), {
					("AlgaSynthDef: Note that the '" ++ controlNameName ++ "' parameter is a reserved name used in AlgaPatterns. If using this def for an AlgaNode, consider changing the name to activate the interpolation features").warn
				});
			});

			//Check if user has explicit Outs, this is not permitted (allow LocalOut for inner fb)
			if(ignoreOutWarning.not, {
				buildSynthDef.children.do({ | ugen |
					if((ugen.isKindOf(AbstractOut)).and(ugen.isKindOf(LocalOut).not), {
						Error("AlgaSynthDef: Out / OffsetOut cannot be explicitly set. They are declared internally.").algaThrow;
					});
				});
			});

			// protect from accidentally returning wrong array shapes
			if(output.containsSeqColl) {
				// try first unbubble singletons, these are ok
				output = output.collect { |each| each.unbubble };
				// otherwise flatten, but warn
				if(output.containsSeqColl) {
					"AlgaSynthDef: Synth output should be a flat array.\n%\nFlattened to: %".format(output, output.flat).warn;
					output = output.flat;
				};
			};

			//Protect user
			output = output ? 0.0;

			// determine rate and numChannels of ugen func
			numChannels = output.numChannels;
			rate = output.rate;
			isScalar = rate === 'scalar';

			// rate is only scalar if output was nil or if it was directly produced by an out ugen
			// this allows us to conveniently write constant numbers to a bus from the synth
			// if you want the synth to write nothing, return nil from the UGen function.
			if(isScalar and: { output.notNil } and: { buildSynthDef.children.last.isKindOf(AbstractOut).not }) {
				rate = 'control';
				isScalar = false;
			};

			//Only makeFadeEnv if the Synth can't free itself
			canFree = UGen.buildSynthDef.children.algaCanFreeSynth;
			makeFadeEnv = makeFadeEnv and: { (isScalar || canFree || makePatternDef).not };

			//the AlgaEnvGate will take care of freeing the synth, even if not used to multiply
			//with output! This is fundamental for the \fadeTime mechanism in Alga to work,
			//freeing synths at the right time.
			envgen = if(makeFadeEnv, {
				AlgaEnvGate.kr(
					gate: if(gateProvided, { \gate.kr }, { \gate.kr(1) }),
					fadeTime: if(fadeTimeProvided, { \fadeTime.kr }, { \fadeTime.kr(0) }),
					i_level: 0, doneAction: 2
				)
			}, {
				1.0
			});

			//Scalar == ir
			if(isScalar, {
				output
			}, {
				//Add \amp if needed: should .kr be used in all cases to save CPU?
				if(ampProvided.not, {
					if(rate === \audio,
						{ output = output * \amp.ar(1) },
						{ output = output * \amp.kr(1) }
					);
				});

				//If not already able to free itself, and makePatternDef,
				//check against output for silence to free synth.
				//Should it check against \amp instead?
				if((canFree.not).and(makePatternDef), {
					if(rate === \audio,
						{ output = AlgaDetectSilence.ar(output) },
						{ output = AlgaDetectSilence.kr(output) }
					)
				});

				//\out control business
				outCtl = \out.ir(0);
				(
					if(replaceOut, { ReplaceOut }, {
						if((rate === \audio).and(sampleAccurate), { OffsetOut }, { Out })
					});
				).multiNewList([rate, outCtl] ++ output);
				if(makeOutDef, {
					//No ReplaceOut for patternTempOut
					var outTempCtl = \patternTempOut.ir(0);
					(
						if((rate === \audio).and(sampleAccurate), { OffsetOut }, { Out })
					).multiNewList([rate, outCtl] ++ output);
				});
			})
		});

		//Reset
		UGen.buildSynthDef = outerBuildSynthDef;

		// set the synthDefs instvars, so they can be used later
		def.rate = rate;
		def.numChannels = numChannels;
		def.canReleaseSynth = makeFadeEnv;
		def.canFreeSynth = def.canReleaseSynth || canFree;
		def.sampleAccurate = sampleAccurate;

		//this is used for AlgaPattern.
		//makeFadeEnv = true can be deceiving.
		//AlgaPatterns' AlgaSynthDefs should just set it to false
		def.explicitFree = canFree;

		//Set outsMapping as \out1 -> 0, etc...
		def.outsMapping = IdentityDictionary(numChannels);
		numChannels.do({ | i |
			var out = ("out" ++ (i + 1)).asSymbol;
			def.outsMapping[out] = i;
		});

		//Store the original func.
		//This is mostly needed to prevent writeArchive from complaining
		def.func = func;

		//Must be array.
		if(outsMapping.isArray, {
			var chanCount = 0;
			outsMapping.do({ | entry, i |
				if(entry.isSymbol, {
					var name = outsMapping[i];
					var val  = outsMapping[i+1];
					if((val.isSymbol.not).and(val != nil), {
						chanCount = chanCount + 1;
						if(val.isArray, {
							val.do({ | arrayEntry, arrayIndex |
								if(arrayEntry < numChannels, {
									def.outsMapping[name] = val;
								}, {
									("AlgaSynthDef: '" ++ name ++ " accesses out of bound channels: " ++ val).error;
								});
							});
						}, {
							if(val < numChannels, {
								def.outsMapping[name] = val;
							}, {
								("AlgaSynthDef: '" ++ name ++ "' accesses an out of bound channel: " ++ val).error;
							});
						});
					}, {
						def.outsMapping[name] = chanCount;
						chanCount = chanCount + 1;
					});
				});
			});
		});

		//Add variants and metadata
		if(variants != nil, { def.variants = variants });
		if(metadata != nil, { def.metadata = metadata });

		^def
	}

	//Always store in alga libname
	sendAndAddToGlobalDescLib { | server, completionMsg |
		desc = this.asSynthDesc(\alga, true);
		this.send(server, completionMsg)
	}

	//Always store in alga libname
	add { | libname, completionMsg, keepDef = true |
		^super.add(\alga, completionMsg, keepDef)
	}

	//Used to
	createDirAndWriteArchiveMd { | dir, writeMd = true |
		var nameStr = this.name.asString;

		//Uses Alga's one
		dir = PathName((dir ? AlgaStartup.algaSynthDefPath).asString).absolutePath;

		//Create alternative files
		if(nameStr.endsWith("_algaPattern").or(
			nameStr.endsWith("_algaPatternTempOut")), {
			var name = nameStr.replace("_algaPattern", "").replace("TempOut", "");
			dir = dir.withoutTrailingSlash ++ "/" ++ name;
		}, {
			dir = dir.withoutTrailingSlash ++ "/" ++ nameStr;
		});

		//Also store archive for Metadata
		if(writeMd, {
			File.mkdir(dir); //Create the new dir only once
			this.writeArchive(dir ++ "/"  ++ this.name ++ ".scsyndefmd");
		});

		^dir
	}

	//Store in Alga's AlgaSynthDefs folder
	writeDefFile { | dir, overwrite = true, mdPlugin, writeMd = true |
		dir = this.createDirAndWriteArchiveMd(dir, writeMd);
		^super.writeDefFile(dir, overwrite, mdPlugin);
	}

	//Alias (writeDef can't be used)
	write { | dir, overwrite = true, mdPlugin, writeMd = true |
		^this.writeDefFile(dir, overwrite, mdPlugin, writeMd)
	}

	//Store in Alga's AlgaSynthDefs folder
	load { | server, completionMsg, dir, writeMd = true |
		dir = this.createDirAndWriteArchiveMd(dir, writeMd);
		^super.load(server, completionMsg, dir);
	}

	//Store in Alga's AlgaSynthDefs folder
	store { | libname=\alga, dir, completionMsg, mdPlugin, writeMd = true |
		dir = this.createDirAndWriteArchiveMd(dir, writeMd);
		^super.store(libname, dir, completionMsg, mdPlugin);
	}
}
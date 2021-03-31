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

//Hybrid between a normal SynthDef and a ProxySynthDef (makeFadeEnv).
//makeFadeEnv, however, does not multiply the output, but it is only used for Alga's internal
//freeing mechanism. Furthermore, outsMapping is provided.
AlgaSynthDef : SynthDef {

	var <>rate, <>numChannels;
	var <>canReleaseSynth, <>canFreeSynth;

	var <>explicitFree;

	var <>outsMapping;

	//Use OffsetOut by default!
	classvar <>sampleAccurate=true;

	*new { | name, func, rates, prependArgs, outsMapping |
		^this.new_inner(name, func, rates, prependArgs, outsMapping)
	}

	*new_inner { | name, func, rates, prependArgs, outsMapping, makeFadeEnv = true |
		var def, rate, numChannels, output, isScalar, envgen, canFree, hasOwnGate;
		var outerBuildSynthDef = UGen.buildSynthDef;

		def = super.new(name, {
			var out, outCtl, buildSynthDef;

			// build the controls from args
			output = SynthDef.wrap(func, rates, prependArgs);
			output = output.asUGenInput;

			// protect from user error
			if(output.isKindOf(UGen) and: { output.synthDef != UGen.buildSynthDef }) {
				Error("AlgaSynthdef: cannot share UGens between AlgaNodes:" + output).throw
			};

			buildSynthDef = UGen.buildSynthDef;

			//Check for invalid controlNames set by user (\out, \gate, ...)
			buildSynthDef.allControlNames.do({ | controlName |
				var error = false;
				var controlNameName = controlName.name;

				//Don't \gate arg when makeFadeEnv is true, otherwise it's fine (it's used in AlgaStartup)
				if((makeFadeEnv.and(controlNameName == \gate)).or(controlNameName == \out), {
					error = true
				});

				if(error, {
					("AlgaSynthDef: \\" ++ controlNameName.asString ++ " parameter cannot be explicitly set. It's used internally.").error;
					^nil
				});
			});

			//Check if user has explicit Outs, this is not permitted
			buildSynthDef.children.do({ | ugen |
				if(ugen.isKindOf(AbstractOut), {
					"AlgaSynthDef: Out / OffsetOut cannot be explicitly set. They are declared internally.".error;
					^nil;
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
			canFree = UGen.buildSynthDef.children.canFreeSynth;
			makeFadeEnv = makeFadeEnv and: { (isScalar || canFree).not };

			//the AlgaEnvGate will take care of freeing the synth, even if not used to multiply
			//with output! This is fundamental for the \fadeTime mechanism in Alga to work,
			//freeing synths at the right time.
			envgen = if(makeFadeEnv, {
				AlgaEnvGate.kr(i_level: 0, doneAction:2);
			}, {
				1.0;
			});

			if(isScalar, {
				output
			}, {
				outCtl = Control.names(\out).ir(0);
				(if(rate === \audio and: { sampleAccurate }) { OffsetOut } { Out }).multiNewList([rate, outCtl] ++ output)
			})
		});

		//Reset
		UGen.buildSynthDef = outerBuildSynthDef;

		// set the synthDefs instvars, so they can be used later
		def.rate = rate;
		def.numChannels = numChannels;
		def.canReleaseSynth = makeFadeEnv;
		def.canFreeSynth = def.canReleaseSynth || canFree;

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

		//Must be array.
		if(outsMapping.class == Array, {
			var chanCount = 0;
			outsMapping.do({ | entry, i |
				if(entry.class == Symbol, {
					var name = outsMapping[i];
					var val  = outsMapping[i+1];

					if((val.class != Symbol).and(val != nil), {

						chanCount = chanCount + 1;

						if(val.class == Array, {
							val.do({ | arrayEntry, arrayIndex |
								if(arrayEntry < numChannels, {
									def.outsMapping[name] = val;
								}, {
									(name ++ " accesses out of bound channels: " ++ val).error;
								});
							});
						}, {
							if(val < numChannels, {
								def.outsMapping[name] = val;
							}, {
								(name ++ " accesses an out of bound channel: " ++ val).error;
							});
						});
					}, {

						def.outsMapping[name] = chanCount;

						chanCount = chanCount + 1;
					});
				});
			});
		});

		^def
	}
}

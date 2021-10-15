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

/*
NOTE that all AlgaSynthDefs here declared have sampleAccurate:false so that
the AlgaSynthDef will use Out instead of OffsetOut. This is FUNDAMENTAL to avoid bugs
with sample accuracy. All these definitions are needed FROM THE START of an audio block,
and should not be delayed as OffsetOut would. sampleAccurate:true is still maintained
as default for AlgaSynthDefs defined by users, in which case OffsetOut can delay the
specific definition within the audio block. Its controls, though, NEED to start at the
beginning of the audio block!
*/
AlgaStartup {
	classvar <algaMaxIO = 8;

	classvar <algaSynthDefPath;
	classvar <algaSynthDefIOPath;
	classvar <algaSynthDefIO_numberPath;

	classvar percentageSplit;

	*initClass {
		algaSynthDefPath = File.realpath(Alga.class.filenameSymbol).dirname.withTrailingSlash ++ "../AlgaSynthDefs";
		algaSynthDefIOPath = (algaSynthDefPath ++ "/IO");
		algaSynthDefIO_numberPath = (algaSynthDefIOPath ++ "/IO_" ++ algaMaxIO ++ "/");
	}

	*algaMaxIO_ { | value |
		if(value.isNumber.not, { "AlgaStartup: algaMaxIO must be a number".error; ^this });
		algaMaxIO = value;
		this.updateAlgaSynthDefIOPath;
	}

	*maxIO {
		^algaMaxIO
	}

	*maxIO_ { | value |
		this.algaMaxIO_(value)
	}

	*updateAlgaSynthDefIOPath {
		algaSynthDefIOPath = (algaSynthDefIOPath ++ "/IO_" ++ algaMaxIO ++ "/").asString;
	}

	*initSynthDefs {
		var folderDeleted = true;

		if(File.exists(algaSynthDefIOPath), {
			folderDeleted = File.deleteAll(algaSynthDefIOPath);
		});

		percentageSplit = 100.0 / algaMaxIO;

		if(folderDeleted, {
			var algaSynthDefFolderCreated = true;

			if(File.exists(algaSynthDefPath).not, {
				algaSynthDefFolderCreated = File.mkdir(algaSynthDefPath);
			});

			if(algaSynthDefFolderCreated, {
				var algaSynthDefIOFolderCreated = File.mkdir(algaSynthDefIOPath);

				if(algaSynthDefIOFolderCreated, {
					var algaSynthDefIO_numberFolderCreated = File.mkdir(algaSynthDefIO_numberPath);

					if(algaSynthDefIO_numberFolderCreated, {
						("\n-> Generating all Alga SynthDefs for a max of " ++ algaMaxIO ++ " I/O count, it may take a while...").postln;

						this.initAlgaPlay;
						this.initAlgaInterp;
						this.initAlgaNorm;
						this.initAlgaMixFades;
						this.initAlgaPatternInterp;
						"\n-> Done!".postln;
					}, {
						("Could not create path: " ++ algaSynthDefIO_numberPath).error;
					});
				}, {
					("Could not create path: " ++ algaSynthDefIOPath).error;
				});
			}, {
				("Could not create path: " ++ algaSynthDefPath).error;
			});
		}, {
			("Could not delete path: " ++ algaSynthDefPath).error;
		});
	}

	*initAlgaPlay {

		var alreadyDonePairs = IdentityDictionary.new;

		var percentageCounter = 0.0;

		"\n(1/5) Generating the alga_play definitions...".postln;

		algaMaxIO.do({ | i |
			var arrayOfZeros_in, arrayOfIndices;

			var innerPercentageSplit = percentageSplit / algaMaxIO;

			(percentageCounter.asStringPrec(2) ++ " %").postln;
			percentageCounter = percentageCounter + percentageSplit;

			i = i + 1;

			if(i == 1, {
				arrayOfZeros_in = "0";

			}, {
				arrayOfZeros_in = "[";

				//[0, 0, 0...
				i.do({ | num |
					arrayOfZeros_in = arrayOfZeros_in ++ "0,";

				});

				//remove trailing coma [0, 0, 0, and enclose in bracket -> [0, 0, 0]
				arrayOfZeros_in = arrayOfZeros_in[0..(arrayOfZeros_in.size - 2)] ++ "]";
			});

			algaMaxIO.do({ | y |

				var sdef, arrayOfIndices, currentPair, isAlreadyDone;

				y = y + 1;

				currentPair = [i, y];
				isAlreadyDone = alreadyDonePairs[currentPair];

				if(isAlreadyDone != true , {
					if(y == 1, {
						arrayOfIndices = "0";
					}, {
						arrayOfIndices = "[";

						y.do({ | num |
							arrayOfIndices = arrayOfIndices ++ num.asString ++ ",";
						});

						arrayOfIndices = arrayOfIndices[0..(arrayOfIndices.size - 2)] ++ "]";
					});

					//Limiter to make sure not to blow up speakers.
					//Note: Using AlgaEnvGate.ar here instead of AlgaDynamicEnvGate.
					//no need for dynamic fade times for play / stop, and AlgaEnvGate is cheaper!
					sdef = "
AlgaSynthDef.new_inner(\\alga_play_" ++ i ++ "_" ++ y ++ ", {
var input = \\in.ar(" ++ arrayOfZeros_in ++ ");
input = Select.ar(\\indices.ir(" ++ arrayOfIndices ++ "), input);
Limiter.ar(input) * AlgaEnvGate.kr
}, makeFadeEnv:false, sampleAccurate:false, makeOutDef:false).algaStore(dir:AlgaStartup.algaSynthDefIO_numberPath);
";

					sdef.interpret;
				});
			});
		});

		("100 %").postln;
	}

	*initAlgaInterp {

		var alreadyDonePairs = IdentityDictionary.new(algaMaxIO);

		var exponentialPercentageSplit = 100.0 / (algaMaxIO * algaMaxIO);
		var percentageCounter = 0.0;

		"\n(2/5) Generating the alga_interp definitions (this step takes the longest time)...".postln;

		//var file = File("~/AlgaSynthDefsTest.scd".standardizePath,"w");

		algaMaxIO.do({ | i |

			var arrayOfZeros_in;

			i = i + 1;

			if(i == 1, {
				arrayOfZeros_in = "0";
			}, {
				arrayOfZeros_in = "[";

				//[0, 0, 0...
				i.do({
					arrayOfZeros_in = arrayOfZeros_in ++ "0,";
				});

				//remove trailing coma [0, 0, 0, and enclose in bracket -> [0, 0, 0]
				arrayOfZeros_in = arrayOfZeros_in[0..(arrayOfZeros_in.size - 2)] ++ "]";
			});

			algaMaxIO.do({ | y |

				var arrayOfIndices;
				var arrayOfMinusOnes, arrayOfOnes;
				var currentPair, isAlreadyDone;

				if(percentageCounter < 10, {
					(percentageCounter.asStringPrec(1) ++ " %").postln;
				}, {
					(percentageCounter.asStringPrec(2) ++ " %").postln;
				});
				percentageCounter = percentageCounter + exponentialPercentageSplit;

				y = y + 1;

				if(y == 1, {
					arrayOfIndices = "0";
					arrayOfMinusOnes = "-1.0";
					arrayOfOnes = "1.0";
				}, {
					arrayOfIndices = "[";
					arrayOfMinusOnes = "[";
					arrayOfOnes = "[";

					y.do({ | num |
						arrayOfIndices = arrayOfIndices ++ num.asString ++ ",";
						arrayOfMinusOnes = arrayOfMinusOnes ++ "-1.0,";
						arrayOfOnes = arrayOfOnes ++ "1.0,";
					});

					arrayOfIndices = arrayOfIndices[0..(arrayOfIndices.size - 2)] ++ "]";
					arrayOfMinusOnes = arrayOfMinusOnes[0..(arrayOfMinusOnes.size - 2)] ++ "]";
					arrayOfOnes = arrayOfOnes[0..(arrayOfOnes.size - 2)] ++ "]";
				});

				currentPair = [i, y];
				isAlreadyDone = alreadyDonePairs[currentPair];

				//Not done already
				if(isAlreadyDone.isNil, {
					var outs = "outs[0] = out;";
					var indices_ar = "in;";
					var indices_kr = "in;";
					var env_pattern_ar = "\\env.ar(0);";
					var env_pattern_kr = "\\env.kr(0);";
					var sampleAndHold_ar = "Select.ar(\\sampleAndHold.kr(0), [env, Latch.ar(env, \\t_sah.tr(0))]);";
					var sampleAndHold_kr = "Select.kr(\\sampleAndHold.kr(0), [env, Latch.kr(env, \\t_sah.tr(0))]);";

					//constant multiplier. this is set when mix's scale argument is a single Number
					var multiplier = "\\outMultiplier.ir(1);";

					if(y > 1, {
						outs = y.asString ++ ".do({ | i | outs[i] = out[i]});";
					});

					//keep in; for i == 0
					if(i > 1, {
						indices_ar = "Select.ar(\\indices.ir(" ++ arrayOfIndices ++ "), in);";
						indices_kr = "Select.kr(\\indices.ir(" ++ arrayOfIndices ++ "), in);";
					});

					[\ar_ar, \kr_kr, \ar_kr, \kr_ar].do({ | rate |
						var result;
						var name, in, indices, env, scaling;
						var name_pattern, env_pattern, sampleAndHold;

						if(rate == \ar_ar, {
							name = "\\alga_interp_audio" ++ i ++ "_audio" ++ y;
							name_pattern = "\\alga_pattern_audio" ++ i ++ "_audio" ++ y;
							in = "\\in.ar(" ++ arrayOfZeros_in ++ ");";
							indices = indices_ar;
							scaling = "Select.ar(\\useScaling.ir(0), [out, outScale]);";
							env = "AlgaDynamicEnvGate.ar(\\t_release.tr(0), \\fadeTime.kr(0));";
							env_pattern = env_pattern_ar;
							sampleAndHold = sampleAndHold_ar;
						});

						if(rate == \kr_kr, {
							name = "\\alga_interp_control" ++ i ++ "_control" ++ y;
							name_pattern = "\\alga_pattern_control" ++ i ++ "_control" ++ y;
							in = "\\in.kr(" ++ arrayOfZeros_in ++ ");";
							indices = indices_kr;
							scaling = "Select.kr(\\useScaling.ir(0), [out, outScale]);";
							env = "AlgaDynamicEnvGate.kr(\\t_release.tr(0), \\fadeTime.kr(0));";
							env_pattern = env_pattern_kr;
							sampleAndHold = sampleAndHold_kr;
						});

						if(rate == \ar_kr, {
							name = "\\alga_interp_audio" ++ i ++ "_control" ++ y;
							name_pattern = "\\alga_pattern_audio" ++ i ++ "_control" ++ y;
							in = "A2K.kr(\\in.ar(" ++ arrayOfZeros_in ++ "));";
							indices = indices_kr;
							scaling = "Select.kr(\\useScaling.ir(0), [out, outScale]);";
							env = "AlgaDynamicEnvGate.kr(\\t_release.tr(0), \\fadeTime.kr(0));";
							env_pattern = env_pattern_kr;
							sampleAndHold = sampleAndHold_kr;
						});

						if(rate == \kr_ar, {
							name = "\\alga_interp_control" ++ i ++ "_audio" ++ y;
							name_pattern = "\\alga_pattern_control" ++ i ++ "_audio" ++ y;
							in = "K2A.ar(\\in.kr(" ++ arrayOfZeros_in ++ "));";
							indices = indices_ar;
							scaling = "Select.ar(\\useScaling.ir(0), [out, outScale]);";
							env = "AlgaDynamicEnvGate.ar(\\t_release.tr(0), \\fadeTime.kr(0));";
							env_pattern = env_pattern_ar;
							sampleAndHold = sampleAndHold_ar;
						});

						result = "
AlgaSynthDef.new_inner(" ++ name ++ ", {
var in, env, out, outMultiplier, outScale, outs;
in = " ++ in ++ "
out = " ++ indices ++ "
outMultiplier = " ++ multiplier ++ "
outScale = out.algaLinCurve(
\\lowMin.ir(" ++ arrayOfMinusOnes ++ "),
\\lowMax.ir(" ++ arrayOfOnes ++ "),
\\highMin.ir(" ++ arrayOfMinusOnes ++ "),
\\highMax.ir(" ++ arrayOfOnes ++ "),
\\scaleCurve.ir(0)
);
out = " ++ scaling ++ "
env = " ++ env ++ "
out = out * outMultiplier;
out = out * env;
outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ outs ++ "
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false, sampleAccurate:false, makeOutDef:false).algaStore(dir:AlgaStartup.algaSynthDefIO_numberPath);

//Used in patterns (env comes from outside)
AlgaSynthDef.new_inner(" ++ name_pattern ++ ", {
var in, env, out, outMultiplier, outScale, outs;
in = " ++ in ++ "
out = " ++ indices ++ "
outMultiplier = " ++ multiplier ++ "
outScale = out.algaLinCurve(
\\lowMin.ir(" ++ arrayOfMinusOnes ++ "),
\\lowMax.ir(" ++ arrayOfOnes ++ "),
\\highMin.ir(" ++ arrayOfMinusOnes ++ "),
\\highMax.ir(" ++ arrayOfOnes ++ "),
\\scaleCurve.ir(0)
);
out = " ++ scaling ++ "
out = out * outMultiplier;
env = " ++ env_pattern ++ "
env = " ++ sampleAndHold ++ "
out = out * env;
outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ outs ++ "
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false, sampleAccurate:false, makeOutDef:false).algaStore(dir:AlgaStartup.algaSynthDefIO_numberPath);

//Used in patterns + fx (no env)
AlgaSynthDef.new_inner(" ++ name_pattern ++ "_fx, {
var in, out, outMultiplier, outScale;
in = " ++ in ++ "
out = " ++ indices ++ "
outMultiplier = " ++ multiplier ++ "
outScale = out.algaLinCurve(
\\lowMin.ir(" ++ arrayOfMinusOnes ++ "),
\\lowMax.ir(" ++ arrayOfOnes ++ "),
\\highMin.ir(" ++ arrayOfMinusOnes ++ "),
\\highMax.ir(" ++ arrayOfOnes ++ "),
\\scaleCurve.ir(0)
);
out = " ++ scaling ++ "
out = out * outMultiplier;
}, makeFadeEnv:false, sampleAccurate:false, makeOutDef:false).algaStore(dir:AlgaStartup.algaSynthDefIO_numberPath);

//Used in patterns + out (env comes from outside, but it's not forwarded)
AlgaSynthDef.new_inner(" ++ name_pattern ++ "_out, {
var in, env, out, outMultiplier, outScale;
in = " ++ in ++ "
out = " ++ indices ++ "
outMultiplier = " ++ multiplier ++ "
outScale = out.algaLinCurve(
\\lowMin.ir(" ++ arrayOfMinusOnes ++ "),
\\lowMax.ir(" ++ arrayOfOnes ++ "),
\\highMin.ir(" ++ arrayOfMinusOnes ++ "),
\\highMax.ir(" ++ arrayOfOnes ++ "),
\\scaleCurve.ir(0)
);
out = " ++ scaling ++ "
out = out * outMultiplier;
env = " ++ env_pattern ++ "
out = out * env;
}, makeFadeEnv:false, sampleAccurate:false, makeOutDef:false).algaStore(dir:AlgaStartup.algaSynthDefIO_numberPath);
";

						result.interpret;

						//file.write(result ++ "\n");
					});

					alreadyDonePairs.put(currentPair, true);
				});

			});
		});

		("100 %").postln;
		//file.close; Document.open("~/AlgaSynthDefsTest.scd".standardizePath);
	}

	*initAlgaNorm {
		var percentageCounter = 0.0;

		"\n(3/5) Generating the alga_norm definitions...".postln;

		algaMaxIO.do({ | i |

			var result_audio, result_control;
			var arrayOfZeros = "[";

			(percentageCounter.asStringPrec(2) ++ " %").postln;
			percentageCounter = percentageCounter + percentageSplit;

			i = i + 1;

			if(i == 1, {

				result_audio = "AlgaSynthDef.new_inner(\\alga_norm_audio1, {
var args = \\args.ar([0, 0]);
var val = args[0];
var env = args[1];
var out = Sanitize.ar(val / env);
out;
}, makeFadeEnv:true, sampleAccurate:false, makeOutDef:false).algaStore(dir:AlgaStartup.algaSynthDefIO_numberPath);";

				result_control = "AlgaSynthDef.new_inner(\\alga_norm_control1, {
var args = \\args.kr([0, 0]);
var val = args[0];
var env = args[1];
var out = Sanitize.kr(val / env);
out;
}, makeFadeEnv:true, sampleAccurate:false, makeOutDef:false).algaStore(dir:AlgaStartup.algaSynthDefIO_numberPath);";

			}, {

				//Generate [0, 0, 0, ...
				(i + 1).do({ //+ 1 because of the env at last position
					arrayOfZeros = arrayOfZeros ++ "0,";
				});

				//remove trailing coma [0, 0, 0, and enclose in bracket -> [0, 0, 0]
				arrayOfZeros = arrayOfZeros[0..(arrayOfZeros.size - 2)] ++ "]";

				result_audio = "AlgaSynthDef.new_inner(\\alga_norm_audio" ++ i.asString ++ ", {
var args = \\args.ar(" ++ arrayOfZeros ++ ");
var val = args[0.." ++ (i - 1).asString ++ "];
var env = args[" ++ i.asString ++ "];
var out = Sanitize.ar(val / env);
out;
}, makeFadeEnv:true, sampleAccurate:false, makeOutDef:false).algaStore(dir:AlgaStartup.algaSynthDefIO_numberPath);";

				result_control = "AlgaSynthDef.new_inner(\\alga_norm_control" ++ i.asString ++ ", {
var args = \\args.kr(" ++ arrayOfZeros ++ ");
var val = args[0.." ++ (i - 1).asString ++ "];
var env = args[" ++ i.asString ++ "];
var out = Sanitize.kr(val / env);
out;
}, makeFadeEnv:true, sampleAccurate:false, makeOutDef:false).algaStore(dir:AlgaStartup.algaSynthDefIO_numberPath);";

			});

			//Evaluate the generated code
			result_audio.interpret;
			result_control.interpret;

			//result_audio.postln;
			//result_control.postln;

		});

		("100 %").postln;
	}

	*initAlgaMixFades {
		var percentageCounter = 0.0;

		"\n(4/5) Generating the alga_fadeIn / alga_fadeOut definitions...".postln;

		algaMaxIO.do({ | i |
			var fadein_kr, fadein_ar;
			var fadeout_kr, fadeout_ar;
			var fade_patternOutEnv_kr, fade_patternOutEnv_ar;

			(percentageCounter.asStringPrec(2) ++ " %").postln;
			percentageCounter = percentageCounter + percentageSplit;

			i = i + 1;

			fadein_kr = "AlgaSynthDef.new_inner(\\alga_fadeIn_control" ++ i.asString ++ ", { | curve = \\lin |
var val = Array.newClear(" ++ (i + 1) ++ ");
" ++ i ++ ".do({ | i |
val[i] = 0;
});
val[" ++ i ++ "] = EnvGen.kr(Env([1, 0], #[1], curve), \\gate.kr(1), 1.0, 0.0, \\fadeTime.kr(0), Done.freeSelf);
val;
}, makeFadeEnv:false, sampleAccurate:false, makeOutDef:false).algaStore(dir:AlgaStartup.algaSynthDefIO_numberPath);";

			fadein_ar = "AlgaSynthDef.new_inner(\\alga_fadeIn_audio" ++ i.asString ++ ", { | curve = \\sin |
var val = Array.newClear(" ++ (i + 1) ++ ");
var zero = DC.ar(0);
" ++ i ++ ".do({ | i |
val[i] = zero;
});
val[" ++ i ++ "] = EnvGen.ar(Env([1, 0], #[1], curve), \\gate.kr(1), 1.0, 0.0, \\fadeTime.kr(0), Done.freeSelf);
val;
}, makeFadeEnv:false, sampleAccurate:false, makeOutDef:false).algaStore(dir:AlgaStartup.algaSynthDefIO_numberPath);";

			fadeout_kr = "AlgaSynthDef.new_inner(\\alga_fadeOut_control" ++ i.asString ++ ", { | curve = \\lin |
var val = Array.newClear(" ++ (i + 1) ++ ");
" ++ i ++ ".do({ | i |
val[i] = 0;
});
val[" ++ i ++ "] = EnvGen.kr(Env([0, 1], #[1], curve), \\gate.kr(1), 1.0, 0.0, \\fadeTime.kr(0), Done.freeSelf);
val;
}, makeFadeEnv:false, sampleAccurate:false, makeOutDef:false).algaStore(dir:AlgaStartup.algaSynthDefIO_numberPath);";

			fadeout_ar = "AlgaSynthDef.new_inner(\\alga_fadeOut_audio" ++ i.asString ++ ", { | curve = \\sin |
var val = Array.newClear(" ++ (i + 1) ++ ");
var zero = DC.ar(0);
" ++ i ++ ".do({ | i |
val[i] = zero;
});
val[" ++ i ++ "] = EnvGen.ar(Env([0, 1], #[1], curve), \\gate.kr(1), 1.0, 0.0, \\fadeTime.kr(0), Done.freeSelf);
val;
}, makeFadeEnv:false, sampleAccurate:false, makeOutDef:false).algaStore(dir:AlgaStartup.algaSynthDefIO_numberPath);";

			fade_patternOutEnv_kr = "AlgaSynthDef.new_inner_inner(\\alga_patternOutEnv_control" ++ i.asString ++ ", {
var env = AlgaDynamicEnvGate.kr(\\t_release.tr(0), \\fadeTime.kr(0));
var val = Array.newClear(" ++ (i + 1) ++ ");
Out.kr(\\env_out.ir(0), env);
" ++ i ++ ".do({ | i |
val[i] = 0;
});
val[" ++ i ++ "] = env;
val;
}, makeFadeEnv:false, sampleAccurate:false, makeOutDef:false, ignoreOutWarning:true).algaStore(dir:AlgaStartup.algaSynthDefIO_numberPath);";

			fade_patternOutEnv_ar = "AlgaSynthDef.new_inner_inner(\\alga_patternOutEnv_audio" ++ i.asString ++ ", {
var env = AlgaDynamicEnvGate.ar(\\t_release.tr(0), \\fadeTime.kr(0));
var val = Array.newClear(" ++ (i + 1) ++ ");
var zero = DC.ar(0);
Out.ar(\\env_out.ir(0), env);
" ++ i ++ ".do({ | i |
val[i] = zero;
});
val[" ++ i ++ "] = env;
}, makeFadeEnv:false, sampleAccurate:false, makeOutDef:false, ignoreOutWarning:true).algaStore(dir:AlgaStartup.algaSynthDefIO_numberPath);";

			fadein_kr.interpret;
			fadein_ar.interpret;
			fadeout_kr.interpret;
			fadeout_ar.interpret;
			fade_patternOutEnv_kr.interpret;
			fade_patternOutEnv_ar.interpret;
		});

		("100 %").postln;
	}

	*initAlgaPatternInterp {
		var result;

		"\n(5/5) Generating the alga_pattern_interp definitions...".postln;

		result = "
AlgaSynthDef.new_inner(\\alga_pattern_interp_env_audio, {
AlgaDynamicEnvGate.ar(\\t_release.tr(0), \\fadeTime.kr(0));
}, makeFadeEnv:false, sampleAccurate:false, makeOutDef:false).algaStore(dir:AlgaStartup.algaSynthDefIO_numberPath);

AlgaSynthDef.new_inner(\\alga_pattern_interp_env_control, {
AlgaDynamicEnvGate.kr(\\t_release.tr(0), \\fadeTime.kr(0));
}, makeFadeEnv:false, sampleAccurate:false, makeOutDef:false).algaStore(dir:AlgaStartup.algaSynthDefIO_numberPath);
";
		result.interpret;

		("100 %").postln;
	}
}

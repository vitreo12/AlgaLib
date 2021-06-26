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

AlgaStartup {
	classvar <algaMaxIO = 8;

	classvar <algaSynthDefPath;
	classvar <algaSynthDefIOPath;

	*initClass {
		algaSynthDefPath = SynthDef.synthDefDir ++ "AlgaSynthDefs";
		algaSynthDefIOPath = (algaSynthDefPath ++ "/IO_" ++ algaMaxIO ++ "/").asString;
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
		algaSynthDefIOPath = (algaSynthDefPath ++ "/IO_" ++ algaMaxIO ++ "/").asString;
	}

	*initSynthDefs {
		var folderDeleted = true;

		if(File.exists(algaSynthDefPath), {
			folderDeleted = File.deleteAll(algaSynthDefPath);
		});

		if(folderDeleted, {
			var algaSynthDefFolderCreated = File.mkdir(algaSynthDefPath);

			if(algaSynthDefFolderCreated, {
				var algaSynthDefIOFolderCreated = File.mkdir(algaSynthDefIOPath);

				if(algaSynthDefIOFolderCreated, {
					("-> Generating all Alga SynthDefs for a max of " ++ algaMaxIO ++ " I/O count, it may take a while...").postln;
					this.initAlgaPlay;
					this.initAlgaInterp;
					this.initAlgaNorm;
					this.initAlgaMixFades;
					this.initAlgaPatternInterp;
					"-> Done!".postln;
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

		algaMaxIO.do({ | i |
			var arrayOfZeros_in, arrayOfIndices;

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
}, makeFadeEnv:false).algaStore(dir:AlgaStartup.algaSynthDefIOPath);
";

					sdef.interpret;
				});
			});
		});
	}

	*initAlgaInterp {

		var alreadyDonePairs = IdentityDictionary.new(algaMaxIO);

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
						var name_pattern, env_pattern;

						if(rate == \ar_ar, {
							name = "\\alga_interp_audio" ++ i ++ "_audio" ++ y;
							name_pattern = "\\alga_pattern_audio" ++ i ++ "_audio" ++ y;
							in = "\\in.ar(" ++ arrayOfZeros_in ++ ");";
							indices = indices_ar;
							scaling = "Select.ar(\\useScaling.ir(0), [out, outScale]);";
							env = "AlgaDynamicEnvGate.ar(\\t_release.tr(0), \\fadeTime.kr(0));";
							env_pattern = env_pattern_ar;
						});

						if(rate == \kr_kr, {
							name = "\\alga_interp_control" ++ i ++ "_control" ++ y;
							name_pattern = "\\alga_pattern_control" ++ i ++ "_control" ++ y;
							in = "\\in.kr(" ++ arrayOfZeros_in ++ ");";
							indices = indices_kr;
							scaling = "Select.kr(\\useScaling.ir(0), [out, outScale]);";
							env = "AlgaDynamicEnvGate.kr(\\t_release.tr(0), \\fadeTime.kr(0));";
							env_pattern = env_pattern_kr;
						});

						if(rate == \ar_kr, {
							name = "\\alga_interp_audio" ++ i ++ "_control" ++ y;
							name_pattern = "\\alga_pattern_audio" ++ i ++ "_control" ++ y;
							in = "A2K.kr(\\in.ar(" ++ arrayOfZeros_in ++ "));";
							indices = indices_kr;
							scaling = "Select.kr(\\useScaling.ir(0), [out, outScale]);";
							env = "AlgaDynamicEnvGate.kr(\\t_release.tr(0), \\fadeTime.kr(0));";
							env_pattern = env_pattern_kr;
						});

						if(rate == \kr_ar, {
							name = "\\alga_interp_control" ++ i ++ "_audio" ++ y;
							name_pattern = "\\alga_pattern_control" ++ i ++ "_audio" ++ y;
							in = "K2A.ar(\\in.kr(" ++ arrayOfZeros_in ++ "));";
							indices = indices_ar;
							scaling = "Select.ar(\\useScaling.ir(0), [out, outScale]);";
							env = "AlgaDynamicEnvGate.ar(\\t_release.tr(0), \\fadeTime.kr(0));";
							env_pattern = env_pattern_ar;
						});

						result = "
AlgaSynthDef.new_inner(" ++ name ++ ", { | scaleCurve = 0 |
var in, env, out, outMultiplier, outScale, outs;
in = " ++ in ++ "
out = " ++ indices ++ "
outMultiplier = " ++ multiplier ++ "
outScale = out.lincurve(
\\lowMin.ir(" ++ arrayOfMinusOnes ++ "),
\\lowMax.ir(" ++ arrayOfOnes ++ "),
\\highMin.ir(" ++ arrayOfMinusOnes ++ "),
\\highMax.ir(" ++ arrayOfOnes ++ "),
scaleCurve,
);
out = " ++ scaling ++ "
env = " ++ env ++ "
out = out * outMultiplier;
out = out * env;
outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ outs ++ "
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).algaStore(dir:AlgaStartup.algaSynthDefIOPath);

//Env comes from outside. Can be sampled and hold too.
AlgaSynthDef.new_inner(" ++ name_pattern ++ ", { | scaleCurve = 0 |
var in, env, out, outMultiplier, outScale;
in = " ++ in ++ "
out = " ++ indices ++ "
outMultiplier = " ++ multiplier ++ "
outScale = out.lincurve(
\\lowMin.ir(" ++ arrayOfMinusOnes ++ "),
\\lowMax.ir(" ++ arrayOfOnes ++ "),
\\highMin.ir(" ++ arrayOfMinusOnes ++ "),
\\highMax.ir(" ++ arrayOfOnes ++ "),
scaleCurve,
);
out = " ++ scaling ++ "
out = out * outMultiplier;
env = " ++ env_pattern ++ "
out = out * env;
out;
}, makeFadeEnv:false).algaStore(dir:AlgaStartup.algaSynthDefIOPath);
";

						result.interpret;

						//file.write(result ++ "\n");
					});

					alreadyDonePairs.put(currentPair, true);
				});

			});

		});

		//file.close; Document.open("~/AlgaSynthDefsTest.scd".standardizePath);
	}

	*initAlgaNorm {
		algaMaxIO.do({ | i |

			var result_audio, result_control;
			var arrayOfZeros = "[";

			i = i + 1;

			if(i == 1, {

				result_audio = "AlgaSynthDef.new_inner(\\alga_norm_audio1, {
var args = \\args.ar([0, 0]);
var val = args[0];
var env = args[1];
var out = Sanitize.ar(val / env);
out;
}, makeFadeEnv:true).algaStore(dir:AlgaStartup.algaSynthDefIOPath);";

				result_control = "AlgaSynthDef.new_inner(\\alga_norm_control1, {
var args = \\args.kr([0, 0]);
var val = args[0];
var env = args[1];
var out = Sanitize.kr(val / env);
out;
}, makeFadeEnv:true).algaStore(dir:AlgaStartup.algaSynthDefIOPath);";

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
}, makeFadeEnv:true).algaStore(dir:AlgaStartup.algaSynthDefIOPath);";

				result_control = "AlgaSynthDef.new_inner(\\alga_norm_control" ++ i.asString ++ ", {
var args = \\args.kr(" ++ arrayOfZeros ++ ");
var val = args[0.." ++ (i - 1).asString ++ "];
var env = args[" ++ i.asString ++ "];
var out = Sanitize.kr(val / env);
out;
}, makeFadeEnv:true).algaStore(dir:AlgaStartup.algaSynthDefIOPath);";

			});

			//Evaluate the generated code
			result_audio.interpret;
			result_control.interpret;

			//result_audio.postln;
			//result_control.postln;

		});
	}

	*initAlgaMixFades {
		algaMaxIO.do({ | i |
			var fadein_kr, fadein_ar;
			var fadeout_kr, fadeout_ar;

			i = i + 1;

			fadein_kr = "AlgaSynthDef.new_inner(\\alga_fadeIn_control" ++ i.asString ++ ", { | curve = \\lin |
var val = Array.newClear(" ++ (i + 1) ++ ");
" ++ i ++ ".do({ | i |
val[i] = 0;
});
val[" ++ i ++ "] = EnvGen.kr(Env([1, 0], #[1], curve), \\gate.kr(1), 1.0, 0.0, \\fadeTime.kr(0), Done.freeSelf);
val;
}, makeFadeEnv:false).algaStore(dir:AlgaStartup.algaSynthDefIOPath);";

			fadein_ar = "AlgaSynthDef.new_inner(\\alga_fadeIn_audio" ++ i.asString ++ ", { | curve = \\sin |
var val = Array.newClear(" ++ (i + 1) ++ ");
" ++ i ++ ".do({ | i |
val[i] = DC.ar(0);
});
val[" ++ i ++ "] = EnvGen.ar(Env([1, 0], #[1], curve), \\gate.kr(1), 1.0, 0.0, \\fadeTime.kr(0), Done.freeSelf);
val;
}, makeFadeEnv:false).algaStore(dir:AlgaStartup.algaSynthDefIOPath);";

			fadeout_kr = "AlgaSynthDef.new_inner(\\alga_fadeOut_control" ++ i.asString ++ ", { | curve = \\lin |
var val = Array.newClear(" ++ (i + 1) ++ ");
" ++ i ++ ".do({ | i |
val[i] = 0;
});
val[" ++ i ++ "] = EnvGen.kr(Env([0, 1], #[1], curve), \\gate.kr(1), 1.0, 0.0, \\fadeTime.kr(0), Done.freeSelf);
val;
}, makeFadeEnv:false).algaStore(dir:AlgaStartup.algaSynthDefIOPath);";

			fadeout_ar = "AlgaSynthDef.new_inner(\\alga_fadeOut_audio" ++ i.asString ++ ", { | curve = \\sin |
var val = Array.newClear(" ++ (i + 1) ++ ");
" ++ i ++ ".do({ | i |
val[i] = DC.ar(0);
});
val[" ++ i ++ "] = EnvGen.ar(Env([0, 1], #[1], curve), \\gate.kr(1), 1.0, 0.0, \\fadeTime.kr(0), Done.freeSelf);
val;
}, makeFadeEnv:false).algaStore(dir:AlgaStartup.algaSynthDefIOPath);";

			fadein_kr.interpret;
			fadein_ar.interpret;
			fadeout_kr.interpret;
			fadeout_ar.interpret;
		});
	}

	*initAlgaPatternInterp {
		var result = "
AlgaSynthDef.new_inner(\\alga_pattern_interp_env_audio, {
AlgaDynamicEnvGate.ar(\\t_release.tr(0), \\fadeTime.kr(0));
}, makeFadeEnv:false).algaStore(dir:AlgaStartup.algaSynthDefIOPath);

AlgaSynthDef.new_inner(\\alga_pattern_interp_env_control, {
AlgaDynamicEnvGate.ar(\\t_release.tr(0), \\fadeTime.kr(0));
}, makeFadeEnv:false).algaStore(dir:AlgaStartup.algaSynthDefIOPath);
";
		result.interpret;
	}
}

AlgaStartup {
	classvar <algaMaxIO = 16;

	classvar <algaSynthDefPath;
	classvar <algaSynthDefIOPath;

	*initClass {
		algaSynthDefPath = SynthDef.synthDefDir ++ "AlgaSynthDefs";
		algaSynthDefIOPath = (algaSynthDefPath ++ "/IO_" ++ algaMaxIO).asString;
	}

	*algaMaxIO_ { | val |
		if(val.isNumber.not, { "AlgaStartup: algaMaxIO must be a number".error; ^this });
		algaMaxIO = val;
		this.updateAlgaSynthDefIOPath;
	}

	*updateAlgaSynthDefIOPath {
		algaSynthDefIOPath = (algaSynthDefPath ++ "/IO_" ++ algaMaxIO).asString;
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
					"-> Creating all Alga SynthDefs, it may take a while...".postln;
					this.initAlgaPlay;
					this.initAlgaInterp;
					this.initAlgaNorm;
					this.initAlgaMixFades;
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

				if(y <= i, { //only y <= i
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

						sdef = "
AlgaSynthDef(\\alga_play_" ++ i ++ "_" ++ y ++ ", {
var input = \\in.ar(" ++ arrayOfZeros_in ++ ");
input = Select.ar(\\indices.ir(" ++ arrayOfIndices ++ "), input);
Out.ar(\\out.ir(0), input * AlgaEnvGate.ar)
}).writeDefFile(AlgaStartup.algaSynthDefIOPath);
";

						sdef.interpret;
					});
				});
			});
		});
	}

	*initAlgaInterp {

		var alreadyDonePairs = IdentityDictionary.new;

		//var file = File("~/test.txt".standardizePath,"w");

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
				var currentPair, isAlreadyDone;

				y = y + 1;

				if(y == 1, {
					arrayOfIndices = "0";
				}, {
					arrayOfIndices = "[";

					y.do({ | num |
						arrayOfIndices = arrayOfIndices ++ num.asString ++ ",";
					});

					arrayOfIndices = arrayOfIndices[0..(arrayOfIndices.size - 2)] ++ "]";
				});

				currentPair = [i, y];
				isAlreadyDone = alreadyDonePairs[currentPair];

				//Not done already
				if(isAlreadyDone != true, {

					var result_audio_audio, result_control_control, result_audio_control, result_control_audio;

					if(i >= y, {

						if(i == 1, {
							//ar -> ar
							result_audio_audio = "
AlgaSynthDef(\\alga_interp_audio" ++ i ++ "_audio" ++ y ++ ", { | curve = \\sin |
var in = \\in.ar(0);
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: curve);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

							//kr -> kr
							result_control_control = "
AlgaSynthDef(\\alga_interp_control" ++ i ++ "_control" ++ y ++ ", { | curve = \\lin |
var in = \\in.kr(0);
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: curve);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

							//ar -> kr
							result_audio_control = "
AlgaSynthDef(\\alga_interp_audio" ++ i ++ "_control" ++ y ++ ", { | curve = \\lin |
var in = A2K.kr(\\in.ar(0));
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: curve);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

							//kr -> ar
							result_control_audio = "
AlgaSynthDef(\\alga_interp_control" ++ i ++ "_audio" ++ y ++ ", { | curve = \\sin |
var in = K2A.ar(\\in.kr(0));
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: curve);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

						}, {
							var out;
							if(y == 1, { out = "out" }, { out = "out[i]" });

							//ar -> ar
							result_audio_audio = "
AlgaSynthDef(\\alga_interp_audio" ++ i ++ "_audio" ++ y ++ ", { | curve = \\sin |
var in = \\in.ar(" ++ arrayOfZeros_in ++ ");
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: curve);
var outs = Array.newClear(" ++ (y + 1) ++ ");
var out = Select.ar(\\indices.ir(" ++ arrayOfIndices ++ "), in);
out = out * env;
" ++ y ++ ".do({
arg i;
outs[i] = " ++ out ++ ";
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

							//kr -> kr
							result_control_control = "
AlgaSynthDef(\\alga_interp_control" ++ i ++ "_control" ++ y ++ ", { | curve = \\lin |
var in = \\in.kr(" ++ arrayOfZeros_in ++ ");
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: curve);
var outs = Array.newClear(" ++ (y + 1) ++ ");
var out = Select.kr(\\indices.ir(" ++ arrayOfIndices ++ "), in);
out = out * env;
" ++ y ++ ".do({
arg i;
outs[i] = " ++ out ++ ";
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

							//ar -> kr
							result_audio_control = "
AlgaSynthDef(\\alga_interp_audio" ++ i ++ "_control" ++ y ++ ", { | curve = \\lin |
var in = A2K.kr(\\in.ar(" ++ arrayOfZeros_in ++ "));
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: curve);
var outs = Array.newClear(" ++ (y + 1) ++ ");
var out = Select.kr(\\indices.ir(" ++ arrayOfIndices ++ "), in);
out = out * env;
" ++ y ++ ".do({
arg i;
outs[i] = " ++ out ++ ";
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

							//kr -> ar
							result_control_audio = "
AlgaSynthDef(\\alga_interp_control" ++ i ++ "_audio" ++ y ++ ", { | curve = \\sin |
var in = K2A.ar(\\in.kr(" ++ arrayOfZeros_in ++ "));
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: curve);
var outs = Array.newClear(" ++ (y + 1) ++ ");
var out = Select.ar(\\indices.ir(" ++ arrayOfIndices ++ "), in);
out = out * env;
" ++ y ++ ".do({
arg i;
outs[i] = " ++ out ++ ";
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

						});

					});

					if(i < y, {

						if(i == 1, {

							//ar -> ar
							result_audio_audio = "
AlgaSynthDef(\\alga_interp_audio" ++ i ++ "_audio" ++ y ++ ", { | curve = \\sin |
var in = \\in.ar(0);
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: curve);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

							//kr -> kr
							result_control_control = "
AlgaSynthDef(\\alga_interp_control" ++ i ++ "_control" ++ y ++ ", { | curve = \\lin |
var in = \\in.kr(0);
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: curve);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

							//ar -> kr
							result_audio_control = "
AlgaSynthDef(\\alga_interp_audio" ++ i ++ "_control" ++ y ++ ", { | curve = \\lin |
var in = A2K.kr(\\in.ar(0));
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: curve);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

							//kr -> ar
							result_control_audio = "
AlgaSynthDef(\\alga_interp_control" ++ i ++ "_audio" ++ y ++ ", { | curve = \\sin |
var in = K2A.ar(\\in.kr(0));
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: curve);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

						}, {

							//var mod_i = i % " ++ i ++ ";
							//outs[i] = out[mod_i];

							var out;
							if(y == 1, { out = "out" }, { out = "out[i]" });

							//ar -> ar
							result_audio_audio = "
AlgaSynthDef(\\alga_interp_audio" ++ i ++ "_audio" ++ y ++ ", { | curve = \\sin |
var in = \\in.ar(" ++ arrayOfZeros_in ++ ");
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: curve);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
out = Select.ar(\\indices.ir(" ++ arrayOfIndices ++ "), out);
" ++ y ++ ".do({
arg i;
outs[i] = " ++ out ++ ";
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

							//kr -> kr
							result_control_control = "
AlgaSynthDef(\\alga_interp_control" ++ i ++ "_control" ++ y ++ ", { | curve = \\lin |
var in = \\in.kr(" ++ arrayOfZeros_in ++ ");
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: curve);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
out = Select.kr(\\indices.ir(" ++ arrayOfIndices ++ "), out);
" ++ y ++ ".do({
arg i;
outs[i] = " ++ out ++ ";
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

							//ar -> kr
							result_audio_control = "
AlgaSynthDef(\\alga_interp_audio" ++ i ++ "_control" ++ y ++ ", { | curve = \\lin |
var in = A2K.kr(\\in.ar(" ++ arrayOfZeros_in ++ "));
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: curve);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
out = Select.kr(\\indices.ir(" ++ arrayOfIndices ++ "), out);
" ++ y ++ ".do({
arg i;
outs[i] = " ++ out ++ ";
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

							//kr -> ar
							result_control_audio = "
AlgaSynthDef(\\alga_interp_control" ++ i ++ "_audio" ++ y ++ ", { | curve = \\sin |
var in = K2A.ar(\\in.kr(" ++ arrayOfZeros_in ++ "));
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: curve);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
out = Select.ar(\\indices.ir(" ++ arrayOfIndices ++ "), out);
" ++ y ++ ".do({
arg i;
outs[i] = " ++ out ++ ";
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

						});

					});

					alreadyDonePairs.put(currentPair, true);

					result_audio_audio.interpret;
					result_control_control.interpret;
					result_audio_control.interpret;
					result_control_audio.interpret;

					/*
					file.write(result_audio_audio ++ "\n");
					file.write(result_control_control ++ "\n");
					file.write(result_audio_control ++ "\n");
					file.write(result_control_audio ++ "\n");
					*/

					/*
					result_audio_audio.postln;
					result_control_control.postln;
					result_audio_control.postln;
					result_control_audio.postln;
					*/

				});

			});

		});

		//file.close;
	}

	*initAlgaNorm {
		algaMaxIO.do({ | i |

			var result_audio, result_control;
			var arrayOfZeros = "[";

			i = i + 1;

			if(i == 1, {

				result_audio = "AlgaSynthDef(\\alga_norm_audio1, {
var args = \\args.ar([0, 0]);
var val = args[0];
var env = args[1];
var out = Sanitize.ar(val / env);
out;
}).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

				result_control = "AlgaSynthDef(\\alga_norm_control1, {
var args = \\args.kr([0, 0]);
var val = args[0];
var env = args[1];
var out = Sanitize.kr(val / env);
out;
}).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

			}, {

				//Generate [0, 0, 0, ...
				(i + 1).do({ //+ 1 because of the env at last position
					arrayOfZeros = arrayOfZeros ++ "0,";
				});

				//remove trailing coma [0, 0, 0, and enclose in bracket -> [0, 0, 0]
				arrayOfZeros = arrayOfZeros[0..(arrayOfZeros.size - 2)] ++ "]";

				result_audio = "AlgaSynthDef(\\alga_norm_audio" ++ i.asString ++ ", {
var args = \\args.ar(" ++ arrayOfZeros ++ ");
var val = args[0.." ++ (i - 1).asString ++ "];
var env = args[" ++ i.asString ++ "];
var out = Sanitize.ar(val / env);
out;
}).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

				result_control = "AlgaSynthDef(\\alga_norm_control" ++ i.asString ++ ", {
var args = \\args.kr(" ++ arrayOfZeros ++ ");
var val = args[0.." ++ (i - 1).asString ++ "];
var env = args[" ++ i.asString ++ "];
var out = Sanitize.kr(val / env);
out;
}).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

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

			fadein_kr = "AlgaSynthDef(\\alga_fadeIn_control" ++ i.asString ++ ", { | curve = \\lin |
var val = Array.newClear(" ++ (i + 1) ++ ");
" ++ i ++ ".do({ | i |
val[i] = 0;
});
val[" ++ i ++ "] = EnvGen.kr(Env([1, 0], #[1], curve), \\gate.kr(1), 1.0, 0.0, \\fadeTime.kr(0), Done.freeSelf);
val;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

			fadein_ar = "AlgaSynthDef(\\alga_fadeIn_audio" ++ i.asString ++ ", { | curve = \\sin |
var val = Array.newClear(" ++ (i + 1) ++ ");
" ++ i ++ ".do({ | i |
val[i] = DC.ar(0);
});
val[" ++ i ++ "] = EnvGen.ar(Env([1, 0], #[1], curve), \\gate.kr(1), 1.0, 0.0, \\fadeTime.kr(0), Done.freeSelf);
val;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

			fadeout_kr = "AlgaSynthDef(\\alga_fadeOut_control" ++ i.asString ++ ", { | curve = \\lin |
var val = Array.newClear(" ++ (i + 1) ++ ");
" ++ i ++ ".do({ | i |
val[i] = 0;
});
val[" ++ i ++ "] = EnvGen.kr(Env([0, 1], #[1], curve), \\gate.kr(1), 1.0, 0.0, \\fadeTime.kr(0), Done.freeSelf);
val;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

			fadeout_ar = "AlgaSynthDef(\\alga_fadeOut_audio" ++ i.asString ++ ", { | curve = \\sin |
var val = Array.newClear(" ++ (i + 1) ++ ");
" ++ i ++ ".do({ | i |
val[i] = DC.ar(0);
});
val[" ++ i ++ "] = EnvGen.ar(Env([0, 1], #[1], curve), \\gate.kr(1), 1.0, 0.0, \\fadeTime.kr(0), Done.freeSelf);
val;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

			fadein_kr.interpret;
			fadein_ar.interpret;
			fadeout_kr.interpret;
			fadeout_ar.interpret;
		});
	}
}

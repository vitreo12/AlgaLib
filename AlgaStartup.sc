AlgaStartup {

	classvar <algaMaxIO = 16;

	*initAlgaPlay { | server |

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
}).add;
";

						sdef.interpret;
					});
				});
			});
		});
	}

	*initAlgaInterp { | server |

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
AlgaSynthDef(\\algaInterp_audio" ++ i ++ "_audio" ++ y ++ ", {
var in = \\in.ar(0);
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: \\sin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//kr -> kr
							result_control_control = "
AlgaSynthDef(\\algaInterp_control" ++ i ++ "_control" ++ y ++ ", {
var in = \\in.kr(0);
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: \\lin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//ar -> kr
							result_audio_control = "
AlgaSynthDef(\\algaInterp_audio" ++ i ++ "_control" ++ y ++ ", {
var in = A2K.kr(\\in.ar(0));
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: \\lin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//kr -> ar
							result_control_audio = "
AlgaSynthDef(\\algaInterp_control" ++ i ++ "_audio" ++ y ++ ", {
var in = K2A.ar(\\in.kr(0));
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: \\sin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

						}, {
							var out;
							if(y == 1, { out = "out" }, { out = "out[i]" });

							//ar -> ar
							result_audio_audio = "
AlgaSynthDef(\\algaInterp_audio" ++ i ++ "_audio" ++ y ++ ", {
var in = \\in.ar(" ++ arrayOfZeros_in ++ ");
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: \\sin);
var outs = Array.newClear(" ++ (y + 1) ++ ");
var out = Select.ar(\\indices.ir(" ++ arrayOfIndices ++ "), in);
out = out * env;
" ++ y ++ ".do({
arg i;
outs[i] = " ++ out ++ ";
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//kr -> kr
							result_control_control = "
AlgaSynthDef(\\algaInterp_control" ++ i ++ "_control" ++ y ++ ", {
var in = \\in.kr(" ++ arrayOfZeros_in ++ ");
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: \\lin);
var outs = Array.newClear(" ++ (y + 1) ++ ");
var out = Select.kr(\\indices.ir(" ++ arrayOfIndices ++ "), in);
out = out * env;
" ++ y ++ ".do({
arg i;
outs[i] = " ++ out ++ ";
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//ar -> kr
							result_audio_control = "
AlgaSynthDef(\\algaInterp_audio" ++ i ++ "_control" ++ y ++ ", {
var in = A2K.kr(\\in.ar(" ++ arrayOfZeros_in ++ "));
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: \\lin);
var outs = Array.newClear(" ++ (y + 1) ++ ");
var out = Select.kr(\\indices.ir(" ++ arrayOfIndices ++ "), in);
out = out * env;
" ++ y ++ ".do({
arg i;
outs[i] = " ++ out ++ ";
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//kr -> ar
							result_control_audio = "
AlgaSynthDef(\\algaInterp_control" ++ i ++ "_audio" ++ y ++ ", {
var in = K2A.ar(\\in.kr(" ++ arrayOfZeros_in ++ "));
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: \\sin);
var outs = Array.newClear(" ++ (y + 1) ++ ");
var out = Select.ar(\\indices.ir(" ++ arrayOfIndices ++ "), in);
out = out * env;
" ++ y ++ ".do({
arg i;
outs[i] = " ++ out ++ ";
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

						});

					});

					if(i < y, {

						if(i == 1, {

							//ar -> ar
							result_audio_audio = "
AlgaSynthDef(\\algaInterp_audio" ++ i ++ "_audio" ++ y ++ ", {
var in = \\in.ar(0);
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: \\sin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//kr -> kr
							result_control_control = "
AlgaSynthDef(\\algaInterp_control" ++ i ++ "_control" ++ y ++ ", {
var in = \\in.kr(0);
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: \\lin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//ar -> kr
							result_audio_control = "
AlgaSynthDef(\\algaInterp_audio" ++ i ++ "_control" ++ y ++ ", {
var in = A2K.kr(\\in.ar(0));
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: \\lin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//kr -> ar
							result_control_audio = "
AlgaSynthDef(\\algaInterp_control" ++ i ++ "_audio" ++ y ++ ", {
var in = K2A.ar(\\in.kr(0));
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: \\sin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

						}, {

							//var mod_i = i % " ++ i ++ ";
							//outs[i] = out[mod_i];

							var out;
							if(y == 1, { out = "out" }, { out = "out[i]" });

							//ar -> ar
							result_audio_audio = "
AlgaSynthDef(\\algaInterp_audio" ++ i ++ "_audio" ++ y ++ ", {
var in = \\in.ar(" ++ arrayOfZeros_in ++ ");
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: \\sin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
out = Select.ar(\\indices.ir(" ++ arrayOfIndices ++ "), out);
" ++ y ++ ".do({
arg i;
outs[i] = " ++ out ++ ";
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//kr -> kr
							result_control_control = "
AlgaSynthDef(\\algaInterp_control" ++ i ++ "_control" ++ y ++ ", {
var in = \\in.kr(" ++ arrayOfZeros_in ++ ");
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: \\lin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
out = Select.kr(\\indices.ir(" ++ arrayOfIndices ++ "), out);
" ++ y ++ ".do({
arg i;
outs[i] = " ++ out ++ ";
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//ar -> kr
							result_audio_control = "
AlgaSynthDef(\\algaInterp_audio" ++ i ++ "_control" ++ y ++ ", {
var in = A2K.kr(\\in.ar(" ++ arrayOfZeros_in ++ "));
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: \\lin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
out = Select.kr(\\indices.ir(" ++ arrayOfIndices ++ "), out);
" ++ y ++ ".do({
arg i;
outs[i] = " ++ out ++ ";
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//kr -> ar
							result_control_audio = "
AlgaSynthDef(\\algaInterp_control" ++ i ++ "_audio" ++ y ++ ", {
var in = K2A.ar(\\in.kr(" ++ arrayOfZeros_in ++ "));
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: \\sin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
out = Select.ar(\\indices.ir(" ++ arrayOfIndices ++ "), out);
" ++ y ++ ".do({
arg i;
outs[i] = " ++ out ++ ";
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

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

	*initAlgaNorm { | server |
		algaMaxIO.do({ | i |

			var result_audio, result_control;
			var arrayOfZeros = "[";

			i = i + 1;

			if(i == 1, {

				result_audio = "AlgaSynthDef(\\algaNorm_audio1, {
var args = \\args.ar([0, 0]);
var val = args[0];
var env = args[1];
var out = Sanitize.ar(val / env);
out;
}).add;";

				result_control = "AlgaSynthDef(\\algaNorm_control1, {
var args = \\args.kr([0, 0]);
var val = args[0];
var env = args[1];
var out = Sanitize.kr(val / env);
out;
}).add;";

			}, {

				//Generate [0, 0, 0, ...
				(i + 1).do({ //+ 1 because of the env at last position
					arrayOfZeros = arrayOfZeros ++ "0,";
				});

				//remove trailing coma [0, 0, 0, and enclose in bracket -> [0, 0, 0]
				arrayOfZeros = arrayOfZeros[0..(arrayOfZeros.size - 2)] ++ "]";

				result_audio = "AlgaSynthDef(\\algaNorm_audio" ++ i.asString ++ ", {
var args = \\args.ar(" ++ arrayOfZeros ++ ");
var val = args[0.." ++ (i - 1).asString ++ "];
var env = args[" ++ i.asString ++ "];
var out = Sanitize.ar(val / env);
out;
}).add;";

				result_control = "AlgaSynthDef(\\algaNorm_control" ++ i.asString ++ ", {
var args = \\args.kr(" ++ arrayOfZeros ++ ");
var val = args[0.." ++ (i - 1).asString ++ "];
var env = args[" ++ i.asString ++ "];
var out = Sanitize.kr(val / env);
out;
}).add;";

			});

			//Evaluate the generated code
			result_audio.interpret;
			result_control.interpret;

			//result_audio.postln;
			//result_control.postln;

		});
	}

	*initSynthDefs { | server |
		this.initAlgaPlay(server);
		this.initAlgaInterp(server);
		this.initAlgaNorm(server);
	}

}

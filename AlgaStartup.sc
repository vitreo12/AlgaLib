AlgaStartup {

	*initSynthDefs {
		arg server = Server.local;

		var alreadyDonePairs = Dictionary.new;

		16.do({
			arg i;

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

			16.do({
				arg y;

				var currentPair, isAlreadyDone;

				y = y + 1;

				currentPair = [i, y];
				isAlreadyDone = alreadyDonePairs[currentPair];

				//Not done already
				if(isAlreadyDone != true, {

					var result_ar_ar, result_kr_kr, result_ar_kr, result_kr_ar;

					if(i >= y, {

						if(i == 1, {
							//ar -> ar
							result_ar_ar = "
ProxySynthDef(\\proxyIn_ar" ++ i ++ "_ar" ++ y ++ ", {
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
							result_kr_kr = "
ProxySynthDef(\\proxyIn_kr" ++ i ++ "_kr" ++ y ++ ", {
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
							result_ar_kr = "
ProxySynthDef(\\proxyIn_ar" ++ i ++ "_kr" ++ y ++ ", {
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
							result_kr_ar = "
ProxySynthDef(\\proxyIn_kr" ++ i ++ "_ar" ++ y ++ ", {
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
							//ar -> ar
							result_ar_ar = "
ProxySynthDef(\\proxyIn_ar" ++ i ++ "_ar" ++ y ++ ", {
var in = \\in.ar(" ++ arrayOfZeros_in ++ ");
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: \\sin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out[i];
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//kr -> kr
							result_kr_kr = "
ProxySynthDef(\\proxyIn_kr" ++ i ++ "_kr" ++ y ++ ", {
var in = \\in.kr(" ++ arrayOfZeros_in ++ ");
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: \\lin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out[i];
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//ar -> kr
							result_ar_kr = "
ProxySynthDef(\\proxyIn_ar" ++ i ++ "_kr" ++ y ++ ", {
var in = A2K.kr(\\in.ar(" ++ arrayOfZeros_in ++ "));
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: \\lin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out[i];
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//kr -> ar
							result_kr_ar = "
ProxySynthDef(\\proxyIn_kr" ++ i ++ "_ar" ++ y ++ ", {
var in = K2A.ar(\\in.kr(" ++ arrayOfZeros_in ++ "));
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: \\sin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out[i];
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

						});

					});

					if(i < y, {

						if(i == 1, {

							//ar -> ar
							result_ar_ar = "
ProxySynthDef(\\proxyIn_ar" ++ i ++ "_ar" ++ y ++ ", {
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
							result_kr_kr = "
ProxySynthDef(\\proxyIn_kr" ++ i ++ "_kr" ++ y ++ ", {
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
							result_ar_kr = "
ProxySynthDef(\\proxyIn_ar" ++ i ++ "_kr" ++ y ++ ", {
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
							result_kr_ar = "
ProxySynthDef(\\proxyIn_kr" ++ i ++ "_ar" ++ y ++ ", {
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

							//ar -> ar
							result_ar_ar = "
ProxySynthDef(\\proxyIn_ar" ++ i ++ "_ar" ++ y ++ ", {
var in = \\in.ar(" ++ arrayOfZeros_in ++ ");
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: \\sin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
var mod_i = i % " ++ i ++ ";
outs[i] = out[mod_i];
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//kr -> kr
							result_kr_kr = "
ProxySynthDef(\\proxyIn_kr" ++ i ++ "_kr" ++ y ++ ", {
var in = \\in.kr(" ++ arrayOfZeros_in ++ ");
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: \\lin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
var mod_i = i % " ++ i ++ ";
outs[i] = out[mod_i];
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//ar -> kr
							result_ar_kr = "
ProxySynthDef(\\proxyIn_ar" ++ i ++ "_kr" ++ y ++ ", {
var in = A2K.kr(\\in.ar(" ++ arrayOfZeros_in ++ "));
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: \\lin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
var mod_i = i % " ++ i ++ ";
outs[i] = out[mod_i];
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//kr -> ar
							result_kr_ar = "
ProxySynthDef(\\proxyIn_kr" ++ i ++ "_ar" ++ y ++ ", {
var in = K2A.ar(\\in.kr(" ++ arrayOfZeros_in ++ "));
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: \\sin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
var mod_i = i % " ++ i ++ ";
outs[i] = out[mod_i];
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

						});

					});

					alreadyDonePairs.put(currentPair, true);

					result_ar_ar.interpret;
					result_kr_kr.interpret;
					result_ar_kr.interpret;
					result_kr_ar.interpret;

					//result_ar_ar.postln;
					//result_kr_kr.postln;
					//result_ar_kr.postln;
					//result_kr_ar.postln;

				});

			});

		});

		//interpolationProxyNormalizer
		16.do({
			arg counter;

			var result_ar, result_kr;
			var arrayOfZeros = "[";

			counter = counter + 1;

			if(counter == 1, {

				result_ar = "SynthDef(\\interpProxyNorm_ar1, {
var args = \\args.ar([0, 0]);
var val = args[0];
var env = args[1];
var out = val / env;
Out.ar(\\out.ir(0), out);
}).add;";

				result_kr = "SynthDef(\\interpProxyNorm_kr1, {
var args = \\args.kr([0, 0]);
var val = args[0];
var env = args[1];
var out = val / env;
Out.kr(\\out.ir(0), out);
}).add;";

			}, {

				//Generate [0, 0, 0, ...
				(counter + 1).do({
					arrayOfZeros = arrayOfZeros ++ "0,";
				});

				//remove trailing coma [0, 0, 0, and enclose in bracket -> [0, 0, 0]
				arrayOfZeros = arrayOfZeros[0..(arrayOfZeros.size - 2)] ++ "]";

				result_ar = "SynthDef(\\interpProxyNorm_ar" ++ counter.asString ++ ", {
var args = \\args.ar( " ++ arrayOfZeros ++ ");
var val = args[0.." ++ (counter - 1).asString ++ "];
var env = args[" ++ counter.asString ++ "];
var out = val / env;
Out.ar(\\out.ir(0), out);
}).add;";

				result_kr = "SynthDef(\\interpProxyNorm_kr" ++ counter.asString ++ ", {
var args = \\args.kr( " ++ arrayOfZeros ++ ");
var val = args[0.." ++ (counter - 1).asString ++ "];
var env = args[" ++ counter.asString ++ "];
var out = val / env;
Out.kr(\\out.ir(0), out);
}).add;";

			});

			//Evaluate the generated code
			result_ar.interpret;
			result_kr.interpret;

			//result_ar.postln;
			//result_kr.postln;

		});
	}

}
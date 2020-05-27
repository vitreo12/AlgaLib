(
s.waitForBoot({

	//...in == out

	SynthDef(\proxyIn_1ar_1kr, {
		Out.kr(\out.ir(0), A2K.kr(\in.ar(0)));
	}).add;

	SynthDef(\proxyIn_2ar_2kr, {
		Out.kr(\out.ir(0), A2K.kr(\in.ar([0, 0])));
	})add;

	//... in > (out == 1)

	SynthDef(\proxyIn_2ar_1kr, {
		Out.kr(\out.ir(0), A2K.kr(\in.ar([0, 0])[0]));
	}).add;

	SynthDef(\proxyIn_3ar_1kr, {
		Out.kr(\out.ir(0), A2K.kr(\in.ar([0, 0, 0])[0]));
	}).add;

	//...(in == 1) < out

	SynthDef(\proxyIn_1ar_2kr, {
		var in = \in.ar(0);
		var out = Array.new(2);
		out[0] = A2K.kr(in);
		out[1] = A2K.kr(in);
		Out.kr(\out.ir(0), out);
	}).add;

	//... in < out (MODULO)

	SynthDef(\proxyIn_2ar_3kr, {
		var in = \in.ar([0, 0]);
		var out = Array.new(2);
		out[0] = A2K.kr(in[0]);
		out[1] = A2K.kr(in[1]);
		out[2] = A2K.kr(in[0]);
		Out.kr(\out.ir(0), out);
	}).add;

	//... in > out

	SynthDef(\proxyIn_3ar_2kr, {
		var in = \in.ar([0, 0, 0]);
		var out = Array.new(2);
		out[0] = A2K.kr(in[0]);
		out[1] = A2K.kr(in[1]);
		Out.kr(\out.ir(0), out);
	}).add;
});
)

(
~func = {
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
ProxySynthDef(\\proxyIn_ar_" ++ i ++ "_ar" ++ y ++ ", {
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
ProxySynthDef(\\proxyIn_kr_" ++ i ++ "_kr" ++ y ++ ", {
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
ProxySynthDef(\\proxyIn_ar_" ++ i ++ "_kr" ++ y ++ ", {
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
ProxySynthDef(\\proxyIn_kr_" ++ i ++ "_ar" ++ y ++ ", {
var in = K2A.ar(\\in.kr(0));
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: \\sin);
var out = in * env;
var outs = Array.newClear(2);
outs[0] = out;
outs[1] = env;
outs;
}, makeFadeEnv:false).add;";

						result_ar_ar.postln;

					}, {
						//ar -> ar
						result_ar_ar = "
ProxySynthDef(\\proxyIn_ar_" ++ i ++ "_ar" ++ y ++ ", {
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
ProxySynthDef(\\proxyIn_kr_" ++ i ++ "_kr" ++ y ++ ", {
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
ProxySynthDef(\\proxyIn_ar_" ++ i ++ "_kr" ++ y ++ ", {
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
ProxySynthDef(\\proxyIn_kr_" ++ i ++ "_ar" ++ y ++ ", {
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

					//result_ar_ar.postln;
					//result_kr_kr.postln;
					//result_ar_kr.postln;
					//result_kr_ar.postln;

				});

				if(i < y, {

					if(i == 1, {

						//ar -> ar
						result_ar_ar = "
ProxySynthDef(\\proxyIn_ar_" ++ i ++ "_ar" ++ y ++ ", {
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
ProxySynthDef(\\proxyIn_kr_" ++ i ++ "_kr" ++ y ++ ", {
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
ProxySynthDef(\\proxyIn_ar_" ++ i ++ "_kr" ++ y ++ ", {
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
ProxySynthDef(\\proxyIn_kr_" ++ i ++ "_ar" ++ y ++ ", {
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

						result_ar_ar.postln;

					}, {

						//ar -> ar
						result_ar_ar = "
ProxySynthDef(\\proxyIn_ar_" ++ i ++ "_ar" ++ y ++ ", {
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
ProxySynthDef(\\proxyIn_kr_" ++ i ++ "_kr" ++ y ++ ", {
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
ProxySynthDef(\\proxyIn_ar_" ++ i ++ "_kr" ++ y ++ ", {
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
ProxySynthDef(\\proxyIn_kr_" ++ i ++ "_ar" ++ y ++ ", {
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


					//result_ar_ar.postln;
					//result_kr_kr.postln;
					//result_ar_kr.postln;
					//result_kr_ar.postln;
				});

				alreadyDonePairs.put(currentPair, true);

				//result_ar_ar.interpret;
				//result_kr_kr.interpret;
				//result_ar_kr.interpret;
				//result_kr_ar.interpret;

				//result_ar_ar.postln;
				//result_kr_kr.postln;
				//result_ar_kr.postln;
				//result_kr_ar.postln;
			});

		});

	});
};

~func.();
)


AlgaDynamicEnvGate {
	*ar { | t_release, fadeTime, doneAction = 2 |
		var selectRelease;
		var riseEnv, riseEndPoint, fallEnv, env;

		//This retrieves \fadeTime from upper AlgaSynthDef
		var realFadeTime = fadeTime ?? { NamedControl.kr(\fadeTime, 0) };

		//This retrieves \t_release from upper AlgaSynthDef
		var real_t_release = t_release ?? { NamedControl.tr(\t_release, 0) };

		//1 / fadeTime. Sanitize is for fadeTime = 0
		var invFadeTime = Select.kr(realFadeTime > 0, [0, realFadeTime.reciprocal]);

		//Trick: if fadeTime is 0 or less, the increment will be BlockSize
		//(which will make Sweep jump to 1 instantly)
		invFadeTime = Select.kr(realFadeTime > 0, [BlockSize.ir, invFadeTime]);

		//rise envelope (ar == sin)
		riseEnv = (Sweep.ar(1, invFadeTime).clip(0, 1) * pi * 0.5).sin;

		//if fadeTime == 0, output 1 (instantly)
		riseEnv = Select.ar(realFadeTime > 0, [DC.ar(1), riseEnv]);

		//Sample the end point when triggering release
		riseEndPoint = Latch.ar(riseEnv, real_t_release);

		//Fall envelope (ar == sin)
		fallEnv = 1 - ((Sweep.ar(real_t_release, invFadeTime).clip(0, 1)) * pi * 0.5).sin;

		//Scale by end point (to toggle release while rise is still going)
		fallEnv = fallEnv * riseEndPoint;

		//If fadeTime == 0, output 0
		fallEnv = Select.ar(realFadeTime > 0, [DC.ar(0), fallEnv]);

		//select fallEnv on trigger
		selectRelease = ToggleFF.kr(real_t_release);

		//Final envelope (use gate to either select rise or fall)
		env = Select.ar(selectRelease, [riseEnv, fallEnv]);

		//Release node when env is done
		DetectSilence.ar(env, doneAction:doneAction);

		^env;
	}

	*kr { | t_release, fadeTime, doneAction = 2 |
		var selectRelease;
		var riseEnv, riseEndPoint, fallEnv, env;

		//This retrieves \fadeTime from upper AlgaSynthDef
		var realFadeTime = fadeTime ?? { NamedControl.kr(\fadeTime, 0) };

		//This retrieves \t_release from upper AlgaSynthDef
		var real_t_release = t_release ?? { NamedControl.tr(\t_release, 0) };

		//1 / fadeTime. Sanitize is for fadeTime = 0
		var invFadeTime = Select.kr(realFadeTime > 0, [0, realFadeTime.reciprocal]);

		//Trick: if fadeTime is 0 or less, the increment will be BlockSize
		//(which will make Sweep jump to 1 instantly)
		invFadeTime = Select.kr(realFadeTime > 0, [BlockSize.ir, invFadeTime]);

		//rise envelope (kr == lin)
		riseEnv = (Sweep.kr(1, invFadeTime).clip(0, 1));

		//if fadeTime == 0, output 1 (instantly)
		riseEnv = Select.kr(realFadeTime > 0, [1, riseEnv]);

		//Sample the end point when triggering release
		riseEndPoint = Latch.kr(riseEnv, real_t_release);

		//Fall envelope (kr == lin)
		fallEnv = 1 - ((Sweep.kr(real_t_release, invFadeTime).clip(0, 1)));

		//Scale by end point (to toggle release while rise is still going)
		fallEnv = fallEnv * riseEndPoint;

		//If fadeTime == 0, output 0
		fallEnv = Select.kr(realFadeTime > 0, [0, fallEnv]);

		//select fallEnv on trigger
		selectRelease = ToggleFF.kr(real_t_release);

		//Final envelope (use gate to either select rise or fall)
		env = Select.kr(selectRelease, [riseEnv, fallEnv]);

		//Release node when env is done
		DetectSilence.kr(env, doneAction:doneAction);

		^env;
	}
}
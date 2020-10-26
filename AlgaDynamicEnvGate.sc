AlgaDynamicEnvGate {
	*ar { | t_release = 0, fadeTime = 0, doneAction = 2 |
		var selectRelease;
		var riseEnv, riseEndPoint, fallEnv, env;

		//1 / fadeTime. Sanitize is for fadeTime = 0
		var invFadeTime = Sanitize.kr(fadeTime.reciprocal);

		//Trick: if fadeTime is 0 or less, the increment will be BlockSize
		//(which will make Sweep jump to 1 instantly, so that when it will be locked with riseEndPoint,
		//it will be one, if coming from a fadeTime of 0)
		invFadeTime = Select.kr(invFadeTime > 0, [BlockSize.ir, invFadeTime]);

		//rise envelope
		riseEnv = (Sweep.ar(1, invFadeTime).clip(0, 1) * pi * 0.5).sin; //ar: use sin shape

		//if fadeTime == 0, output 1 (instantly)
		riseEnv = Select.ar(invFadeTime > 0, [DC.ar(1), riseEnv]);

		//Sample the end point when triggering release
		riseEndPoint = Latch.ar(riseEnv, t_release);

		//Fall envelope
		fallEnv = 1 - ((Sweep.ar(t_release, invFadeTime).clip(0, 1)) * pi * 0.5).sin; //ar: use sin shape

		//Scale by end point (to toggle release while rise is still going)
		fallEnv = fallEnv * riseEndPoint;

		//If fadeTime == 0, output 0
		fallEnv = Select.ar(invFadeTime > 0, [DC.ar(0), fallEnv]);

		//select fallEnv on trigger
		selectRelease = ToggleFF.kr(t_release);

		//Final envelope (use gate to either select rise or fall)
		env = Select.ar(selectRelease, [riseEnv, fallEnv]);

		//Release node when env is done
		DetectSilence.ar(env, doneAction:doneAction);

		^env;
	}

	*kr { | t_release = 0, fadeTime = 0, doneAction = 2 |
		var selectRelease;
		var riseEnv, riseEndPoint, fallEnv, env;

		//1 / fadeTime. Sanitize is for fadeTime = 0
		var invFadeTime = Sanitize.kr(fadeTime.reciprocal);

		//Trick: if fadeTime is 0 or less, the increment will be BlockSize
		//(which will make Sweep jump to 1 instantly)
		invFadeTime = Select.kr(invFadeTime > 0, [BlockSize.ir, invFadeTime]);

		//rise envelope
		riseEnv = (Sweep.kr(1, invFadeTime).clip(0, 1) * pi * 0.5).sin; //ar: use sin shape

		//if fadeTime == 1, output 1 (instantly)
		riseEnv = Select.kr(invFadeTime > 0, [1, riseEnv]);

		//Sample the end point when triggering release
		riseEndPoint = Latch.kr(riseEnv, t_release);

		//Fall envelope
		fallEnv = 1 - ((Sweep.kr(t_release, invFadeTime).clip(0, 1)) * pi * 0.5).sin; //ar: use sin shape

		//Scale by end point (to toggle release while rise is still going)
		fallEnv = fallEnv * riseEndPoint;

		//If fadeTime == 0, output 0
		fallEnv = Select.kr(invFadeTime > 0, [0, fallEnv]);

		//select fallEnv on trigger
		selectRelease = ToggleFF.kr(t_release);

		//Final envelope (use gate to either select rise or fall)
		env = Select.kr(selectRelease, [riseEnv, fallEnv]);

		//Release node when env is done
		DetectSilence.kr(env, doneAction:doneAction);

		^env;
	}

}
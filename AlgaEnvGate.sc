AlgaEnvGate {

	/*
	*ar { arg i_level=1, gate, fadeTime, doneAction=2, curve='sin';
		var synthGate = gate ?? { NamedControl.kr(\gate, 1.0) };
		var synthFadeTime = fadeTime ?? { NamedControl.kr(\fadeTime, 0) };
		^EnvGen.ar(
			Env.new([ i_level, 1.0, 0.0], #[1.0, 1.0], curve, 1),
			synthGate, 1.0, 0.0, synthFadeTime, doneAction
		)
	}

	*kr { arg i_level=1, gate, fadeTime, doneAction=2, curve='sin';
		var synthGate = gate ?? { NamedControl.kr(\gate, 1.0) };
		var synthFadeTime = fadeTime ?? { NamedControl.kr(\fadeTime, 0) };
		^EnvGen.kr(
			Env.new([ i_level, 1.0, 0.0], #[1.0, 1.0], curve, 1),
			synthGate, 1.0, 0.0, synthFadeTime, doneAction
		)
	}
	*/

	*ar {
		arg i_level=1, gate, connectionTime, doneAction=2, curve='sin';
		var synthGate = gate ?? { NamedControl.kr(\gate, 1.0) };
		var synthFadeTime = connectionTime ?? { NamedControl.kr(\connectionTime, 0) };
		//synthFadeTime.poll(1);
		^EnvGen.ar(
			Env.new([ i_level, 1.0, 0.0], #[1.0, 1.0], curve, 1),
			synthGate, 1.0, 0.0, synthFadeTime, doneAction
		)
	}

	*kr {
		arg i_level=1, gate, connectionTime, doneAction=2, curve='sin';
		var synthGate = gate ?? { NamedControl.kr(\gate, 1.0) };
		var synthFadeTime = connectionTime ?? { NamedControl.kr(\connectionTime, 0) };
		//synthFadeTime.poll(1);
		^EnvGen.kr(
			Env.new([ i_level, 1.0, 0.0], #[1.0, 1.0], curve, 1),
			synthGate, 1.0, 0.0, synthFadeTime, doneAction
		)
	}

}

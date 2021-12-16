// AlgaLib: SuperCollider implementation of Alga, an interpolating live coding environment.
// Copyright (C) 2020-2021 Francesco Cameli.

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

//Like AlgaDynamicEnvGate but with reset.
AlgaDynamicEnvGateReset {
	*ar { | t_reset, fadeTime, doneAction = 2 |
		var selectReset, real_t_reset;
		var riseEnv, riseEndPoint, resetEnv, resetEnvCount, env;

		//This retrieves \fadeTime from upper AlgaSynthDef
		var realFadeTime = fadeTime ?? { NamedControl.kr(\fadeTime, 0) };

		//1 / fadeTime. Sanitize is for fadeTime = 0
		var invFadeTime = Select.kr(realFadeTime > 0, [0, realFadeTime.reciprocal]);

		//This retrieves \t_reset from upper AlgaSynthDef
		t_reset = t_reset ?? { NamedControl.tr(\t_reset, 0) };

		//Ensure that t_release can only be triggered once
		real_t_reset = Select.kr(PulseCount.kr(t_reset) <= 1, [0, t_reset]);

		//Trick: if fadeTime is 0 or less, the increment will be BlockSize
		//(which will make Sweep jump to 1 instantly)
		invFadeTime = Select.kr(realFadeTime > 0, [BlockSize.ir, invFadeTime]);

		//rise envelope
		riseEnv = Sweep.ar(1, invFadeTime).clip(0, 1);

		//if fadeTime == 0, output 1 (instantly)
		riseEnv = Select.ar(realFadeTime > 0, [DC.ar(1), riseEnv]);

		//Sample the end point when triggering release
		riseEndPoint = Latch.ar(riseEnv, real_t_reset);

		//reset envelope (every time, not just once)
		resetEnv = Sweep.ar(t_reset, invFadeTime).clip(0, 1);

		//If fadeTime == 0, output 1 (instantly)
		resetEnv = Select.ar(realFadeTime > 0, [DC.ar(1), resetEnv]);

		//select resetEnv on trigger
		selectReset = ToggleFF.kr(real_t_reset);

		//Final envelope (use gate to either select rise or reset)
		env = Select.ar(selectReset, [riseEnv, resetEnv]);

		//Add to resetEnv. When it reaches 2, it's done
		resetEnvCount = PulseCount.kr(real_t_reset) + resetEnv;

		//Release node when resetEnv is done
		FreeSelf.kr(resetEnvCount >= 2 /*DelayN.kr(resetEnvCount >= 2, delaytime: SampleDur.ir * BlockSize.ir)*/);

		^[env, riseEndPoint];
	}
}

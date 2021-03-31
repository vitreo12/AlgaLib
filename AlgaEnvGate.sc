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

//This class is probably useless: the difference between .ar and .kr is not anything relevant,
//I could just use the standard EnvGate (which uses .kr method)
AlgaEnvGate {
  //Don't give default values to gate and fadeTime, or it would screw the namedcontrol business 
	*ar { | i_level=0, gate, fadeTime, doneAction=2, curve='sin' |
		var synthGate = gate ?? { NamedControl.kr(\gate, 1.0) }; //This retrieves \gate from upper AlgaSynthDef
		var synthFadeTime = fadeTime ?? { NamedControl.kr(\fadeTime, 0) }; //This retrieves \fadeTime from upper AlgaSynthDef
		^EnvGen.ar(
			Env.new([ i_level, 1.0, 0.0], #[1.0, 1.0], curve, 1),
			synthGate, 1.0, 0.0, synthFadeTime, doneAction
		)
	}

  //Don't give default values to gate and fadeTime, or it would screw the namedcontrol business 
	*kr { | i_level=0, gate, fadeTime, doneAction=2, curve='sin' |
		var synthGate = gate ?? { NamedControl.kr(\gate, 1.0) }; //This retrieves \gate from upper AlgaSynthDef
		var synthFadeTime = fadeTime ?? { NamedControl.kr(\fadeTime, 0) }; //This retrieves \fadeTime from upper AlgaSynthDef
		^EnvGen.kr(
			Env.new([ i_level, 1.0, 0.0], #[1.0, 1.0], curve, 1),
			synthGate, 1.0, 0.0, synthFadeTime, doneAction
		)
	}
}

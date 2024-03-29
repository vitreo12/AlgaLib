// AlgaLib: SuperCollider implementation of Alga, an interpolating live coding environment.
// Copyright (C) 2020-2022 Francesco Cameli.

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

//Just like EnvGate, but with split .ar and .kr
//It's used internally in AlgaSynthDef for makeFadeEnv and in alga_play synth
AlgaEnvGate {
	*ar { | i_level=0, gate, fadeTime, doneAction=2, curve='sin' |
		^EnvGen.ar(
			Env.new([ i_level, 1.0, 0.0], #[1.0, 1.0], curve, 1),
			gate, 1.0, 0.0, fadeTime, doneAction
		)
	}

	*kr { | i_level=0, gate, fadeTime, doneAction=2, curve='lin' |
		^EnvGen.kr(
			Env.new([ i_level, 1.0, 0.0], #[1.0, 1.0], curve, 1),
			gate, 1.0, 0.0, fadeTime, doneAction
		)
	}
}

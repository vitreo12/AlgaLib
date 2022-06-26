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

AlgaSmoother {
	*ar { | val1, val2, time = 0, curve = 0, trig = 1 |
		var phasor = Sweep.ar(trig, time.reciprocal).clip(0, 1).lincurve(curve: curve);
		^((val1 * (1 - phasor)) + (val2 * phasor));
	}

	*kr { | val1, val2, time = 0, curve = 0, trig = 1 |
		var phasor = Sweep.kr(trig, time.reciprocal).clip(0, 1).lincurve(curve: curve);
		^((val1 * (1 - phasor)) + (val2 * phasor));
	}
}
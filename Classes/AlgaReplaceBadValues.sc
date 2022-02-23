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

//Credits to https://github.com/adcxyz/SafetyNet
AlgaReplaceBadValues {
	*ar { |in, sub = 0, id = 0,  post = 2|
		var subIndex =  CheckBadValues.ar(in, id, post) > 0;
		// prepare for Select
		sub = sub.asArray.collect { |sub1|
			if (sub1.rate != \audio) { sub = K2A.ar(sub) } { sub };
		};
		^Select.ar(subIndex, [in, sub]);
	}
	*kr { |in, sub = 0, id = 0,  post = 2|
		var subIndex = CheckBadValues.kr(in, id, post) > 0;
		^Select.kr(subIndex, [in, sub]);
	}
}
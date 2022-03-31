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

LiteralFunctions {
	classvar funcs;

	*initClass {
		funcs = IdentitySet(10)
	}

	*add { | func |
		funcs.add(func)
	}

	*remove { | func |
		funcs.remove(func)
	}

	*includes { | func |
		^funcs.includes(func)
	}
}

+Function {
	literalFunc { LiteralFunctions.add(this) }

	//Alias
	litFunc { this.literalFunc }

	//Alias
	lf { this.literalFunc }

	isLiteralFunction { ^LiteralFunctions.includes(this) }
}
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

//Credits go to Pkr from BenoitLib: https://github.com/cappelnord/BenoitLib
AlgaPkr : Pfunc {
	*new { | algaNode, limit, valOnError = 1 |
		var check;
		var last = 0.0;
		var func;

		if(limit != nil, {
			func = {
				if(algaNode.algaInstantiatedAsSender, {
					var bus = algaNode.synthBus.bus;
					var val = bus.getSynchronous();
					if(val < limit, { val = limit });
					val;
				}, {
					("AlgaNode: not instantiated yet. Returning " ++ valOnError).error;
					valOnError
				});
			};
		}, {
			func = {
				if(algaNode.algaInstantiatedAsSender, {
					algaNode.synthBus.bus.getSynchronous();
				}, {
					("AlgaNode: not instantiated yet. Returning " ++ valOnError).error;
					valOnError
				});
			};
		});

		^Pfunc( func );
	}
}

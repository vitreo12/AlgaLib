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
	*new { | algaNode, limit |
		var check;
		var last = 0.0;

		var bus = algaNode.synthBus.bus;

		if(algaNode.algaInstantiatedAsSender.not, {
			"AlgaPkr: the sender AlgaNode is not instantiated yet. This will yield 0".warn;
			^Pfunc( { 0 } );
		});

		bus.isSettable.not.if {
			"AlgaPkr: not a kr Bus. This will only yield 0".warn;
			^Pfunc( { 0 } );
		};

		check = { bus.server.hasShmInterface }.try;

		check.if ({
			var func;
			if(limit != nil, {
				func = {
					var val = bus.getSynchronous();
					if(val < limit, { val = limit });
					val;
				};
			}, {
				func = { bus.getSynchronous() };
			});

			^Pfunc( func );
		}, {
			"AlgaPkr: no shared memory interface detected. Use localhost server on SC 3.5 or higher to get better performance".warn;
			bus.get( { |v| last = v } );
			^Pfunc( { bus.get( { |v| last = v } ); last } );
		});
	}
}

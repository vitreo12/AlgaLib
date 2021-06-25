//Credits to BenoitLib

AlgaPkr : Pfunc {
	*new { | algaNode, limit |
		var check;
		var last = 0.0;

		var bus = algaNode.synthBus.bus;

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

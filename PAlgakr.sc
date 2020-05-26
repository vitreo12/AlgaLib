 //From https://github.com/cappelnord/BenoitLib/blob/master/patterns/Pkr.sc
PAlgakr : Pfunc {
	*new {
		arg bus, limit = 0.0;

		var bus_sync;
		bus = bus.asBus;

		// audio?
		bus.isSettable.not.if {
			"Not a kr Bus or NodeProxy. This will only yield 0".warn;
			^Pfunc({0}).asStream;
		};

		bus_sync = Pfunc({bus.getSynchronous()}).asStream;

		^Pif(bus_sync <= limit, limit, bus_sync);
	}
}
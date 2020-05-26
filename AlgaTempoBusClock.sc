//Just as TempoBusClock, but with slaves for multiple servers
AlgaTempoBusClock : TempoBusClock {

	//Slaves' tempo proxy functions
	var <>slavesControl;

	init {
		arg tempo, beats, seconds, queueSize;

		//Init clock, actually:
		super.init(tempo, beats, seconds, queueSize);

		//Init dictionary
		slavesControl = IdentityDictionary.new;

		^this;
	}

	//Called when changing tempo
	setTempoAtBeat { | newTempo, beats |
		slavesControl.do({
			arg slaveControl;

			if(slaveControl.numChannels != nil, {
				slaveControl.set(\fadeTime, 0.0, \tempo, newTempo);
			}, {
				//It's been deleted from its parent ProxySpace, remove it from array
				slavesControl.removeAt(slaveControl);
			});
		});

		control.set(\fadeTime, 0.0, \tempo, newTempo);

		^super.setTempoAtBeat(newTempo, beats)
	}

	//Called when changing tempo
	setTempoAtSec { | newTempo, secs |
		slavesControl.do({
			arg slaveControl;

			if(slaveControl.numChannels != nil, {
				slaveControl.set(\fadeTime, 0.0, \tempo, newTempo);
			}, {
				//It's been deleted from its parent ProxySpace, remove it from array
				slavesControl.removeAt(slaveControl);
			});
		});

		control.set(\fadeTime, 0.0, \tempo, newTempo);

		^super.setTempoAtSec(newTempo, secs)
	}
}

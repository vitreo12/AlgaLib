// AlgaLib: SuperCollider implementation of Alga, an interpolating live coding environment.
// Copyright (C) 2020-2021 Francesco Cameli.

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

//This class is used to specify individual parameters of a pattern argument.
//It can be used to dynamically set parameters of a connected Node (like scale and chans).
AlgaArg {
	//The streams
	var <senderStream, <chansStream, <scaleStream;

	//The unpacked entries
	var <sender, <chans, scale;

	*new { | node, chans, scale |
		^super.new.init(node, chans, scale)
	}

	init { | argSender, argChans, argScale |
		senderStream = argSender.algaAsStream; //Pattern support
		chansStream  = argChans.algaAsStream;  //Pattern support
		scaleStream  = argScale.algaAsStream;  //Pattern support
	}

	algaAdvance {
		sender = senderStream.next;
		chans  = chansStream.next;
		scale  = scaleStream.next;
		scale.algaAdvanceArrayScaleValues;
	}

	algaInstantiatedAsSender {
		if(sender.isAlgaNode, { ^sender.algaInstantiatedAsSender });
		^false
	}

	isAlgaArg { ^true }

	//Used in AlgaBlock
	blockIndex {
		if(sender.isAlgaNode, { ^sender.blockIndex });
		^(-1);
	}

	//Used in AlgaBlock
	blockIndex_ { | val |
		if(sender.isAlgaNode, { sender.blockIndex_(val) });
	}

	//Used in AlgaBlock
	inNodes {
		if(sender.isAlgaNode, { ^sender.inNodes });
		^nil
	}

	//Used in AlgaBlock
	outNodes {
		if(sender.isAlgaNode, { ^sender.outNodes });
		^nil
	}
}

//Alias
AA : AlgaArg {}

//This class is used for the \out parameter... Should it also store time?
//Perhaps, the first node -> time pair should be considered if using a ListPattern:
/*
out: (
Pseq([
AlgaOut(a, \freq, scale:[20, 30], time:2),   <-- uses time: 2 also for the later 'a'
AlgaOut(b, \freq, scale:[10, 50], time:3),   <-- uses time: 3 also for the later 'b'
b,
a
], inf)
)
*/
AlgaOut {
	//The streams
	var <nodeStream, <paramStream, <chansStream, <scaleStream;

	//The unpacked entries
	var <node, <param, <chans, scale;

	*new { | node, param = \in, chans, scale |
		^super.new.init(node, param, chans, scale)
	}

	init { | argNode, argParam, argChans, argScale |
		nodeStream   = argNode.algaAsStream;  //Pattern support
		paramStream  = argParam.algaAsStream; //Pattern support
		chansStream  = argChans.algaAsStream; //Pattern support
		scaleStream  = argScale.algaAsStream; //Pattern support
	}

	algaAdvance {
		node  = nodeStream.next;
		param = paramStream.next;
		chans = chansStream.next;
		scale = scaleStream.next;
		scale.algaAdvanceArrayScaleValues;
	}

	isAlgaOut { ^true }
}

//Alias
AO : AlgaOut {}

//This class is used to create a temporary AlgaNode for a parameter in an AlgaPattern
AlgaTemp {
	var <def;

	//The streams (def can't be, use Pseq of AlgaTemps for that)
	var <chansStream, <scaleStream;

	//The unpacked entries
	var <chans, <scale;

	var <sampleAccurate = false;
	var <controlNames;
	var <numChannels, <rate;
	var <valid = false;

	var <>algaReaderPfuncParams;
	var <>algaReaderKeysOrFuncsAtParam;

	*new { | def, chans, scale, sampleAccurate = false |
		^super.new.init(def, chans, scale, sampleAccurate)
	}

	init { | argDef, argChans, argScale, argSampleAccurate |
		if(argDef.isPattern, {
			"AlgaTemp: no support for Pattern as 'def' yet. Use a Pattern of AlgaTemps to achieve the same result".error;
			argDef = nil;
		});
		def = argDef;
		chansStream  = argChans.algaAsStream;  //Pattern support
		scaleStream  = argScale.algaAsStream;  //Pattern support
		sampleAccurate = argSampleAccurate ? false;
		algaReaderPfuncParams = Array.newClear();
	}

	algaAdvance {
		chans = chansStream.next;
		scale = scaleStream.next;
		scale.algaAdvanceArrayScaleValues;
	}

	setDef { | argDef |
		def = argDef
	}

	checkValidSynthDef { | def |
		var synthDesc = SynthDescLib.global.at(def);
		var synthDef;

		if(synthDesc == nil, {
			("AlgaTemp: Invalid AlgaSynthDef: '" ++ def.asString ++ "'").error;
			^nil;
		});

		synthDef = synthDesc.def;

		if(synthDef.class != AlgaSynthDef, {
			("AlgaTemp: Invalid AlgaSynthDef: '" ++ def.asString ++"'").error;
			^nil;
		});

		numChannels = synthDef.numChannels;
		rate = synthDef.rate;
		controlNames = synthDesc.controls;

		//Set validity
		if((numChannels != nil).and(rate != nil).and(controlNames != nil), { valid = true });
	}

	isAlgaTemp { ^true }
}

//Alias
AT : AlgaTemp {}

//This class is used to schedule actions on steps
AlgaStep {
	var <>step = 0;
	var <>retryOnFailure = true;
	var <>tries = 2;
	var <post = false;
	var <>func;
	var <>condition;

	*new { | step = 0, retryOnFailure = true, tries = 2, post = false |
		if(step.isNumber.not, {
			"AlgaStep: step must be a number. Using 0.".warn;
			step = 0
		});
		if((retryOnFailure != true).and(retryOnFailure != false), {
			"AlgaStep: retryOnFailure must be a boolean. Using true.".warn;
			retryOnFailure = true
		});
		if(tries.isNumber.not, {
			"AlgaStep: tries must be a number. Using 2.".warn;
			tries = 2
		});
		if((post != true).and(post != false), {
			"AlgaStep: post must be a boolean. Using false.".warn;
			post = false
		});
		^super.newCopyArgs(step, retryOnFailure, tries, post)
	}

	isAlgaStep { ^true }
}

//Alias
AS : AlgaStep {}

//Advance Array values for scale (min / max can be Patterns)
+SequenceableCollection {
	algaAdvanceArrayScaleValues {
		this.do({ | entry, i |
			if(entry.isStream, {
				entry = entry.next;
				this[i] = entry;
			});
		});
	}
}
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

AlgaProxySpace {
	classvar <paramsArgs;
	classvar <currentNode;
	var <nodes;
	var <patternsEvents;
	var <server;
	var <sched = 1;
	var <interpTime = 0, <interpShape, <playTime = 0;
	var <replacePlayTime = true, <playSafety = \clip;

	*boot { | onBoot, server, algaServerOptions, clock |
		var newSpace;
		server = server ? Server.default;
		Alga.boot(onBoot, server, algaServerOptions, clock);
		newSpace = this.new.init;
		newSpace.push;
		^newSpace;
	}

	*initClass {
		paramsArgs = IdentityDictionary(10);
	}

	*addParamArgs { | param, value |
		if(paramsArgs[currentNode] == nil, { paramsArgs[currentNode] = IdentityDictionary() });
		paramsArgs[currentNode][param] = value;
	}

	init {
		CmdPeriod.add(this);
		nodes = IdentityDictionary(10);
		patternsEvents = IdentityDictionary(10);
		interpShape = Env([0, 1], 1);
		^this
	}

	interpTime_ { | value |
		interpTime = value;
		nodes.do({ | node | node.interpTime = value });
	}

	it { ^interpTime }

	it_ { | value | this.interpTime_(value) }

	interpShape_ { | value |
		interpShape = value;
		nodes.do({ | node | node.interpShape = value });
	}

	is { ^interpShape }

	is_ { | value | this.interpShape(value) }

	playTime_ { | value |
		playTime = value;
		nodes.do({ | node | node.playTime = value });
	}

	pt { ^playTime }

	pt_ { | value | this.playTime_(value) }

	replacePlayTime_ { | value |
		replacePlayTime = value;
		nodes.do({ | node | node.replacePlayTime = value });
	}

	rpt { ^replacePlayTime }

	rpt_ { | value | this.replacePlayTime_(value) }

	playSafety_ { | value |
		playSafety = value;
		nodes.do({ | node | node.playSafety = value });
	}

	ps { ^playSafety }

	ps_ { | value | this.playSafety_(value) }

	copyAllProxySpaceParams { | node |
		node.interpTime = interpTime;
		node.interpShape = interpShape;
		node.playTime = playTime;
		node.replacePlayTime = replacePlayTime;
		node.playSafety = playSafety;
		node.sched = sched;
	}

	sched_ { | value |
		nodes.do({ | node | node.sched = value });
	}

	stop { | time, sched |
		nodes.do({ | node | node.stop(time:time, sched:sched) })
	}

	clear { | time, sched |
		nodes.do({ | node | node.clear(time:time, sched:sched) })
	}

	push {
		if(currentEnvironment !== this, {
			Environment.push(this)
		}, {
			"AlgaProxySpace: this environment is already current".warn
		});
	}

	pop {
		Environment.pop
	}

	at { | key |
		var node = nodes[key];
		if(node.isNil, {
			node = this.newNode;
			nodes[key] = node;
		});
		^node
	}

	newNode {
		var node = AlgaNode(\alga_silent, server: server);
		this.copyAllProxySpaceParams(node);
		^node
	}

	//This allows to retrieve Symbol.kr / Symbol.ar BEFORE they're sent to the server.
	triggerDef { | node, def |
		currentNode = node;
		def.value;
	}

	explicitNode { | node, key, def |
		node.clear;
		nodes[key] = def;
		^def;
	}

	newPatternFromNode { | node, key, def |
		var wasPlaying = node.isPlaying;
		var interpTime = node.connectionTime;
		var interpShape = node.interpShape;
		var playTime = node.playTime;
		var defBeforeMod = def.copy; //def gets modified in AlgaPattern. Store the original one
		var pattern = AlgaPattern(
			def: def,
			interpTime: interpTime,
			interpShape: interpShape,
			playTime: playTime,
			server: server
		);

		//Store
		patternsEvents[pattern] = defBeforeMod;

		//Copy relevant vars over
		pattern.copyVars(node);

		//Clear the old one
		node.clear;

		//Play new one
		if(wasPlaying, { pattern.play });

		//Replace entry
		nodes[key] = pattern;
		^pattern;
	}

	patternDifferential { | node, key, def |
		var defBeforeMod = def.copy;
		var currentEventPairs = patternsEvents[node];
		var newConnections = IdentityDictionary();

		//Check for differences in the Event to perform interpolations.
		//OR perform a single .replace if there's at least one replaceable element.
		block { | break |
			def.keysValuesDo({ | key, newEntry |
				var currentEntry = currentEventPairs[key];
				//Trick to compare actual differences in the source code
				var currentEntryCompileString = currentEntry.asCompileString;
				var newEntryCompileString = newEntry.asCompileString;
				if(newEntryCompileString != currentEntryCompileString, {
					newConnections[key] = newEntry;
					//If the connection would trigger a replace (including a Buffer connection),
					//just reset all things to go through with a normal SINGLE replace call,
					//instead of piling up multiple ones
					if(node.connectionTriggersReplace(key).or(
						node.patternOrAlgaPatternArgContainsBuffers(newEntry)), {
						newConnections.clear;
						break.value(nil);
					});
				});
			});
		};

		//Update patternsEvents
		patternsEvents[node] = defBeforeMod;

		//Perform differential connections
		if(newConnections.size > 0, {
			newConnections.keysValuesDo({ | param, entry |
				node.from(
					sender: entry,
					param: param
				);
			});
			^true
		});
		^false
	}

	replaceNode { | node, key, def |
		//The args replacement ONLY works with AlgaNode
		if(node.isAlgaPattern.not, {
			var currentArgsID, currentArgs;

			//Fundamental: it will allow to retrieve AlgaNodes in Symbol.ar/kr
			this.triggerDef(node, def);

			//These are updated thanks to triggerDef
			currentArgsID = paramsArgs[currentNode];
			if(currentArgsID != nil, {
				currentArgs = Array();
				currentArgsID.keysValuesDo({ | param, val |
					currentArgs = currentArgs.add(param).add(val);
				});
				if(currentArgs.size > 0, {
					^node.replace(
						def: def,
						args: currentArgs
					);
				});
			});
		});

		//Standard replace
		^node.replace(
			def: def
		);
	}

	put { | key, def |
		var node = this.at(key);

		//If user explicitly sets an AlgaNode (e.g. ~a = AN( ))
		//use that and just replace the entry, clearing the old one.
		if(def.isAlgaNode, { ^this.explicitNode(node, key, def) });

		//New AlgaPattern (Event)
		if(def.isEvent, {
			if(node.isAlgaPattern.not, {
				//If old node was AlgaNode, create the new pattern
				^this.newPatternFromNode(node, key, def)
			}, {
				//If old node was AlgaPattern, perform differential checks
				if(this.patternDifferential(node, key, def), { ^node });
			});
		});

		//Fallback is replace
		^this.replaceNode(node, key, def);
	}

	cmdPeriod {
		currentNode = nil;
		nodes.clear;
		paramsArgs.clear;
	}

	clock { ^Alga.clock(server) }
}

//Support for AlgaNode and AlgaArg
+Symbol {
	ar { | val, lag, spec |
		if((val.isAlgaNode).or(val.isAlgaArg), {
			AlgaProxySpace.addParamArgs(this, val);
			^NamedControl.ar(this, 0, lag, spec)
		});
		^NamedControl.ar(this, val, lag, spec)
	}

	kr { | val, lag, fixedLag = false, spec |
		if((val.isAlgaNode).or(val.isAlgaArg), {
			AlgaProxySpace.addParamArgs(this, val);
			^NamedControl.kr(this, 0, lag, fixedLag, spec)
		});
		^NamedControl.kr(this, val, lag, fixedLag, spec)
	}
}

//This fixes bug when doing def.value in AlgaProxySpace.triggerDef
+Nil {
	addAr { ^nil }
}
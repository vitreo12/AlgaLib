AlgaProxySpace {
	classvar <nodes;
	classvar <paramsArgs;
	classvar <currentNode;
	var <server;

	*boot { | onBoot, server, algaServerOptions, clock |
		var newSpace;
		server = server ? Server.default;
		Alga.boot(onBoot, server, algaServerOptions, clock);
		newSpace = this.new.init;
		newSpace.push;
		^newSpace;
	}

	*initClass {
		nodes = IdentityDictionary(10);
		paramsArgs = IdentityDictionary(10);
	}

	*addParamArgs { | param, value |
		if(paramsArgs[currentNode] == nil, {
			paramsArgs[currentNode] = IdentityDictionary()
		});
		paramsArgs[currentNode][param] = value;
	}

	init {
		CmdPeriod.add(this);
		^this
	}

	push {
		if(currentEnvironment !== this, {
			Environment.push(this)
		}, {
			"AlgaProxySpace: this environment is already current".warn
		});
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
		^AlgaNode(\alga_silent, server: server);
	}

	//This allows to retrieve Symbol.kr / Symbol.ar
	//BEFORE they're sent to the server.
	triggerDef { | node, def |
		currentNode = node;
		def.value;
	}

	put { | key, def |
		var currentArgsID, currentArgs;
		var node = this.at(key);

		//If user explicitly sets an AlgaNode, e.g. ~a = AN( ),
		//use that and just replace the entry, clearing the old one.
		if(def.isAlgaNode, {
			node.clear;
			nodes[key] = def;
			^def;
		});

		//New AlgaPattern (Event)
		if(def.isEvent, {
			//If old node was AlgaNode, otherwise, go forward with the .replace mechanism
			if(node.isAlgaPattern.not, {
				var wasPlaying = node.isPlaying;
				var interpTime = node.connectionTime;
				var interpShape = node.interpShape;
				var playTime = node.playTime;
				var pattern = AlgaPattern(
					def: def,
					interpTime: interpTime,
					interpShape: interpShape,
					playTime: playTime,
					server: server
				);

				//Copy relevant vars over
				pattern.copyVars(node);

				//Clear the old one
				node.clear;

				//Play new one
				if(wasPlaying, { pattern.play });

				//Replace entry
				nodes[key] = pattern;
				^pattern;
			}, {
				//Check for differences in the Event to perform interpolations

				//If at least one would require .replace, just run it once
			});
		});

		//This allows to retrieve Symbol.kr / Symbol.ar using AlgaNodes
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

		//Standard replace
		^node.replace(def);
	}

	cmdPeriod {
		currentNode = nil;
		nodes.clear;
		paramsArgs.clear;
	}

	clock {
		^Alga.clock(server)
	}
}

+Symbol {
	ar { | val, lag, spec |
		if(val.isAlgaNode, {
			AlgaProxySpace.addParamArgs(this, val);
			^NamedControl.ar(this, 0, lag, spec)
		});
		^NamedControl.ar(this, val, lag, spec)
	}

	kr { | val, lag, fixedLag = false, spec |
		if(val.isAlgaNode, {
			AlgaProxySpace.addParamArgs(this, val);
			^NamedControl.kr(this, 0, lag, fixedLag, spec)
		});
		^NamedControl.kr(this, val, lag, fixedLag, spec)
	}
}

//This fixes bug when doing def.value in AlgaProxySpace.put
+Nil {
	addAr { ^nil }
}
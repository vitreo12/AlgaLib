AlgaProxySpace {
	classvar <nodes;
	classvar <paramsArgs;
	classvar <currentNode;

	*boot { | onBoot, server, algaServerOptions, clock |
		var newSpace;
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
		^AlgaNode(\alga_silent);
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

		//If user explicitly sets an AlgaNode, use that
		if(def.isAlgaNode, {
			nodes[key] = def;
			^def;
		});

		//Otherwise, go on with the replacing

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
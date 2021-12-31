AlgaProxySpace {
	classvar <nodes;
	classvar <paramsArgs;
	classvar <currentNode;

	*boot { | onBoot, server, algaServerOptions, clock |
		var newSpace;
		Alga.boot(onBoot, server, algaServerOptions, clock);
		newSpace = this.new();
		newSpace.push;
		^newSpace;
	}

	*initClass {
		nodes = IdentityDictionary(10);
		paramsArgs = IdentityDictionary(10);
	}

	*addParamArgs { | param, value |
		if(paramsArgs[currentNode] == nil, {
			paramsArgs[currentNode] = OrderedIdentitySet()
		});
		paramsArgs[currentNode].add(param).add(value);
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

	put { | key, def |
		var currentArgs;
		var node = this.at(key);

		//Used to trigger Symbol's kr / ar
		currentNode = node;
		def.value;

		currentArgs = paramsArgs[currentNode].asArray;

		if(currentArgs.size > 0, {
			^node.replace(
				def: def,
				args: currentArgs
			);
		});

		^node.replace(def);
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

+Nil {
	addAr { ^nil }
}
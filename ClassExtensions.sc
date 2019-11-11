+ NodeProxy {
	after {
		arg nextProxy;
		this.group.moveAfter(nextProxy.group);
		^this;
	}

	before {
		arg nextProxy;
		this.group.moveBefore(nextProxy.group);
		^this;
	}
}

+ Array {
	=> {
		arg nextProxy, param = \in;

		"Array's => : need to add all the logic".warn;

		^nextProxy;
	}

	//To allow ~c = [~a, ~b] , ensuring ~a  and ~b are before ~c
	putObjBefore {

		"Array's putObjBefore : need to add all the logic now".warn;
	}
}

+ Function {
	=> {
		arg nextProxy, param = \in;

		"Function's => : need to add all the logic".warn;

		^nextProxy;
	}

	//To allow ~c = {~a * 0.5}, ensuring ~a is before ~c
	putObjBefore {

		arg targetProxy, server;

		var funcDef = this.def;

		//Find all proxies recursively for this binary operations
		var allProxiesDict;

		//constants will also show params, but if there is a NodeProxy
		//of some kind, it's been created on the relative ProxySpace.
		//It can be retrieved with: VitreoProxySpace.findSpace(~d);
		var possibleProxies = funcDef.constants;

		if(possibleProxies.size > 0, {
			var proxySpace;
			var isTargetProxyAnVNdef = (targetProxy.class.superclass == VitreoNodeProxy).or(targetProxy.class.superclass.superclass == VitreoNodeProxy);

			//Array instead?
			allProxiesDict = IdentityDictionary.new;

			//"possibleProxies: ".postln;

			//possibleProxies.postln;

			if(isTargetProxyAnVNdef.not, {
				//A VitreoNodeProxy
				proxySpace = VitreoProxySpace.findSpace(targetProxy);

			}, {
				//A VitreoNdef
				proxySpace = VNdef.all.at(server.name)
			});


			if(proxySpace != nil, {
				possibleProxies.do({

					arg possibleProxySymbolName;

					//Check if the possibleProxy is in the proxySpace
					var nodeProxy = proxySpace[possibleProxySymbolName];

					//Non-valid symbols will return a VitreoNodeProxy with nil channels
					if(nodeProxy.numChannels != nil, {
						//("Found one VitreoNodeProxy : " ++ possibleProxy.asString).postln;
						allProxiesDict.put(possibleProxySymbolName, nodeProxy);
					});
				})
			});
		});

		"Function's putObjBefore : need to add all the logic now".warn;

		allProxiesDict.postln;

	}

}

+ AbstractOpPlug {

	//To allow (~a * 0.5) => ~b (and viceversa, if called with this being a VNProxy)
	=> {
		arg nextProxy, param = \in;

		//Array instead?
		var allProxiesDict = IdentityDictionary.new;

		//"Binary!!".warn;

		this.findAllProxies(allProxiesDict);

		"AbstractOpPlug's => : need to add all the logic now".warn;

		allProxiesDict.postln;

		^this;
	}

	//To allow ~c = ~a * 0.5, ensuring ~a is before ~c
	putObjBefore {

		arg targetProxy;

		//Array instead?
		var allProxiesDict = IdentityDictionary.new;

		this.findAllProxies(allProxiesDict);

		"AbstractOpPlug's putObjBefore : need to add all the logic now".warn;

		allProxiesDict.postln;

		^this;
	}

}

+ BinaryOpPlug {

	findAllProxies {
		arg dict;

		var firstOp = this.a;
		var isFirstOpAnOp = firstOp.class.superclass == AbstractOpPlug;

		var secondOp = this.b;
		var isSecondOpAnOp = secondOp.class.superclass == AbstractOpPlug;

		if(isFirstOpAnOp, {

			firstOp.findAllProxies(dict);

		}, {

			var isFirstOpAProxy = (firstOp.class == VitreoNodeProxy).or(firstOp.class.superclass == VitreoNodeProxy).or(firstOp.class.superclass.superclass == VitreoNodeProxy);

			if(isFirstOpAProxy, {
				//Add to dict, found a proxy
				dict.put(firstOp, firstOp);
			});

		});

		if(isSecondOpAnOp, {

			secondOp.findAllProxies(dict);

		}, {

			var isSecondOpAProxy = (secondOp.class == VitreoNodeProxy).or(secondOp.class.superclass == VitreoNodeProxy).or(secondOp.class.superclass.superclass == VitreoNodeProxy);

			if(isSecondOpAProxy, {
				//Add to dict, found a proxy
				dict.put(secondOp, secondOp);
			});

		});
	}

}

+ UnaryOpPlug {

	findAllProxies {
		arg dict;

		var firstOp = this.a;
		var isFirstOpAnOp = firstOp.class.superclass == AbstractOpPlug;

		if(isFirstOpAnOp, {

			firstOp.findAllProxies(dict);

		}, {

			var isFirstOpAProxy = (firstOp.class == VitreoNodeProxy).or(firstOp.class.superclass == VitreoNodeProxy).or(firstOp.class.superclass.superclass == VitreoNodeProxy);

			if(isFirstOpAProxy, {
				//Add to dict, found a proxy
				dict.put(firstOp, firstOp);
			});

		});
	}
}
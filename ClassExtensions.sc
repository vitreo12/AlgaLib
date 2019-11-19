+ IdentityDictionary {

	//To loop over all inProxies, including if they are Array and treating them normally.
	doProxiesLoop {
		arg function;

		this.keysValuesDo({ arg key, value, i;

			if(value.class == Array, {
				value.do({
					arg entry;
					function.value(entry, i);
				});
			}, {
				function.value(value, i);
			});
		});
	}
}


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

						("Found one VitreoNodeProxy : " ++ possibleProxySymbolName.asString).postln;
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

		var isNextProxyAProxy, interpolationProxyEntry, thisParamEntryInNextProxy, paramRate, hasInterpolationProxyBeenCreated;

		var opProxy;

		var interpolationProxy;

		var allProxiesDict, inProxiesArray;

		isNextProxyAProxy = (nextProxy.class == VitreoNodeProxy).or(nextProxy.class.superclass == VitreoNodeProxy).or(nextProxy.class.superclass.superclass == VitreoNodeProxy);

		if((isNextProxyAProxy.not), {
			"nextProxy is not a valid VitreoNodeProxy!!!".warn;
			^this;
		});

		if(nextProxy.group == nil, {
			("nextProxy hasn't been instantiated yet!!!").warn;
			^this;
		});


		/************************/
		/* nextProxy init stuff */
		/************************/

		//This is the connection that is in place with the interpolation NodeProxy.
		thisParamEntryInNextProxy = nextProxy.inProxies[param];

		//Free previous connections to the nextProxy, if there were any
		nextProxy.freePreviousConnection(param);


		/**********************************************/
		/* Retrieve all proxies in the AbstractOpPlug */
		/**********************************************/

		//Dict containing all the NodeProxies in the OpPlug. Could it be an array instead???
		allProxiesDict = IdentityDictionary.new;

		this.findAllProxies(allProxiesDict);

		//This is what will be stored as inProxy in nextProxy
		inProxiesArray = Array.newClear(allProxiesDict.size);

		//"AbstractOpPlug's => : need to add all the logic now".warn;

		//allProxiesDict.postln;

		/***********************************************/
		/* Create interpProxy  for nextProxy if needed */
		/***********************************************/

		//Returns true if new interp proxy is created, false if it already existed. Pass the Op in as source to use.
		nextProxy.createNewInterpProxyIfNeeded(param, this);

		//This has been created by now, or just retrieved. It now stores the AbstractOpPlug function instead of standard interpolationProxy's stuff
		interpolationProxy = nextProxy.interpolationProxies[param];

		// Deal with inProxies and outProxies stuff
		allProxiesDict.do({
			arg proxyInOp, index;

			//Check all proxies are on same server as nextProxy's
			if(proxyInOp.server != nextProxy.server, {
				"nextProxy is on a different server!!!".warn;
				^this;
			});

			if(proxyInOp.group == nil, {
				("This proxy hasn't been instantiated yet!!!").warn;
				^this;
			});

			//Create a new block if needed. All successive loop entries will be added to the same block.
			proxyInOp.createNewBlockIfNeeded(nextProxy);

			//Set the outProxy for the proxy. Don't use param name as it could be linked to multiple nextProxies.
			proxyInOp.outProxies.put(nextProxy, nextProxy);

			index.postln;

			inProxiesArray[index] = proxyInOp;
		});

		//Use interpolationProxy as gateway!!!
		//interpolationProxy.source = this;

		//Set the proxies array as inProxy entry for nextProxy
		nextProxy.inProxies.put(param, inProxiesArray);

		//Also add connections for interpolationProxy for this param
		interpolationProxy.inProxies.put(\in, inProxiesArray);

		//REARRANGE BLOCK!...
		//this needs server syncing (since the interpolationProxy's group needs to be instantiated on server)
		VitreoBlocksDict.blocksDict[nextProxy.blockIndex].rearrangeBlock(nextProxy.server);


		//With fade: with modulated proxy at the specified param
		nextProxy.connectXSet(interpolationProxy, param);


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
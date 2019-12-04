+ IdentityDictionary {

	//To loop over all inProxies for a ANProxy, including if they are Array and treating them normally.
	//Alias for do
	doProxiesLoop {
		arg function;

		this.keysValuesDo({
			arg key, value, i;

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

	//To loop over all inProxies for a ANProxy, including if they are Array and treating them normally.
	//Alias for keysValuesDo
	keysValuesDoProxiesLoop {
		arg function;

		this.keysValuesDo({
			arg key, value, i;

			if(value.class == Array, {
				value.do({
					arg entry;
					function.value(key, entry, i);
				});
			}, {
				function.value(key, value, i);
			});
		});
	}
}

//Fixes: https://github.com/supercollider/supercollider/issues/4311
+ ProxyNodeMap {

	controlNames {
		var res = Array.new;

		//"NODE MAP EXTENSION".warn;

		this.keysValuesDo { |key, value|
			var rate = if(value.rate == \audio) { \audio } { \control };
			res = res.add(ControlName(key, nil, rate, value))
		};

		^res
	}

}


/*
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
*/

/*

+ Number {

	=> {
		arg nextProxy, param = \in;

		"Number's => : need to add all the logic".warn;

		^nextProxy;
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

		^this;
	}
}

+ Function {
	=> {
		arg nextProxy, param = \in;

		var isNextProxyAProxy, interpolationProxyEntry, thisParamEntryInNextProxy, paramRate, hasInterpolationProxyBeenCreated;

		var functionProxy;

		var interpolationProxy;

		var allProxiesDict, functionProxiesArray;

		isNextProxyAProxy = (nextProxy.class == AlgaNodeProxy).or(nextProxy.class.superclass == AlgaNodeProxy).or(nextProxy.class.superclass.superclass == AlgaNodeProxy);

		if((isNextProxyAProxy.not), {
			"nextProxy is not a valid AlgaNodeProxy!!!".warn;
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

		//Create a new block if needed
		nextProxy.createNewBlockIfNeeded(nextProxy);

		/****************************************/
		/* Retrieve all proxies in the Function */
		/****************************************/

		//Dict containing all the NodeProxies in the OpPlug. Could it be an array instead???
		allProxiesDict = IdentityDictionary.new;
		this.findAllProxies(nextProxy, nextProxy.server, allProxiesDict);

		/***********************************************/
		/* Create interpProxy  for nextProxy if needed */
		/***********************************************/

		//Returns true if new interp proxy is created, false if it already existed. Pass the Op in as source to use.
		nextProxy.createNewInterpProxyIfNeeded(param, this);

		//This has been created by now, or just retrieved. It now stores the AbstractOpPlug function instead of standard interpolationProxy's stuff
		interpolationProxy = nextProxy.interpolationProxies[param];

		//This is what will be stored as inProxy in nextProxy
		functionProxiesArray = this.createAndPopulateFunctionProxiesArray(allProxiesDict, nextProxy);

		//Set the proxies array as inProxy entry for nextProxy
		nextProxy.inProxies.put(param, functionProxiesArray);

		//Also add connections for interpolationProxy for this param
		interpolationProxy.inProxies.put(\in, functionProxiesArray);

		"mhhhh".postln;

		nextProxy.blockIndex.postln;

		//REARRANGE BLOCK!...
		//this needs server syncing (since the interpolationProxy's group needs to be instantiated on server)
		AlgaBlocksDict.blocksDict[nextProxy.blockIndex].rearrangeBlock(nextProxy.server);


		//With fade: with modulated proxy at the specified param
		nextProxy.connectXSet(interpolationProxy, param);


		^nextProxy;
	}

	//To allow ~c = {~a * 0.5}, ensuring ~a is before ~c
	putObjBefore {

		arg targetProxy, index;

		var server, allProxiesDict, functionProxiesArray;

		server = targetProxy.server;

		allProxiesDict = IdentityDictionary.new;
		this.findAllProxies(targetProxy, server, allProxiesDict);

		functionProxiesArray = this.createAndPopulateFunctionProxiesArray(allProxiesDict, targetProxy);

		if(index == nil, {index = \ALL});

		//Set the proxies array as inProxy entry for nextProxy... Special symbol name to store the ins to
		if(functionProxiesArray.size > 0, {
			targetProxy.inProxies.put(\___SPECIAL_ASSIGNMENT___ ++ index.asSymbol, functionProxiesArray);
		});

		//outProxies are already assigned in createAndPopulateFunctionProxiesArray

		^this;
	}

	createAndPopulateFunctionProxiesArray {
		arg allProxiesDict, nextProxy;

		var functionProxiesArray = Array.newClear(allProxiesDict.size);

		// Deal with inProxies and outProxies stuff
		allProxiesDict.do({
			arg functionProxyEntry, index;

			//Check all proxies are on same server as nextProxy's
			if(functionProxyEntry.server != nextProxy.server, {
				"nextProxy is on a different server!!!".warn;
				^nil;
			});

			/*
			if(functionProxyEntry.group == nil, {
				("This proxy hasn't been instantiated yet!!!").warn;
				^nil;
			});
			*/

			//functionProxyEntry.group.postln;
			//functionProxyEntry.outProxies.postln;

			//Create a new block if needed. All successive loop entries will be added to the same block.
			functionProxyEntry.createNewBlockIfNeeded(nextProxy);

			//Set the outProxy for the proxy. Don't use param name as it could be linked to multiple nextProxies.
			functionProxyEntry.outProxies.put(nextProxy, nextProxy);

			//Add entry to the array
			functionProxiesArray[index] = functionProxyEntry;
		});

		^functionProxiesArray;
	}

	findAllProxies {
		arg targetProxy, server, dict;

		var funcDef = this.def;

		//constants will also show params, but if there is a NodeProxy
		//of some kind, it's been created on the relative ProxySpace.
		//It can be retrieved with: AlgaProxySpace.findSpace(~d);
		var possibleProxies = funcDef.constants;

		if(possibleProxies.size > 0, {
			var proxySpace;
			var isTargetProxyAnVNdef = (targetProxy.class.superclass == AlgaNodeProxy).or(targetProxy.class.superclass.superclass == AlgaNodeProxy);

			//"possibleProxies: ".postln;

			//possibleProxies.postln;

			if(isTargetProxyAnVNdef.not, {
				//A AlgaNodeProxy
				proxySpace = AlgaProxySpace.findSpace(targetProxy);

			}, {
				//A AlgaNdef
				proxySpace = VNdef.all.at(server.name)
			});

			//Search if the proxies in the func body are actually in same proxy space as the target
			if(proxySpace != nil, {
				possibleProxies.do({

					arg possibleProxySymbolName;

					//Check if the possibleProxy is in the proxySpace
					var nodeProxy = proxySpace[possibleProxySymbolName];

					//Non-valid symbols will return a AlgaNodeProxy with nil channels
					if(nodeProxy.numChannels != nil, {

						("Found one AlgaNodeProxy : " ++ possibleProxySymbolName.asString).postln;
						dict.put(possibleProxySymbolName, nodeProxy);
					});
				})
			});
		});
	}

}

+ AbstractOpPlug {

	//To allow (~a * 0.5) => ~b (and viceversa, if called with this being a VNProxy)
	=> {
		arg nextProxy, param = \in;

		var isNextProxyAProxy, interpolationProxyEntry, thisParamEntryInNextProxy, paramRate, hasInterpolationProxyBeenCreated;

		var opProxy;

		var interpolationProxy;

		var allProxiesDict, opProxiesArray;

		isNextProxyAProxy = (nextProxy.class == AlgaNodeProxy).or(nextProxy.class.superclass == AlgaNodeProxy).or(nextProxy.class.superclass.superclass == AlgaNodeProxy);

		if((isNextProxyAProxy.not), {
			"nextProxy is not a valid AlgaNodeProxy!!!".warn;
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

		//Create a new block if needed
		nextProxy.createNewBlockIfNeeded(nextProxy);

		/**********************************************/
		/* Retrieve all proxies in the AbstractOpPlug */
		/**********************************************/

		//Dict containing all the NodeProxies in the OpPlug. Could it be an array instead???
		allProxiesDict = IdentityDictionary.new;

		this.findAllProxies(allProxiesDict);

		/***********************************************/
		/* Create interpProxy  for nextProxy if needed */
		/***********************************************/

		//Returns true if new interp proxy is created, false if it already existed. Pass the Op in as source to use.
		nextProxy.createNewInterpProxyIfNeeded(param, this);

		//This has been created by now, or just retrieved. It now stores the AbstractOpPlug function instead of standard interpolationProxy's stuff
		interpolationProxy = nextProxy.interpolationProxies[param];

		//This is what will be stored as inProxy in nextProxy
		opProxiesArray = this.createAndPopulateOpProxiesArray(allProxiesDict, nextProxy);

		//Set the proxies array as inProxy entry for nextProxy
		nextProxy.inProxies.put(param, opProxiesArray);

		//Also add connections for interpolationProxy for this param
		interpolationProxy.inProxies.put(\in, opProxiesArray);

		//REARRANGE BLOCK!...
		//this needs server syncing (since the interpolationProxy's group needs to be instantiated on server)
		AlgaBlocksDict.blocksDict[nextProxy.blockIndex].rearrangeBlock(nextProxy.server);


		//With fade: with modulated proxy at the specified param
		nextProxy.connectXSet(interpolationProxy, param);


		^nextProxy;
	}

	createAndPopulateOpProxiesArray {
		arg allProxiesDict, nextProxy;

		var opProxiesArray = Array.newClear(allProxiesDict.size);

		// Deal with inProxies and outProxies stuff
		allProxiesDict.do({
			arg opProxyEntry, index;

			//Check all proxies are on same server as nextProxy's
			if(opProxyEntry.server != nextProxy.server, {
				"nextProxy is on a different server!!!".warn;
				^this;
			});

			/*
			if(opProxyEntry.group == nil, {
				("This proxy hasn't been instantiated yet!!!").warn;
				^this;
			});
			*/

			//Create a new block if needed. All successive loop entries will be added to the same block.
			opProxyEntry.createNewBlockIfNeeded(nextProxy);

			//Set the outProxy for the proxy. Don't use param name as it could be linked to multiple nextProxies.
			opProxyEntry.outProxies.put(nextProxy, nextProxy);

			opProxiesArray[index] = opProxyEntry;
		});

		^opProxiesArray;

	}

	//To allow ~c = ~a * 0.5, ensuring ~a is before ~c
	putObjBefore {

		arg targetProxy, index;

		var allProxiesDict, opProxiesArray;

		allProxiesDict = IdentityDictionary.new;

		this.findAllProxies(allProxiesDict);

		"To be implemented...".warn;

		//opProxiesArray = this.createAndPopulateOpProxiesArray(allProxiesDict, targetProxy);

		//if(index == nil, {index = \ALL});

		//Set the proxies array as inProxy entry for nextProxy... Special symbol name to store the ins to

		//targetProxy.inProxies.put(\___SPECIAL_ASSIGNMENT___ ++ index.asSymbol, opProxiesArray);

		//outProxies are already assigned in createAndPopulateFunctionProxiesArray

		^this;
	}

}

+ BinaryOpPlug {

	//This works recursively if a or b are another Ops
	findAllProxies {
		arg dict;

		var firstOp = this.a;
		var isFirstOpAnOp = firstOp.class.superclass == AbstractOpPlug;

		var secondOp = this.b;
		var isSecondOpAnOp = secondOp.class.superclass == AbstractOpPlug;

		if(isFirstOpAnOp, {

			firstOp.findAllProxies(dict);

		}, {

			var isFirstOpAProxy = (firstOp.class == AlgaNodeProxy).or(firstOp.class.superclass == AlgaNodeProxy).or(firstOp.class.superclass.superclass == AlgaNodeProxy);

			if(isFirstOpAProxy, {
				//Add to dict, found a proxy
				dict.put(firstOp, firstOp);
			});

		});

		if(isSecondOpAnOp, {

			secondOp.findAllProxies(dict);

		}, {

			var isSecondOpAProxy = (secondOp.class == AlgaNodeProxy).or(secondOp.class.superclass == AlgaNodeProxy).or(secondOp.class.superclass.superclass == AlgaNodeProxy);

			if(isSecondOpAProxy, {
				//Add to dict, found a proxy
				dict.put(secondOp, secondOp);
			});

		});
	}

}



+ UnaryOpPlug {

	//Need to define the getter for a
	a {
		^a;
	}

	//This works recursively if a is another Op
	findAllProxies {
		arg dict;

		var firstOp = this.a;
		var isFirstOpAnOp = firstOp.class.superclass == AbstractOpPlug;

		if(isFirstOpAnOp, {

			firstOp.findAllProxies(dict);

		}, {

			var isFirstOpAProxy = (firstOp.class == AlgaNodeProxy).or(firstOp.class.superclass == AlgaNodeProxy).or(firstOp.class.superclass.superclass == AlgaNodeProxy);

			if(isFirstOpAProxy, {

				//Add to dict, found a proxy
				dict.put(firstOp, firstOp);
			});

		});
	}

}


*/
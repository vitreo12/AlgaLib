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

//To allow ~a * 0.5 => ~b syntax
+ BinaryOpPlug {

	=> {
		arg nextProxy, param = \in;

		//Find all proxies recursively for this binary operations
		var allProxiesDict = IdentityDictionary.new;

		//"Binary!!".warn;

		this.findAllProxies(allProxiesDict);

		allProxiesDict.postln;

		"Need to add all the logic now".warn;

		^this;

	}

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

	=> {
		arg nextProxy, param = \in;

		//Find all proxies recursively for this unary operation
		var allProxiesDict = IdentityDictionary.new;

		//"Unary!!".warn;

		this.findAllProxies(allProxiesDict);

		allProxiesDict.postln;

		"Need to add all the logic now".warn;

		^this;

	}

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
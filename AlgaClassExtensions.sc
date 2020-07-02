+Dictionary {
	//Loop over a Dict, unpacking Set. It's used in AlgaBlock
	//to unpack inNodes of an AlgaNode
	nodesLoop { | function |
		this.keysValuesDo({
			arg key, value, i;
			if(value.class == Set, {
				value.do({ | entry |
					function.value(entry, i);
				});
			}, {
				function.value(value, i);
			});
		});
	}
}

+Object {
	isAlgaNode { ^false }
	instantiated { ^true }
	isNumberOrArray { ^((this.isNumber).or(this.isArray)) }
}
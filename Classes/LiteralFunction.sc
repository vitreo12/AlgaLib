LiteralFunctions {
	classvar funcs;

	*initClass {
		funcs = IdentitySet(10)
	}

	*add { | func |
		funcs.add(func)
	}

	*remove { | func |
		funcs.remove(func)
	}

	*includes { | func |
		^funcs.includes(func)
	}
}

+Function {
	literalFunc { LiteralFunctions.add(this) }

	//Alias
	litFunc { this.literalFunc }

	//Alias
	lf { this.literalFunc }

	isLiteralFunction { ^LiteralFunctions.includes(this) }
}
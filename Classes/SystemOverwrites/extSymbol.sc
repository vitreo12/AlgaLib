// AlgaLib: SuperCollider implementation of Alga, an interpolating live coding environment.
// Copyright (C) 2020-2022 Francesco Cameli.

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

//Support for AlgaNode and AlgaArg
+Symbol {
	ar { | val, lag, spec |
		if((val.isAlgaNode).or(val.isAlgaArg), {
			AlgaProxySpace.addParamArgs(this, val);
			if(AlgaProxySpace.isTriggerDef.not, {
				^NamedControl.ar(this, 0, lag, spec);
			}, {
				^DC.ar(0)
			});
		});
		if(AlgaProxySpace.isTriggerDef.not, {
			^NamedControl.ar(this, val, lag, spec)
		}, {
			^DC.ar(0)
		});
	}

	kr { | val, lag, fixedLag = false, spec |
		if((val.isAlgaNode).or(val.isAlgaArg), {
			AlgaProxySpace.addParamArgs(this, val);
			if(AlgaProxySpace.isTriggerDef.not, {
				^NamedControl.kr(this, 0, lag, fixedLag, spec)
			}, {
				^DC.kr(0)
			});
		});
		if(AlgaProxySpace.isTriggerDef.not, {
			^NamedControl.kr(this, val, lag, fixedLag, spec)
		}, {
			^DC.kr(0)
		});
	}
}

//Fixes for bugs when doing def.value in AlgaProxySpace.triggerDef
+Nil {
	//Symbol
	addAr { ^nil }
	addKr { ^nil }
	addTr { ^nil }

	//LocalBuf
	maxLocalBufs { ^nil }
	maxLocalBufs_ { }
}

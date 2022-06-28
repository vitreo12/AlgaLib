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

//Store the Env -> Buffer pairs used for envShape
AlgaDynamicEnvelopes {
	classvar <>numPreAllocatedBuffers = 1;
	classvar <preAllocatedBuffersCount;

	classvar <envs;
	classvar <preAllocatedBuffers;

	*initEnvs { | server |
		envs = IdentityDictionary(2);
		preAllocatedBuffers = IdentityDictionary(numPreAllocatedBuffers);
		numPreAllocatedBuffers.do({ | i |
			//+1 just like Env
			//+1 for the extra 987654321.0
			preAllocatedBuffers[i] = Buffer.alloc(server, (Alga.maxEnvPoints * 4) + 2);
		});
		preAllocatedBuffersCount = 0;
	}

	*add { | env, server |
		var envsAtServer;
		if(env.isKindOf(Env).not, { ^nil });
		server = server ? Server.default;
		envsAtServer = envs[server];
		if(envsAtServer == nil, {
			//Can't be IdentityDictionary for Env == Env
			envsAtServer = Dictionary(256);
			envs[server] = envsAtServer;
		});
		if(envsAtServer[env] == nil, {
			var envAsArray = env.algaAsArray;
			var buffer;
			if(this.isNextBufferPreAllocated, {
				//preAllocatedBuffers won't require any .sync
				buffer = preAllocatedBuffers[preAllocatedBuffersCount];
				buffer.setn(0, envAsArray ++ 987654321.0);
				preAllocatedBuffersCount = preAllocatedBuffersCount + 1;
			}, {
				buffer = Buffer.sendCollection(server, envAsArray);
			});
			envsAtServer[env] = buffer;
			^buffer;
		});
		^envsAtServer[env];
	}

	//TODO: free unused envelope Buffers automatically
	*remove { | env, server |
		var envsAtServer;
		server = server ? Server.default;
		envsAtServer = envs[server];
		if(envsAtServer != nil, {
			if(envsAtServer[env].isBuffer, {
				envsAtServer[env].free;
				envsAtServer.removeAt(env);
			});
		});
	}

	*getOrAdd { | env, server |
		var val = (this.get(env, server) ? this.add(env, server)) ? this.get(Env([0, 1], 1), server);
		if(val.isBuffer, { ^val }, { -1 });
	}

	*get { | env, server |
		^(envs[server][env])
	}

	*isNextBufferPreAllocated {
		^(preAllocatedBuffersCount < numPreAllocatedBuffers);
	}
}
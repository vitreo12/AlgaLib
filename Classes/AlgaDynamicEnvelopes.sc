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
	classvar <envs;

	*initEnvs {
		envs = IdentityDictionary(2);
	}

	*add { | env, server |
		var envsAtServer;
		server = server ? Server.default;
		envsAtServer = envs[server];
		if(envsAtServer == nil, {
			//Can't be IdentityDictionary for Env == Env
			envsAtServer = Dictionary(256);
			envs[server] = envsAtServer;
		});
		if(envsAtServer[env] == nil, {
			var buffer = Buffer.sendCollection(server, env.algaAsArray);
			envsAtServer[env] = buffer;
			^buffer;
		});
		^envsAtServer[env];
	}

	//TODO: free unused envelope Buffers
	*remove { | env |
		if(envs[env].isBuffer, {
			envs[env].free;
			envs.removeAt(env);
		});
	}

	*get { | env, server |
		var val = (envs[server][env]) ? this.add(env, server);
		if(val.isBuffer, { ^val }, { -1 });
	}
}
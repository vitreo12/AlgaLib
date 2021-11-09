// AlgaLib: SuperCollider implementation of Alga, an interpolating live coding environment.
// Copyright (C) 2020-2021 Francesco Cameli.

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

//Like AlgaNode, but in a custom group at the top.
AlgaMod : AlgaNode {
	isAlgaMod { ^true }

	createAllGroups {
		//THIS IS ESSENTIAL FOR PROPER WORKING!!
		var parGroup = Alga.modParGroup(server);

		//THIS ONE MUST BE GROUP AND NOT PARGROUP FOR ORDERING TO WORK CORRECTLY!!
		group = AlgaGroup(parGroup);

		//Keep playGroup as Group: no need to multithread here
		playGroup = AlgaGroup(group);

		synthGroup = AlgaGroup(group);
		//synthGroup = AlgaParGroup(group);

		normGroup = AlgaGroup(group);
		//normGroup = AlgaParGroup(group);

		tempGroup = AlgaGroup(group);
		//tempGroup = AlgaParGroup(group);

		interpGroup = AlgaGroup(group);
		//interpGroup = AlgaParGroup(group);
	}
}

//Alias
AM : AlgaMod { }
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

//Play and dispatch streams to registered AlgaPatterns
AlgaPatternPlayer {

	//Parse AlgaTemps like AlgaPattern!

	//Advance entries

	//Store entries in AlgaReaders (these will be read from the AlgaPatterns)

	isAlgaPatternPlayer { ^true }
}

//alias
AlgaPlayer : AlgaPatternPlayer {}

//alias
APP : AlgaPatternPlayer {}

//Used under the hood in AlgaPattern to read from an AlgaPatternPlayer
AlgaReader {
	isAlgaReader { ^true }
}
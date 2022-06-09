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

/*
AlgaInterpreter {
	classvar oldPreProcessor;

	//Add the AlgaInterpreter to the sclang Interpreter
	*push {
		oldPreProcessor = thisProcess.interpreter.preProcessor;
		thisProcess.interpreter.preProcessor = { | code |
			//Also run the old preProcessor if defined ?
			//if(oldPreProcessor.isFunction, {
			//	code = oldPreProcessor.value(code)
			//});

			//Run Alga's interpreter
			this.interpret(code);
		}
	}

	//Return the old preProcessor function
	*pop {
		thisProcess.interpreter.preProcessor = oldPreProcessor;
	}

	//String -> sclang code
	*interpret { | code |
		code.postln;
		^code
	}
}

//Alias
AI : AlgaInterpreter { }

/*
AlgaInterpreter.push

(
//Alga
~d1 \sine freq << [~d2 \noise amp << [~d4 \lfo >> freq ~d3 \blip]]

//SC (code between --- will be ignored by the AlgaInterpreter)
---
~d5 = { Saw.ar(\freq.kr(440)) };
~d1 <<+.freq ~d5;
---
)
*/
*/
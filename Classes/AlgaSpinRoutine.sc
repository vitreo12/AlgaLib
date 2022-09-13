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

AlgaSpinRoutine {
	*waitFor { | condition, func, breakCondition, interval = 0.01, maxTime = 5 |
		if(condition.class != Function, {
			"waitFor only accepts a function as condition".error;
			^nil;
		});

		if(func.class != Function, {
			"waitFor only accepts a function as func".error;
			^nil;
		});

		if(condition.value, { ^func.value });

		if(breakCondition == nil, { breakCondition = { false } });

		//Spin around condition, once true, execute func
		fork {
			var accumTime = 0;
			var break = false;

			while( { condition.value.not }, {
				if(breakCondition.value, { condition = { true }; break = true; });
				interval.wait;
				accumTime = accumTime + interval;
				if(maxTime != nil, {
					if(accumTime >= maxTime, {
						("AlgaSpinRoutine: exceeded maximum wait time: " ++ maxTime).error;
						condition = { true }; break = true;
					});
				});
			});

			if(break.not, { func.value });
		}
	}
}
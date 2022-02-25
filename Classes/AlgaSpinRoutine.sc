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
	*waitFor { | condition, func, interval = 0.005, maxTime = 5 |
		//First, check if condition is true already, no need to go into a routine if that's the case
		if(condition.value, {
			func.value;
		}, {
			//Spin around condition, then execute onComplete, if not exceeding maximum wait time
			fork {
				var accumTime = 0;
				var exceededMaxTime = false;

				while( { condition.value.not }, {
					interval.wait;
					accumTime = accumTime + interval;
					if(maxTime != nil, {
						if(accumTime >= maxTime, {
							"AlgaSpinRoutine: exceeded maximum wait time".error;
							exceededMaxTime = true;
							condition = { true };
						});
					});
				});

				if(exceededMaxTime.not, {
					func.value;
				});
			}
		});
	}
}

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

//All functions related to adding actions to the AlgaScheduler
AlgaActionScheduler {
	var <obj;
	var <scheduler;
	var <scheduledStepActionsPre, <scheduledStepActionsPost;

	/*
	Required vars:

	AlgaNode / AlgaPattern / AlgaPatternPlayer:
	server, schedInSeconds
	*/

	*new { | obj, scheduler |
		^super.new.init(obj, scheduler)
	}

	init { | argObj, argScheduler |
		obj = argObj;
		scheduler = argScheduler;
	}

	//If needed, it will compile the AlgaSynthDefs in functionSynthDefDict and wait before executing func.
	//Otherwise, it will just execute func
	compileFunctionSynthDefDictIfNeeded { | func, functionSynthDefDict |
		//If functionSynthDefDict has elements, it means that there are AlgaSynthDefs with Functions to be waited for
		if(functionSynthDefDict != nil, {
			if(functionSynthDefDict.size > 0, {
				var wait = Condition();

				fork {
					//Loop over and compile Functions and AlgaTemps
					functionSynthDefDict.keysValuesDo({ | name, sdef |
						//AlgaTemp
						if(sdef.isArray, {
							var algaTemp = sdef[1];
							sdef = sdef[0];
							sdef.sendAndAddToGlobalDescLib(obj.server);
							algaTemp.checkValidSynthDef(name);
						}, {
							//Just AlgaSynthDef
							sdef.sendAndAddToGlobalDescLib(obj.server);
						})
					});

					//Unlock condition
					obj.server.sync(wait);
				};

				this.addAction(
					condition: { wait.test == true },
					func: { func.value },
					preCheck: true //execute right away if possible (most unlikely)
				);

				^this
			});
		});

		//No Functions to consume
		^func.value;
	}

	//Add an action to scheduler. This takes into account sched == AlgaStep
	addAction { | condition, func, sched = 0, topPriority = false, preCheck = false |
		//AlgaStep scheduling.
		//I reverted the support for topPriority for one simple reason: it would only be used for replace.
		//As such, the problem is that any connection that was scheduled at the same time won't happen anyways,
		//cause the .replaced node won't be algaInstatiated, and the connection would not be made until the next round.
		//It's probably better to just keep the user's declaration order.
		if(sched.isAlgaStep, {
			if((obj.isAlgaPattern).or(obj.isAlgaPatternPlayer), {
				this.addScheduledStepAction(
					step: sched,
					condition: condition,
					func: func
				);
			}, {
				//AlgaNodes don't support AlgaStep
				sched = sched.step;
				("AlgaNode: only AlgaPatterns support AlgaStep actions. Scheduling action at " ++ sched).error;
				scheduler.addAction(
					condition: condition,
					func: func,
					sched: sched,
					topPriority: topPriority,
					preCheck: preCheck,
					schedInSeconds: obj.schedInSeconds
				)
			});
		}, {
			//Normal scheduling (sched is a number or AlgaQuant)
			scheduler.addAction(
				condition: condition,
				func: func,
				sched: sched,
				topPriority: topPriority,
				preCheck: preCheck,
				schedInSeconds: obj.schedInSeconds
			)
		});
	}

	//Iterate through all scheduledStepActions and execute them accordingly
	advanceAndConsumeScheduledStepActions { | post = false |
		var stepsToRemove = IdentitySet();

		//Pre or post
		var scheduledStepActions;
		if(post.not, {
			scheduledStepActions = scheduledStepActionsPre;
		}, {
			scheduledStepActions = scheduledStepActionsPost;
		});

		//Go ahead with the advancing + removal
		if(scheduledStepActions.size > 0, {
			scheduledStepActions.do({ | step |
				var condition = step.condition;
				var func = step.func;
				var retryOnFailure = step.retryOnFailure;
				var tries = step.tries;
				var stepCount = step.step;

				if(stepCount <= 0, {
					if(condition.value, {
						func.value;
						stepsToRemove.add(step);
					}, {
						if(retryOnFailure.not, {
							stepsToRemove.add(step);
						}, {
							if(tries <= 0, {
								stepsToRemove.add(step);
							}, {
								step.tries = tries - 1;
							});
						});
					});
				});

				step.step = stepCount - 1;
			});
		});

		//stepsToRemove is needed or it won't execute two consecutive true
		//functions if remove was inserted directly in the call earlier
		if(stepsToRemove.size > 0, {
			stepsToRemove.do({ | step | scheduledStepActions.remove(step) })
		});
	}

	//Creates a new AlgaStep with set condition and func
	addScheduledStepAction { | step, condition, func |
		//A new action must be created, otherwise, if two addActions are being pushed
		//with the same AlgaStep, only one of the action would be executed (the last one),
		//as the entry would be overwritten in the OrderedIdentitySet
		var newStep = step.copy;
		var post = step.post;
		var scheduledStepActions;
		newStep.condition = condition ? { true };
		newStep.func = func;

		//Create if needed
		if(post.not, {
			scheduledStepActionsPre = scheduledStepActionsPre ? OrderedIdentitySet(10);
			scheduledStepActions = scheduledStepActionsPre;
		}, {
			scheduledStepActionsPost = scheduledStepActionsPost ? OrderedIdentitySet(10);
			scheduledStepActions = scheduledStepActionsPost;
		});

		//Add
		scheduledStepActions.add(newStep)
	}
}
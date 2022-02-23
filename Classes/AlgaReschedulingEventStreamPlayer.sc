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

//https://scsynth.org/t/set-patterns-dur-right-away/3103/9
AlgaReschedulingEventStreamPlayer {
	var <player;

	*new { | stream, event |
		^super.new.init(stream, event)
	}

	init { | stream, event |
		player = EventStreamPlayer(stream, event);
	}

	play { | argClock, doReset = false, quant |
		player.play(argClock, doReset, quant)
	}

	stop { player.stop }
	reset { player.reset }
	refresh { player.refresh }

	//Stops and remove all actions that relate to the same EventStreamPlayer
	//that were scheduled for the exact time of the stopping.
	stopAtTopPriority { | when = 0 |
		var clock  = player.clock;
		clock.algaSchedAtQuantOnceWithTopPriority(when, {
			var queue = clock.queue;
			this.stop; //Stop first
			if(queue.size > 0, {
				forBy(1, queue.size-1, 3, { | i |
					var currentTime  = queue[i];
					var currentEntry = queue[i + 1];

					//If time and entry both match, remove it
					if((clock.beats == currentTime).and(currentEntry == player), {
						//Remove the 3 entries that belong to the EventStreamPlayer
						//being scheduled at this time. The array shifts back.
						//The - 1 is needed as forBy starts from 1, and first element
						//is the index of the queue
						3.do({ queue.removeAt(i - 1) });
					});
				});
			});
		})
	}

	reschedule { | when = 0, func |
		var stream = player.stream;
		var clock  = player.clock;

		clock.algaSchedOnceWithTopPriority(when, {
			player.stop;
			this.init(stream, player.event);
			if(func != nil, { func.value });
			//play has some overhead, find the leanest way.
			//Check TempoClockPriority.scd: the example with nested clock shows what happens here.
			//timing is correct: even if in top priority, play will be pushed to bottom, as it has nested
			//clock calls. However, it still is executed at the right precise timing
			player.play(clock, quant:0);
		});
	}

	rescheduleAtQuant { | quant = 0, func |
		var stream = player.stream;
		var clock  = player.clock;

		clock.algaSchedAtQuantOnceWithTopPriority(quant, {
			player.stop;
			this.init(stream, player.event);
			if(func != nil, { func.value });
			//play has some overhead, find the leanest way.
			//Check TempoClockPriority.scd: the example with nested clock shows what happens here.
			//timing is correct: even if in top priority, play will be pushed to bottom, as it has nested
			//clock calls. However, it still is executed at the right precise timing
			player.play(clock, quant:0);
		});
	}

	rescheduleAbs { | when = 0, func |
		var stream = player.stream;
		var clock  = player.clock;

		//TempoClock's schedAbs still expect beats, I need seconds here
		if(clock.isTempoClock, { clock = SystemClock });

		//absolute scaling
		when = clock.seconds + when;

		clock.schedAbs(when, {
			player.stop;
			this.init(stream, player.event);
			if(func != nil, { func.value });
			//play has some overhead, find the leanest way.
			//Check TempoClockPriority.scd: the example with nested clock shows what happens here.
			//timing is correct: even if in top priority, play will be pushed to bottom, as it has nested
			//clock calls. However, it still is executed at the right precise timing
			player.play(player.clock, quant:0); //still play on its former clock
		});
	}

	stream { player.stream }
	asEventStreamPlayer { ^player }
	canPause { ^player.canPause }
	event { ^player.event }
	event_ { | event | player.event_(event) }
	mute { player.mute }
	unmute { player.unmute }
	muteCount { ^player.muteCount }
	muteCount_ { | count | player.muteCount_(count) }
}

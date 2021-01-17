//https://scsynth.org/t/set-patterns-dur-right-away/3103/9
ReschedulingEventStreamPlayer {
	var <player;
	var <lastTime;

	*new { | stream, event |
		^super.new.init(stream, event)
	}

	init { | stream, event |
		player = EventStreamPlayer(stream.collect { | event |
			lastTime = thisThread.beats;
			event
		}, event);
	}

	// hmm, haven't handled dependent notifications here
	play { | argClock, doReset = false, quant |
		player.play(argClock, doReset, quant)
	}
	stop { player.stop }
	reset { player.reset }
	refresh { player.refresh }

	rescheduleAbs { | newBeats |
		var stream = player.stream;
		var clock = player.clock;
		player.stop;
		player = EventStreamPlayer(stream, player.event).refresh;
		clock.schedAbs(newBeats, player);
	}

	reschedule { | when |
		this.rescheduleAbs(lastTime + when);
	}

	algaSchedAtQuantOnce { | quant, task, offset = 0.0000000000000569 |
		player.clock.algaSchedAtQuantOnce(quant, task, offset);
	}

	algaSchedAtQuant { | quant, task, offset = 0.0000000000000569 |
		player.clock.algaSchedAtQuant(quant, task, offset);
	}

	algaSchedOnce { | when, task, offset = 0.0000000000000569 |
		player.clock.algaSchedOnce(when, task, offset);
	}

	algaSched { | when, task, offset = 0.0000000000000569 |
		player.clock.algaSched(when, task, offset);
	}

	stream { player.stream }
	asEventStreamPlayer {}
	canPause { ^player.canPause }
	event { ^player.event }
	event_ { | event | player.event_(event) }
	mute { player.mute }
	unmute { player.unmute }
	muteCount { ^player.muteCount }
	muteCount_ { | count | player.muteCount_(count) }
}

+ Pattern {
	playRescheduling { | clock, protoEvent, quant |
		clock = clock ? TempoClock.default;
		^ReschedulingEventStreamPlayer(this.asStream, protoEvent)
		.play(clock, false, quant)
	}
}

+ Clock {
	//offset allows to execute it slightly before quant!
	//0.0000000000000569 is the smallest number i could find that works
	algaSchedAtQuantOnce { | quant, task, offset = 0.0000000000000569 |
		if(this.isTempoClock, {
			this.algaTempoClockSchedAtQuant(quant - offset, { task.value; nil });
		}, {
			this.algaSchedOnce(quant, task, offset)
		});
	}

	//offset allows to execute it slightly before quant!
	//0.0000000000000569 is the smallest number i could find that works
	algaSchedAtQuant { | quant, task, offset = 0.0000000000000569 |
		if(this.isTempoClock, {
			this.algaTempoClockSchedAtQuant(quant - offset, task);
		}, {
			this.algaSched(quant, task, offset)
		});
	}

	algaSchedOnce { | when, task, offset = 0.0000000000000569 |
		if(this.isTempoClock, { "TempoClock.sched will schedule after beats, not time!".warn; });
		this.sched(when - offset, { task.value; nil });
	}

	algaSched { | when, task, offset = 0.0000000000000569 |
		if(this.isTempoClock, { "TempoClock.sched will schedule after beats, not time!".warn; });
		this.sched(when - offset, task);
	}
}

+ TempoClock {
	algaTempoClockSchedAtQuant { | quant = 1, task |
		this.schedAbs(quant.nextTimeOnGrid(this), task)
	}

	isTempoClock { ^true }
}
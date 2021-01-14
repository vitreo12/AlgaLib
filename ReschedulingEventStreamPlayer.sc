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

	schedAtQuantOnce { | quant, task |
		var clock = player.clock;
		if(clock.isTempoClock, {
			clock.schedAtQuant(quant, { task.value; nil });
		}, {
			this.schedOnce(quant, task)
		});
	}

	schedAtQuant { | quant, task |
		var clock = player.clock;
		if(clock.isTempoClock, {
			clock.schedAtQuant(quant, task);
		}, {
			this.sched(quant, task)
		});
	}

	schedOnce { | when, task |
		var clock = player.clock;
		if(clock.isTempoClock, { "TempoClock.sched will schedule after beats, not time!".warn; });
		clock.sched(when, { task.value; nil });
	}

	sched { | when, task |
		var clock = player.clock;
		if(clock.isTempoClock, { "TempoClock.sched will schedule after beats, not time!".warn; });
		clock.sched(when, task);
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

+ TempoClock {
	schedAtQuant { | quant = 1, task |
		this.schedAbs(quant.nextTimeOnGrid(this), task)
	}

	isTempoClock { ^true }
}
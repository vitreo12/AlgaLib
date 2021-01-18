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

	algaSchedAtQuant { | quant, task, offset = 0.0000000000000569 |
		player.clock.algaSchedAtQuant(quant, task, offset);
	}

	algaSched { | when, task, offset = 0.0000000000000569 |
		player.clock.algaSched(when, task, offset);
	}

	stream { player.stream }
	asEventStreamPlayer { ^this }
	canPause { ^player.canPause }
	event { ^player.event }
	event_ { | event | player.event_(event) }
	mute { player.mute }
	unmute { player.unmute }
	muteCount { ^player.muteCount }
	muteCount_ { | count | player.muteCount_(count) }
}

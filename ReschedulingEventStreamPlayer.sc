//https://scsynth.org/t/set-patterns-dur-right-away/3103/9
ReschedulingEventStreamPlayer {
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

	reschedule { | when = 0 |
		var stream = player.stream;
		var clock  = player.clock;

		clock.algaSchedOnceWithTopPriority(when, {
			player.stop;
			this.init(stream, player.event);
			player.play(clock, quant:0); //play has some overhead, find the leanest way
		});
	}

	rescheduleAtQuant { | quant = 0 |
		var stream = player.stream;
		var clock  = player.clock;

		clock.algaSchedAtQuantOnceWithTopPriority(quant, {
			player.stop;
			this.init(stream, player.event);
			player.play(clock, quant:0); //play has some overhead, find the leanest way
		});
	}

	rescheduleAbs { | when = 0 |
		var stream = player.stream;
		var clock  = player.clock;

		//TempoClock's schedAbs still expect beats, I need seconds here
		if(clock.isTempoClock, {
			clock = SystemClock
		});

		//absolute scaling
		when = clock.seconds + when;

		clock.schedAbs(when, {
			player.stop;
			this.init(stream, player.event);
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
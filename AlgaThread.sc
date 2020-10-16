AlgaThread {
	classvar <>verbose = false, <defaultClock;
	var <func, <name, <clock, <task;

	*new { | func, name = "anon", clock, autostart = true |
		^super.newCopyArgs(func, name, clock).init(autostart);
	}

	*initClass {
		defaultClock = SystemClock;
	}

	init { | autostart = true |
		task = Routine({
			if(verbose, { ("AlgaThread" + name + "starts.").postcln });
			func.value;
		}, 16384);

		if(autostart, { this.start });
	}

	cmdPeriod {
		this.clear; //stop and clear the previous task
		this.init(true); //re-init the task
		if( verbose ) { ("AlgaThread" + name + "is back up.").postcln };
	}

	start {
		if(task.isPlaying, {
			if(verbose, { ("AlgaThread" + name + "already playing.").postcln });
			^this;
		});
		task.reset.play(clock ? defaultClock);
		CmdPeriod.add(this);
		if(verbose, { ("AlgaThread" + name + "started.").postcln });
	}

	play { this.start }

	stop {
		CmdPeriod.remove(this);
		if(verbose, { ("AlgaThread" + name + "stopping.").postcln });
		task.stop;
	}

	clear {
		CmdPeriod.remove(this);
		if(verbose, { ("AlgaThread" + name + "cleared.").postcln });
		task.stop;
		task.clear;
	}
}

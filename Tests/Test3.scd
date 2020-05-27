AlgaLib.boot

~a = {SinOsc.ar(\f.kr([440, 441]))}
~a.play
~a.stop

~a.ft = 5
~a.free

~b = {LFNoise0.kr(1).range(300, 1000)}

~c = {LFNoise1.ar([2, 10]).range(300, 1000)}

~a <=.f ~b

~a <=.f 440

~a <=.f ~c

~a.clear
~a.stop
~a.play

s.vol = -10
s.quit

Alga.boot({0.1.wait; s.plotTree})

(
a = AlgaNode({SinOsc.ar(\f.ar(330)).dup}, fadeTime:2);
b = AlgaNode({DC.ar(200)});
o = AlgaNode({\in.ar(0).dup}, fadeTime:1).play;
b >>.f a;
a >> o;
)

b.replace({DC.ar(400)})

a.ft = 10

b.replace({DC.ar(1000)})

//trigger this while the interpolation is happening: it wouldn't work,
//as the system is creating a whole new set of synths and patching them together
a.replace({Saw.ar(\f.ar(330) * 0.2)})
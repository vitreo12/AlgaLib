Alga.boot

(
a = AlgaNode({SinOsc.ar(\f.ar(330))}, fadeTime:2);
b = AlgaNode({DC.ar(200)});
o = AlgaNode({\in.ar(0).dup}, fadeTime:0).play;
b >>.f a;
a >> o;
)

b.replace({DC.ar(400)})

//This shouldn't make the envelope again! It should already connect
//to the final result! NO FADE TIME when doing this!
a.replace({SinOsc.ar(\f.ar(200))})
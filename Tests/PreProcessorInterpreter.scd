this.preProcessor = { |code| code.replace("~ _ ~", "Saw.ar(200)") };

{ ~ _ ~ }.def.sourceCode.postln;


this.preProcessor = { |code| code.replace("<|.", "<| \\") };

{ a <|.freq }.def.sourceCode.postln;


this.preProcessor = { |code| code.replace("~ ", " = AlgaNode") };

{ a~ (\default) }.def.sourceCode.postln;



preProcessor = nil; //reset
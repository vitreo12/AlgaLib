// AlgaInterpreter {
//     // implement language here, using Interpreter.preProcessor_ to boot it
//     // together with an Alga server
// }

// + Interpreter {
//     preProcessor_ { | what |
//         // if any Alga servers are running, don't allow changing preProcessor
//         // this is quite an "extreme" case, but necessary if imlementing the language
//         // here in sclang
//         if(Alga.servers.size > 0, {
//             "Alga is running. Can't change preProcessor. Reboot interpreter to do so.".error;
//             ^nil;
//         });

//         preProcessor = what;
//     }
// }

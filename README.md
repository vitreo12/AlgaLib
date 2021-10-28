AlgaLib 
=======

This is the *SuperCollider* implementation of *Alga*.

*Alga* is a new language for live coding that focuses on the creation and connection of sonic
modules. Unlike other audio software environments, the act of connecting *Alga* modules together is
viewed as an essential component of music composing and improvising, and not just as a mean towards
static audio patches. In *Alga*, the definition of a new connection between the output of a module
and the input of another does not happen instantaneously, but it triggers a process of *parameter
interpolation* over a specified window of time.

For usage and examples, check the *Help files* and the *Examples* folder.

###AlgaAudioControl

This *UGen* fixes some synchronization issues that may result in audio glitches for short enveloped
sounds. After installing it, no further action is required: *Alga* will detect it and use it
internally. To install the `AlgaAudioControl` *UGen* follow these simple instructions:

1. Download from https://github.com/vitreo12/AlgaAudioControl/releases/tag/v0.0.1

2. Unzip it to your *SuperCollider*'s `Platform.userExtensionDir`. 

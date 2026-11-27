# MusicMate 3.37.0

**Each speaker side tuned on its own.** A speaker or car tuning now plays the sweep through the left side and then the right at each of the five spots, and gives each side its own correction. The louder side plays quieter to match the other, and the side heard first waits a few milliseconds so both reach the centre seat together. The result screen shows each side with **Both**, **Left** and **Right**.

**Choose what the tuning aims for.** The result offers **Car**, **Room** and **Flat**. Changing it refits the same sweeps, so there is no need to measure again.

**Measurement mic calibration files.** Import the calibration file that comes with a UMIK, Dayton or similar measurement mic under **Microphone calibration**. MusicMate leaves the mic's own response out of every sweep and remembers the file for that mic.

**Steadier tunings.** The centre spot counts for half the result, and MusicMate boosts less where the five spots disagree. The mic's gain control and noise suppression are off during sweeps.

**Fixes.** Play from the car or a headset works again after Android stopped MusicMate in the background. MateTune no longer clicks between gapless tracks. The song from other players now also shows for Neutron, USB Audio Player Pro and HiBy.

**Validation.** 497 app unit tests passed, and the full speaker tuning ran on an emulator against a simulated car. Per-side tuning has not yet been tried in a real car.

Version code: 166. See [CHANGELOG.md](https://github.com/thawee/musicmate/blob/v3.37.0/CHANGELOG.md) for details.

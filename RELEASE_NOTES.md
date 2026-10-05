# MusicMate 3.25.0

Native USB bit-perfect playback is now supported on Android 14+. When enabled in Settings under Audiophile Playback, MusicMate requests direct hardware output from the Android audio mixer for connected USB DACs when the audio format (sample rate and channels) matches the DAC's capabilities.

During active bit-perfect USB sessions, audio is delivered at 100% unity gain without digital scaling or dithering, and ReplayGain volume leveling is automatically bypassed. Active status is displayed with real-time badges (`USB BIT-PERFECT`, `USB DIRECT`, or `DIRECT OUTPUT`) in Now Playing and the Studio Console.

The player dock output label now matches the Music Center, consistently displaying the name of the active audio output device (such as Phone Speaker or USB DAC).

Version code: 147. See [CHANGELOG.md](https://github.com/thawee/musicmate/blob/v3.25.0/CHANGELOG.md) for details.

# MusicMate 3.36.0

**Notification access removed.** MusicMate no longer requests notification-listener access. Integrated discovery, transport control and queue continuation through other Android players' media sessions have been removed.

**Optional Poweramp song display.** Enable **Settings → Player integrations → Show Poweramp song** to see Poweramp's reported song and playing/paused status in Music Center while MusicMate is open. Disabled by default; uses Poweramp broadcasts without notification access.

**Clear playback ownership.** Poweramp metadata appears in a separate card. MusicMate controls and MateTune apply to MusicMate playback. Local playback, DSP, ReplayGain, network outputs and explicit Open in External App file handoff remain available.

**Validation.** 449 app unit tests and 18 emulator tests passed, including real broadcast delivery, settings, playback controls and local audio playback. Installed Poweramp interoperability still needs testing on a device with Poweramp.

Version code: 165. See [CHANGELOG.md](https://github.com/thawee/musicmate/blob/v3.36.0/CHANGELOG.md) for details.

This removes notification-listener access from the APK; Google Play Protect acceptance still depends on Google's assessment.

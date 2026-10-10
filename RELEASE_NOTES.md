# MusicMate 3.26.0

DLNA streaming works again on Android 17. Android 17 blocks local network traffic until an app holds the new Local network access permission, so the media server could not announce itself or find players. MusicMate now asks for this permission when the server starts.

USB DACs now get high-resolution output. 24-bit songs keep their full resolution instead of being reduced to 16-bit, and the DAC runs at the song's own sample rate when it offers that rate (for example 96 kHz instead of Android's usual 48 kHz). The output picker shows the DAC format, such as `USB 96 kHz / 32-bit`. This replaces the USB bit-perfect setting, which could never succeed on phones without an Android bit-perfect mixer, such as the Galaxy S25. ReplayGain and gapless playback work again on USB.

VU meters now measure the audio sent to the output, so they also move for high-resolution songs. Next in Music Center moves one song per tap instead of two.

Version code: 148. See [CHANGELOG.md](https://github.com/thawee/musicmate/blob/v3.26.0/CHANGELOG.md) for details.

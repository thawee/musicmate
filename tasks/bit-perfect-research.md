# Bit-perfect USB research (2026-11-27)

Researcher findings from the Android docs, AOSP source and the androidx/media issues. Points marked "unverified" have not been tried on a device.

- **API.** Everything needed is in API 34. `setPreferredMixerAttributes(attrs, device, mixerAttrs)` and `clearPreferredMixerAttributes` need MODIFY_AUDIO_SETTINGS, which the app already has. `getSupportedMixerAttributes(device)` lists the available formats, and an entry with `getMixerBehavior() == MIXER_BEHAVIOR_BIT_PERFECT` means the device can do it. `addOnPreferredMixerAttributesChangedListener` fires on set, clear and disconnect.
- **Conditions.** Only USAGE_MEDIA and only USB devices are allowed. The AudioTrack must match the mixer's encoding, sample rate and channel mask exactly, or AOSP `PlaybackThread::createTrack_l` fails the track with BAD_VALUE. A data-processing effect attached to the session also fails it. Support is optional, AIDL HAL only, and depends on the phone maker (Pixel since Sep 2024).
- **Other sounds.** Only one bit-perfect client at a time. Other apps' sounds and notifications are muted or the output falls back to mixing, depending on the Android build (unverified on a device).
- **Volume.** The framework applies no software volume. AOSP passes the track volume to the HAL output, so the volume keys probably drive the DAC's hardware volume (unverified).
- **Media3.** There is no support up to 1.11.1. Issue androidx/media#415 is open, and issue #1931 (24- and 32-bit integer AudioTrack output) is a prerequisite for it. So the sink must build the track format from the mixer attributes itself, and must not use float output or resampling.
- **Proof on a device.** Run `adb shell dumpsys media.audio_flinger`. Look for the thread header `Output thread ... (BIT_PERFECT):` and its `Sample rate` and `HAL format`, and check that the track table's `BitPerfect` column shows `true` for MusicMate.

Sources:
- https://developer.android.com/reference/android/media/AudioManager
- https://developer.android.com/reference/android/media/AudioMixerAttributes
- https://developer.android.com/media/platform/improve-audio-playback
- https://source.android.com/docs/core/audio/preferred-mixer-attr
- https://github.com/androidx/media/issues/415
- https://github.com/androidx/media/issues/1931
- AOSP frameworks/av services/audioflinger Threads.cpp and IAfTrack.h, main branch.

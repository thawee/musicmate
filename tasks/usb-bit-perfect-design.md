# Native USB bit-perfect design

## Caller and ownership

The listener enables **USB bit-perfect** in Settings and starts the next song on **This Device**. `AndroidPlayerController` snapshots the preference for that song. `UsbBitPerfectSession` owns the decoded format, Android mixer request, output lease, and immutable `Status`. Music Center and the player picker read the service's status for the matching device ID.

`Status` distinguishes `DISABLED`, `UNAVAILABLE`, `PENDING`, and `REQUESTED`. `REQUESTED` means Android accepted the exact preference and the current AudioTrack routes to that USB device. It does not prove transport or DAC behavior.

## Grounding

- Media3 1.11.1 `DefaultAudioSink.configure` converts high-resolution PCM to 16-bit unless float output is enabled. A track tag or supported mixer rate cannot establish an unmodified playback path.
- `AudioTrackAudioOutputProvider` exposes the final encoding, rate, and channel mask before AudioTrack creation. `AudioTrackAudioOutput.getAudioTrack` exposes the actual route.
- Android's preferred mixer attributes require a supported USB format, media audio attributes, and `MODIFY_AUDIO_SETTINGS`. Setter failure must leave ordinary playback available.
- `AudioLevelProcessor` copies bytes unchanged. ReplayGain changes software volume. Nonunity gain and playback transformations invalidate a bit-perfect request.
- The repository records Bluetooth distortion from forcing float output. Preserve the ordinary sink's float policy and decline requests when its conversion loses precision.

## Alternatives and decision

| Alternative | Result |
| --- | --- |
| Choose a mixer from track tags in the controller | Rejected. Tags do not identify decoded/output precision, and buffered transitions can refer to different tracks. |
| Own the request beside the sink and AudioTrack lifecycle | Accepted. Exact output matching, fallback, route checks, and cleanup share one owner. |

The playback worker implements the accepted sketch. Avoid a separate USB driver or a vendored Media3 sink. Native requests use the existing decoder/sink only when it preserves precision. High-resolution content narrowed by this pipeline uses ordinary playback with an explicit reason.

## Lifecycle contracts

- Snapshot the setting at the next song. With USB mode enabled, prepare songs individually so upcoming source metadata cannot change a buffered song's request.
- Keep normal gapless playback when this option is off.
- Bypass ReplayGain for the requested USB path and use unity software volume. Honor audio-focus ducking by leaving bit-perfect mode, rather than overriding ducking gain.
- Retry an AudioTrack creation failure once without the mixer preference before reporting a playback error.
- Clear only the preference owned by the released output. A delayed release from an older output must not clear a newer request.
- On detach, route mismatch, changed preference, or processing changes, stop advertising the request and clear the owned preference.

## Verification

Regression checks cover exact mixer matching, rejected and failed requests, precision changes, source metadata narrowing, replacement cleanup, and ordinary fallback. Build and emulator checks cover settings and ordinary playback. A physical USB DAC remains necessary to validate a successful native request and sample-rate transitions.

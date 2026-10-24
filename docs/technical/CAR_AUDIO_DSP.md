# Car Audio DSP Specification

Status: design agreed with the owner on 2026-10-23; slices 1 to 4 built 2026-10-24, slice 5 (car-only activation) open.

Renamed on screen to **Speaker Tuning** on 2026-10-24: the same correction and auto-switch serve any measured speaker (a car, or a Bluetooth speaker at home). Code names (`CarDsp`, `CarProfile`), the `car_dsp` preferences and the `car_profiles` folder keep the Car name so saved profiles survive. Since 2026-10-24 the active profile follows the media route (`CarRoute`): a Bluetooth route picks the profile with that address, an Android Auto route (a remote submix) picks the car of the last car-class Bluetooth handshake (`CarHandshakeReceiver`, declared in the manifest) or else the only car profile, and any other route gives No correction. The switch acts only when the route changes, so a manual pick holds until then. The first design keyed on the hands-free link, which the MU-X drops about 5 s after wireless Android Auto starts, so it never held.

MusicMate plays locally; when Android routes that playback to the car through Android Auto, MusicMate applies a measured per-car correction plus a chosen sound mode. MusicMate does not appear in Android Auto: no browse tree, no car meta-data, no Play car review.

---

## 1. Decisions

| # | Decision |
|---|---|
| Q1 | DSP runs inside `IntegerPcmAudioOutput.write()`, the one point every PCM buffer passes. Not a Media3 `AudioProcessor`: with float output enabled, `DefaultAudioSink` skips custom processors for high-res input, and turning float output off would drop 24-bit sources to 16-bit. |
| Q2 | Calibration is a guided phone-mic measurement while parked, done once; afterwards the profile applies automatically. |
| Q3 | Car target curve: low shelf +4 to +6 dB below about 100 Hz, tilt to about -3 dB at 20 kHz. The target is an assumption, not a published result: no in-car preferred target curve was found in public research (2026-10-23); it follows the shape of the Harman headphone target, which derives from a loudspeaker's in-room response. Keep its numbers until the in-car A/B check says otherwise. Correction covers 30 Hz to 8 kHz, cuts up to -10 dB, boosts at most +3 dB and only at 100 Hz and above (cuts only below 100 Hz), at most 10 PEQ bands, deep nulls left alone. |
| Q4 | Measurement goes through MusicMate's own playback path with DSP bypassed (log sweep, `UNPROCESSED` mic, 5 positions averaged, noise check, head unit EQ flat). |
| Q5 | Revised 2026-10-23 after the first car log: wireless Android Auto only. The car is a `TYPE_REMOTE_SUBMIX` media route while a Bluetooth car link is connected; the first time, the user confirms that link is a car, and the choice is remembered by Bluetooth address. Any other route turns DSP off. Manual bypass from Now Playing. |
| Q6 | v1 scope: wizard, Car Audio settings section, response graph, Now Playing chip, 10-band PEQ + auto preamp + safety limiter. Deferred: volume-dependent loudness, road-noise adaptation, manual PEQ editor, WebUI controls. |
| Q7 | Five sound modes on top of the correction: Reference, Bass, Vocal, Drive, Custom. |
| Q8 | Verification bar and build order in sections 7 and 8. |

## 2. Signal path

```
Media3 decoder (float for high-res, int16 for CD)
   -> IntegerPcmAudioOutput.write()
        DSP off : unchanged path (byte-identical)
        DSP on  : to double -> preamp -> correction PEQ (<=10) -> mode filters (<=3)
                  -> safety limiter -> float input: int24 | int16 input: TPDF-dithered int16
   -> AudioTrack -> Android audio -> Android Auto -> head unit -> cabin
```

- Coefficients and filter state in `double`, recomputed per track sample rate (44.1 to 192 kHz). Low-frequency biquads at 192 kHz are not stable in `float`.
- 16-bit input stays 16-bit out so the AudioTrack is never reopened mid-track.
- Each input block is filtered once into a staging buffer, as the float path already does with `converted`; a partial `super.write` must never filter a sample twice.
- Non-finite input (NaN or infinity from a decoder) becomes silence before processing; if a frame still comes out non-finite, it is silenced and the filters reset. A warning is logged once per output. Found on 2026-10-24: Samsung's `c2.sec.flac.decoder` returned noise and NaN for a valid 16-bit FLAC (that file uses 1152-sample blocks), so `FlacDecoderSelector` now puts `c2.android.flac.decoder` first.
- A USB DAC media route is never processed, whatever the target says (`CarDsp.updateRoute`, fed by the route log). Only float and 16-bit input are processed; any other encoding passes through.
- DSP state is read once per buffer. Changes are heard after the queued AudioTrack audio drains (about 0.5 to 1 s).
- Mode or profile changes crossfade the old and new filter chains over about 30 ms to avoid clicks.
- Auto preamp = -(maximum of the combined magnitude response of correction + mode + Custom trims, evaluated on a log-frequency grid), never positive. The limiter (linked across channels, instant attack, about 50 ms release, -0.5 dBFS ceiling) is a safety net for overs, not a loudness tool. No compression in any mode except Sleep's light compressor (section 5).

## 3. Calibration

Revised 2026-10-23 while building slice 4: the sweep plays on its own track, and noise comes from the measurement itself (see the notes after the steps).

1. Request `RECORD_AUDIO` on entering the wizard.
2. Checklist: parked, engine off or idling, windows closed, AC fan off, head unit EQ, loudness and sound-field settings flat, volume at the usual listening level, phone held the same way at every position with the mic facing forward (phone mics are directional at high frequencies).
3. Pause MusicMate playback. Play a 10 s exponential sine sweep, 20 Hz to 20 kHz at 48 kHz (Farina), on a dedicated 16-bit `AudioTrack` with the player's media attributes, so it takes the same Android route as music; nothing enters the queue or history, and ReplayGain, speed and Car DSP do not apply. Record from 0.5 s before to 3 s after with `AudioRecord`, source `UNPROCESSED` when the device reports support, else `VOICE_RECOGNITION` marked approximate, forced to the built-in mic.
4. Reject the take if the recorder is not on the built-in mic, the sweep track is not on the music route, the recording clips (>= 0.99), no response stands out from the noise, or the response arrives too late to measure the noise after it.
5. Deconvolve with the inverse sweep (one 2^20 FFT; latency independent). Window the response from 0.2 s before its peak (the low-frequency ringing ahead of the peak; harmonic responses sit about 1 s earlier) to 0.5 s after, then take 1/6-octave smoothed power on a 1/48-octave grid. Measured flat within 0.1 dB from 30 Hz to 16 kHz on a clean synthetic sweep (above, the smoothing band runs past the sweep's end).
6. Noise: the same window 0.6 s after the peak in the deconvolved output, within the recording (so a late sweep is rejected, not given a flattering SNR; with 3 s recorded after the sweep, up to about 1.7 s of latency fits). Reject the take if any 1/3-octave band from 100 Hz to 8 kHz is less than 20 dB above it. A band below 100 Hz under the limit in any take is not a rejection: the fit leaves everything from that band down uncorrected and the verify check skips it. This revises the Q4 rule (owner informed 2026-10-23): a phone or small speaker cannot play deep bass, so raising the volume would not help.
7. Repeat at 5 positions around the driver's head at ear height (centre, left, right, forward, back, about 15 cm). Average in power.
8. Fit at most 10 peaking bands toward the target within the Q3 limits. Boost allowed: none below 100 Hz, rising to +3 dB at 200 Hz, +3 dB above, and none into a narrow deep null (> 6 dB below target over < 1/3 octave); the combined curve must respect the same limit at every point. Level is aligned by the median difference over 200 Hz-2 kHz.
9. Show measured, target and predicted curves. Name and save the profile with the car's Bluetooth link. The latest saved profile is the active one; the Car Audio section can switch or delete profiles.
10. Verify sweep: the sweep pre-filtered offline by the correction (the live DSP does not touch the dedicated track), deconvolved with the plain inverse (section 7).

## 3a. Mic accuracy

Android CDD 5.11 only requires the `UNPROCESSED` source to be flat within +/-10 dB from 100 Hz to 7 kHz, +/-20 dB below 100 Hz and +/-30 dB above 7 kHz. A phone mic is therefore an approximate instrument: its own roll-off, especially in the bass, is indistinguishable from the car's response, and a fit down to 30 Hz can end up correcting the mic. Decision (owner, 2026-10-23): below 100 Hz the fit may only cut, never boost, so a mic roll-off can tame a real bass peak but never adds bass. Bass level below 100 Hz is set by ear through the Bass mode or the Custom slider. A USB measurement mic is out of scope for v1.

## 4. Activation

- Rule (owner, 2026-10-23): Car DSP is active when the media route is `TYPE_REMOTE_SUBMIX` and a Bluetooth device the user has confirmed as a car is connected (HFP). The phone always joins the car's Bluetooth before wireless Android Auto starts. The first time a remote submix appears with an unconfirmed Bluetooth device connected, MusicMate asks once ("Use Car DSP with CAR2-4BF4?") and stores the answer by Bluetooth address, with the name for display. Each confirmed car has its own profile. Casting or mirroring without a confirmed car never turns DSP on.
- On every routing change, read the routed device's `type`, `productName` and `id`. Media3's `AudioOutput` may not expose the AudioTrack's routing listener; with minSdk 36, prefer `AudioManager.getAudioDevicesForAttributes()` with the `USAGE_MEDIA` attributes, re-checked from an `AudioDeviceCallback`. Confirm against the Media3 1.11 API in slice 1.
- Match against saved signatures. Match: load that profile and its last-used mode. No match: DSP off.
- Wired and wireless Android Auto get separate profiles if their signatures differ.
- Note: `AudioOutputHelper.java:188-190` classes `TYPE_USB_ACCESSORY` as USB. If wired Android Auto reports that type, the USB DAC format logic (`UsbOutputFormat.prepare`) may already act on the car route. Slice 1 settles this.

### Measured route (SM-S931B, MU-X, wireless Android Auto, 2026-10-23, first session)

- While Android Auto carried media, the media route was `TYPE_REMOTE_SUBMIX` (25), product `remote-submix`, address `-775964769:ap:4mixp:0`, rates `[48000]`, encodings `[2]` (16-bit PCM). Android Auto takes media through a remote submix, so the car receives 48 kHz / 16-bit regardless of the source.
- Not USB: `usbDacPath=false`, `usbFormat=android default`. The USB DAC logic does not touch the car route.
- `uiMode` stayed 1 (normal), so car mode is not a usable signal.
- The car's Bluetooth device (`TYPE_BLUETOOTH_SCO`, `CAR2-4BF4`) appeared for 5 s before the submix and was gone while the submix was active, so it is not visible among output devices during playback.
- `remote-submix` is generic; whether the numeric part of the address is stable across sessions is unknown until a second session is logged.

## 5. Sound modes

| Mode | Filters on top of the correction |
|---|---|
| Reference (default) | none |
| Bass | low shelf +4 dB at 80 Hz, bell -1 dB at 250 Hz |
| Vocal | bell +2.5 dB at 2.5 kHz Q 1, low shelf -1.5 dB at 120 Hz, bell -1 dB at 300 Hz |
| Drive | low shelf +3 dB at 70 Hz, bell +1.5 dB at 3 kHz, high shelf +2 dB at 8 kHz |
| Relax | high shelf -2.5 dB at 6 kHz, bell -1.5 dB at 3 kHz, low shelf +1.5 dB at 150 Hz |
| Focus | bell -2 dB at 2.5 kHz Q 0.8, high shelf -1 dB at 8 kHz |
| Sleep | high shelf -4 dB at 4 kHz, low shelf -2 dB at 60 Hz, plus the light compressor |
| Custom | Bass and Treble sliders, each +/-4 dB |

At most 3 filters and +4 dB per mode. Each profile remembers its last mode, and every mode is offered on every output.

Relax, Focus and Sleep were added on 2026-10-24 after a research pass. They follow Sony's Relaxed and Mellow presets. Focus and Sleep are educated guesses, not tested curves: the research says lyrics are what distract and slow, soft music is what helps sleep, and EQ changes neither.

Sleep is the one compressed mode (owner decision, 2026-10-24): 2:1 above -20 dBFS with a 6 dB soft knee, 10 ms attack, 400 ms release, linked across channels, no makeup gain, placed after the EQ and before the safety limiter. Loud moments come down and quiet passages pass untouched. When Sleep is left, the processor keeps running until the compressor has released, so the level never jumps.

## 6. Storage and UI

- One JSON file per car profile in the app files directory: name, date, route signature, sample rate measured, PEQ bands, measured and target curves, last mode, Custom trims, mic source used.
- Settings (`SettingsScreen.kt`): Car Audio section with profile list, Calibrate, Re-measure, Delete.
- Calibration wizard: full-screen Compose flow.
- Now Playing: chip "Car · <mode>"; tap opens Off, Reference, Bass, Vocal, Drive, Custom. Off is the A/B bypass.

## 7. Verification

JVM unit tests:
- Biquad response matches design gain within 0.1 dB at 44.1, 48, 96 and 192 kHz.
- Deconvolution recovers a known synthetic car filter within 0.5 dB.
- PEQ fit respects at most 10 bands, boosts no higher than +3 dB, and no boost below 100 Hz.
- DSP off: output byte-identical to input.
- Partial `write`: no sample filtered twice.

In car:
- Verify sweep with DSP on: within +/-3 dB of the achievable target (the target clipped by the boost cap, and by the cuts-only rule below 100 Hz) from 60 Hz to 8 kHz after 1/6-octave smoothing; otherwise show where it misses.
- The verify sweep uses the same phone mic as the fit, so a pass proves the fit is self-consistent, not that the car is accurate. Only a reference mic or listening confirms absolute accuracy.
- Listening A/B with the chip across all modes; no clicks on switching. Judge Bass and Drive while driving, not only parked: whole-body vibration lowers preferred bass by up to about 3 dB (AES E-Library 15150).

## 8. Build order

1. Route logging (diagnostic) and one drive, wired and wireless, to capture the real Android Auto signature.
2. DSP engine in `IntegerPcmAudioOutput` with a hardcoded test curve behind a developer toggle; unit tests.
3. Sound modes and the Now Playing chip on a flat correction.
4. Calibration wizard: permission, checklist, sweeps, deconvolution, fit, graph, save.
5. Automatic activation by signature, plus the verify sweep.

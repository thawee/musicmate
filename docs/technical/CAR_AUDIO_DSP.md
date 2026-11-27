# Car Audio DSP Specification

Status: speaker calibration, route matching and shared audio tuning are implemented. Acoustic accuracy and external-device playback require hardware verification.

Since 2026-10-28, **Audio tuning** presents speaker and headphone profiles in one dropdown with Microphone, Listening and AutoEQ creation methods. Speakers can use the existing microphone room calibration or guided listening; AutoEQ imports measured speaker corrections rather than searching the headphone database. Legacy speaker files and Bluetooth matching remain readable. Selecting a saved speaker in the unified controls binds its correction and sound mode to the current output. See [the shared tuning interface](AUDIO_TUNING.md) for method and hardware requirements.

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
| Q7 | Seven sound modes on top of the correction: Neutral, Bass, Vocal, Lively, Relax, Night, Custom. |
| Q8 | Verification bar and build order in sections 7 and 8. |

## 2. Signal path

```
Media3 decoder (float for high-res, int16 for CD)
   -> LookaheadAudioSink.handleBuffer()
        DSP off : unity 24/32-bit PCM remains byte-identical
        DSP on  : to double -> preamp -> correction PEQ (<=10) -> mode filters (<=3)
                  -> linked true-peak lookahead limiter -> integer PCM (24/32-bit)
   -> IntegerPcmAudioOutput.write() -> output metering
   -> AudioTrack -> Android audio -> Android Auto -> head unit -> cabin
```

- Coefficients and filter state in `double`, recomputed per track sample rate (44.1 to 192 kHz). Low-frequency biquads at 192 kHz are not stable in `float`.
- Float and 16-bit input become 24-bit PCM; packed 24/32-bit sources retain their width. Gain, EQ and limiting run in double precision before final quantization.
- Each input block is filtered once into a staging buffer, as the float path already does with `converted`; a partial `super.write` must never filter a sample twice.
- Non-finite input (NaN or infinity from a decoder) becomes silence before processing; if a frame still comes out non-finite, it is silenced and the filters reset. A warning is logged once per output. Found on 2026-10-24: Samsung's `c2.sec.flac.decoder` returned noise and NaN for a valid 16-bit FLAC (that file uses 1152-sample blocks), so `FlacDecoderSelector` now puts `c2.android.flac.decoder` first.
- Speaker DSP bypasses USB DAC media routes (`CarDsp.updateRoute`, fed by the route log). Separate [headphone EQ](HEADPHONE_EQ.md) can process USB only after explicit enablement for that output. Speaker DSP handles float and 16-bit input; headphone EQ also handles packed 24/32-bit PCM.
- DSP state is read once per buffer. Changes are heard after the queued AudioTrack audio drains (about 0.5 to 1 s).
- Mode or profile changes crossfade the old and new filter chains over about 30 ms to avoid clicks.
- Auto preamp = -(maximum of the combined magnitude response of correction + mode + Custom trims, evaluated on a 1/48-octave grid from 1 Hz to Nyquist, plus band centres and DC/Nyquist endpoints), never positive. Including the full PCM bandwidth accounts for shelf boost outside 20 Hz–20 kHz. This is a sampled response estimate, not a guarantee against transient or inter-sample overs. The live limiter links channels, uses about 5 ms of lookahead plus interpolation context and about 50 ms release, and targets -1 dBTP with an additional 1.2 dB detector allowance. Its four-times ITU Annex 2 interpolator is supplemented by a 96-tap sinc detector for high-frequency boundaries. This finite detector is a safety net, not a mathematical bound on ideal reconstruction or a loudness tool. Lookahead drains at end of stream and format changes; seeks discard old state. At 48 kHz the delay is 288 frames (6 ms); at 192 kHz it is 1008 frames (5.25 ms). Unity bypass adds no delay until DSP first engages; subsequent bypass frames retain that stream delay without attenuation. Each processed block reports its lowest limiter gain and any full-scale clamp to `OutputGuard`, which drives the Limiting and Clipping light on the tuning chip, throttled to one update per 250 ms. No compression in any mode except Night's light compressor (section 5).

### Sound-quality validation (2026-10-29)

Regression signals exposed missing headroom outside the previous 20 Hz–20 kHz response grid: a +6 dB low shelf at 20 Hz reserved only 3 dB, and a +6 dB high shelf at 8 kHz, rendered at 192 kHz, produced a steady 48 kHz sine sample peak of 1.014539 before limiting. The full-band estimate fixes these cases. Tests also cover transient/multitone sample peaks and linked stereo gain at 44.1, 48, 96 and 192 kHz. These are digital checks, not acoustic measurements or evidence of audible superiority. The live limiter additionally passes independent 16-times, 192-tap output reconstruction fixtures at all four rates, including impulses, near-Nyquist boundaries and amplitude bursts (highest fixture peak: -1.144 dBTP). This finite fixture set does not prove a universal ceiling. Media3 still trims encoder delay/padding downstream of the limiter; device resampling and codecs also remain outside this check. Device CPU, listening and DAC loopback validation remain pending.

On the connected phone with HiBy R3 Bluetooth playback, AudioFlinger showed MusicMate's active 44.1 kHz track feeding a 96 kHz, 32-bit PCM A2DP mixer; AudioService reported LDAC. The track had zero recorded underruns in that snapshot and `BitPerfect=false`. This route resamples before Bluetooth transport. The HAL's PCM format does not establish the codec bitrate, receiver DAC format or end-to-end fidelity. No output settings were changed during inspection.

## 3. Calibration

Revised 2026-10-23 while building slice 4: the sweep plays on its own track, and noise comes from the measurement itself (see the notes after the steps).

1. Request `RECORD_AUDIO` on entering the wizard.
2. Checklist: parked, engine off or idling, windows closed, AC fan off, head unit EQ, loudness and sound-field settings flat, volume at the usual listening level, phone held the same way at every position with the mic facing forward (phone mics are directional at high frequencies).
3. Pause MusicMate playback. Play a 10 s exponential sine sweep, 20 Hz to 20 kHz at 48 kHz (Farina), on a dedicated 16-bit `AudioTrack` with the player's media attributes, so it takes the same Android route as music; nothing enters the queue or history, and ReplayGain, speed and Car DSP do not apply. Record from 0.5 s before to 3 s after with `AudioRecord`, source `UNPROCESSED` when the device reports support, else `VOICE_RECOGNITION` marked approximate, forced to the built-in mic.
4. Reject the take if the recorder is not on the built-in mic, the sweep track is not on the music route, the recording clips (>= 0.99), no response stands out from the noise, or the response arrives too late to measure the noise after it.
5. Deconvolve with the inverse sweep (one 2^20 FFT; latency independent). Window the response from 0.2 s before its peak (the low-frequency ringing ahead of the peak; harmonic responses sit about 1 s earlier) to 0.5 s after, then take 1/6-octave smoothed power on a 1/48-octave grid. Measured flat within 0.1 dB from 30 Hz to 16 kHz on a clean synthetic sweep (above, the smoothing band runs past the sweep's end).
6. Noise: compare the response with the same window 0.6 s after its peak in the deconvolved output, within the recording (a late sweep is rejected). Also compare a 250 ms Hann-windowed raw recording around each sweep frequency with the louder of the first and last 250 ms of ambient audio. Use the lower SNR estimate. The limit remains 20 dB per 1/3-octave band. Up to three unreliable bands at or above 100 Hz may be excluded across all five positions; broader noise rejects the current take without discarding earlier positions. A band below 100 Hz under the limit raises the correction's lower boundary above it. Clipping, missing response and late arrival remain rejection conditions.
7. Repeat at 5 positions around the driver's head at ear height (centre, left, right, forward, back, about 15 cm). Average in power.
8. Fit at most 10 peaking bands toward the target within the Q3 limits. Boost allowed: none below 100 Hz, rising to +3 dB at 200 Hz, +3 dB above, and none into a narrow deep null (> 6 dB below target over < 1/3 octave); the combined curve must respect the same limit at every point. Excluded bands have a quarter-octave margin on each side, covering the band and response smoothing. Their values do not influence level alignment, null detection or filter selection. Their desired correction is zero; combined filter tails are constrained to within 1 dB, checked at 1/192-octave spacing and the exact region boundaries with a 0.02 dB margin. Level alignment uses reliable points over 200 Hz-2 kHz.
9. Show measured, target and predicted curves. Name and save the profile with the car's Bluetooth link. The latest saved profile is the active one; the Car Audio section can switch or delete profiles.
10. Verify sweep: the sweep pre-filtered offline by the correction (the live DSP does not touch the dedicated track), deconvolved with the plain inverse (section 7). Check only the original reliable frequencies. Additional unreliable bands require another measurement; they cannot silently reduce verification coverage. Save excluded band centres in the profile, show a partial-calibration warning, and leave gaps in the graph at excluded frequencies. Profiles without this metadata retain their previous behavior.

Engine-noise handling (2026-10-27) identifies unreliable frequency regions; it does not notch the microphone recording or cancel the physical engine sound. The displayed frequency near 157 Hz is a band centre. Repeated sweeps at the same spot are deferred until car measurements establish their benefit and alignment requirements; the five-position power average remains. Phone-microphone headphone calibration is unsupported. A separate headphone-profile workflow is planned next.

Retry guidance distinguishes recording overload from insufficient signal-to-noise ratio. Low SNR first asks for a quieter room and steady microphone placement. If recent retries alternate between clipping and low SNR, keep the lower playback setting and address room noise or microphone placement rather than repeatedly reversing volume changes. This history clears after an accepted position or a new calibration session. Rejected takes remain rejected; the clipping and SNR thresholds are unchanged.

Side by side (2026-11-27). A speaker spot plays one stereo stream: the left sweep, then the right sweep 13.5 s later, so each half of the 27 s recording has the single-sweep layout above and both sides share the route's latency. A spot counts only when both halves pass; a side with no response at all leaves the other side's take and one correction for both. The combined take is the power average of the sides with the lower SNR of each band. Each side is fitted on its own with the session's exclusions. Balance is the median left minus right level from 200 Hz to 2 kHz, capped at 6 dB, and attenuates the louder side. Arrival is the first sample within 10 ms before the peak at a tenth of it or more, so a weak direct sound ahead of a strong reflection still counts; the side heard first at the centre spot is delayed by the gap, and a gap over 6 ms is dropped. The profile keeps the combined bands for older builds, mono and multichannel tracks. The check plays both sides through the stereo chain and passes only when both sides pass; the remaining arrival gap is logged, not checked. Headphone fixtures stay mono.

## 3a. Mic accuracy

Android CDD 5.11 only requires the `UNPROCESSED` source to be flat within +/-10 dB from 100 Hz to 7 kHz, +/-20 dB below 100 Hz and +/-30 dB above 7 kHz. A phone mic is therefore an approximate instrument: its own roll-off, especially in the bass, is indistinguishable from the car's response, and a fit down to 30 Hz can end up correcting the mic. Decision (owner, 2026-10-23): below 100 Hz the fit may only cut, never boost, so a mic roll-off can tame a real bass peak but never adds bass. Bass level below 100 Hz is set by ear through the Bass mode or the Custom slider. A USB measurement mic is out of scope for v1.

## 4. Activation

Legacy speaker selection follows `CarRoute`. A Bluetooth media route selects the saved profile with that address. A remote submix selects the profile matching the last car-class Bluetooth handshake, or the only car profile if no handshake matches. With multiple car profiles and no matching handshake, no correction is selected. Other routes select no legacy correction. A manual profile choice holds until the route changes. An active hands-free connection is not required.

The shared tuning library stores explicit bindings separately. Selecting a saved correction in Audio tuning binds it to the compatible current output. An existing disabled binding bypasses EQ. USB processing requires the explicit USB EQ switch; a legacy speaker preference cannot grant that consent.

### Measured route (SM-S931B, MU-X, wireless Android Auto, 2026-10-23, first session)

- While Android Auto carried media, the media route was `TYPE_REMOTE_SUBMIX` (25), product `remote-submix`, address `-775964769:ap:4mixp:0`, rates `[48000]`, encodings `[2]` (16-bit PCM). Android Auto takes media through a remote submix, so the car receives 48 kHz / 16-bit regardless of the source.
- Not USB: `usbDacPath=false`, `usbFormat=android default`. The USB DAC logic does not touch the car route.
- `uiMode` stayed 1 (normal), so car mode is not a usable signal.
- The car's Bluetooth device (`TYPE_BLUETOOTH_SCO`, `CAR2-4BF4`) appeared for 5 s before the submix and was gone while the submix was active, so it is not visible among output devices during playback.
- `remote-submix` is generic; whether the numeric part of the address is stable across sessions is unknown until a second session is logged.

## 5. Sound modes

Speakers and headphones share these named mode curves and Night compression. Preferences remain separate per car and headphone output. Neutral applies the correction alone; Off bypasses processing. The stored `REFERENCE` value retains existing car choices. Custom retains the released ranges: +/-4 dB for speakers and +/-6 dB for headphones.

| Mode | Filters on top of the correction |
|---|---|
| Neutral (default) | none |
| Bass | low shelf +4 dB at 80 Hz, bell -1 dB at 250 Hz |
| Vocal | bell +2.5 dB at 2.5 kHz Q 1, low shelf -1.5 dB at 120 Hz, bell -1 dB at 300 Hz |
| Lively | low shelf +3 dB at 70 Hz, bell +1.5 dB at 3 kHz, high shelf +2 dB at 8 kHz |
| Relax | high shelf -2.5 dB at 6 kHz, bell -1.5 dB at 3 kHz, low shelf +1.5 dB at 150 Hz |
| Night | high shelf -4 dB at 4 kHz, low shelf -2 dB at 60 Hz, plus the light compressor |
| Custom | Bass and Treble sliders, each +/-4 dB |

At most 3 filters and +4 dB per mode. Each profile remembers its last mode, and every mode is offered on every output.

The preset list was simplified on 2026-10-26: Drive became Lively, Sleep became Night, and Focus was removed. Saved Focus choices migrate to Relax; renamed modes retain their curves. These are listening preferences, not validated concentration or sleep treatments.

Night is the one compressed mode (owner decision, 2026-10-24): 2:1 above -20 dBFS with a 6 dB soft knee, 10 ms attack, 400 ms release, linked across channels, no makeup gain, placed after the EQ and before the safety limiter. Loud moments come down and quiet passages pass untouched. When Night is left, the processor keeps running until the compressor has released, so the level never jumps.

ReplayGain remains a separate playback setting (Off, Track or Album), applied at track start and transitions alongside any preset. Presets do not change the ReplayGain preference. Its metadata peak guard is independent of the DSP headroom calculation and safety limiter. Night compression reduces louder passages within a track; ReplayGain adjusts level between tagged tracks. The sleep timer remains independent of both.

## 6. Storage and UI

Main menu → Audio tuning opens the shared [audio tuning sheet](AUDIO_TUNING.md). The Now Playing tuning chip offers quick saved-profile and sound-mode selection, profile creation and access to the full editor. The Speaker tab uses the same profile dropdown and sound modes as Headphone. Microphone and Listening create speaker corrections. AutoEQ imports measured speaker EQ text because its searchable database contains headphone measurements.

Existing microphone speaker profiles remain in `files/car_profiles/`. `CorrectionProfiles` presents them in the shared selector with stable `speaker:` IDs. Explicit selection copies the correction into the shared library without removing the original measurement. The first copy preserves the legacy speaker mode, Custom trims and non-USB enablement. USB still requires explicit consent.

`HeadphonePreferences` stores the shared library in `files/headphone_profiles.json`, including profile output types, methods and output bindings. Bindings hold the selected profile, EQ enablement, USB consent, sound mode and Custom trims. Legacy `car_dsp` preferences remain available before a shared binding exists. Legacy profile rename and deletion keep the shared copy in sync.

`HeadphoneControls.kt` owns the common controls and callbacks. `DspTuningComponents.kt` owns the sheet, output tabs, response panel and calibration action rows. `CorrectionProfileSelector.kt` renders the shared dropdown. `CarDspControls.kt` retains legacy speaker modes and profile management. The microphone wizard remains a separate Compose activity.

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
- Listening A/B with the chip across all modes; no clicks on switching. Judge Bass and Lively while driving, not only parked: whole-body vibration lowers preferred bass by up to about 3 dB (AES E-Library 15150).

## 8. Build order

1. Route logging (diagnostic) and one drive, wired and wireless, to capture the real Android Auto signature.
2. DSP engine in `IntegerPcmAudioOutput` with a hardcoded test curve behind a developer toggle; unit tests.
3. Sound modes and the Now Playing chip on a flat correction.
4. Calibration wizard: permission, checklist, sweeps, deconvolution, fit, graph, save.
5. Automatic activation by signature, plus the verify sweep.

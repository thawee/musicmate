# Audio tuning interface

Music Mate uses one tuning sheet for speaker, headphone and earphone corrections. Open it from Settings → Audio tuning → Open audio tuning, or tap the tuning chip on Now Playing. The sheet uses Music Mate's dark surfaces, a gold response curve and neutral selection controls.

## Output and profile selection

The Speaker and Headphone tabs choose the type of correction to browse or create. The output name identifies the current audio route. USB and Bluetooth describe the connection, so either can carry a speaker or headphone correction. A built-in phone speaker cannot apply or audition a headphone correction.

The Correction profile dropdown lists both output types. Each entry shows its name, output type and calibration method. Entries for the other type remain visible but disabled. Selecting a compatible profile assigns it to the connected output. Flat / bypass removes that output's selected correction. Profiles remain available in the library.

EQ active and EQ bypassed show processing state. The switch controls enablement separately from profile selection. USB EQ starts bypassed and requires explicit permission to change samples. A sound mode never grants that permission. Legacy speaker configurations keep their existing speaker switch until the user selects a shared profile.

## Response and sound modes

The frequency-response panel plots the selected correction plus its sound mode from 20 Hz to 20 kHz. It samples the actual filter response and uses a logarithmic frequency axis. The vertical scale starts at ±6 dB and expands for larger responses. This is an EQ preview, not a new acoustic measurement or a display of the room's measured response. The curve remains visible while EQ is bypassed.

Listening profiles display the average left and right correction. The graph does not display channel balance, total preamp attenuation or the time-varying effect of Night compression.

Sound modes sit below the curve. Neutral keeps the saved correction. Bass, Vocal, Lively, Relax and Night add shared mode processing. Custom exposes the saved bass and treble trims. Edit listening setup reopens a listening profile. Profile details reveals the source, correction details and Delete profile action. Deleting a shared profile bypasses EQ on every binding that used it.

## Calibration actions

The Calibrate your sound section offers these methods for both tabs:

| Action | Speaker | Headphone or earphone |
| --- | --- | --- |
| Microphone | Measure the listening position with the speaker target. | Measure with a suitable fixture, selected microphone and fixture-specific target. |
| Listening | Adjust channels and tone from the listening position. | Adjust balance and per-ear tone with guided tests. |
| AutoEQ | Import measured Equalizer APO correction text. | Search measured profiles for an exact headphone model. |

Import text file and Paste profile remain available below the method rows. The importer validates the draft before adding it to the library. Saved listening profiles can be edited or deleted without reconnecting their original output. Auditioning test sounds requires a compatible connected local output.

See [Headphone EQ](HEADPHONE_EQ.md) for microphone requirements, guided listening steps and the import format. See [Car audio DSP](CAR_AUDIO_DSP.md) for speaker measurement and playback details. A phone microphone held near an earcup does not establish a flat headphone response.

## Layout and accessibility

The editor opens as an expanded bottom sheet with a maximum width of 640 dp. The header and Close audio tuning control stay fixed while the controls scroll. Profile selection, output and processing state precede calibration. Secondary profile details expand on request. Settings contains a compact entry that opens the same editor.

Text and graph headings wrap at larger font sizes. The graph reserves space for its measured axis labels. Output tabs expose selected semantics, the EQ switch exposes its checked state, and tone sliders have named accessibility descriptions.

## Verification

The implementation was checked on an Android 16 emulator at normal size, 150% system text and a compact display. The app unit suite passes 330 tests. The affected editor suites cover 20 distinct UI tests:

- `HeadphoneEqUiTest`: profile selection, mode selection, USB consent, bypass, import errors and offline deletion.
- `ManualHeadphoneUiTest`: draft isolation, guided tests, cancellation and calibration preservation.
- `ManualHeadphoneFlowTest`: save and reopen speaker and headphone profiles.
- `AudioTuningSheetUiTest`: response visibility, calibration access, expandable details and fixed close control at normal and large text sizes.

Run the unit suite with `./gradlew :app:testGithubDebugUnitTest`. Run each UI class in a separate invocation with `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=apincer.android.mmate.ui.compose.AudioTuningSheetUiTest`, replacing the class name for each suite. Results depend on the emulator serial. UI tests do not establish microphone accuracy, acoustic sound quality or external-device playback stability.

# Audio tuning interface

Music Mate uses one tuning sheet for speaker, headphone and earphone corrections. Open the full editor from the main menu → Audio tuning (directly below Settings). The tuning chip on Now Playing opens a compact quick panel. The sheet uses Music Mate's dark surfaces, a gold response curve and neutral selection controls.

## Quick tuning during playback

The 48dp Now Playing entry shows an equalizer icon and one line such as Vocal · On, Vocal · Bypassed or Vocal · Unavailable. Tap it to choose a saved correction profile and sound mode without leaving playback. One dropdown lists speaker and headphone/earphone profiles with their output type and calibration method. Compatible profiles can be selected directly; incompatible corrections remain visible but disabled. Bluetooth and USB can carry either type, while a built-in phone speaker cannot apply a headphone correction.

A Current sound card groups the local output, correction selector and effective EQ state in both panels. Its switch controls processing separately from profile and mode selection; choosing a profile or mode does not grant USB EQ consent. Without a shared speaker profile, the existing speaker sound modes remain available. A new headphone output needs a saved profile before its sound modes can apply.

Quick tuning keeps profile creation out of the listening controls. A compact Manage tuning action in the fixed header expands the same sheet into the full editor, starting at the top. Calibrate your sound in the editor offers Speaker or Headphone creation with Microphone, Listening and AutoEQ. Listening starts a draft; AutoEQ searches headphone measurements or imports measured speaker correction. Closing either panel returns to playback. Profile and mode changes reuse the same persistence and route checks as the full editor.

The quick panel omits the EQ curve, custom trim sliders, import forms and profile management. Edit Custom opens the full editor with the selected mode retained. Use the full editor for these controls. The displayed output is the Android local audio route; this panel does not establish that a remote DLNA stream receives DSP.

## Output and profile selection

The Speaker and Headphone selector in Calibrate your sound chooses the type of new correction to create. It does not change the active profile or disable its listening controls. The output name identifies the current audio route. USB and Bluetooth describe the connection, so either can carry a speaker or headphone correction. A built-in phone speaker cannot apply or audition a headphone correction.

The Correction profile dropdown lists both output types. Each entry shows its name, output type and calibration method. Incompatible entries remain visible but disabled with a connection hint. Selecting a compatible profile assigns it to the connected output. No correction removes that output's selected correction. This is separate from bypass: turning the EQ switch off retains the selected profile and mode. Profiles remain available in the library.

Active, Bypassed and Unavailable show effective processing state, with a reason when processing is off or unavailable. The switch controls enablement separately from profile selection. USB EQ starts bypassed and requires explicit permission to change samples. A sound mode never grants that permission. Legacy speaker configurations use the same status and mode controls until the user selects a shared profile. Selecting a mode preserves enablement on every output; it never turns processing on implicitly.

## Response and sound modes

The EQ curve panel plots the selected correction plus its sound mode from 20 Hz to 20 kHz. It samples the actual filter response and uses a logarithmic frequency axis. The vertical scale starts at ±6 dB and expands for larger responses. This is an EQ preview, not a new acoustic measurement or a display of the room's measured response. The curve remains visible while EQ is bypassed or unavailable, with an explicit Preview status.

Listening profiles display the average left and right correction. The graph does not display channel balance, total preamp attenuation or the time-varying effect of Night compression.

Sound modes sit above the curve as descriptive cards. Every card shows its tonal character before selection; the selected card uses a subtle gold border/background and a checkmark. Cards expose mutually exclusive selection semantics and 48dp minimum touch targets. They use two columns where space permits and one column at narrow widths or enlarged text sizes, with wrapping descriptions and no truncation. Details beneath the cards explain the selected mode, including calibration retention for Neutral and dynamics for Night. Neutral keeps the saved correction. Bass, Vocal, Lively, Relax and Night add shared mode processing. Custom exposes the saved bass and treble trims. Edit listening setup reopens a listening profile. Manage profiles reveals the source, correction details, listening-profile editing and Delete profile action. Deleting a shared profile bypasses EQ on every binding that used it.

## Calibration actions

The Calibrate your sound section offers these methods for either creation type:

| Action | Speaker | Headphone or earphone |
| --- | --- | --- |
| Microphone | Measure the listening position with the speaker target. | Measure with a suitable fixture, selected microphone and fixture-specific target. |
| Listening | Adjust channels and five tone controls from the listening position. | Adjust balance and per-ear tone with the Musical guide. |
| AutoEQ | Import measured Equalizer APO correction text. | Search measured profiles for an exact headphone model. |

Import text file and Paste profile are available under Manage profiles. The importer validates the draft before adding it to the library. Saved listening profiles can be edited or deleted without reconnecting their original output. Auditioning test sounds requires a compatible connected local output.

Musical guide moves through Bass impact, Warmth, Vocal naturalness, Clarity and Treble detail, then a Final music check. Replay a sample, adjust, or use Skip step to retain the current value. Adjusted previews the draft; Original previews flat EQ and balance. Advanced exposes all five bands: 100 Hz low shelf, 300 Hz peak, 1 kHz peak, 3 kHz peak and 8 kHz high shelf, each within ±6 dB. Existing profiles and templates start with neutral warmth and clarity.

Samples include synthesized instrument phrases and real male/female speech. Speech comes from 8 kHz Open Speech Repository recordings and retains its approximately 4 kHz bandwidth after upsampling; use instrument samples to judge treble. Replays last three seconds, or six seconds when alternating ears. Compare the same sample between Original and Adjusted before saving.

See [Headphone EQ](HEADPHONE_EQ.md) for microphone requirements, guided listening steps and the import format. See [Car audio DSP](CAR_AUDIO_DSP.md) for speaker measurement and playback details. A phone microphone held near an earcup does not establish a flat headphone response.

## Layout and accessibility

The editor opens as an expanded bottom sheet with a maximum width of 640 dp. The header and Close audio tuning control stay fixed while the controls scroll. Quick and full tuning keep the sheet anchored during swipes; use Close, Back or the outside scrim to dismiss it. Profile selection, output and processing state precede calibration. Secondary profile details expand on request. The main menu contains Audio tuning directly below Settings; it closes the drawer before opening this editor.

Text and graph headings wrap at larger font sizes. The graph reserves space for its measured axis labels. Creation-type controls and sound modes expose selected semantics, the EQ switch exposes its checked state, and tone sliders have named accessibility descriptions.

## Verification

The implementation was checked on an Android 16 emulator at normal size, 150% system text and a compact display. See the current verification entry in tasks/todo.md for the unit-suite count. UI coverage includes:

- `HeadphoneEqUiTest`: profile selection, mode selection, USB consent, bypass, import errors and offline deletion.
- `ManualHeadphoneUiTest`: draft isolation, five-step progression, skipped values, cancellation and calibration preservation.
- `SoundSignatureUiTest`: descriptions readable before selection, mutually exclusive selection, large text without clipping and compact header management access during quick-panel scrolling, without profile creation in the body.
- `QuickTuningUiTest`: compact profile/mode selection, USB consent, incompatible outputs, profile creation and full-editor navigation.
- `FiveStepListeningUiTest`: independent five-band saving, final music check, voice selection and identical-source Original / Adjusted comparisons.
- `ManualHeadphoneFlowTest`: save and reopen speaker and headphone profiles.
- `AudioTuningSheetUiTest`: response visibility, calibration access, expandable details, fixed close control at normal and large text sizes, and real body swipes without sheet movement in quick and full tuning.
- `MainLibraryAccessibilityTest`: Audio tuning below Settings and shared-sheet opening/dismissal.
- `SupportScreensAccessibilityTest`: Settings accessibility and absence of the former tuning entry.

The five-step guide and save/reopen flows also pass on a compact display at 150% text size. Four Settings screenshot references are validated, including compact/expanded layouts at 200% text.

Run the unit suite with `./gradlew :app:testGithubDebugUnitTest`. Run each UI class in a separate invocation with `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=apincer.android.mmate.ui.compose.AudioTuningSheetUiTest`, replacing the class name for each suite. Results depend on the emulator serial. UI tests do not establish microphone accuracy, acoustic sound quality or external-device playback stability.

For whole-sheet large-text verification, record `adb -s emulator-5554 shell settings get system font_scale`, set it to `1.5`, run the sheet checks, inspect the capture, then restore the recorded value. A density provider inside the sheet tests body scaling; a provider outside the modal alone does not reliably scale the dialog header.

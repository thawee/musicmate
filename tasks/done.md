# Done

Sections moved from todo.md once every item was ticked.

## Volume leveling on the playback screen (2026-11-10)

- [x] Add a pure leveling result to ReplayGainManager (off, no tags, applied gain) with unit tests, and drop the raw-tag display string.
- [x] Re-apply leveling to the current local track when the mode, pre-amp or clipping setting changes.
- [x] Show a truthful leveling status on Now Playing and the studio console, computed off the main thread, with a spoken description and a renderer notice.
- [x] Add a Volume leveling section (mode, pre-amp, current track line) to quick tuning.
- [x] Fix the Settings leveling copy and move it to strings.xml.
- [x] Build, run core and app unit tests, update docs and changelog, and verify on the phone if it is free.

Verified 2026-11-10: core 273, githubDebug 389 and playDebug 389 unit tests pass. On the phone, Track mode re-applied at once (log volume 1.0), and a −3 dB pre-amp on an untagged track showed "No ReplayGain tags, pre-amp −3.0 dB" with volume 0.708. The tagged-track display, album mode, TalkBack and a remote renderer were not exercised on a device.


## Car Audio UI/UX pass (2026-10-24)
Phone screenshots showed: Off mixed into the mode list, selection by highlight only, no sign of the active car correction in the dialog, wizard is a wall of text with the action at the end of the scroll, no position picture or countdown, grey Delete.
- [x] U1. Car DSP dialog: on/off switch in the header, mode rows with radio marks (dimmed while off; picking one turns DSP on), curve of the applied EQ, car correction row with Calibrate, Done button. Done; curve scale ±6 dB, wider only when needed; slider tick dots hidden.
- [x] U2. Wizard: top bar with back arrow, 4-step strip (Prepare, Measure, Review, Check), actions pinned at the bottom, top-down picture of the 5 positions, countdown bar while measuring, graph axis labels, name prefilled with the car's Bluetooth name. Done; Result, Verifying and Done screens compile but were not seen on the phone (they need real sweeps).
- [x] U3. Settings card: radio marks, destructive Delete icon and confirm in the theme's error colour. Done with a new `Status.Destructive` token; rows not seen on the phone yet (no saved car).
- [x] U4. Tests both flavors, release build, install, screenshot every screen. Done 2026-10-24: 193 tests per flavor, release built, installed, dialog (Reference, Drive, Custom), checklist, position 1 and the empty Settings card screenshotted. Review fixes: back arrow and system back ask before dropping measured spots or an unsaved result (Stop in the destructive colour); closing mid-sweep cancels the capture (emulator: recording stopped after 8 s, not 13.5 s); dimmed saved mode announces "Saved, Car DSP off"; at font scale 2 the stage labels became one "Step n of 4 · Name" line and the number circles grow with the text. Emulator: `SupportScreensAccessibilityTest` 3/3 (needed "Don't keep activities" off on the AVD); Settings rows and delete confirm seen with two fake profiles. Not seen: Result, Verifying and Done (need real sweeps).

## Several cars (2026-10-24)
Owner has several cars. Today one profile is active for every car, the chip shows only the mode, and cars swap only in Settings.
- [x] C1. Each car remembers its own sound mode and Custom trims (preferences keyed by profile id; "No correction" keeps the global ones; a new car starts from the current mode). On/off stays global. Done; `CarDspPreferences.select/load/save` take SharedPreferences so they are JVM-tested.
- [x] C2. Car DSP dialog: a CAR list (No correction plus every saved car, radio rows) to swap cars, and "Calibrate a car". Done; picking a car turns Car DSP on, like picking a mode.
- [x] C3. Chip shows the active car's name: "MU-X · DRIVE"; "CAR · DRIVE" with no car; long names cut with an ellipsis. Done; the chip takes a smaller share of the row than the target pill (weights 0.5 and 1), so a long name is cut first.
- [x] C4. Auto-switch: when a Bluetooth HFP device connects whose address matches a saved car, that car becomes active (also checked at start). No match changes nothing, since an unknown device may be headphones; the "unknown car falls back to No correction" rule needs slice 5's car confirmation. Built, not yet seen working (`CarAutoSelect`, started and released with the player; emulators have no Bluetooth). Review fixes: checks BLUETOOTH_CONNECT and logs when missing, receiver exported (protected broadcast from the Bluetooth stack), no crash if the proxy callback lands after release. Test without a car: calibrate with a hands-free Bluetooth speaker, pick No correction, reconnect the speaker, watch `logcat -s CarDsp`; then kill and reopen with it connected.
- [x] C5. JVM tests (per-car mode keys, address match), both flavors, release build, install, screenshots. 196 tests per flavor (2 new per-car, 1 address); Medium_Phone emulator with sample cars: list, swap, MU-X Drive and Mazda Vocal kept, long name, Settings follows. Installed on the phone 2026-10-24 (update in place, signer and all-files access verified, BLUETOOTH_CONNECT granted, app starts).
- [x] C6. Phone test of auto-switch with AL5125SH_BT as stand-in car, 2026-10-24. `media_route_log.txt`: 12:30:53 "disconnected, back to No correction", 12:31:01 "connected, its correction is now active"; chip and output pill follow. Disconnect rule added at the owner's request (also applied at start-up when the active car is not connected).

## Rename to Speaker Tuning (2026-10-24)
Owner chose "Speaker Tuning": the feature now serves any measured speaker (car or Bluetooth speaker), not only cars. Only on-screen and doc wording changes; class names, the `car_dsp` preferences and `car_profiles` folder stay so saved speakers and modes survive.
- [x] S1. Now Playing chip and dialog: "Speaker Tuning", section SPEAKER, "Tune a speaker"; chip fallback TUNING. Seen on the emulator.
- [x] S2. Settings card SPEAKER TUNING; wizard copy generic (car or room checklist, Front instead of Windscreen, speaker name); error hints. Wizard start screen seen on the emulator; Settings card not opened.
- [x] S3. Privacy policy and CAR_AUDIO_DSP.md note the new name.
- [x] S4. 196 tests per flavor, release built and installed on the phone 2026-10-24 (signer and all-files access verified).

## Rename a tuned speaker (2026-10-24)
Owner wants a friendly, editable name per speaker. The profile already has a `name` (set at save, prefilled with the Bluetooth name) but it cannot be changed afterwards.
- [x] N1. `CarDspPreferences.renameProfile` saves the new name and updates the chip at once when that speaker is active; JVM test `renameKeepsTheModeAndUpdatesTheActiveName`.
- [x] N2. Settings card: a Rename button per speaker opens a dialog with the name (1 to 40 characters) and the Bluetooth device name underneath. Opens focused with the old name selected; emulator rename to "Weekend MU-X" saved to the profile file.
- [x] N3. Speaker rows in Settings and the Now Playing dialog show the Bluetooth name when it differs from the friendly name. Seen in Settings on the emulator.
- [x] N4. 197 tests per flavor, release built and installed on the phone 2026-10-24 (signer, all-files access verified); screenshots from the emulator since the phone was asleep.

# Simplify tuning selection (2026-11-06)

- [x] Add direct profile creation from the quick empty state and clarify EQ labels.
- [x] Group compatible profiles first and make selected profiles clear.
- [x] Update affected checks, run targeted validation and inspect diffs.

Verification: app/test APK builds passed; all five TuningAudibilityTest unit cases passed. API 34 emulator: 26 distinct tuning UI checks passed across QuickTuningUiTest, HeadphoneEqUiTest, PlaybackTuningUiTest, AudioTuningSheetUiTest and SoundSignatureUiTest (the signature class was rerun after updating its obsolete no-creation assertion). Direct creation/cancellation also passed at 150% system text. Inspected quick tuning and enlarged chooser screenshots in /tmp/musicmate-quick-tuning.png and /tmp/musicmate-quick-profile-creation-large.png. Restored emulator activity and font settings. Working-tree and main diff whitespace checks passed. Build/UI logs: /tmp/musicmate-tuning-*.log. Existing unrelated changes retained.

# Audio tuning UX review (2026-11-06)

- [x] Trace quick tuning, profile/sound selection, Auto settings and full editor.
- [x] Check layout, labels, accessibility and available runtime evidence (source only; no connected Android device).
- [x] Rank simplifications with source references and verification limits; see tasks/audio-tuning-ux-review-2026-11-06.md.

# Mood-aware Auto sound rules (2026-10-30)

- [x] Add Mood metadata matching with deterministic priority and unit coverage.
- [x] Add searchable Mood choices and an explicit way to load improved starter matches without replacing saved edits silently.
- [x] Verify unit/UI behavior and document the rule choices and limitations.

Verification: all 381 app unit tests and all eight AutoSoundUiTest checks pass on API 34. The reset test failed before the control existed, then passed with confirmation, cancellation and fallback preservation. Mood matching, hold/resume, legacy JSON defaults and tag aliases are covered. All 27 starter rules were checked against the actual Genre/Style/Mood resource lists, without duplicate field/value pairs. Updated the guide and changelog; working-tree and main whitespace checks pass. Restored the emulator activity setting. Installed the updated build on 192.168.1.54:42765 with app data retained.

# Refine default music styles (2026-10-30)

- [x] Update the shared Style vocabulary with the agreed names and Acoustic only.
- [x] Verify the exact list and Android resource compilation; update the changelog.

Verification: checked the exact 14-entry list for duplicates and excluded additions. `:app:processGithubDebugResources` passed. The shared defaults serve both the tag editor and Auto match picker; existing track tags and saved rules are unchanged.

# Document and commit Auto sound mode (2026-10-30)

- [x] Update the tuning guide and Unreleased changelog with Auto behavior and searchable match settings.
- [x] Review the staged changes and recorded test results.
- [x] Commit the feature, tests and documentation, leaving unrelated files untouched.

# Searchable Auto match tags (2026-10-30)

- [x] Find MusicMate's default genre/style vocabularies and existing dropdown pattern.
- [x] Replace Add/Edit match tag text entry with a dropdown that filters as the user types; test field switching and saving.
- [x] Verify tests/build, inspect on Android and install the update on the phone.

Verification: the new test first failed against the plain field, then all six AutoSoundUiTest checks passed on the emulator, including opening the default list, filtering Electronic, switching to Style, filtering Ambient and saving the selected rule. All 374 app unit tests pass. Built and installed with data retained on 192.168.1.54:42765; emulator activity setting restored. Only Audio Auto match creation/editing changed; tag editor source is untouched. Custom rule text remains supported. Updated guidance and docs; included in the Auto sound mode feature commit.

# Auto sound mode (2026-10-30)

- [x] Trace track metadata, shared DSP status, persistence, and quick/full tuning controls.
- [x] Implement and test deterministic genre/style matching, editable rules and fallback, preserving manual settings and correction profiles.
- [x] Apply resolved modes on track changes and support a session hold without persisting automatic results.
- [x] Add shared Auto/Manual controls, active reason, settings and session override to quick/full tuning.
- [x] Run focused and full checks, inspect runtime UI and review diffs; document limitations and verification.

Verification: 374 app unit tests pass. Separate emulator invocations pass AutoSoundUiTest (5), QuickTuningUiTest (8), PlaybackTuningUiTest (2), AudioTuningSheetUiTest (5), and HeadphoneEqUiTest (8); Auto's five checks pass again after gold-theme polish. Fixed a pre-existing color-budget failure by replacing three identical gold literals with the existing Brand.Gold token. Whitespace checks pass against the working tree and main. Matching/session tests cover fallback, priority, complete tags, persistence, manual retention and USB consent. No acoustic quality claim or mood inference. Installed the final build on 192.168.1.54:42765 with data retained. Inspected fresh phone Auto/settings screenshots and text hierarchy; actions remain reachable by scrolling. Restored Manual with Vocal and the HiBy correction unchanged, left playback stopped, and restored the emulator activity setting. Included in the Auto sound mode feature commit.

# Playback output and screen awake
- [x] Clarify output selection and remove duplicate header action.
- [x] Apply the existing screen-awake setting to playback.
- [x] Verify build; device UI and screen timeout remain to be checked.

# Stabilize car playback (2026-10-29)
- [x] Correct disconnect handling and verify active-output behavior.
- [x] Improve persisted pause and playback-error diagnostics.
- [x] Run focused verification and review the combined changes.

# Investigate car playback interruptions, October 28–29 (2026-10-29)

- [x] Preserve available historical phone logs and identify coverage for yesterday and this morning.
- [x] Trace stop/pause/focus/route/error sequences against playback code and identify supported causes.
- [x] Report timeline and evidence; implement and verify a focused fix only if a code defect is established.

# Refine transport visual hierarchy (2026-10-29)

- [x] Check primary guidance and reduce visible Previous/Next circles to about two-thirds of Play/Pause, retaining large hit areas.
- [x] Verify targets/callbacks and affected screenshots.
- [x] Update docs, install and inspect on the phone when unlocked.

Checked Android accessibility (48dp phone minimum) and Google Design for Driving sizing (76dp head-unit minimum); neither prescribes a visual ratio. Previous/Next now have 60dp visible circles and 28dp icons inside the retained 80dp IconButton targets, compared with 88dp Play/Pause. Two transport checks pass at compact width and normal/150% text, proving targets, separation and callbacks. Three updated playback screenshot checks pass. Installed with data retained, inspected fresh emulator and unlocked-phone captures, left playback stopped and sound settings unchanged. UI documentation updated.

# Improve playback transport reachability (2026-10-29)

- [x] Inspect transport sizing and layout; give Previous and Next prominent, well-separated controls.
- [x] Verify touch targets, callbacks, compact layout and accessibility.
- [x] Update docs, install and inspect the phone layout.

Reproduced 48dp navigation targets with failing compact/large-text tests. Previous/Next now have 80dp circular targets and 36dp icons, Play/Pause is 88dp, and Shuffle/Repeat/Sleep timer have a separate 48dp row. Navigation callbacks and enabled-state behavior are retained. Nine focused UI checks pass (transport 2, accessibility 5, playback tuning 2), covering 320dp width, 150% text, separation, viewport bounds and callback delivery. Fresh normal/large emulator captures inspected. Installed with app data retained and inspected on the unlocked phone: Previous [54,1851][294,2091], Next [786,1851][1026,2091] are both 240px/80dp with generous separation. Phone shows AL5125SH_BT and Lively · On; no playback, mode or volume changes made. Updated UI docs; all three affected playback screenshot checks pass after intentional reference updates. Whitespace checks pass for working tree and main comparison.

# Focus quick tuning on listening (2026-10-29)

- [x] Replace the large footer with compact header management access; remove quick profile creation.
- [x] Verify management navigation, saved profile/mode controls and normal/large text.
- [x] Update docs and install on phone.
- [x] Inspect the latest phone layout after unlock.

Removed the quick creation callback/dialog and full-width footer rather than keeping unused navigation. Manage tuning is a compact fixed-header text action; the editor retains all three creation methods. A failing regression first confirmed the unwanted Create profile entry, then all 353 unit tests and 16 focused UI checks passed (QuickTuning 8, SoundSignature 3, AudioTuningSheet 5). Signature checks also pass at 150% system text, and sheet checks ran with the system text enlarged. Emulator font restored to 1.0. Fresh quick-panel capture inspected. Updated Audio tuning/UI/review docs and installed with data retained on `192.168.1.67:34667`; after unlock, fresh physical-phone capture confirmed compact Manage tuning, readable mode cards and no creation row/footer. Header action retained identical bounds [700,323][1008,387] after scrolling, Custom remained reachable, and Manage tuning opened the full editor with AL5125SH_BT / Active preserved. Returned to playback. No playback or sound settings changed. Whitespace checks pass against the working tree and main.

# Descriptive sound signature cards (2026-10-29)

- [x] Replace mode pills with shared descriptive cards and restrained selected styling; retain truthful status and USB consent.
- [x] Group current sound information and pin Full audio tuning outside the quick panel scroll area.
- [x] Verify selection, pinned action and large-text scrolling; inspect emulator, update docs and install.
- [x] Inspect the installed quick/full panels on the physical phone after it is unlocked.

Verification: 353 unit tests pass. Separate emulator runs pass QuickTuningUiTest (8), HeadphoneEqUiTest (8), AudioTuningSheetUiTest (5) and SoundSignatureUiTest (3). The three signature checks also pass at 150% system font size; emulator text restored to 1.0. Text-layout checks exposed fractional-width overflow in wrap-content descriptions; descriptions now use the full card width. Fresh quick/full/large captures inspected, including restrained selection and pinned footer. Working-tree and main-comparison whitespace checks pass. Updated Audio tuning/UI/review documentation. Installed with data retained on `192.168.1.67:34667`; phone reports last update `2026-10-29 11:51:43`. After user unlocked the phone, fresh quick/full captures were inspected: descriptions wrap, Vocal uses the restrained selected state, and Full audio tuning stays at identical bounds [144,2138][876,2212] before/after scrolling. Full editor opens and all three calibration methods plus profile management are reachable; Close stays pinned. Returned to playback with AL5125SH_BT / Vocal / Active preserved. No mode/profile/volume changes or autoplay performed. Changes remain uncommitted.

# Compact playback sound control (2026-10-29)

- [x] Verify the playback entry uses one readable mode/status line and retains its 48dp target.
- [x] Replace the stacked label, preserving status semantics and responsive output layout.
- [x] Check normal/large text, update documentation, install and inspect on the phone.

Reproduced the missing compact label with failing playback checks, then replaced the stacked text with one annotated mode/status line. Both normal and 150% text checks pass, asserting one line with no visual overflow, a 48dp target and access to quick tuning. Accessibility name/state remain intact. Updated Audio tuning/UI docs; working-tree and main-comparison whitespace checks pass. Installed with data retained on `192.168.1.67:34667` and inspected a fresh physical-phone capture: equalizer + Vocal · On fits beside AL5125SH_BT / BLUETOOTH with aligned 48dp controls. No automatic playback started; changes remain uncommitted.

# Implement reviewed DSP tuning experience (2026-10-29)

- [x] Prove and unify mode-selection, bypass and route-status behavior without granting USB consent implicitly.
- [x] Polish playback entry and shared quick/full tuning controls; reorder listening controls ahead of curve and group management actions.
- [x] Verify focused regressions, full unit suite and emulator layouts at normal/large text; update documentation and inspect diffs.

Implementation starts from `docs/technical/DSP_UX_REVIEW.md`. Preserve existing uncommitted work; use shared controls for consistent behavior. No DLNA processing or level-matched comparison is added.

Implemented shared active/bypassed/unavailable feedback, consistent explicit enablement, mode descriptions/radio semantics, No correction naming, Edit Custom shortcut, listening controls before the EQ curve and disclosed profile management. Creation type no longer gates the current profile's controls. The playback entry has a 48dp target; output controls wrap at larger text sizes. A new regression reproduced both normal/large device badges expanding vertically under a long output name; bounded device width, weighted name and single-line badge fix the cause.

Verification: reproduced legacy speaker mode auto-enablement with a failing UI regression, then confirmed the fix. All 353 app unit tests pass. Separate emulator runs pass QuickTuningUiTest (8), AudioTuningSheetUiTest (5, including whole-sheet 150% text and stable swipes), HeadphoneEqUiTest (8), PlaybackTuningUiTest (2) and MusicCenterAccessibilityTest (5): 28 UI/accessibility checks. Fresh quick/full/large-text/playback captures inspected. Seven affected playback screenshot previews pass after intentional reference updates. A broader comparison also exposed four closed-library reference mismatches outside this change; those references were restored and left untouched. Both working-tree and main-comparison whitespace checks pass. Updated Audio tuning, Headphone EQ, UI and review documentation. No physical sound-quality claim, phone installation or commit made for this redesign.

# Review playback DSP and Audio tuning UX (2026-10-29)

Installation follow-up: user requested installation after the redesign. Verified the GitHub debug APK includes all-files access, installed with `adb install -r` on `192.168.1.67:34667`, and opened MainActivity. Phone reports version `3.30.0-261029`, last update `2026-10-29 11:09:42`. App data retained.

Phone inspection follow-up: captured real playback, quick tuning and full tuning on `192.168.1.67:34667`. Playback shows AL5125SH_BT and Vocal / On; both panels show the saved profile and Active. No observed overlap/clipping in the inspected controls. Quick mode choices wrap to three rows at the phone's current display configuration (system font scale 1.0), placing Full audio tuning below the first viewport; one scroll reaches it. Full editor keeps Close pinned; EQ curve, all three calibration actions and Manage profiles are reachable by scrolling. Returned to playback without changing sound mode, profile, enablement or volume. Remaining polish: make Full audio tuning persistently accessible in the quick panel.

- [x] Inspect current Compose controls, playback state and fresh quick/full tuning layouts using the requested UI/UX Pro Max skill.
- [x] Assess clarity, profile/mode interaction, visual hierarchy, accessibility and audio-specific trust cues.
- [x] Deliver a prioritized design review and concrete target flows for a polished audio app.

Review saved in `docs/technical/DSP_UX_REVIEW.md`. Fresh quick/full emulator captures inspected, including full tuning at 150% text; four quick and four full-sheet UI checks pass. Highest priority: consistent mode activation, truthful active/bypassed/unavailable states, and bypass that preserves profile selection. Proposed quick/full hierarchy and acceptance checks documented; no production UI changed for this review.

# Fix microphone calibration level prompts (2026-10-29)

- [x] Trace clipping, noise and level decisions; reproduce the repeated up/down guidance with focused tests.
- [x] Fix the root cause and make guidance match the measured condition while preserving clipping and SNR protection.
- [x] Run relevant unit/UI checks, document behavior and install the verified build with data retained.

Finding: each rejection previously gave independent volume advice even though TOO_QUIET measures SNR and CLIPPED tests the whole recording. Added retry history so opposite rejections produce consistent lower-setting/noise/placement guidance; reset after an accepted position or new session. Seven guidance regressions and all 48 calibration unit tests pass. Capture and analyzer thresholds remain unchanged. User confirmed an external speaker/car system with the phone microphone; physical acoustic conditions still require an on-device retry. Rejection diagnostics now record reason/detail for follow-up.

Final verification: all 348 unit tests and four calibration UI tests pass, including long retry guidance at 150% text with the pinned Measure again action. Updated calibration documentation; diff whitespace against main passes. Confirmed GitHub APK all-files access and installed with app data retained on `192.168.1.67:34667`, then launched MainActivity. Version `3.30.0-261029`, update time `2026-10-29 10:17:37`. No automatic acoustic sweep was played on the phone. Changes remain uncommitted.

# Review and fix other sheet swipes (2026-10-29)

- [x] Review Music Center, Related tracks and Tags from filename; reproduce sheet movement with touch tests.
- [x] Fix confirmed body-scroll/drag conflicts and preserve scrolling, actions and dismissal.
- [x] Run targeted UI and unit checks, review documentation and install the GitHub build with app data retained.

Confirmed red: body swipes dismissed Music Center and Related tracks; filename tags moved off-screen (header y=485 to y=2315). Disabled modal sheet gestures and removed drag handles, including the Music Center preview handle. All three new swipe regression checks now pass, including bottom-row access, callbacks, Music Center tab selection and explicit dismissal. Queue reorder code remains unchanged.

Final verification: 341 unit tests, three new sheet swipe checks and five Music Center accessibility checks pass. Updated and validated ten affected Music Center screenshot references; inspected compact and queue layouts. Documented the sheet gesture contract in `docs/technical/UI.md`; diff whitespace against main passes. Confirmed the GitHub APK's all-files access, installed with data retained on `192.168.1.67:34667`, and launched MainActivity. Version `3.30.0-261029`, update time `2026-10-29 09:38:07`. Changes remain uncommitted.

# Fix tuning swipe bounce (2026-10-29)

- [x] Reproduce the reported swipe movement with real touch gestures and identify sheet/scroll ownership.
- [x] Apply the smallest fix and add a swipe regression check.
- [x] Verify quick/full tuning, build and install the corrected GitHub APK with data retained.

Cause and fix: vertical body scrolling competed with the modal sheet's drag gestures. The original gesture check showed 119 px of unwanted sheet movement in both panels. Disable sheet gestures and remove the drag handle; keep body scrolling and explicit dismissal. Four sheet UI checks now pass, including real swipes in quick/full panels and existing normal/large-text editor controls. Complete-swipe position recording avoids waiting for UI idle while a touch is held at an overscroll boundary.

Verification: 341 unit tests and four quick-tuning UI tests pass in the final build, in addition to the four sheet checks. Diff whitespace against main passes. Confirmed the GitHub APK retains all-files access, installed with data retained on `192.168.1.67:34667`, and launched MainActivity. Package update time: `2026-10-29 09:11:43`.

# Now Playing quick tuning (2026-10-29)

- [x] Add a compact playback tuning panel with saved speaker/headphone profile selection, mode selection and processing state.
- [x] Reuse full tuning persistence and route safeguards; provide full tuning and new-profile access without leaving playback.
- [x] Verify profile/mode changes, USB and incompatible outputs, navigation and compact/large-text layouts; update docs.
- [x] Install the verified build when the phone's current wireless debugging address is available.

Design: one labeled profile selector rather than speaker/headphone tabs in the quick panel. Keep the full editor in the main menu. Reuse existing profile and mode callbacks, preserve USB consent and explain when a saved correction cannot apply to the current output.

Verification: 341 unit tests pass. Four quick-panel UI tests pass at normal size and on a compact display at 150% text. Eight existing profile UI tests and two full-sheet UI tests pass. Inspected both quick-panel layouts; checked documentation links and diff whitespace against main. Installed with app data retained on `192.168.1.67:34667` and launched MainActivity; version `3.30.0-261029`, update time `2026-10-29 08:29:55`. Changes remain uncommitted.

# Commit musical DSP tuning and menu update (2026-10-29)

- [x] Review the pending DSP changes and finish user/developer documentation.
- [x] Confirm verification evidence, file scope and staged whitespace; exclude unrelated local files.
- [x] Commit code, samples, tests, screenshot references and documentation.

Verification: final 341-test unit suite passes, including the color-token budget check. Preserved prior emulator and four screenshot validation evidence. Checked documentation links and staged whitespace. Commit scope includes only the reviewed DSP/menu delivery; unrelated local directories remain untracked.

# Audio tuning in the main menu (2026-10-29)

- [x] Put Audio tuning directly below Settings and open the shared tuning sheet after closing the drawer.
- [x] Remove the Settings entry and unused wrapper; update navigation documentation.
- [x] Build and verify menu order, opening/dismissal and Settings; install the updated GitHub build on the connected phone.

Verification: GitHub app and test APKs build. Emulator menu-order/open/dismiss and Settings accessibility checks pass. Four Settings screenshot references refreshed and validated, including 200% text; expanded preview inspected. Navigation documentation updated, diff whitespace checked against main. Installed with data retained and launched on `192.168.1.54:39189`; package update time `2026-10-29 07:08:34`.

# Five-step musical listening calibration (2026-10-29)

- [x] Define five independent bands, repeatable musical examples and compatibility with saved three-band profiles.
- [x] Add regression coverage and implement profile persistence, validation, mono averaging and sample playback.
- [x] Update the guide with musical labels, Original / Adjusted, advanced controls and a final musical comparison.
- [x] Verify unit and emulator flows, inspect the UI, update documentation and install the GitHub build.

Design: retain the existing 100 Hz shelf, 1 kHz peak and 8 kHz shelf exactly. Add neutral-default 300 Hz warmth and 3 kHz clarity controls. Five steps: Bass impact, Warmth, Vocal naturalness, Clarity, Treble detail. Samples must be bundled or generated repeatably offline, with fixed audition level and identical source data for Original / Adjusted. Keep per-ear calibration, skip/replay, USB consent, route/focus cancellation and explicit playback.

Verification: 341 unit tests pass. Twelve UI tests pass across the musical guide, existing calibration controls and speaker/headphone save/reopen flows; the new guide and save flows also pass on a compact emulator at 150% text size. Inspected guide/comparison screenshots, checked documentation links and diff whitespace. Installed the GitHub APK with app data retained on `192.168.1.54:39189` (3.30.0-261029, update time 2026-10-29 06:56:08), then launched MainActivity. Physical acoustic quality still requires listening on the intended output.

# Document and commit DSP tuning (2026-10-29)

- [x] Update DSP documentation to match the shared tuning sheet, profile controls and calibration actions.
- [x] Review the complete DSP diff and verification evidence; exclude unrelated local files.
- [x] Prepare the reviewed DSP code, tests and documentation for commit; preserve unrelated local files.

Verification: GitHub build and all 330 unit tests pass. The editor previously passed 20 UI tests, including compact-screen and large-text checks. Local documentation links and staged whitespace checked. Staging excludes `.claude/` and `.taste-skill/`.

# DSP tuning UI redesign (2026-10-29)

- [x] Inspect the current tuning screen, brand patterns and interaction coverage; define the visual hierarchy.
- [x] Rebuild the tuning panel with a spacious sheet, clear output/profile controls, response visualization and deliberate calibration actions.
- [x] Verify the key selection, bypass, USB consent and calibration flows; inspect emulator screenshots and large-text scrolling.
- [x] Build the GitHub APK, review the scoped diff and install the verified update on the phone.

Validation: 330 unit tests pass; 20 distinct UI tests pass across HeadphoneEqUiTest (8), ManualHeadphoneUiTest (8), ManualHeadphoneFlowTest (2), AudioTuningSheetUiTest (2). Inspected normal, 150% system font and compact-screen screenshots; corrected graph heading wrapping and compact tab wording. Shared profile selection, explicit USB consent, bypass, mode changes, imports, offline deletion and save/reopen remain covered. GitHub APK retains MANAGE_EXTERNAL_STORAGE. Installed with adb install -r on 192.168.1.54:41099; app data preserved. Scoped HEAD/main diff and whitespace reviewed.

Design question: What correction and sound mode am I using on this output?
Style: True-black flat, seed 1925248322; native Android adaptation retaining existing palette and type. Signature: actual gold frequency-response curve. Browser DOM inspection does not apply to Compose; verify emulator screenshots instead.
Direction: Music Mate's dark surfaces and gold response line; flat audio-console sections with clear typography and quiet rules. Keep the shared profile dropdown. Calibration uses descriptive action rows; import and profile details remain secondary. Use actual correction data, never illustrative metrics.

# Unified correction profiles and calibration methods (2026-10-28)

- [x] Trace saved profile selection, output assignment, microphone measurement and listening flows; define shared profile presentation without losing existing data.
- [x] Add one consistent saved-profile dropdown and creation methods: Microphone, Listening, AutoEQ.
- [x] Support microphone and listening setup for speaker and headphone/earphone outputs, with accurate route/measurement requirements.
- [x] Verify profile persistence, output isolation, correction playback and creation flows with focused tests and emulator checks.
- [x] Run the app unit suite, review diffs against HEAD/main and document practical measurement limits.
- Validation: 330 unit tests pass. Separate API 36 emulator suites pass HeadphoneEqUiTest (8), ManualHeadphoneUiTest (8), ManualHeadphoneFlowTest (2), HeadphoneMicrophoneUiTest (2), CalibrationNoiseUiTest (3): 23 distinct UI tests. Saved speaker and headphone listening profiles reopen with their output/method and correct binding; an open draft retains its chosen type through route changes. Reviewed HEAD/main diffs and whitespace. GitHub APK retains all-files access. Physical fixture accuracy, external microphone routing and acoustic listening remain hardware validation tasks.
- Design: preserve released stores; adapt legacy microphone speaker profiles into the common selector with stable prefixed IDs. Route bindings own enablement/modes and explicit USB consent. Microphone headphones require a named fixture, rig-specific target and locked input/output; do not reuse the speaker target. AutoEQ searches headphone models; speakers use measured parametric imports.

# Shared sound modes and guided listening tuning (2026-10-28)

- [x] Ground and sketch: share sound-mode curves and UI labels, keep correction profiles/output consent separate; use bounded band-by-band listening tests rather than a continuous loudness-matching sweep.
- [x] Unify speaker/headphone modes with Neutral/Bass/Vocal/Lively/Relax/Night/Custom; preserve released settings and correction values.
- [x] Add low-to-high headphone listening steps with left/right tests, per-ear adjustments, skip/replay/bypass and stop on lifecycle/output changes.
- [x] Verify generated frequencies, gain limits, ear isolation, persistence, mode composition and guide navigation with regression tests.
- [x] Run app unit suites and affected emulator UI tests; review diffs and document listening-vs-microphone limits.
- Validation: shared Bass curve, generated single-band frequencies, and guided UI each reproduced a failure before implementation. All 318 github app unit tests pass; separate API 36 emulator runs pass ManualHeadphoneUiTest (7), HeadphoneEqUiTest (6), ManualHeadphoneFlowTest (1). New guide preserves skipped bands/opposite ear values, supports manual fallback and saves/reopens its settings. Existing correction, bypass, consent and cancellation coverage passes. Reviewed diffs against HEAD/main and whitespace checks. No physical headphone listening or acoustic measurement performed.
- Compatibility: named modes share curves and Night compression; legacy Custom ranges remain speaker +/-4 dB and headphone +/-6 dB. Stored car REFERENCE displays Neutral. The prior conversation's unreleased headphone soundPreset field is replaced by shared soundMode; released JSON without that field retains Custom trims.

Design: a shared SoundMode owns named curves and Night compression; correction profiles remain independent. Extend the existing headphone test request with a typed test band (bass 100 Hz, mids 1 kHz, treble 8 kHz), defaulting to the existing multitone for compatibility. Keep draft listening adjustments local until Save. Reuse the existing player and cancellation/focus/route checks. Rejected a continuous sweep because adjusting while pitch changes is ambiguous; rejected frequency-to-frequency loudness matching because perceived loudness is not a flat-response measurement.

# Headphone sound modes over calibration (2026-10-28)

- [x] Add regression tests for calibrated profiles plus per-output sound presets and persistence.
- [x] Compose selected presets over imported/manual corrections, preserving channel calibration and legacy custom trims.
- [x] Add accessible preset selection to headphone settings with the active mode visible.
- [x] Run unit tests, build instrumentation tests, and verify the UI on a device if available.
- Validation: reproduced missing preset composition in three failing routing tests before the fix; all 316 github app unit tests pass afterward. Six Headphone EQ and six DIY UI tests pass in separate runs on the API 36 emulator, including Bass-to-Neutral selection with calibration and EQ enablement retained, plus Custom controls on DIY profiles. Diff checked against main and HEAD. No physical DAC/headphone listening test performed.

# DSP quality and stability (2026-10-28)

- [x] Trace DSP processing, PCM conversion and output lifecycle; identify reachable defects.
- [x] Reproduce confirmed audio defects with failing regression tests.
- [x] Implement minimal fixes, verifying each slice.
- [x] Run audio tests, inspect the diff against main, and document hardware validation limits.
- Fixed a discontinuity when settings change during a crossfade: finish the current fade, then pick up the latest target at a block boundary. Immediate unconsented USB bypass remains covered by the existing tests.
- Validation: the new continuity regression failed before the fix and passes at 44.1/48/96/192 kHz afterward. DSP/headphone/output tests and the full github debug app unit suite pass; diff checked against main (the existing branch contains prior DSP changes). No hardware listening, long-session, or acoustic/distortion measurements performed.

# `playlists.json` rule review (2026-10-15)

- [x] Inventory each playlist and its JSON rules, including wildcard and exclusion shapes. Ten playlists: four smart, one title with 519 rules, five genre; no exclusions.
- [x] Compare each rule with `PlaylistEntry`/`GenreRule` matching semantics and the playlist's stated intent.
- [x] Check for duplicate, dead, overbroad, or missing rules using reproducible source or library evidence. JSON parses; one duplicate title/artist key; no unrecognized genre/mood/style terms. No representative phone library available for zero-match validation.
- [x] Report specific JSON paths and proposed corrections, separating confirmed errors from taste or intent choices.

# Static and dynamic playlist review (2026-10-15)

- [x] Trace creation, editing, deletion, persistence, and playback for static playlists.
- [x] Trace rule evaluation, refresh, persistence, and playback for dynamic playlists.
- [x] Check existing tests and run focused verification for suspected defects. Focused core tests passed: 7 `PlaylistEntrySmartTest` and 41 `QueueManagerTest`; neither covers the reviewed UI count, duplicate-name, or delimiter paths.
- [x] Report only confirmed findings with severity, file references, and any verification limits. Static add order is noted as an unresolved product expectation because the docs do not promise playlist ordering.

# Idle playback artwork bug (2026-10-15)

- [x] Reproduce it yourself on the matching surface via the driver skill (Non-negotiables), even when a debug or instrumentation protocol says to ask the user to reproduce. Ask the user only with a stated, specific reason the control surface cannot reach the target, and only after driving it as far as it goes. If it won't reproduce directly, synthesize the trigger, tighten conditions, or instrument until it fires. Attempted on the Android emulator with 22 tracks, screen-off playback, and song transitions. The blank-list symptom did not occur there; the user's phone is not connected.
- [x] Binary-search the cause. Form the candidate hypotheses, then rule them out until one survives. Seed them with `how` over the affected subsystem and the **why** skill for regression history. Each pass, take the split that cuts the most remaining problem space, get runtime evidence, eliminate. When program state is unclear, add instrumentation or logging and read it as the code runs. Don't guess. Drive a long or stubborn hunt with Claude Code's `loop` skill. Confirm the surviving *mechanism* with runtime evidence before the step-3 architect/interrogate fan-out. Service StateFlow polling does not replay old tracks; `MainActivity` did issue an artwork request on every playback tick and accepted late results. The independent list-image path yielded no confirmed failure on the emulator.
- [x] Plan the fix. If it crosses a function boundary, `architect` first. Delegate implementation to a subagent using your configured bug-fix model (default in poteto-mode's Models section) with a specific scope. Scoped to `MainActivity` artwork requests and callbacks; no function signature changed.
- [x] Verify on the same surface. The original repro now passes. "Inconclusive" or wrong-surface is not a pass. Flag it. Unit tests show branch behavior, not bug absence. `:app:testDebugUnitTest` and `:app:assembleDebug` passed; the updated emulator displayed matching artwork after rapid Next actions and a screen-off song transition. Exact reported blank-list reproduction remains inconclusive on the emulator.
- [x] Stage the commits so the failing repro lands before the fix in git history. See the **tdd** skill for the failing-test-first cadence when the bug has a cheap local test path. Skip it when the test would be expensive, integration-heavy, or unclear. Skip: no cheap JVM test can drive `MainActivity` and Coil; the app has no Robolectric dependency, and the emulator baseline did not reproduce the reported blank-list symptom. No commit requested.
- [x] Run **Opening a PR**. Skip: this is a local bug fix on `master`; no PR target was requested.

# UX review fixes (2026-10-13, from tasks/ux-review-2026-10-13.md)
Branch `ux-review-fixes`. Batches 1-6 done and checked on the phone (S25, 3.27.0 release build installed as an update, library kept): commits 91807796, 0271b56b, d0550dda, 3eef3906, e55be42b, ad796260. Signing gate passed (installed and release certs both B5:75:7A:...).
Found while doing batch 6: with no current track and shuffle off, Play calls `getNextTrack()` from index 0 and starts the SECOND track (`MainActivity.onDockPlayPauseClicked`). Up Next shows that honestly; the Play behaviour itself is not changed. Needs a decision.
Verify each batch with unit tests, `./gradlew :app:assembleDebug`, and a check on the phone (S25, real library). Run the reviewer on batches 3, 4 and 6. Proposed: one commit per batch on a branch (we are on `master`); this needs the user's OK.

Gate before the first install on the phone: the installed 3.27.0 is a release build (not debuggable), with 8,117 tracks scanned and analysed. Compare the signing certificate from `./gradlew :app:signingReport` with the installed package's certificate (`apksigner verify --print-certs` on the pulled APK). If they differ, stop and ask. Never uninstall and never clear data (see lessons.md).

Decisions (user, 2026-10-13): D1 "24-Bit Lossless"; D2 option A (list order + Shuffled chip + Up next line); D3 #FFD700; D4 drop "Hi-Res Edition".
- D1 (batch 3): one name for the 24-bit 44.1-88.2 kHz tier, used in all four places: library pill, song page, the Audio Quality category title (`Constants.TITLE_CD_EXT_QUALITY` = "Studio Quality", which also produces the "SQ" tile) and About. Proposal: "24-Bit" (pill "24-BIT", expanded "24-BIT LOSSLESS", category "24-Bit Lossless"). The Hi-Res tile badge becomes "HI-RES" / "LOSSLESS" instead of "24-BIT" / "STUDIO".
- D2 (batch 6): Queue tab while shuffle is on. Option A: keep list order, add a "Shuffled" chip and an "Up next: <title>" line in the header (row numbers stay list positions). Option B: render the list in play order while shuffle is on (drag-reorder then edits the play order).
- D4 (batch 2): drop "Hi-Res Edition" from the drawer, or keep it.
- D3 (batch 7): the brand gold. Proposal: #FFD700 for accents and active states, with #FFB300 kept only as a DR tier color.

## Batch 1: contrast (#1, #2)
- [x] `dr_high` is used for text (`TagUIUtils.getDRScoreColor`) and by `shape_background_dr_high.xml` via the deprecated `getDRScoreBackgroundColor`, which has no callers. Change it from #2E7D32 to a green of at least 4.5:1 on #1E1E1E (#4CAF50 measures 6.74 / 6.00 / 5.02:1 on #121212 / #1E1E1E / #2C2C2C).
- [x] `dr_none` #BDBDBD: 9.97:1 on #121212, no change.
- [x] Replace text uses of #888888 (13), #757575 (6) and #666666 (2) with one muted color of at least 4.5:1 on #2C2C2C (#9E9E9E, 5.21:1). Change only text colors; leave borders and icon tints alone. Add it as `Text.Muted` in `MusicMateDesignTokens` and reference it.
- [x] Phone: library DR labels, queue durations, server status lines. Screenshot before and after.

## Batch 2: copy and labels (#11, #12, #13, #16)
- [x] `StringUtils.formatDuration` (core): fix singular and plural in the current capitalized style: "1 Hr", "2 Hrs", "1 Min", "1.0 Day". "Days of Music" was chosen on purpose (`MainActivity.java:670`). Add a core unit test that covers the singular, plural, under-1-hour and over-48-hour cases. There are 16 callers in app/core, and no other module uses it. Check that none of them parses the text.
- [x] Tech Info (`TagsTechnicalPage.kt:263,421`): "ALL TAGS COMPLETE" and "High resolution" / "Standard" / "Low resolution".
- [x] Tag editor dock: the "Format" button (`action_reformat` -> `TagsEditorFragment.doFormatTags`) fixes garbled Thai text and tidies the case and spacing of tag text. Add a new string "Tidy Tags" for that button; `btn_format` itself is used only there.
- [x] Drawer: drop " • Hi-Res Edition" (`MainScaffold.kt:232`), per D4.

## Batch 3: one name per quality tier (#6), needs D1
- [x] Before renaming, list every `contains(` / `equals` match on tier strings. Several places pick a tier or color by searching the label text: `AudioPresentation.tier(raw)` ("Studio", "Hi-Res", "Lossless"), `FolderListItem` (category title), `NowPlayingPage.kt:121` (grade name), `TagRepository.java:483,556` and `TagUtils.java:368` (`TITLE_CD_EXT_QUALITY` as a search keyword). Make each match use the tier itself or the constant, not the display text.
- [x] `AudioPresentation.Tier.PCM24` labels and `Constants.TITLE_CD_EXT_QUALITY` per D1.
- [x] `FolderListItem.kt:187-188`: the Hi-Res tile badge uses the same tier names.
- [x] Grep for other hard-coded tier strings ("24-BIT STUDIO", "STUDIO", "HI-RES LOSSLESS") and route them through `AudioPresentation`.
- [x] Unit test: each tier maps to its labels, and each new label maps back to the same tier through `tier(raw)`, so a rename that breaks detection fails the test.
- [x] Phone: library pill, song page, Audio Quality tiles, console badge.

## Batch 4: console states (#15 idle part, #17)
- [x] With no track loaded, hide the quality badge and the empty pill (`FullscreenStudioConsole.kt`). Show "N tracks queued" instead of "MusicMate / High-Fidelity Studio Audio".
- [x] Music Center idle: first read what Play does when no track is loaded but the queue is not empty. If it starts the queue, change "Ready to play / Select a song" to "N tracks queued · press Play".
- [x] Console Shuffle and Repeat descriptions include their state, matching Music Center (`FullscreenStudioConsole.kt:752,836`).

## Batch 5: layout (#8, #10, #14)
- [x] Queue chip row: already had a fading edge (`QueuePage.kt` CompactSourceDeck); review finding #8 was wrong. Duplicate count left as is.
- [x] Song page Play button: move it off the artwork, next to the title, as a 48dp button. Keep long-press for Play Next.
- [x] Output picker: give the app list a fading bottom edge, or a height that ends on a whole row.

## Batch 6: queue under shuffle (#15), needs D2
- [x] Per D2, in `QueuePage.kt` / `QueueState.kt`, using the play order from `QueueManager` (`shuffleOrder` / `shuffleIds`).
- [x] `QueueManagerTest`: a test that the play-order view matches `upNext()`.
- [x] Phone: with shuffle on, the console's Up Next matches what the Queue tab shows next.

## Batch 7: tokens (#5, #4, #3, #9), one file per commit
- [x] Theme already reads MusicMateDesignTokens (`MusicMateTheme.kt:41-52`). Gold per D3: accents use Brand.Gold (6986d49f, reviewer PASS; follow-up b8596356). Amber kept for DR 8-12, warnings, VU needles, Studio Console (now Brand.GoldAmber).
- [x] Guard: `ColorLiteralBudgetTest` (fbdf468c), per-file budget that only goes down; proved failing on an added literal. Regenerate with the scratchpad budget.py or by hand after each migration.
- [x] Migrated (values unchanged): QueuePage 72c1f058, MainScaffold 2b4ab384, FullscreenStudioConsole 7bcd31f4. New tokens: Brand.GoldContainer, Brand.GoldOutline, Status.Positive. Budget 861 -> 745.
- [x] Remaining files: context-based pass (borders, surfaces, accent gold) over 24 files, one commit each, reviewer PASS. Budget 745 -> 668. The rest are identity/data colours (tiers, sources, DR, meters, gradients) left literal on purpose; migrate only with a named role.
- [x] #4/#3/#9 in 3bbb1036, phone-checked: sentence text 11sp, caps badges >= 10sp, 48dp side-by-side buttons (playlist names wrap to 2 lines), gold seek bar, volume percent, console Up Next room.
- [x] Play fix follow-up: f73daa44 + 8cd25215 (first playable track on smart sources; reviewer CHANGES addressed).

## Not planned
- #7 PLAY NEXT pills: check them on the phone while something is playing (this changes the playback state, so ask first), then decide.

# Release 3.25.0 (2026-10-05)
- [x] Native USB bit-perfect playback (Android 14+): opt-in mixer bypass, exact decoded format matching, unity gain, ReplayGain bypass, and status badges.
- [x] Dock and Music Center output label alignment: both local playback surfaces consistently display the active resolved audio device name.
- [x] Verified on emulator: instrumented settings test passed, library scan and audio playback functional, settings toggle verified. Core and app unit tests pass.

# Docs reorganization and User Guide (2026-10-04)
- [x] Move docs with git mv (names unchanged): docs/ gets USER_GUIDE, MUSIC_QUALITY_GUIDE, screenshots; docs/technical/ gets TECHNICAL, DESIGN, UI, ECOSYSTEM, PLAYBACK_ARCHITECTURE, WEBSOCKET_API, WEBUI, PERFORMANCE, NETWORK_RESILIENCE; docs/archive/ gets superseded CHANGES_SUMMARY (removed HttpCore), NIO_WEB_ENGINE_10_10 (marked superseded), lib-review (2026-07). Root keeps README, CHANGELOG, CONTRIBUTING, RELEASE_NOTES (release workflow reads it).
- [x] Script: rewrite relative links after the move and check every Markdown link and image in the repo resolves.
- [x] Rewrite USER_GUIDE from the user's view, by task: getting started, browsing, playing and the queue, choosing a player, streaming and web remote, song details and tags, organizing files, quality badges, settings, troubleshooting. Check each behaviour on the phone while writing.
- [x] Capture extra screenshots for the guide (menu, queue, tag edit form, settings, Studio Console) after analysis finishes, same crop/resize script.
- [x] Verify links and images (check_links.py: 0 broken before and after the move), review, commit (no push).
- [x] Retake all README and User Guide screenshots on the emulator with a demo library of freely licensed music (CC0 / Creative Commons, covers free to use), so no copyrighted cover art or personal library is published. Current shots are from the user's phone (user decision 2026-10-04: keep as is until then).
  - [x] tools/demo-library: generate original music (numpy synthesis, varied dynamics), covers (CoreGraphics), FLAC 16/44.1, 24/88.2, 24/96, 24/192 and ALAC 24/48 via afconvert, tags and art via mutagen. Invented artists only.
  - [x] Emulator: replace old /sdcard/Music/MusicMate Demo, reinstall signed 3.24 build, grant access, scan, wait for analysis.
  - [x] Retake the 12 screenshots (player picker stays from the phone: emulator cannot see network renderers, and the shot has no cover art or library), crop, check each image.
  - [ ] Commit and push.
- [x] DR mismatch: Studio Console / Now Playing show dynamicRange truncated ("DR 13" for '39) while lists show drScore rounded ("DR14"). Use one field everywhere.
- [x] Library tips card says "Long Press: Long-press a song for batch editing", but in the default Play tracks mode a long press opens the song details; batch selection needs the Edit tags mode. Make the tip follow the tap mode.
- [x] Studio Console "UP NEXT" showed the playing track (Adagio Cantabile) while the queue's next was Afterglow (emulator, 2026-10-04).
- [x] "Don't show these tips again" kept the tips on screen until restart (GestureHintBanner had no state; callers pass no onDismissAll). Fixed with remembered dismissal; verified on the emulator queue tips.
- [x] Settings: "Album Gain" button text wraps onto two lines at default font size.

# Scan analysis phase (2026-10-04, found during README screenshots)
- [x] Deep scan (DR / extras for every track) runs inside the expedited ScanAudioFileWorker with no progress: header stays at "Scanning: 8302/8302 files" for hours. Job scheduler stops the job on timeout (observed STOP ... timeout after ~10 min, next run capped ~5 min) and WorkManager reruns the whole worker, repeating the folder walk and file check before resuming DR. Fix: separate DR worker chained after the scan that resumes from tracks without DR and reports "Analyzing: x/y". Verify on the phone, including a stop and resume.
- [x] Fixed: AnalyzeTracksWorker (unique work MusicAnalyzeWork) runs after the scan, cancelled by any new scan. Phone: header "Analyzing: x/5387 tracks"; forced timeout (cmd jobscheduler timeout -n androidx.work.systemjobscheduler) at 22:13:26, new run 15 s later at 34/3428, no scan job ran.
- [x] Parallel analysis (min(4, cores/2) threads) and ALAC temp file in cache. Phone: 1 thread 1.79 tracks/s overall (2.3/s on the same remaining tracks); 4 threads 5.1 tracks/s (630 tracks in 123 s), 428% CPU, PSS 420 MB, skin 42 C (moderate), no crashes.
- [x] DR badge color used truncation while the label rounds; color now rounds too.
- [x] DSD tracks never get DR (no Android DSD decoder: "Failed to initialize audio/dsd"), so every analysis run retries them. Decide: mark as not analyzable, or analyze via FFmpeg.

# Hotspot renderer discovery (2026-10-04)

- [x] Reproduce: hotspot on next to Wi-Fi; /proc/net/igmp showed SSDP joined on wlan0 only for 2.5 min (same pid); stopping/starting the server added swlan0.
- [x] Root cause 1: UPnP binds interfaces at startup; restart only on primary-IP change, which stays the Wi-Fi IP. Fix: interface signature; restart on change (hotspot/network events + 30 s tick), deferred while casting. Verified: swlan0 joined 31 s after hotspot on, no app restart.
- [x] Root cause 2: player list filtered renderers to the primary IP's subnet. Fix: accept any bound interface subnet. Verified: HiBy 10.221.118.217 listed (user).
- [x] Root cause 3 (found by step 2 failing: off/on lost ALL renderers): UpnpServiceImpl.shutdown() shuts down its configuration's executor; the hub reused one configuration, so a restarted stack never processed discovery replies. Fix: Supplier of a fresh configuration per stack (main, jetty, undertow modules). Verified with logs (same pid): off -> Verox re-added 1.3 s after restart; on -> HiBy R3 + Verox re-added; user confirmed both in the picker.
- skip: failing-test-first commit; no cheap local test (needs real interfaces); verified on device instead.

# Reliability review (2026-10-04)

- [x] Background tasks: MusicMateExecutors guards every task (Exception and LinkageError logged instead of killing the app); double-checked singleton fixed.
- [x] NIO request handler also catches LinkageError (e.g. ExceptionInInitializerError) and answers 500.
- [x] Media server: wake lock (4 h timeout) renewed every 30 s while RUNNING; lock methods synchronized; periodic discovery, renderer sync monitor and position poll chain survive exceptions.
- [x] Local playback: onPlayerError skips to the next track (once per bad item), stops after 3 failures in a row. Verified on the emulator with a corrupt file.
- [x] Main-thread DB audit: debug builds now reject main-thread Room queries (release still allows them as a safety net). Emulator sweep of launch, drawer screens, play, preview, editor, Tech Info, Music Center and search found 5 violations in 3 places, all fixed: editor Artist and genre-library lists (now loaded on IO), servers' fallback QueueManager (now lazy), shared QueueManager load at service start (background when constructed on the main thread; whenLoaded() publishes queue, shuffle and repeat). Final sweep: 0 violations.

# Genre / mood / style / origin presets (2026-10-04)

- [x] 1. Presets without "/" (single names); playlist rules use the same names; a shared vocabulary maps legacy values (e.g. "R&B, Soul", "Chill / Relax", "US/UK") to the new names on read and in matching.
- [x] 2. Playlist rules match value by value (split multi-value genre, case-insensitive, via the vocabulary).
- [x] 3. Dropdown opens with the full list and the current value ticked; filters only after typing; no unrelated fallback.
- [x] 4. Presets first, then "In your library" without near-duplicates; "Clear" in every field.
- [x] 5. One-line hint per field; tidy overlaps (Origin list; Style entries duplicating genres).
- [x] Tests (343, 8 new); emulator: dropdown opens full list with Clear and hint; legacy files tagged "Alternative/Indie" and "R&B/Soul" scan as "Alternative" and "R&B"; Personal Essentials includes the legacy Alternative track. Editor save also normalizes. Style "Ambient" kept (used by playlist rules); "Acoustic" style removed.

# Emulator review fixes, 13 items (2026-10-04)

- [x] 1. Search box does not auto-focus / open the keyboard on launch or return.
- [x] 2. System Access screen has a way back (and continues once storage is granted).
- [x] 3. Folder dialog: opaque; readable paths; clear "Add folder"; visible remove per folder.
- [x] 4. Drawer: "System Access" not truncated by its badge.
- [x] 5. Audio Quality: header counts categories; every category has art.
- [x] 6. Similar Tracks empty state says no similar songs were found.
- [x] 7. Playlist cover badges do not break or clip words.
- [x] 8. "Audiophile Sanctuary (DR12+)" only includes tracks that meet its rule.
- [x] 9. Artists/Genres show distinguishable placeholders.
- [x] 10. Settings switches are visible when off.
- [x] 11. One name for the fullscreen console.
- [x] 12. Server tab: neutral stopped state, consistent wording.
- [x] 13. Queue header icons readable/labelled and on-theme; source chips show they scroll.
- [x] Tests (335), release build, emulator check of each item, changelog, commit. Item 8 root cause: silent audio stored DR as Infinity (~9.2e16), which passed DR12+.

# WAV tags and NPE audit (2026-10-04)

- [x] Find why WAV embedded tags/cover art are not read. Not release-only (debug same). Genre: JThinkReader replaced the ID3 genre with an absent TXXX:GENRE. Cover: MediaMetadataRetriever ignores ID3 pictures in WAV.
- [x] Fix the demonstrated cause; verified on the emulator (debug DB: Classical/Folk; release: 4 genres, gold/green covers, Tech Info 100%, MP3s unchanged).
- [x] NPE audit: getPackage (WavTag, OkHttp) fixed earlier; JustDSD not shipped; track-getter dereferences reviewed (13, 2 guarded + 3 container titles); Kotlin !! (3, QR dialog race fixed); system services and listFiles/getParentFile chains reviewed.
- [x] Fixes committed; 328 JVM tests pass (no existing reader tests; verified on emulator instead).
- [x] Release 3.23.2 (code 144): optimized APK verified on the emulator (scan, 4 genres, WAV covers, Tech Info, no crashes). The phone was not connected, so the phone streaming/UPnP gates were not rerun; streaming code is unchanged since 3.23.1.

# Song preview / tag editor UX fixes (2026-10-04)

Crash fix committed separately (96ea1785, FFmpegKit keep rules). Remaining review items:
- [x] 1. Save reflects pending edits on both preview and editor (editor typing lights it up; dim when nothing to save).
- [x] 2. Change Cover reads as a button (add-photo icon).
- [x] 3. Selection mode shows "N Selected" (Move/Convert/Remove/Select All moved to overflow). Correction: the long-press label was already right; the review wrongly assumed listener tap mode, but this phone uses edit mode.
- [x] 4. Editor form fills the space above the keyboard (adjustResize + overlap-only IME padding) and restores after it closes.
- [x] 5. "Year" label reads "Year / Date".
- [x] 6. Bottom panel padding/margins tightened (modest). User decision (2026-10-04): keep Organize/More in the editor.
- [x] 7. Discard and Remove-art dialogs: opaque dark panel, light Cancel, red destructive button. Remaining: a brown band over the button area is drawn above the app's views (persists with the panel painted solid); it existed before.
- [x] 8. Back from the editor returns to the preview; Discard resets text edits in place (other pending drafts keep close-and-discard).
- [x] 9. Section headings use the app's gold accent.
- [x] 10. Copy Diagnostics is a quiet text button.
- [x] 11. "Reload Tags", "Save Art", "Remove Art" (red); icons dropped so labels fit.
- [x] 12. Tech Info headings: Tag Check, Loudness (ReplayGain), Embedded Cover Art, File Location, All Embedded Tags, Audio Stream (FFmpeg).
- [x] Build, 328 unit tests, phone check of each item (test edits discarded, no tag writes), changelog, commit.

# Main screen: single Play button (2026-10-03)

- [x] Replace the Play + Shuffle pills beside the search stats with one Play pill; shuffle stays in the now-playing controls.
- [x] When shuffle mode is on, start results playback at a random track (shuffle mode keeps the starting track first).
- [x] Remove the unused shuffle parameter from onPlayResults/playCurrentResults; add a view-model test (10/10 MainViewModelTest, UiLayoutPolicyTest pass).
- [x] On-device check (optimized build signed with the phone key, data kept): one Play pill shows; shuffle off starts at the first result; shuffle on gave three different random starts. Shuffle was turned back off and playback paused.
- [x] Main-screen UX fixes, tested on the phone: opaque mini player, solid scroll-to-top button, marquee only while playing, and compact badges at large text (duration inline at 1.3x). 109 app unit tests pass.
- [x] Changelog/UI.md updated and committed.
- [x] Follow-up, tested on the phone: NEW/DL badge becomes a dot on list artwork (spoken label kept), menu mark on the logo, equaliser icon for Music Center, "Days of Music" with playtime before size, and singular "1 Track"/"1 Song".
- [x] Library size removed from the main header (kept for search results); About shows "22.9 Days of Music • 286.68 GB" and includes it in the share text. Verified on the phone.

# SonicNIO throughput experiments (2026-10-03)

- [x] Capture a repeatable current-code host baseline and preserve source/evidence before tuning.
- [x] Implement configurable, contention-aware file write budgets and bounded PCM batching; test bytes, partial writes, short/failing producers and cancellation ownership.
- [x] Compare repeated identical host workloads, including seek contention; retain supported defaults and run core/UPnP checks and debug build. Existing socket coverage checks HTTP overload with active WebSocket control.
- [x] Update PERFORMANCE.md with methods, measurements, decisions and device/Netty verification limits.

Result: PCM batching enabled; shared/solo file budgets retain 256 KiB defaults because 512 KiB/1 MiB experiments did not establish benefit. 99 complete host measurements, nine matched-content phone runs, exact byte/range checks and 162 core + 43 UPnP cases pass. Synthetic 4 KiB generated-body throughput median 169.054 → 1,070.370 MiB/s (6.33×); phone WAV duration 0.852291 → 0.830846 s, with no substantial throughput gain established. Some native host medians were 2.8–5.1% lower; preserve this limitation and require controlled profiling rather than claiming a native speedup. A discovered queue-space/partial-buffer race was diagnosed and regressed. Final APK installed and its SHA-256 verified. Details and artifacts: [PERFORMANCE.md §10](../PERFORMANCE.md#10-throughput-follow-up), [205 individual test results](performance/sonicnio-throughput-2026-10-03/TEST_RESULTS.md).

# SonicNIO performance documentation (2026-10-03)

- [x] Inventory design decisions, benchmark scripts, retained device measurements and individual JUnit results; identify missing evidence and comparison limits.
- [x] Create a durable performance document with concepts, implementation approach, methodology, per-test results, before/after and historical Netty comparisons, reproducible commands and outstanding measurements.
- [x] Validate totals, calculations, source links and documentation diffs; link the report from the existing streaming summary.

Delivery: [PERFORMANCE.md](../PERFORMANCE.md), [199 individual test results](performance/sonicnio-2026-10-03/TEST_RESULTS.md), four device JSON artifacts, normalized JUnit/repeat evidence, source/evidence digests and five archived session harnesses. Verified 35 suites / 199 passing cases, 40 repeat runs / 2,160 executions, before/after percentage calculations, local links and manifest digests. Documentation-only work; benchmarks were not rerun. Netty attribution, unmatched Wi-Fi and missing raw artifacts remain explicitly qualified.

# Full-Height Music Center (Eliminate Empty Black Gap Above Sheet)

## Status: 🟢 Completed & Verified

### Objective
Expand Music Center (`AudioHubSheet.kt`) to full height (filling available vertical space ~95-100% like Spotify and Apple Music), eliminating the empty black backdrop above the previous 65% clamped sheet.

### Checklist
- [x] **Task 1: Update `AudioHubSheet.kt` to Full Height**
  - [x] Remove hardcoded `sheetHeight = screenHeightDp * 0.65f` clamping.
  - [x] Set root content Column to `Modifier.fillMaxSize()`.
  - [x] Ensure `Box` container in `AudioHubContainer` uses `fillMaxSize()` for MODAL and PREVIEW presentations.
- [x] **Task 2: Verify Layout Behavior across Tabs**
  - [x] `NowPlayingPage.kt`: Verify album art flip container and controls expand gracefully with `.weight(1f)` without clipping.
  - [x] `QueuePage.kt`: Verify queue list takes full height without clipping.
  - [x] `MediaServerPage.kt`: Verify scrollable cards display properly with expanded space.
- [x] **Task 3: Unit Tests, Screenshots & Regression Testing**
  - [x] Run `./gradlew :app:testDebugUnitTest :core:testDebugUnitTest`: All tests pass cleanly.
  - [x] Run `./gradlew assembleDebug`: Clean debug APK assembled (`app/build/outputs/apk/debug/app-debug.apk`).
  - [x] Check git diff hygiene (`git diff --check` passes with 0 warnings).

---

# Fix Music Center Play & Next Controls (Items 1 to 4)

## Status: 🟢 Completed & Verified

### Objective
Resolve the 4 identified issues in Music Center -> Playback screen and underlying playback control architecture:
1. Permit playback start from Play & Next in `NowPlayingPage.kt` via `canStartPlayback` and `UiLayoutPolicy.playControlsEnabled`.
2. Dim Next and Previous button icons when disabled (`Color(0x66FFFFFF)` vs `Color.White`).
3. Differentiate user-initiated skips from natural looping on `RepeatMode.ONE` in `QueueManager.java` and `MusicMateServiceImpl.java`.
4. Prioritize queue order (`getCurrentTrack()` / `getNextTrack()`) over random selection when starting playback from the queue in `MainActivity.java`.

### Checklist
- [x] **Task 1: Permit Playback Start in `NowPlayingPage.kt` & `UiLayoutPolicy.kt`**
  - [x] Add `playControlsEnabled` to `UiLayoutPolicy.kt` and tests in `UiLayoutPolicyTest.kt`.
  - [x] Support `canStartPlayback` parameter in `NowPlayingPage.kt` and wire it in `AudioHubSheet.kt`.
- [x] **Task 2: Correct Disabled Icon Tint for Next and Previous in `NowPlayingPage.kt`**
  - [x] Apply `if (playControlsEnabled) Color.White else Color(0x66FFFFFF)` to Next icon.
  - [x] Apply `if (controlsEnabled) Color.White else Color(0x66FFFFFF)` to Previous icon.
- [x] **Task 3: Differentiate User Skip from Natural Loop on `RepeatMode.ONE`**
  - [x] Overload `QueueManager.getNextTrack(boolean forceSkip)` to bypass `RepeatMode.ONE` on user skip.
  - [x] Call `getNextTrack(true)` for explicit skip actions in `MusicMateServiceImpl.java`, while keeping `getNextTrack(false)` for natural completion.
  - [x] Add regression tests in `QueueManagerTest.java`.
- [x] **Task 4: Preserve Queue Order on Initial Playback in `MainActivity.java`**
  - [x] In `onDockPlayPauseClicked()`, query `qm.getCurrentTrack()` or `qm.getNextTrack()` when shuffle is off, falling back to random only when shuffle is on or no track is found.
- [x] **Task 5: Verification & Polish**
  - [x] Run `:core:testDebugUnitTest` and `:app:testDebugUnitTest`: all unit tests pass cleanly.
  - [x] Check git diff hygiene: `git diff --check` passes with zero warnings.

---

# Compact Smart Queue Header & Playlist Integration

### Objective
1. **Drastically reduce vertical space footprint of Queue header:**
   - Consolidate Toolbar (40dp) and Engine HUD (32dp) into a single 34dp header bar.
   - Replace bulky 52dp 2-line cards with ultra-compact 28dp micro-capsule chips (`[🖐️ Manual]`, `[✨ New]`, `[📥 DL]`, `[🧭 Discover]`, `[⏳ Rediscover]`, `[📋 Playlist ▾]`).
   - Remove the 40dp static intelligence info box (retain only transient error micro-banners).
   - Cut total header height from ~196dp to ~60dp (~68% reduction), maximizing visible track listing inside the 65% height bottom sheet.
2. **Support Load from Playlist:**
   - **Pattern A (Action): Instant Load/Append to Queue:** Add `PlaylistPickerDialog` to directly replace ("Play All") or append ("+ Queue") all playable tracks of any playlist (Smart or Custom) into the queue.
   - **Pattern B (Smart Source): Playlist as Auto-Refill Engine:** Add `QueueManager.Source.PLAYLIST` and `activePlaylistName` to continuously auto-fill up to 20 unplayed tracks matching the selected playlist.

### Checklist
- [x] **Phase 1: Ultra-Compact Queue Header Redesign (`QueuePage.kt`)**
  - [x] Merge Top Toolbar and Engine HUD into a unified 34dp bar with title, live jewel LED dot, track count/time, inline slots badge, and compact focus/clear/playlist actions.
  - [x] Redesign `SmartSourceDeck` into sleek single-line 28dp micro-capsules without subtitles.
  - [x] Remove static `SmartSourceInfoBanner` container; keep only conditional error micro-alerts.
- [x] **Phase 2: Playlist Selection & Loading Implementation**
  - [x] Create `PlaylistPickerDialog.kt` composable to display all available playlists from `PlaylistRepository.getPlaylists()`.
  - [x] Implement instant actions: "Play All" (replaces queue) and "Add to Queue" (appends).
  - [x] Add `Source.PLAYLIST` and `activePlaylistName` to `QueueManager.java` for continuous smart auto-refill from a chosen playlist.
  - [x] Connect `QueuePage.kt` Playlist chip & toolbar icon to open picker and switch active playlist.
  - [x] Add playlist button in `QueueEmptyState`.
- [x] **Phase 3: Verification & Polish**
  - [x] Run unit tests (`:app:testDebugUnitTest`, `:core:test`): all 102 unit tests pass cleanly.
  - [x] Verify build via `./gradlew assembleDebug`: clean APK generated.
  - [x] Verified `git diff --check`: zero warnings.

---

# Smart Queue UI/UX Overhaul: Audiophile Console & Visual Discovery

## Status: 🟢 Completed & Hardened

### Objective
Elevate the Smart Queue UI/UX from a plain utility list into an immersive, audiophile-grade smart console with tactile source switching, real-time refill HUD, cover-art thumbnails, audio fidelity badges, and clear visual queue zoning (Now Playing vs. Manual Play Next vs. Smart Refill).

### Checklist
- [x] **Phase 1: Audiophile Smart Source Deck & Engine HUD (`QueuePage.kt`)**
  - Replaced generic Material dropdown menu with a horizontally scrollable obsidian segmented pill deck for the 5 sources (Manual, New, Downloads, Discover, Rediscover) with distinct accent colors and jewel borders.
  - Implemented live "Engine HUD": pulsing status LED indicator (Emerald pulse for active auto-fill, Amber for refreshing, Slate for manual), slot count badge (`SLOTS: 18/20`), and manual refill button with haptic feedback.
  - Added frosted glassmorphic intelligence banner explaining the active smart algorithm and auto-refill rules.
- [x] **Phase 2: High-Fidelity Queue Items with Cover Art & Codec Badges**
  - Added 42dp cover-art squircle thumbnails with Coil `AsyncImage` and dark background fallback.
  - Added animated vector equalizer overlay and gold border/title accent for the active playing track.
  - Integrated compact audiophile quality badge (`QualityBadge`) for Hi-Res, DSD, 24-bit, and CD quality tracks.
  - Added subtle track provenance micro-badge (`NOW PLAYING`, `PLAY NEXT`, `SMART`) to distinguish user choices from smart auto-fill.
- [x] **Phase 3: Visual Queue Zoning & Rich Empty State**
  - Grouped and sectioned upcoming tracks visually with categorized dividers (`NOW PLAYING`, `UP NEXT · MANUAL PRIORITY`, `✦ SMART REFILL · [SOURCE]`).
  - Designed an audiophile empty-state graphic with quick-start action pills (`[✨ Try Discoveries]`, `[Browse Library]`).
  - Polished swipe-to-dismiss gesture with crimson gradient and haptic resistance.
- [x] **Phase 4: Verification, Lint & Build**
  - 117 unit tests passed across `:app`, `:core`, and `:server` modules; 3 Python SQLite migration tests passed.
  - Assembled clean debug APK (`app-debug.apk`).
  - Updated `UI.md`, `CHANGELOG.md`, and recorded UI lessons in `tasks/lessons.md`.

### Verification & Results
- **Unit Tests:** 117 tests pass (50 core, 51 app, 16 server), plus 3 Python SQLite tests.
- **Build:** Clean debug APK assembled (`app/build/outputs/apk/debug/app-debug.apk`).
- **Hygiene:** `git diff --check` passes with zero warnings.
- **Device Limitations:** Physical device display and touch interaction remain pending runtime verification.

---

# Listening History and Discovery Sources

## Status: 🟢 Implemented & Verified (Device Review Pending)

- [x] Add durable history storage separate from queue state, with a non-destructive database migration (`listening_history` table, `MIGRATION_1_2`, RoomDbHelper, tested with SQLite script).
- [x] Implement playback accounting with regression tests for pauses, seeks, duplicate events, same-track handoffs/repeats, completion and skips (`ListeningHistoryTracker`, 8 core unit tests).
- [x] Wire service playback events and add Unplayed/Rediscover sources using the approved 90%/30-day defaults (`MusicMateServiceImpl`, `QueueManager`, `QueuePage.kt`).
- [x] Run core/app tests and debug build, update documentation, and record device verification limitations (117 unit tests pass across all modules, debug APK built, `USER_GUIDE.md` and `CHANGELOG.md` updated).

### Verification & Results
- **Unit Tests:** 117 tests pass (50 core, 51 app, 16 server), plus 3 Python SQLite migration/query tests.
- **Build:** `./gradlew assembleDebug` succeeds cleanly (`app/build/outputs/apk/debug/app-debug.apk`).
- **Device Limitations:** Physical device playback telemetry, UI interaction, and audio transitions remain unverified in runtime environment due to device access constraints.

---

# Smart Queue Implementation

- [x] Implement persisted Manual/New/Downloads source state and bounded refill with session exclusions and manual priority.
- [x] Integrate background refresh and shared source selector in queue UI, with accurate category descriptions and empty states.
- [x] Add six queue regressions for stable next tracks/manual priority, removal suppression after restart, anchor restoration/manual freeze, bounded refill/new candidates, clear-during-query races, and Downloads/missing files. All 40 core and 51 app tests pass; debug APK built; diff check clean.
- Detailed approved definitions: `tasks/smart-queue-plan.md`.
- Device playback, source-menu rendering, and process-restart journeys remain unverified because device-tool instruction access was blocked earlier in this session.

---

# Preview Simplification and Save Feedback

- [x] Quiet genre, cover controls, metadata borders, and utility colors; center button content and reserve indicator space while keeping five actions.
- [x] Connect saving/success/failure feedback to both save paths and clear delayed feedback on destruction. Source review complete; device interactions remain unverified.
- [x] Build debug APK, run app tests, inspect resources/diff, and document visual verification limits. Debug assembly succeeds; 51 app tests pass; diff clean. XML checks confirm all five centered actions. Updated UI/changelog. New rendering, large-font behavior, and interactive save outcomes still require device verification; device-tool instruction access remains blocked in this session.

---

# Studio Command Dock — Implemented; Device Review Pending

## Objective
- Redesign the five tag-preview actions into one console-like control surface without removing any action.

## Checklist
- [x] Charcoal dock surface with 24dp top corners, hairline top border, and 16dp side padding.
- [x] Evenly spaced utility row with vector icons, 48dp targets, hairline separators, and neutral styling; removed Organize's filled pill.
- [x] 65/35 split between graphite Edit Song Info and Save, both 52dp with 14dp corners.
- [x] Save indicator covering clean, unsaved, saving, and saved states with accessibility state descriptions.
- [x] Brief press compression plus existing haptics; long-press shortcuts preserved.
- [x] Build, app tests, and diff checks pass; all five action IDs retained.

## Verification limit
- Device visual and interactive review remains pending; device-tool instruction access was denied earlier in this session.

---

# Preview Screen Visual Fixes

## Objective
- Resolve screenshot review findings while preserving all five bottom actions and existing editing work.

## Plan and verification
- [x] Replace fixed preview header sizing with scrollable content bounded above the measured action dock; match and center artwork controls. Java/resource compilation passed.
- [x] Align labeled metadata rows, clarify track counts, unify vector icons/accent colors, and improve badge contrast and wrapping. Debug assembly and app unit tests passed.
- [x] Build debug APK, run app unit tests, inspect diff, and verify rendering on an available device if access permits. Debug assembly and 51 app tests pass; `git diff --check` passes. XML comparison confirms all five bottom action definitions are unchanged. Device rendering remains unverified because access to the required device-interaction reference was denied.

## Result
- Preview scrolls independently above the dock; editor return-to-preview requires an actual expanding gesture. Removed the obsolete pager dock margin to avoid double-reserving footer space.
- Matching 48dp artwork controls, labeled metadata rows, localized track counts, consistent navigation icons/colors, wrapping badges, and brighter preview DR colors implemented.
- Updated UI documentation and Unreleased notes. APK: `app/build/outputs/apk/debug/app-debug.apk`.

---

# Statusline Overhaul: Single-Line HUD with Remaining Quota

## Status: 🟢 Completed & Hardened

### Objectives
1. **Compact Single-Line Statusline:**
   - Migrate quota display from a second line to the primary line directly following `Google AI Pro` (plan tier) separated by `│`, eliminating vertical clutter and the newline.
2. **Quota Metrics Format Update:**
   - Switch from used percentages to remaining percentages (`% left`).
   - Format each quota section as `<reset_time> <percentage>% left` (e.g., `4h31m 100% left` and `6d4h 93% left`).
   - Remove wide horizontal progress bars to keep single-line width within standard terminal boundaries (~100 chars).
3. **Color Inversion for Remaining Quota:**
   - Implement `color_for_remaining_pct()` so high quota (>60%) displays green, moderate (36-60%) yellow, low (16-35%) orange, and critical (<=15%) red.
   - Retain `color_for_pct()` for used metrics like context window usage (`ctx_pct`).
4. **Performance & Reliability Hardening:**
   - Single-invocation `jq` execution for all JSON data extraction and arithmetic.
   - Full ISO 8601 timezone offset parsing (`+07:00`, `-05:00`, `.123Z`, raw epoch).
   - Stale/elapsed countdown handling (suppresses stale negative timers when quota already reset).
   - Strict zero-stderr failsafe exit on invalid/empty JSON inputs.

---

### Master Checklist

- [x] **Phase 1: Statusline Script Refactor (`~/.gemini/statusline.sh`)**
  - [x] Backup current script to `~/.gemini/statusline.sh.bak3`
  - [x] Update jq extraction to calculate remaining percentages (`% left`) from `remaining_fraction`, `remaining_percentage`, or `used_percentage`
  - [x] Add `color_for_remaining_pct()` function with appropriate color bands
  - [x] Format 5h quota as `${c_muted}${q5h_rel}${reset} ${q5h_color}${q5h_pct}% left${reset}`
  - [x] Format weekly quota as `${c_muted}${qwk_rel}${reset} ${qwk_color}${qwk_pct}% left${reset}`
  - [x] Append quotas directly to `line1` after `plan_tier` separated by `${sep}` without newline
  - [x] Remove `line2` multiline output

- [x] **Phase 2: Comprehensive Test Suite & Verification**
  - [x] Test with user image scenario: Gemini 3.8 Flash, 22% context, `musicmate (master*)`, `Google AI Pro`, 5h quota (4h31m, 100% left), 7d quota (6d4h, 93% left)
  - [x] Test with Claude Code rate_limits payload
  - [x] Test with edge cases (empty JSON, missing quota, detached HEAD, missing plan tier)
  - [x] Verify ANSI 24-bit TrueColor sequences across remaining quota gradient (100% -> green, 70% -> green, 50% -> yellow, 25% -> orange, 5% -> red)

- [x] **Phase 3: Stability Hardening & Edge-Case Audit**
  - [x] RFC3339 / ISO 8601 parser in `jq` supporting UTC `Z`, subseconds, and timezone offsets (`+07:00`, `-05:00`)
  - [x] Prevent stale countdowns: when reset timestamp is in the past (>60s elapsed), suppress countdown and show pure `% left`
  - [x] Remove newline from empty/corrupt fallback (`printf "agy"`) to ensure clean TUI rendering

---

## Review & Verification

### Verification Summary
1. **Single-Line Rendering Verification:**
   - Contiguous single-line HUD:
     `Gemini 3.8 Flash (High) │ 󰍛 22% │ musicmate (master*) │ Google AI Pro │ 4h31m 100% left │ 6d4h 93% left`
   - Verified no extra newlines or trailing newline breaks in terminal TUIs.
2. **Quota Calculations & Formatting:**
   - Evaluated `remaining_fraction` (1.0 -> `100% left`, 0.93 -> `93% left`).
   - Verified fallback for Claude Code `used_percentage` (`100 - used_percentage`).
   - Relative reset times (`calc_relative_time`) accurately format hours/minutes (`4h31m`) and days/hours (`6d4h`).
3. **Adaptive Color Grading:**
   - Tested 5 quota levels: 100% (green), 70% (green), 50% (yellow), 25% (orange), 5% (red).
   - Time string is styled in `c_muted` matching the Tokyo Night theme hierarchy.
4. **Resilience & Fallbacks:**
   - Tested 5 corrupt/null input variations: zero stderr bytes, clean exits with code 0.
   - Tested non-git directories and detached HEAD states.

---

# Tag Activity Preview Refinement & Dialog Lifecycle Hardening

## Status: 🟢 Completed & Verified

### Objectives
1. **Tag Preview Screen UX:**
   - Eliminate duplicated artist and album overlay text (`panel_artist`) on cover art scrim.
   - Retain prominent song title overlay while delegating discography navigation to interactive Compose [`StudioProvenanceSection`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/ui/compose/AudioBadges.kt).
2. **Compose Dialog Lifecycle Resolution:**
   - Fix fatal `IllegalStateException: ViewTreeLifecycleOwner not found from androidx.compose.ui.platform.ComposeView` when launching Search & Match dialogs.
   - Standardize on `ComponentDialog` with explicit ViewTree owner bindings.
3. **Menu & Performance Hygiene:**
   - Purge obsolete commented-out actions in `tag_more_actions_menu.xml` and `TagsActivity.java`.
   - Add in-memory `LruCache` for third-party music app icons in `PlayerPickerDialog.kt`.

### Checklist
- [x] Remove `panel_artist` from `activity_tags.xml` and `TagsActivity.java`
- [x] Migrate `showSearchQueryDialog` and `showSearchResultsDialog` to `ComponentDialog`
- [x] Bind `ViewTreeLifecycleOwner`, `ViewTreeSavedStateRegistryOwner`, `ViewTreeViewModelStoreOwner`
- [x] Clean up `tag_more_actions_menu.xml` and `TagsActivity.doShowMoreActions()`
- [x] Implement `appIconCache` in `PlayerPickerDialog.kt`
- [x] Record pattern in `tasks/lessons.md`
- [x] Update `UI.md` and `CHANGELOG.md`
- [x] Run unit tests and deploy debug build to physical device (`<device-serial>`)

---

# Whole-App Functional and UI/UX Audit

## Objective
- Review the Android app end to end for reproducible functional defects and user-facing usability/accessibility issues without changing application code or overwriting existing work.

## Checklist
- [x] Map app entry points, primary flows, test coverage, and available verification environment.
- [x] Inspect representative user flows and UI resources for concrete issues; distinguish confirmed defects from hypotheses.
- [x] Run feasible build/tests; device UI testing was not performed because the connected device was in another app and the required interaction reference was inaccessible.
- [x] Report prioritized findings with file/line evidence, impact, reproduction steps, and verification limitations.

---

# Fix Whole-App Audit Findings

## Objective
- Fix all seven findings from the whole-app audit while preserving pre-existing edits in TagsActivity.java, AudioBadges.kt and activity_tags.xml.

## Ordered implementation and verification
- [x] Recheck affected code and existing tests; establish baseline of pre-existing changes.
- [x] Fix lost Search & Match and auto-tag draft warning; focused build/tests pass (activity flow needs device verification).
- [x] Fix non-paginated folders/playlists (and other full-list queries) duplicating on scroll; pagination regression test and focused build pass.
- [x] Fix empty-playlist creation affordance and stale navigation filters; build/tests pass, runtime UI check pending.
- [x] Wire empty queue Browse Library action through both hosts; build/tests pass, runtime UI check pending.
- [x] Add semantic activation to mini-player and Now Playing flip, and adjustable/keyboard controls to Studio Console seek/volume; build/tests pass, accessibility device check pending.
- [x] Persist the user's media-server stopped intent across activity recreation and cancel pending starts; app tests and debug build pass.
- [x] Run full core/app unit tests and debug build; review diff and report that device/visual verification was not performed and app lint still has 22 unrelated pre-existing errors.

---

# Document, Version, and Commit Audit Fixes

## Objective
- Update user/developer documentation and release notes to reflect the completed fixes and existing preview edits, bump the Android patch version, then commit the complete working tree requested by the user.

## Checklist
- [x] Reconcile current documentation and release conventions with the complete diff, including earlier uncommitted preview work.
- [x] Update relevant user/UI documentation and changelog with accurate behavior and verification caveats.
- [x] Bump patch version and versionCode; update stale displayed version in the drawer.
- [x] Verify Gradle tests/build, documentation consistency, staged scope, and diff hygiene (lint has known unrelated failures; device UI not run).
- [x] Commit all intended source/documentation changes; confirm clean working tree and report commit ID.

---

# Usage and Functional UX Review

## Objective
- Review current primary user journeys for usability and functional gaps, rechecking the latest implementation rather than repeating resolved audit findings.

## Checklist
- [x] Inspect navigation, discovery, playback, queue, and library-editing flows with source evidence.
- [x] Verify high-impact findings against call sites; source review only, no builds, tests, or device interaction performed.
- [x] Deliver prioritized findings with user impact and actionable recommendations.

## Review summary
- Found playback handoff ordering, immediate artwork writes despite Discard, loaded-page queue scope, search state divergence, and folder draft persistence defects.
- Identified ineffective empty-library recovery and queue-clear recovery friction.
- Rechecked current implementation rather than repeating previously resolved audit findings. Runtime playback and visual/accessibility checks remain unverified.

---

# Improve Usage and Functional UX

## Objective
- Resolve the seven reviewed issues with predictable playback, recoverable editing, and actionable library states.

## Implementation and verification
- [x] Correct playback target handoff ordering; app unit tests/compilation pass (physical handoff still needs device verification).
- [x] Stage selected artwork until Save, preserve originals on failure, and retain drafts for retry; four file-persistence tests and TagsViewModel tests pass.
- [x] Resolve the full active query for Quick Play; 1,003-track regression fails before fix and passes after fix, with MainViewModel suite passing.
- [x] Synchronize visible search with navigation and Back behavior; app compilation/tests pass.
- [x] Keep folder additions/removals in one draft until scan confirmation; app compilation/tests pass.
- [x] Offer context-specific empty-library, no-results, and error recovery actions; test distinguishes no matches from no indexed music.
- [x] Make queue clearing explicitly confirmed in the shared QueuePage used by both hosts.
- [x] Run app/core tests and debug build, check lint/diff, and document verification limits.

## Verification refinements
- Verify artwork also resolves after Save (including previously cached artwork); preserve the selected file path in saved metadata.
- Bind the folder dialog to a lifecycle-aware host so the new discovery action can safely display Compose content.
- Device interaction references are permission-blocked; device/visual journeys cannot be verified in this session.

## Results
- 51 app and 34 core tests pass (85 total), including eight new regression cases covering artwork persistence/retry, complete-result playback, failed playback lookup, and empty-library classification.
- Debug APK assembled at `app/build/outputs/apk/debug/app-debug.apk`.
- Lint reports 22 pre-existing errors in unchanged service/audio declarations (MissingSuperCall and Media3 opt-in), plus warnings/hints; no lint errors in added behavior.
- `git diff --check` passes. Updated `UI.md` and Unreleased changelog to document the new interactions.
- Device playback handoff, dialog rendering, and touch/accessibility journeys remain unverified.

---

# App functionality and interface review (2026-09-30)

- [x] Inspect current functionality, UI flows, pending changes, and differences against main.
- [x] Run relevant automated checks and inspect available device/runtime evidence.
- [x] Record confirmed defects, usability findings, and verification limits in tasks/app-review-2026-09-30.md.

Evidence: 142 unit tests and debug assembly pass. Screenshot comparisons fail for 21/26 previews; lint reports 33 errors. Five fresh previews inspected visually. No connected device; live interaction checks remain outstanding in the report.

# SonicNIO metadata optimization and measurements (2026-10-03)

- [x] Preserve current FileResponse/runtime/APK baseline and add reproducible metadata and request benchmarks.
- [x] Replace unnecessary ETag hexadecimal formatting and per-response date formatter construction; verify byte identity, concurrency and existing HTTP behavior.
- [x] Run interleaved before/after host measurements and available phone checks; record all samples, allocation, throughput and latency without implying a Netty comparison.
- [x] Run core/UPnP tests and debug build, inspect the incremental diff and publish results in PERFORMANCE.md with evidence links.

Scope: implement the research report's first metadata optimization. Header-token caching, parser changes and buffer pooling remain separate experiments so measurements can be attributed to this change.

Result: [PERFORMANCE.md §11](../PERFORMANCE.md#11-metadata-encoding-follow-up), [complete report/evidence](performance/sonicnio-metadata-2026-10-03/REPORT.md). ETag/date allocation medians fall 94.3%/86.8%; 304/206 preparation times fall 13.1%/5.5%. Six interleaved phone samples show seek median 16.282 → 7.804 ms with essentially unchanged 39.43 MiB/s four-stream throughput. Host bulk throughput is unchanged. All 207 core/UPnP cases and debug build pass; bytes, metadata and installed APK identity verified. Optimized APK remains installed. Small-sample latency gains and absent Netty comparison remain explicit.

# Dock output label mismatch (2026-10-05)

- [x] Trace dock and Music Center output labels. Dock used the generic player label; Music Center resolved the Android output device.
- [x] Apply the smallest fix using the existing audio output model. Both local labels now use the same resolved device name; no new API or abstraction.
- [x] Run app unit tests and debug build, inspect the diff against main, and check whitespace.
- [x] Verify the fixed APK on the emulator: uninstalled old signature build, installed debug build, verified connected Android test `UsbBitPerfectSettingsTest` (100% pass), and confirmed matching resolved device label ("Phone Speaker") across both the player dock and Music Center. (Physical USB hardware validation still deferred to physical device).

USB hardware validation requires the physical phone; verified on emulator.

# Native USB bit-perfect playback (2026-10-05)

- [x] Ground playback, mixer APIs, settings and badge behavior; compare lifecycle designs.
- [x] Implement an opt-in USB mode with exact decoded-format matching, unity gain and safe fallback.
- [x] Replace capability-based badge with playback mode status; document the setting.
- [x] Run regression tests, debug build and available runtime checks; record physical USB verification limits.

Throughput checkpoint:
- Blocking first steps: verify Media3 1.11.1 sink extension points and Android mixer behavior before implementation.
- Independent workstreams: research lifecycle and settings/UI contracts read-only in parallel.
- Shared mutable state: one playback owner controls mixer preference and status; workers own disjoint files.
- Smallest safe decomposition: one worker implements the coupled sink/controller lifecycle; settings and integration follow its status contract.

# Sleep timer fixes (2026-10-15)
User report: "the sleep timer function does not work properly" (no symptom details, no phone attached).
- [x] Dialog: highlight the armed option from the service's armed minutes, not the countdown text ("29m" never matched "30m"); show the remaining time in the header. Pure matcher with a unit test.
- [x] Service: a generation counter so a fade that is already running cannot clear a timer set during it, and "Turn Off" stops a running fade.
- [x] Service: fade only a renderer MusicMate drives; a monitored stream cannot be paused, so it is left alone.
- [x] Service: `sleepTimerEndTimeMs` volatile.
- [x] Unit tests + `:app:assembleDebug` (JAVA_HOME temurin 21).
- [x] Service: a failed volume read/write no longer skips the pause.
- [x] User: HiBy R3 over DLNA. No fade for HIBY/SHANLING/FIIO (`DeviceProfile.canFadeVolume`): the fade's GetVolume + 3 SetVolume burst lands right before Pause on a stack documented to drop actions under SetVolume bursts (docs/technical/UI.md).
- [x] Resolved by the fade skip (sleep timer works on the R3 in 3.28.1). Watch item only: `playerPause` sets `isUserInitiatedStop` before Pause succeeds, so a dropped Pause leaves MusicMate thinking it is paused and it won't advance. A Stop fallback must call `mediaHub.playerStop` directly, not `internalStopOnDMRPlayer` (it clears the current track).
- [x] Phone check: user confirmed the sleep timer works with the HiBy R3 on 3.28.1 (2026-10-15); no Pause/SetVolume warnings from MusicMate in the log.
- [x] Ask the user which output was used: HiBy R3 over DLNA.

# Studio Console overflow at large text (2026-10-15)
At fontScale 2 (900x480 preview) the right column's six rows overflow and the transport row and volume fader are clipped (present at HEAD before 3.28.1).
- [x] Title and spec chips in one block with `weight(1f, fill = false)` + `verticalScroll`, so they give way and the header, seek bar, transport and fader keep their size.
- [x] Render both full-screen previews (normal and large text) and compare with the earlier renders.
- [x] Unit tests + debug build.
- [x] dB readout: one line (`softWrap = false`, `widthIn(min = 34.dp)`); it wrapped to two lines at large text.

# Improve built-in playlist rules (2026-10-15)

- [x] Align smart playlist filters and descriptions with what the matcher can actually prove.
- [x] Tighten genre playlist rules around the stated listening moods and remove the duplicate title rule.
- [x] Validate JSON, preset vocabulary, matching examples, and the final diff against main.

# Audit CD wording on Android screens and Web UI (2026-10-15)

- [x] Find rendered CD labels and trace their data sources on Android and Web UI.
- [x] Change broad quality labels to Lossless while keeping genuinely CD-specific descriptions.
- [x] Verify affected UI paths, run focused tests or build checks, and inspect the final diff.

# Audit lossy wording on Android and Web UI (2026-10-15)

- [x] Trace the stored lossy code to Android and Web UI labels.
- [x] Replace ambiguous user-facing wording where the app means lossy audio.
- [x] Verify rendered labels, focused tests, build, and final diff.

# Make music list header stats consistent (2026-10-15)

- [x] Trace header summaries for library, search, and filtered song lists.
- [x] Use one compact song-list summary without intermittent file size.
- [x] Verify affected cases, build/tests, and final diff. Emulator showed count and duration in library and search; debug tests and build passed; signed release installed on phone; scoped diff check passed.

# Release 3.28.3 to main (2026-10-15)

- [x] Audit the uncommitted changes, release conventions, docs, and remote branch state.
- [x] Update user documentation, changelog, and Android version for the verified changes.
- [x] Run focused tests and release build, inspect the staged diff and signed APK. Web UI tests, playlist JSON validation, Android unit tests, release build, APK signature, and version metadata passed.
- [x] Commit the release changes, update `main`, tag `v3.28.3`, and confirm the GitHub release asset. Workflow succeeded; downloaded APK is signed by the MusicMate key and reports version code 154.

# Fix reviewed UI issues (2026-10-18)

- [x] Persist Play Next provenance with the queue, including reload and removal cases. A new behavior test failed before the fix and passed afterward.
- [x] Keep the collapsed editor title in sync when the displayed track changes; an emulator tag save updated the collapsed title immediately.
- [x] Remove the obstructing Go to Top overlay; drag the fast scrollbar in both directions on the emulator.
- [x] Run focused tests, build the GitHub variant, inspect the final diff, and verify the UI on an emulator without changing the phone's playback or library. App and core unit tests: 406 passed, 0 failures; GitHub debug APK built and installed on the emulator. The Play Next badge appeared before restart, and its saved state survived force-stop and relaunch.

# Commit UI review fixes and documentation (2026-10-18)

- [x] Update the relevant release and UI documentation for the three verified fixes.
- [x] Re-run focused verification and check the staged diff against `main`. App and core unit tests: 406 passed, 0 failures; GitHub debug APK built; staged diff has no whitespace errors.
- [x] Commit only the fix and documentation files; leave unrelated working-tree changes untouched.

# Install reviewed UI fixes on phone (2026-10-18)

- [x] Build a signed GitHub release APK from commit `083024d9` and verify its package, permissions, and signing certificate against the installed phone APK. Package `apincer.android.mmate`, version `3.28.3-261018` (code 154), `MANAGE_EXTERNAL_STORAGE`, matching signer SHA-256 `b5757a1e…1d29ee`.
- [x] Install the APK as an update on `192.168.1.54:42841` without clearing app data. `adb install -r` succeeded; first install time stayed 2026-10-04 21:24:56.
- [x] Confirm the installed APK matches the build and the app launches with its library intact. Installed APK SHA-256 matches the build; version is `3.28.3-261018`; all-files access remains allowed; the library shows 8,094 tracks and playback is active to HiBy R3.

# Fix first-connect HiBy R3 volume display (2026-10-18)

- [x] Trace the initial DLNA volume request, response, and UI state on the phone and in code without changing playback volume. Phone log: `HiBy R3: 100 in [0, 100] = 100%`; the app displays exactly that UPnP response. The R3's physical output-port volume is a separate value not exposed by this response.
- [x] Fix the first incorrect state at its source and cover the observable first-connect behavior with a focused test. Exact HiBy R3 targets no longer read or set DLNA volume; other renderers retain remote volume control. Both player screens direct users to the R3 itself.
- [x] Build and test, then verify the live phone behavior before committing or installing an update. 408 app/core tests passed, signed GitHub 3.28.4 APK built and installed in place; installed APK hash matches the build, all-files access remains allowed, and both phone player screens show “Set volume on HiBy R3” with no 100% slider.

# Restore safe write-only HiBy R3 volume control (2026-10-18)

- [x] Separate readable volume from writable volume and define the unknown-first-connection state. HiBy R3 remains unreadable; the first write is capped at 5% and waits for the renderer's SetVolume acknowledgment.
- [x] Add an explicit low starting action in both player screens; enable normal controls only after the R3 acknowledges that value.
- [x] Add focused tests, run app/core tests and APK build, verify the UI and device behavior. 409 unit tests passed, signed GitHub release built. On the phone, HiBy showed an unknown level on first connection; an explicit 5% command was acknowledged and Music Center showed “Set 5%” with the slider. Studio Console showed the same sent level. Physical loudness cannot be measured through DLNA.
- [x] Update release/user docs, commit the fix, and install the verified update on the phone. Installed GitHub 3.28.5 (code 156) in place; signer and all-files permission verified, first install time retained.

# MusicMate product strategy (2026-10-19)

Priority confirmed by the owner: reliable core and repeat usage. Capacity: one developer. Budget, usage baseline and revenue targets are unspecified.

- [x] Ground current positioning, capabilities and constraints in repository evidence; check current competitor positioning using primary sources. Read the README, user guide, compatibility register, current release notes, Play listing and recorded owner decisions. Checked publisher pages for BubbleUPnP, USB Audio Player PRO, Poweramp and Symfonium.
- [x] Draft all nine Product Strategy Canvas sections with explicit trade-offs, metric definitions and realistic capacity allocation. Written in `docs/PRODUCT_STRATEGY.md`, preserving the confirmed open-source and Pro decisions.
- [x] Add a 90-day sequence, critical hypotheses and inexpensive experiments; validate coherence and clearly label assumptions. Proposed a voluntary ten-person cohort, explicit retention denominators, support-time constraints and a narrow tested-device promise.
- [x] Review the written artifact against the confirmed priority and provide the recommendation with a link to the strategy. All nine sections present; local document targets checked. Targets are proposed learning gates, not measured results.

# Playback UX fixes (2026-10-25, from the playback review)

throughput checkpoint: blocking first step is none; one owner, because the three fixes share `MainActivity.java`; no shared state beyond that file; smallest decomposition is one commit per fix.

- [x] Mini player: offer Previous and Show playing song as TalkBack actions on the artwork and title, give the Play box a button role, and use string resources for its labels. `MiniPlayerAccessibilityTest` checks the actions run and Play has the button role.
- [x] Play before the player service connects: hold the request and run it once the service binds and the saved queue is restored, instead of the "No active player" toast. Song tap, header Play and artist Play reach PLAYING on the emulator; the early-tap branch itself was not observed because the list draws after the bind.
- [x] Playback errors: show them in a snackbar above the mini player with a Retry action, and move the messages to `strings.xml`. Success notices use the same snackbar. A unit test checks Retry plays the tapped track; the "Playing 4 tracks" snackbar sits above the mini player on the emulator.
- [x] Verify: app unit tests pass for both flavors (225 in github, 0 failures), both debug APKs build, and 2 of 2 instrumented tests pass on Medium_Phone (API 36).

# Artist and folder play order (2026-10-25)
- [x] Order findByArtist by album, then track number, so "Play" on an artist starts with track 01 of each album.
- [x] Order findInPath by path, then track number for CUE tracks that share one file.
- [x] Add a db-room instrumented test that inserts tracks out of order and checks both queries. Done: TrackOrderTest passes 2/2 and fails 2/2 against the old queries.
- [x] Order findByAlbumAndAlbumArtist by numeric track number, so track 10 comes after track 2. Done: TrackOrderTest passes 3/3, and the album case fails on the old text sort.
- [x] Verify on the emulator that "Play Aurora Lane" starts with "Neon Avenue". Done: media session showed Neon Avenue, and the artist list reads 01 to 04.

# DSP preset cleanup (2026-10-26)
- [x] Update presets to Reference, Bass, Vocal, Lively, Relax, Night, Custom; migrate saved Drive/Sleep/Focus choices without losing calibration or trims. Added failing tests first, then verified migration with and without a profile.
- [x] Inspect existing ReplayGain integration and retain independent loudness control across presets. Already applied at track start/transitions; no preset-dependent changes. Current volume scalar cannot boost quiet tracks above unity.
- [x] Verify focused DSP tests (41), ReplayGain tests (4), full app unit suite (231), debug/release builds and rendered seven-preset picker on emulator and signed phone release (scrolled to verify all modes). Signed release certificate matches phone; installed with data retained, Vocal/Tuned and timer off retained. Existing playback/icon edits preserved; no car listening validation claimed.

# Calibration and headphone review fixes (2026-10-28)
- [x] Add failing JVM tests: a recorder failure during a take, a failed save and a failed check sweep must not crash the wizard; saving a DIY profile turns EQ on for Bluetooth and wired outputs but not USB.
- [x] Wizard: catch capture and save failures, show a retry message, and reach Done when the check sweep fails.
- [x] SweepRecorder: release the recorder when the track cannot be built, and stop when audio focus is refused.
- [x] Show capture and route problems with their own advice instead of the volume hint.
- [x] Auto-enable DIY EQ on save for Bluetooth and wired outputs; mention wired headphones in the copy.
- [x] Run the JVM and affected emulator tests and a debug build, then review the diff.
- Validation (after review fixes: fit guarded, take attempt narrowed to capture, save notice moved beside the pinned button): 297 JVM tests pass for github and play; on the API 36 emulator CalibrationNoiseUiTest (3), HeadphoneEqUiTest (4), ManualHeadphoneUiTest (5) and ManualHeadphoneFlowTest (1) pass; assembleGithubDebug passes.

# Verify DSP sound quality (2026-10-29)

- [x] Audit PCM/output routing and inspect available connected-device evidence: HiBy R3 LDAC, active 44.1 kHz track into a 96 kHz A2DP mixer, zero track underruns in the snapshot.
- [x] Stress-test EQ headroom and limiter with reproducible signals; fix demonstrated defects with regression coverage. Full-band response estimate corrects missing shelf headroom; 31 focused checks passed.
- [x] Run full GitHub debug unit suite and assembleGithubDebug, review scoped changes against main, and document measured results and hardware limits. Build passed; no installation or listening comparison performed. True-peak protection remains unproven.

# Fair listening calibration comparisons (2026-10-29)

- [x] Add regression coverage for matched Original/Adjusted levels without undoing channel balance or exceeding the quiet preview ceiling.
- [x] Implement attenuation-only stereo RMS comparison matching; retain diagnostic tone gain cues. Explain calibration-only previews, active playback modes, and Custom trim reset without changing saved modes.
- [x] Verify unit/UI behavior, build, and document the result. All 368 unit tests passed; focused listening guide emulator tests and assembleGithubDebug passed. Matching estimates average signal level, not perceived loudness. No hardware listening comparison performed.

# Document and install listening comparison update (2026-10-29)

- [x] Update user-facing documentation and changelog for DSP headroom and fair listening comparisons.
- [x] Verify documentation and install the tested GitHub debug APK on the connected Galaxy S25, retaining data. ADB install -r returned Success; build validation remains 368 unit tests and 11 emulator UI tests passed.

# Show library status in Now Playing (2026-10-29)

- [x] Trace Preview status rules: NEW/DL describe unorganized files; managed tracks show Organized, without claiming duplicate detection.
- [x] Reuse NEW/DL beside the quality badge and add a descriptive Organized badge. Two emulator checks pass, including track changes and idle state at 320dp width and 150% text.
- [x] Update docs/changelog, assembleGithubDebug, inspect text visibility/accessibility and check scoped diffs against main. Build and whitespace checks pass; no phone installation performed.

# Microphone measurement reliability (2026-11-03)
- [x] Inspect capture and rejection logic; reproduce false rejection with realistic signals.
- [x] Fix demonstrated cause while keeping measurement quality limits.
- [x] Run calibration tests, build, and inspect scoped diff; report hardware limitations.
- Evidence: calibration JVM suite and assembleGithubDebug pass; clean 0.7 s echo regression failed before the fix and passes after it. No physical microphone trial performed. Scoped diff reviewed against HEAD and main.

## Remove automatic sound mode (2026-11-06)
- [x] Remove metadata-driven sound resolution and playback hooks; retain saved manual output choices.
- [x] Replace Auto/Manual UI with direct sound choices and remove rule settings; update affected tests.
- [x] Update tuning documentation and changelog; build, run focused tests, verify runtime and diff.
Scope: remove Auto completely; retain existing manual presets and calibration. Album-aware volume leveling is separate future work.

Removal validation: full GitHub debug unit suite passes (373 tests); app and instrumentation APK builds pass. API 34 emulator passes 23 UI checks across ManualSoundUiTest, QuickTuningUiTest, HeadphoneEqUiTest, PlaybackTuningUiTest and SoundSignatureUiTest. git diff --check and git diff master --check pass. No AutoSound/AutoTuningSoundModes/auto_sound_mode references remain in app sources. Emulator always_finish_activities restored to 1. Evidence: /tmp/musicmate-remove-auto-build.log and /tmp/musicmate-remove-auto-ui.log.
Device update: adb install -r succeeded on 192.168.1.51:41677; app data preserved.

## Simple sound presets and Custom feedback (2026-11-06)
- [x] Keep Neutral/Warm/Vocal/Lively/Custom; migrate saved Bass/Night choices safely.
- [x] Persist Night listening separately per output, applying compression without tonal filters.
- [x] Add live Custom signature text, immediate sliders, Reset and temporary comparison that retains correction.
- [x] Update meaningful backend/UI tests and docs; build, run tests, verify runtime and install the verified update.
Design: retain stored RELAX name with Warm label. Legacy Bass becomes equivalent Custom trims where practical; legacy Night becomes Neutral plus Night listening. Compare is transient, never saved, and ends on dismissal/output change. Preserve USB consent and correction.

Validation: 380 GitHub debug unit tests pass; app and instrumentation APKs build. All 26 affected emulator UI checks pass across six classes (QuickTuningUiTest rerun: 8/8 after replacing an ambiguous generic switch selector). Runtime coverage includes live drag preview before release, signature text, Reset/Compare cleanup, independent Night and large-text cards. Whitespace checks against working tree and master pass. Emulator always_finish_activities restored to 1. Evidence: /tmp/musicmate-simple-sound-build.log, /tmp/musicmate-simple-sound-ui.log and /tmp/musicmate-simple-sound-quick-ui.log. No physical listening comparison performed.
Device update: adb install -r succeeded on 192.168.1.51:41677; existing app data preserved.

## Clarify playback tuning badge (2026-11-06)
- [x] Show On/Bypassed status with preset and an active-only Night moon indicator.
- [x] Explain processing and saved Night state in accessibility text; document badge semantics.
- [x] Build, verify focused badge behavior and install the updated APK.

Validation: app/test APK builds pass; QuickTuningUiTest passes 9 tests including Night indicators across all five presets, bypass/unavailable/off behavior and accessibility wording. git diff --check and git diff master --check pass. Emulator always_finish_activities restored to 1. Logs: /tmp/musicmate-night-badge-build.log and /tmp/musicmate-night-badge-ui.log.
Device: updated 192.168.1.51:41677 successfully with adb install -r; app data retained.

## Show correction in playback badge (2026-11-06)
- [x] Add a secondary correction-profile line, retaining the preset/status hierarchy and Night icon.
- [x] Verify profile visibility while bypassed, no-correction state and long names at enlarged text.
- [x] Build, run focused UI checks and install the verified APK.

Validation: GitHub debug app/test builds pass. QuickTuningUiTest passes 10 tests including secondary-line position, long profile at 150% text, bypass retention and No correction; PlaybackTuningUiTest passes 2 tests after updating its stale EQ on/off matcher to On/Bypassed. Badge fits playback and remains tappable at normal/large text. Working-tree and master whitespace checks pass. Emulator always_finish_activities restored to 1. Evidence: /tmp/musicmate-profile-badge-build.log, /tmp/musicmate-profile-badge-ui.log and /tmp/musicmate-profile-badge-playback-ui.log.
Device: adb install -r succeeded on 192.168.1.51:41677; app data retained.

## Optional correction and independent sound modes (2026-11-06)
- [x] Remove correction prerequisite in processing and preferences.
- [x] Enable sound controls without correction and clarify status wording.
- [x] Verify no-correction behavior, build and install.

Validation: app and instrumentation APK builds pass; full JVM suite 384 tests, zero failures/errors. Focused runtime UI suite passes 21 tests including no-correction quick controls and correction removal in the full editor. DSP tests cover each preset, Night, USB consent, Custom preview/comparison and preference persistence without correction. Legacy transition preserves mode/Night/enablement. git diff --check and git diff master --check pass. Emulator always_finish_activities restored to 1. Installed on 192.168.1.51:41677 with adb install -r successfully; data retained. Logs: /tmp/musicmate-optional-correction-verified.log and /tmp/musicmate-optional-correction-ui.log.

## Simplify audio tuning interface (2026-11-06)
- [x] Review rendered quick/full tuning and define a simpler control hierarchy.
- [x] Put sound selection first, consolidate status, and tuck correction setup behind management.
- [x] Verify touch/scroll, large text, independent correction behavior; build and install.

Validation: app and instrumentation builds pass. Main runtime suite passes 28 tests; selected-description and fixed-header suite passes 3 tests after measuring actual line bounds with 1px rounding tolerance. Reviewed rendered quick/full panels at normal text and full panel at 150%; modes and Night are visible, correction/curve collapsed, no clipping. Working-tree and master whitespace checks pass. Emulator always_finish_activities restored to 1. Logs: /tmp/musicmate-simplify-ui.log, /tmp/musicmate-simplify-complete-ui.log, /tmp/musicmate-simplify-complete-build.log. Screenshots: /tmp/musicmate-tuning-simple.png, /tmp/musicmate-dsp-simple.png, /tmp/musicmate-dsp-simple-large.png. Installed successfully on 192.168.1.51:41677 with data retained; streamed transfer stalled, final file-based install succeeded. Evidence: /tmp/musicmate-simplify-device-install.log.

# Measure loudness for ReplayGain (2026-11-10)

- [x] Fix the ebur128 parser to read the Summary block with peak=true, as a pure function with unit tests from real ffmpeg output.
- [x] Add a Measure loudness action for selected tracks, computing album gain only when a whole library album is measured.
- [x] Always write track and album ReplayGain tags to the file after measuring and refresh the gain cache. The database has no ReplayGain fields; the next scan re-reads the changed file.
- [x] Add an opt-in background worker that measures tracks without ReplayGain tags only while charging, writing the tags to the files.
- [x] Run unit tests, build, update docs and changelog, and verify on the phone.

Verification: core 281, githubDebug 389 and playDebug 389 unit tests pass, including LoudnessTest (4) and LoudnessTaggerTest (4). On the phone, Measure loudness on #REBEL (the only track of album SIX) wrote track and album gain of -8.60 dB, peak 1.011579; desktop ffmpeg measures the file at -9.4 LUFS. Selecting both tracks of Adele 19 wrote track gains 0.50 and -4.00 dB and album gain -2.51 dB on both, matching the duration-weighted mean by hand; measuring Tired alone afterwards kept -2.51 dB and left the other file untouched. Two 24/96 tracks (8 minutes) took about 2.6 s. Turning the charging option on registered a LoudnessWorker job requiring charging, battery not low and storage not low; turning it off removed it. Not verified: a worker run on the phone, which was left off so it does not rewrite library files.

# Tuning status while a renderer plays (2026-11-08)

- [x] Publish the remote player name in NowPlayingState from MainActivity.
- [x] Keep the Now Playing tuning chip visible and unavailable for remote players.
- [x] Show a renderer notice and an unavailable status in quick and full tuning; disable Compare there.
- [x] Name the tuning switch by its visible label and update the UI tests.
- [x] Use one fallback string for a missing output.
- [x] Build, run unit and tuning UI tests, inspect the diff.

Verification: 385 app unit tests pass, including the new TuningAudibilityTest renderer case. On the MM_API34 emulator, QuickTuningUiTest (14), HeadphoneEqUiTest (8), AudioTuningSheetUiTest (5), PlaybackTuningUiTest (4, two new renderer cases at 100% and 150% text) and SoundSignatureUiTest (3) pass. The 150% renderer case first failed because "Neutral · Unavailable" wrapped, so the badge text cap went from 220dp to 280dp. Renderer screenshots were inspected. Not verified: a real DLNA renderer on the phone, and what TalkBack announces for the merged switch row (the hint and stateDescription may repeat the reason).

# AutoEq search and DIY presets (2026-10-28)
- [x] Feature 1. `how` over the affected subsystem (explorer map of headphones package, dialog and import flow).
- [x] Feature 2. `architect` for parallel design exploration. skip: two small additions on an existing shape; one design pass reviewed by the advisor instead.
- [x] Feature 3. Throughput checkpoint. Blocking first: data shapes for presets and the AutoEq index entry. Independent workstreams: presets (manual tuning model and dialog) and AutoEq (new fetch/index code and import UI) touch disjoint files except HeadphoneControls.kt. Shared mutable state: HeadphoneControls.kt, so the AutoEq UI wiring goes last. Smallest safe decomposition: one owner, because the code is untracked and cannot be split into worktrees.
- [x] Feature 4. Delegate code-writing. Adapted: the headphone code is untracked, so a worktree would not contain it; one worker edits the main tree while I do not touch it.
- [x] Feature 5. Verify on the matching surface: JVM tests, emulator UI tests, phone install.
- [x] Feature 6. Small ordered commits. skip: user commits on request only.
- [x] Feature 7. interrogate if contested. skip unless the advisor disputes the design.
- [x] Feature 8. Opening a PR. skip: no commit or PR requested.
- [x] DIY presets: a preset table (Flat, U-shape, V-shape, Warm, Bright, Vocal and similar) applied to the selected ear (Both, Left or Right) in the DIY dialog, then still adjustable.
- [x] AutoEq: download and cache results/INDEX.md, search by model, fetch the chosen ParametricEQ.txt, import with "AutoEq, by <source>" as the source, and show an offline or not-found message. Verified live on the phone: index 0.8 s and profile 0.3 s after moving httpGet to OkHttp, since HttpURLConnection took 60 to 326 s on this Wi-Fi because IPv6 to GitHub is dropped.

# Queue source switch replaces Up next (2026-11-10)

- [x] Make picking a source or mood replace the upcoming tracks, keeping the current and Play next tracks.
- [x] Offer Undo that restores the whole queue, until dismissed and across several switches in a row.
- [x] Leave the queue unchanged and say so when a source finds nothing.
- [x] Verify on the phone with Sleep, New and Downloads, then Undo, ending on Manual.

# Queue review fixes and mood queues (2026-11-10)

Design: mood presets are built-in genre-rule playlists in `playlists.json` with two new optional fields, `category: "mood"` and `sessionMinutes` (0 = refill forever). They reuse `Source.PLAYLIST`. The review is in this session's transcript; findings are listed below.

- [x] Core: fix the "Groove" and "Synth-based" style terms, add a test that every rule value exists in the tag-editor lists.
- [x] Core: parse `category` and `sessionMinutes`; add Drive, Focus, Wind down and Sleep presets.
- [x] Core: mood sources shuffle candidates; a source with `sessionMinutes` stops refilling once the session's suggested tracks reach that length. Tests in QueueManagerTest.
- [x] UI: error copy with a recovery step and a working retry; refresh status as text plus a live region; chips become FilterChip with selected semantics.
- [x] UI: stable row keys for drag reorder, dp threshold, handle label; Undo snackbar after swipe-remove.
- [x] UI: one name per source, "below" copy, plurals, strings in resources, sentence case, jargon, empty-state actions, source accent tokens, badge label and colour from one tier, tap haptics.
- [x] UI: Mood chip and picker sheet with match counts and "Stops after N min" for Sleep; mood presets hidden from the playlist picker.
- [x] Verify: core and app unit tests, screenshot references, device screenshots of queue, chips and mood sheet.
- [x] Count tag coverage on the phone: 8133 tracks, mood on 769 (9%), style on 1567 (19%), either on 1910 (23%), BPM on 0. Old style names (Groove 74, Synth-based 21, Audiophile Vocal 168) need aliases; Sleep rules widened because no track is tagged Sleep.
- Deferred: reading the BPM tag. The Room column already exists but every row is 0, because nothing reads the tag yet.

Verification: 265 core and 389 app unit tests pass. The two queue screenshot references were regenerated and inspected; the other 27 references already fail on a clean HEAD (last refreshed 2026-10-29) and were left alone. On the phone, the Mood dialog counted Drive 187, Focus 134, Wind down 632 and Sleep 131 tracks; selecting Sleep marked the chip in the mood accent, then the source was set back to Manual. Not verified on a device: tracks actually being added by a mood (the phone queue already holds the whole library) and the 45-minute stop.
After review: picking a mood that adds nothing now shows "Every Sleep track is already in your queue." (seen on the phone), the active row label reads Selected, and a disabled row dims as a whole.


# Crossfeed and limiter indicator (2026-11-20)
Crossfeed is a port of BS2B (MIT, 700 Hz / 4.5 dB default). It boosts no frequency (0.00 dB worst case at 44.1 and 192 kHz), so the auto preamp needs no term for it. Sample peaks can still rise by up to 1.9 dB at 44.1 kHz and 2.6 dB at 352.8 kHz on a full-scale anti-phase square; the safety limiter after the chain catches those and the guard shows Limiting.
- [x] `audio/dsp/Crossfeed.kt`: BS2B filter on a stereo frame, with reset.
- [x] `CarDspSettings.crossfeed` and `DspChain(crossfeed)`: crossfeed first, then balance and per-ear EQ (speakers simulated, then the headphone corrected); stereo only. CarDspProcessor builds its three chains through one helper, so toggling crossfades.
- [x] `HeadphoneBinding.crossfeed`, `HeadphonePreferences.setCrossfeed`, `settingsFor` carries it (same gate as Night: tuning on, USB consent).
- [x] Crossfeed switch beside Night listening in quick and full tuning, with an inactive hint when tuning is off or USB consent is missing; the Now Playing badge shows it, and saved-but-inactive in its accessibility state.
- [x] `OutputGuard`: the processor reports each block's limiter reduction (0.1 dB or more) and any hard clamp; throttled flow.
- [x] Badge shows "Limiting" (GoldAmber) or "Clipping" (Destructive) for 3 s after the latest event, also in its accessibility state; not for a remote player.
- [x] Tests: response at or below 0 dB on the grid (44.1, 192, 352.8 kHz), anti-phase square stays under the ceiling and lights Limiting, chain order, settings and JSON default; guard reports for limiter, clamp, clean playback.
- [x] Docs (HEADPHONE_EQ, AUDIO_TUNING, CAR_AUDIO_DSP, USER_GUIDE) and CHANGELOG.
- [x] Unit tests, githubDebug build, badge screenshot render.
- [x] Review fix: a +1.0 sample landing on the top code is not counted as Clipping; only a real overshoot is. Badge render baseline added at app/src/screenshotTestGithubDebug/reference/.../AudioTuningBadgeGuardPreview.
- [x] Limiting threshold raised from 0.1 dB to 1 dB at the user's request, so the routine 0.5 dB ceiling on full-scale masters stays dark.

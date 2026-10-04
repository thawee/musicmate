# MQA display fix plan

Status: implemented. App tests/build pass; eight MQA previews render and were visually inspected. Screenshot reference comparisons and the remaining live-device matrix are pending.

Objective: preserve MQA identity across native screens, use consistent tier colors, and expose the stored original sample rate without presenting it as a measured playback/output rate.

## Display contract

| Surface | MQA | MQA Studio |
| --- | --- | --- |
| Compact library/queue/related quality label | MQA | MQA |
| Expanded badge and full-screen verdict | MQA MASTER | MQA STUDIO |
| Native tier accent | Existing MQA magenta | Existing MQA magenta |
| Compact resolution | Encoded file bit depth/sample rate | Encoded file bit depth/sample rate |
| Player technical details | Encoded rate and labeled original rate, when known | Encoded rate and labeled original rate, when known |

Example: a 24-bit/44.1 kHz MQA Studio file with original rate 192 kHz shows compact `MQA 24/44.1`, expanded `MQA STUDIO`, and technical details `Encoded: 24-bit / 44.1 kHz` plus `Original: 192 kHz`. Original rate is metadata, not evidence of active unfolding or DAC output. Unknown original rates are omitted. Known original rates remain clearly identified even when equal to the encoded rate.

Retain compact label density and the existing native magenta design. The Web UI's green styling and collapsed Studio label are separate product choices; changes to those are outside these four native fixes.

## 1. Add focused regression coverage

- [x] Extend `VUMeterAndBadgeTest.kt` with MQA and MQA Studio at 16/44.1, 24/44.1, and 24/96; demonstrate the compact 24-bit classification failure before fixing it.
- [x] Cover expanded labels, tier accents, and detailed resolution through the shared pure presentation helpers introduced below.
- [x] Cover original rates that differ, match, or are absent; null tracks and non-MQA tracks carrying stale original-rate metadata.
- [x] Retain current DSD, lossless, Hi-Res, PCM24, and lossy behavior as regression checks.

## 2. Centralize native quality presentation

- [x] Inspect existing helpers and introduce the smallest shared, unit-testable presentation policy needed for label and accent selection in the app UI layer; reuse `TagUtils` predicates.
- [x] Keep DSD precedence, then recognize MQA Studio/MQA before generic Hi-Res and PCM24 tiers.
- [x] Use that policy in both `QualityBadge` overloads, `getUnifiedBadgeText`, and `UnifiedAudioBadge` in `AudioBadges.kt`; handle the string fallback's Studio label explicitly.
- [x] Remove the independent MQA-blind verdict calculation in `MainActivity.java`, using a Java-callable presentation helper or the same policy in the full-screen track rendering.
- [x] Ensure `NowPlayingPage.kt` and `FullscreenStudioConsole.kt` agree for the same track, including fallback data and track changes.

Acceptance: 24-bit MQA remains labeled MQA at every native width; expanded Studio identity survives; all MQA badges and verdicts use the configured magenta accent. Generic quality labels remain stable.

## 3. Share resolution presentation

- [x] Add a focused formatter for encoded file resolution and optional MQA original sample rate. Preserve fractional rates such as 44.1 and 88.2 kHz.
- [x] Keep compact resolution tied to the encoded file rate; use its accessible description to include the known original rate.
- [x] Show explicitly labeled encoded/original rates in Now Playing audio anatomy and full-screen technical details, using the same formatter.
- [x] Use original-rate metadata only for MQA tracks. Avoid zero, duplicate unlabeled values, or decoding/output claims.
- [x] Preserve DSD formatting and incomplete-metadata fallbacks.

Acceptance: the example above exposes both rates in player details and accessible badge descriptions; compact rows remain readable. Missing original metadata does not invent a rate.

## 4. Verify layouts and behavior

- [x] Add MQA/MQA Studio preview fixtures and focused screenshot coverage for compact versus wider rows (below/at 390dp), expanded badges, and both player modes.
- [x] Check long original-rate text and 200% font size for clipping or inaccessible details; place details in existing scrollable spec areas where appropriate.
- [x] Run focused badge/presentation tests and relevant player tests, then `:app:testDebugUnitTest` and `:app:assembleDebug`.
- [x] Run relevant screenshot comparisons and inspect changed renders; do not silently accept new references.
- [x] Review the scoped diff against `main`, preserve unrelated working-tree edits, and run `git diff --check`.
- [ ] When a device is available, exercise real MQA/MQA Studio files, track changes, compact/wide layouts, and both player modes. Record any unavailable runtime checks as pending.

## 5. Document completion

- [x] Update `UI.md` and `USER_GUIDE.md` with the quality precedence, shared accent, and encoded/original distinction.
- [x] Record test/build/screenshot results and live verification limits in the review report and `tasks/todo.md`.

Implementation boundary: presentation and its tests/docs only. Metadata scanning, MQA detection algorithms, playback decoding, database schema, and Web API changes are not needed to fix the confirmed native display defects.

## Verification evidence (2026-09-30)

- Red regression: 24/44.1 MQA expected `MQA 24/44.1`, actual `24-BIT 24/44.1` before the fix.
- Final `:app:testDebugUnitTest :app:assembleDebug`: 94 tests, zero failures/errors/skips; debug APK built. Badge suite has 11 tests and includes MQA, Studio, original-rate, incomplete metadata, and generic-format cases.
- Eight focused MQA previews rendered and were visually inspected: 360dp/390dp rows, 200% text rows, regular playback, audio anatomy at normal/200% text, and full-screen playback at normal/200% text. The original-rate labels wrap/read correctly; the audio anatomy remains scrollable. Library rows now wrap DR/duration below the badge at large text instead of clipping.
- Focused screenshot validation reports eight missing reference images, zero image mismatches. No reference images were created or replaced. The earlier all-preview run retained the 21 existing comparison mismatches plus missing MQA references.
- Reviewed relevant differences against `main` and the working-tree diff; preserved pre-existing changes. `git diff --check` passed.
- Installed the final debug APK and launched it successfully on device `RFCY21CLTDY`. A real 24/44.1 MQA Studio track preview displayed `MQA STUDIO` with magenta styling; UI hierarchy confirmed `Audio quality: MQA STUDIO` and `Resolution: Encoded: 24-bit / 44.1 kHz`. That track did not expose a known original sample rate. Live MQA/MQA Studio player journeys, track changes, and resize checks remain pending.
- Logs: `/tmp/musicmate-mqa-red.log`, `/tmp/musicmate-mqa-final-tests.log`, `/tmp/musicmate-mqa-previews.log`. Rendered images: `app/build/outputs/screenshotTest-results/preview/debug/rendered/apincer/android/mmate/ui/compose/MqaScreenshotTestKt/`.

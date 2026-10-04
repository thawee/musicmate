# App functionality and interface review — 2026-09-30

## Scope and result

Reviewed the current native Android app, including pending UI/queue changes, documented user flows, playback callbacks, menu wiring, unit tests, and rendered screenshot previews. Inspected differences against `main`; that branch differs across much of the application, so this is a focused functional/UX review rather than an exhaustive audit of every changed file. Existing application edits were preserved.

Four concrete findings remain. The build and unit tests pass, but screenshot and lint gates are red. Live device behavior remains unverified because `adb devices -l` returned no connected devices.

## Findings (ordered by priority)

### 1. [P1] Natural completion bypasses Repeat One

- **Locations:** `app/src/main/java/apincer/android/mmate/service/MusicMateServiceImpl.java:189`, `:747`, `:766`; `app/src/main/java/apincer/android/mmate/service/AndroidPlayerController.java:179`.
- **Trigger:** Use local playback, select Repeat One, and let a track finish through the completion callback.
- **Evidence:** `STATE_ENDED` invokes `onPlaybackCompleted()`, which calls `skipToNextInQueue()`. The pending change makes that method call `getNextTrack(true)`, bypassing Repeat One. A subsequent queued track plays, or playback stops at the queue end, instead of repeating the current track. The automatic-transition fallback also uses the completion callback.
- **Recommendation:** Distinguish natural completion from an explicit Next action at the service boundary. Only explicit skips should use `forceSkip=true`.
- **Verification gap:** Queue unit tests cover the overloads separately, but do not cover this service callback path. This finding is established by the call chain, not a live playback reproduction.

### 2. [P2] Single-track conversion disappeared from the overflow menu

- **Location:** `app/src/main/java/apincer/android/mmate/ui/compose/TrackListItem.kt:249`.
- **Trigger:** Open a track's overflow menu and try to convert its format.
- **Evidence:** The replacement Compose menu contains Play Now, Play Next, Add to Queue, and Open in External App. It omits `action_encoding_file`, although `MainActivity.handleTrackMenuAction()` still handles it and `menu_track_popup.xml` and `UI.md` specify Convert Format. Conversion remains in the multi-select menu, requiring users to discover a different workflow.
- **Recommendation:** Restore Convert Format in the Compose overflow menu and test the emitted action ID.

### 3. [P2] Audio metadata clips at 200% text size

- **Location:** `app/src/main/java/apincer/android/mmate/ui/compose/TrackListItem.kt:195`.
- **Trigger:** Display the library at 360dp width with system text scaling at 200%.
- **Evidence:** The freshly rendered `MainShellPopulatedPreview_Compact 200% text` visibly cuts off the dynamic-range labels and hides or truncates track durations. The badge layout chooses its compact variant solely from screen width, then places all badges and duration in one unwrapped row. It does not consider font scale or the actual space left after artwork and the overflow column.
- **Recommendation:** Wrap or stack the metadata at large text sizes, using available row width and font scale. Preserve readable text rather than shrinking it.
- **Image:** `app/build/outputs/screenshotTest-results/preview/debug/rendered/apincer/android/mmate/ui/compose/MainLibraryScreenshotTestKt/MainShellPopulatedPreview_Compact 200% text_3e756e6f_0.png`.

### 4. [P2] Playback menu actions remain enabled when unavailable

- **Locations:** `app/src/main/java/apincer/android/mmate/ui/compose/TrackListItem.kt:249`; `app/src/main/java/apincer/android/mmate/ui/MainActivity.java:1370`.
- **Trigger:** Open a track menu while the playback service is unbound or disconnected, then select a playback/queue action.
- **Evidence:** The previous popup hid the playback group when the service was unavailable. The new menu always enables all three actions. Play Next and Add to Queue silently return without changing the queue or showing feedback when the binding check fails; Play Now similarly returns from `playCurrentResults()` for a null service.
- **Recommendation:** Expose playback availability to the menu and disable unavailable actions, or provide a recovery action and explicit feedback.

## Interface observations

- The dark palette, gold accents, track artwork, and grouped Music Center tabs provide a consistent visual hierarchy in the inspected previews.
- Phone landscape and the foldable supporting pane render usable library/queue layouts in the inspected images. These previews do not establish touch, keyboard, or TalkBack behavior.
- Settings at 200% text stacks its interaction choices and wraps copy. Content below the initial viewport is not treated as a defect merely because the screen is scrollable.
- The new rounded tag-action dock declares a 16dp bottom margin, but `TagsActivity.java:413` resets it to zero on insets dispatch. Verify the resulting dock spacing on a device before considering the floating treatment complete.
- Queue source chips use small text and a custom clickable row without explicit selected-state semantics. Inspect their accessibility tree and touch bounds on-device; current queue screenshot fixtures omit the deck when no manager is provided.

## Validation evidence

| Check | Result |
| --- | --- |
| `:core:testDebugUnitTest` + `:app:testDebugUnitTest` | 142 tests across 25 suites; zero failures, errors, or skips |
| `:app:assembleDebug` | Passed |
| `:app:validateDebugScreenshotTest` | 26 previews; 21 comparisons failed, zero rendering errors |
| `:app:lintDebug` | Failed: 33 errors, 1107 warnings, 9 hints |
| `git diff --check` | Passed |
| Connected runtime / accessibility tests | Not run: no device connected |

Screenshot comparisons reflect pending visual changes as well as possible regressions; 21 comparison failures do not mean 21 functional defects. Visually inspected fresh playback, library landscape, library 200% text, foldable queue, and Settings 200% text previews. Reference images were not updated.

The first lint error is `MissingSuperCall` on `MusicMateServiceImpl.onStartCommand`. The repository's prior task notes already report a 33-error lint baseline; this review does not attribute all lint issues to the pending edits.

Reports: `app/build/reports/screenshotTest/preview/debug/index.html`, `app/build/reports/lint-results-debug.html`. Logs: `/tmp/musicmate-app-review-gradle.log`, `/tmp/musicmate-app-review-ui.log`, `/tmp/musicmate-app-review-lint.log`.

## Remaining device verification

Use a device with representative music and an available playback target to verify launch/folder discovery, permission grant/revoke, service reconnection, search and Back, local Repeat One, DLNA/external-player handoff, queue reorder/remove/undo, tag Save/Discard and artwork persistence, server start/stop, rotation/resize, and TalkBack/keyboard access. Passing previews and policy unit tests do not prove these journeys.

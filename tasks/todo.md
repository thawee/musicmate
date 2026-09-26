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

# Compact Tag Preview — Implemented; Device Review Pending

## Objective
- Improve first-screen density after the user confirmed folder scrolling works. Detailed plan: `tasks/plan.md`.

## Checklist
- [x] Review the proposed compact layout with the user. Approved: “do it”.
- [x] Compact the centered square artwork and title using available viewport space. Java/resources compile; source checked for restoring full-width editor artwork. Device transition verification pending.
- [x] Group genre/audio badges and consolidate labeled metadata rows. Compose compilation passes; navigation callbacks preserved and source reviewed.
- [ ] Compare against the reference screenshot; target all metadata visible at default font on that phone.
- [x] Build, run app tests, check diff, and update UI/release documentation. Debug assembly and app tests passed; diff clean. Source review covers 48dp controls, 56dp metadata rows, wrapping, and viewport bounds; runtime responsive/accessibility checks remain pending.

## Verification limit
- The same-device screenshot comparison and interactive preview/editor checks remain pending because device-tool instruction access was denied earlier in this session. Do not treat the first-screen fit target as visually verified.

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
- [x] Run unit tests and deploy debug build to physical device (`RFCY21CLTDY`)

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

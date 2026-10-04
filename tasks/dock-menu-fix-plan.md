# Plan: Main Dock Menu Safety and UX

## Status and scope

Plan recorded with user approval to use this separate file and append the checklist to `tasks/todo.md`. Implementation has not started. The existing `tasks/plan.md` and unfinished tag-preview visual check remain intact.

Preserve the floating dock, MusicMate branding, navigation drawer, existing destinations, and Listener/Curator behavior. Fix selection safety and Back handling first, then menu wording and accessibility.

Out of scope: new Clean Up/Inspect workflows, monetization, playback-engine changes, broad selection-system refactoring, and the unrelated user edit in `AboutScreen.kt`. No new dependencies are assumed.

## Evidence and limitations

- `MainScaffold.kt`: the dock logo opens a `ModalNavigationDrawer`; menu actions dispatch callbacks and request closure.
- `MainActivity.java`: category changes do not end selection; list replacement retains positional selection while batch actions use the selected-track collection.
- `MainActivity.java` and `DrawerInterop.kt`: Back handling does not consult drawer state, although open/close helpers exist.
- `MainScaffold.kt`: selected styling exists, but custom menu items do not expose selected semantics.
- Menu labels obscure some destinations: similar-title matching is labeled Discover Similar; Notifications opens notification-listener access.
- These are source-review findings. The connected Samsung phone was locked during the review; visual behavior and live reproduction remain unverified. No dedicated drawer Back or category-selection regression tests were found.

## Implementation decisions

- End selection before applying a library-destination change, including reselecting a destination that resets the current query. Opening or dismissing the drawer alone must preserve selection.
- Prevent pending refreshes from restoring selections belonging to a previous destination. Keep same-view selection behavior unless a regression demonstrates that reconciliation is needed.
- Give an open drawer priority in Back handling. Reuse the existing drawer bridge where sufficient; avoid competing Back handlers. When closed, preserve current selection/search/category Back behavior.
- Keep current menu destinations and grouping. Wording changes must not imply new recommendation, analysis, or duplicate-deletion capabilities.
- Use semantic selected state and explicit minimum interaction areas. Do not infer actual touch bounds from icon size alone.
- Keep the logo; choose any additional visible menu cue after a device comparison, without crowding transport controls.

## Ordered implementation tasks

### 1. Make library navigation selection-safe

**Description:** Clear contextual selection at the library-navigation boundary so batch actions cannot retain hidden tracks after a destination change. Add regression coverage before the fix.

**Acceptance criteria:**
- Switching destination with selected tracks removes selection and contextual action mode before showing the new list.
- Pending list refreshes cannot restore prior-destination selection; Edit, Move, Convert, and Delete cannot receive the old tracks.
- Opening/dismissing the drawer and refreshing within the same view retain expected selection behavior.

**Verification:** Use fixture tracks and fake batch-action receivers to exercise category changes and delayed refreshes. Never execute destructive checks against the real music collection. Run focused new tests, then `./gradlew :app:testDebugUnitTest :app:assembleDebug`.

**Dependencies:** None.
**Likely files:** `MainActivity.java`, a focused test under `app/src/test/`, and a small testable navigation helper only if existing test seams require one. Inspect `MySelectionTracker.java` and `ListInterop.kt`; change them only if necessary.
**Estimated scope:** Medium, 2-4 files.

### 2. Dismiss the drawer before underlying Back navigation

**Description:** Route Back to the open drawer first, without changing underlying library state.

**Acceptance criteria:**
- One Back action closes an open drawer while preserving selection, search, filters, and category.
- With the drawer closed, Back retains existing underlying behavior; no duplicate Back consumption is introduced.
- Outside-tap, swipe dismissal, and repeated open/close interactions still work.

**Verification:** Add focused regression tests for drawer-open and drawer-closed states, including active selection and search. Run app unit tests/debug build and verify system Back on an unlocked device.

**Dependencies:** Task 1, sequenced to avoid concurrent edits to `MainActivity.java`.
**Likely files:** `MainActivity.java`, `DrawerInterop.kt` if required, and focused navigation tests.
**Estimated scope:** Medium, 2-3 files.

### Checkpoint A: Functional safety

- Focused regressions and the app test suite pass; debug build succeeds.
- Verify select tracks -> open menu -> switch category -> no stale selection.
- Verify open menu -> Back -> same underlying view and state.
- Record any device blocker rather than marking interactive verification complete.

### 3. Clarify menu labels

**Description:** Make labels match existing destinations, using string resources for changed copy.

| Current | Proposed | Explanation |
| --- | --- | --- |
| Discover Similar | Similar Tracks | Find possible duplicates; not recommendations or proof of duplicate files |
| Notifications | Notification Access | Explain its use for integration with other players |
| Manage Library | Music Folders & Scan | Select indexed folders and start scanning |
| Sound Grade | Audio Quality | Browse quality categories; does not start analysis |

**Acceptance criteria:**
- Labels and brief explanations match the current handler behavior, without changing destination IDs or queries.
- Similar-track wording does not imply that results are safe to delete.
- All changed copy remains readable at enlarged text sizes without overlapping icons or badges.

**Verification:** Verify action-to-destination mapping, build resources/Compose, and inspect labels at default and enlarged text sizes.

**Dependencies:** Checkpoint A.
**Likely files:** `MainScaffold.kt`, `res/values/strings.xml`, and menu tests if available.
**Estimated scope:** Small to medium, 2-3 files.

### 4. Improve menu accessibility and recognition

**Description:** Expose current destination state, provide reliable interaction areas, and verify the menu affordance without redesigning the dock.

**Acceptance criteria:**
- Accessibility semantics expose the current library destination and meaningful action labels; external Settings/About actions do not falsely become library selections.
- Menu button and rows provide at least 48dp interaction areas without overlap; focus order, dismissal focus, insets, and scrolling remain usable.
- Menu remains reachable with no current track, during playback, on narrow/landscape layouts, and with large text. Any added menu cue preserves branding and playback space.

**Verification:** Add focused Compose semantics/interaction tests where supported. Inspect existing test setup before selecting tooling; any new test dependency needs explicit justification. Manually check TalkBack, focus return, touch bounds, and device screenshots. Compare a minimal menu cue before finalizing a visual change.

**Dependencies:** Task 3, because both touch `MainScaffold.kt`.
**Likely files:** `MainScaffold.kt`, string/drawable resources as needed, and focused UI tests.
**Estimated scope:** Medium, 3-5 files.

### Checkpoint B: Menu experience

- Wording and selected semantics match the actual destinations.
- No clipping, inaccessible bottom entries, or overlapping dock targets in the tested layouts.
- Capture and inspect before/after screenshots; record device/build identity and distinguish installed-build behavior from current source.

### 5. Complete verification and documentation

**Description:** Verify the integrated behavior, review the final diff, and document only confirmed results.

**Acceptance criteria:**
- All 13 menu destinations, active highlighting, dismissal methods, and return from external screens pass the applicable checks.
- Functional regressions, app unit tests, debug build, and diff hygiene pass; unresolved runtime or accessibility checks are explicitly listed.
- UI documentation, release notes, and checklist accurately describe the result; unrelated work remains unchanged.

**Verification:** Run `./gradlew :app:testDebugUnitTest :app:assembleDebug` and `git diff --check`. Confirm available Gradle tasks against current configuration before running, since documentation also describes flavor-specific builds. Run relevant lint checks and separate pre-existing failures from regressions. Conduct an independent review of selection and Back changes.

**Dependencies:** Tasks 1-4 and both checkpoints.
**Likely files:** `UI.md`, `CHANGELOG.md`, `tasks/todo.md`, this plan's verification status.
**Estimated scope:** Medium, documentation and verification.

## Device validation matrix

| State | Required check |
| --- | --- |
| No current track / playing track | Menu reachable; navigation does not interrupt playback |
| Selected fixture tracks | Category switch clears selection; drawer dismissal alone preserves it |
| Active search / filter / category detail | Back closes drawer before changing underlying state |
| Every menu destination | Correct destination; drawer closes; library highlight remains accurate |
| Settings / About / system permission screens | Return without unexpected navigation or permission changes |
| Narrow portrait / landscape / large text | All entries reachable; no overlap or clipped essential labels |
| TalkBack / keyboard where available | Correct labels and selected state; usable focus and dismissal |

Use disposable fixtures for batch-operation checks. Do not grant permissions, scan new folders, alter library files, or reinstall the app merely to inspect menu navigation without the necessary authorization.

## Risks and open decisions

- Positional selection remains elsewhere: keep this fix bounded to navigation safety and flag unrelated reconciliation defects separately.
- Shared files make parallel implementation risky: execute tasks sequentially. An independent reviewer can review completed slices.
- Device access/build mismatch can invalidate visual claims: unlock the device and establish which build is being inspected before accepting runtime evidence.
- Menu cue choice remains open until live comparison; the correctness fixes do not depend on it.
- Existing `AboutScreen.kt` changes and prior unfinished tasks belong to the user and must remain intact.

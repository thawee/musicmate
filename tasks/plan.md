# Implementation Plan: App UX Modernization

## Overview

Raise MusicMate's UI from its current polished phone experience to a measurable, adaptive, and accessible Android experience. The work starts with visual and accessibility safeguards, then replaces ad hoc navigation state with Navigation 3, adds a canonical supporting-pane experience for larger windows, and finishes with reversible queue interactions plus device validation.

The current baseline already includes a navigation rail at 840dp, responsive Settings layouts, actionable empty states, 48dp targets in recently touched flows, and guarded playback controls. The remaining gaps are the lack of screenshot and accessibility test infrastructure, hardcoded user-facing text, a hybrid Java/Compose navigation shell, modal-only Music Center presentation, incomplete keyboard/pointer behavior, and pending live TalkBack/device checks.

## Success Criteria

- Compact portrait, compact at 200% font scale, landscape, and expanded tablet layouts have deterministic screenshot coverage for the library, Music Center, queue, and Settings.
- Automated Compose accessibility checks pass for the primary library, playback, queue, server, and Settings flows; TalkBack order and labels are also checked on a device.
- Navigation state survives rotation, resize, and process recreation without changing search, selection, playback, or Back behavior.
- Compact windows keep the Music Center sheet; expanded windows can show Music Center as a supporting pane beside library content.
- Queue removal is reversible, reordering is discoverable to touch and assistive technology users, and core actions work with keyboard and pointer input.
- The debug build, app/core unit tests, screenshot verification, focused connected tests, and diff hygiene all pass before completion.

## Architecture Decisions

- Establish tests before structural migration. Screenshot fixtures and accessibility checks provide a reviewable baseline for each later slice.
- Add Navigation 3 through a compatibility layer around `MainActivity` and `MainScaffoldCallbacks`. This keeps playback, repository, and service behavior stable while routes and back-stack ownership move into Compose.
- Migrate overlays first, then top-level destinations. Music Center and Studio Console are isolated route slices that prove save/restore and Back behavior before the main library navigation moves.
- Use Material 3 Adaptive Navigation 3 scenes for the expanded Music Center supporting pane. Compact windows retain the existing sheet interaction and all widths share the same hoisted content/state.
- Keep the track library as a dense single-column collection. The expanded supporting pane uses extra width without forcing track rows into a harder-to-scan grid.
- Use resource-backed user text and semantic state descriptions in every touched screen. Decorative artwork and icons remain hidden from the accessibility tree where surrounding controls already provide the label.
- Isolate the experimental Compose screenshot engine in a dedicated source set with pinned versions. Task 1 is a compatibility gate for AGP 9.4.1; implementation stops before UI migration if the Gradle tasks are unreliable.

## Dependency Graph

```text
Task 1 verification harness
├── Tasks 2-3 visual baselines
└── Tasks 4-6 accessibility/localization
        └── Task 7 Navigation 3 route foundation
            ├── Task 8 overlay routes
            │   └── Task 10 adaptive Music Center pane
            └── Task 9 top-level navigation
                ├── Task 10 adaptive Music Center pane
                └── Task 11 keyboard/pointer interactions
Tasks 4-6 ────────────────┬── Task 11 queue interaction polish
                          └── Task 12 motion and large-text hardening
Tasks 10-12 ───────────────── Task 13 release validation
```

## Task List

### Phase 1: Verification Foundation

- [x] Task 1: Add Compose screenshot and connected accessibility test infrastructure.
- [x] Task 2: Capture deterministic library and main-shell visual baselines.
- [x] Task 3: Capture deterministic Music Center, queue, and Settings visual baselines.

### Checkpoint A: Baseline Locked

- [x] Screenshot verification runs without modifying reference images.
- [x] A Compose accessibility smoke test runs on the supported emulator/device API.
- [x] `:app:testDebugUnitTest` and `:app:assembleDebug` remain green.

### Phase 2: Accessibility and Language

- [ ] Task 4: Harden library and main-shell semantics.
- [ ] Task 5: Harden Music Center, queue, and media-server semantics.
- [x] Task 6: Harden Settings, permission, and About semantics.

### Checkpoint B: Accessible Primary Flows

- [x] Automated checks cover labels, target size, contrast, and traversal order in the primary flows.
- [x] All touched user-facing strings come from Android resources.
- [x] Baseline screenshot changes are intentional and reviewed.

### Phase 3: Navigation Foundation

- [x] Task 7: Add the type-safe Navigation 3 route and saved-back-stack model.
- [x] Task 8: Move Music Center and Studio Console overlays onto Navigation 3 scenes.
- [x] Task 9: Move top-level library destinations and Back handling onto Navigation 3.

### Checkpoint C: Navigation Compatibility

- [x] Search, selection, drawer/rail, playback callbacks, deep links, and Back precedence behave as before.
- [x] Route and selected-item state survive rotation, resize, and process recreation.
- [x] Unit, screenshot, and focused connected tests pass.

### Phase 4: Adaptive Interaction Polish

- [ ] Task 10: Present Music Center as an expanded-window supporting pane.
- [ ] Task 11: Make queue and library actions reversible and keyboard/pointer capable.
- [ ] Task 12: Respect reduced motion and harden 200% text layouts.

### Checkpoint D: Adaptive Experience

- [ ] Compact, medium, and expanded windows preserve task continuity while resizing.
- [ ] Touch, keyboard, pointer, TalkBack, and large-text users can complete the core flows.
- [ ] No content is clipped by system bars, display cutouts, IME, or pane boundaries.

### Phase 5: Release Evidence

- [ ] Task 13: Run the full UX matrix and publish the verified behavior and remaining limits.

### Checkpoint E: Complete

- [ ] All automated suites and build gates pass.
- [ ] Reference-image diffs receive human visual approval before any golden update.
- [ ] Device results, known limits, and follow-up observations are recorded in project documentation.

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| AGP 9.4.1 and the experimental screenshot plugin are incompatible | High | Treat Task 1 as a stop/go gate; pin the compatible standalone plugin or document a contained AGP test-suite upgrade before touching UI code. |
| The Java activity and global Compose singleton disagree about navigation state | High | Introduce one typed route model, retain a callback adapter during migration, and add save/restore and Back-policy tests before moving destinations. |
| Supporting-pane work duplicates the existing bottom sheet | Medium | Extract one stateless Music Center content composable and host it in either a sheet scene or supporting pane scene. |
| Screenshot tests become noisy because of images, animations, or time | Medium | Use fixed fixtures, fake artwork, disabled clocks, stable fonts, and deterministic window configurations. |
| Accessibility fixes change visual density | Medium | Preserve minimum target sizes, allow wrapping/scrolling, and review the four required visual configurations at each checkpoint. |
| Navigation migration expands beyond a focused slice | Medium | Keep each task at five files or fewer and preserve service/repository APIs throughout this plan. |

## Scope Boundaries

- Playback engine, media-server protocol, tag-writing behavior, and repository schema changes are outside this plan.
- A full rewrite of `MainActivity` or all remaining XML/tag-editor surfaces is a separate migration.
- Experimental Grid and FlexBox adoption requires a separate proposal after the stable adaptive shell is verified.
- Visual golden files are updated only after a human reviews the generated diffs.

## References

- [Android Compose accessibility testing](https://developer.android.com/develop/ui/compose/accessibility/testing)
- [Android adaptive canonical layouts](https://developer.android.com/develop/adaptive-apps/guides/canonical-layouts)
- [Compose Preview Screenshot Testing](https://developer.android.com/studio/preview/compose-screenshot-testing)

## Approval

- User approved replacing the prior plan on 2026-09-28.
- The prior Compact Tag Preview plan is preserved at `tasks/archive/compact-tag-preview-plan.md`.

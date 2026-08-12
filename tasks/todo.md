# Option 2: Minimal Corner Accent Dot for "New" Track Indicator

## Objectives
Replace the bulky diagonal `TriangleLabelView` overlay on cover art in `view_list_music_tag.xml` with a sleek 8dp circular dot indicator (`shape_new_dot_indicator.xml`) anchored to the top-right corner of the cover art frame.

## Tasks
- [x] 1. Create `shape_new_dot_indicator.xml` drawable (8dp oval in `@color/colorGold` with subtle dark border).
- [x] 2. Update `view_list_music_tag.xml`:
  - [x] Replace `TriangleLabelView` (`item_new_label`) with an 8dp `View` anchored to `top|end` with `4dp` margin.
- [x] 3. Update `MusicTagAdapter.java`:
  - [x] Update `mNewLabelView` field type in `ViewHolder` from `TriangleLabelView` to `View`.
  - [x] Update `onBindViewMusicTag` to set visibility `GONE` for managed tracks and `VISIBLE` for unmanaged/new tracks.
- [x] 4. Verify compilation (`./gradlew compileDebugSources`).
- [x] 5. Document results in `tasks/todo.md`, update `DESIGN.md`, `CHANGELOG.md`, and `tasks/lessons.md`.

## Review & Results
- **Corner Accent Dot Applied**: Replaced the bulky diagonal `TriangleLabelView` banner (which cut off 30% of album artwork) with an 8dp circular Gold dot indicator ([`shape_new_dot_indicator.xml`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/res/drawable/shape_new_dot_indicator.xml)) anchored neatly to the top-right corner of the cover art frame. Cover art artwork is now 100% visible and un-obscured.
- **Verification**: Verified compilation cleanly with `./gradlew compileDebugSources` (`BUILD SUCCESSFUL`).

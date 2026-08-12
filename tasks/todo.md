# Fix Player Picker Menu Item Order Interleaving

## Objectives
Fix menu order interleaving in `showPlayerPickerPopup` so that all playback devices/renderers (Group 0) are listed FIRST, and utility actions (Rescan & Bluetooth Setup) in Group 1 appear LAST at the bottom of the popup.

## Tasks
- [x] 1. Update `MainActivity.java`: Offset Group 1 utility item `order` values (`baseOrder = renderers.size() + 10`) so Android `MenuBuilder` sorts Group 1 items strictly after all Group 0 renderers.
- [x] 2. Verify compilation (`./gradlew compileDebugSources`).
- [x] 3. Document results in `tasks/todo.md`, update `DESIGN.md`, `CHANGELOG.md`, and `tasks/lessons.md`.

## Review & Results
- **Root Cause Identified**: Android `PopupMenu` / `MenuBuilder` sorts menu items globally by the `order` parameter across all groups. Because Group 0 renderers were assigned `order = 0, 1, 2...` and Group 1 utility items were assigned `order = 0, 1`, Android interleave-sorted Group 1 items between Group 0 items.
- **Fix Summary**: Offset Group 1 item order values to `baseOrder = (renderers.size() + 10)`. All playback target items now appear **FIRST**, followed by the divider line and utility actions at the very bottom.
- **Verification**: Verified compilation cleanly with `./gradlew compileDebugSources` (`BUILD SUCCESSFUL`).

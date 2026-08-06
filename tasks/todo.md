# Task Plan: Implement Comprehensive UX Enhancements for Audio Hub

## 1. Touch Targets & Accessibility
- [x] Expand transport controls (`Play/Pause`, `Next`, `Prev`, `Shuffle`, `Repeat`) in `sheet_now_playing_queue.xml` to `48dp x 48dp` minimum touch target size (`minWidth="48dp"`, `minHeight="48dp"` with centered 24dp icons).

## 2. Segmented Pill/Tab Navigation Control
- [x] Replace passive dot indicators in `sheet_audio_hub.xml` header with a modern `MaterialButtonToggleGroup` / pill tab switcher (`Playing` | `Signal` | `Server`).
- [x] Update `AudioHubBottomSheet.java` tab selection listeners and page change callbacks to synchronize with the toggle group seamlessly.

## 3. Artwork Gesture Visual Feedback
- [x] Implement visual feedback overlays (skip left/right animated arrows and play/pause ripple indicators) over album art during fling and double-tap gestures in `AudioHubBottomSheet.java`.

## 4. Queue Layout & Dynamic Sheet Elevation
- [x] Enhance layout structure in `sheet_audio_hub.xml` and `AudioHubBottomSheet.java` to optimize vertical real estate for queue tracks and ensure smooth bottom sheet handling.

## 5. Verification & Commit
- [x] Verify build with `./gradlew assembleRoomDebug`.
- [x] Update `CHANGELOG.md` & `tasks/lessons.md`.
- [x] Commit code with detailed commit message.

## Review
- All 4 UX recommendations fully implemented and compiled cleanly (`BUILD SUCCESSFUL in 9s`).




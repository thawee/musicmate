# Plan: 3 Pro-Refinements for Music Center Hub

## Overview
Implement 3 high-value UI/UX refinements in `AudioHubBottomSheet`:
1. "Jump to Now Playing" quick-scroll button in Queue tab header.
2. Vertical centering & 1-Tap "Copy Server URL" button in Server tab.
3. Interactive Audio Route Path telemetry hint on Playback tab.

---

## Tasks Checklist

- [x] **Phase 1: Bluetooth Device in Player Selection Picker**
  - [x] Update `MainActivity.showPlayerPickerPopup()` to detect connected Bluetooth audio output via `AudioOutputHelper`.
  - [x] Enhance `Local Player` menu item label/icon in picker to show connected Bluetooth device (e.g., `🎧 Sony WH-1000XM4 (BT • LDAC)`).
  - [x] Ensure selecting the Bluetooth / Local target routes audio via `playbackService.switchPlayer(localTarget)` and updates UI.

- [x] **Phase 2: "Play Next" Functionality Fix**
  - [x] Enhance `QueueManager.addPlayNext(Track song)` to prevent duplicates by removing existing track instances before inserting at `currentIndex + 1`.
  - [x] Uncomment `R.id.action_play_next` menu item in `menu_main_actionmode.xml`.
  - [x] Uncomment and wire `R.id.action_play_next` action handling in `MainActivity.java` `onActionItemClicked`.
  - [x] Refresh `AudioHubBottomSheet` queue list after calling `addPlayNext`.

- [x] **Phase 3: Verification & Git Commit**
  - [x] Verify build with `./gradlew assembleDebug`.
  - [x] Commit changes to Git.

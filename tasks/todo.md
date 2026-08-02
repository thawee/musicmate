# Unified Controller UX Implementation Plan (DLNA & Local Players)

Enhance MusicMate with a persistent, unified Floating Playback Bar, instant single-tap song playback, a top-header Quick-Cast button, and robust queue management across both DLNA Renderers and Local Android Players.

---

### Task 1: Single-Tap Playback vs. Metadata Edit Split
- [x] Modify track item click listener in `MainActivity.java` / `MusicTagAdapter.java`:
  - **Single Tap on Song Row**: Instantly calls `playbackService.playSong(track)` to play/cast on the active target (DLNA / Local) and populate `QueueManager`.
  - **Tap Cover Art / Edit Icon**: Opens `TagsActivity` metadata editor.

---

### Task 2: Unified Floating Playback Bar (`layout_floating_playback_bar.xml`)
- [x] Create `layout_floating_playback_bar.xml` featuring:
  - Album Art thumbnail & 2-line text (**Song Title**, **Artist Name** • **Target Player Badge**).
  - Transport Controls: Skip Previous (`⏮️`), Play/Pause Toggle (`⏯️`), Skip Next (`⏭️`).
- [x] Embed the layout into `activity_main.xml` immediately above `bottom_navigation_container`.
- [x] Implement Display/Hide rules in `MainActivity.java`:
  - **Show**: When `PlaybackState` is `PLAYING` or `PAUSED` (or track loaded).
  - **Hide**: When playback is `STOPPED`/Idle or during Multi-Select Action Mode.

---

### Task 3: Bottom Navigation Bar Dual-Layer Integration
- [x] Update `navigation_now_playing` in `activity_main.xml`:
  - Displays real-time **Signal Path Quality Summary** (e.g. `FLAC 352.8kHz/24bit • Bit-Perfect`).
  - Tapping opens `SignalPathBottomSheet` for technical DAC and signal path details.

---

### Task 4: Top-Header Quick-Cast Icon
- [x] Add a prominent **Cast Icon (`ic_cast`)** on `header_panel` in `activity_main.xml`.
- [x] Bind tap listener to open `MediaServerManagementSheet` for 1-tap renderer selection.

---

### Task 5: Queue Management UI (Purge & Add to Queue)
- [x] Add **Clear Queue (`🗑️`)** action in Now Playing / Queue Sheet (`queueManager.emptyPlayingQueue()`).
- [x] Add **Add to Queue (`➕`)** & **Play Next (`▶️`)** buttons to multi-selection Action Mode in `MainActivity.java`.

---

### Task 6: Build Verification & Documentation Updates
- [x] Run full compilation `./gradlew :app:compileNioRoomDebugJavaWithJavac`.
- [x] Update `USER_GUIDE.md`, `NETWORK_RESILIENCE.md`, and `CHANGELOG.md`.

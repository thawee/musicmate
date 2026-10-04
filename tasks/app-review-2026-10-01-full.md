# Full functional and UX review - 2026-10-01

Static code review of HEAD `4fc8eb34`. Four areas were reviewed in parallel; findings marked "Verified" were re-read by the lead reviewer. No device was connected, so nothing here is reproduced at runtime.

Still open from earlier reviews: Convert Format is missing from the track overflow menu, and its playback actions stay enabled with no service.

## P1 - functional bugs

| # | Area | Finding | Location | Status |
| --- | --- | --- | --- | --- |
| P1-1 | Queue | Removing the playing track skips the following track. Both indices then point at B, and "next" returns C. | `QueueManager.java:818-830` | Verified |
| P1-2 | Playback | Notification, lock-screen and headset Next/Prev don't use the queue on local playback. The `MediaLibrarySession` wraps a single-item ExoPlayer with an empty callback. | `MusicMateServiceImpl.java:482` | Verified (Media3 behavior inferred) |
| P1-3 | Playback | Stale DLNA gapless preload plays after a queue edit or Repeat change, and `setPlaybackTrack` re-adds the removed track. | `MusicMateServiceImpl.java:1476-1504`, `QueueManager.java:555-563` | Reported |
| P1-4 | Playback | Switching target while paused leaves Play sending `resume` to a renderer with nothing loaded. The UI shows PLAYING, then the fallback skips. | `MusicMateServiceImpl.java:1220,1228` | Reported |
| P1-5 | Queue | With shuffle on, every queue edit reshuffles, so Play Next lands randomly and played tracks reappear. | `QueueManager.java:381-396,701-735` | Reported |
| P1-6 | Playback | Sleep "end of track" pauses on a manual Next, and is ignored on DLNA gapless transitions. | `MusicMateServiceImpl.java:741,1395-1419` | Reported |
| P1-7 | Server | Stop does not stick. The network callback restarts an IDLE hub, and Stop during STARTING is ignored. | `MediaServerHubImpl.java:308-320,569-582` | Verified |
| P1-8 | Server | UI reports RUNNING right after an async `start()`. Failures and Wi-Fi-loss stops never reach `statusLiveData`. | `MusicMateServiceImpl.java:586-595` | Reported |
| P1-9 | Settings | Changing the server engine creates a fresh `MediaServerManager` and calls `restartServer()` before the bind completes, so nothing restarts. | `SettingsActivity.kt:60-66` | Verified |
| P1-10 | Tags | Organize uses and persists unsaved edits without Save. If DR analysis fails, the DB and the file diverge. | `TagsActivity.java:1840-1876`, `FileRepository.java:629-646` | Reported |
| P1-11 | Tags | The `writeTagToFile` result is ignored, so the DB is updated and "Success" is reported even when the file write fails. | `FileOperationTask.java:258-268` | Verified |
| P1-12 | Tags | Search & Match overwrites the folder `Cover.jpg` before Save, so Discard cannot undo it. A partial download truncates the original. | `TagsActivity.java:918-925`, `MusicBrainzClient.java:293` | Verified |
| P1-13 | Library | Query exceptions are swallowed into an empty list, so the user sees "No tracks" instead of the error and Retry state. | `TagRepository.java:422-428` | Verified |
| P1-14 | Library | Paged append has no dedupe and `ORDER BY title` has no tiebreaker, so a scan during paging can produce a duplicate LazyColumn key and crash. | `MainViewModel.kt:134`, `MusicListScreen.kt:272` | Verified (append), crash path reported |

## P2 - UX defects

| # | Area | Finding | Location |
| --- | --- | --- | --- |
| P2-1 | Playback | The sleep-timer chip never counts down or clears after it fires. | `MainActivity.java:1158-1165` |
| P2-2 | Playback | DMR volume assumes 50, so the sleep fade can get louder, and volume is not restored after the fade. | `MusicMateServiceImpl.java:1037-1104` |
| P2-3 | Scan | Every process start cancels any running scan and starts an incremental one, replacing a pending full rescan. | `MusixMateApp.java:69,99-110` |
| P2-4 | Server | The media server auto-starts on first launch (default true) without the user opting in. | `MainActivity.java:363-366` |
| P2-5 | Scan | A FAILED scan looks the same as success, and an ENQUEUED (storage-blocked) scan shows nothing. | `MainActivity.java:462-478` |
| P2-6 | Tags | Extract and Remove embedded art always report success. Remove is destructive with no confirm. Extract silently overwrites `Cover.jpg`. | `TagsActivity.java:1348-1386` |
| P2-7 | Files | Batch Delete, Move and Convert dismiss the dialog on completion, hiding per-file failures. Tag Delete calls `finish()` even when every file failed. | `MainActivity.java:1676-1818`, `TagsActivity.java:1828` |
| P2-8 | Files | Convert drops embedded art (`-vn`) and does not re-embed it. | `FFMpegHelper.java:213-234` |
| P2-9 | Tags | A picked cover applies only to tracks in the displayed track's folder, but the toast still says "Saved N". | `TagsActivity.java:1418-1428` |
| P2-10 | Library | The fast-scroll thumb uses a stale `thumbOffsetPx`/`totalItems`, so dragging and the thumb position are wrong after paging. | `FastScrollbar.kt:72-84,136-148` |
| P2-11 | Library | Tapping artwork during selection mode replaces the queue instead of toggling selection. | `MainActivity.java:2005-2012` |
| P2-12 | Library | Folder Play/Queue is silent when unbound and toasts success before async work that may fail. | `MainActivity.java:2030-2042` |
| P2-13 | Library | The scroll position carries over when drilling into a collection and on Back. | `MusicListScreen.kt:225` |

## P3 - polish

- After granting storage access, the pending scan is lost. `MainActivity.java:1525`, `PermissionScreen.kt:96-118`.
- The drilled-in collection, search and filter are lost on font-scale or theme change and on process death, because there is no `onSaveInstanceState`.
- Folder row buttons are 36dp with generic labels. Track row a11y labels ignore Listener/Curator tap modes. `FolderListItem.kt:133,144`, `TrackListItem.kt:104`.
- `MySelectionTracker.java` is dead code.

## Suggested fix order

1. Data integrity: P1-11, P1-12, P1-10, P2-6, P2-7.
2. Queue correctness: P1-1, P1-5, P1-3, P1-6.
3. Control surfaces: P1-2, P1-4.
4. Server trust: P1-7, P1-8, P1-9, P2-4.
5. Library resilience: P1-13, P1-14, then the P2 library items.

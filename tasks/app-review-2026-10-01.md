# App functional and UX review - 2026-10-01

Delta review against `tasks/app-review-2026-09-30.md`, covering commits `a0965f3d`..`6bfc033f` (3.20.2) and the uncommitted worker edits.

## Findings

| # | Pri | Status | Finding |
| --- | --- | --- | --- |
| 1 | P0 | New | HEAD (3.20.2) does not compile without the uncommitted worker edits |
| 2 | P1 | Open, now committed | Natural completion bypasses Repeat One |
| 3 | P2 | New | Tracks without embedded art re-run extraction on every cover load |
| 4 | P2 | Open | Convert Format missing from track overflow menu |
| 5 | P2 | Open | Playback menu actions enabled while service unavailable |
| 6 | P2 | Likely fixed | Metadata clipping at 200% text (now `FlowRow`); re-render preview to confirm |

### 1. [P0] Released commit breaks the app build
- `e06283db` changed `FileRepository.isManagedInLibrary(Track)` to `static isManagedInLibrary(Context, Track)` (`core/.../FileRepository.java:235`) but left the app call sites on the old instance method: `FileOperationTask.java:218,265`, `ScanAudioFileWorker.java:132`.
- The uncommitted edits fix it. Commit them before tagging or building 3.20.2 from a clean checkout.

### 2. [P1] Repeat One ignored on natural track end
- `e06283db` committed the change from yesterday's finding 1. `onPlaybackCompleted()` (`MusicMateServiceImpl.java:189`) calls `skipToNextInQueue()`, which uses `getNextTrack(true)` (`:747`, `:766`), so it skips the Repeat One check.
- Gapless preload and the fallback path (`:1105`, `:1429`, `:1468`) still use `getNextTrack()`, which honours Repeat One. On a DLNA renderer this preloads the current track while the completion path advances the queue, so the queue and what plays disagree.
- Fix: split `skipToNextInQueue(boolean userInitiated)`. Completion passes `false`. Intent and UI Next pass `true`. History should record `end(true)` only when the user skipped.

### 3. [P2] Lazy embedded-art extraction has no negative cache
- `FileRepository.getCoverArt()` (`:174`) calls `extractEmbedCoverArt()` whenever the cover file is missing. For a file with no embedded picture it returns `null` and writes nothing, so every later Coil fetch (`CoverartFetcher.java:56`) and `TagsTechnicalPage.kt:93` opens a new `MediaMetadataRetriever`. Coil does not cache failures, so scrolling a large art-less library pays this I/O on every bind.
- For managed tracks, a read path now writes `Cover.jpg` into the user's music folder as a side effect.
- Fix: keep a no-art marker (for example `DEFAULT_COVERART`, or an in-memory set keyed by path) after a failed extraction. Consider limiting folder writes to scan or explicit actions.

### 4. [P2] Convert Format missing from the single-track menu
- `TrackListItem.kt:253-287` still offers only Play Now, Play Next, Add to Queue and Open With. `action_encoding_file` is handled but cannot be reached from this menu.

### 5. [P2] Playback actions enabled with no service
- The menu items have no `enabled` state. When the service is unbound, the actions silently do nothing (yesterday's finding 4, unchanged).

## Validation
| Check | Result |
| --- | --- |
| `:app:assembleDebug` (working tree) | Pass |
| `:core:testDebugUnitTest`, `:app:testDebugUnitTest` | Pass |
| HEAD-only build | Not run; inferred from the diff (removed method still referenced) |
| Screenshot, lint, device | Not re-run; see 2026-09-30 review |

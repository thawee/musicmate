# App functional review (delta) - 2026-10-01

Covers HEAD `c78e2e54` ("Protect tags and cover art from unsaved or failed writes") and the uncommitted `gradle/libs.versions.toml` bump. Baseline: `tasks/app-review-2026-10-01-full.md` (HEAD `4fc8eb34`). Static review only; no device run.

## Fixed in c78e2e54 (verified by reading the code)

| ID | Result |
| --- | --- |
| P1-10 | Organize asks to save first. Both save paths run the follow-up only when every item saved (`TagsActivity.java:1216-1280`, `TagsEditorFragment.kt:259-300`). |
| P1-11 | Measure DR reports "Failed" and skips the DB save when `writeTagToFile` fails (`FileOperationTask.java:264-267`). |
| P1-12 | Search & Match stages the cover through `stageArtwork`. `Cover.jpg` changes only on Save. Downloads go to a `.part` file and are renamed into place. |
| P2-6 | Remove asks for confirmation. Extract asks before replacing an existing `Cover.jpg`. Both report real and partial counts. Extraction no longer clobbers a cover when the file has no art. |
| P2-7 | Batch Delete, Move and Convert show a failure summary. Tag Delete stays open when nothing was removed. Caveat: N1 below. |

## New findings

| # | Pri | Finding | Location |
| --- | --- | --- | --- |
| N1 | Fixed | The batch completion race makes the new failure summary unreliable. Each worker increments `count` before posting its status, then checks `count.get() == size`. A worker that has incremented but not yet posted can lose to a faster worker that reaches `size` and posts `onComplete` first. Its "Failed" then lands after the summary, so the summary is missing it. Two workers can also both see `size`, so `onComplete` runs twice and you get two dialogs, two `loadMusicItems()` calls, and in the tag editor two `finish()`/dialog paths. | `FileOperationTask.java:84-101` (delete), `:121-138` (move); same pattern in `measureDR` `:260-288` |
| N2 | Resolved (bytecode patch, see lessons.md) | The uncommitted `httpcore5` 5.5-beta2 to beta3 bump is inconsistent with the vendored `SingleCoreIOReactor` (copied from beta2, 7-arg constructor). In beta3, `DefaultConnectingIOReactor` calls a new 8-arg constructor that takes an `IOFunction` socket factory, which would throw `NoSuchMethodError` if a connecting reactor is ever created. `DefaultListeningIOReactor` still uses the 7-arg constructor, and no project code builds a requester, so the server path should still work. Re-vendor from beta3 sources, or stay on beta2. | `server-jupnp-httpcore/src/main/java/org/apache/hc/core5/reactor/SingleCoreIOReactor.java:84`, `gradle/libs.versions.toml:113-114` |
| N3 | Fixed | When the Measure DR tag write fails, the analysed DR values stay on the in-memory `Track` shared with the list. The UI shows unsaved values until the next reload. | `FileOperationTask.java:262-267` |
| N4 | Fixed | The Javadoc "Delete multiple media files" now sits above `isFailureStatus` instead of `deleteFiles`. | `FileOperationTask.java:64-70` |
| N5 | Fixed | Local gapless: after an automatic ExoPlayer transition the playlist is `[previous, current]`, and the next preload called `removeMediaItem(1)`, removing the playing track. `setNextTrack` now works relative to `getCurrentMediaItemIndex()` and drops finished items. Not reproduced on a device. | `AndroidPlayerController.java:390` |

Fix for N1: post the terminal status first, then use a separate counter for completion. Call `if (done.incrementAndGet() == size) callback.onComplete();` from a `finally` block, so completion runs exactly once and after every status has been posted.

## Still open from the full review

`c78e2e54` does not touch the queue, the service, the server hub, settings or the library code, so these are unchanged:

- P1-1 (re-read: `QueueManager.java:818-830`, still decrements only when `position < index`), P1-2 to P1-9, P1-13, P1-14.
- P2-1 to P2-5, P2-8 (Convert still uses `-vn`), P2-9 (`applyArtworkToTrack` is still folder-scoped, `TagsViewModel.kt:81-87`), P2-10 to P2-13.
- Convert Format missing from the track overflow menu. Playback actions enabled with no service.

## Suggested next steps

1. N1 (small, and the P2-7 fix depends on it).
2. Resolve N2 before committing the version bump. Build and smoke-test DLNA discovery and streaming on a device.
3. Queue correctness: P1-1, P1-5, P1-3, P1-6.
4. Control surfaces: P1-2, P1-4. Then server trust: P1-7, P1-8, P1-9.

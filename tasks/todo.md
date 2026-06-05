# Task Plan: Clean Up & Enhance Notifications

## Todo List
- [x] Implement a helper method in `MediaNotificationBuilder` to create a `PendingIntent` pointing to `MainActivity`.
- [x] Add `.setContentIntent(pendingIntent)` to all active notification states.
- [x] Make design decision regarding `MediaStyle` (Decided to keep it for best UX).
- [x] Clean up dead code in `MediaNotificationBuilder.java` and `MusicMateServiceImpl.java`.
- [x] Verify that the code compiles successfully with Gradle (Build succeeded!).

# Task Plan: Enable Auto-Scroll by Default in Android UI

## Todo List
- [x] Modify [Settings.java](file:///Users/thawee.p/Workspaces/github/musicmate/core/src/main/java/apincer/android/mmate/service/MediaNotificationBuilder.java) to set the default value of `isListFollowNowPlaying` to `true`.
- [x] Modify [settings.xml](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/res/xml/settings.xml) to set the `android:defaultValue` of `"preference_list_follows_now_playing"` to `true`.
- [x] Compile and verify the build runs successfully.

# Task Plan: Fix Auto-Scroll Race Condition for Songs Beyond 500

## Todo List
- [x] Modify `scrollToSong(Track)` in [MainActivity.java](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/ui/MainActivity.java) to wrap the scroll operation in `mRecyclerView.post(...)`. This ensures it runs after the LiveData observer's posted `adapter.setMusicTags(musicTags)` runnable, resolving the race condition when songs are loaded dynamically from page 2+.
- [x] Compile and verify the build runs successfully.

# Task Plan: Review TagsActivity & Fragments

## Todo List
- [x] Read and analyze [TagsActivity.java](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/ui/TagsActivity.java) to understand the host activity structure.
- [x] Read and analyze [TagsEditorFragment.java](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/ui/TagsEditorFragment.java) to see the main tag editing interface.
- [x] Read and analyze [TagsTechnicalFragment.java](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/ui/TagsTechnicalFragment.java) to see technical audio details display.
- [x] De-duplicate progress dialog handling in [TagsTechnicalFragment.java](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/ui/TagsTechnicalFragment.java) by routing it to the parent `TagsActivity` progress dialog (preventing dialog leaks).
- [x] Compile and verify the build runs successfully.

# Task Plan: Refactor ScanAudioFileWorker for Robustness & Efficiency

## Todo List
- [x] Read and analyze [ScanAudioFileWorker.java](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/worker/ScanAudioFileWorker.java) to evaluate its efficiency and logic.
- [x] Inspect how it is scheduled/triggered in [MusixMateApp.java](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/MusixMateApp.java) or [MainActivity.java](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/ui/MainActivity.java).
- [x] Refactor `processPaths` in `ScanAudioFileWorker.java` to use `CountDownLatch` for waiting on asynchronous batch scans before returning from `doWork()`.
- [x] Refactor `search` in `ScanAudioFileWorker.java` to run sequential stream walk directly on the background worker thread, removing the redundant `ForkJoinPool`.
- [x] Refactor `doWork()` in `ScanAudioFileWorker.java` to execute `deepScan()` synchronously (or block on it) to guarantee it completes before `doWork()` returns.
- [x] Compile and verify the codebase compiles successfully.

## Review Notes & Results
1. **CountDownLatch Integration**: Correctly blocks `doWork()` inside the `processPaths()` method during batch scanning using `CountDownLatch`. This ensures that WorkManager knows when the worker is actually done, preventing premature shutdown and database state corruption.
2. **Redundant ForkJoinPool Removed**: Removed the custom `ForkJoinPool` overhead from `search()` and let the sequential `Files.walk` run on the parent thread.
3. **Synchronous deepScan()**: Configured `deepScan()` to run synchronously on the worker's execution thread instead of offloading to low-priority background thread pool asynchronously.
4. **Cancellation Responsiveness**: Added checks for `isStopped()` inside the loops in `doWork()`, `processPaths()`, and `deepScan()` to immediately halt operations if cancelled or requested to stop by the system.
5. **Gradle Compile Verification**: Verified compile success with `./gradlew compileDebugSources`.

# Task Plan: Show Scan Progress Indicator in MainActivity

## Todo List
- [x] Refactor `processPaths` in `ScanAudioFileWorker.java` to compute progress and publish it using `setProgressAsync()`.
- [x] Add `WorkManager` and `WorkInfo` imports to `MainActivity.java`.
- [x] Set up WorkManager unique work LiveData observer in `MainActivity.java` inside `setupObserveViewModel()` to control `swipeRefreshLayout` refreshing state automatically.
- [x] Compile and verify the build runs successfully.

# Task Plan: Redesign Scan Progress as Bouncing Dots in MainActivity

## Todo List
- [x] Create circular dot shape resource `shape_dot_indicator.xml`.
- [x] Integrate `scan_progress_dots` LinearLayout (containing three Views) in `activity_main.xml` below the search bar, on the far right.
- [x] Add top and bottom padding to the layout to prevent animation clipping.
- [x] Declare `scanProgressDots` View and `dotAnimator` AnimatorSet in `MainActivity.java` and initialize them in `onCreate()`.
- [x] Implement programmatic bouncing animation using translationY `ObjectAnimator`s with sequential start delays.
- [x] Update WorkManager observer to trigger the dot layout visibility and animation instead of SwipeRefreshLayout refreshing.
- [x] Compile and verify the build runs successfully.

## Review Notes & Results
1. **Premium Bouncing Dots Loader**: Replaced the full-screen circle overlay (`SwipeRefreshLayout` animation) with a modern, inline bouncing 3-dot ellipsis indicator, positioned neatly below the search bar on the far right.
2. **Animation Implementation**: Programmed smooth, hardware-accelerated bouncing animations using `ObjectAnimator` with a `ValueAnimator.INFINITE` loop and staggered start delays (`150ms`, `300ms`) for a polished running dot effect.
3. **Clipping Prevention**: Added `8dp` vertical padding to the progress dot layout container, giving the dots enough space to translate vertically without clipping.
4. **Isolated UI Experience**: Isolated the background scan indicator from the pull-to-refresh swipe handler, allowing users to browse their existing music library smoothly while a background scan is active.
5. **Gradle Compile Verification**: Verified clean build via `./gradlew compileDebugSources`.

# Task Plan: Concurrency & Threading Audit

## Todo List
- [x] Implement progress update throttling in `ScanAudioFileWorker.java` to reduce WorkManager/Binder IPC overhead.
- [x] Add `stopDotAnimation()` to `onDestroy()` in `MainActivity.java` to prevent infinite animation memory leaks.
- [x] Compile and verify the build runs successfully.

## Review Notes & Results
1. **Memory Leak Prevention**: Added `stopDotAnimation()` to the `onDestroy()` lifecycle callback in `MainActivity.java`. This cancels the infinite looping view animators on target dot views when the activity is destroyed, preventing context-related memory leaks of the Activity object.
2. **Throttled Progress Reporting**: Throttled enqueued progress updates inside `ScanAudioFileWorker` (calling `setProgressAsync` only every 5 files or upon completion). This dramatically cuts down on redundant Binder transactions and system IPC calls, saving battery and CPU cycles.
3. **Thread Safety**: All concurrent operations use robust constructs (`CountDownLatch`, thread-safe `AtomicInteger`, and main-thread serialization of UI operations via LiveData and RecyclerView/UI handlers).
4. **Successful Build**: Verified clean compilation via `./gradlew compileDebugSources`.
5. **Animation Speed Optimization**: Slowed down the bouncing animation of the 3 progress dots by increasing the bounce duration to `1000ms` and staggered starting delays to `250ms` and `500ms`. This creates a much calmer, smoother, and higher-end visual effect in the header.

# Task Plan: Clean Up Tags From Filename Dialog Style

## Todo List
- [x] Remove hardcoded solid black dialog window background override in `TagsEditorFragment.java` to restore native Material rounded corners and styling for the free text input dialog.
- [x] Compile and verify the build runs successfully.

## Review Notes & Results
1. **Themed Custom Text Dialog**: Removed the hardcoded `dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));` override inside `TagsEditorFragment.java`. Now, the "free text" input sub-dialog correctly inherits the parent `R.style.AlertDialogTheme` properties, displaying with a beautiful semi-transparent frosted-glass/dark surface, Material 3 shadow elevation, and rounded corners matching the rest of the application.
2. **Parsing Integrity**: Verified that filename-to-tag parsing logic and tag containers function exactly as expected.
3. **Successful Compilation**: Verified clean builds with `./gradlew compileDebugSources`.











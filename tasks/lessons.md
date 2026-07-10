# Lessons Learned: MusicMate Notification & Scroll Refactoring

## Learnings & Patterns
- **No MediaSession by Intention**: When working with apps that act as headless receivers/controllers (e.g. casting to DLNA) and do not play audio locally, the lack of `MediaSession` is an intentional architectural pattern to prevent the service from competing for system audio focus or hijacking Bluetooth/wired headphone buttons.
- **Differentiate UI Scope (WebUI vs. Native UI)**: Before implementing UI features (like list auto-scrolling) in hybrid apps, clarify if the target is the Android Native UI or the WebUI to avoid redundant changes.
- **Asynchronous Adapter Layout Race Condition**: When using pagination in a `RecyclerView` (e.g. loading pages of 500 items), calling `adapter.getMusicTagPosition(item)` right after updating a LiveData will return `NO_POSITION` if the layout/setMusicTags step is posted asynchronously. Wrapping the lookup and scroll in `recyclerView.post(...)` ensures it runs after the adapter is updated.
- **Avoid Dynamic Button Labels**: Dynamic button text (e.g., changing from "Import" to "Analyze" based on the track's management state) creates inconsistent UI and confuses the user. Prefer a stable, clear label like "Organize" that matches both contexts elegantly.
- **Cache-First Lookup in Hot Paths**: When resolving media resources (e.g., cover art) in hot paths like list scrolling or image loading, always check for pre-resolved cache keys/files first in $O(1)$ time. Synchronously scanning folders (`listFiles()`) for every bound item causes severe disk I/O bottlenecks and UI lag.
- **SelectionTracker Click Interception**: Setting an explicit `OnClickListener` on viewholder item roots overrides standard short-click selection behavior in `SelectionTracker` and launches default click activities concurrently. Inside the click listener, check if selection is active using `tracker.hasSelection()` and toggle the key selection programmatically.
- **Dismissing Contextual Action Mode**: A `SelectionObserver` should automatically dismiss the active `ActionMode` using `actionMode.finish()` when the tracker's selection count reaches `0`.
- **RecyclerView Disabling Anti-Pattern**: Dynamically calling `setEnabled(false)` on a `RecyclerView` while scrolling disrupts touch dispatch loops, leading to abrupt halts, touch-out-of-sync events, and accidental item clicks once enabled.
- **Scroll-Stopping Touch Selection Guard**: When the user touches a moving/flinging list to stop it, the transition to idle happens instantly. A subsequent long-press or tap can get erroneously registered as selection. By using a `SimpleOnItemTouchListener` to intercept the `ACTION_DOWN` event when the list is not idle, we can set an `isScrollStoppingTouch` flag to block selection during that touch cycle.

- **Filtered List Subtitle Counts**: When displaying total item counts, storage size, and duration in header/subtitle views while a local filter (like search or category filter) is active, prioritize the adapter's filtered dataset properties (`getTotalItems()`, `getTotalSize()`, `getTotalDuration()`) over database-wide category statistics, preventing inconsistent subtitles (e.g. showing category totals in a filtered list).
- **Directory Image Loader Safeguard**: When resolving cover art resource paths, verify if the resolved path is a directory (using `file.isDirectory()`). Passing directory paths to image loading frameworks (like Coil) causes silent decode failures or broken images; instead, filter directories out and fallback to the default/missing cover image.

## Actionable Rules for Future Changes
- Always ask/confirm if the app is designed to run headlessly or casting-only before suggesting standard local playback components (like `MediaSession`).
- Confirm the target screen/view (Native vs. WebUI) when implementing UI enhancement requests.
- Always use `view.post(...)` for coordinate-based or position-based operations on lists (like `RecyclerView`) that rely on recently updated adapter datasets.
- Ensure image loaders and resource retrievers resolve cached paths first and bypass directory scanning/listing functions unless cached lookups fail.
- Differentiate between requests to review flow logic versus requests to relocate or reorganize layout elements (e.g., moving items between side menus), and verify with the user before performing layout changes.
- Keep main action bar buttons stable in naming and placement; do not dynamically change primary action button text unless explicitly requested.
- For music library/audiophile tools, "Organize" is a premium and natural term that covers both file placement (importing) and audio analysis.
- When using `SelectionTracker`, always ensure that the item's standard click listener checks `tracker.hasSelection()` first to toggle selection rather than firing the default action (like opening edit details).
- Automatically finish the contextual `ActionMode` inside selection observers when selection count reaches zero.
- Never disable or enable `RecyclerView` dynamically using `setEnabled` during scroll states.
- Block item click and selection states using a touch interceptor flag (`isScrollStoppingTouch`) when the first `ACTION_DOWN` of a touch event starts during a scroll/fling state.
- Ensure that any UI header subtitle updates check if a filter is active (`adapter.hasFilter()`) and pull counts directly from the adapter instead of database statistics.
- Safeguard all image-fetching helpers by checking `covertFile.isDirectory()` before passing the file path to image loading libraries.
- **Thai Encoding Recovery & Performance**: When recovering garbled Thai text (TIS-620/Windows-874 misread as Latin-1), extract raw bytes using `ISO-8859-1` and decode using `windows-874`. Add a fast $O(N)$ check for high ASCII (`0xA1`-`0xFB`) to immediately bypass standard English/non-Thai text with zero allocations, avoiding massive CPU/GC overhead from failed conversion attempts.
- **Real-Time Interactive Previews**: In complex editors with multiple component builders (e.g. tag-from-filename parser with tag pills), register a change listener on the custom component to automatically refresh preview fields whenever items are added, removed, or dragged. This eliminates the need for manual "Preview" triggers and makes the UI feel smooth, alive, and responsive.
- **"More Actions" Menu Prioritization & Labeling**: Prioritize core/frequent user workflows (like metadata curation and tag search) at the top of contextual and overflow menus. Use active, descriptive, and premium labels (e.g., "Verify Lossless Quality" rather than technical internal project names like "MusicMate Spectra") to make technical tools feel high-end and accessible.

- **File Move/Rename Metadata Update**: When moving or renaming a file physically on disk (e.g. `Files.move`), all file operations referencing the old path (such as checking `lastModified()`) will fail or return `0` because the file no longer exists at the source. Always read properties like size and lastModified from the **new/target** file path to ensure database records are kept in sync and prevent false modifications from triggering rescans.

## Actionable Rules for Future Changes
- Always use `ISO_8859_1` to recover raw bytes from Latin-1 strings before decoding with standard target charsets (like `windows-874`).
- Implement low-overhead pre-screening checks (e.g. `hasHighAscii`) in metadata parsers to prevent wastefully invoking heavy converters/decoders on standard ASCII text.
- Connect direct event observers or change listeners on custom drag/drop tag components to automatically refresh dependent preview inputs in real-time, matching modern interactive UX patterns.
- Contextual/overflow menus (like the "More Actions" tag menu) must sort tasks by frequency of use: primary curation/editing tasks first, followed by correction utilities, and technical inspection/lookup tools last. Label menu items using active, user-centric verbs rather than technical or library-internal titles.
- When performing file movements, renames, or format conversions, always query metadata properties (like `lastModified` or `fileSize`) from the **new target file** instance, never the source file instance.





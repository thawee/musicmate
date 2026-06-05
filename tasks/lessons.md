# Lessons Learned: MusicMate Notification & Scroll Refactoring

## Learnings & Patterns
- **No MediaSession by Intention**: When working with apps that act as headless receivers/controllers (e.g. casting to DLNA) and do not play audio locally, the lack of `MediaSession` is an intentional architectural pattern to prevent the service from competing for system audio focus or hijacking Bluetooth/wired headphone buttons.
- **Differentiate UI Scope (WebUI vs. Native UI)**: Before implementing UI features (like list auto-scrolling) in hybrid apps, clarify if the target is the Android Native UI or the WebUI to avoid redundant changes.
- **Asynchronous Adapter Layout Race Condition**: When using pagination in a `RecyclerView` (e.g. loading pages of 500 items), calling `adapter.getMusicTagPosition(item)` right after updating a LiveData will return `NO_POSITION` if the layout/setMusicTags step is posted asynchronously. Wrapping the lookup and scroll in `recyclerView.post(...)` ensures it runs after the adapter is updated.

## Actionable Rules for Future Changes
- Always ask/confirm if the app is designed to run headlessly or casting-only before suggesting standard local playback components (like `MediaSession`).
- Confirm the target screen/view (Native vs. WebUI) when implementing UI enhancement requests.
- Always use `view.post(...)` for coordinate-based or position-based operations on lists (like `RecyclerView`) that rely on recently updated adapter datasets.

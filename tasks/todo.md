# Checklist: Category-wide Subtitle Stats UX Improvement

## Phase 1: Planning and Verification
- [x] Define the `SearchResultStats` data class in the `core` module to hold aggregate statistics (count, size, duration)
- [x] Extend the `DbHelper` interface and implement the aggregate statistics query in `OrmLiteHelper` using efficient SQLite COUNT/SUM/GROUP BY calculations
- [x] Update `TagRepository` to expose a method for fetching `SearchResultStats` for a given `SearchCriteria`
- [x] Expose `SearchResultStats` via a new LiveData in `MainViewModel` and fetch it asynchronously in the background when loading music items
- [x] Update `MainActivity` to observe the new LiveData and update the subtitle with full category statistics

## Phase 2: Create Data Model ✅
- [x] Created `core/src/main/java/apincer/music/core/model/SearchResultStats.java`

## Phase 3: Extend Database Layer ✅
- [x] Modify `core/src/main/java/apincer/music/core/repository/spi/DbHelper.java` – added `getSearchStats()` and `getSimilarSongsStats()`
- [x] Modify `db-ormlite/src/main/java/apincer/music/ormlite/OrmLiteHelper.java` – implemented aggregate queries per category

## Phase 4: Extend Repository & ViewModel ✅
- [x] `TagRepository.getSearchStats()` – handles PLAYLIST and DUPLICATE specially; delegates to DbHelper otherwise
- [x] `MainViewModel` – `_searchStats` LiveData; fetched async in `loadMusicItems()`; reset to null on each new search

## Phase 5: Update UI in MainActivity ✅
- [x] `setupObserveViewModel()` – observe `viewModel.searchStats` to refresh subtitle
- [x] `updateHeaderPanel(SearchResultStats)` – uses aggregate stats for count/size/duration; falls back to adapter if stats not yet available

## Phase 6: Verification ✅
- [x] `./gradlew compileDebugJavaWithJavac` → BUILD SUCCESSFUL in 4s (0 errors)
- [x] Committed as `015cf876`

## Review & Results
- **Accurate Category Totals**: The subtitle now shows the real total across *all* matched tracks in the category, not just the 500-item pagination chunk.
- **No Performance Regression**: Aggregation is done via a single `COUNT(*)/SUM(fileSize)/SUM(audioDuration)` SQL query — no Track objects are fully loaded.
- **Graceful Fallback**: While the async stats query runs, the subtitle shows the adapter's chunk-count (fast, immediate), then seamlessly updates to the full stats when ready.
- **Top-Level View Enhancement**: The all-songs/category listing now also shows total size + duration alongside the count.
- **All Categories Supported**: LIBRARY, ARTIST, GENRE, CODEC (all variants), PUBLISHER, SEARCH mode, DUPLICATE, PLAYLIST.

# Checklist: Category-wide Subtitle Stats UX Improvement

## Phase 1: Planning and Verification
- [ ] Define the `SearchResultStats` data class in the `core` module to hold aggregate statistics (count, size, duration)
- [ ] Extend the `DbHelper` interface and implement the aggregate statistics query in `OrmLiteHelper` using efficient SQLite COUNT/SUM/GROUP BY calculations
- [ ] Update `TagRepository` to expose a method for fetching `SearchResultStats` for a given `SearchCriteria`
- [ ] Expose `SearchResultStats` via a new LiveData in `MainViewModel` and fetch it asynchronously in the background when loading music items
- [ ] Update `MainActivity` to observe the new LiveData and update the subtitle with full category statistics

## Phase 2: Create Data Model
- [ ] Create `core/src/main/java/apincer/music/core/model/SearchResultStats.java`
  - Needs `totalCount`, `totalSize`, `totalDuration` fields and getters

## Phase 3: Extend Database Layer
- [ ] Modify `core/src/main/java/apincer/music/core/repository/spi/DbHelper.java`
  - Add `SearchResultStats getSearchStats(SearchCriteria criteria);`
- [ ] Modify `db-ormlite/src/main/java/apincer/music/ormlite/OrmLiteHelper.java`
  - Add helper to construct ORMLite `QueryBuilder<TrackEntity, ?>` from `SearchCriteria` (shared between track list query and stats query)
  - Implement `getSearchStats` using the shared query builder, querying the database using raw aggregates (`COUNT(*)`, `SUM(fileSize)`, `SUM(audioDuration)`)
  - Correctly handle grouping cases (like grouping by title/artist) by either aggregating over results or using subqueries

## Phase 4: Extend Repository & ViewModel
- [ ] Modify `core/src/main/java/apincer/music/core/repository/TagRepository.java`
  - Add `public SearchResultStats getSearchStats(SearchCriteria criteria)` method that delegates to `dbHelper`
- [ ] Modify `app/src/main/java/apincer/android/mmate/ui/viewmodel/MainViewModel.java`
  - Add `private final MutableLiveData<SearchResultStats> _searchStats = new MutableLiveData<>();`
  - Add public `LiveData<SearchResultStats> searchStats = _searchStats;`
  - In `loadMusicItems(SearchCriteria)`, clear/reset `_searchStats` (e.g. set to null/empty)
  - Execute the stats query asynchronously on the background thread and post value to `_searchStats`

## Phase 5: Update UI in MainActivity
- [ ] Modify `app/src/main/java/apincer/android/mmate/ui/MainActivity.java`
  - Observe `viewModel.searchStats`
  - Update `updateHeaderPanel()` to accept or use the observed `SearchResultStats` instead of reading totals from the adapter
  - Fall back to the adapter's totals if `SearchResultStats` is null (e.g. during loading) to maintain smooth UI states

## Phase 6: Verification
- [ ] Compile and build project using `./gradlew compileDebugJavaWithJavac`
- [ ] Verify that there are no compilation errors

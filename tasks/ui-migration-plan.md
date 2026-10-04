# Android Jetpack Compose UI Migration Plan

## Phase 1: Setup & Foundation (COMPLETED)
- [x] **Gradle Configuration:** Inject Kotlin 2.x Compose Compiler, Material 3, and Compose UI tooling dependencies into `libs.versions.toml` and `app/build.gradle`.
- [x] **Interop Architecture:** Establish `DialogInterop.kt` wrapper for safe `ComposeView` injection into legacy Java code.
- [x] **First Component Migration:** Rewrite `doScanDirectories()` (Discover Music Folders) from 150-line `view_action_directories.xml` to pure `@Composable` in `MusicFoldersDialog.kt`.

## Phase 2: Action Dialogs & Bottom Sheets
*Goal: Remove all inline `MaterialAlertDialogBuilder` boilerplate from `MainActivity.java` and `TagsActivity.java`.*
- [ ] **Action Dialogs (`MainActivity.java`)**
  - [x] Migrate `actionTrash()` (Move to Trash Dialog)
  - [x] Migrate `actionMove()` (Move to Music Dialog)
  - [x] Migrate `actionFormat()` (Encode/Convert Audio Format Dialog)
- [x] **`AudioHubBottomSheet.java` (In Progress)**
  - [x] Migrate Media Server Tab to Compose (`MediaServerPage.kt`).
  - [x] Migrate Queue Tab to Compose (`QueuePage.kt`).
  - [x] Rewrite `AudioHubBottomSheet` XML into a Compose `@OptIn(ExperimentalMaterial3Api::class) ModalBottomSheet`.
  - [x] Wrap with Interop class for `MainActivity` to call.

## Phase 3: Custom UI Widgets
*Goal: Replace custom Canvas-drawn Java Views with idiomatic declarative Canvas Compose equivalents.*
- [ ] **Data Visualization:**
  - [x] `QualityPieChartView.java` -> `QualityPieChart.kt`
  - [x] `DynamicRangeView.java` -> `DynamicRangeMeters.kt`
  - [x] `WaveformView.java` -> `Waveform.kt` (Unused Java deleted, Compose created)
- [ ] **Badges & Indicators:**
  - [x] `QualityIndicatorView`, `NewIndicatorView`, `RatingIndicatorView` -> `AudioBadges.kt`

## Phase 4: `TagsActivity` & Metadata Editors
*Goal: Convert the Tag Editing screens to Jetpack Compose for better state management.*
- [x] **`TagsTechnicalFragment.java`:** Convert the read-only technical specs layout (`fragment_tags_technical.xml`) to Compose.
- [x] **`TagsEditorFragment.java`:** Convert the ID3 tag text fields and cover art picker (`fragment_tags_editor.xml`) to Compose.

## Phase 5: The Final Boss (`MainActivity` List & Scaffold)
*Goal: Replace the God-Activity's `RecyclerView` and `ResideMenu`.*
- [x] **MusicTagAdapter:** Rewrite the complex `RecyclerView` adapter and `SelectionTracker` into a `LazyVerticalGrid` / `LazyColumn` with declarative `selected` states. (COMPLETED - Adapter fully excised from MainActivity)
- [x] **Navigation & Scaffold:** Replace the legacy `ResideMenu` with a standard Jetpack Compose Material 3 `ModalNavigationDrawer` and `Scaffold`. (COMPLETED - DrawerInterop created)
- [ ] **Empty State & Loading:** Convert `SmartSwipeRefreshLayout` and the empty list placeholder into Compose components.

## Rules of Engagement (Strangler Fig Pattern)
1. **Never rewrite the entire screen at once.**
2. **Build the `@Composable`, write the `Interop.kt` wrapper, replace the Java invocation, delete the XML.**
3. **Verify compilation and runtime UI parity after every single step.**

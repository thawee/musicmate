# Checklist: Remember State and Music List when returning to MainActivity

## Phase 1: Investigation & Planning
- [x] Analyze launcher callback `tagViewResultLauncher` in `MainActivity.java`.
- [x] Analyze back navigation and result configuration in `TagsActivity.java` and `TagsEditorFragment.java`.
- [x] Define a plan to set `RESULT_OK` on successful tag edits, skip reloading on cancel/back (i.e. `RESULT_CANCELED`), and preserve/restore scroll position on list refresh.

## Phase 2: Implementation
- [x] Add `isSaved` state tracking in `TagsActivity.java`.
- [x] Set `isSaved = true` in `TagsEditorFragment.java` upon successful tag save operation.
- [x] Override `finish()` in `TagsActivity.java` to set `setResult(RESULT_OK)` if `isSaved` is true.
- [x] Modify `tagViewResultLauncher` in `MainActivity.java` to only call `loadMusicItems` if `resultCode == RESULT_OK`.
- [x] Implement scroll position saving and restoring in `MainActivity.java` when `musicItems` LiveData is updated.

## Phase 3: Verification
- [x] Compile the project to verify successful build.
- [x] Document final results.

## Phase 4: Handle Paging (> 500 items) on Reload
- [x] Add `reloadMusicItems()` in `MainViewModel.java` to load all currently fetched items up to `Math.max(1, currentPage) * PAGE_SIZE` from database.
- [x] Correctly maintain `currentPage` and `isLastPage` state inside `reloadMusicItems()`.
- [x] Call `viewModel.reloadMusicItems()` in `tagViewResultLauncher` of `MainActivity.java` instead of `viewModel.loadMusicItems(...)`.
- [x] Compile and verify the build.

## Phase 5: Flow and Navigation Improvements
- [x] Fix wrong toggle group checked listener in `TagsActivity.java` (change from `editorToggleGroup` to `techToggleGroup` for `TagsTechnicalFragment`).
- [x] Prevent listener accumulation/memory leaks in `TagsActivity.java` by calling `clearOnButtonCheckedListeners()` on all toggle groups.
- [x] Decouple system Back key behavior in `TagsActivity.java` from scroll state, allowing Back to finish the screen immediately.
- [x] Compile and verify the build.

## Phase 6: Material 3 Button Color Harmonization
- [x] Remove hardcoded `app:iconTint="#FFD700"` from action buttons in `activity_tags.xml` to allow system default dynamic theme colors.
- [x] Compile and verify the build.

## Phase 7: Theme-Compliant UI Color Harmonization
- [x] Harmonize tab indicator and selected text colors in `activity_tags.xml` by replacing `#FFD700` with `?attr/colorPrimary`.
- [x] Remove hardcoded `app:boxStrokeColor="#FFD700"` and `app:hintTextColor="#FFD700"` on Title input layout in `fragment_editor_tags.xml` to align focused colors with other input fields.
- [x] Replace hardcoded `#FFD700` colors for section headers in `fragment_editor_tags.xml` and `fragment_editor_tech.xml` with theme attributes (`?attr/colorPrimary` or `?attr/colorSecondary`).
- [x] Compile and verify the build.

## Phase 8: Dialog Background Harmonization
- [ ] In `view_action_directories.xml`, replace the custom translucent background `@drawable/shape_bottom_frosted_panel` with `@drawable/bg_dialog_dark_blur` to match the background style of all other dialogs in the application.
- [ ] Compile and verify the build.

## Phase 8: Dialog Background Harmonization
- [x] In `view_action_directories.xml`, replace the custom translucent background `@drawable/shape_bottom_frosted_panel` with `@drawable/bg_dialog_dark_blur` to match the background style of all other dialogs in the application.
- [x] Compile and verify the build.

## Phase 9: Text Label Refinements
- [x] Correct the "System Credentials" header in `fragment_editor_tags.xml` to a music-appropriate title like "Release & Track Info".
- [x] Refine section headers in `fragment_editor_tags.xml` and `fragment_editor_tech.xml` to evoke a premium, music-focused tone (e.g. "Song Identity", "Audio Stream Diagnostics").
- [x] Define dynamic dialog string resources in `strings.xml` for single/multiple track removal confirmation: "Remove this track from your Library?" and "Remove these tracks from your Library?".
- [x] Update `move_to_trash` string resource value in `strings.xml` to "Remove Song", and update other occurrences of delete/trash strings to premium, collection-focused terminology.
- [x] Update `TagsActivity.java` to use the dynamic string resources for the delete bottom sheet dialog title.
- [x] Update `view_action_trash_bottom_sheet_dialog.xml` default header title text.
- [x] Compile and verify the build.


## Review & Results

### 1. Selective Reloading based on RESULT_OK
- **Changes in `TagsActivity`:** Tracked whether modifications (saves, moves, or deletions) occurred by maintaining an `isSaved` flag and exposing `setSaved(boolean)`.
- **Exiting `TagsActivity`:** Overrote `finish()` to set `setResult(AppCompatActivity.RESULT_OK)` when `isSaved` is true, while avoiding overwriting any explicit filter-redirect results from `doBackToMainActivity`.
- **Changes in `MainActivity`:** Modified the `tagViewResultLauncher` callback to reload the music items (`viewModel.loadMusicItems(...)`) **only** when the returned result code is `RESULT_OK`. Cancel/Back presses (which return `RESULT_CANCELED`) no longer trigger database reload.

### 2. Preserving Scroll Position on Updates
- **Changes in `MainActivity`:** Updated the `musicItems` LiveData observer to save the layout manager's state using `onSaveInstanceState()` before updating the adapter, and restore it with `onRestoreInstanceState(state)` immediately after. This prevents scroll position resets when updating the list.

### 3. Paging-Aware List Reload
- **Changes in `MainViewModel`:** Added a `reloadMusicItems()` method that reads the current `currentPage` and queries all page-loaded items from the database up to `Math.max(1, currentPage) * PAGE_SIZE`. It dynamically computes the correct `currentPage` and `isLastPage` flags based on the returned list size to preserve the paginated state without data loss.
- **Changes in `MainActivity`:** Updated the launcher callback so that if no filter redirection is requested, it invokes `viewModel.reloadMusicItems()` instead of resetting to page 0.

### 4. Toggle Group Listener Corrections
- **Listener Un-registration:** Added `clearOnButtonCheckedListeners()` to all `MaterialButtonToggleGroup` views in `TagsActivity.setupActionButtons()` before attaching new ones, preventing memory leaks and multiple-trigger issues.
- **Checked Target Correction:** Directed `addOnButtonCheckedListener` for the technical fragment info menu on `techToggleGroup` instead of `editorToggleGroup`, fixing the unresponsive buttons under the "Tech Info" tab.

### 5. Back Navigation Decoupling
- **Simplified Exit:** Modified `handleOnBackPressed()` to call `finish()` immediately regardless of scroll states, ensuring a standard, predictable back button navigation flow.

### 6. Material 3 Color Harmonization
- **Theme integration:** Removed all hardcoded `app:iconTint="#FFD700"` properties from the action buttons inside `activity_tags.xml`. This enables the Material 3 components to dynamically style and color themselves based on the user's active dynamic theme palette and night mode settings automatically.

### 7. Layout Color Compliance Updates
- **TabLayout Harmonization:** Replaced hardcoded gold indicator and text selection colors (`#FFD700`) in `activity_tags.xml` with `?attr/colorPrimary` to adhere to system dynamic themes.
- **Outlined TextInputLayout Alignment:** Removed custom box stroke and hint color overrides on the Title input box inside `fragment_editor_tags.xml` so that the Title field focuses with the same color scheme as the rest of the form fields.
- **Section Headers Styling:** Refactored all hardcoded gold header titles (e.g., "Primary Metadata", "Advanced Info", "Storage Intelligence", "Raw Metadata", "Engine Output") across the editor and technical fragment XML files to use `?attr/colorPrimary` for full layout consistency.

### 8. Dialog Background Harmonization
- **Unified Backgrounds:** Replaced the custom translucent background (`@drawable/shape_bottom_frosted_panel`) in `view_action_directories.xml` (Library Expansion dialog) with `@drawable/bg_dialog_dark_blur` (the 90% opaque frosted dark base). This makes all custom action dialogs in the application completely uniform in appearance and resolves readability issues.
- **Icon Tint Update:** Changed the hardcoded gold tint (`#FFD700`) of the header icon in `view_action_directories.xml` to `?attr/colorPrimary` to align with the rest of the app's headers.

### 9. Premium Copy & Dialog Refinements
- **Removal Confirmation Resources**: Added `remove_track_confirm_single` ("Remove this track from your Library?") and `remove_track_confirm_multiple` ("Remove these tracks from your Library?") string resources.
- **Dynamic Dialog Titles**: Refactored `doDeleteMediaItems()` in `TagsActivity.java` to fetch the removal confirmation text dynamically using the new resource strings.
- **Copy Polish for Audiophiles**: Updated and reworded all delete/trash operations across `strings.xml` to focus on collection/library management phrasing rather than system-level operations (e.g., changed "Delete" to "Remove", "Move to Trash" to "Remove Song", "Files to Delete" to "Tracks to Remove").
- **Layout Consistency**: Updated the default text of `bottom_sheet_title` in `view_action_trash_bottom_sheet_dialog.xml` to match the polished collection terminology.

### 10. Compilation Verification
- Ran `./gradlew :app:compileNioDebugJavaWithJavac` and successfully compiled the app and its subprojects.

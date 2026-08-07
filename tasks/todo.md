# MusicMate: Audiophile UI/UX Overhaul Plan

## Phase 1: The Dark, Premium Foundation
- [ ] **OLED Black Themes**: Adjust the global Material 3 theme to enforce true pitch-black backgrounds (`#000000` or `#050505`) for absolute contrast, instead of default dark grays.
- [ ] **Typography Audit**: Review text styles. Swap heavily bolded, large fonts for sleek, refined text weights. Emphasize tracking (letter spacing) and clean hierarchy.
- [ ] **Neon Accents (Subtle)**: Ensure primary accent colors (e.g., currently playing track, active states) are sharp, glowing "neon" tones that stand out brilliantly against the dark canvas without overwhelming the cover art.

## Phase 2: Restructuring the "File Manager" Flow
- [ ] **Redesign `view_list_item.xml` (The Track List)**:
    - Declutter the metadata row. Stop cramming DR, Bitrate, and Duration into tiny adjacent pills.
    - Implement a clean "right-aligned" metadata approach, or elegant dividers (e.g., `FLAC • 24/192 • DR12`).
    - Remove heavy card backgrounds from list items; let them breathe on the true black canvas.
- [ ] **Artist/Genre "Hero" Vibe (Future Architecture)**:
    - *Note:* While a full architecture change (like building a bespoke Artist Fragment) is large, we can immediately improve the visual hierarchy of the lists by stripping away the "folder" iconography and leaning into edge-to-edge cover arts and refined typography.

## Phase 3: Elevating Audiophile Metadata
- [x] Strip out chunky backgrounds from `view_quality_indicator.xml`, `view_dynamic_range_db.xml`, and `view_duration.xml`.
- [x] Strip out chunky backgrounds from `view_badge.xml` (used for codec and sample rate).
- [x] Consolidate spacing in `view_list_music_tag.xml` (and `view_list_item_compared.xml`) so metadata flows as a single, elegant string separated by subtle space.
- [x] Remove outer borders and heavy padding from `view_list_music_tag.xml` and `view_list_folder.xml` to remove the "spreadsheet card" vibe and allow edge-to-edge flow.
- [x] **Signal Path Refinement**: Review `sheet_audio_hub.xml` and `signal_path_step.xml`. Clean up the padding and typography so the diagnostic tools feel like high-end telemetry rather than debugging menus.
- [x] **Now Playing Bar Polish**: Ensure `layout_floating_playback_bar.xml` (and `bottom_navigation_container` in activities) acts as a seamless glass/dark overlay rather than a chunky bottom tab.

## Phase 4 & 5: Dialogs and Quick Actions (Completed)
- [x] Strip old styles and chunky dialog windows from `view_now_playing.xml`, `sheet_now_playing_queue.xml`, `dialog_text_input.xml`, and `dialog_item_list.xml`.
- [x] Add "Play" and "Enqueue" quick-action buttons directly to folder cards (`view_list_folder.xml`) replacing the old redundant chevron.

## Review & Verification
- [ ] Compile the app and review all changes side-by-side with the old UI.
- [ ] Ensure the app no longer feels like a "database" but rather a high-fidelity listening gallery.

## Phase 6: Deprecate & Remove OrmLite
- [x] **Remove `db-ormlite` Module**: Delete the `db-ormlite` directory and remove its entry from `settings.gradle`.
- [x] **Update Build Flavors**: Modify `app/build.gradle` to remove the `database` flavor dimension (or remove the `ormlite` product flavor) so the app builds exclusively with Room.
- [x] **Clean Up Dependency Injection**: Find and remove any Hilt/Dagger modules (e.g., `OrmLiteDatabaseModule`) that specifically provide `OrmLiteHelper`, ensuring only `RoomDbHelper` is provided.
- [x] **Verify Build - [ ] **Verify Build & Test** Test**: Run a clean build and verify that the app compiles and correctly accesses the database using Room.

# Task Plan: Implement Drag Handle Pill & Top-Right Close Icon on Action Dialogs & Bottom Sheets

> **Note**: Playback Control (`sheet_audio_hub.xml`, `view_action_signal_path_bottom_sheet.xml`, `view_action_server_management_bottom_sheet.xml`) remains 100% untouched.

## 1. Search & Match Query Dialog (`view_action_search_query_dialog.xml`)
- [x] Add top center drag handle pill (`bg_drag_handle_pill`).
- [x] Add top-right close icon (`btn_close_search_dialog`) in header with proper RelativeLayout constraints.
- [x] Bind close button in `TagsActivity.java` (`dismiss()`).
- [x] Remove redundant bottom `button_cancel` and divider line. Elevate `button_search` as full-width primary button.

## 2. Search Results Picker Dialog (`view_action_search_results_dialog.xml`)
- [x] Add top center drag handle pill (`bg_drag_handle_pill`).
- [x] Add top-right close icon (`btn_close_results_dialog`) in header with proper RelativeLayout constraints.
- [x] Bind close button in `TagsActivity.java` (`dismiss()`).
- [x] Remove redundant bottom `button_cancel` bar to give `ListView` maximum vertical scroll space.

## 3. Trash / Delete Confirmation Bottom Sheet (`view_action_trash_bottom_sheet_dialog.xml`)
- [x] Add top center drag handle pill (`bg_drag_handle_pill`).
- [x] Add top-right close icon (`btn_close_trash_sheet`) in header with proper RelativeLayout constraints.
- [x] Bind close button in `TagsActivity.java` (`dismiss()`).
- [x] Remove redundant bottom `button_cancel` and divider line. Elevate `button_move_to_trash` as full-width primary button with trash icon.

## 4. Custom Text Input Dialog (`dialog_text_input.xml` & `TagsEditorFragment.java`)
- [x] Add drag handle pill (`bg_drag_handle_pill`) and top-right close icon (`btn_close_input_dialog`) in header.
- [x] Update `TagsEditorFragment.java` to bind close button and streamline dialog layout.

## 5. Verification & Build
- [x] Verify build with `./gradlew assembleRoomDebug`.
- [x] Document results in `tasks/todo.md` and `tasks/lessons.md`.

## Review
- Rolled back all Playback Control / AudioHub files ([`sheet_audio_hub.xml`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/res/layout/sheet_audio_hub.xml), [`view_action_signal_path_bottom_sheet.xml`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/res/layout/view_action_signal_path_bottom_sheet.xml), [`view_action_server_management_bottom_sheet.xml`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/res/layout/view_action_server_management_bottom_sheet.xml), [`styles.xml`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/res/values/styles.xml)) to maintain 100% of existing Playback Control functionality.
- Successfully updated all action dialogs ([`view_action_search_query_dialog.xml`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/res/layout/view_action_search_query_dialog.xml), [`view_action_search_results_dialog.xml`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/res/layout/view_action_search_results_dialog.xml), [`view_action_trash_bottom_sheet_dialog.xml`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/res/layout/view_action_trash_bottom_sheet_dialog.xml), [`dialog_text_input.xml`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/res/layout/dialog_text_input.xml)) to feature the dedicated drag handle pill (`bg_drag_handle_pill`) and a unconstrained top-right close icon button (`44dp x 44dp`).
- Bound all close listeners in [`TagsActivity.java`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/ui/TagsActivity.java) and [`TagsEditorFragment.java`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/ui/TagsEditorFragment.java).
- Verified build using `./gradlew assembleRoomDebug` (`BUILD SUCCESSFUL in 7s`).












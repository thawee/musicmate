import re

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "r") as f:
    content = f.read()

def replace_method(content, method_name, title_id, icon_id, btn_text_id, action_call):
    start_idx = content.find(f'private void {method_name}(List<Track> selections)')
    if start_idx == -1:
        return content

    brace_count = 0
    end_idx = -1
    for i in range(start_idx, len(content)):
        if content[i] == '{':
            brace_count += 1
        elif content[i] == '}':
            brace_count -= 1
            if brace_count == 0:
                end_idx = i + 1
                break
    
    if end_idx != -1:
        new_method = f"""private void {method_name}(List<Track> selections) {{
        if (selections.isEmpty()) return;

        final AlertDialog[] alertHolder = new AlertDialog[1];
        apincer.android.mmate.ui.compose.ActionFilesState state = new apincer.android.mmate.ui.compose.ActionFilesState(selections);
        
        View cview = apincer.android.mmate.ui.compose.DialogInterop.createActionFilesDialogView(
            this,
            getString({title_id}),
            {icon_id},
            state,
            getString({btn_text_id}),
            () -> {{ if(alertHolder[0] != null) alertHolder[0].dismiss(); }},
            () -> {{ if(alertHolder[0] != null) alertHolder[0].dismiss(); }},
            () -> {{
                state.setBusy(true);
                state.setProgress(FileOperationTask.getInitialProgress(selections.size()));
                operationTask.{action_call}(getApplicationContext(), selections,
                        new FileOperationTask.ProgressCallback() {{
                            @Override
                            public void onProgress(Track tag, int progress, String status) {{
                                runOnUiThread(() -> {{
                                    state.updateStatus(tag, status);
                                    state.setProgress(progress);
                                    if ("Deleted".equalsIgnoreCase(status) && isPlaybackServiceBound && playbackService != null) {{
                                        playbackService.onTrackDeleted(tag);
                                    }}
                                }});
                            }}

                            @Override
                            public void onComplete() {{
                                runOnUiThread(() -> {{
                                    viewModel.loadMusicItems();
                                    state.setBusy(false);
                                    if(alertHolder[0] != null) alertHolder[0].dismiss();
                                }});
                            }}
                        }});
            }}
        );

        AlertDialog alert = new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setTitle("")
                .setView(cview)
                .setCancelable(true)
                .create();
        
        alertHolder[0] = alert;
        alert.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        alert.setCanceledOnTouchOutside(false);
        if (alert.getWindow() != null) {{
            alert.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }}
        alert.show();
    }}"""
        return content[:start_idx] + new_method + content[end_idx:]
    return content

content = replace_method(content, "doDeleteMediaItems", "R.string.title_removing_music_files", "R.drawable.rounded_delete_24", "R.string.move_to_trash", "deleteFiles")
content = replace_method(content, "doMoveMediaItems", "R.string.title_moving_music_files", "R.drawable.rounded_drive_file_move_24", "R.string.move_to_music", "moveFiles")

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "w") as f:
    f.write(content)
print("Replaced actions successfully.")

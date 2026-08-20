import re

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "r") as f:
    content = f.read()

start_idx = content.find('private void doScanDirectories() {')
if start_idx == -1:
    print("Cannot find doScanDirectories")
else:
    # Find the matching closing brace
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
        new_method = """private void doScanDirectories() {
        if (!PermissionUtils.checkAccessPermissions(getApplicationContext())) {
            Intent intent = new Intent(MainActivity.this, PermissionActivity.class);
            startActivity(intent);
            return;
        }

        List<String> defaultPaths = FileRepository.getDefaultMusicPaths(this);
        Set<String> defaultPathsSet = new HashSet<>(defaultPaths);
        List<String> dirs = TagRepository.getDirectories(this);
        List<String> storageIds = DocumentFileCompat.getStorageIds(getApplicationContext());

        // We need a reference to the AlertDialog so we can dismiss it from inside the Compose callbacks
        final AlertDialog[] alertHolder = new AlertDialog[1];

        View cview = apincer.android.mmate.ui.compose.DialogInterop.createMusicFoldersDialogView(
            this,
            dirs,
            defaultPathsSet,
            storageIds,
            () -> { if(alertHolder[0] != null) alertHolder[0].dismiss(); }, // onClose
            () -> { if(alertHolder[0] != null) alertHolder[0].dismiss(); }, // onCancel
            (isDeep, updatedDirs) -> { // onScan
                apincer.music.core.Settings.setDirectories(getApplicationContext(), updatedDirs);
                if (isDeep) {
                    new MaterialAlertDialogBuilder(MainActivity.this, R.style.AlertDialogTheme)
                        .setTitle("Full Rescan")
                        .setMessage(getString(R.string.directories_confirm_full_scan))
                        .setPositiveButton("Start", (dialog, which) -> {
                            apincer.android.mmate.worker.ScanAudioFileWorker.startScan(getApplicationContext(), true);
                            if(alertHolder[0] != null) alertHolder[0].dismiss();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                } else {
                    apincer.android.mmate.worker.ScanAudioFileWorker.startScan(getApplicationContext(), false);
                    if(alertHolder[0] != null) alertHolder[0].dismiss();
                }
            },
            (sid) -> { // onAddStorage
                DialogProperties properties = new DialogProperties();
                properties.selection_mode = DialogConfigs.SINGLE_MODE;
                properties.selection_type = DialogConfigs.DIR_SELECT;
                FilePickerDialog dialog = new FilePickerDialog(MainActivity.this, properties);
                dialog.setDialogSelectionListener(files -> {
                    if (files != null && files.length > 0) {
                        String f = files[0];
                        // Re-trigger the dialog with new directory
                        dirs.add(f);
                        if(alertHolder[0] != null) alertHolder[0].dismiss();
                        apincer.music.core.Settings.setDirectories(getApplicationContext(), dirs); // Save immediately
                        doScanDirectories(); // Re-open
                    }
                });
                dialog.setTitle("Select a Directory");
                dialog.show();
            }
        );

        AlertDialog alert = new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setTitle("")
                .setView(cview)
                .setCancelable(true)
                .create();
        
        alertHolder[0] = alert;

        alert.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        alert.setCanceledOnTouchOutside(false);

        // Make popup round corners
        if (alert.getWindow() != null) {
            alert.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        alert.show();
    }"""
        # Note: the decorator @SuppressLint("SetTextI18n") was before it, we keep it because start_idx is at `private void`
        content = content[:start_idx] + new_method + content[end_idx:]
        with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "w") as f:
            f.write(content)
        print("Replaced doScanDirectories successfully.")

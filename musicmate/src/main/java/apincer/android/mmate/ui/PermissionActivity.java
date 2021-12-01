package apincer.android.mmate.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import apincer.android.mmate.R;
import apincer.android.mmate.service.MusicListeningService;

public class PermissionActivity extends AppCompatActivity {
    private LinearLayout panel;
    private TextView txtTitle;
    private TextView txtDesc;
    private TextView txtConfirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);
        panel = findViewById(R.id.perms_panel);

        txtTitle = findViewById(R.id.title);
        txtDesc = findViewById(R.id.description);
        txtConfirm = findViewById(R.id.confirm);

        setUpPermissions();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MusicListeningService.REQUEST_CODE_STORAGE_PERMISSION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // camera related task you need to do.
                    // check int/ext uri permission, if not request it
                    //doShowStoragePermissions();
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                    onBackPressed();
                   // startActivityForResult(intent, MusicListeningService.REQUEST_CODE_STORAGE_PERMISSION);
                }// else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
               // }
                return;
            }
        }
    }

    private void setUpPermissions() {
        // check all permission required/granted and display on screen
        // check all storage and display required/granted on screen
        // allow to grant permission
        // allow to gramt permission on storages

        txtTitle.setText("Permissions for using Music Mate application");
        txtConfirm.setText("OK");
        txtConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ActivityCompat.requestPermissions(PermissionActivity.this,
                        MusicListeningService.PERMISSIONS_ALL,
                        MusicListeningService.REQUEST_CODE_STORAGE_PERMISSION);
            }
        });

        // Internet
        LayoutInflater layoutInflater = LayoutInflater.from(getApplicationContext());
        panel.addView(getPermissionView(layoutInflater,null, "Full Storage Access", "(Required)", "Read/Write media, and files on device to manage music collection."));
        panel.addView(getPermissionView(layoutInflater,null, "Read External Storage", "(Required)", "Read photos, media, and files on device."));
        panel.addView(getPermissionView(layoutInflater,null, "Write External Storage", "(Required)", "Write media, and files on device to manage music collection."));
        //  panel.addView(getPermissionView(layoutInflater,null, "Internet", "(Optional)", "Access Internet for coverart and MusicBrainz services"));

       // if (!PermissionUtils.IsPermissionsEnabled(this, MusicListeningService.PERMISSIONS_ALL)) {
           /*PermissionsDialogue.Builder alertPermissions = new PermissionsDialogue.Builder(PermissionActivity.this)
                    .setCancelable(false)
                    .setMessage(getString(R.string.app_name) + " requires the following permissions to manage music")
                    .setIcon(R.drawable.ic_launcher)
                    .setRequireStorage(PermissionsDialogue.REQUIRED)
                    .setOnContinueClicked((view, dialog) -> {
                        doShowStoragePermissions();
                        dialog.dismiss();
                    })
                    .setDecorView(getWindow().getDecorView())
                    .build();
            alertPermissions.show(); */
           /*
            Needs needs = new Needs.Builder(getApplicationContext())
                    .setTitleIcon(getDrawable(R.drawable.ic_launcher))
                    .setTitle("Permission instructions for using this Android app.")
                    .addNeedsItem(new NeedsItem(null, "· SD Card", "(Required)", "Access photos, media, and files on device."))
                    .addNeedsItem(new NeedsItem(null, "· Location", "(Required)", "Access this device's location."))
                    .addNeedsItem(new NeedsItem(null, "· Camera", "(Optional)", "Take pictures and record video."))
                    .addNeedsItem(new NeedsItem(null, "· Contact", "(Optional)", "Access this device's contacts."))
                    .addNeedsItem(new NeedsItem(null, "· SMS", "(Optional)", " end and view SMS messages."))
                    .setDescription("The above accesses are used to better serve you.")
                    .setConfirm("Confirm")
                    .setBackgroundAlpha(0.6f)
                    //.setLifecycleOwner()
                    .build();
            needs.setOnConfirmListener(new OnConfirmListener() {
                @Override
                public void onConfirm() {
                    // confirmed
                }
            });
            needs.show(getCurrentFocus()); // shows the popup menu to the center.
            needs.dismiss(); // dismiss the popup menu.
            */
       // }
    }

    private View getPermissionView(LayoutInflater layoutInflater, Drawable icon, String title, String required, String desc) {
        View perm = layoutInflater.inflate(R.layout.view_permission_item, null);
        ImageView imgIcon = perm.findViewById(R.id.item_needs_image);
        TextView txtTitle = perm.findViewById(R.id.item_needs_title);
        TextView txtRequired = perm.findViewById(R.id.item_needs_require);
        TextView txtDesc = perm.findViewById(R.id.item_needs_description);
        txtTitle.setText(title);
        txtRequired.setText(required);
        txtDesc.setText(desc);
        return perm;
    }
/*
    private void doShowStoragePermissions() {
        txtTitle.setText("Specific storage permissions");
        txtDesc.setText("Storage permission are required for storage volume.\n For each volume ...");
        txtConfirm.setEnabled(true);
        txtConfirm.setText("OK");
        txtConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent myIntent = new Intent(PermissionActivity.this, MediaBrowserActivity.class);
                startActivity(myIntent);
            }
        });

        // Internet
        LayoutInflater layoutInflater = LayoutInflater.from(getApplicationContext());
        infos = MediaFileRepository.getInstance(getApplication()).getStorageInfos().values();
        panel.removeAllViews();
        int i = 0;
        for (MediaFileRepository.StorageInfo inf: infos) {
            boolean grantedPermission = isPermissionGranted(inf);
            View stoView = getPermissionView(layoutInflater,null, inf.title, "(Required)", "Read photos, media, and files on device.");
            stoView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentStorageInfo = inf;
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    startActivityForResult(intent, MusicListeningService.REQUEST_CODE_STORAGE_PERMISSION);
                }
            });
            panel.addView(stoView);
        }
    } */

    /*
    private boolean isPermissionGranted(MediaFileRepository.StorageInfo inf) {
        List<UriPermission> perms = this.getContentResolver().getPersistedUriPermissions();
        for (UriPermission perm: perms) {
           // perm.
            Uri uri =perm.getUri();

        }
        return false;
    } */
/*
    @Override
    public final void onActivityResult(final int requestCode, final int resultCode, final Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == MusicListeningService.REQUEST_CODE_STORAGE_PERMISSION) {
            if (resultCode == Activity.RESULT_OK) {
                // Get Uri from Storage Access Framework.
                // Persist access permissions.
                this.getContentResolver().takePersistableUriPermission(resultData.getData(), (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
                Uri uri = resultData.getData();
                String rootPath = currentStorageInfo.path.getAbsolutePath();
                Preferences.setPersistableUriPermission(this, rootPath, uri);
            }
        }
    }

    private void setUpStoragePermission(StorageVolume volume) {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    MusicListeningService.REQUEST_CODE_STORAGE_PERMISSION);

            // MY_PERMISSIONS_REQUEST_CAMERA is an
            // app-defined int constant. The callback method gets the
            // result of the request.

        } else {
            // Permission has already been granted
            // check int/ext uri permission, if not request it
        }
    } */
}

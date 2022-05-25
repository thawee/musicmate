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

import com.anggrayudi.storage.SimpleStorage;
import com.anggrayudi.storage.file.StorageId;

import apincer.android.mmate.R;
import apincer.android.mmate.utils.PermissionUtils;

public class PermissionActivity extends AppCompatActivity {
    // android 5 SD card permissions
    public static final int REQUEST_CODE_STORAGE_PERMISSION = 1010;

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
                                           String []permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (!SimpleStorage.hasFullDiskAccess(getApplicationContext(), StorageId.PRIMARY)) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
                finish();
            }
            return;
        }
    }

    private void setUpPermissions() {
        // check all permission required/granted and display on screen
        // check all storage and display required/granted on screen
        // allow to grant permission
        // allow to gramt permission on storages

        txtTitle.setText("Permissions for using Music Mate application");
        txtConfirm.setText("OK");
        txtConfirm.setOnClickListener(v -> ActivityCompat.requestPermissions(PermissionActivity.this,
                PermissionUtils.PERMISSIONS_ALL,
                REQUEST_CODE_STORAGE_PERMISSION));

        // Internet
        LayoutInflater layoutInflater = LayoutInflater.from(getApplicationContext());
       // panel.addView(getPermissionView(layoutInflater,null, "Full Storage Access", "(Required)", "Read/Write media, and files on device to manage music collection."));
        panel.addView(getPermissionView(layoutInflater,null, "Read External Storage", "(Required)", "Read photos, media, and files on device."));
        panel.addView(getPermissionView(layoutInflater,null, "Write External Storage", "(Required)", "Write media, and files on device to manage music collection."));
        //  panel.addView(getPermissionView(layoutInflater,null, "Internet", "(Optional)", "Access Internet for coverart and MusicBrainz services"));
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
}

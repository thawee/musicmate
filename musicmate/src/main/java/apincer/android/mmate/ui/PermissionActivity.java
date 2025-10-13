package apincer.android.mmate.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import apincer.android.mmate.R;
import apincer.android.mmate.core.utils.PermissionUtils;

public class PermissionActivity extends AppCompatActivity {
    // android 5 SD card permissions
    public static final int REQUEST_CODE_STORAGE_PERMISSION = 1010;

    private LinearLayout panel;
    private TextView txtTitle;
    private TextView txtDesc;
    private TextView txtConfirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);
        // set status bar color to black
        Window window = getWindow();
       // window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
       // window.setStatusBarColor(ContextCompat.getColor(this, android.R.color.black));
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
        // If the background is dark, use light icons
        insetsController.setAppearanceLightStatusBars(false);

        setContentView(R.layout.activity_permissions);
        panel = findViewById(R.id.perms_panel);

        txtTitle = findViewById(R.id.title);
        txtDesc = findViewById(R.id.description);
        txtConfirm = findViewById(R.id.confirm);

        setUpPermissions();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String []permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // if (!Environment.isExternalStorageManager()) {
                if(!PermissionUtils.checkFullStorageAccessPermissions(getApplicationContext())) {
                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
                finish();
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void setUpPermissions() {
        // check all permission required/granted and display on screen
        // check all storage and display required/granted on screen
        // allow to grant permission
        // allow to gramt permission on storages

        txtTitle.setText("Permissions for Music Mate");
        txtConfirm.setText("OK");
        //todo when permission is granted
        txtConfirm.setOnClickListener(v -> ActivityCompat.requestPermissions(PermissionActivity.this,
                PermissionUtils.PERMISSIONS_ALL,
                REQUEST_CODE_STORAGE_PERMISSION));

        // Internet
        LayoutInflater layoutInflater = LayoutInflater.from(getApplicationContext());

        panel.addView(getPermissionView(layoutInflater,null, "Access Audio Files", "(Required)", "Read audio media files on device."));
        panel.addView(getPermissionView(layoutInflater,null, "Full Storage Access", "(Required)", "Read/Write music files on device to manage music collection."));
    }

    private View getPermissionView(LayoutInflater layoutInflater, Drawable icon, String title, String required, String desc) {
        @SuppressLint("InflateParams") View perm = layoutInflater.inflate(R.layout.view_permission_item, null);
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

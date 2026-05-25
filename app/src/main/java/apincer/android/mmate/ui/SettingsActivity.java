package apincer.android.mmate.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.Window;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import apincer.android.mmate.R;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);

        // Enable Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.activity_fragement);

        ImageView blurBackground = findViewById(R.id.fragment_background_blur);
        // We'll use a standard placeholder or representative art for settings
        // To keep it simple, we don't inject tagRepos here, but just find art if needed.
        
        // set status bar color to black
        Window window = getWindow();
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
        insetsController.setAppearanceLightStatusBars(false);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.settings_toolbar);
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Settings Master");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_content, new SettingsFragment())
                .commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {//do whatever
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.settings, rootKey);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String s) {
           /* if(Constants.PREF_ENABLE_MEDIA_SERVER.equals(s)) {
                boolean enableMediaServer = sharedPreferences.getBoolean(Constants.PREF_ENABLE_MEDIA_SERVER, false);
                // Notify the application of the change
                if (getActivity()!=null && getActivity().getApplication() instanceof MusixMateApp) {
                    ((MusixMateApp) getActivity().getApplication()).onMediaServerSettingChanged(enableMediaServer);
                }
            }else if(Constants.PREF_CLEAR_CACHED.equals(s)) {
                MusixMateApp.getInstance().clearCaches();
            } */
        }

        @Override
        public void onResume() {
            super.onResume();
            PreferenceManager.getDefaultSharedPreferences(getContext()).registerOnSharedPreferenceChangeListener(this);
        }
    }
}
package apincer.android.mmate.ui;

/**
 * Created by Administrator on 8/26/17.
 */

import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceFragmentCompat;

import apincer.android.mmate.Preferences;
import apincer.android.mmate.R;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if(Preferences.isOnNightModeOnly(getApplicationContext())) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM); //must place before super.onCreate();
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragement);
        getSupportActionBar().setTitle(R.string.app_settings);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
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

    public static class SettingsFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.settings, rootKey);
          /*  SwitchPreferenceCompat httpServer = findPreference("preference_http_streaming_server");
            if(httpServer!=null) {
                String summary = String.valueOf(httpServer.getSummary());
                String ip = HostInterface.getIPv4Address(); // ApplicationUtils.getIPAddress(true);
                summary = summary.replace("<ip>", ip+":"+http_port);
                httpServer.setSummary(summary);
                if (MusixMateApp.getInstance().isHttpServerRunning()) {
                    httpServer.setChecked(true);
                } else {
                    httpServer.setChecked(false);
                }
                httpServer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                        if ((Boolean) newValue) {
                           // MusixMateApp.statHttpServer(getActivity());
                           // Intent myIntent = new Intent(getContext(), UpnpServerControlActivity.class);
                           // startActivity(myIntent);
                        } else {
                           // MusixMateApp.stopHttpServer(getActivity());
                        }
                        return true;
                    }
                });
            }*/
        }
    }
}
package com.balsikandar.crashreporter.ui;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.balsikandar.crashreporter.CrashReporter;
import com.balsikandar.crashreporter.R;
import com.balsikandar.crashreporter.adapter.MainStateAdapter;
import com.balsikandar.crashreporter.utils.Constants;
import com.balsikandar.crashreporter.utils.CrashUtil;
import com.balsikandar.crashreporter.utils.FileUtils;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.File;

public class CrashReporterActivity extends AppCompatActivity {
    private final String[] titles = {"Crashes", "Exceptions"};

    private MainStateAdapter mainPagerAdapter;
    private int selectedTabPosition = 0;

    //region activity callbacks
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.log_main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.delete_crash_logs) {
            clearCrashLog();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // This makes your app draw under the status and navigation bars.
        // This is what CAUSES the overlap you're seeing.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.crash_reporter_activity);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(getString(R.string.crash_reporter));
        toolbar.setSubtitle(getApplicationName());
        setSupportActionBar(toolbar);

        ViewPager2 viewPager =  findViewById(R.id.viewpager);
        if (viewPager != null) {
            setupViewPager(viewPager);
        }

        TabLayout tabLayout = findViewById(R.id.tabs);
       // tabLayout.setupWithViewPager(viewPager);
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(titles[position])
        ).attach();
    }
    //endregion

    private void clearCrashLog() {
        new Thread(() -> {
            String crashReportPath = TextUtils.isEmpty(CrashReporter.getCrashReportPath()) ?
                    CrashUtil.getDefaultPath() : CrashReporter.getCrashReportPath();

            File[] logs = new File(crashReportPath).listFiles();
            for (File file : logs) {
                FileUtils.delete(file);
            }
            runOnUiThread(this::clearLogs);
        }).start();
    }

    public void clearLogs() {
        // ViewPager2 creates fragments with tags like "f0", "f1", etc.
        Fragment crashFragment = getSupportFragmentManager().findFragmentByTag("f" + 0);
        Fragment exceptionFragment = getSupportFragmentManager().findFragmentByTag("f" + 1);

        if (crashFragment instanceof CrashLogFragment) {
            ((CrashLogFragment) crashFragment).clearLog();
        }

        if (exceptionFragment instanceof ExceptionLogFragment) {
            ((ExceptionLogFragment) exceptionFragment).clearLog();
        }
    }

    private void setupViewPager(ViewPager2 viewPager) {
        String[] titles = {getString(R.string.crashes), getString(R.string.exceptions)};
        mainPagerAdapter = new MainStateAdapter(getSupportFragmentManager(), getLifecycle());
        viewPager.setAdapter(mainPagerAdapter);

       /* viewPager.addOnPageChangeListener(new SimplePageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                selectedTabPosition = position;
            }
        }); */

        Intent intent = getIntent();
        if (intent != null && !intent.getBooleanExtra(Constants.LANDING, false)) {
            selectedTabPosition = 1;
        }
        viewPager.setCurrentItem(selectedTabPosition);
    }

    private String getApplicationName() {
        ApplicationInfo applicationInfo = getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : getString(stringId);
    }

}

package apincer.android.mmate.ui;

import android.os.Bundle;
import android.text.Spannable;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.mikepenz.aboutlibraries.LibsBuilder;

import java.util.Objects;

import apincer.android.mmate.BuildConfig;
import apincer.android.mmate.R;
import apincer.android.mmate.utils.ApplicationUtils;
import mehdi.sakout.aboutpage.AboutPage;
import mehdi.sakout.aboutpage.Element;

public class LibrariesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Element libsElement = new Element();
        libsElement.setTitle("Third-Party Libraries");
        libsElement.setOnClickListener(view -> new LibsBuilder()
                .withAboutAppName("Third-Party Libraries")
                .withAboutIconShown(false)
                .withAboutVersionShown(false)
                .withAboutVersionShownCode(false)
                .withAboutVersionShownName(false)
                .withEdgeToEdge(false)
                .withLicenseShown(true)
                .withLicenseDialog(false)
                .withSearchEnabled(false)
                .withSortEnabled(true)
                .withActivityTitle("Third-Party Libraries")
                .start(LibrariesActivity.this));

        Element musicElement = new Element();
        View aboutPage = new AboutPage(this,true)
                .isRTL(false)
                .setImage(R.mipmap.ic_launcher)
                .setDescription(BuildConfig.VERSION_NAME+"\nManage music files your way, and enjoy listening to your music :D")
               // .addGroup("Digital Music")
                .addItem(musicElement)
                //.addGroup("Third-Party Libraries")
                .addItem(libsElement)
                .create();

        Objects.requireNonNull(getSupportActionBar()).setTitle(R.string.app_about);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        setContentView(aboutPage);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {//do whatever
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}

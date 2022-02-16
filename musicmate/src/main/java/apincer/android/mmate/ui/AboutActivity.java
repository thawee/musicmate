package apincer.android.mmate.ui;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.mikepenz.aboutlibraries.LibsBuilder;

import apincer.android.mmate.BuildConfig;
import apincer.android.mmate.R;
import apincer.android.mmate.utils.ApplicationUtils;
import mehdi.sakout.aboutpage.AboutPage;
import mehdi.sakout.aboutpage.Element;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Element libsElement = new Element();
        libsElement.setTitle("Libraries Used");
        libsElement.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new LibsBuilder()
                        .withAboutAppName("Libraries Used")
                        .withAboutIconShown(false)
                        .withAboutVersionShown(false)
                        .withAboutVersionShownCode(false)
                        .withAboutVersionShownName(false)
                        .withEdgeToEdge(false)
                        .withLicenseShown(true)
                        // .withAboutSpecial1("DEVELOPER")
                        // .withAboutSpecial1Description("<b>Thawee Prakaipetch</b><br /><br />E-Mail: thaweemail@gmail.com<br />")
                      //  .withAboutSpecial1("DIGITAL MUSIC")
                      //  .withAboutSpecial1Description(ApplicationUtils.getAssetsText(AboutActivity.this,"digital_music.html"))
                        //.withAboutMinimalDesign(true)
                        .withLicenseDialog(false)
                        .withSearchEnabled(false)
                        .withSortEnabled(true)
                        //.withFields(R.string.class.getFields())
                        //.withAboutDescription("<b>by Thawee Prakaipetch</b><br /><br />Managing music collections on Android<br /><b>Enjoy Your Music :D</b>")
                       // .withAboutDescription("<b>by Thawee Prakaipetch</b><br /><b>Enjoy Your Music :D</b>"+ApplicationUtils.getAssetsText(this,"digital_music.html"))
                        .withActivityTitle("Dependency Libraries")
                        .start(AboutActivity.this);
            }
        });

        Element musicElement = new Element();
        musicElement.setTitle(ApplicationUtils.getAssetsText(this,"digital_music.txt"));

        View aboutPage = new AboutPage(this,true)
                .isRTL(false)
                .setImage(R.mipmap.ic_launcher)
                .setDescription(BuildConfig.VERSION_NAME+"\n\nManaging music collections on Android\nEnjoy Your Music :D")
                .addGroup("Digital Music")
                .addItem(musicElement)
                .addGroup("Third-Party Libraries")
                .addItem(libsElement)
                .create();

        getSupportActionBar().setTitle(R.string.app_about);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        setContentView(aboutPage);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                //do whatever
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}

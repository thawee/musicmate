package apincer.android.mmate.ui;

import android.os.Bundle;

import com.mikepenz.aboutlibraries.LibsBuilder;

import androidx.appcompat.app.AppCompatActivity;
import apincer.android.mmate.BuildConfig;
import apincer.android.mmate.R;

/**
 * Created by e1022387 on 2/13/2018.
 */

public class AboutActivity extends AppCompatActivity {
    String versionName = BuildConfig.VERSION_NAME;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new LibsBuilder()
                //provide a style (optional) (LIGHT, DARK, LIGHT_DARK_TOOLBAR)
               // .withActivityStyle(Libs.ActivityStyle.LIGHT_DARK_TOOLBAR)
                //start the activity
                .withAboutAppName("Music Mate")
                .withAboutIconShown(true)
                .withAboutVersionShown(true)
                .withAboutSpecial1("DEVELOPER")
                .withAboutSpecial1Description("<b>Thawee Prakaipetch</b><br /><br />email: thaweemail@gmail.com<br />")
                //.withAboutSpecial2("ABOUT DEVELOPER").withAboutSpecial2Description("Thawee Prakaipetch")
                .withAboutSpecial2("CHANGELOG")
                .withAboutSpecial2Description(getString(R.string.changelog_text))
                //.withFields(R.string.class.getFields())
                //.withAutoDetect(false)
              //  .withLicenseShown(true)
               // .withVersionShown(true)
                .withAboutDescription("<b>by Thawee Prakaipetch</b><br /><br />Managing music collections on your Android<br /><b>enjoy your Music :D</b>")
                //.withLibraries("objectbox","coil","rxandroid","rxjava","retrofit","gson","android_shape_imageview","jaudiotagger","epoxy","justdsd","elasticviews")
                //.withExcludedLibraries("fastadapter","iconics_core","support_annotations","intellijannotations")
                .withActivityTitle("About")
                .start(this);
    }
}
package apincer.android.tripmate;

import androidx.appcompat.app.AppCompatDelegate;

import apincer.android.tripmate.objectbox.ObjectBox;
import timber.log.Timber;

public class Application extends android.app.Application {

    @Override public void onCreate() {
        super.onCreate();

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        //Timber.plant(new Timber.DebugTree());

        //initialize ObjectBox is when your app starts
        ObjectBox.init(this);
    }
}

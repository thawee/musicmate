package apincer.android.mmate.service;

import android.service.notification.NotificationListenerService;
import android.util.Log;

/**
 * Required for monitoring media sessions from other apps.
 * This service must be declared in AndroidManifest.xml with proper permissions.
 */
public class MediaNotificationListener extends NotificationListenerService {
    private static final String TAG = "MediaNotificationListener";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "MediaNotificationListener created");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MediaNotificationListener destroyed");
    }
}

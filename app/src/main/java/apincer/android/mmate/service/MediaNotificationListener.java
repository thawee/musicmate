package apincer.android.mmate.service;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

/**
 * Required for monitoring media sessions from other apps.
 * This service must be declared in AndroidManifest.xml with proper permissions
 * and android:exported="true".
 */
public class MediaNotificationListener extends NotificationListenerService {
    private static final String TAG = "MediaNotificationListener";
    private static boolean isConnected = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "MediaNotificationListener created");
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        isConnected = true;
        Log.d(TAG, "MediaNotificationListener connected successfully");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        isConnected = false;
        Log.d(TAG, "MediaNotificationListener disconnected");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isConnected = false;
        Log.d(TAG, "MediaNotificationListener destroyed");
    }

    public static boolean isConnected() {
        return isConnected;
    }
}

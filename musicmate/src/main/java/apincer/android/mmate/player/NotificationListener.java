package apincer.android.mmate.player;


import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.utils.StringUtils;

public class NotificationListener extends NotificationListenerService {
    private static final String TAG = "NotificationListener";
    private final Object stateLock = new Object();

    // Constants for notification extras
    private static final String EXTRA_TITLE = "android.title";
    private static final String EXTRA_TEXT = "android.text";

    // Set of supported music player package names for efficient lookup
    private static final Set<String> SUPPORTED_PLAYERS = new HashSet<>(Arrays.asList(
            PlayerControl.PlayerPackageNames.HIBY_MUSIC_PACK_NAME,
            PlayerControl.PlayerPackageNames.NE_PLAYER_LITE_PACK_NAME,
            PlayerControl.PlayerPackageNames.EDDICTPLAYER_PACK_NAME,
            PlayerControl.PlayerPackageNames.NEUTRON_MUSIC_PACK_NAME,
            PlayerControl.PlayerPackageNames.UAPP_PACK_NAME
            // Add new player packages here
    ));

    // Track previous playback state to avoid duplicate updates
    private String previousPackage;
    private String previousTitle;
    private String previousArtist;

    public NotificationListener() {
        super();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "NotificationListener service being destroyed");
        // Reset state variables
        previousPackage = null;
        previousTitle = null;
        previousArtist = null;

        // Let the parent handle unbinding - don't call unbindService yourself
        super.onDestroy();
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "NotificationListener service connected");
        // Reset state variables
        previousPackage = null;
        previousTitle = null;
        previousArtist = null;
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.d(TAG, "NotificationListener service disconnected");

        // Attempt to reconnect
        requestRebind(new ComponentName(this, NotificationListener.class));
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();

        // Check if this is from a supported music player
        if (!SUPPORTED_PLAYERS.contains(packageName)) {
            return;
        }

        Bundle extras = sbn.getNotification().extras;
        if (extras == null) {
            Log.w(TAG, "Notification extras is null for package: " + packageName);
            return;
        }

        try {
            // Extract title and artist information
            String title = extras.getString(EXTRA_TITLE);
            if (title == null) {
                return;
            }

            title = StringUtils.removeTrackNo(title);

            // Get artist information
            CharSequence textSequence = extras.getCharSequence(EXTRA_TEXT);
            String artist = textSequence != null ? textSequence.toString() : "";

            // Publish the now playing information
            publishNowPlaying(packageName, title, artist);
        } catch (Exception e) {
            Log.e(TAG, "Error processing notification from " + packageName, e);
        }

       // Log.i(TAG, "Notification posted: " + sbn.getPackageName());
       /* String pack = sbn.getPackageName();
        Bundle extras = sbn.getNotification().extras;
        if (PlayerControl.PlayerPackageNames.HIBY_MUSIC_PACK_NAME.equals(pack)) {
            String title = extras.getString("android.title");
            title = StringUtils.removeTrackNo(title);
            String artist = String.valueOf (extras.getCharSequence("android.text"));
            publishNowPlaying(pack, title, artist);
        } else if (PlayerControl.PlayerPackageNames.NE_PLAYER_LITE_PACK_NAME.equals(pack)) {
            String title = extras.getString("android.title");
            title = StringUtils.removeTrackNo(title);
            String artist = String.valueOf (extras.getCharSequence("android.text"));
            publishNowPlaying(pack, title, artist);
        } else if (PlayerControl.PlayerPackageNames.EDDICTPLAYER_PACK_NAME.equals(pack)) {
            String title = extras.getString("android.title");
            title = StringUtils.removeTrackNo(title);
            String artist = String.valueOf (extras.getCharSequence("android.text"));
            publishNowPlaying(pack, title, artist);
        } else if (PlayerControl.PlayerPackageNames.NEUTRON_MUSIC_PACK_NAME.equals(pack)) {
            String title = extras.getString("android.title");
            title = StringUtils.removeTrackNo(title);
            String artist = String.valueOf (extras.getCharSequence("android.text"));
            publishNowPlaying(pack, title, artist);
        } else if (PlayerControl.PlayerPackageNames.UAPP_PACK_NAME.equals(pack)) {
            String title = extras.getString("android.title");
            title = StringUtils.removeTrackNo(title);
            String artist = String.valueOf (extras.getCharSequence("android.text"));
            publishNowPlaying(pack, title, artist);
        } */
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Handle the notification removed event
       // Log.i(TAG, "Notification removed: " + sbn.getPackageName());
    }

    public void publishNowPlaying(String packageName, String title, String artist) {
        // Check if the playback information has changed to avoid unnecessary updates
        synchronized(stateLock) {
            if (!(StringUtils.compare(previousPackage, packageName) &&
                    StringUtils.compare(previousTitle, title) &&
                    StringUtils.compare(previousArtist, artist))) {

                // Update previous state
                previousPackage = packageName;
                previousTitle = title;
                previousArtist = artist;

                Log.d(TAG, "Now playing update: " + title + " - " + artist + " [" + packageName + "]");

                try {
                    // Send the now playing information to PlayerControl
                    MusixMateApp.getPlayerControl().setPlayingSong(
                            getApplicationContext(),
                            packageName,
                            title,
                            artist,
                            null);
                } catch (Exception ex) {
                    Log.e(TAG, "Error sending now playing information", ex);
                }
            }
        }
    }

    public static void reconnectNotificationListener(Context context) {
        ComponentName componentName = new ComponentName(context, NotificationListener.class);
        NotificationListenerService.requestRebind(componentName);
    }
}

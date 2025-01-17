package apincer.android.mmate.player;


import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.utils.StringUtils;

public class NotificationListener extends NotificationListenerService {
    private static final String TAG = "NotificationListener";

    private String prvPack;
    private String prvTitle;
    private String prvArtist;

    public NotificationListener() {
        super();
    }

  //  Context context;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
       // Log.i(TAG, "Notification posted: " + sbn.getPackageName());
        String pack = sbn.getPackageName();
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
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Handle the notification removed event
       // Log.i(TAG, "Notification removed: " + sbn.getPackageName());
    }

    public void publishNowPlaying(String pack, String title, String artist) {
        if(!(StringUtils.compare(prvPack, pack) && StringUtils.compare(prvTitle, title) && StringUtils.compare(prvArtist, artist))) {
            prvPack = pack;
            prvTitle = title;
            prvArtist = artist;
            try {
                MusixMateApp.getPlayerControl().setPlayingSong(getApplicationContext(), pack, title, artist, null);
            } catch (Exception ex) {
                Log.e(TAG, "sendBroadcast", ex);
            }
        }
    }
}

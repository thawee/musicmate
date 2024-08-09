package apincer.android.mmate.broadcast;


import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import apincer.android.mmate.utils.StringUtils;

public class NotificationListener extends NotificationListenerService {
    private static final String TAG = "NotificationListener";

    private String prvPack;
    private String prvTitle;
    private String prvArtist;

    public NotificationListener() {
        super();
    }

    Context context;

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
       // Log.d(TAG, "onDestroy MusicMate Notification");
    }

    @Override

    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
       // Log.d(TAG, "onCreate MusicMate Notification");
    }

    @Override

    public void onNotificationPosted(StatusBarNotification sbn) {
        String pack = sbn.getPackageName();
        Bundle extras = sbn.getNotification().extras;
        if (BroadcastHelper.PlayerPackageNames.HIBY_MUSIC_PACK_NAME.equals(pack)) {
            String title = extras.getString("android.title");
            title = StringUtils.removeTrackNo(title);
            String artist = String.valueOf (extras.getCharSequence("android.text"));
            sendBroadcast(pack, title, artist);
        } else if (BroadcastHelper.PlayerPackageNames.NE_PLAYER_LITE_PACK_NAME.equals(pack)) {
            String title = extras.getString("android.title");
            title = StringUtils.removeTrackNo(title);
            String artist = String.valueOf (extras.getCharSequence("android.text"));
            sendBroadcast(pack, title, artist);
        } else if (BroadcastHelper.PlayerPackageNames.EDDICTPLAYER_PACK_NAME.equals(pack)) {
            String title = extras.getString("android.title");
            title = StringUtils.removeTrackNo(title);
            String artist = String.valueOf (extras.getCharSequence("android.text"));
            sendBroadcast(pack, title, artist);
        } else if (BroadcastHelper.PlayerPackageNames.NEUTRON_MUSIC_PACK_NAME.equals(pack)) {
            String title = extras.getString("android.title");
            title = StringUtils.removeTrackNo(title);
            String artist = String.valueOf (extras.getCharSequence("android.text"));
            sendBroadcast(pack, title, artist);
        } else if (BroadcastHelper.PlayerPackageNames.UAPP_PACK_NAME.equals(pack)) {
            String title = extras.getString("android.title");
            title = StringUtils.removeTrackNo(title);
            String artist = String.valueOf (extras.getCharSequence("android.text"));
            sendBroadcast(pack, title, artist);
        }
    }

    public void sendBroadcast(String pack, String title, String artist) {
        if(!(StringUtils.compare(prvPack, pack) && StringUtils.compare(prvTitle, title) && StringUtils.compare(prvArtist, artist))) {
            prvPack = pack;
            prvTitle = title;
            prvArtist = artist;
            Intent intent = new Intent("apincer.android.mmate");
            intent.putExtra(MusicBroadcastReceiver.INTENT_KEY_PACKAGE, pack);
            intent.putExtra(MusicBroadcastReceiver.INTENT_KEY_PLAYER, pack);
            intent.putExtra("track", title);
            intent.putExtra("artist", artist);
            sendBroadcast(intent);
        }
    }
}

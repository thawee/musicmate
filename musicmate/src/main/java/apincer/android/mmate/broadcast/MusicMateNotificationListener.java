package apincer.android.mmate.broadcast;


import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import apincer.android.mmate.utils.StringUtils;

public class MusicMateNotificationListener extends NotificationListenerService {
     public static final String HIBY_MUSIC = "com.hiby.music";
    public static final String NE_PLAYER_LITE = "jp.co.radius.neplayer_lite_an";

    public static final String SHANLING_EDDICTPLAYER = "com.shanling.eddictplayer";

    public static final String NEUTRON_MUSIC_PLAYER = "com.neutroncode.mp";

    public static String UAPP = "com.extreamsd.usbaudioplayerpro";

    private String prvPack;
    private String prvTitle;
    private String prvArtist;

    public MusicMateNotificationListener() {
        super();
    }

    Context context;

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(this.getClass().getName(), "onDestroy MusicMate Notification");
    }

    @Override

    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        Log.d(this.getClass().getName(), "onCreate MusicMate Notification");
    }

    @Override

    public void onNotificationPosted(StatusBarNotification sbn) {
        String pack = sbn.getPackageName();
        Bundle extras = sbn.getNotification().extras;
        if (HIBY_MUSIC.equals(pack)) {
            String title = extras.getString("android.title");
            title = StringUtils.removeTrackNo(title);
            String artist = String.valueOf (extras.getCharSequence("android.text"));
            sendBroadcast(pack, title, artist);
        } else if (NE_PLAYER_LITE.equals(pack)) {
            String title = extras.getString("android.title");
            title = StringUtils.removeTrackNo(title);
            String artist = String.valueOf (extras.getCharSequence("android.text"));
            sendBroadcast(pack, title, artist);
        } else if (SHANLING_EDDICTPLAYER.equals(pack)) {
            String title = extras.getString("android.title");
            title = StringUtils.removeTrackNo(title);
            String artist = String.valueOf (extras.getCharSequence("android.text"));
            sendBroadcast(pack, title, artist);
        } else if (NEUTRON_MUSIC_PLAYER.equals(pack)) {
            String title = extras.getString("android.title");
            title = StringUtils.removeTrackNo(title);
            String artist = String.valueOf (extras.getCharSequence("android.text"));
            sendBroadcast(pack, title, artist);
        } else if (UAPP.equals(pack)) {
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

    @Override

    public void onNotificationRemoved(StatusBarNotification sbn) {

    }
}

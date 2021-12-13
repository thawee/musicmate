package apincer.android.mmate.service;


import android.content.Context;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class MusicMateNotificationListener extends NotificationListenerService implements ListeningReceiver {

    public static final String HIBY_MUSIC = "com.hiby.music";
    String NE_PLAYER_LITE = "jp.co.radius.neplayer_lite_an";

    public MusicMateNotificationListener() {
        super();
    }

    Context context;

    @Override

    public void onCreate() {

        super.onCreate();
        context = getApplicationContext();

    }

    @Override

    public void onNotificationPosted(StatusBarNotification sbn) {
        String pack = sbn.getPackageName();
        Bundle extras = sbn.getNotification().extras;
        if (HIBY_MUSIC.equals(pack)) {
            MusicPlayerInfo player = new MusicPlayerInfo();
            player.playerPackage = pack;
            String title = extras.getString("android.title");
            String artist = extras.getCharSequence("android.text").toString();
            if (MusicListeningService.getInstance() != null) {
                MusicListeningService.getInstance().setPlayerInfo(player);
                MusicListeningService.getInstance().setPlayingSong(title, artist, null);
            }
        } else if (NE_PLAYER_LITE.equals(pack)) {
            MusicPlayerInfo player = new MusicPlayerInfo();
            player.playerPackage = pack;
            String title = extras.getString("android.title");
            String artist = extras.getCharSequence("android.text").toString();
            if (MusicListeningService.getInstance() != null) {
                MusicListeningService.getInstance().setPlayerInfo(player);
                MusicListeningService.getInstance().setPlayingSong(title, artist, null);
            }
        }
    }

    @Override

    public void onNotificationRemoved(StatusBarNotification sbn) {

    }
}

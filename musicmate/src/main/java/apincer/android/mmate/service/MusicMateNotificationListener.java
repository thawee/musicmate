package apincer.android.mmate.service;


import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import apincer.android.mmate.utils.BitmapHelper;

public class MusicMateNotificationListener extends NotificationListenerService implements ListeningReceiver {

    public static final String HIBY_MUSIC = "com.hiby.music";
    String NE_PLAYER_LITE = "jp.co.radius.neplayer_lite_an";

    public MusicMateNotificationListener() {
        super();
    }

    Context context;
    protected MusicPlayerInfo playerInfo;
    public static String DEAFULT_PLAYER_NAME = "UNKNOWN Player";

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
            String title = extras.getString("android.title");
            String artist = extras.getCharSequence("android.text").toString();
            if (MusicListeningService.getInstance() != null) {
                setPlayer(getApplicationContext(),pack, null);
                MusicListeningService.getInstance().setPlayerInfo(playerInfo);
                MusicListeningService.getInstance().setPlayingSong(title, artist, null);
            }
        } else if (NE_PLAYER_LITE.equals(pack)) {
            String title = extras.getString("android.title");
            String artist = extras.getCharSequence("android.text").toString();
            if (MusicListeningService.getInstance() != null) {
                setPlayer(getApplicationContext(),pack, null);
                MusicListeningService.getInstance().setPlayerInfo(playerInfo);
                MusicListeningService.getInstance().setPlayingSong(title, artist, null);
            }
        }
    }

    @Override

    public void onNotificationRemoved(StatusBarNotification sbn) {

    }

    private void initPlayer() {
        if(playerInfo==null) {
            playerInfo = new MusicPlayerInfo();
        }
        playerInfo.playerName = DEAFULT_PLAYER_NAME;
        playerInfo.playerPackage = "unknown";
        playerInfo.playerIconBitmap = null;
        playerInfo.playerIconDrawable = null;
    }

    protected void setPlayer(Context context, String packageName, String playerName) {
        initPlayer();
        playerInfo.playerPackage = packageName;
        playerInfo.playerName = playerName==null?DEAFULT_PLAYER_NAME:playerName;
        ApplicationInfo ai = MusicListeningService.getInstance().getApplicationInfo(packageName);
        if(ai!=null) {
            playerInfo.playerIconDrawable = context.getPackageManager().getApplicationIcon(ai);
            if (playerInfo.playerIconDrawable != null) {
                playerInfo.playerIconBitmap = BitmapHelper.drawableToBitmap(playerInfo.playerIconDrawable);
            }else {
                playerInfo.playerIconBitmap = null;
            }
            if(playerName==null) {
                playerInfo.playerName = String.valueOf(context.getPackageManager().getApplicationLabel(ai));
            }
        }
    }
}

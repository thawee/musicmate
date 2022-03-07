package apincer.android.mmate.broadcast;


import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class MusicMateNotificationListener extends NotificationListenerService {
     public static final String HIBY_MUSIC = "com.hiby.music";
    public static final String NE_PLAYER_LITE = "jp.co.radius.neplayer_lite_an";

    public MusicMateNotificationListener() {
        super();
    }

    Context context;
  //  protected MusicPlayerInfo playerInfo;
  //  public static String DEAFULT_PLAYER_NAME = "UNKNOWN Player";

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
            sendBroadcast(pack, title, artist);
           // if (MusicListeningService.getInstance() != null) {
          //      setPlayer(getApplicationContext(),pack, null);
          //  broadcastHelper.setPlayerInfo(playerInfo);
         //   broadcastHelper.setPlayingSong( context, title,artist,null);
            //    MusicListeningService.getInstance().setPlayerInfo(playerInfo);
            //    MusicListeningService.getInstance().setPlayingSong(title, artist, null);
           // }
        } else if (NE_PLAYER_LITE.equals(pack)) {
            String title = extras.getString("android.title");
            String artist = extras.getCharSequence("android.text").toString();
            sendBroadcast(pack, title, artist);
           // if (MusicListeningService.getInstance() != null) {
            //    setPlayer(getApplicationContext(),pack, null);
            //    MusicListeningService.getInstance().setPlayerInfo(playerInfo);
            //    MusicListeningService.getInstance().setPlayingSong(title, artist, null);
           // broadcastHelper.setPlayerInfo(playerInfo);
           // broadcastHelper.setPlayingSong( context, title,artist,null);
           // }
        }
    }

    public void sendBroadcast(String pack, String title, String artist) {
        Intent intent = new Intent("apincer.android.mmate");
        intent.putExtra(MusicBroadcastReceiver.INTENT_KEY_PACKAGE, pack);
        intent.putExtra(MusicBroadcastReceiver.INTENT_KEY_PLAYER, pack);
        intent.putExtra("track", title);
        intent.putExtra("artist", artist);
        sendBroadcast(intent);
    }

    @Override

    public void onNotificationRemoved(StatusBarNotification sbn) {

    }

    /*
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
        ApplicationInfo ai = null;
        try {
            ai = context.getPackageManager().getApplicationInfo(packageName, 0); // MusicListeningService.getInstance().getApplicationInfo(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
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
    } */
}

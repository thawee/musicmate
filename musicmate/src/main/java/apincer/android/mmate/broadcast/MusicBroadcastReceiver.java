package apincer.android.mmate.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.List;

import apincer.android.mmate.player.PlayerControl;
import apincer.android.mmate.player.PlayerInfo;
import apincer.android.mmate.utils.BitmapHelper;
import apincer.android.mmate.utils.LogHelper;

/**
 * Supporting android standard broadcast
 *  - Google Music
 *  - Foobar200
 *  - Neutron Player
 *  - USB Audio Player Pro
 *  - VIVO Music Player, cannot get player details
 *  - FIIO Music Player
 *  NOT supported player - not send any broadcast at all
 *      - HFPlayer (com.onkyo.jp.musicplayer)
 *      - Radsone DCT
 *      - Hiby Music
 */
@Deprecated
public class MusicBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = LogHelper.getTag(MusicBroadcastReceiver.class);
    public static String PACKAGE_SONY_MUSIC = "com.sonyericson.music";
    public static String PACKAGE_NEUTRON = "com.neutroncode.mp";
    public static String PACKAGE_UAPP = "com.extreamsd.usbaudioplayerpro";
    public static String PACKAGE_FOOBAR2000="com.foobar2000.foobar2000";
    public static String PACKAGE_POWERAMP = "com.maxmpz.audioplayer";
    public static String PREFIX_UAPP = "com.extreamsd.usbaudioplayershared";
    //public static String PREFIX_VLC = "org.videolan.vlc";
    public static String INTENT_KEY_PACKAGE = "package";
    public static String INTENT_KEY_PLAYER = "player";
    public static String INTENT_KEY_SCROBBLING_SOURCE = "scrobbling_source";

    String PLAYER_NAME_FOOBAR2000 = "foobar2000";
    //private MusicListeningService service;
    private final PlayerControl broadcastHelper;
    protected String title;
    protected String artist;
    protected String album;
    protected PlayerInfo playerInfo;
    public static String DEAFULT_PLAYER_NAME = "UNKNOWN Player";

    public MusicBroadcastReceiver(PlayerControl broadcastHelper ) {
        this.broadcastHelper = broadcastHelper;
        playerInfo = PlayerInfo.buildLocalPlayer("unknown", DEAFULT_PLAYER_NAME,null);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle extras = intent.getExtras();

        //KEEP for DEBUG
        /*    String string = "";
            if(extras != null) {
                for (String key : extras.keySet()) {
                    string += " " + key + " => " + extras.get(key) + ";";
                }
            } */

            // skip broadcast and notification lister if play by dlna client
           // if(broadcastHelper.getPlayerInfo()!= null && broadcastHelper.getPlayerInfo().isValidStreamPlayer(player)) return;

            boolean paused = intent.getBooleanExtra("paused", false);
            if(!paused) {
                try {
                    String playerPackage = extractPlayer(context, intent);

                    extractTitle(playerPackage, extras);
                   // displayNotification();
                  //  MusicListeningService.getInstance().setPlayerInfo(playerInfo);
                  //  MusicListeningService.getInstance().setPlayingSong(title,artist,album);
                   // service.setListeningReceiver(this);
                  //  if((!StringUtils.isEmpty(playerPackage)) && ApplicationUtils.isAppRunning(context, playerPackage)) {
                       // broadcastHelper.setPlayerInfo(playerInfo);
                        broadcastHelper.setPlayingSong(context, playerPackage, title, artist, album);
                //    }
                }catch (Exception ex) {
                    Log.e(TAG, "onReceive",ex);
                }
            }
    }

    protected String extractPlayer(Context context, Intent intent) {
        String action = intent.getAction();
        String playerPackage = "";

        if(action.startsWith(PREFIX_UAPP)) {
            playerPackage = PACKAGE_UAPP;
            setPlayer(context, PACKAGE_UAPP,null);
        }else {
            playerPackage = intent.getStringExtra(INTENT_KEY_PACKAGE);
            String playerName = intent.getStringExtra(INTENT_KEY_PLAYER);
            if(PlayerControl.PlayerPackageNames.NE_PLAYER_LITE_PACK_NAME.equalsIgnoreCase(playerName)) {
                playerPackage = playerName;
                playerName = null;
            }else if(PlayerControl.PlayerPackageNames.HIBY_MUSIC_PACK_NAME.equalsIgnoreCase(playerName)) {
                playerPackage = playerName;
                playerName = null;
            }else if(PLAYER_NAME_FOOBAR2000.equalsIgnoreCase(playerName)) {
                playerPackage = PACKAGE_FOOBAR2000;
            }else if(PACKAGE_POWERAMP.equals(intent.getStringExtra(INTENT_KEY_SCROBBLING_SOURCE))) {
                playerPackage = PACKAGE_POWERAMP;
            }

            if(playerPackage==null) {
                playerPackage = getDefaultPlayerPackage(context);
            }
            setPlayer(context, playerPackage,playerName);

        }
        return playerPackage;
    }

    protected String getDefaultPlayerPackage(Context context) {
        try {
            Intent musicPlayerIntent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC);
            List<ResolveInfo> list = context.getPackageManager().queryIntentActivities(musicPlayerIntent, PackageManager.MATCH_DEFAULT_ONLY);
            if(list!=null && list.size()>0) {
                // get last in the list
                return list.get(list.size()-1).activityInfo.packageName;
            }
        }catch (Exception ex) {
            Log.e(TAG, "getDefaultPlayerPackage",ex);
        }
        return null;
    }

    protected void setPlayer(Context context, String packageName, String playerName) {
        playerInfo = PlayerInfo.buildLocalPlayer("unknown", DEAFULT_PLAYER_NAME,null);
        playerInfo.playerPackage = packageName;
        playerInfo.playerName = playerName==null?DEAFULT_PLAYER_NAME:playerName;
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(packageName, 0); // MusicListeningService.getInstance().getApplicationInfo(packageName);
            if (ai != null) {
                playerInfo.playerIconDrawable = context.getPackageManager().getApplicationIcon(ai);
                if (playerInfo.playerIconDrawable != null) {
                    playerInfo.playerIconBitmap = BitmapHelper.drawableToBitmap(playerInfo.playerIconDrawable);
                } else {
                    playerInfo.playerIconBitmap = null;
                }
                if (playerName == null || playerName.equals(packageName)) {
                    playerInfo.playerName = String.valueOf(context.getPackageManager().getApplicationLabel(ai));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "setPlayer",e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public void register(Context context) {
        IntentFilter iF = new IntentFilter();
/*
        // USB Audio Player Pro
        iF.addAction("com.extreamsd.usbaudioplayershared.metachanged"); // API < 14
        //iF.addAction("com.extreamsd.usbaudioplayershared.playstatechanged"); // API >= 14, no need
		
		//PowerAmp
        iF.addAction("com.maxmpz.audioplayer.playstatechanged");
		iF.addAction("com.maxmpz.audioplayer.metachanged");

        // Sony
        //SEMC Music Player
        iF.addAction("com.sonyericsson.music.playbackcontrol.ACTION_TRACK_STARTED");
        iF.addAction("com.sonyericsson.music.playbackcontrol.ACTION_PLAYBACK_PLAY");
       // iF.addAction("com.sonyericsson.music.playbackcontrol.ACTION_PAUSED");
       // iF.addAction("com.sonyericsson.music.TRACK_COMPLETED");
        iF.addAction("com.sonyericsson.music.metachanged");
       // iF.addAction("com.sonyericsson.music.playbackcomplete");
        iF.addAction("com.sonyericsson.music.playstatechanged");
        iF.addAction("com.sonyericsson.music.RECENTLY_PLAYED");

        // spotify
        iF.addAction("com.spotify.music.metadatachanged");
        iF.addAction("com.spotify.music.playbackstatechanged");
        iF.addAction("com.spotify.music.queuechanged");

        //VIVO Music
        // iF.addAction("com.android.bbkmusic.metachanged");

         //MIUI
       // iF.addAction("com.miui.player.metachanged");

        //HTC
       // iF.addAction("com.htc.music.metachanged");
       // iF.addAction("com.htc.music.playstatechanged");

        // GoneMAD
       // iF.addAction("gonbemad.dashclock.music.metachanged");
       // iF.addAction("gonbemad.dashclock.music.playstatechanged");

        //JR
       // iF.addAction("com.jrstudio.music.metachanged");
       // iF.addAction("com.jrstudio.music.playstatechanged");

        // SAMSUNG
       // iF.addAction("com.samsung.sec.android.MusicPlayer.metachanged");
       // iF.addAction("com.samsung.music.metachanged");
       // iF.addAction("com.samsung.sec.metachanged");
       // iF.addAction("com.samsung.sec.android.metachanged");
       // iF.addAction("com.samsung.MusicPlayer.metachanged");
       // iF.addAction("com.sec.android.music.state.META_CHANGED");

        // FIIO
        iF.addAction("com.fiio.musicalone.player.brocast");

        // hiby
        iF.addAction("com.hiby.music");
*/
        // get forward from notification reader
        iF.addAction("apincer.android.mmate");

        // LMS Material App
        iF.addAction("com.craigd.lmsmaterial.app");

        //Audio
       // iF.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
       // iF.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
      //  iF.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
	  
	    // Android
       /* iF.addAction("com.android.music.metachanged");
        iF.addAction("com.android.music.playstatechanged");
        iF.addAction("com.android.music.playbackcomplete");
        iF.addAction("com.android.music.queuechanged");
        iF.addAction("com.android.music.updateprogress");
*/
        iF.addCategory("");

        context.registerReceiver(this, iF, Context.RECEIVER_NOT_EXPORTED);
    }

    protected void extractTitle(String playerPackage, Bundle bundle) {
       // String action = intent.getAction();
        artist = "";
        album = "";
        title = "";

        artist = bundle.getString("artist");
       album = bundle.getString("album");
       title = bundle.getString("track");
       //if(PACKAGE_POWERAMP.equals(playerPackage)) {
       //    title = StringUtils.trimToEmpty(title.replace( " - "+artist+" - "+ album, ""));
       //}
        if(PACKAGE_SONY_MUSIC.equalsIgnoreCase(playerPackage)) {
            artist = bundle.getString("ARTIST_NAME");
            album = bundle.getString("ALBUM_NAME");
            title = bundle.getString("TRACK_NAME");
        }
    }
}

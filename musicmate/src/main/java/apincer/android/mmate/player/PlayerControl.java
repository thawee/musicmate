package apincer.android.mmate.player;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.media.AudioManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;

import apincer.android.mmate.Settings;
import apincer.android.mmate.broadcast.AudioTagPlayingEvent;
import apincer.android.mmate.broadcast.MusicBroadcastReceiver;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.utils.BitmapHelper;
import apincer.android.mmate.utils.LogHelper;
import apincer.android.mmate.utils.StringUtils;


public class PlayerControl {
    private static final String TAG = LogHelper.getTag(PlayerControl.class);
    public static String DEAFULT_PLAYER_NAME = "UNKNOWN Player";

    /*
    These are the package names of the apps. for which we want to
    listen the notifications
 */
    public static final class PlayerPackageNames {
        public static final String HIBY_MUSIC_PACK_NAME = "com.hiby.music";
        public static final String NE_PLAYER_LITE_PACK_NAME = "jp.co.radius.neplayer_lite_an";
        public static final String NEUTRON_MUSIC_PACK_NAME = "com.neutroncode.mp";
        public static final String UAPP_PACK_NAME = "com.extreamsd.usbaudioplayerpro";
        public static final String EDDICTPLAYER_PACK_NAME = "com.shanling.eddictplayer";
        public static final String FOOBAR2000="com.foobar2000.foobar2000";
        public static final String POWERAMP = "com.maxmpz.audioplayer";
        public static final String SONY_MUSIC = "com.sonyericson.music";
    }

    /**
     * Poweramp package name
     */
    public static final String PAAPI_ACTION_API_COMMAND="com.maxmpz.audioplayer.API_COMMAND";
    public static final String PAAPI_COMMAND="cmd";
    public static final int PAAPI_COMMAND_NEXT=4;
    public static final String PAAPI_PACKAGE_NAME = "com.maxmpz.audioplayer";
    public static final String PAAPI_PLAYER_SERVICE_NAME = "com.maxmpz.audioplayer.player.PlayerService";
    public static final ComponentName PAAPI_PLAYER_SERVICE_COMPONENT_NAME = new ComponentName(PAAPI_PACKAGE_NAME, PAAPI_PLAYER_SERVICE_NAME);


    private volatile static MusicTag playingSong;
    private volatile static PlayerInfo playerInfo;
    private FileRepository provider;

    public PlayerControl() {

    }

    public MusicTag getPlayingSong() {
        return playingSong;
    }

    public PlayerInfo getPlayerInfo() {
        return playerInfo;
    }

    /*
    private void registerReceiver(Context context, MusicBroadcastReceiver receiver) {
        if(receiver != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                receiver.register(context);
            }
        }
        receivers.add(receiver);
    }

    private void unregisterReceivers(Context context) {
        if(!receivers.isEmpty()) {
            for (MusicBroadcastReceiver receiver : receivers) {
                try {
                    if(receiver != null) {
                        context.unregisterReceiver(receiver);
                    }
                } catch (Exception ex) {
                    Log.e(TAG,"unregisterReceivers",ex);
                }
            }
            receivers.clear();
        }
    } */

    protected PlayerInfo extractPlayer(Context context, String packageName, String playerName) {
        PlayerInfo playerInfo = PlayerInfo.buildLocalPlayer("unknown", DEAFULT_PLAYER_NAME,null);
        playerInfo.playerPackage = packageName;
        playerInfo.playerName = playerName==null?DEAFULT_PLAYER_NAME:playerName;
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(packageName, 0); // MusicListeningService.getInstance().getApplicationInfo(packageName);
            playerInfo.playerIconDrawable = context.getPackageManager().getApplicationIcon(ai);
            playerInfo.playerIconBitmap = BitmapHelper.drawableToBitmap(playerInfo.playerIconDrawable);
            if (playerName == null || playerName.equals(packageName)) {
                playerInfo.playerName = String.valueOf(context.getPackageManager().getApplicationLabel(ai));
            }
        } catch (Exception e) {
            Log.e(TAG, "extractPlayer",e);
        }
        return playerInfo;
    }

    public void setPlayingSong(Context context, String pack, String currentTitle, String currentArtist, String currentAlbum) {
        PlayerInfo player =  extractPlayer(context, pack, pack);

        if(playerInfo!= null && playerInfo.isPlayingByStreamPlayer(player)) {
           // skip local player if currently play from dlna streamer
           return;
       }

        currentTitle = StringUtils.trimTitle(currentTitle);
        currentArtist = StringUtils.trimTitle(currentArtist);
        currentAlbum = StringUtils.trimTitle(currentAlbum);

        if(provider ==null) {
            provider = FileRepository.newInstance(context);
        }
            try {
                MusicTag newPlayingSong = provider.findMediaItem(currentTitle, currentArtist, currentAlbum);
                if(newPlayingSong!=null && !newPlayingSong.equals(playingSong)) {
                    playingSong = newPlayingSong;
                    playerInfo = player;
                   // callback.onPlaying(context, playingSong);
                    AudioTagPlayingEvent.publishPlayingSong(playingSong);
                }else {
                    playingSong = null;
                }
            } catch (Exception ex) {
                Log.e(TAG,"setPlayingSong", ex);
                playingSong = null;
            }
    }

    public void setPlayingSong(PlayerInfo player, MusicTag tag) {
        if(playerInfo!= null && playerInfo.isPlayingByStreamPlayer(player)) {
            // skip local player if currently play from dlna streamer
            return;
        }

        try {
            if(tag!=null && !tag.equals(playingSong)) {
                playerInfo = player;
                playingSong = tag;
                AudioTagPlayingEvent.publishPlayingSong(playingSong);
            }else {
                playingSong = null;
            }
        } catch (Exception ex) {
            Log.e(TAG,"setPlayingSong", ex);
        }
    }

    public boolean isPlaying() {
        return (playerInfo != null && playingSong != null);
    }

    public void playNextSongOnMatched(Context context, MusicTag item) {
        if(item.equals(getPlayingSong())) {
            playNextSong(context);
        }
    }

    public  void playNextSong(Context context) {
        if(Settings.isVibrateOnNextSong(context)) {
            try {
                Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                long[] pattern = {10, 40, 10, 40,10 };
                // Vibrate for 500 milliseconds
                // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                //vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, VibrationEffect.DEFAULT_AMPLITUDE));
                // } else {
                //deprecated in API 26
                //vibrator.vibrate(100);
                //		vibrator.vibrate(pattern, -1);
                //  }
            } catch (Exception ex) {
                Log.e(TAG,"playNextSong",ex);
            }
        }
        if(playerInfo == null || playerInfo.getPlayerPackage()==null) {
            // skip control for streaming renderer/controller
            return;
        }

        if(PlayerPackageNames.NEUTRON_MUSIC_PACK_NAME.equals(playerInfo.playerPackage)) {
            // Neutron MP use
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
            audioManager.dispatchMediaKeyEvent(event);
        }else if(PlayerPackageNames.POWERAMP.equals(playerInfo.playerPackage)) {
            // call PowerAmp API
            //PowerampAPIHelper.startPAService(this, new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.COMMAND, PowerampAPI.Commands.NEXT));
            Intent intent = new Intent(PAAPI_ACTION_API_COMMAND).putExtra(PAAPI_COMMAND, PAAPI_COMMAND_NEXT);
            intent.setComponent(PAAPI_PLAYER_SERVICE_COMPONENT_NAME);
            context.startForegroundService(intent);
        }else if(PlayerPackageNames.UAPP_PACK_NAME.equals(playerInfo.playerPackage) ||
                PlayerPackageNames.FOOBAR2000.equals(playerInfo.playerPackage) ) {
              //  MusicBroadcastReceiver.PREFIX_VLC.equals(playerInfo.playerPackage)) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
            audioManager.dispatchMediaKeyEvent(event);
           //long eventTime = SystemClock.uptimeMillis();
           // audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
          //  audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
        }else if (PlayerPackageNames.HIBY_MUSIC_PACK_NAME.equals(playerInfo.playerPackage)) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            long eventTime = SystemClock.uptimeMillis();
            audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
            audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
        }else if (PlayerPackageNames.EDDICTPLAYER_PACK_NAME.equals(playerInfo.playerPackage)) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            long eventTime = SystemClock.uptimeMillis();
            audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
            audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
        }else if (PlayerPackageNames.NE_PLAYER_LITE_PACK_NAME.equals(playerInfo.playerPackage)) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
            audioManager.dispatchMediaKeyEvent(event);

            Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON);
            i.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT));
            context.sendOrderedBroadcast(i, null);

            i = new Intent(Intent.ACTION_MEDIA_BUTTON);
            i.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT));
            context.sendOrderedBroadcast(i, null);
        }else if (Settings.isUseMediaButtons(context)) {
            Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON);
            i.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT));
            context.sendOrderedBroadcast(i, null);

            i = new Intent(Intent.ACTION_MEDIA_BUTTON);
            i.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT));
            context.sendOrderedBroadcast(i, null);
        } else {
            // used for most player
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
            audioManager.dispatchMediaKeyEvent(event);
        }
    }

  /*  public void onCreate(Application musixMateApp) {
        registerReceiver(musixMateApp, new MusicBroadcastReceiver(this));
    }

    public void onTerminate(Application musixMateApp) {
        unregisterReceivers(musixMateApp);
    } */

    /*
    public void setPlayingSong(MusicTag listening) {
        playingSong = listening;
    } */
}

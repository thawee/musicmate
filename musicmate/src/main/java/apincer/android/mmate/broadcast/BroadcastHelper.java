package apincer.android.mmate.broadcast;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.Preferences;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.utils.StringUtils;

public class BroadcastHelper {
    private static final String TAG = BroadcastHelper.class.getName();
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
    private volatile static MusicPlayerInfo playerInfo;
    private static final List<MusicBroadcastReceiver> receivers = new ArrayList<>();
    private final Callback callback;
    private FileRepository provider; // = AudioFileRepository.newInstance(context);

    public BroadcastHelper(@NonNull Callback callback) {
        this.callback = callback;
    }

    public static MusicTag getPlayingSong() {
        return playingSong;
    }

    public MusicPlayerInfo getPlayerInfo() {
        return playerInfo;
    }

    public void onResume(Activity activity) {
        registerReceiver(activity, new MusicBroadcastReceiver(this));
    }

    public void onPause(Activity activity) {
        unregisterReceivers(activity);
    }

    private void registerReceiver(Context context, MusicBroadcastReceiver receiver) {
        if(receiver != null) {
            ((MusicBroadcastReceiver)receiver).register(context);
        }
       // provider = AudioFileRepository.newInstance(context);
        receivers.add(receiver);
    }

    private void unregisterReceivers(Context context) {
        if(!receivers.isEmpty()) {
            for (MusicBroadcastReceiver receiver : receivers) {
                try {
                    if(receiver != null) {
                        context.unregisterReceiver((BroadcastReceiver)receiver);
                    }
                } catch (Exception ex) {
                    Log.e(TAG,"unregisterReceivers",ex);
                }
            }
            receivers.clear();
        }
    }

    protected void setPlayingSong(Context context, String currentTitle, String currentArtist, String currentAlbum) {
        currentTitle = StringUtils.trimTitle(currentTitle);
        currentArtist = StringUtils.trimTitle(currentArtist);
        currentAlbum = StringUtils.trimTitle(currentAlbum);

        if(provider ==null) {
            provider = FileRepository.newInstance(context);
        }

        if(provider!=null) {
            try {
                MusicTag newPlayingSong = provider.findMediaItem(currentTitle, currentArtist, currentAlbum);
                if(newPlayingSong!=null && !newPlayingSong.equals(playingSong)) {
                    playingSong = newPlayingSong;
                    callback.onPlaying(context, playingSong);
                }else {
                    playingSong = null;
                }
            } catch (Exception ex) {
                Log.e(TAG,"setPlayingSong", ex);
            }
        }else {
            playingSong = null;
        }
    }

    protected void setPlayerInfo(MusicPlayerInfo playerInfo) {
        BroadcastHelper.playerInfo = playerInfo;
    }

    public static void playNextSongOnMatched(Context context, MusicTag item) {
        if(item.equals(getPlayingSong())) {
            playNextSong(context);
        }
    }

    public static void playNextSong(Context context) {
       // if(playerInfo==null) {
       //     return;
       // }

        if(Preferences.isVibrateOnNextSong(context)) {
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

        if(MusicBroadcastReceiver.PACKAGE_NEUTRON.equals(playerInfo.playerPackage)) {
            // Neutron MP use
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
            audioManager.dispatchMediaKeyEvent(event);
        }else if(MusicBroadcastReceiver.PACKAGE_POWERAMP.equals(playerInfo.playerPackage)) {
            // call PowerAmp API
            //PowerampAPIHelper.startPAService(this, new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.COMMAND, PowerampAPI.Commands.NEXT));
            Intent intent = new Intent(PAAPI_ACTION_API_COMMAND).putExtra(PAAPI_COMMAND, PAAPI_COMMAND_NEXT);
            intent.setComponent(PAAPI_PLAYER_SERVICE_COMPONENT_NAME);
            context.startForegroundService(intent);
        }else if(MusicBroadcastReceiver.PACKAGE_UAPP.equals(playerInfo.playerPackage) ||
                MusicBroadcastReceiver.PACKAGE_FOOBAR2000.equals(playerInfo.playerPackage) ||
                MusicBroadcastReceiver.PREFIX_VLC.equals(playerInfo.playerPackage)) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
            audioManager.dispatchMediaKeyEvent(event);
            //long eventTime = SystemClock.uptimeMillis();
            //audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
            //audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
        }else if (MusicMateNotificationListener.HIBY_MUSIC.equals(playerInfo.playerPackage)) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            long eventTime = SystemClock.uptimeMillis();
            audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
            audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
        }else if (MusicMateNotificationListener.SHANLING_EDDICTPLAYER.equals(playerInfo.playerPackage)) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            long eventTime = SystemClock.uptimeMillis();
            audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
            audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
        }else if (MusicMateNotificationListener.NE_PLAYER_LITE.equals(playerInfo.playerPackage)) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
            audioManager.dispatchMediaKeyEvent(event);

            Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON);
            i.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT));
            context.sendOrderedBroadcast(i, null);

            i = new Intent(Intent.ACTION_MEDIA_BUTTON);
            i.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT));
            context.sendOrderedBroadcast(i, null);
        }else if (Preferences.isUseMediaButtons(context)) {
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

    public void onCreate(Application musixMateApp) {
        registerReceiver(musixMateApp, new MusicBroadcastReceiver(this));
    }

    public void onTerminate(Application musixMateApp) {
        unregisterReceivers(musixMateApp);
    }
}

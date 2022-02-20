package apincer.android.mmate.broadcast;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.Preferences;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.repository.AudioFileRepository;
import apincer.android.mmate.utils.StringUtils;
import timber.log.Timber;

public class BroadcastHelper {
    /**
     * Poweramp package name
     */
    public static final String PAAPI_ACTION_API_COMMAND="com.maxmpz.audioplayer.API_COMMAND";
    public static final String PAAPI_COMMAND="cmd";
    public static final int PAAPI_COMMAND_NEXT=4;
    public static final String PAAPI_PACKAGE_NAME = "com.maxmpz.audioplayer";
    public static final String PAAPI_PLAYER_SERVICE_NAME = "com.maxmpz.audioplayer.player.PlayerService";
    public static final ComponentName PAAPI_PLAYER_SERVICE_COMPONENT_NAME = new ComponentName(PAAPI_PACKAGE_NAME, PAAPI_PLAYER_SERVICE_NAME);


    private static AudioTag playingSong;
    private static MusicPlayerInfo playerInfo;
    private static final List<ListeningReceiver> receivers = new ArrayList<>();
    private Callback callback;

    public BroadcastHelper(@NonNull Callback callback) {
        this.callback = callback;
        playerInfo = new MusicPlayerInfo();
    }

    public static AudioTag getPlayingSong() {
        return playingSong;
    }

    public MusicPlayerInfo getPlayerInfo() {
        return playerInfo;
    }

    public void onResume(Activity activity) {
        registerReceiver(activity, new MusicMateBroadcastReceiver(this));
    }

    public void onPause(Activity activity) {
        unregisterReceivers(activity);
    }

    private void registerReceiver(Context context, ListeningReceiver receiver) {
        if(receiver instanceof MusicMateBroadcastReceiver) {
            ((MusicMateBroadcastReceiver)receiver).register(context);
        }
        receivers.add(receiver);
    }

    private void unregisterReceivers(Context context) {
        if(!receivers.isEmpty()) {
            for (ListeningReceiver receiver : receivers) {
                try {
                    if(receiver instanceof MusicMateBroadcastReceiver) {
                        context.unregisterReceiver((BroadcastReceiver)receiver);
                    }
                } catch (Exception ex) {
                    Timber.e( ex);
                }
            }
            receivers.clear();
        }
    }

    protected void setPlayingSong(Context context, String currentTitle, String currentArtist, String currentAlbum) {
        // FIXME move to RXAndroid
        currentTitle = StringUtils.trimTitle(currentTitle);
        currentArtist = StringUtils.trimTitle(currentArtist);
        currentAlbum = StringUtils.trimTitle(currentAlbum);
        AudioFileRepository provider = AudioFileRepository.getInstance(context);
        playingSong = null;
        if(provider!=null) {
            try {
                playingSong = provider.findMediaItem(currentTitle, currentArtist, currentAlbum);
                callback.onPlaying(playingSong);
            } catch (Exception ex) {
                Timber.e( ex);
            }
        }
    }

    protected void setPlayerInfo(MusicPlayerInfo playerInfo) {
        this.playerInfo = playerInfo;
    }

    public static void playNextSongOnMatched(Context context, AudioTag item) {
        if(item.equals(getPlayingSong())) {
            playNextSong(context);
        }
    }

    public static void playNextSong(Context context) {
        if(playerInfo==null) {
            return;
        }

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
                Timber.e(ex);
            }
        }

        if(ListeningReceiver.PACKAGE_NEUTRON.equals(playerInfo.playerPackage)) {
            // Neutron MP use
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
            audioManager.dispatchMediaKeyEvent(event);
        }else if(ListeningReceiver.PACKAGE_POWERAMP.equals(playerInfo.playerPackage)) {
            // call PowerAmp API
            //PowerampAPIHelper.startPAService(this, new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.COMMAND, PowerampAPI.Commands.NEXT));
            Intent intent = new Intent(PAAPI_ACTION_API_COMMAND).putExtra(PAAPI_COMMAND, PAAPI_COMMAND_NEXT);
            intent.setComponent(PAAPI_PLAYER_SERVICE_COMPONENT_NAME);
            context.startForegroundService(intent);
        }else if(ListeningReceiver.PACKAGE_UAPP.equals(playerInfo.playerPackage) ||
                ListeningReceiver.PACKAGE_FOOBAR2000.equals(playerInfo.playerPackage) ||
                ListeningReceiver.PREFIX_VLC.equals(playerInfo.playerPackage)) {
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

}

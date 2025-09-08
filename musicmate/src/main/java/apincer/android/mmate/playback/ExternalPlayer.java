
package apincer.android.mmate.playback;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.SystemClock;
import android.view.KeyEvent;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import apincer.android.mmate.Settings;
import apincer.android.mmate.repository.database.MusicTag;

public class ExternalPlayer implements Player {
    public static String DEFAULT_PLAYER_NAME = "UNKNOWN Player";
    public static final String HIBY_MUSIC_PACK_NAME = "com.hiby.music";
    public static final String NE_PLAYER_LITE_PACK_NAME = "jp.co.radius.neplayer_lite_an";
    public static final String NEUTRON_MUSIC_PACK_NAME = "com.neutroncode.mp";
    public static final String UAPP_PACK_NAME = "com.extreamsd.usbaudioplayerpro";
    public static final String FOOBAR2000_PACK_NAME="com.foobar2000.foobar2000";
    public static final String POWERAMP_PACK_NAME = "com.maxmpz.audioplayer";

    /**
     * Poweramp package name
     */
    public static final String PAAPI_ACTION_API_COMMAND="com.maxmpz.audioplayer.API_COMMAND";
    public static final String PAAPI_COMMAND="cmd";
    public static final int PAAPI_COMMAND_NEXT=4;
    public static final String PAAPI_PACKAGE_NAME = "com.maxmpz.audioplayer";
    public static final String PAAPI_PLAYER_SERVICE_NAME = "com.maxmpz.audioplayer.player.PlayerService";
    public static final ComponentName PAAPI_PLAYER_SERVICE_COMPONENT_NAME = new ComponentName(PAAPI_PACKAGE_NAME, PAAPI_PLAYER_SERVICE_NAME);

    // Set of supported music player package names for efficient lookup
    public static final Set<String> SUPPORTED_PLAYERS = new HashSet<>(Arrays.asList(
            ExternalPlayer.HIBY_MUSIC_PACK_NAME,
            ExternalPlayer.NE_PLAYER_LITE_PACK_NAME,
            ExternalPlayer.NEUTRON_MUSIC_PACK_NAME,
            ExternalPlayer.UAPP_PACK_NAME,
            ExternalPlayer.POWERAMP_PACK_NAME
            // Add new player packages here
    ));

    private final Context context;
    private final String packageName;
    private final String displayName;
    private final Drawable icon;
    private final MutableLiveData<NowPlaying> nowPlaying = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isConnected = new MutableLiveData<>(true);

    public ExternalPlayer(Context context, String packageName, String displayName, Drawable icon) {
        this.context = context;
        this.packageName = packageName;
        this.displayName = displayName;
        this.icon = icon;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getId() {
        return packageName;
    }

    @Override
    public Drawable getIcon() {
        return icon;
    }

    @Override
    public void play(MusicTag song) {
        if(!SUPPORTED_PLAYERS.contains(packageName)) return;

        Uri musicUri = Uri.parse(song.getPath());
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(musicUri, "audio/*");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        // Update NowPlaying state to reflect that an external player is likely playing
        NowPlaying current = nowPlaying.getValue();
        if (current == null) {
            current = new NowPlaying();
        }
        current.setSong(song);
        current.setPlayingState("PLAYING"); // Assume it starts playing
        nowPlaying.postValue(current);
    }

    @Override
    public void next() {
        if(!SUPPORTED_PLAYERS.contains(packageName)) return;

        if(ExternalPlayer.NEUTRON_MUSIC_PACK_NAME.equals(packageName)) {
            // Neutron MP use
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
            dispatchMediaKeyEvent(audioManager, event);
        }else if(ExternalPlayer.POWERAMP_PACK_NAME.equals(packageName)) {
            // call PowerAmp API
            //PowerampAPIHelper.startPAService(this, new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.COMMAND, PowerampAPI.Commands.NEXT));
            Intent intent = new Intent(PAAPI_ACTION_API_COMMAND).putExtra(PAAPI_COMMAND, PAAPI_COMMAND_NEXT);
            intent.setComponent(PAAPI_PLAYER_SERVICE_COMPONENT_NAME);
            context.startForegroundService(intent);
        }else if(ExternalPlayer.UAPP_PACK_NAME.equals(packageName) ||
                ExternalPlayer.FOOBAR2000_PACK_NAME.equals(packageName) ) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
            dispatchMediaKeyEvent(audioManager, event);
            //long eventTime = SystemClock.uptimeMillis();
            // audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
            //  audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
        }else if (ExternalPlayer.HIBY_MUSIC_PACK_NAME.equals(packageName)) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            long eventTime = SystemClock.uptimeMillis();
            dispatchMediaKeyEvent(audioManager, new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
            dispatchMediaKeyEvent(audioManager, new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
       /* }else if (PlayerPackageNames.EDDICTPLAYER_PACK_NAME.equals(playerInfo.playerPackage)) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            long eventTime = SystemClock.uptimeMillis();
            audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
            audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT, 0)); */
        }else if (ExternalPlayer.NE_PLAYER_LITE_PACK_NAME.equals(packageName)) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
            dispatchMediaKeyEvent(audioManager, event);

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
            dispatchMediaKeyEvent(audioManager, event);
        }
    }

    @Override
    public void pause() {
        if(!SUPPORTED_PLAYERS.contains(packageName)) return;
        // Cannot control external player directly
        // You might want to update the NowPlaying state to PAUSED if you have a way to detect it
    }

    @Override
    public void resume() {
        if(!SUPPORTED_PLAYERS.contains(packageName)) return;
        // Cannot control external player directly
    }

    @Override
    public void stop() {
        if(!SUPPORTED_PLAYERS.contains(packageName)) return;

        // Cannot control external player directly
        NowPlaying current = nowPlaying.getValue();
        if (current != null) {
            current.setPlayingState("STOPPED");
            nowPlaying.postValue(current);
        }
    }

    @Override
    public String getDetails() {
        if(!SUPPORTED_PLAYERS.contains(packageName)) return packageName;
        return "";
    }

    private static void dispatchMediaKeyEvent(AudioManager audioManager, KeyEvent event) {
        if(audioManager != null && event != null) {
            audioManager.dispatchMediaKeyEvent(event);
        }
    }
}

package apincer.music.core.playback;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.playback.spi.PlaybackTarget;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

public class ExternalPlayer implements PlaybackTarget {
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
            HIBY_MUSIC_PACK_NAME,
            NE_PLAYER_LITE_PACK_NAME,
            NEUTRON_MUSIC_PACK_NAME,
            UAPP_PACK_NAME,
            POWERAMP_PACK_NAME,
            FOOBAR2000_PACK_NAME
            // Add new player packages here
    ));

    private final Context context;
    private final String targetId;
    private final String displayName;
    private final String description;

    private final BehaviorSubject<PlaybackState> playbackStateSubject = BehaviorSubject.createDefault(new PlaybackState());

    private ExternalPlayer(Context context, String targetId,String displayName,String description) {
        this.context = context;
        this.targetId = targetId;
        this.displayName = displayName;
        this.description = description;
    }

    @Override
    public boolean play(MediaTrack track) {
        if(!SUPPORTED_PLAYERS.contains(targetId)) return false;

        Uri musicUri = Uri.parse(track.getPath());
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(musicUri, "audio/*");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }

    @Override
    public boolean pause() {
        return false;
    }

    @Override
    public boolean resume() {
        return false;
    }

    @Override
    public boolean stop() {
        return false;
    }

    @Override
    public boolean next() {
        if(!SUPPORTED_PLAYERS.contains(targetId)) return false;

        if(ExternalPlayer.NEUTRON_MUSIC_PACK_NAME.equals(targetId)) {
            // Neutron MP use
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
            dispatchMediaKeyEvent(audioManager, event);
        }else if(ExternalPlayer.POWERAMP_PACK_NAME.equals(targetId)) {
            // call PowerAmp API
            //PowerampAPIHelper.startPAService(this, new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.COMMAND, PowerampAPI.Commands.NEXT));
            Intent intent = new Intent(PAAPI_ACTION_API_COMMAND).putExtra(PAAPI_COMMAND, PAAPI_COMMAND_NEXT);
            intent.setComponent(PAAPI_PLAYER_SERVICE_COMPONENT_NAME);
            context.startForegroundService(intent);
        }else if(ExternalPlayer.UAPP_PACK_NAME.equals(targetId) ||
                ExternalPlayer.FOOBAR2000_PACK_NAME.equals(targetId) ) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
            dispatchMediaKeyEvent(audioManager, event);
            //long eventTime = SystemClock.uptimeMillis();
            // audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
            //  audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
        }else if (ExternalPlayer.HIBY_MUSIC_PACK_NAME.equals(targetId)) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            long eventTime = SystemClock.uptimeMillis();
            dispatchMediaKeyEvent(audioManager, new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
            dispatchMediaKeyEvent(audioManager, new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
       /* }else if (PlayerPackageNames.EDDICTPLAYER_PACK_NAME.equals(playerInfo.playerPackage)) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            long eventTime = SystemClock.uptimeMillis();
            audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
            audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT, 0)); */
        }else if (ExternalPlayer.NE_PLAYER_LITE_PACK_NAME.equals(targetId)) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
            dispatchMediaKeyEvent(audioManager, event);

            Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON);
            i.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT));
            context.sendOrderedBroadcast(i, null);

            i = new Intent(Intent.ACTION_MEDIA_BUTTON);
            i.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT));
            context.sendOrderedBroadcast(i, null);
            //}else if (Settings.isUseMediaButtons(context)) {
        }else {
            Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON);
            i.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT));
            context.sendOrderedBroadcast(i, null);

            i = new Intent(Intent.ACTION_MEDIA_BUTTON);
            i.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT));
            context.sendOrderedBroadcast(i, null);
       /* } else {
            // used for most player
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
            dispatchMediaKeyEvent(audioManager, event); */
        }
        return true;
    }

    @Override
    public boolean seekTo(long positionMs) {
        return false;
    }

    @Override
    public boolean setVolume(float volume) {
        return false;
    }

    @Override
    public boolean isStreaming() {
        return false;
    }

    @Override
    public PlaybackState getPlaybackState() {
        return null;
    }

    @Override
    public String getTargetId() {
        return targetId;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void refreshPlayerState() {

    }

    private void dispatchMediaKeyEvent(AudioManager audioManager, KeyEvent event) {
        if(audioManager != null && event != null) {
            audioManager.dispatchMediaKeyEvent(event);
        }
    }

    public static class Factory {
        public static PlaybackTarget create(Context context, String packageName, String playerName) {
            if(ExternalPlayer.SUPPORTED_PLAYERS.contains(packageName)) {
                try {
                    ApplicationInfo ai = context.getPackageManager().getApplicationInfo(packageName, 0);
                    if (playerName == null || playerName.equals(packageName)) {
                        playerName = String.valueOf(context.getPackageManager().getApplicationLabel(ai));
                    }
                } catch (Exception ignore) {
                }

                playerName = (playerName == null) ? ExternalPlayer.DEFAULT_PLAYER_NAME : playerName;
                return new ExternalPlayer(context, packageName, playerName, playerName);
            }
            return null;
        }
    }
}

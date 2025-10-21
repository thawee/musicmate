package apincer.music.core.playback;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.playback.spi.PlaybackTarget;

public class ExternalAndroidPlayer implements PlaybackTarget {

   // public static String DEFAULT_PLAYER_NAME = "UNKNOWN Player";
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


    private ExternalAndroidPlayer(Context context, String targetId, String displayName, String description) {
        this.context = context;
        this.targetId = targetId;
        this.displayName = displayName;
        this.description = description;
    }

    //@Override
    public boolean play(MediaTrack track) {
        if(!SUPPORTED_PLAYERS.contains(targetId)) return false;

        Uri musicUri = Uri.parse(track.getPath());
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(musicUri, "audio/*");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }

    public boolean isStreaming() {
        return false;
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
    public boolean canReadSate() {
        return true;
    }


/*
    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            Log.d("ExternalPlayer", "onPlaybackStateChanged");
            if (state == null) {
                return;
            }

            // --- START or STOP the poller based on the state ---
            if (state.getState() == PlaybackState.STATE_PLAYING) {
                // We are playing. Start the progress updater.
                scheduleProgressUpdate();
                if(playbackCallback!= null) {
                    playbackCallback.onPlaybackStateTimeElapsedSeconds(state.getPosition());
                }
            } else {
                // We are paused, stopped, etc. Stop the progress updater.
                stopProgressUpdate();
            }
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            Log.d("ExternalPlayer", "onMetadataChanged");
            if(metadata ==null) return;

            String title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE);
            String artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST);
            String album = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM);
            long duration = metadata.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION);

            if(playbackCallback != null) {
                playbackCallback.onMediaTrackChanged(title, artist, album, duration);
            }
        }
    }; */



    public static class Factory {
        public static PlaybackTarget create(Context context, String packageName) {
            if(ExternalAndroidPlayer.SUPPORTED_PLAYERS.contains(packageName)) {
                String playerName = getAppName(context, packageName);
                return new ExternalAndroidPlayer(context, packageName, playerName, playerName);
            }
            return null;
        }


        private static String getAppName(Context context, String packageName) {
            try {
                return context.getPackageManager().getApplicationLabel(
                        context.getPackageManager().getApplicationInfo(packageName, 0)
                ).toString();
            } catch (Exception e) {
                return packageName;
            }
        }
    }
}

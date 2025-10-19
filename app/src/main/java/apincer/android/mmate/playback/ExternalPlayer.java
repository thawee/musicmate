package apincer.android.mmate.playback;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.media.session.MediaController;
import android.net.Uri;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.playback.spi.PlaybackTarget;

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

    private MediaController mediaController;

    private ExternalPlayer(Context context, MediaController mediaController, String targetId, String displayName, String description) {
        this.context = context;
        this.mediaController = mediaController;
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

    public MediaController getMediaController() {
        return mediaController;
    }

    public void unregisterCallback(MediaController.Callback externalControllerCallback) {
        mediaController.unregisterCallback(externalControllerCallback);
        mediaController = null;
    }

    public void registerCallback(MediaController.Callback externalControllerCallback) {
        mediaController.registerCallback(externalControllerCallback);
    }

    public static class Factory {
        public static PlaybackTarget create(Context context, MediaController mediaController, String packageName, String playerName) {
            if(ExternalPlayer.SUPPORTED_PLAYERS.contains(packageName)) {
                try {
                    ApplicationInfo ai = context.getPackageManager().getApplicationInfo(packageName, 0);
                    if (playerName == null || playerName.equals(packageName)) {
                        playerName = String.valueOf(context.getPackageManager().getApplicationLabel(ai));
                    }
                } catch (Exception ignore) {
                }

                playerName = (playerName == null) ? ExternalPlayer.DEFAULT_PLAYER_NAME : playerName;
                return new ExternalPlayer(context, mediaController, packageName, playerName, playerName);
            }
            return null;
        }
    }
}

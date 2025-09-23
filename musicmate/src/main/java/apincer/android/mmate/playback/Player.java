package apincer.android.mmate.playback;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;

import org.jupnp.model.meta.RemoteDevice;

import apincer.android.mmate.repository.database.MusicTag;

public interface Player {

    String getDisplayName();

    String getId();

    Drawable getIcon();

    void play(MusicTag song);

    void next();

    void pause();

    void resume();

    void stop();

    String getDetails();

    class Factory {
        public static Player create(Context context, String packageName, String playerName) {
            if(ExternalPlayer.SUPPORTED_PLAYERS.contains(packageName)) {
                Drawable icon = null;
                try {
                    ApplicationInfo ai = context.getPackageManager().getApplicationInfo(packageName, 0);
                    icon = context.getPackageManager().getApplicationIcon(ai);
                    if (playerName == null || playerName.equals(packageName)) {
                        playerName = String.valueOf(context.getPackageManager().getApplicationLabel(ai));
                    }
                } catch (Exception ignore) {
                }

                playerName = (playerName == null) ? ExternalPlayer.DEFAULT_PLAYER_NAME : playerName;
                return new ExternalPlayer(context, packageName, playerName, icon);
            }else {
                playerName = (playerName == null) ? ExternalPlayer.DEFAULT_PLAYER_NAME : playerName;
                return new StreamingPlayer(context, packageName, playerName);
            }
        }

        public static Player create(Context context, RemoteDevice renderer) {
            return new DlnaPlayer(context, renderer);
        }
    }
}
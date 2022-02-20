package apincer.android.mmate.broadcast;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

public class MusicPlayerInfo {
    String playerPackage;
    String playerName;

    public String getPlayerPackage() {
        return playerPackage;
    }

    public String getPlayerName() {
        return playerName;
    }

    public Bitmap getPlayerIconBitmap() {
        return playerIconBitmap;
    }

    public Drawable getPlayerIconDrawable() {
        return playerIconDrawable;
    }

    Bitmap playerIconBitmap;
    Drawable playerIconDrawable;
}

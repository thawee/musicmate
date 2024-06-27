package apincer.android.mmate.broadcast;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

public class MusicPlayerInfo {
    public enum TYPE{LOCAL,DLNA}
    private final TYPE playerType;
    String playerPackage;
    String playerName;

    public static MusicPlayerInfo buildStreamPlayer(String playerName,Drawable playerIconDrawable) {
        return new MusicPlayerInfo(TYPE.DLNA, "", playerName, playerIconDrawable);
    }

    public static MusicPlayerInfo buildLocalPlayer(String playerPackage, String playerName,Drawable playerIconDrawable) {
        return new MusicPlayerInfo(TYPE.LOCAL, playerPackage, playerName, playerIconDrawable);
    }

    private MusicPlayerInfo(TYPE typ, String playerPackage,String playerName,Drawable playerIconDrawable) {
        this.playerName = playerName;
        this.playerPackage = playerPackage;
        this.playerIconDrawable = playerIconDrawable;
        this.playerType = typ;
    }

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

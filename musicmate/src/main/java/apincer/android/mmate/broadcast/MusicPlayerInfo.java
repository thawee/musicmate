package apincer.android.mmate.broadcast;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

public class MusicPlayerInfo {
    public enum TYPE{LOCAL,DLNA}
    private final TYPE playerType;
     String playerPackage;
     String playerName;
    private long time;

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
        this.time = System.currentTimeMillis();
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

    public boolean isValidStreamPlayer() {
        if(playerType==TYPE.DLNA && time < System.currentTimeMillis()- 300000)  {
            // play ny dlna in last 5 mins
            return true;
        }
        return false;
    }

    Bitmap playerIconBitmap;
    Drawable playerIconDrawable;
}

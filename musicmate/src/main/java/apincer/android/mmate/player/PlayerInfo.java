package apincer.android.mmate.player;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

public class PlayerInfo {
    public void setClientAddress(String clientIp) {
    }

    public enum TYPE{LOCAL,DLNA}
    private final TYPE playerType;
     public String playerPackage;
     public String playerName;
     String clientIp;

    public String setClientAddress() {
        return clientIp;
    }

    private final long time;

    public static PlayerInfo buildStreamPlayer(String playerName, Drawable playerIconDrawable) {
        return new PlayerInfo(TYPE.DLNA, "", playerName, playerIconDrawable);
    }

    public static PlayerInfo buildLocalPlayer(String playerPackage, String playerName, Drawable playerIconDrawable) {
        return new PlayerInfo(TYPE.LOCAL, playerPackage, playerName, playerIconDrawable);
    }

    private PlayerInfo(TYPE typ, String playerPackage, String playerName, Drawable playerIconDrawable) {
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

    public boolean isStreamPlayer( ) {
        // play ny dlna
        return playerType == TYPE.DLNA;
    }

    public boolean checkStreamPlayerExpired() {
        // if stream player and played in last 5 minutes
        // if not stream, return true
        if(!isStreamPlayer()) return true;
        return (time < System.currentTimeMillis() - 300000);
    }


    public Bitmap playerIconBitmap;
    public Drawable playerIconDrawable;
}

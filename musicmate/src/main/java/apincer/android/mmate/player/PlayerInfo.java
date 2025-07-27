package apincer.android.mmate.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.jupnp.model.meta.Icon;
import org.jupnp.model.meta.RemoteDevice;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;

public class PlayerInfo {
    public void setClientAddress(String clientIp) {
    }

    public enum TYPE{LOCAL,DMC,DMR}
    private final TYPE playerType;
     public String playerPackage;
     public String playerName;
     String clientIp;

    private final long time;

    public static PlayerInfo buildStreamPlayer(Context context, String playerName, String clientIp) {
        //todo should detect name of dlna server first, and fallback for header if fail
       // this.clientIp = clientIp;
        RemoteDevice dev = MusixMateApp.getInstance().getRenderer(clientIp);
        if(dev != null) {
             String name = dev.getDetails().getFriendlyName();
             String model = dev.getDetails().getSerialNumber();
            Drawable playerIconDrawable = null;
            if(name.toLowerCase().contains("ropieee")) {
                playerIconDrawable = ContextCompat.getDrawable(context, R.drawable.ropieee);
            }else if(name.toLowerCase().contains("volumio")) {
                playerIconDrawable = ContextCompat.getDrawable(context, R.drawable.volumio);
            }else {
                playerIconDrawable = ContextCompat.getDrawable(context, R.drawable.img_upnp_white);
            }

            return new PlayerInfo(TYPE.DMR, model, name, playerIconDrawable);
        }else {
            Drawable playerIconDrawable = ContextCompat.getDrawable(context, R.drawable.rounded_airplay_24);
            return new PlayerInfo(TYPE.DMR, "", playerName, playerIconDrawable);
        }
    }

    public static PlayerInfo buildLocalPlayer(String playerPackage, String playerName, Drawable playerIconDrawable) {
        return new PlayerInfo(TYPE.LOCAL, playerPackage, playerName, playerIconDrawable);
    }

    public static void saveDeviceIcon(RemoteDevice device, String filePath) throws Exception {
        Icon[] icons = device.getIcons();
        if (icons != null && icons.length > 0) {
            URL iconUrl = icons[0].getUri().toURL();
            try (InputStream in = iconUrl.openStream();
                 FileOutputStream out = new FileOutputStream(filePath)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                System.out.println("Icon saved to " + filePath);
            }
        } else {
            System.out.println("No icons found for the device.");
        }
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

    public TYPE getPlayerType() {
        return  playerType;
    }

    public Bitmap getPlayerIconBitmap() {
        return playerIconBitmap;
    }

    public Drawable getPlayerIconDrawable() {
        return playerIconDrawable;
    }

    public boolean isStreamPlayer( ) {
        // play ny dlna
        return playerType == TYPE.DMC || playerType == TYPE.DMR;
    }

    public boolean checkStreamPlayerExpired() {
        // if stream player and played in last 5 minutes
        // if not stream, return true
        if(!isStreamPlayer()) return true;
        return (time < System.currentTimeMillis() - 300000);
    }


    public Bitmap playerIconBitmap;
    public Drawable playerIconDrawable;

    @Override
    public boolean equals(@Nullable Object obj) {
        if(obj == null) return false;
        if(obj instanceof PlayerInfo) {
            playerName.equals(((PlayerInfo) obj).getPlayerName());
        }
        return super.equals(obj);
    }
}

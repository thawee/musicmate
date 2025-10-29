package apincer.music.core.playback;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.playback.spi.PlaybackTarget;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

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

    public static class Factory {
        public static PlaybackTarget create(Context context, String packageName) {
            if(ExternalAndroidPlayer.SUPPORTED_PLAYERS.contains(packageName)) {
                String playerName = getAppName(context, packageName);
                String playerVersion = getAppVersionName(context, packageName);
                String playerDescription = getAppDescription(context, packageName);
                if(playerDescription == null) {
                    playerDescription = playerVersion;
                }else {
                    playerName = playerName +" "+ playerVersion;
                }
                return new ExternalAndroidPlayer(context, packageName, playerName, playerDescription);
            }
            return null;
        }

             /* DANGEROUS: This method performs networking and MUST NOT be
             * called on the main (UI) thread. It will crash your app.
             * Use a background thread (Executor, AsyncTask, etc.).
             *
             * @return The Play Store description, or null if not found or an error occurred.
             */
            private static String getPlayStoreDescription(Context context, String packageName) {
                // The Context isn't strictly needed here, but we'll keep your signature
                try {
                    final String playStoreUrl = "https://play.google.com/store/apps/details?id="
                            + packageName
                            + "&hl=en"; // Force English language

                    Request request = new Request.Builder()
                            .url(playStoreUrl)
                            // Add a User-Agent to mimic a browser, increasing reliability
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                            .build();

                    OkHttpClient client = new OkHttpClient();
                    // This line will CRASH if on the main thread
                    Response response = client.newCall(request).execute();

                    ResponseBody body = response.body();
                    if (!response.isSuccessful() || body == null) {
                        return null; // Page failed to load
                    }

                    // Parse the HTML
                    String html = body.string();
                    Document doc = Jsoup.parse(html);

                    // Find the <meta> tag with itemprop="description"
                    // This is more reliable than finding a <div> by its class name
                    Element descriptionMeta = doc.select("meta[itemprop=description]").first();

                    if (descriptionMeta != null) {
                        // Get the text from its "content" attribute
                        return descriptionMeta.attr("content");
                    }

                    // Fallback: Try to find the description text container
                    // This selector is FRAGILE and will break if Google changes their HTML
                    Element descDiv = doc.select("div[jsname=sngebd]").first();
                    if (descDiv != null) {
                        return descDiv.text();
                    }

                    return null; // Description not found

                } catch (Exception e) {
                    e.printStackTrace();
                    return null; // An error occurred
                }
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

        public static String getAppVersionName(Context context, String packageName) {
            try {
                PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
                return packageInfo.versionName;
            } catch (PackageManager.NameNotFoundException e) {
                return "N/A";
            }
        }

        public static Drawable getAppIcon(Context context, String packageName) {
            try {
                return context.getPackageManager().getApplicationIcon(packageName);
            } catch (PackageManager.NameNotFoundException e) {
                // Return a default icon or null
                return null;
            }
        }

        public static String getAppDescription(Context context, String packageName) {
            PackageManager pm = context.getPackageManager();
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);

                // The description is stored as a resource ID. 0 means not set.
                if (appInfo.descriptionRes != 0) {
                    // We use getText() to load the string from the other app's resources
                    CharSequence description = pm.getText(packageName, appInfo.descriptionRes, appInfo);
                    if (description != null) {
                        return description.toString();
                    }
                }
            } catch (Exception e) {
                // e.g., PackageManager.NameNotFoundException
                e.printStackTrace();
            }

            // Return null or an empty string if no description is found
            return null;
        }
    }
}

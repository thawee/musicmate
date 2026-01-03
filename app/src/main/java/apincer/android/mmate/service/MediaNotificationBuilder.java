package apincer.android.mmate.service;
import static apincer.android.mmate.service.MusicMateServiceImpl.CHANNEL_ID;
import static apincer.android.mmate.service.MusicMateServiceImpl.OFFLINE_STATUS;
import static apincer.android.mmate.service.MusicMateServiceImpl.ONLINE_STATUS;
import static apincer.android.mmate.service.MusicMateServiceImpl.SERVICE_ID;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.palette.graphics.Palette;
import com.google.android.material.color.MaterialColors;

import java.io.IOException;
import java.io.InputStream;

import apincer.android.mmate.R;
import apincer.android.mmate.coil3.CoverartFetcher;
import apincer.android.mmate.ui.MainActivity;
import apincer.music.core.Constants;
import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.playback.spi.PlaybackTarget;
import apincer.music.core.server.spi.MediaServerHub;
import coil3.BitmapImage;
import coil3.Image;
import coil3.ImageLoader;
import coil3.SingletonImageLoader;
import coil3.request.ImageRequest;
import coil3.size.Scale;
import coil3.target.Target;

public class MediaNotificationBuilder {

    private static void showPlaybackNotification(Context context, PlaybackTarget player, Bitmap albumArt, String title, String artist) {

        // Make sure bitmap is software-based for Palette
        Bitmap safeAlbumArt = ensureSoftwareBitmap(albumArt);

        Palette.from(safeAlbumArt).generate(palette -> {
            int surface = MaterialColors.getColor(
                    context, com.google.android.material.R.attr.colorSurface, Color.DKGRAY);

            int dominantColor = surface;
            if (palette != null) {
                dominantColor = palette.getDominantColor(surface);
            }

            // Blend for Material-style background
            dominantColor = blendColors(dominantColor, surface, 0.6f);
            // Slightly transparent for nice overlay look
            dominantColor = withAlpha(dominantColor, 0.85f);

            String subText = "on " + player.getDisplayName();
            int onDominantColor = getContrastingTextColor(dominantColor);

            Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setSmallIcon(R.drawable.ic_notification_default)
                    .setTicker(title)
                    .setContentTitle(title)
                    .setContentText(artist)
                    .setLargeIcon(safeAlbumArt)
                   // .setContentTitle(Constants.getPresentationName())
                    .setSubText(subText)
                    .setColor(dominantColor)          // main tint
                    .setColorized(true)               // enables background tinting
                    //.setOngoing(true)
                    .setStyle(new MediaStyle()
                    //                .setShowActionsInCompactView(0, 1, 2)
                            // optional if you have MediaSession
                            //.setMediaSession(mediaSession.getSessionToken())
                    )
                    /*.setStyle(new NotificationCompat.BigPictureStyle()
                            .bigPicture(safeAlbumArt)
                            .bigLargeIcon((Bitmap) null) // hides duplicate icon
                            .showBigPictureWhenCollapsed(true))

                     */
                    /*
                    // Example playback actions (replace with yours)
                    .addAction(R.drawable.ic_previous, "Previous", null)
                    .addAction(R.drawable.ic_play_arrow, "Play", null)
                    .addAction(R.drawable.ic_next, "Next", null)

                    */
                    .build();

            NotificationManager nm = ContextCompat.getSystemService(context, NotificationManager.class);
            nm.notify(SERVICE_ID, notification);
        });
    }

    @Deprecated
    private void notifyPlayingSong(Context context, PlaybackTarget player, final MediaTrack track,Bitmap albumArt) {
        if(track == null || albumArt == null) return;

        final Bitmap albumArtBitmap = ensureSoftwareBitmap(albumArt);

        Palette.from(albumArtBitmap).generate(palette -> {
           /* int fallbackColor = MaterialColors.getColor(getApplicationContext(), com.google.android.material.R.attr.colorSurface, Color.DKGRAY);
            int dominantColor = fallbackColor;
            if (palette != null) {
                dominantColor = palette.getDominantColor(fallbackColor);
            }
            int onDominantColor = getContrastingTextColor(dominantColor);

            // Make it 40% opaque (60% transparent)
            dominantColor = withAlpha(dominantColor, 0.9f);
            notifyPlayingSong(player, track, albumArtBitmap, dominantColor, onDominantColor); */

            int surface = MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorSurface,
                    Color.DKGRAY
            );

            int dominantColor = surface;
            if (palette != null) {
                dominantColor = palette.getDominantColor(surface);
            }

            // Blend and fade for Material style
            dominantColor = blendColors(dominantColor, surface, 0.6f);
            dominantColor = withAlpha(dominantColor, 0.85f);

            int onDominantColor = getContrastingTextColor(dominantColor);
            notifyPlayingSong(context,player, track, albumArtBitmap, dominantColor, onDominantColor);
        });
    }

    private void notifyPlayingSong(Context context, PlaybackTarget player, MediaTrack track, Bitmap albumArtBitmap, int bgColor, int textColor) {
        boolean isPlaying = true;

        RemoteViews collapsed = new RemoteViews(context.getPackageName(), R.layout.notification_collapsed);
        RemoteViews expanded  = new RemoteViews(context.getPackageName(), R.layout.notification_expanded);

        expanded.setInt(R.id.notification_root, "setBackgroundColor", bgColor);
        collapsed.setTextColor(R.id.title, textColor);
        collapsed.setTextColor(R.id.artist, textColor);
        expanded.setTextViewText(R.id.title, track.getTitle());
        expanded.setTextViewText(R.id.artist, track.getArtist());
        expanded.setImageViewBitmap(R.id.album_art_large, albumArtBitmap);

        collapsed.setInt(R.id.notification_root, "setBackgroundColor", bgColor);
        collapsed.setTextColor(R.id.title_collapsed, textColor);
        collapsed.setTextColor(R.id.artist_collapsed, textColor);
        collapsed.setTextViewText(R.id.title_collapsed, track.getTitle());
        collapsed.setTextViewText(R.id.artist_collapsed, track.getArtist());
        collapsed.setImageViewBitmap(R.id.album_art_small, albumArtBitmap);

        String subText = "on " + player.getDisplayName();

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_default)
                .setContentTitle(Constants.getPresentationName())
                .setSubText(subText)
                .setCustomContentView(collapsed)
                .setCustomBigContentView(expanded)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                //.setOngoing(isPlaying)
                .setColor(bgColor)
                .setColorized(true)
                .build();

        //notificationManager.notify(SERVICE_ID, notification);
        NotificationManager nm = ContextCompat.getSystemService(context, NotificationManager.class);
        nm.notify(SERVICE_ID, notification);
    }

    /**
     * Creates a notification for when the app is streaming to a player without playback controls.
     * It displays full track information, the server status, and the player it's streaming to.
     *
     * @param track    The currently streaming media track. Can be null.
     * @param player   The target player receiving the stream. Must not be null.
     * @param albumArt   The current albumArt of streaming media track.
     * @return A configured NotificationCompat.Builder.
     */
    private static NotificationCompat.Builder createStreamingNotification(
            Context context,
            @Nullable MediaTrack track,
            @NonNull PlaybackTarget player,
            @NonNull Bitmap albumArt) {

        String title;
        String contentText;
        String subText;

        // --- 1. Set up the main text content based on track availability ---
        if (track != null) {
            title = track.getTitle();
            // Use the artist for the content text. Provide a fallback if it's missing.
            if (track.getArtist() != null && !track.getArtist().isEmpty()) {
                contentText = "by "+track.getArtist();
            } else {
                contentText = "Streaming audio"; // A generic but clear fallback
            }
        } else {
            // Fallback text for when there is no track info
            title = "Streaming...";
            contentText = "Streaming audio"; // A generic but clear fallback
        }

        // --- 2. Create a rich subtext with player name ---
        subText = "on " + player.getDisplayName();

        // --- 4. Build the notification ---
        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_default)
                .setContentTitle(title)
                .setContentText(contentText)
                .setSubText(subText)
                .setLargeIcon(albumArt)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setOngoing(true) // Streaming notifications should be sticky
                .setOnlyAlertOnce(true);

        /*
        if(isControllable()) {
            // for controlled from webui
            // Create media control intents
            PendingIntent playPauseIntent = createActionIntent(ACTION_PLAY_PAUSE); // Implement these
            PendingIntent nextIntent = createActionIntent(ACTION_NEXT);
            PendingIntent prevIntent = createActionIntent(ACTION_PREVIOUS);

            int playPauseIcon = isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play_arrow;

            // Add media control actions
            builder.addAction(R.drawable.ic_previous, "Previous", prevIntent);
            builder.addAction(playPauseIcon, "Play/Pause", playPauseIntent);
            builder.addAction(R.drawable.ic_next, "Next", nextIntent);
        } */

        // Apply the Style
        builder.setStyle(new NotificationCompat.BigPictureStyle()
                .bigPicture(albumArt)
                // This is a common trick: hide the circular largeIcon when the
                // bigPicture is shown to avoid having two copies of the image.
                .bigLargeIcon((Bitmap) null)
        );

        return builder;
    }


    /**
     * Creates a default album art bitmap by loading "no_cover.png" from the app's assets folder.
     *
     * @return The Bitmap loaded from assets, or null if the file cannot be found or read.
     */
    private static Bitmap loadDefaultAlbumArt(Context context) {
        AssetManager assetManager = context.getAssets();

        // Use a try-with-resources block to ensure the InputStream is automatically closed.
        try (InputStream inputStream = assetManager.open("Covers/no_cover.png")) {
            // Decode the stream directly into a Bitmap
            return BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
            // If the file is not found or another I/O error occurs, log it for debugging.
            Log.e("MusicMateService", "Failed to load default album art from assets", e);
            // Return null as a fallback. The calling code should handle this.
            return null;
        }
    }

    /**
     * Creates a simple notification to show that we are monitoring an external player.
     * Contains no media controls to avoid conflicts.
     *
     * @param player The external player being monitored.
     * @param status The current status of the server.
     */
    private static void showPlayerNotification(
            Context context,
            @NonNull PlaybackTarget player,
            @Nullable MediaServerHub.ServerStatus status) {

        String serverInfo = (status != null && status.isOnline())
                ? ONLINE_STATUS
                : OFFLINE_STATUS;

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_default)
                .setContentTitle(Constants.getPresentationName())
                .setContentText("Connected to " + player.getDisplayName())
                .setSubText(serverInfo)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .build();

        NotificationManager nm = ContextCompat.getSystemService(context, NotificationManager.class);
        nm.notify(SERVICE_ID, notification);
    }



    /**
     * Creates a PendingIntent that will either start or stop the media servers,
     * depending on their current state. This is used for the action button on the
     * server-status notification.
     *
     * @return A PendingIntent configured to toggle the server's running state.
     */
    private static PendingIntent createServerToggleIntent(Context context, MediaServerHub.ServerStatus status) {
        String action;

        if (status != null && status.isOnline()) {
            // If the server is currently online, the button should stop it.
            action = MediaServerManager.ACTION_STOP_SERVER;
        } else {
            // If the server is offline or status is unknown, the button should start it.
            action = MediaServerManager.ACTION_START_SERVER;
        }

        // Now, create the PendingIntent with the determined action,
        // using the same secure pattern as our media controls.
        Intent intent = new Intent(context, MusicMateServiceImpl.class);
        intent.setAction(action);

        // For Android 12 (API 31) and higher, specifying mutability is required.
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        flags |= PendingIntent.FLAG_IMMUTABLE;

        return PendingIntent.getService(context, 0, intent, flags);
    }

    /**
     * Uses Coil to asynchronously load album art and then displays it in a
     * BigPictureStyle notification.
     */
    @Deprecated
    public static void updateNotificationForStreaming(Context context, MediaTrack track, PlaybackTarget player) {
        ImageLoader imageLoader = SingletonImageLoader.get(context);

        Target target = new Target() {
            @Override
            public void onSuccess(@NonNull Image result) {
                if(result instanceof BitmapImage bitmapImage) {
                    Bitmap bitmap = bitmapImage.getBitmap();
                    // Update the builder with the real album art and re-post the notification.
                    NotificationCompat.Builder builder = createStreamingNotification(context,track, player, bitmap);
                    builder.setLargeIcon(bitmap);
                    //notificationManager.notify(SERVICE_ID, builder.build());
                    NotificationManager nm = ContextCompat.getSystemService(context, NotificationManager.class);
                    nm.notify(SERVICE_ID, builder.build());
                }
            }

            @Override
            public void onError(@Nullable Image errorDrawable) {
                // Image failed to load. Show the notification with the default art instead.
                Bitmap defaultArt = loadDefaultAlbumArt(context);
                NotificationCompat.Builder builder = createStreamingNotification(context,track, player, defaultArt);
                //notificationManager.notify(SERVICE_ID, builder.build());
                NotificationManager nm = ContextCompat.getSystemService(context, NotificationManager.class);
                nm.notify(SERVICE_ID, builder.build());
            }
        };

        ImageRequest request = CoverartFetcher.builder(context, track)
                .data(track)
                // CRITICAL: Scale the image to a 2:1 aspect ratio for BigPictureStyle.
                // This prevents cropping and saves a huge amount of memory.
                //.size(1024, 512)
                .scale(Scale.FIT)
                .size(640, 320)
                .target(target)
                .build();

        imageLoader.enqueue(request);
    }


    /**
     * Builds and displays the dynamic foreground service notification.
     * <p>
     * This notification intelligently adapts its content and controls based on the current
     * playback context. It can show server status, full media controls for streaming, or
     * a simple monitoring status for external players.
     *
     * @param track  The currently active media track, containing details like title and artist.
     * This can be {@code null} if nothing is playing.
     * @param player The current playback target. This determines the type of notification to show.
     * If {@code null}, a generic server status notification is shown.
     */
    public static void updateNotification(
            @NonNull Context context,
            @Nullable MediaTrack track,
            @Nullable PlaybackTarget player,
            MediaServerHub.ServerStatus status,
            long totalTracks) {

        // Determine which type of notification to build
       /* if(player !=null && player.isStreaming()) {
            //updateNotificationForStreaming(track, player);
            updateNotificationPlaying(context, track, player);
        }else {
            NotificationCompat.Builder builder;
            if (player == null) {
                // CASE 1: No active player. Show only the server status.
                // This informs the user the service is running in the background.
                builder = createServerStatusNotification(context, status, totalTracks);
                // Post the generated notification to the system.
                // This will create or update the existing foreground service notification.
                //notificationManager.notify(SERVICE_ID, builder.build());
                NotificationManager nm = ContextCompat.getSystemService(context, NotificationManager.class);
                nm.notify(SERVICE_ID, builder.build());

                // } else if (player.isStreaming() && isControllable()) {
                // CASE 2: Controlling a streaming player (e.g., DLNA/UPnP).
                // This shows full track info and media playback controls.
                //     builder = createStreamingControlNotification(track, player, status);
                // } else if (player.isStreaming()) {
                // CASE 3: Controlling a streaming player (e.g., JPlay, mConnect).
                // This shows server info, full track info and NO media playback controls.
                //     builder = createStreamingNotification(track, player, status);
            } else {
                // CASE 4: Monitoring an external, non-controllable player (e.g., Spotify Connect).
                // This shows a simple status to avoid conflicting with the other app's notification.
                // builder = createMonitoringNotification(player, status);
                updateNotificationPlaying(context, track, player);
            } */

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Music Playback", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Music playback controls");
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }

        if (player == null) {
            // CASE 1: No active player. Show only the server status.
            // This informs the user the service is running in the background.
            showServerStatusNotification(context, status, totalTracks);
        }else if(track == null){
            showPlayerNotification(context, player, status);
        }else {
            showPlaybackNotification(context, track, player);
        }
    }

    /**
     * Uses Coil to asynchronously load album art and then displays it in a
     * BigPictureStyle notification.
     */
    private static void showPlaybackNotification(Context context, MediaTrack track, PlaybackTarget player) {

        Target target = new Target() {
            @Override
            public void onStart(@Nullable Image placeholder) {
                Target.super.onStart(placeholder);
            }

            @Override
            public void onSuccess(@NonNull Image result) {
                if(result instanceof BitmapImage bitmapImage) {
                    Bitmap bitmap = bitmapImage.getBitmap();
                    //notifyPlayingSong(player, track, bitmap);
                    showPlaybackNotification(context, player,bitmap, track.getTitle(), track.getArtist());
                }
            }

            @Override
            public void onError(@Nullable Image errorDrawable) {
                // Image failed to load. Show the notification with the default art instead.
                Bitmap defaultArt = loadDefaultAlbumArt(context);
                // notifyPlayingSong(player, track, defaultArt);
                showPlaybackNotification(context, player,defaultArt, track.getTitle(), track.getArtist());
            }
        };

        ImageLoader imageLoader = SingletonImageLoader.get(context);
        ImageRequest request = CoverartFetcher.builder(context, track)
                .data(track)
                // CRITICAL: Scale the image to a 2:1 aspect ratio for BigPictureStyle.
                // This prevents cropping and saves a huge amount of memory.
                //.size(1024, 512)
                .scale(Scale.FIT)
                .size(640, 320)
                .target(target)
                .build();
        imageLoader.enqueue(request);
    }

    /**
     * Creates a PendingIntent for a media control action.
     * This intent is configured to be sent back to this service when a notification
     * button is pressed.
     *
     * @param action The specific action string (e.g., ACTION_PLAY_PAUSE) for the intent.
     * @return A configured PendingIntent ready to be attached to a notification action.
     */
    private static PendingIntent createActionIntent(Context context, String action) {
        // Create an intent that will be directed back to this same service
        Intent intent = new Intent(context, MusicMateServiceImpl.class);
        intent.setAction(action);

        // For Android 12 (API 31) and higher, specifying mutability is required.
        // FLAG_IMMUTABLE is the recommended and more secure choice for intents
        // that don't need to be modified by the receiving app.
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        flags |= PendingIntent.FLAG_IMMUTABLE;

        // The request code (the second parameter, here 0) can be used to differentiate
        // between pending intents, but since we use unique actions, it's not critical.
        return PendingIntent.getService(context, 0, intent, flags);
    }

    /**
     * Creates a notification that only displays the media server status.
     * Used when no player is active.
     *
     * @param status The current status of the server.
     */
    private static void showServerStatusNotification(
            Context context,
            @Nullable MediaServerHub.ServerStatus status,
            long tracks) {
        String contentText = "Tap to start the server";
        String statusText = OFFLINE_STATUS;
       // int tracks = (status != null) ? (int) tagRepos.getTotalSongs() : 0;
        PendingIntent toggleIntent = createServerToggleIntent(context, status); // Needs to be implemented
        int toggleIcon = R.drawable.ic_play_arrow;
        String toggleTitle = "Start Server";

        if (status != null && status.isOnline()) {
            statusText = ONLINE_STATUS;
            contentText = tracks + " tracks available";
            toggleIcon = R.drawable.ic_stop;
            toggleTitle = "Stop Server";
        }

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_default)
                .setContentTitle(Constants.getPresentationName())
                .setContentText(contentText)
                .setSubText(statusText)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .addAction(toggleIcon, toggleTitle, toggleIntent)
                .build();

        NotificationManager nm = ContextCompat.getSystemService(context, NotificationManager.class);
        nm.notify(SERVICE_ID, notification);
    }

    // --- Helper: Ensure software bitmap (for Palette)
    private static Bitmap ensureSoftwareBitmap(Bitmap input) {
        if (input == null) return null;
        if (input.getConfig() != Bitmap.Config.HARDWARE) return input;
        return input.copy(Bitmap.Config.ARGB_8888, false);
    }

    // --- Helper: Make color semi-transparent
    private static int withAlpha(int color, float alphaFraction) {
        int alpha = Math.round(255 * alphaFraction);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    // --- Helper: Blend two colors
    private static int blendColors(int color1, int color2, float ratio) {
        final float inverseRation = 1f - ratio;
        float r = Color.red(color1) * ratio + Color.red(color2) * inverseRation;
        float g = Color.green(color1) * ratio + Color.green(color2) * inverseRation;
        float b = Color.blue(color1) * ratio + Color.blue(color2) * inverseRation;
        return Color.rgb((int) r, (int) g, (int) b);
    }

    // --- Helper: Choose contrasting text color (white or black)
    private static int getContrastingTextColor(int color) {
        double luminance = (0.299 * Color.red(color) +
                0.587 * Color.green(color) +
                0.114 * Color.blue(color)) / 255;
        return luminance > 0.5 ? Color.BLACK : Color.WHITE;
    }
}

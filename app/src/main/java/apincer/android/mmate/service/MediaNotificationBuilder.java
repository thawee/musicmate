package apincer.android.mmate.service;

import static apincer.android.mmate.service.MusicMateServiceImpl.CHANNEL_ID;
import static apincer.android.mmate.service.MusicMateServiceImpl.SERVER_STATUS_OFFLINE;
import static apincer.android.mmate.service.MusicMateServiceImpl.SERVER_STATUS_ONLINE_PREFIX;
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
import apincer.music.core.model.Track;
import apincer.music.core.playback.spi.PlaybackTarget;
import apincer.music.core.server.spi.MediaServerHub;
import coil3.BitmapImage;
import coil3.Image;
import coil3.ImageLoader;
import coil3.SingletonImageLoader;
import coil3.request.ImageRequest;
import coil3.size.Scale;
import coil3.target.Target;

/**
 * Utility for constructing and displaying MusicMate foreground service notifications.
 */
public class MediaNotificationBuilder {

    private static PendingIntent createContentIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void showPlaybackNotification(Context context, PlaybackTarget player, Bitmap albumArt, String title, String artist) {
        Bitmap safeAlbumArt = ensureSoftwareBitmap(albumArt);

        Palette.from(safeAlbumArt).generate(palette -> {
            int surface = MaterialColors.getColor(
                    context, com.google.android.material.R.attr.colorSurface, Color.DKGRAY);

            int dominantColor = surface;
            if (palette != null) {
                dominantColor = palette.getDominantColor(surface);
            }

            dominantColor = blendColors(dominantColor, surface, 0.6f);
            dominantColor = withAlpha(dominantColor, 0.85f);

            String subText = "with " + player.getDisplayName();

            Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setSmallIcon(R.drawable.ic_notification_default)
                    .setTicker(title)
                    .setContentTitle(title)
                    .setContentText(artist)
                    .setLargeIcon(safeAlbumArt)
                    .setSubText(subText)
                    .setColor(dominantColor)
                    .setColorized(true)
                    .setContentIntent(createContentIntent(context))
                    .setStyle(new MediaStyle())
                    .build();

            notify(context, notification);
        });
    }

    private static Bitmap loadDefaultAlbumArt(Context context) {
        AssetManager assetManager = context.getAssets();
        try (InputStream inputStream = assetManager.open("Covers/no_cover.png")) {
            return BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
            Log.e("MusicMateService", "Failed to load default album art from assets", e);
            return null;
        }
    }

    private static void showPlayerNotification(
            Context context,
            @NonNull PlaybackTarget player,
            @Nullable MediaServerHub.ServerStatus status) {

        String serverInfo = (status != null && status.isOnline())
                ? SERVER_STATUS_ONLINE_PREFIX
                : SERVER_STATUS_OFFLINE;

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_default)
                .setContentTitle(Constants.getPresentationName())
                .setContentText("Connected with " + player.getDisplayName())
                .setSubText(serverInfo)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(createContentIntent(context))
                .setOnlyAlertOnce(true)
                .build();

        notify(context, notification);
    }

    private static PendingIntent createServerToggleIntent(Context context, MediaServerHub.ServerStatus status) {
        String action = (status != null && status.isOnline())
                ? MediaServerManager.ACTION_STOP_SERVER
                : MediaServerManager.ACTION_START_SERVER;

        Intent intent = new Intent(context, MusicMateServiceImpl.class);
        intent.setAction(action);

        return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void notify(Context context, Notification builder) {
        NotificationManager nm = ContextCompat.getSystemService(context, NotificationManager.class);
        if (nm != null) {
            nm.notify(SERVICE_ID, builder);
        }
    }

    public static void updateNotification(
            @NonNull Context context,
            @Nullable Track track,
            @Nullable PlaybackTarget player,
            MediaServerHub.ServerStatus status,
            long totalTracks) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Music Playback", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Music playback controls");
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }

        if (player == null) {
            showServerStatusNotification(context, status, totalTracks);
        } else if (track == null) {
            showPlayerNotification(context, player, status);
        } else {
            showPlaybackNotification(context, track, player);
        }
    }

    private static void showPlaybackNotification(Context context, Track track, PlaybackTarget player) {
        Target target = new Target() {
            @Override
            public void onSuccess(@NonNull Image result) {
                if (result instanceof BitmapImage bitmapImage) {
                    Bitmap bitmap = bitmapImage.getBitmap();
                    showPlaybackNotification(context, player, bitmap, track.getTitle(), track.getArtist());
                }
            }

            @Override
            public void onError(@Nullable Image errorDrawable) {
                Bitmap defaultArt = loadDefaultAlbumArt(context);
                showPlaybackNotification(context, player, defaultArt, track.getTitle(), track.getArtist());
            }
        };

        ImageLoader imageLoader = SingletonImageLoader.get(context);
        ImageRequest request = CoverartFetcher.builder(context, track)
                .data(track)
                .scale(Scale.FIT)
                .size(640, 320)
                .target(target)
                .build();
        imageLoader.enqueue(request);
    }

    private static void showServerStatusNotification(
            Context context,
            @Nullable MediaServerHub.ServerStatus status,
            long tracks) {
        String statusText = SERVER_STATUS_OFFLINE;
        if (status != null && status.isOnline()) {
            statusText = SERVER_STATUS_ONLINE_PREFIX;
        }

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_default)
                .setContentTitle(Constants.getPresentationName())
                .setSubText(statusText)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(createContentIntent(context))
                .setOnlyAlertOnce(true)
                .build();

        notify(context, notification);
    }

    private static Bitmap ensureSoftwareBitmap(Bitmap input) {
        if (input == null) return null;
        if (input.getConfig() != Bitmap.Config.HARDWARE) return input;
        return input.copy(Bitmap.Config.ARGB_8888, false);
    }

    private static int withAlpha(int color, float alphaFraction) {
        int alpha = Math.round(255 * alphaFraction);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static int blendColors(int color1, int color2, float ratio) {
        final float inverseRation = 1f - ratio;
        float r = Color.red(color1) * ratio + Color.red(color2) * inverseRation;
        float g = Color.green(color1) * ratio + Color.green(color2) * inverseRation;
        float b = Color.blue(color1) * ratio + Color.blue(color2) * inverseRation;
        return Color.rgb((int) r, (int) g, (int) b);
    }
}

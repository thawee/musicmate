package apincer.android.mmate.coil3;

import static apincer.music.core.Constants.DEFAULT_COVERART;
import static apincer.music.core.repository.FileRepository.getCoverartDir;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

import apincer.music.core.model.Track;
import apincer.music.core.repository.FileRepository;
import coil3.ImageLoader;
import coil3.decode.DataSource;
import coil3.decode.FileImageSource;
import coil3.decode.ImageSource;
import coil3.fetch.FetchResult;
import coil3.fetch.Fetcher;
import coil3.fetch.SourceFetchResult;
import coil3.request.CachePolicy;
import coil3.request.ImageRequest;
import coil3.request.Options;
import kotlin.coroutines.Continuation;
import okio.FileSystem;
import okio.Path;

public class CoverartFetcher implements Fetcher {
    Context context;
    Track musicTag;
    public CoverartFetcher(Context context, Track musicTag) {
        this.context = context;
        this.musicTag = musicTag;
    }

   // private Image getDefaultCover() {
       // File defaultCoverartDir = new File(getCoverartDir(mContext),DEFAULT_COVERART);
        /*try {
            if(!defaultCoverartDir.exists()) {
                FileUtils.createParentDirs(defaultCoverartDir);
                InputStream in = ApplicationUtils.getAssetsAsStream(mContext.getApplicationContext(), DEFAULT_COVERART);
                Files.copy(in, defaultCoverartDir.toPath(), REPLACE_EXISTING);
            }
        } catch (IOException ignored) { }

        Bitmap bitmap = BitmapFactory.decodeFile(defaultCoverartDir.getAbsolutePath());
        */
    //    Bitmap bitmap = BitmapFactory.decodeStream(ApplicationUtils.getAssetsAsStream(context, COVER_ARTS+DEFAULT_COVERART));
   //     return new BitmapImage(bitmap, true);
   // }

    @Nullable
    @Override
    public FetchResult fetch(@NonNull Continuation<? super FetchResult> continuation) {
        File covertFile = FileRepository.getCoverArt(context, musicTag);
        String cacheKey = musicTag.getAlbumArtFilename();
        if(covertFile == null || !covertFile.exists() || covertFile.isDirectory()) {
            if (musicTag.isContainer()) {
                return null; // Return null to trigger Coil's error/fallback drawable for containers
            }
            covertFile = getDefaultCover();
            cacheKey = null;
        }
        ImageSource source = new FileImageSource(
                Path.get(covertFile),
                FileSystem.SYSTEM,
                cacheKey,
                null,
                null);

        return new SourceFetchResult(
                source,
                null, // mime type
                DataSource.DISK
        );
    }

    private File getDefaultCover() {
        File defaultCover = new File(getCoverartDir(context), DEFAULT_COVERART);
        if (!defaultCover.exists()) {
            try {
                if (!defaultCover.getParentFile().exists()) {
                    defaultCover.getParentFile().mkdirs();
                }
                try (java.io.InputStream in = context.getAssets().open("Covers/" + DEFAULT_COVERART);
                     java.io.OutputStream out = new java.io.FileOutputStream(defaultCover)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
            } catch (java.io.IOException e) {
                android.util.Log.e("CoverartFetcher", "Failed to copy default cover art from assets", e);
            }
        }
        return defaultCover;
    }

    public static class Factory implements Fetcher.Factory<Track> {
        Context context;
        public Factory(Context context) {
            this.context = context;
        }

        @Nullable
        @Override
        public Fetcher create(@NonNull Track musicTag, @NonNull Options options, @NonNull ImageLoader imageLoader) {
            return new CoverartFetcher(context, musicTag);
        }
    }

    public static ImageRequest.Builder builder(Context context, Track tag) {
        ImageRequest.Builder builder = new ImageRequest.Builder(context);
        if(tag != null) {
            builder.diskCacheKey(tag.getAlbumArtFilename());
            builder.fetcherFactory(new Factory(context), kotlin.jvm.JvmClassMappingKt.getKotlinClass(Track.class));
            if (!tag.isManaged()) {
                builder.diskCachePolicy(CachePolicy.DISABLED); // Disable disk caching
                builder.memoryCachePolicy(CachePolicy.DISABLED); // Disable memory caching
            }
        }
        return builder;
    }
}

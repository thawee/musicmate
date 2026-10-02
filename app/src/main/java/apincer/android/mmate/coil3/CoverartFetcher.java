package apincer.android.mmate.coil3;

import static apincer.music.core.Constants.DEFAULT_COVERART;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

import apincer.music.core.model.Track;
import apincer.music.core.repository.FileRepository;
import coil3.ImageLoader;
import coil3.decode.AssetMetadata;
import coil3.decode.DataSource;
import coil3.decode.FileImageSource;
import coil3.decode.ImageSource;
import coil3.decode.ImageSourceKt;
import coil3.fetch.FetchResult;
import coil3.fetch.Fetcher;
import coil3.fetch.SourceFetchResult;
import coil3.request.CachePolicy;
import coil3.request.ImageRequest;
import coil3.request.Options;
import kotlin.coroutines.Continuation;
import okio.FileSystem;
import okio.Okio;
import okio.Path;

public class CoverartFetcher implements Fetcher {
    Context context;
    Track musicTag;
    public CoverartFetcher(Context context, Track musicTag) {
        this.context = context;
        this.musicTag = musicTag;
    }

    @Nullable
    @Override
    public FetchResult fetch(@NonNull Continuation<? super FetchResult> continuation) {
        File covertFile = FileRepository.getCoverArt(context, musicTag);
        if(covertFile == null || !covertFile.exists() || covertFile.isDirectory()) {
            if (musicTag.isContainer()) {
                return null; // Return null to trigger Coil's error/fallback drawable for containers
            }
            try {
                return new SourceFetchResult(defaultCover(), "image/png", DataSource.DISK);
            } catch (java.io.IOException e) {
                android.util.Log.e("CoverartFetcher", "Cannot open the bundled default cover", e);
                return null; // Coil shows the request's error/fallback drawable
            }
        }
        ImageSource source = new FileImageSource(
                Path.get(covertFile),
                FileSystem.SYSTEM,
                musicTag.getAlbumArtFilename(),
                null,
                null);

        return new SourceFetchResult(
                source,
                null, // mime type
                DataSource.DISK
        );
    }

    /** The bundled "no cover" image, read straight from the APK's assets (nothing copied to disk). */
    private ImageSource defaultCover() throws java.io.IOException {
        String assetPath = "Covers/" + DEFAULT_COVERART;
        return ImageSourceKt.ImageSource(
                Okio.buffer(Okio.source(context.getAssets().open(assetPath))),
                FileSystem.SYSTEM,
                new AssetMetadata(assetPath));
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

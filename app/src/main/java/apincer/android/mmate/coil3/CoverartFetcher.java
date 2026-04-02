package apincer.android.mmate.coil3;

import static apincer.music.core.Constants.DEFAULT_COVERART;
import static apincer.music.core.repository.FileRepository.getCoverartDir;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
import kotlin.reflect.KCallable;
import kotlin.reflect.KClass;
import kotlin.reflect.KFunction;
import kotlin.reflect.KType;
import kotlin.reflect.KTypeParameter;
import kotlin.reflect.KVisibility;
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
        if(covertFile == null || !covertFile.exists()) {
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
        return new File(getCoverartDir(context),DEFAULT_COVERART);
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
            builder.fetcherFactory(new Factory(context), new KClassMusicTag());
            if (!tag.isManaged()) {
                builder.diskCachePolicy(CachePolicy.DISABLED); // Disable disk caching
                builder.memoryCachePolicy(CachePolicy.DISABLED); // Disable memory caching
            }
        }
        return builder;
    }

    private static class KClassMusicTag implements KClass<Track> {
        @NonNull
        @Override
        public Collection<KFunction<Track>> getConstructors() {
            return Collections.emptyList();
        }

        @Nullable
        @Override
        public String getSimpleName() {
            return "";
        }

        @Nullable
        @Override
        public String getQualifiedName() {
            return "";
        }

        @NonNull
        @Override
        public Collection<KCallable<?>> getMembers() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public Collection<KClass<?>> getNestedClasses() {
            return Collections.emptyList();
        }

        @Nullable
        @Override
        public Track getObjectInstance() {
            return null;
        }

        @Override
        public boolean isInstance(@Nullable Object o) {
            return o instanceof Track;
        }

        @NonNull
        @Override
        public List<KTypeParameter> getTypeParameters() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<KType> getSupertypes() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<KClass<? extends Track>> getSealedSubclasses() {
            return Collections.emptyList();
        }

        @Nullable
        @Override
        public KVisibility getVisibility() {
            return null;
        }

        @Override
        public boolean isFinal() {
            return false;
        }

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public boolean isAbstract() {
            return false;
        }

        @Override
        public boolean isSealed() {
            return false;
        }

        @Override
        public boolean isData() {
            return false;
        }

        @Override
        public boolean isInner() {
            return false;
        }

        @Override
        public boolean isCompanion() {
            return false;
        }

        @Override
        public boolean isFun() {
            return false;
        }

        @Override
        public boolean isValue() {
            return false;
        }

        @NonNull
        @Override
        public List<Annotation> getAnnotations() {
            return Collections.emptyList();
        }
    }
}

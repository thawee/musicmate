package apincer.android.mmate.provider;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static apincer.android.mmate.Constants.COVER_ARTS;
import static apincer.android.mmate.Constants.DEFAULT_COVERART_DLNA_RES;
import static apincer.android.mmate.Constants.DEFAULT_COVERART_FILE;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.utils.FileUtils;
import coil3.BitmapImage;
import coil3.Image;
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
    MusicTag musicTag;
    public CoverartFetcher(MusicTag musicTag) {
        this.musicTag = musicTag;
    }

    public static Image getDefaultCover(Context mContext) {
        File defaultCoverartDir = new File(getCoverartDir(COVER_ARTS),DEFAULT_COVERART_FILE);
        try {
            if(!defaultCoverartDir.exists()) {
                FileUtils.createParentDirs(defaultCoverartDir);
                InputStream in = ApplicationUtils.getAssetsAsStream(MusixMateApp.getInstance(), DEFAULT_COVERART_DLNA_RES);
                Files.copy(in, defaultCoverartDir.toPath(), REPLACE_EXISTING);
            }

        } catch (IOException ignored) { }

        Bitmap bitmap = BitmapFactory.decodeFile(defaultCoverartDir.getAbsolutePath());

        return new BitmapImage(bitmap, true);
    }

    @Nullable
    @Override
    public FetchResult fetch(@NonNull Continuation<? super FetchResult> continuation) {
        File covertFile = FileRepository.getCoverArt(musicTag);
        String key = musicTag.isMusicManaged()?musicTag.getAlbumUniqueKey():musicTag.getUniqueKey();
        if(covertFile == null) {
            // FIXME: should remove, cover already extracted during scan
            covertFile = FileRepository.newInstance(MusixMateApp.getInstance()).extractCoverArt(musicTag);
        }
        if(covertFile != null) {
            Path imagePath = Path.get(covertFile);

            ImageSource source = new FileImageSource(imagePath, FileSystem.SYSTEM, key, null, null);

            return new SourceFetchResult(
                    source,
                    null, // mime type
                    DataSource.DISK
            );
        }

        return null;
    }

    public static File getCoverartDir(String coverartName) {
        File coverartDir = MusixMateApp.getInstance().getExternalCacheDir();
        return new File(coverartDir, coverartName);
    }

    public static class Factory implements Fetcher.Factory<MusicTag> {

        @Nullable
        @Override
        public Fetcher create(@NonNull MusicTag musicTag, @NonNull Options options, @NonNull ImageLoader imageLoader) {
            return new CoverartFetcher(musicTag);
        }
    }

    public static ImageRequest.Builder builder(Context context, MusicTag tag) {
        ImageRequest.Builder builder = new ImageRequest.Builder(context);
        builder.fetcherFactory(new Factory(), new KClassMusicTag());
        if(!tag.isMusicManaged()) {
            builder.diskCachePolicy(CachePolicy.DISABLED); // Disable disk caching
            builder.memoryCachePolicy(CachePolicy.DISABLED); // Disable memory caching
        }
        return builder;
    }

    private static class KClassMusicTag implements KClass<MusicTag> {
        @NonNull
        @Override
        public Collection<KFunction<MusicTag>> getConstructors() {
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
        public MusicTag getObjectInstance() {
            return null;
        }

        @Override
        public boolean isInstance(@Nullable Object o) {
            return o instanceof MusicTag;
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
        public List<KClass<? extends MusicTag>> getSealedSubclasses() {
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

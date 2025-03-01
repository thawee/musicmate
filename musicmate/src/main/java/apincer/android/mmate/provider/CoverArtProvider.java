package apincer.android.mmate.provider;

import static apincer.android.mmate.Constants.COVER_ARTS;
import static apincer.android.mmate.Constants.DEFAULT_COVERART_FILE;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.nio.file.Files;

import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.TagRepository;
import apincer.android.mmate.utils.ParcelFileDescriptorUtil;
import apincer.android.utils.FileUtils;

@Deprecated
public final class CoverArtProvider extends ContentProvider {
    private static final String TAG = "CoverArtProvider";

    public static Uri getUriForMusicTag(Context context, MusicTag item) {
            // use music file
       // MusicMateExecutors.execute(() -> {
            try {
                // extract cover art during build uri in background
                File dir = context.getExternalCacheDir();
                String albumUniqueKey = item.getAlbumUniqueKey(); // getPathForUri(uri);
                String path = getCacheCover(albumUniqueKey);

                File pathFile = new File(dir, path);
                if (!pathFile.exists()) {
                    FileUtils.createParentDirs(pathFile);
                    //dir = pathFile.getParentFile();
                    //dir.mkdirs();
                    // extract covert to cache directory
                    MusicTag tag = TagRepository.getMusicTagAlbumUniqueKey(albumUniqueKey);
                    FileRepository.newInstance(context).extractCoverArt(tag);
                }
            } catch (Exception e) {
                Log.d(TAG,"getUriForMusicTag: ", e);
            }
//});

            return new Builder().scheme("content").authority("apincer.mmate.coverart.provider").path(item.getAlbumUniqueKey()).build();
        }

    private static String getCacheCover(String albumUnique) {
        return COVER_ARTS +albumUnique+".png";
    }

    private String getPathForUri(Uri uri) {
            if (getContext() != null && uri != null && uri.getPath()!=null) {
                return uri.getPath().substring(1);
            }
            throw new IllegalStateException("No Context attached");
        }

        public int delete(@NonNull Uri uri, String str, String[] strArr) {
            throw new UnsupportedOperationException("No support delete");
        }

        public String getType(@NonNull Uri uri) {
            return "image/*";
        }

        public Uri insert(@NonNull Uri uri, ContentValues contentValues) {
            throw new UnsupportedOperationException("No support inserts");
        }

        public boolean onCreate() {
            return true;
        }

    @Nullable
    @Override
    public Cursor query(@NonNull @android.support.annotation.NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        throw new UnsupportedOperationException("No support query");
    }

    public String getCacheCover(Uri uri) {
        return COVER_ARTS +getPathForUri(uri)+".png";
    }

    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String str) {
            try {
                File dir =  getContext().getExternalCacheDir();
                String path = getCacheCover(uri);

                File pathFile = new File(dir, path);
                if(pathFile.exists()) {
                    //Log.d(TAG,"found "+pathFile.toPath());
                    return ParcelFileDescriptorUtil.pipeFrom(Files.newInputStream(pathFile.toPath()));
                }else {
                    File defaultCover = new File(dir, COVER_ARTS);
                    defaultCover = new File(defaultCover, DEFAULT_COVERART_FILE);
                    return ParcelFileDescriptorUtil.pipeFrom(Files.newInputStream(defaultCover.toPath()));
                }
            } catch (Exception e) {
                Log.d(TAG,"openFile: ", e);
            }
            return null;
        }

        public int update(@NonNull Uri uri, ContentValues contentValues, String str, String[] strArr) {
            throw new UnsupportedOperationException("No support updates");
        }
    }

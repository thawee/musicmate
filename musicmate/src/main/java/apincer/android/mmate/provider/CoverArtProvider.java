package apincer.android.mmate.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
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

public final class CoverArtProvider extends ContentProvider {
    private static final String TAG = "CoverArtProvider";
    public static final String COVER_ARTS = "/CoverArts/";

    public static Uri getUriForMusicTag(MusicTag item) {
            // use music file
            return new Builder().scheme("content").authority("apincer.mmate.coverart.provider").path(item.getAlbumUniqueKey()).build();
        }

       private String getPathForUri(Uri uri) {
            if (getContext() != null) {
                return uri.getPath().substring(1);
            }
            throw new IllegalStateException("No Context attached");
        }

        public int delete(Uri uri, String str, String[] strArr) {
            throw new UnsupportedOperationException("No support delete");
        }

        public String getType(Uri uri) {
            return "image/*";
        }

        public Uri insert(Uri uri, ContentValues contentValues) {
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

    public ParcelFileDescriptor openFile(Uri uri, String str) {
            try {
                File dir =  getContext().getExternalCacheDir();
                String albumUniqueKey = getPathForUri(uri);
                String path = getCacheCover(uri);

                File pathFile = new File(dir, path);
                if(!pathFile.exists()) {
                        dir = pathFile.getParentFile();
                        dir.mkdirs();
                        // extract covert to cache directory
                        MusicTag tag = TagRepository.getMusicTagAlbumUniqueKey(albumUniqueKey);
                        FileRepository.extractCoverArt(tag, pathFile);
                        pathFile = new File(dir, path);
                }
                if(pathFile.exists()) {
                    //Log.d(TAG,"found "+pathFile.toPath());
                    return ParcelFileDescriptorUtil.pipeFrom(Files.newInputStream(pathFile.toPath()));
                }
            } catch (Exception e) {
                Log.d(TAG,"openFile: ", e);
            }
            Log.d(TAG,"no coverart for "+uri.getPath());
            return null;
        }

        public int update(Uri uri, ContentValues contentValues, String str, String[] strArr) {
            throw new UnsupportedOperationException("No support updates");
        }
    }

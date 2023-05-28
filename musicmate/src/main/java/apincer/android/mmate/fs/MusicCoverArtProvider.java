package apincer.android.mmate.fs;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.io.FileInputStream;

import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.utils.ParcelFileDescriptorUtil;

public final class MusicCoverArtProvider extends ContentProvider {
    private static final String TAG = MusicCoverArtProvider.class.getName();
        public static Uri getUriForMusicTag(MusicTag item) {
            return new Builder().scheme("content").authority("apincer.android.mmate.coverart.provider").path(item.getPath()).build();
        }

       private File getFileForUri(Uri uri) {
        if (getContext() != null) {
            return new File(uri.getPath());
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

    public ParcelFileDescriptor openFile(Uri uri, String str) {
            try {
                File file = getFileForUri(uri);
                File dir =  getContext().getExternalCacheDir();
                File pathDir = file.getParentFile();
                String path = DigestUtils.md5Hex(pathDir.getAbsolutePath())+".png";

                path = "/CoverArts/"+path;

                File pathFile = new File(dir, path);
                if(!pathFile.exists()) {
                        dir = pathFile.getParentFile();
                        dir.mkdirs();
                        // extract covert to cache directory
                        FileRepository.extractCoverArt(file.getAbsolutePath(), pathFile);
                }

                return ParcelFileDescriptorUtil.pipeFrom(new FileInputStream(pathFile));
            } catch (Exception e) {
                Log.e(TAG,"Open CoverArt File: "+e.getMessage());
            }
            return null;
        }

        public int update(Uri uri, ContentValues contentValues, String str, String[] strArr) {
            throw new UnsupportedOperationException("No support updates");
        }
    }

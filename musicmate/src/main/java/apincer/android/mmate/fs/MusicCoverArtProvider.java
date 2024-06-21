package apincer.android.mmate.fs;

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

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;

import apincer.android.mmate.R;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.ParcelFileDescriptorUtil;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.utils.FileUtils;

public final class MusicCoverArtProvider extends ContentProvider {
    private static final String TAG = MusicCoverArtProvider.class.getName();
        public static Uri getUriForMusicTag(MusicTag item) {
            String absPath = item.getPath().toLowerCase();
            if(absPath.contains("/music/") && !absPath.contains("/telegram/")) {
                // if has alblum, use parent dir
                if(!StringUtils.isEmpty(item.getAlbum())) {
                    // use directory
                    File file = new File(item.getPath());
                    return new Builder().scheme("content").authority("apincer.android.mmate.coverart.provider").path(file.getParentFile().getAbsolutePath()).build();
                }
            }
            // use music file
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

    public static String getCacheCover(File file) {
        /* String absPath = file.getAbsolutePath().toLowerCase();
        if(absPath.contains("/music/") && !absPath.contains("/telegram/")) {
            // use folder for managed files, others use full file path
            pathDir = file.getParentFile();
        }*/

        String path = DigestUtils.md5Hex(file.getAbsolutePath())+".png";

        return "/CoverArts/"+path;
    }

    public ParcelFileDescriptor openFile(Uri uri, String str) {
            try {
                File file = getFileForUri(uri);
                File dir =  getContext().getExternalCacheDir();
               /* File pathDir = file;
                if(file.getAbsolutePath().contains("/Music/")) {
                    // use folder for managed files, others use full file path
                    pathDir = file.getParentFile();
                }

                String path = DigestUtils.md5Hex(pathDir.getAbsolutePath())+".png";

                path = "/CoverArts/"+path; */
                String path = getCacheCover(file);

                File pathFile = new File(dir, path);
                if(!pathFile.exists()) {
                        dir = pathFile.getParentFile();
                        dir.mkdirs();
                        // extract covert to cache directory
                        FileRepository.extractCoverArt(file.getAbsolutePath(), pathFile);
                    pathFile = new File(dir, path);
                }
                return ParcelFileDescriptorUtil.pipeFrom(Files.newInputStream(pathFile.toPath()));
            } catch (Exception e) {
                Log.e(TAG,"Open CoverArt File: "+e.getMessage());
            }
            return null;
        }

        public int update(Uri uri, ContentValues contentValues, String str, String[] strArr) {
            throw new UnsupportedOperationException("No support updates");
        }
    }

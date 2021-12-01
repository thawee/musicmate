package apincer.android.mmate.fs;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;

import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.repository.AudioFileRepository;
import apincer.android.mmate.utils.ParcelFileDescriptorUtil;

public final class EmbedCoverArtProvider extends ContentProvider {
        public static Uri getUriForMediaItem(AudioTag item) {
            return new Builder().scheme("content").authority("apincer.android.mmate.embed.coverart.provider").path(item.getPath()).build();
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
                InputStream is = AudioFileRepository.getArtworkAsStream(uri.getPath());
                if(is!=null) {
                    return ParcelFileDescriptorUtil.pipeFrom(is);
                }
              //  return ParcelFileDescriptor.open(getFileForUri(uri), b(str));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        public int update(Uri uri, ContentValues contentValues, String str, String[] strArr) {
            throw new UnsupportedOperationException("No support updates");
        }
    }

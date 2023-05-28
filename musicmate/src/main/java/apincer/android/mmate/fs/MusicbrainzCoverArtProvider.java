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

import java.io.InputStream;
import java.util.UUID;

import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.ParcelFileDescriptorUtil;
import fm.last.musicbrainz.coverart.CoverArt;
import fm.last.musicbrainz.coverart.CoverArtArchiveClient;
import fm.last.musicbrainz.coverart.CoverArtImage;
import fm.last.musicbrainz.coverart.impl.DefaultCoverArtArchiveClient;

public final class MusicbrainzCoverArtProvider extends ContentProvider {
    private static final String TAG = MusicbrainzCoverArtProvider.class.getName();
        public static Uri getUriForMediaItem(MusicTag item) {
            return new Builder().scheme("content").authority("apincer.android.mmate.musicbrainz.coverart.provider").path(item.getAlbum()).build();
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
                CoverArtArchiveClient client = new DefaultCoverArtArchiveClient(true);
                String path = uri.getPath();
                if(path.startsWith("/")) {
                    path = path.substring(1);
                }
                UUID mbid = UUID.fromString(path);
                CoverArt coverArt = client.getByMbid(mbid);
                if (coverArt != null) {
                    for (CoverArtImage coverArtImage : coverArt.getImages()) {
                        if (coverArtImage.isFront()) {
                            InputStream is = coverArtImage.getImage();
                            if(is!=null) {
                                return ParcelFileDescriptorUtil.pipeFrom(is);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG,"openFile",e);
            }
            return null;
        }

        public int update(Uri uri, ContentValues contentValues, String str, String[] strArr) {
            throw new UnsupportedOperationException("No support updates");
        }
    }

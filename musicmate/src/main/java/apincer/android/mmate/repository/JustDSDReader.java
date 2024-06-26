package apincer.android.mmate.repository;

import android.content.Context;
import android.util.Log;

import com.anggrayudi.storage.file.DocumentFileCompat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.justcodecs.dsd.DISOFormat;
import org.justcodecs.dsd.Scarletbook;
import org.justcodecs.dsd.Utils;

import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import apincer.android.mmate.Constants;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.utils.FileUtils;

public class JustDSDReader extends TagReader{
    private static final String TAG = "JustDSDReader";
    @Override
    public List<MusicTag> readMusicTag(Context context, String path) {
        Log.d(TAG, "JustDSD -> "+path);
        try {
            String fileExtension = FileUtils.getExtension(path).toUpperCase();
            String simpleName = DocumentFileCompat.getBasePath(context, path);
            String storageId = DocumentFileCompat.getStorageId(context, path);
            File iso = new File(path);
            long lastModified = iso.lastModified();
            long length = iso.length();
            DISOFormat dsf = new DISOFormat();
            dsf.init(new Utils.RandomDSDStream(iso));
            String album = (String) dsf.getMetadata("Album");
            if (album == null)
                album = (String) dsf.getMetadata("Title");
            if (album == null) {
                album = iso.getName();
                album = album.substring(0, album.length() - 4);
            }
            String genre = String.format("%s", Utils.nvl(dsf.getMetadata("Genre"), ""));
            String year = String.format("%s", dsf.getMetadata("Year"));
            //String genre = String.format("REM TOTAL %02d:%02d%n", dsf.atoc.minutes, dsf.atoc.seconds));
            MusicTag[] mList;
            Scarletbook.TrackInfo[] tracks = (Scarletbook.TrackInfo[]) dsf.getMetadata("Tracks");
            if (tracks != null && tracks.length > 0) {
                mList = new MusicTag[tracks.length];
                for (int t = 0; t < tracks.length; t++) {
                    mList[t] = new MusicTag();
                    mList[t].setPath(path);
                    mList[t].setFileFormat(fileExtension);
                    mList[t].setSimpleName(simpleName);
                    mList[t].setStorageId(storageId);
                    // mList[t].setLossless(true);
                    mList[t].setAudioStartTime(tracks[t].startFrame);
                    mList[t].setAudioBitRate(dsf.getSampleCount());
                    mList[t].setAudioBitsDepth(1);
                    mList[t].setAudioSampleRate(dsf.getSampleRate());
                    mList[t].setAudioEncoding(Constants.MEDIA_ENC_SACD);
                    mList[t].setFileLastModified(lastModified);
                    mList[t].setFileSize(length);
                    mList[t].setAlbum(album);
                    mList[t].setGenre(genre);
                    mList[t].setYear(year);
                    mList[t].setTrack(String.format(Locale.US, "%02d", t + 1));

                    mList[t].setTitle(String.format(Locale.US,"%s", Utils.nvl(StringUtils.normalizeName(tracks[t].get("title")), "NA")));
                    if (tracks[t].get("performer") != null) {
                        mList[t].setArtist(String.format(Locale.US,"%s", StringUtils.normalizeName(tracks[t].get("performer"))));
                    }

                    if (dsf.textDuration > 0) {
                        int start = (int) Math.round(dsf.getTimeAdjustment() * tracks[t].start);
                        mList[t].setAudioDuration(start);
                    } else {
                        mList[t].setAudioDuration(tracks[t].start + tracks[t].startFrame);
                    }
                    readJSON(mList[t]);
                }
            } else {
                mList = new MusicTag[1];
                MusicTag metadata = new MusicTag();

                metadata.setFileFormat(fileExtension);
                metadata.setSimpleName(simpleName);
                metadata.setStorageId(storageId);
                metadata.setFileLastModified(lastModified);
                //metadata.setLossless(true);
                metadata.setAlbum(album);
                metadata.setTitle(album);
                metadata.setGenre(genre);
                metadata.setYear(year);
                metadata.setAudioStartTime(0);
                metadata.setAudioBitRate(dsf.getSampleCount());
                metadata.setAudioBitsDepth(1);
                metadata.setAudioSampleRate(dsf.getSampleRate());
                metadata.setAudioEncoding(Constants.MEDIA_ENC_SACD);
                metadata.setFileLastModified(lastModified);
                metadata.setFileSize(length);
                metadata.setAudioDuration((dsf.atoc.minutes * 60) + dsf.atoc.seconds);
                metadata.setTrack(String.format(Locale.US,"%02d", 1));
                readJSON(metadata);
                mList[0] = metadata;
            }
            dsf.close();

            return Arrays.asList(mList);

        } catch(Exception e){
            Log.e(TAG, "ReadMusicTag",e);
        }
        return null;
    }

    @Override
    public List<MusicTag> readFullMusicTag(Context context, String path) {
        return readMusicTag(context,path);
    }

    private void readJSON(MusicTag metadata) {
        try {
            File f = new File(metadata.getPath());
            String fileName = f.getParentFile().getAbsolutePath()+"/"+metadata.getTrack()+".json";
            f = new File(fileName);
            if(f.exists()) {
                Gson gson = new GsonBuilder()
                        .setPrettyPrinting()
                        .create();
                MusicTag tag = gson.fromJson(new FileReader(fileName), MusicTag.class);
                metadata.setMediaQuality(tag.getMediaQuality());
                metadata.setTitle(tag.getTitle());
                metadata.setArtist(tag.getArtist());
                metadata.setAlbum(tag.getAlbum());
                metadata.setAlbumArtist(tag.getAlbumArtist());
                metadata.setGenre(tag.getGenre());
                metadata.setGrouping(tag.getGrouping());
                metadata.setMediaType(tag.getMediaType());
                metadata.setRating(tag.getRating());
                metadata.setComposer(tag.getComposer());
                metadata.setYear(tag.getYear());
                metadata.setComment(tag.getComment());
            }
        } catch (Exception e) {
            Log.e(TAG, "readJSON",e);
        }
    }

    public static boolean isSupportedFileFormat(String path) {
        try {
            String ext = StringUtils.trimToEmpty(FileUtils.getExtension(path));
            SupportedFileFormat.valueOf(ext.toUpperCase());
            return true;
        }catch(Exception ex) {
            return false;
        }
    }

    public enum SupportedFileFormat
    {

        FLAC("iso", "ISO");

        /**
         * File Suffix
         */
        private String filesuffix;

        /**
         * User Friendly Name
         */
        private String displayName;

        /** Constructor for internal use by this enum.
         */
        SupportedFileFormat(String filesuffix, String displayName)
        {
            this.filesuffix = filesuffix;
            this.displayName = displayName;
        }

        /**
         *  Returns the file suffix (lower case without initial .) associated with the format.
         */
        public String getFilesuffix()
        {
            return filesuffix;
        }


        public String getDisplayName()
        {
            return displayName;
        }
    }
}

package apincer.android.mmate.repository;

import static apincer.android.mmate.repository.FFMPeg.detectMQA;
import static apincer.android.mmate.utils.StringUtils.toDouble;
import static apincer.android.mmate.utils.StringUtils.toLong;
import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import android.content.Context;

import com.anggrayudi.storage.file.DocumentFileCompat;

import org.kc7bfi.jflac.FLACDecoder;
import org.kc7bfi.jflac.io.RandomFileInputStream;
import org.kc7bfi.jflac.metadata.Metadata;
import org.kc7bfi.jflac.metadata.Picture;
import org.kc7bfi.jflac.metadata.StreamInfo;
import org.kc7bfi.jflac.metadata.VorbisComment;

import java.io.File;

import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.utils.FileUtils;
import timber.log.Timber;

public class JustFLAC {
    public static boolean isSupportedFileFormat(String path) {
        try {
            String ext = StringUtils.trimToEmpty(FileUtils.getExtension(path));
            SupportedFileFormat.valueOf(ext.toUpperCase());
            return true;
        }catch(Exception ex) {
            return false;
        }
    }

    public static MusicTag readMusicTag(Context context, String mediaPath) {

        try {
            RandomFileInputStream is = new RandomFileInputStream(mediaPath);
            FLACDecoder decoder = new FLACDecoder(is);

            Metadata[] metas = decoder.readMetadata();
            StreamInfo si = decoder.getStreamInfo();
            MusicTag tag = new MusicTag();
            tag.setAudioSampleRate(si.getSampleRate());
            tag.setAudioChannels(String.valueOf(si.getChannels()));
            tag.setAudioBitsDepth(si.getBitsPerSample());
            tag.setAudioBitRate(si.getTotalSamples());
            long duration = (si.getTotalSamples() * 1000L * 1000L) / si.getSampleRate();
            tag.setAudioDuration(duration);
            for(Metadata meta: metas) {
                if(meta instanceof VorbisComment) {
                    VorbisComment comment = (VorbisComment) meta;
                    tag.setYear(getFirstString(comment, "YEAR"));
                    tag.setTitle(getFirstString(comment, "TITLE"));
                    tag.setDisc(getFirstString(comment, "DISCNUMBER"));
                    tag.setTrack(getFirstString(comment, "TRACKNUMBER"));
                    tag.setArtist(getFirstString(comment, "ARTIST"));
                    tag.setMediaQuality(getFirstString(comment, "QUALITY"));
                    tag.setAlbum(getFirstString(comment, "ALBUM"));
                    tag.setAlbumArtist(getFirstString(comment, "ALBUMARTIST"));
                    tag.setGenre(getFirstString(comment, "GENRE"));
                    tag.setGrouping(getFirstString(comment, "GROUPING"));
                    tag.setPublisher(getFirstString(comment, "PUBLISHER"));
                    tag.setRating(getFirstInt(comment, "RATING"));
                    tag.setTrackTruePeak(getFirstDouble(comment, "REPLAYGAIN_TRACK_PEAK"));
                    tag.setTrackRG(getFirstDouble(comment, "REPLAYGAIN_TRACK_GAIN", " dB"));
                    tag.setMediaType(getFirstString(comment, "MEDIA"));
                    tag.setComment(getFirstString(comment, "DESCRIPTION"));
                }else if (meta instanceof Picture) {
                   // tag.setEmbedCoverArt("embed");
                    tag.setEmbedCoverArt(((Picture)meta).getMimeString());
                }
            }
            tag.setPath(mediaPath);
            tag.setAudioEncoding("flac");
            tag.setFileFormat("flac");
            readFileInfo(context,tag);
            detectMQA(tag,50000); // timeout 50 seconds
            return  tag;
        } catch (Exception e) {
            Timber.e(e);
        }
        return null;
    }

    private static double getFirstDouble(VorbisComment comment, String name, String suffix) {
        String val = getFirstString(comment, name);
        if(val.endsWith(suffix)) {
            String txt = trimToEmpty(val.replace(suffix, ""));
            return toDouble(txt);
        }
        return 0L;
    }

    private static void readFileInfo(Context context, MusicTag tag) {
        File file = new File(tag.getPath());
        tag.setFileLastModified(file.lastModified());
        tag.setFileSize(file.length());
        tag.setSimpleName(DocumentFileCompat.getBasePath(context, tag.getPath()));
        tag.setStorageId(DocumentFileCompat.getStorageId(context, tag.getPath()));
    }

    private static double getFirstDouble(VorbisComment comment, String key) {
        String val = getFirstString(comment, key);
        return StringUtils.toDouble(val);
    }

    private static int getFirstInt(VorbisComment comment, String key) {
        String val = getFirstString(comment, key);
        return StringUtils.toInt(val);
    }

    private static String getFirstString(VorbisComment comment, String name) {
       /* String []vals = comment.getCommentByName(key);
        if(vals!=null && vals.length >0) {
            return vals[0];
        }
        return  ""; */
        try {
            final String[] comments = comment.getCommentByName( name );
            return ( comments.length > 0 )
                    ? comments[ 0 ] : "";
        }
        catch ( final Exception e ){
            return name;
        }
    }

    public enum SupportedFileFormat
    {

        FLAC("flac", "Flac");

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

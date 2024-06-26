package apincer.android.mmate.repository;

import static apincer.android.mmate.repository.MQADetector.detectMQA;
import static apincer.android.mmate.utils.StringUtils.toDouble;
import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import android.content.Context;
import android.util.Log;

import org.kc7bfi.jflac.FLACDecoder;
import org.kc7bfi.jflac.FrameListener;
import org.kc7bfi.jflac.frame.Frame;
import org.kc7bfi.jflac.metadata.Metadata;
import org.kc7bfi.jflac.metadata.Picture;
import org.kc7bfi.jflac.metadata.StreamInfo;
import org.kc7bfi.jflac.metadata.VorbisComment;

import java.io.FileInputStream;
import java.util.Collections;
import java.util.List;

import apincer.android.mmate.utils.StringUtils;
import apincer.android.utils.FileUtils;

public class JustFLACReader extends TagReader{
    private static final String TAG = "JustFLACReader";
    private static final int NO_OF_BITS_IN_BYTE = 8;
    private static final int KILOBYTES_TO_BYTES_MULTIPLIER = 1000;
    public static boolean isSupportedFileFormat(String path) {
        try {
            String ext = StringUtils.trimToEmpty(FileUtils.getExtension(path));
            return SupportedFileFormat.FLAC.equals(SupportedFileFormat.valueOf(ext.toUpperCase()));
        }catch(Exception ex) {
            return false;
        }
    }

    public List<MusicTag> readMusicTag(Context context, String mediaPath) {
        Log.d(TAG, "JustFLAC -> "+mediaPath);
        try {
            MusicTag tag = new MusicTag();
            FileInputStream is = new FileInputStream(mediaPath);
            FLACDecoder decoder = new FLACDecoder(is);
            decoder.addFrameListener(new MetadataReader(tag));
            decoder.decode();
            tag.setPath(mediaPath);
            tag.setAudioEncoding("flac");
            tag.setFileFormat("flac");
            readFileInfo(context,tag);
           // tag.setFileSizeRatio(getFileSizeRatio(tag));
           // detectMQA(tag,5000); // timeout 5 seconds
            return Collections.singletonList(tag);
        } catch (Exception e) {
            Log.e(TAG, "ReadMusicTag: "+e.getMessage());
        }
        return null;
    }

    public List<MusicTag> readFullMusicTag(Context context, String mediaPath) {
        Log.d(TAG, "JustFLAC -> "+mediaPath);
        try {
            MusicTag tag = new MusicTag();
            FileInputStream is = new FileInputStream(mediaPath);
            FLACDecoder decoder = new FLACDecoder(is);
            decoder.addFrameListener(new MetadataReader(tag));
            decoder.decode();
            tag.setPath(mediaPath);
            tag.setAudioEncoding("flac");
            tag.setFileFormat("flac");
            readFileInfo(context,tag);
            // tag.setFileSizeRatio(getFileSizeRatio(tag));
            detectMQA(tag,5000); // timeout 5 seconds
            return Collections.singletonList(tag);
        } catch (Exception e) {
            Log.e(TAG, "ReadMusicTag: "+e.getMessage());
        }
        return null;
    }

    private int computeBitrate(float length, long size)
    {
        return (int) ((size / KILOBYTES_TO_BYTES_MULTIPLIER) * NO_OF_BITS_IN_BYTE / length);
    }
    private String parseMimeString(String mimeString) {
        if(mimeString.contains("/")) {
            return mimeString.substring(mimeString.indexOf("/")+1);
        }
        return mimeString;
    }

    private static double getFirstDouble(VorbisComment comment, String name, String suffix) {
        String val = getFirstString(comment, name);
        if(val.endsWith(suffix)) {
            String txt = trimToEmpty(val.replace(suffix, ""));
            return toDouble(txt);
        }
        return 0L;
    }

    /*
    private static void readFileInfo(Context context, MusicTag tag) {
        File file = new File(tag.getPath());
        tag.setFileLastModified(file.lastModified());
        tag.setFileSize(file.length());
        tag.setSimpleName(DocumentFileCompat.getBasePath(context, tag.getPath()));
        tag.setStorageId(DocumentFileCompat.getStorageId(context, tag.getPath()));
    } */

    private static double getFirstDouble(VorbisComment comment, String key) {
        String val = getFirstString(comment, key);
        return StringUtils.toDouble(val);
    }

    private static int getFirstInt(VorbisComment comment, String key) {
        String val = getFirstString(comment, key);
        return StringUtils.toInt(val);
    }

    private static String getFirstString(VorbisComment comment, String name) {
        try {
            final String[] comments = comment.getCommentByName( name );
            return ( comments.length > 0 )
                    ? comments[ 0 ] : "";
        }
        catch ( final Exception e ){
            return name;
        }
    }

    class MetadataReader implements FrameListener {
        MusicTag tag;

        public MetadataReader(MusicTag tag) {
            this.tag = tag;
        }

        @Override
        public void processMetadata(Metadata metadata) {
            if(metadata instanceof StreamInfo) {
                StreamInfo si = (StreamInfo)metadata;
                tag.setAudioSampleRate(si.getSampleRate());
                tag.setAudioChannels(String.valueOf(si.getChannels()));
                tag.setAudioBitsDepth(si.getBitsPerSample());
                double duration = (si.getTotalSamples() * 1.00) / si.getSampleRate();
                tag.setAudioDuration(duration);
                tag.setAudioBitRate((long) ((si.getTotalSamples() * si.getBitsPerSample()) / duration));
           } else if(metadata instanceof VorbisComment) {
                VorbisComment comment = (VorbisComment) metadata;
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
                tag.setDynamicRangeMeter(getFirstDouble(comment, KEY_MM_TRACK_DYNAMIC_RANGE));
                tag.setTrackTP(getFirstDouble(comment, KEY_TAG_TRACK_PEAK));
                tag.setTrackRG(getFirstDouble(comment,  KEY_TAG_TRACK_GAIN, " dB"));
                tag.setDynamicRange(getFirstDouble(comment,  KEY_MM_MEASURED_DR));
                tag.setMediaType(getFirstString(comment, "MEDIA"));
                tag.setComment(getFirstString(comment, "DESCRIPTION"));
            }else if (metadata instanceof Picture) {
                Picture pict = ((Picture)metadata);
                tag.setCoverartMime(parseMimeString(pict.getMimeString()));
              //  pict.
            }
        }

        @Override
        public void processFrame(Frame frame) {

        }

        @Override
        public void processError(String msg) {

        }
    }
}

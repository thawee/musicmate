package apincer.music.core.codec;

import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_AIF_ALBUM;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_AIF_ALBUM_ARTIST;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_AIF_ARTIST;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_AIF_COMMENT;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_AIF_COMPILATION;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_AIF_COMPOSER;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_AIF_DISC;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_AIF_GENRE;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_AIF_GROUPING;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_AIF_PUBLISHER;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_AIF_QUALITY;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_AIF_TITLE;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_AIF_TRACK;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_AIF_YEAR;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_ALBUM;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_ALBUM_ARTIST;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_ARTIST;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_COMMENT;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_COMPILATION;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_COMPOSER;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_DISC;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_GENRE;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_GROUPING;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_MP3_ALBUM;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_MP3_ALBUM_ARTIST;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_MP3_ARTIST;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_MP3_COMMENT;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_MP3_DISC;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_MP3_GENRE;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_MP3_TITLE;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_MP3_TRACK;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_MP3_YEAR;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_MP4_ALBUM;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_MP4_ALBUM_ARTIST;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_MP4_ARTIST;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_MP4_AUTHOR;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_MP4_COMMENT;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_MP4_COMPOSER;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_MP4_GENRE;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_MP4_GROUPING;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_MP4_PUBLISHER;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_MP4_TITLE;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_MP4_TRACK;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_MP4_YEAR;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_PUBLISHER;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_TITLE;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_TRACK;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_WAVE_ALBUM;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_WAVE_ALBUM_ARTIST;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_WAVE_ARTIST;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_WAVE_COMMENT;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_WAVE_COMPOSER;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_WAVE_DISC;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_WAVE_GENRE;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_WAVE_GROUP;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_WAVE_PUBLISHER;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_WAVE_QUALITY;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_WAVE_TITLE;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_WAVE_TRACK;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_WAVE_YEAR;
import static apincer.music.core.codec.FFMpegHelper.KEY_TAG_YEAR;
import static apincer.music.core.codec.FFMpegHelper.METADATA_KEY;
import static apincer.music.core.utils.TagUtils.isAIFFile;
import static apincer.music.core.utils.TagUtils.isFLACFile;
import static apincer.music.core.utils.TagUtils.isMPegFile;
import static apincer.music.core.utils.TagUtils.isMp4File;
import static apincer.music.core.utils.TagUtils.isWavFile;
import static apincer.music.core.utils.StringUtils.isEmpty;
import static apincer.music.core.utils.StringUtils.trimToEmpty;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.antonkarpenko.ffmpegkit.FFmpegKit;
import com.antonkarpenko.ffmpegkit.FFmpegSession;
import com.antonkarpenko.ffmpegkit.ReturnCode;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;

import apincer.music.core.provider.FileSystem;
import apincer.music.core.database.MusicTag;
import apincer.music.core.utils.StringUtils;
import apincer.android.utils.FileUtils;

public class FFMpegWriter extends TagWriter {
    private static final String TAG = "FFMpegWriter";
    private final Context context;

    public FFMpegWriter(Context context) {
        super();
        this.context = context;
    }

    /*
    @Deprecated
    public static Loudness getLoudness(String path) {
        try { */
/*
   -i "%a" -af ebur128 -f null --i "%a" -af ebur128 -f null -
  Integrated loudness:
    I:         -19.7 LUFS
    Threshold: -30.6 LUFS

  Loudness range:
    LRA:        13.0 LU
    Threshold: -40.6 LUFS
    LRA low:   -30.0 LUFS
    LRA high:  -17.0 LUFS

  True peak:
    Peak:        0.5 dBFS[Parsed_ebur128_0 @ 0x7b44c68950]

*/
 /*           //String cmd = "-i \""+tag.getPath()+"\" -af ebur128= -f null -";
            String cmd = " -hide_banner -nostats -i \"" + path + "\" -filter_complex ebur128=peak=true:framelog=verbose -f null -";
            //ffmpeg -nostats -i ~/Desktop/input.wav -filter_complex ebur128=peak=true -f null -
            // String cmd = "-i \""+path+"\" -af loudnorm=I=-16:TP=-1.5:LRA=11:print_format=json -f null -";
            FFmpegSession session = FFmpegKit.execute(cmd);
            String data = getFFmpegOutputData(session);
            String keyword = "Integrated loudness:";

            int startTag = data.lastIndexOf(keyword);
            if (startTag > 0) {
                String integrated = data.substring(data.indexOf("I:") + 3, data.indexOf("LUFS"));
                String range = data.substring(data.indexOf("LRA:") + 5, data.indexOf("LU\n"));
                String peak = data.substring(data.indexOf("Peak:") + 6, data.indexOf("dBFS"));
                return new Loudness(toDouble(trimToEmpty(integrated)), toDouble(trimToEmpty(range)), toDouble(trimToEmpty(peak)));
            }
        } catch (Exception ex) {
            Timber.e(ex);
        }
        return null;
    } */


    protected boolean writeTag(MusicTag tag) {
        // check free space on storage
        // ffmpeg write to new tmp file
        // ffmpeg -i aiff.aiff -map 0 -y -codec copy -write_id3v2 1 -metadata "artist-sort=emon feat sort" aiffout.aiff
        // ffmpeg -hide_banner -i aiff.aiff -map 0 -y -codec copy -metadata "artist-sort=emon feat sort" aiffout.aiff
        Log.i(TAG, "writeTag: "+tag.getPath());
        String srcPath = tag.getPath();
       // File dir2 = context.getExternalCacheDir();
        File dir = FileSystem.getCacheDirForFile(context, tag.getStorageId());

        String ext = FileUtils.getExtension(srcPath);
        String targetPath = "/tmp/"+ DigestUtils.md5Hex(srcPath)+"."+ext;
        dir = new File(dir, targetPath);
        FileUtils.createParentDirs(dir);
        targetPath = dir.getAbsolutePath();
        targetPath = escapePathForFFMPEG(targetPath);
        String metadataKeys = getMetadataTrackKeys(tag.getOriginTag(), tag);
        if(isEmpty(metadataKeys)) return false; // co change to write to change
        String options = " -hide_banner -nostats ";
        String copyOption = " -map 0 -y -codec copy ";
        if(isMPegFile(tag)) {
            //options = options + " -fflags +genpts ";
            copyOption = " -c copy ";
        }

       // String cmd = options +" -i \"" + srcPath + "\" -map 0 -y -codec copy "+metadataKeys+ "\""+targetPath+"\"";
        String cmd = options +" -i \"" + srcPath + "\""+copyOption+metadataKeys+ "\""+targetPath+"\"";
        //ffmpeg -nostats -i ~/Desktop/input.wav -filter_complex ebur128=peak=true -f null -
        // String cmd = "-i \""+path+"\" -af loudnorm=I=-16:TP=-1.5:LRA=11:print_format=json -f null -";

        Log.d(TAG, "write tags: "+cmd);
        FFmpegSession session = FFmpegKit.execute(cmd);
        if(ReturnCode.isSuccess(session.getReturnCode())) {
            // success
            // move file
           // if(isFLACFile(tag) || isWavFile(tag) || isAIFFile(tag) || isMp4File(tag)) {
                if(FileSystem.safeMove(context, targetPath, srcPath)) {
                   // FileRepository.newInstance(context).scanMusicFile(new File(srcPath),true);
                    return true;
                }else {
                    FileSystem.delete(targetPath); // delete source file to clear space
                    return false;
                }
          /*  }else {
                // FIXME : for test
                FileSystem.move(context, targetPath, srcPath + "_TAGS." + tag.getFileFormat());
                FileRepository.newInstance(context).scanMusicFile(new File(srcPath + "_TAGS." + tag.getFileFormat()),false);
                return true;
            }*/
        }else {
            Log.d(TAG, session.getOutput());
            // fail, delete tmp file;
            File tmp = new File(targetPath);
            if(tmp.exists()) {
                FileSystem.delete(tmp);
            }
            return false;
        }
    }

    private static String getMetadataTrackKeys(MusicTag origin, MusicTag tag) {
        // -metadata language="eng"
        if (isWavFile(tag)) {
            return getMetadataTrackKeysForWave(tag);
        } else if (isFLACFile(tag)) {
            return getMetadataTrackKeysForFlac(tag);
        }else if (isAIFFile(tag)) {
            return getMetadataTrackKeysForAIF(tag);
        }else if (isMp4File(tag)) {
            return getMetadataTrackKeysForMp4(tag);
        }else if (isMPegFile(tag)) {
            return getMetadataTrackKeysForMp3(tag);
        }
        // need mp3
        return null;
    }

    private static String getMetadataTrackKeysForMp3(MusicTag tag) {
        return " -write_id3v2 1 " +
                getMetadataTrackKey(KEY_TAG_MP3_TITLE, tag.getTitle()) +
                getMetadataTrackKey(KEY_TAG_MP3_ALBUM, tag.getAlbum()) +
                getMetadataTrackKey(KEY_TAG_MP3_ARTIST, tag.getArtist()) +
                getMetadataTrackKey(KEY_TAG_MP3_ALBUM_ARTIST, tag.getAlbumArtist()) +
               // getMetadataTrackKey(KEY_TAG_MP3_COMPOSER, tag.getComposer()) +
               // getMetadataTrackKey(KEY_TAG_MP3_COMPILATION, (tag.isCompilation())) +
               // getMetadataTrackKey(KEY_TAG_MP3_COMMENT, tag.getComment()) +
                getMetadataTrackKey(KEY_TAG_MP3_COMMENT,prepareMMComment(tag))+
                getMetadataTrackKey(KEY_TAG_MP3_DISC, tag.getDisc()) +
                getMetadataTrackKey(KEY_TAG_MP3_GENRE, tag.getGenre()) +
               // getMetadataTrackKey(KEY_TAG_MP3_GROUPING, tag.getGrouping()) +
               // getMetadataTrackKey(KEY_TAG_MP3_PUBLISHER, tag.getPublisher()) +
               // getMetadataTrackKey(KEY_TAG_MP3_MEDIA, tag.getMediaType()) +
               // getMetadataTrackKey(KEY_TAG_MP3_QUALITY, tag.getMediaQuality()) +
               // getMetadataTrackKey(KEY_TAG_MP3_RATING, tag.getRating()) +
                getMetadataTrackKey(KEY_TAG_MP3_TRACK, tag.getTrack()) +
                getMetadataTrackKey(KEY_TAG_MP3_YEAR, tag.getYear());
    }

    private static String getMetadataTrackKey(String key, String val) {
        return METADATA_KEY + " " + key + "=\"" + trimToEmpty(val).replace("\"","\\\"") + "\" ";
    }
    private static String getMetadataTrackKey(String key, boolean val) {
        return METADATA_KEY + " " + key + "=\"" + (val?1:0) + "\" ";
    }

    private static String getMetadataTrackKeysForFlac(MusicTag tag) {
        // work for all fields
        return getMetadataTrackKey(KEY_TAG_TITLE, tag.getTitle()) +
                getMetadataTrackKey(KEY_TAG_ALBUM, tag.getAlbum()) +
                getMetadataTrackKey(KEY_TAG_ARTIST, tag.getArtist()) +
                getMetadataTrackKey(KEY_TAG_ALBUM_ARTIST, tag.getAlbumArtist()) +
                getMetadataTrackKey(KEY_TAG_COMPOSER, tag.getComposer()) +
                getMetadataTrackKey(KEY_TAG_COMPILATION, (tag.isCompilation())) +
                getMetadataTrackKey(KEY_TAG_COMMENT, tag.getComment()) +
                getMetadataTrackKey(KEY_TAG_DISC, tag.getDisc()) +
                getMetadataTrackKey(KEY_TAG_GENRE, tag.getGenre()) +
               // getMetadataTrackKey(KEY_TAG_GROUPING, tag.getGrouping()) +
                getMetadataTrackKey(KEY_TAG_PUBLISHER, tag.getPublisher()) +
                getMetadataTrackKey(TagReader.KEY_TAG_QUALITY, tag.getQualityRating()) +
                getMetadataTrackKey(KEY_TAG_TRACK, tag.getTrack()) +
                getMetadataTrackKey(KEY_TAG_YEAR, tag.getYear());
    }

    @SuppressLint("DefaultLocale")
    private static String getMetadataTrackKeysForMp4(MusicTag tag) {
        String tags = getMetadataTrackKey(KEY_TAG_MP4_TITLE, tag.getTitle()) +
                getMetadataTrackKey(KEY_TAG_MP4_ALBUM, tag.getAlbum()) +
                getMetadataTrackKey(KEY_TAG_MP4_AUTHOR, tag.getArtist()) +
                getMetadataTrackKey(KEY_TAG_MP4_ARTIST, tag.getArtist()) +
                getMetadataTrackKey(KEY_TAG_MP4_ALBUM_ARTIST, tag.getAlbumArtist()) +
                getMetadataTrackKey(KEY_TAG_MP4_COMPOSER, tag.getComposer()) +
              //  getMetadataTrackKey(KEY_TAG_MP4_COMPILATION, (tag.isCompilation())) +
                getMetadataTrackKey(KEY_TAG_MP4_COMMENT, tag.getComment()) +
              //  getMetadataTrackKey(KEY_TAG_MP4_DISC, tag.getDisc()) +
                getMetadataTrackKey(KEY_TAG_MP4_GENRE, tag.getGenre()) +
               // getMetadataTrackKey(KEY_TAG_MP4_GROUPING, tag.getGrouping()) +
                getMetadataTrackKey(KEY_TAG_MP4_PUBLISHER, tag.getPublisher()) +
             //   getMetadataTrackKey(KEY_TAG_MP4_LANGUAGE, tag.getLanguage()) + ///
             //   getMetadataTrackKey(KEY_TAG_MP4_MEDIA, tag.getMediaType()) +
             //   getMetadataTrackKey(KEY_TAG_MP4_QUALITY, tag.getMediaQuality()) +
             //   getMetadataTrackKey(KEY_TAG_MP4_RATING, tag.getRating()) +
                getMetadataTrackKey(KEY_TAG_MP4_TRACK, tag.getTrack()) +
                getMetadataTrackKey(KEY_TAG_MP4_YEAR, tag.getYear());

        return tags;
    }


    private static String getMetadataTrackKeysForAIF(MusicTag tag) {
        return " -write_id3v2 1 " +
                getMetadataTrackKey(KEY_TAG_AIF_TITLE, tag.getTitle()) +
                getMetadataTrackKey(KEY_TAG_AIF_ALBUM, tag.getAlbum()) +
                getMetadataTrackKey(KEY_TAG_AIF_ARTIST, tag.getArtist()) +
                getMetadataTrackKey(KEY_TAG_AIF_ALBUM_ARTIST, tag.getAlbumArtist()) +
                getMetadataTrackKey(KEY_TAG_AIF_COMPOSER, tag.getComposer()) +
                getMetadataTrackKey(KEY_TAG_AIF_COMPILATION, (tag.isCompilation())) +
                getMetadataTrackKey(KEY_TAG_AIF_COMMENT, tag.getComment()) +
                getMetadataTrackKey(KEY_TAG_AIF_DISC, tag.getDisc()) +
                getMetadataTrackKey(KEY_TAG_AIF_GENRE, tag.getGenre()) +
               // getMetadataTrackKey(KEY_TAG_AIF_GROUPING, tag.getGrouping()) +
                getMetadataTrackKey(KEY_TAG_AIF_PUBLISHER, tag.getPublisher()) +
                getMetadataTrackKey(KEY_TAG_AIF_QUALITY, tag.getQualityRating()) +
                getMetadataTrackKey(KEY_TAG_AIF_TRACK, tag.getTrack()) +
                getMetadataTrackKey(KEY_TAG_AIF_YEAR, tag.getYear());
    }

    private static String getMetadataTrackKeysForWave(MusicTag tag) {
        // need to include all metadata
        //String comment = getWaveComment(tag);
        return getMetadataTrackKey(KEY_TAG_WAVE_TITLE,tag.getTitle()) +
                getMetadataTrackKey(KEY_TAG_WAVE_ALBUM,tag.getAlbum())+
                getMetadataTrackKey(KEY_TAG_WAVE_ALBUM_ARTIST,tag.getAlbumArtist())+
                getMetadataTrackKey(KEY_TAG_WAVE_ARTIST,tag.getArtist())+
                getMetadataTrackKey(KEY_TAG_WAVE_GENRE,tag.getGenre())+
               // getMetadataTrackKey(KEY_TAG_WAVE_LANGUAGE,tag.getLanguage())+
                getMetadataTrackKey(KEY_TAG_WAVE_TRACK,tag.getTrack())+
                getMetadataTrackKey(KEY_TAG_WAVE_YEAR,tag.getYear())+
                getMetadataTrackKey(KEY_TAG_WAVE_COMPOSER,tag.getComposer())+
              //  getMetadataTrackKey(KEY_TAG_WAVE_GROUP,tag.getGrouping())+
            //    getMetadataTrackKey(KEY_TAG_WAVE_MEDIA,tag.getMediaType())+
                getMetadataTrackKey(KEY_TAG_WAVE_DISC,tag.getDisc())+
                getMetadataTrackKey(KEY_TAG_WAVE_PUBLISHER,tag.getPublisher())+
                getMetadataTrackKey(KEY_TAG_WAVE_QUALITY,tag.getQualityRating()) +
                getMetadataTrackKey(KEY_TAG_WAVE_COMMENT,prepareMMComment(tag));
    }

    private static String prepareMMComment(MusicTag musicTag) {
        return StringUtils.trimToEmpty(musicTag.getComment()) +"\n<##>" +
                StringUtils.trimToEmpty(musicTag.getDisc())+
              //  "#"+StringUtils.trimToEmpty(musicTag.getGrouping())+
                "#"+StringUtils.trimToEmpty(musicTag.getQualityRating())+
           //     "#"+musicTag.getRating()+
                "#"+StringUtils.trimToEmpty(musicTag.getAlbumArtist())+
                "#"+StringUtils.trimToEmpty(musicTag.getComposer())+
                "#"+musicTag.getDynamicRangeScore()+
                "#"+musicTag.getDynamicRange()+
               // "#"+musicTag.getMeasuredSamplingRate()+
                "</##> ";
    }

    public static String escapePathForFFMPEG(String path) {
        path = StringUtils.trimToEmpty(path);
       // path = path.replace("'", "''");
        return path;
    }

    /*
    https://github.com/Moonbase59/loudgain/blob/master/src/loudgain.c
    public static final double RGV2_REFERENCE = -18.00;
    public static double getReplayGain(MusicTag tag) {
        double max_true_peak_level = -1.0; // dBTP; default for -k, as per EBU Tech 3343
        // boolean will_clip = false;
        double trackReplayGain = getReplayGain(tag.getTrackLoudness());
        double tpeakGain = 1.0; // "gained" track peak
        // double tnew;
        double tpeak = Math.pow(10.0, max_true_peak_level / 20.0); // track peak limit
        // boolean tclip = false;

        // Check if track will clip, and correct if so requested (-k/-K)

        // track peak after gain
        tpeakGain = Math.pow(10.0, trackReplayGain / 20.0) * tag.getTrackTruePeek();
        //  tnew = tpeakGain;

        // printf("\ntrack: %.2f LU, peak %.6f; album: %.2f LU, peak %.6f\ntrack: %.6f, %.6f; album: %.6f, %.6f; Clip: %s\n",
        // 	scan -> track_gain, scan -> track_peak, scan -> album_gain, scan -> album_peak,
        // 	tgain, tpeak, again, apeak, will_clip ? "Yes" : "No");

        if (tpeakGain > tpeak) {
            // set new track peak = minimum of peak after gain and peak limit
            double tnew = min(tpeakGain, tpeak);
            trackReplayGain = trackReplayGain - (log10(tpeakGain/tnew) * 20.0);
            //  tclip = true;
        }
        return trackReplayGain;
        //return String.format(Locale.getDefault(),"%.2f", trackReplayGain);
        // tag.setReplayGain(Double.toString(trackReplayGain));

        //  will_clip = false;

        // printf("\nAfter clipping prevention:\ntrack: %.2f LU, peak %.6f; album: %.2f LU, peak %.6f\ntrack: %.6f, %.6f; album: %.6f, %.6f; Clip: %s\n",
        // 	scan -> track_gain, scan -> track_peak, scan -> album_gain, scan -> album_peak,
        // 	tgain, tpeak, again, apeak, will_clip ? "Yes" : "No");
        //  }
    }

    private static double getReplayGain(double loudnessIntegrated) {
        return RGV2_REFERENCE - loudnessIntegrated;
    } */
}

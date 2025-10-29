package apincer.music.core.codec;

import static apincer.music.core.utils.StringUtils.toDouble;

import android.content.Context;
import android.util.Log;

import com.antonkarpenko.ffmpegkit.FFmpegKit;
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig;
import com.antonkarpenko.ffmpegkit.FFmpegSession;
import com.antonkarpenko.ffmpegkit.Level;
import com.antonkarpenko.ffmpegkit.ReturnCode;
import com.antonkarpenko.ffmpegkit.Session;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import apincer.music.core.provider.FileSystem;
import apincer.music.core.database.MusicTag;
import apincer.music.core.utils.LogHelper;
import apincer.android.utils.FileUtils;

public class FFMpegHelper {
    private static final String TAG = "FFMpegHelper";

    public interface PcmStreamProcessor {
        void process(java.io.InputStream pcmStream) throws IOException;
    }

    @Deprecated
    public static void extractCoverArt(MusicTag tag, File pathFile) {
        Log.d(TAG, "extractCoverArt: "+pathFile);
        String targetPath = pathFile.getAbsolutePath();
       // targetPath = escapePathForFFMPEG(targetPath);
        String options = " -c:v copy ";

        String cmd = " -hide_banner -nostats -i \"" + tag.getPath() + "\" " + options + " \"" + targetPath + "\"";
        LogHelper.setFFMpegOff();
        FFmpegKit.execute(cmd); // do not clear the result
    }

    public static void extractCoverArt(String path, File pathFile) {
        try {
            Log.d(TAG, "extractCoverArt: "+path);
            String targetPath = pathFile.getAbsolutePath();
           // targetPath = escapePathForFFMPEG(targetPath);
            String options = " -c:v copy ";

            String cmd = " -hide_banner -nostats -i \"" + path + "\" " + options + " \"" + targetPath + "\"";
            LogHelper.setFFMpegOff();
            FFmpegKit.execute(cmd); // do not clear the result
        }catch (Exception ex) {
            Log.e(TAG, "extractCoverArt", ex);
        }
    }

    public static void removeCoverArt(Context context, MusicTag tag) {
       // if(!isEmpty(tag.getCoverartMime())) {
            String pathFile = tag.getPath();
            String ext = FileUtils.getExtension(pathFile);
            pathFile = pathFile.replace("."+ext, "no_embed."+ext);
            String options = " -vn -codec:a copy ";
           // String options =" -map 0:V -y -codec copy ";

            String cmd = " -hide_banner -nostats -i \"" + tag.getPath() + "\" " + options + " \"" + pathFile+ "\"";
            LogHelper.setFFMpegOff();
            Session session = FFmpegKit.execute(cmd); // do not clear the result
            if (ReturnCode.isSuccess(session.getReturnCode())) {
                FileSystem.safeMove(context, pathFile, tag.getPath(), true);
                //return pathFile;
            }else {
                FileSystem.delete(pathFile);
            }
       // }
    }

    public static final String KEY_BIT_RATE = "bit_rate";
    public static final String KEY_START_TIME = "start_time";
    public static final String KEY_DURATION = "duration";
    public static final String KEY_TAG = "TAG:";
   public static final String KEY_TAG_ARTIST = "ARTIST";
    public static final String KEY_TAG_ALBUM = "ALBUM";
    public static final String KEY_TAG_ALBUM_ARTIST = "album_artist";
    public static final String KEY_TAG_COMPOSER = "COMPOSER";
    public static final String KEY_TAG_COMMENT = "COMMENT";
    public static final String KEY_TAG_COMPILATION = "COMPILATION";
    public static final String KEY_TAG_DISC = "disc"; //"DISCNUMBER";
    public static final String KEY_TAG_GENRE = "GENRE";
    public static final String KEY_TAG_GROUPING = "GROUPING";
    public static final String KEY_TAG_TRACK = "track";
    public static final String KEY_TAG_PUBLISHER = "PUBLISHER";

   // public static final String KEY_TAG_RATING = "RATING";
   public static final String KEY_TAG_TITLE = "TITLE";
    public static final String KEY_TAG_YEAR = "YEAR";

    // WAVE file
    //https://www.digitizationguidelines.gov/audio-visual/documents/listinfo.html
    public static final String KEY_TAG_WAVE_ARTIST = "IART"; //artist
    public static final String KEY_TAG_WAVE_ALBUM = "IPRD"; // album
    public static final String KEY_TAG_WAVE_ALBUM_ARTIST = "IENG"; // engineers
    public static final String KEY_TAG_WAVE_GENRE = "IGNR"; //genre
    public static final String KEY_TAG_WAVE_TRACK = "IPRT"; //track
    public static final String KEY_TAG_WAVE_TITLE = "INAM"; //title
    public static final String KEY_TAG_WAVE_YEAR = "date"; //""ICRD"; //date
    public static final String KEY_TAG_WAVE_MEDIA = "IMED";
    public static final String KEY_TAG_WAVE_COMMENT = "ICMT"; // comment
    public static final String KEY_TAG_WAVE_PUBLISHER = "ISRC"; // name of person or organization
    public static final String KEY_TAG_WAVE_DISC = "ISRF"; // original form of material
    public static final String KEY_TAG_WAVE_GROUP = "IKEY"; // list of keyword, saperated by semicolon
    public static final String KEY_TAG_WAVE_COMPOSER = "ICMS"; // person who commision the subject
    public static final String KEY_TAG_WAVE_QUALITY = "ISBJ"; // Describes the contents of the file

    // AIF/AIFF
    public static final String KEY_TAG_AIF_ARTIST = "ARTIST";
    public static final String KEY_TAG_AIF_ALBUM = "ALBUM";
    public static final String KEY_TAG_AIF_ALBUM_ARTIST = "album_artist";
    public static final String KEY_TAG_AIF_COMPOSER = "COMPOSER";
    public static final String KEY_TAG_AIF_COMMENT = "COMMENT";
    public static final String KEY_TAG_AIF_COMPILATION = "COMPILATION";
    public static final String KEY_TAG_AIF_DISC = "disc"; //"DISCNUMBER";
    public static final String KEY_TAG_AIF_GENRE = "GENRE";
    public static final String KEY_TAG_AIF_GROUPING = "GROUPING";
    public static final String KEY_TAG_AIF_TRACK = "track";
    public static final String KEY_TAG_AIF_PUBLISHER = "PUBLISHER";
    //public static final String KEY_TAG_AIF_LANGUAGE = "LANGUAGE";
    public static final String KEY_TAG_AIF_MEDIA = "MEDIA";
   // public static final String KEY_TAG_AIF_RATING = "RATING";
   public static final String KEY_TAG_AIF_QUALITY = "QUALITY";
    public static final String KEY_TAG_AIF_TITLE = "TITLE";
    public static final String KEY_TAG_AIF_YEAR = "YEAR";

    // QuickTime/MOV/MP4/M4A
    // https://wiki.multimedia.cx/index.php/FFmpeg_Metadata
    public static final String KEY_TAG_MP4_ARTIST = "artist"; //for aac
    public static final String KEY_TAG_MP4_AUTHOR = "author"; // for alac
    public static final String KEY_TAG_MP4_ALBUM = "album"; // album
    public static final String KEY_TAG_MP4_ALBUM_ARTIST = "album_artist";
    public static final String KEY_TAG_MP4_GENRE = "genre"; //genre
    public static final String KEY_TAG_MP4_TRACK = "track"; //track
    public static final String KEY_TAG_MP4_TITLE = "title"; //title
    public static final String KEY_TAG_MP4_YEAR = "year"; //date
    public static final String KEY_TAG_MP4_COMPOSER = "composer";
    public static final String KEY_TAG_MP4_GROUPING = "grouping";
    public static final String KEY_TAG_MP4_COMMENT = "comment";  // comment
    public static final String KEY_TAG_MP4_PUBLISHER = "copyright"; //copy right

    //https://gist.github.com/eyecatchup/0757b3d8b989fe433979db2ea7d95a01
    public static final String KEY_TAG_MP3_ARTIST = "artist"; //artist
    public static final String KEY_TAG_MP3_ALBUM = "album"; // album
    public static final String KEY_TAG_MP3_ALBUM_ARTIST = "album_artist";
    public static final String KEY_TAG_MP3_GENRE = "genre"; //genre
    public static final String KEY_TAG_MP3_TRACK = "track"; //track
    public static final String KEY_TAG_MP3_TITLE = "title"; //title
    public static final String KEY_TAG_MP3_YEAR = "date"; //date
    public static final String KEY_TAG_MP3_DISC = "disc";
    public static final String KEY_TAG_MP3_COMMENT = "comment";  // comment
    public static final String METADATA_KEY = "-metadata";

    @Deprecated
    public static void measureDRandStat(MusicTag tag) {
        // String cmd ="-hide_banner -of default=noprint_wrappers=0 -show_format -print_format json \""+path+"\"";
       // String filter = " -filter:a drmeter,replaygain,volumedetect,astats -vn -sn -dn "; // -vn -sn -dn to ignore none audio to speed up the process
         String filter = " -filter:a drmeter,replaygain,astats -vn -sn -dn "; // -vn -sn -dn to ignore none audio to speed up the process

        String targetPath = tag.getPath();
        //targetPath = escapePathForFFMPEG(targetPath);
       // String filter = " -filter:a replaygain ";

        String cmd ="-hide_banner -nostats -i \""+targetPath+"\""+filter+" -f null -";
        Log.d(TAG, "measureDRandStat: "+tag.getPath());
        FFmpegKitConfig.setLogLevel(Level.AV_LOG_INFO);
        FFmpegSession session = FFmpegKit.execute(cmd);
        FFmpegKitConfig.setLogLevel(Level.AV_LOG_ERROR);
        // if (ReturnCode.isSuccess(session.getReturnCode())) {
        String data = session.getOutput();
        parseOverallDRMeter(tag, data);
       // parseVolume(tag, data);
        parseASTATS(tag, data);
        session.cancel();
       // }else {
            // try to get from file name
        //    String data = getFFmpegOutputData(session);
        //    parseReplayGain(tag, data);
        //    session.cancel();
       // }
    }

    private static void parseASTATS(MusicTag tag, String data) {
        try {
            // Overall DR: 14.0357
            //[Parsed_astats_2 @ 0xb4000077c3ec35d0] Bit depth: 24/24
            //[Parsed_astats_2 @ 0xb4000077c3ec35d0] Dynamic range: 144.397910
            Pattern pattern = Pattern.compile("\\s*Dynamic range:\\s*(\\S*)");
            Matcher matcher = pattern.matcher(data);
            if (matcher.find()) {
                String info = matcher.group(1);
                tag.setDynamicRange(toDouble(info));
               // tag.setAstatDR(toDouble(info));
            }
        }catch (Exception ex) {
            Log.e(TAG, "parseASTATS", ex);
        }
    }

    private static void parseOverallDRMeter(MusicTag tag, String data) {
        try {
            // Overall DR: 14.0357
            Pattern pattern = Pattern.compile("\\s*Overall DR:\\s*(\\S*)");
            Matcher matcher = pattern.matcher(data);
            if (matcher.find()) {
                String info = matcher.group(1);
                tag.setDynamicRangeScore(toDouble(info));
            }
        }catch (Exception ex) {
            Log.e(TAG, "parseOverallDRMeter", ex);
        }
    }

    public static boolean convert(Context context, String srcPath, String targetPath, int cLevel, int bitDept) {
        String options="";
        if (srcPath.toLowerCase().endsWith(".dsf")){
            // convert from dsf to 24 bits, 48 kHz
            // use lowpass filter to eliminate distortion in the upper frequencies.
            options = " -af \"lowpass=24000, volume=6dB\" -sample_fmt s32 -ar 48000 ";
        }

        if (targetPath.toLowerCase().endsWith(".flac")) {
            if(cLevel >=0) {
                options = options + " -y -vn -c:a flac -compression_level " + cLevel;
            }else {
                options = options + " -y -vn -c:a flac -compression_level 0 ";
            }
        }else if (targetPath.toLowerCase().endsWith(".mp3")) {
            // convert to 320k bitrate
           // options = " -ar 44100 -q:a 0 -ab 320k ";
            options = " -c:a libmp3lame -b:a 320k ";
        }else if (targetPath.toLowerCase().endsWith(".m4a")) {
            if(bitDept > 16) {
                options = " -sample_fmt s32 -y -acodec pcm_s"+bitDept+"be "; //alac
            }else {
                options = " -y -vn -c:a alac "; //alac
            }
        }else if (targetPath.toLowerCase().endsWith(".aiff")) {
            if(bitDept > 16) {
                options = " -sample_fmt s32 -y -acodec pcm_s"+bitDept+"be "; //aif
            }else {
                options = " -sample_fmt s16 -y -acodec pcm_s"+bitDept+"be "; //aif
            }
        }

        Log.i(TAG, "Converting: "+ srcPath);

        String ext = FileUtils.getExtension(srcPath);
        String targetExt = FileUtils.getExtension(targetPath).toLowerCase(Locale.US);
        File dir = context.getExternalCacheDir();
        String tmpPath = "/tmp/"+ DigestUtils.md5Hex(srcPath)+"."+ext;
        dir = new File(dir, tmpPath);
        FileUtils.createParentDirs(dir);
        tmpPath = dir.getAbsolutePath();
        FileSystem.copy(context, srcPath, tmpPath);

        String tmpTarget = tmpPath.replace("."+ext, "_NEWFMT."+targetExt);

        String cmd = " -hide_banner -nostats -i "+tmpPath+" "+options+" \""+tmpTarget+"\"";
        Log.i(TAG, "Converting with cmd: "+ cmd);

       // FFmpegKit.executeAsync(cmd, session -> callbak.onFinish(ReturnCode.isSuccess(session.getReturnCode())));
       try {
           FFmpegSession session = FFmpegKit.execute(cmd);

           if (!ReturnCode.isCancel(session.getReturnCode())) {
               File targetFile = new File(targetPath);
               if(targetFile.exists()) {
                   targetPath = targetPath.replace(".", "_001.");
               }
               FileSystem.move(context, tmpTarget, targetPath);
               //FileRepository.newInstance(context).scanMusicFile(new File(targetPath),true); // re scan file
               return true;
           }
       }finally {
           FileSystem.delete(tmpPath);
       }
       return false;
    }

   /* public static String escapePathForFFMPEG(String path) {
        path = StringUtils.trimToEmpty(path);
       // path = path.replace("'", "''");
        return path;
    } */

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

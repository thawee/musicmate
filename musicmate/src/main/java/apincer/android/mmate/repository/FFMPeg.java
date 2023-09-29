package apincer.android.mmate.repository;

import static apincer.android.mmate.utils.MusicTagUtils.isAIFFile;
import static apincer.android.mmate.utils.MusicTagUtils.isFLACFile;
import static apincer.android.mmate.utils.MusicTagUtils.isMPegFile;
import static apincer.android.mmate.utils.MusicTagUtils.isMp4File;
import static apincer.android.mmate.utils.MusicTagUtils.isWavFile;
import static apincer.android.mmate.utils.StringUtils.gainToDouble;
import static apincer.android.mmate.utils.StringUtils.getWord;
import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.toBoolean;
import static apincer.android.mmate.utils.StringUtils.toDouble;
import static apincer.android.mmate.utils.StringUtils.toDurationSeconds;
import static apincer.android.mmate.utils.StringUtils.toInt;
import static apincer.android.mmate.utils.StringUtils.toLong;
import static apincer.android.mmate.utils.StringUtils.toUpperCase;
import static apincer.android.mmate.utils.StringUtils.trim;
import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.anggrayudi.storage.file.DocumentFileCompat;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.FFprobeSession;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.Session;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import apincer.android.mmate.Constants;
import apincer.android.mmate.fs.FileSystem;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mqaidentifier.NativeLib;
import apincer.android.utils.FileUtils;

public class FFMPeg extends TagReader {
    private static final String TAG = FFMPeg.class.getName();
    public static final String KEY_MUSICMATE_DR = "MM_MDR";

    public static void extractCoverArt(MusicTag tag, File pathFile) {
        if(!isEmpty(tag.getEmbedCoverArt())) {
            String targetPath = pathFile.getAbsolutePath();
            targetPath = escapePathForFFMPEG(targetPath);
            String options = " -c:v copy ";

            String cmd = " -hide_banner -nostats -i \"" + tag.getPath() + "\" " + options + " \"" + targetPath + "\"";

            FFmpegKit.execute(cmd); // do not clear the result
        }
    }

    public static void extractCoverArt(String path, File pathFile) {
        //if(!isEmpty(tag.getEmbedCoverArt())) {
        try {
            String targetPath = pathFile.getAbsolutePath();
            targetPath = escapePathForFFMPEG(targetPath);
            String options = " -c:v copy ";

            String cmd = " -hide_banner -nostats -i \"" + path + "\" " + options + " \"" + targetPath + "\"";

            FFmpegKit.execute(cmd); // do not clear the result
        }catch (Exception ex) {
            Log.e("FFMPeg.extractCoverArt", ex.getMessage());
        }
    }

    public static String removeCoverArt(Context context, MusicTag tag) {
        if(!isEmpty(tag.getEmbedCoverArt())) {
            String pathFile = tag.getPath();
            String ext = FileUtils.getExtension(pathFile);
            pathFile = pathFile.replace("."+ext, "no_embed."+ext);
            String options = " -vn -codec:a copy ";
           // String options =" -map 0:V -y -codec copy ";

            String cmd = " -hide_banner -nostats -i \"" + tag.getPath() + "\" " + options + " \"" + pathFile+ "\"";
            Session session = FFmpegKit.execute(cmd); // do not clear the result
            if (ReturnCode.isSuccess(session.getReturnCode())) {
                FileSystem.safeMove(context, pathFile, tag.getPath(), true);
                //return pathFile;
                return tag.getPath();
            }else {
                FileSystem.delete(context, pathFile);
            }
        }
        return null;
    }

    public enum SupportedFileFormat
    {
        // OGG("ogg", "Ogg"),
        // OGA("oga", "Oga"),
        MP3("mp3", "Mp3"),
        FLAC("flac", "Flac"),
        //MP4("mp4", "Mp4"),
        M4A("m4a", "Mp4"),
        // M4P("m4p", "M4p"),
        // WMA("wma", "Wma"),
        WAV("wav", "Wav"),
        //  RA("ra", "Ra"),
        //  RM("rm", "Rm"),
        //  M4B("m4b", "Mp4"),
        AIF("aif", "Aif"),

        APE("ape", "Ape"),
        AIFF("aiff", "Aif"),
        //  AIFC("aifc", "Aif Compressed"),
        DSF("dsf", "Dsf"),
        DFF("dff", "Dff");

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

    private static final String KEY_BIT_RATE = "bit_rate";
   // private static final String KEY_FORMAT_NAME = "format_name";
    private static final String KEY_START_TIME = "start_time";
    private static final String KEY_DURATION = "duration";
    private static final String KEY_SIZE = "size";
    private static final String KEY_MQA = "MQA";
    private static final String KEY_TAG = "TAG:";
    private static final String KEY_TAG_ARTIST = "ARTIST";
    private static final String KEY_TAG_ALBUM = "ALBUM";
    private static final String KEY_TAG_ALBUM_ARTIST = "album_artist";
    private static final String KEY_TAG_COMPOSER = "COMPOSER";
    private static final String KEY_TAG_COMMENT = "COMMENT";
    private static final String KEY_TAG_COMPILATION = "COMPILATION";
    private static final String KEY_TAG_DISC = "disc"; //"DISCNUMBER";
    private static final String KEY_TAG_GENRE = "GENRE";
    private static final String KEY_TAG_GROUPING = "GROUPING";
    private static final String KEY_TAG_TRACK = "track";
    private static final String KEY_TAG_PUBLISHER = "PUBLISHER";
    //private static final String KEY_TAG_LANGUAGE = "LANGUAGE";
    private static final String KEY_TAG_ENCODER = "ENCODER";
    private static final String KEY_TAG_MQA_ORIGINAL_SAMPLERATE = "ORIGINALSAMPLERATE";
    private static final String KEY_TAG_MQA_ENCODER = "MQAENCODER";
    private static final String KEY_TAG_MEDIA = "MEDIA";
    private static final String KEY_TAG_RATING = "RATING";
    private static final String KEY_TAG_QUALITY = "QUALITY";
    private static final String KEY_TAG_TITLE = "TITLE";
    private static final String KEY_TAG_YEAR = "YEAR";

    // WAVE file
    //https://www.digitizationguidelines.gov/audio-visual/documents/listinfo.html
    private static final String KEY_TAG_WAVE_ARTIST = "IART"; //artist
    private static final String KEY_TAG_WAVE_ALBUM = "IPRD"; // album
    private static final String KEY_TAG_WAVE_ALBUM_ARTIST = "IENG"; // engineers
    private static final String KEY_TAG_WAVE_GENRE = "IGNR"; //genre
    private static final String KEY_TAG_WAVE_TRACK = "IPRT"; //track
    private static final String KEY_TAG_WAVE_TITLE = "INAM"; //title
    private static final String KEY_TAG_WAVE_YEAR = "date"; //""ICRD"; //date
    private static final String KEY_TAG_WAVE_MEDIA = "IMED";
    private static final String KEY_TAG_WAVE_COMMENT = "ICMT"; // comment
   // private static final String KEY_TAG_WAVE_PUBLISHER = "copyright"; //""ICOP"; //copy right
   // private static final String KEY_TAG_WAVE_LANGUAGE = "ILNG"; // language
    private static final String KEY_TAG_WAVE_PUBLISHER = "ISRC"; // name of person or organization
    private static final String KEY_TAG_WAVE_DISC = "ISRF"; // original form of material
    private static final String KEY_TAG_WAVE_GROUP = "IKEY"; // list of keyword, saperated by semicolon
    private static final String KEY_TAG_WAVE_COMPOSER = "ICMS"; // person who commision the subject
    private static final String KEY_TAG_WAVE_QUALITY = "ISBJ"; // Describes the contents of the file

    // AIF/AIFF
    private static final String KEY_TAG_AIF_ARTIST = "ARTIST";
    private static final String KEY_TAG_AIF_ALBUM = "ALBUM";
    private static final String KEY_TAG_AIF_ALBUM_ARTIST = "album_artist";
    private static final String KEY_TAG_AIF_COMPOSER = "COMPOSER";
    private static final String KEY_TAG_AIF_COMMENT = "COMMENT";
    private static final String KEY_TAG_AIF_COMPILATION = "COMPILATION";
    private static final String KEY_TAG_AIF_DISC = "disc"; //"DISCNUMBER";
    private static final String KEY_TAG_AIF_GENRE = "GENRE";
    private static final String KEY_TAG_AIF_GROUPING = "GROUPING";
    private static final String KEY_TAG_AIF_TRACK = "track";
    private static final String KEY_TAG_AIF_PUBLISHER = "PUBLISHER";
    //private static final String KEY_TAG_AIF_LANGUAGE = "LANGUAGE";
    private static final String KEY_TAG_AIF_MEDIA = "MEDIA";
    private static final String KEY_TAG_AIF_RATING = "RATING";
    private static final String KEY_TAG_AIF_QUALITY = "QUALITY";
    private static final String KEY_TAG_AIF_TITLE = "TITLE";
    private static final String KEY_TAG_AIF_YEAR = "YEAR";

    // QuickTime/MOV/MP4/M4A
    // https://wiki.multimedia.cx/index.php/FFmpeg_Metadata
    private static final String KEY_TAG_MP4_ARTIST = "author"; //artist
    private static final String KEY_TAG_MP4_ALBUM = "album"; // album
    private static final String KEY_TAG_MP4_ALBUM_ARTIST = "album_artist";
    private static final String KEY_TAG_MP4_GENRE = "genre"; //genre
    private static final String KEY_TAG_MP4_TRACK = "track"; //track
    private static final String KEY_TAG_MP4_TITLE = "title"; //title
    private static final String KEY_TAG_MP4_YEAR = "year"; //date
    private static final String KEY_TAG_MP4_COMPOSER = "composer";
    //private static final String KEY_TAG_MP4_MEDIA = "media_type";
    private static final String KEY_TAG_MP4_GROUPING = "grouping";
    //private static final String KEY_TAG_MP4_DISC = "disc";
    private static final String KEY_TAG_MP4_COMMENT = "comment";  // comment
    private static final String KEY_TAG_MP4_PUBLISHER = "copyright"; //copy right
   // private static final String KEY_TAG_MP4_LANGUAGE = "language"; // language
   // private static final String KEY_TAG_MP4_RATING = "rating";
   // private static final String KEY_TAG_MP4_QUALITY = "Song-DB_Preference";
   // private static final String KEY_TAG_MP4_COMPILATION = "compilation";

    //https://gist.github.com/eyecatchup/0757b3d8b989fe433979db2ea7d95a01
    private static final String KEY_TAG_MP3_ARTIST = "artist"; //artist
    private static final String KEY_TAG_MP3_ALBUM = "album"; // album
    private static final String KEY_TAG_MP3_ALBUM_ARTIST = "album_artist";
    private static final String KEY_TAG_MP3_GENRE = "genre"; //genre
    private static final String KEY_TAG_MP3_TRACK = "track"; //track
    private static final String KEY_TAG_MP3_TITLE = "title"; //title
    private static final String KEY_TAG_MP3_YEAR = "date"; //date
    //private static final String KEY_TAG_MP3_COMPOSER = "composer";
    //private static final String KEY_TAG_MP3_MEDIA = "media_type";
    //private static final String KEY_TAG_MP3_GROUPING = "grouping";
    private static final String KEY_TAG_MP3_DISC = "disc";
    private static final String KEY_TAG_MP3_COMMENT = "comment";  // comment
    //private static final String KEY_TAG_MP3_PUBLISHER = "copyright"; //copy right

    private static final String KEY_TRACK_GAIN = "REPLAYGAIN_TRACK_GAIN";
    private static final String KEY_TRACK_PEAK = "REPLAYGAIN_TRACK_PEAK"; // added by thawee
    private static final String KEY_ALBUM_GAIN = "REPLAYGAIN_ALBUM_GAIN";
    private static final String KEY_ALBUM_PEAK = "REPLAYGAIN_ALBUM_PEAK"; // added by thawee
    private static final String KEY_TRACK_LOUDNESS = "REPLAYGAIN_REFERENCE_LOUDNESS";

    private static final String KEY_TRACK_DYNAMIC_RANGE = "TRACK_DYNAMIC_RANGE";

    private static final String METADATA_KEY = "-metadata";

    public static class Loudness {
        public double getIntegratedLoudness() {
            return integratedLoudness;
        }

        public double getLoudnessRange() {
            return loudnessRange;
        }

        public double getTruePeak() {
            return truePeak;
        }

        double integratedLoudness;
        double loudnessRange;
        double truePeak;

        public Loudness(double integrated, double range, double peak) {
            this.integratedLoudness = integrated;
            this.loudnessRange = range;
            this.truePeak = peak;
        }
    }

    public interface CallBack {
        void onFinish(boolean status);
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

    public List<MusicTag> readMusicTag(Context context, String path) {
        //return readFFprobe(path);
        Log.d(TAG, "FFMpeg -> "+path);
        MusicTag tag = readTagFromFile(context, path);
       // MusicTag tag = readFFprobe(context, path);
        detectMQA(tag,50000); // timeout 50 seconds
        //analystStatFromFile(context, tag,60000); // timeout 50 seconds
        return Arrays.asList(tag);
    }

    /*
    @Deprecated
    public static void readLoudness(Context context, MusicTag tag) {
        String targetPath = tag.getPath();
        targetPath = escapePathForFFMPEG(targetPath);
        String cmd = " -hide_banner -nostats -i \"" + targetPath + "\" -filter_complex ebur128=peak=true:framelog=verbose -f null -";
        //ffmpeg -nostats -i ~/Desktop/input.wav -filter_complex ebur128=peak=true -f null -
        // String cmd = "-i \""+path+"\" -af loudnorm=I=-16:TP=-1.5:LRA=11:print_format=json -f null -";
        FFmpegSession session = FFmpegKit.execute(cmd);
        String data = getFFmpegOutputData(session);
        parseLoudness(tag, data);
        session.cancel();

        if(tag.getTrackLoudness()==-70.0) {
            // trye reading again, first time may get error
            session = FFmpegKit.execute(cmd);
            data = getFFmpegOutputData(session);
            parseLoudness(tag, data);
            session.cancel();
        }
    } */
  /*
    @Deprecated
    private static void parseLoudness(MusicTag tag, String data) {
        String keyword = "Integrated loudness:";
        int startTag = data.lastIndexOf(keyword);
        if (startTag > 0) {
            String loudness = data.substring(data.indexOf("I:",startTag) + 3, data.indexOf("LUFS",startTag));
            //String range = data.substring(data.indexOf("LRA:",startTag) + 5, data.indexOf("LU\n",startTag));
            String truePeak = data.substring(data.indexOf("Peak:",startTag) + 6, data.indexOf("dBFS",startTag));
           // tag.setTrackLoudness(toDouble(loudness));
            //tag.setTrackRange(toDouble(range));
            tag.setTrackTruePeak(toDouble(truePeak));
        }
    } */

    public static void detectQuality(MusicTag tag) {
        // String cmd ="-hide_banner -of default=noprint_wrappers=0 -show_format -print_format json \""+path+"\"";
       // String filter = " -filter:a drmeter,replaygain,volumedetect,astats -vn -sn -dn "; // -vn -sn -dn to ignore none audio to speed up the process
         String filter = " -filter:a drmeter,replaygain,astats -vn -sn -dn "; // -vn -sn -dn to ignore none audio to speed up the process
        // if(MusicTagUtils.isDSDFile(path)) {
       //     filter = " -filter:a drmeter ";
       // }
        String targetPath = tag.getPath();
        targetPath = escapePathForFFMPEG(targetPath);
       // String filter = " -filter:a replaygain ";

        String cmd ="-hide_banner -nostats -i \""+targetPath+"\""+filter+" -f null -";

        FFmpegSession session = FFmpegKit.execute(cmd);
       // if (ReturnCode.isSuccess(session.getReturnCode())) {
        String data = session.getOutput();
        //    String data = getFFmpegOutputData(session);
        parseReplayGain(tag, data);
        parseDynamicRange(tag, data);
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
                tag.setMeasuredDR(toDouble(info));
               // tag.setAstatDR(toDouble(info));
            }
        }catch (Exception ex) {
            Log.e(TAG, "parseVolume", ex);
        }
    }

    private static void parseVolume(MusicTag tag, String data) {
        try {
            // Overall DR: 14.0357
            Pattern pattern = Pattern.compile("\\s*Overall DR:\\s*(\\S*)");
            Matcher matcher = pattern.matcher(data);
            if (matcher.find()) {
                String info = matcher.group(1);
                tag.setTrackDR(toDouble(info));
            }
        }catch (Exception ex) {
            Log.e(TAG, "parseVolume", ex);
        }
    }

    public static MusicTag readTagFromFile(Context context, String path) {
        // String cmd ="-hide_banner -of default=noprint_wrappers=0 -show_format -print_format json \""+path+"\"";
        // String filter = " -filter:a drmeter,replaygain ";
        // if(MusicTagUtils.isDSDFile(path)) {
        //     filter = " -filter:a drmeter ";
        // }
       // String filter = " -filter:a drmeter ";

       // String cmd ="-hide_banner -nostats -i \""+path+"\""+filter+" -f null -";
        String cmd ="-hide_banner -nostats -i \""+path+"\" -f null -";

        FFmpegSession session = FFmpegKit.execute(cmd);
        if (ReturnCode.isSuccess(session.getReturnCode())) {
            String data = getFFmpegOutputData(session);
            String output = session.getOutput();
            MusicTag tag = new MusicTag();
            tag.setData("Running Time:"+session.getDuration()+"\n"+data);
            tag.setPath(path);
            readFileInfo(context,tag);
            parseStreamInfo(tag,output);
            detectFileFormat(tag);
            parseDurationInfo(tag, output);

            parseTagsInfo(tag);
            // parseReplayGain(tag);
          //  parseDynamicRange(tag);

           // tag.setLossless(isLossless(tag));
           // tag.setFileSizeRatio(getFileSizeRatio(tag));
            return tag;
        }else {
            // try to get from file name
            MusicTag tag = new MusicTag();
            String data = getFFmpegOutputData(session);
            String output = session.getOutput();
            tag.setReadError(true);
            tag.setData("Running Time (error):"+session.getDuration()+"\n"+data);
            tag.setPath(path);
            readFileInfo(context,tag);
            parseStreamInfo(tag,output);
            detectFileFormat(tag);
            parseDurationInfo(tag,output);
            parseTagsInfo(tag);
            // parseReplayGain(tag);
           // parseDynamicRange(tag);

           // tag.setLossless(isLossless(tag));
           // tag.setFileSizeRatio(getFileSizeRatio(tag));
            session.cancel();
            return tag;
        }
    }

    private static void detectFileFormat(MusicTag tag) {
        String encoding = trimToEmpty(tag.getAudioEncoding());
        String ext = FileUtils.getExtension(tag.getPath()).toLowerCase(Locale.US);
        if(encoding.toUpperCase(Locale.US).contains(Constants.MEDIA_ENC_AAC)) {
            tag.setFileFormat(Constants.MEDIA_ENC_AAC.toLowerCase(Locale.US));
            tag.setAudioEncoding(Constants.MEDIA_ENC_AAC.toLowerCase(Locale.US));
        }else if(encoding.toUpperCase(Locale.US).contains(Constants.MEDIA_ENC_ALAC)) {
                tag.setFileFormat(Constants.MEDIA_ENC_ALAC.toLowerCase(Locale.US));
                tag.setAudioEncoding(Constants.MEDIA_ENC_ALAC.toLowerCase(Locale.US));
        } else if(Constants.FILE_EXT_AIF.equalsIgnoreCase(ext)||Constants.FILE_EXT_AIFF.equalsIgnoreCase(ext)){
            tag.setFileFormat(Constants.MEDIA_ENC_AIFF.toLowerCase(Locale.US));
        }else if(Constants.FILE_EXT_WAVE.equalsIgnoreCase(ext)){
            tag.setFileFormat(Constants.MEDIA_ENC_WAVE.toLowerCase(Locale.US));
        }else if(Constants.FILE_EXT_MP3.equalsIgnoreCase(ext)){
            tag.setFileFormat(Constants.MEDIA_ENC_MPEG.toLowerCase(Locale.US));
        }else {
            tag.setFileFormat(ext);
        }
    }

    private static void parseDurationInfo(MusicTag tag, String data) {
        try {
            //Duration: 00:04:49.11, start: 0.047889, bitrate: 305 kb/s
            //Duration: 00:04:13.33, start: 0.000000, bitrate: 2358 kb/s
            Pattern pattern = Pattern.compile("\\s*Duration:\\s*([\\s\\S]*?),\\s*start:\\s*([\\s\\S]*?),\\s+bitrate:\\s*([\\s\\S]*?)\\s*kb/s");
            Matcher matcher = pattern.matcher(data);
            if (matcher.find()) {
                tag.setAudioDuration(toDurationSeconds(matcher.group(1)));
                tag.setAudioStartTime(toDouble(matcher.group(2)));
                tag.setAudioBitRate(toLong(matcher.group(3)) * 1000);
            }else {
                pattern = Pattern.compile("\\s*Duration:\\s*([\\s\\S]*?),\\s+bitrate:\\s*([\\s\\S]*?)\\s*kb/s");
                matcher = pattern.matcher(data);
                if (matcher.find()) {
                    tag.setAudioDuration(toDurationSeconds(matcher.group(1)));
                    tag.setAudioStartTime(0.0);
                    tag.setAudioBitRate(toLong(matcher.group(2)) * 1000);
                }
            }
        }catch (Exception ex) {
            Log.e(TAG, "parseDurationInfo", ex);
        }
    }

    private static void parseTagsInfo(MusicTag tag) {
        // Input #0,
        // Output #0,
        // Stream #0:0: Audio:
       // Pattern pattern = Pattern.compile("(?m)^\\s*Stream.*?(?:Video):\\s*([\\s\\S]*?)(?=^\\s*Metadata:|\\Z)");
       String data = tag.getData();
       String []lines = data.split("\n");
        Map<String, String> tags = new HashMap<>();
        String prevKey="";
       for(String line: lines) {
           line = trimToEmpty(line);
           int ind = line.indexOf(":");
           if(ind >0) {
              // String[] field = line.split(":");
               String key = trimToEmpty(line.substring(0, ind)); //getField(field, 0);
               String val = trimToEmpty(line.substring(ind+1)); //getField(field, 1);
               if (!tags.containsKey(toUpperCase(key))) {
                   tags.put(toUpperCase(key), trimToEmpty(val)); // all upper case
                   tags.put(trimToEmpty(key), trimToEmpty(val)); // as is from file
               }
               prevKey = key;
           }else if(ind==0){
               // 2nd line
               String val = trimToEmpty(line.substring(ind+1)); //getField(field, 1);
               if (tags.containsKey(toUpperCase(prevKey))) {
                   String vl = trimToEmpty(tags.get(toUpperCase(prevKey))); // all upper case
                   tags.put(toUpperCase(prevKey), vl+"\n"+trimToEmpty(val)); // all upper case
                   tags.put(trimToEmpty(prevKey), vl+"\n"+trimToEmpty(val)); // as is from file
               }
           }
       }
       // populate tags
        // KEY_TAG_ALBUM
        tag.setAlbum(getValueForKey(tags, KEY_TAG_ALBUM));

        //KEY_TAG_ALBUM_ARTIST
        tag.setAlbumArtist(getValueForKey(tags, KEY_TAG_ALBUM_ARTIST));

        // KEY_TAG_ARTIST
        tag.setArtist(getValueForKey(tags, KEY_TAG_ARTIST));

        //KEY_TAG_COMPOSER
        tag.setComposer(getValueForKey(tags, KEY_TAG_COMPOSER));

        //KEY_TAG_COMPILATION
        tag.setCompilation(toBoolean(getValueForKey(tags, KEY_TAG_COMPILATION)));

        // YEAR
        tag.setYear(getValueForKey(tags, KEY_TAG_YEAR));

        //KEY_TAG_COMMENT
        tag.setComment(getValueForKey(tags, KEY_TAG_COMMENT));

        //KEY_TAG_DISCNUMBER
        tag.setDisc(getValueForKey(tags, KEY_TAG_DISC));

        //KEY_TAG_GENRE
        tag.setGenre(getValueForKey(tags, KEY_TAG_GENRE));

        //KEY_TAG_GROUPING
        tag.setGrouping(getValueForKey(tags, KEY_TAG_GROUPING));

        //KEY_TAG_TRACK
        tag.setTrack(getValueForKey(tags, KEY_TAG_TRACK));

        //KEY_TAG_MEDIA
        tag.setMediaType(getValueForKey(tags, KEY_TAG_MEDIA));

        //KEY_TAG_RATING
        tag.setRating(toInt(getValueForKey(tags, KEY_TAG_RATING)));

        //KEY_TAG_QUALITY
        tag.setMediaQuality(getValueForKey(tags, KEY_TAG_QUALITY));

        //KEY_TAG_TITLE
        tag.setTitle(getValueForKey(tags, KEY_TAG_TITLE));
        if(isEmpty(tag.getTitle())) {
            tag.setTitle(FileUtils.getFileName(tag.getPath()));
        }

        tag.setTrackDR(toDouble(getValueForKey(tags, KEY_TRACK_DYNAMIC_RANGE)));

        // KEY_TRACK_GAIN
        tag.setTrackRG(toDouble(getValueForKey(tags, KEY_TRACK_GAIN)));

        //KEY_TRACK_PEAK
        tag.setTrackTruePeak(toDouble(getValueForKey(tags, KEY_TRACK_PEAK)));

        // KEY_ALBUM_GAIN
       // tag.setAlbumRG(toDouble(getValueForKey(tags, KEY_ALBUM_GAIN)));

        //KEY_ALBUM_PEAK
       // tag.setAlbumTruePeak(toDouble(getValueForKey(tags, KEY_ALBUM_PEAK)));

        // publisher
        tag.setPublisher(getValueForKey(tags, KEY_TAG_PUBLISHER));


        // replay gain
        // tag.setTrackLoudness(toDouble(getValueForKey(tags, KEY_TRACK_LOUDNESS)));
        tag.setTrackRG(gainToDouble(getValueForKey(tags, KEY_TRACK_GAIN)));
        tag.setTrackTruePeak(toDouble(getValueForKey(tags, KEY_TRACK_PEAK)));
        // tag.setAlbumRG(gainToDouble(getValueForKey(tags, KEY_ALBUM_GAIN)));
        // tag.setAlbumTruePeak(toDouble(getValueForKey(tags, KEY_ALBUM_PEAK)));
        tag.setMeasuredDR(toDouble(getValueForKey(tags, KEY_MUSICMATE_DR)));

        // MQA from tag and encoder
        String encoder = getValueForKey(tags, KEY_TAG_ENCODER);
        String mqaEncoder = getValueForKey(tags, KEY_TAG_MQA_ENCODER);
        if(!MusicTagUtils.isFLACFile(tag)) {
            tag.setMqaInd("None");
        }else if(encoder.contains(KEY_MQA) || !isEmpty(mqaEncoder)) {
            tag.setMqaInd("MQA");
            tag.setMqaSampleRate(toLong(getValueForKey(tags, KEY_TAG_MQA_ORIGINAL_SAMPLERATE)));
            tag.setMqaScanned(false);
        }

        // read Quick time Specific tags
        if(isMp4File(tag)) {
            tag.setTitle(getValueForKey(tags, KEY_TAG_MP4_TITLE));
            tag.setAlbumArtist(getValueForKey(tags, KEY_TAG_MP4_ALBUM_ARTIST));
            tag.setComposer(getValueForKey(tags, KEY_TAG_MP4_COMPOSER));
            // tag.setCompilation(toBoolean(getTagforKey(tags, KEY_TAG_MP4_COMPILATION)));
            // tag.setMediaType(getTagforKey(tags, KEY_TAG_MP4_MEDIA));
            // tag.setMediaQuality(getTagforKey(tags, KEY_TAG_MP4_QUALITY));
            tag.setPublisher(getValueForKey(tags, KEY_TAG_MP4_PUBLISHER));
            tag.setYear(getValueForKey(tags, KEY_TAG_MP4_YEAR));
        }

        // read Wave specific tags
        if(MusicTagUtils.isWavFile(tag)) {
            tag.setComposer(getValueForKey(tags, KEY_TAG_WAVE_COMPOSER));
            tag.setAlbumArtist(getValueForKey(tags, KEY_TAG_WAVE_ALBUM_ARTIST));
            tag.setGrouping(getValueForKey(tags, KEY_TAG_WAVE_GROUP));
            tag.setMediaType(getValueForKey(tags, KEY_TAG_WAVE_MEDIA));
            tag.setPublisher(getValueForKey(tags, KEY_TAG_WAVE_PUBLISHER));
            tag.setYear(getValueForKey(tags, KEY_TAG_WAVE_YEAR));
            tag.setMediaQuality(getValueForKey(tags, KEY_TAG_WAVE_QUALITY));
            tag.setDisc(getValueForKey(tags, KEY_TAG_WAVE_DISC));
            parseMMComment(tag);
        }else if(MusicTagUtils.isMPegFile(tag)) {
            parseMMComment(tag);
        }else if(MusicTagUtils.isDSDFile(tag.getPath())) {
            tag.setAudioBitsDepth(1);
        }
    }

    private static void parseReplayGain(MusicTag tag, String data) {
        try {
            //[Parsed_replaygain_1 @ 0x7f82d1b047c0] track_gain = -0.37 dB
            //[Parsed_replaygain_1 @ 0x7f82d1b047c0] track_peak = 0.931971
            if(data.contains("[Parsed_replaygain")) {
                data = data.substring(data.indexOf("[Parsed_replaygain"));
            }
            Pattern pattern = Pattern.compile("\\s*track_gain =\\s*(\\S*)");
            Matcher matcher = pattern.matcher(data);
            if (matcher.find()) {
                String info = matcher.group(1);
                info = info.replace("+", "");
                tag.setTrackRG(toDouble(info));
            }

            pattern = Pattern.compile("\\s*track_peak =\\s*(\\S*)");
            matcher = pattern.matcher(data);
            if (matcher.find()) {
                String info = matcher.group(1);
                tag.setTrackTruePeak(toDouble(info));
            }
        }catch (Exception ex) {
            Log.e(TAG, "parseReplayGain", ex);
        }
    }

    private static void parseDynamicRange(MusicTag tag, String data) {
        try {
            // Overall DR: 14.0357
            Pattern pattern = Pattern.compile("\\s*Overall DR:\\s*(\\S*)");
            Matcher matcher = pattern.matcher(data);
            if (matcher.find()) {
                String info = matcher.group(1);
                tag.setTrackDR(toDouble(info));
            }
        }catch (Exception ex) {
            Log.e(TAG, "parseDynamicRange", ex);
        }
    }

    @Deprecated
    public static MusicTag readFFprobe(Context context, String path) {
       // String cmd ="-hide_banner -of default=noprint_wrappers=0 -show_format -print_format json \""+path+"\"";
        String cmd ="-hide_banner -of default=noprint_wrappers=0 -show_format \""+path+"\"";

        FFprobeSession session = FFprobeKit.execute(cmd);
        if (ReturnCode.isSuccess(session.getReturnCode())) {
            String data = session.getOutput();
            MusicTag tag = new MusicTag();
            tag.setData("Running Time:"+session.getDuration()+"\n"+data);
            tag.setPath(path);
            readFileInfo(context, tag);
            parseStreamInfo(tag,data);
            parseFormatInfo(tag);
            detectFileFormat(tag);
            //tag.setLossless(isLossless(tag));
           // tag.setFileSizeRatio(getFileSizeRatio(tag));

            parseDurationInfo(tag, session.getOutput());
            parseTagsInfo(tag);
            // parseReplayGain(tag);
            parseDynamicRange(tag, data);
            detectFileFormat(tag);
            // tag.setLossless(isLossless(tag));
           // tag.setFileSizeRatio(getFileSizeRatio(tag));
            return tag;
        }else {
            // try to get from file name
            MusicTag tag = new MusicTag();
            tag.setReadError(true);
            tag.setData(session.getOutput());
            tag.setPath(path);
            detectFileFormat(tag);
            session.cancel();
            Log.d(TAG, session.getOutput());
            return tag;
        }
    }

    public static boolean writeTagQualityToFile(Context context, MusicTag tag) {
        if(MusicTagUtils.isWavFile(tag)) return false; // wave file not support track gain
        if(!MusicTagUtils.isLossless(tag)) return false; // no need for compress encoding
        /// check free space on storage
        // ffmpeg write to new tmp file
        // ffmpeg -i aiff.aiff -map 0 -y -codec copy -write_id3v2 1 -metadata "artist-sort=emon feat sort" aiffout.aiff
        // ffmpeg -hide_banner -i aiff.aiff -map 0 -y -codec copy -metadata "artist-sort=emon feat sort" aiffout.aiff
        String srcPath = tag.getPath();
        File dir = context.getExternalCacheDir();
        String targetPath = "/tmp/"+ DigestUtils.md5Hex(srcPath)+"."+tag.getFileFormat();
        dir = new File(dir, targetPath);
        if(!dir.getParentFile().exists()) {
            dir.getParentFile().mkdirs();
        }
        targetPath = dir.getAbsolutePath();
        //targetPath = escapePathForFFMPEG(targetPath);
        String metadataKeys = getMetadataTrackGainKeys(tag);

        String options = " -hide_banner -nostats ";
        String copyOption = " -map 0 -y -codec copy ";
        if(isMPegFile(tag)) {
            //options = options + " -fflags +genpts ";
            copyOption = " -c copy ";
        }

        String cmd = options+" -i \"" + srcPath + "\""+ copyOption + metadataKeys+ "\""+targetPath+"\"";
        //ffmpeg -nostats -i ~/Desktop/input.wav -filter_complex ebur128=peak=true -f null -
        // String cmd = "-i \""+path+"\" -af loudnorm=I=-16:TP=-1.5:LRA=11:print_format=json -f null -";
        FFmpegSession session = FFmpegKit.execute(cmd);
        if(ReturnCode.isSuccess(session.getReturnCode())) {
            // success
            // moveSafe file
            if(FileSystem.safeMove(context, targetPath, srcPath)) {
                FileRepository.newInstance(context).scanMusicFile(new File(srcPath), true);
                return true;
            }else {
                FileSystem.delete(context, targetPath); // delete source file to clear space
                return false;
            }
        }else {
            Log.i(TAG, session.getOutput());
            // fail, delete tmp file;
            FileSystem.delete(context, new File(targetPath));
            return false;
        }
    }

    public static boolean writeTagToFile(Context context, MusicTag tag) {
        /// check free space on storage
        // ffmpeg write to new tmp file
        // ffmpeg -i aiff.aiff -map 0 -y -codec copy -write_id3v2 1 -metadata "artist-sort=emon feat sort" aiffout.aiff
        // ffmpeg -hide_banner -i aiff.aiff -map 0 -y -codec copy -metadata "artist-sort=emon feat sort" aiffout.aiff
        String srcPath = tag.getPath();
        File dir = context.getExternalCacheDir();
        //File dir = FileSystem.getDownloadPath(context, "tmp");
        String ext = FileUtils.getExtension(srcPath);
        String targetPath = "/tmp/"+ DigestUtils.md5Hex(srcPath)+"."+ext;
        dir = new File(dir, targetPath);
        if(!dir.getParentFile().exists()) {
            dir.getParentFile().mkdirs();
        }
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
                    FileRepository.newInstance(context).scanMusicFile(new File(srcPath),true);
                    return true;
                }else {
                    FileSystem.delete(context, targetPath); // delete source file to clear space
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
                FileSystem.delete(context, tmp);
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
        String tags = " -write_id3v2 1 " +
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
        return tags;
    }

    private static String getMetadataTrackKey(String key, String val) {
        return METADATA_KEY + " " + key + "=\"" + trimToEmpty(val).replace("\"","\\\"") + "\" ";
    }
    private static String getMetadataTrackKey(String key, boolean val) {
        return METADATA_KEY + " " + key + "=\"" + (val?1:0) + "\" ";
    }
    private static String getMetadataTrackKey(String key, int val) {
        return METADATA_KEY + " " + key + "=\"" + val + "\" ";
    }
    private static String getMetadataTrackKey(String key, double val) {
        return METADATA_KEY + " " + key + "=\"" + val + "\" ";
    }

    private static String getMetadataTrackKeysForFlac(MusicTag tag) {
        // work for all fields
        String tags = getMetadataTrackKey(KEY_TAG_TITLE, tag.getTitle()) +
                getMetadataTrackKey(KEY_TAG_ALBUM, tag.getAlbum()) +
                getMetadataTrackKey(KEY_TAG_ARTIST, tag.getArtist()) +
                getMetadataTrackKey(KEY_TAG_ALBUM_ARTIST, tag.getAlbumArtist()) +
                getMetadataTrackKey(KEY_TAG_COMPOSER, tag.getComposer()) +
                getMetadataTrackKey(KEY_TAG_COMPILATION, (tag.isCompilation())) +
                getMetadataTrackKey(KEY_TAG_COMMENT, tag.getComment()) +
                getMetadataTrackKey(KEY_TAG_DISC, tag.getDisc()) +
                getMetadataTrackKey(KEY_TAG_GENRE, tag.getGenre()) +
                getMetadataTrackKey(KEY_TAG_GROUPING, tag.getGrouping()) +
                getMetadataTrackKey(KEY_TAG_PUBLISHER, tag.getPublisher()) +
                getMetadataTrackKey(KEY_TAG_MEDIA, tag.getMediaType()) +
                getMetadataTrackKey(KEY_TAG_QUALITY, tag.getMediaQuality()) +
                getMetadataTrackKey(KEY_TAG_RATING, tag.getRating()) +
                getMetadataTrackKey(KEY_TAG_TRACK, tag.getTrack()) +
                getMetadataTrackKey(KEY_TAG_YEAR, tag.getYear());
        return tags;
    }

    private static String getMetadataTrackKeysForMp4(MusicTag tag) {
        String tags = getMetadataTrackKey(KEY_TAG_MP4_TITLE, tag.getTitle()) +
                getMetadataTrackKey(KEY_TAG_MP4_ALBUM, tag.getAlbum()) +
                getMetadataTrackKey(KEY_TAG_MP4_ARTIST, tag.getArtist()) +
                getMetadataTrackKey(KEY_TAG_MP4_ALBUM_ARTIST, tag.getAlbumArtist()) +
                getMetadataTrackKey(KEY_TAG_MP4_COMPOSER, tag.getComposer()) +
              //  getMetadataTrackKey(KEY_TAG_MP4_COMPILATION, (tag.isCompilation())) +
                getMetadataTrackKey(KEY_TAG_MP4_COMMENT, tag.getComment()) +
              //  getMetadataTrackKey(KEY_TAG_MP4_DISC, tag.getDisc()) +
                getMetadataTrackKey(KEY_TAG_MP4_GENRE, tag.getGenre()) +
                getMetadataTrackKey(KEY_TAG_MP4_GROUPING, tag.getGrouping()) +
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
        String tags = " -write_id3v2 1 " +
                getMetadataTrackKey(KEY_TAG_AIF_TITLE, tag.getTitle()) +
                getMetadataTrackKey(KEY_TAG_AIF_ALBUM, tag.getAlbum()) +
                getMetadataTrackKey(KEY_TAG_AIF_ARTIST, tag.getArtist()) +
                getMetadataTrackKey(KEY_TAG_AIF_ALBUM_ARTIST, tag.getAlbumArtist()) +
                getMetadataTrackKey(KEY_TAG_AIF_COMPOSER, tag.getComposer()) +
                getMetadataTrackKey(KEY_TAG_AIF_COMPILATION, (tag.isCompilation())) +
                getMetadataTrackKey(KEY_TAG_AIF_COMMENT, tag.getComment()) +
                getMetadataTrackKey(KEY_TAG_AIF_DISC, tag.getDisc()) +
                getMetadataTrackKey(KEY_TAG_AIF_GENRE, tag.getGenre()) +
                getMetadataTrackKey(KEY_TAG_AIF_GROUPING, tag.getGrouping()) +
                getMetadataTrackKey(KEY_TAG_AIF_PUBLISHER, tag.getPublisher()) +
                getMetadataTrackKey(KEY_TAG_AIF_MEDIA, tag.getMediaType()) +
                getMetadataTrackKey(KEY_TAG_AIF_QUALITY, tag.getMediaQuality()) +
                getMetadataTrackKey(KEY_TAG_AIF_RATING, tag.getRating()) +
                getMetadataTrackKey(KEY_TAG_AIF_TRACK, tag.getTrack()) +
                getMetadataTrackKey(KEY_TAG_AIF_YEAR, tag.getYear());
        return tags;
    }

    private static String getMetadataTrackKeysForWave(MusicTag tag) {
        // need to include all metadata
        //String comment = getWaveComment(tag);
        String tags = getMetadataTrackKey(KEY_TAG_WAVE_TITLE,tag.getTitle()) +
                getMetadataTrackKey(KEY_TAG_WAVE_ALBUM,tag.getAlbum())+
                getMetadataTrackKey(KEY_TAG_WAVE_ALBUM_ARTIST,tag.getAlbumArtist())+
                getMetadataTrackKey(KEY_TAG_WAVE_ARTIST,tag.getArtist())+
                getMetadataTrackKey(KEY_TAG_WAVE_GENRE,tag.getGenre())+
               // getMetadataTrackKey(KEY_TAG_WAVE_LANGUAGE,tag.getLanguage())+
                getMetadataTrackKey(KEY_TAG_WAVE_TRACK,tag.getTrack())+
                getMetadataTrackKey(KEY_TAG_WAVE_YEAR,tag.getYear())+
                getMetadataTrackKey(KEY_TAG_WAVE_COMPOSER,tag.getComposer())+
                getMetadataTrackKey(KEY_TAG_WAVE_GROUP,tag.getGrouping())+
                getMetadataTrackKey(KEY_TAG_WAVE_MEDIA,tag.getMediaType())+
                getMetadataTrackKey(KEY_TAG_WAVE_DISC,tag.getDisc())+
                getMetadataTrackKey(KEY_TAG_WAVE_PUBLISHER,tag.getPublisher())+
                getMetadataTrackKey(KEY_TAG_WAVE_QUALITY,tag.getMediaQuality()) +
                getMetadataTrackKey(KEY_TAG_WAVE_COMMENT,prepareMMComment(tag));
        return tags;
    }

    private static String prepareMMComment(MusicTag musicTag) {
        String comment = StringUtils.trimToEmpty(musicTag.getComment()) +"\n<##>" +
                StringUtils.trimToEmpty(musicTag.getDisc())+
                "#"+StringUtils.trimToEmpty(musicTag.getGrouping())+
                "#"+StringUtils.trimToEmpty(musicTag.getMediaQuality())+
                "#"+musicTag.getRating()+
                "#"+StringUtils.trimToEmpty(musicTag.getAlbumArtist())+
                "#"+StringUtils.trimToEmpty(musicTag.getComposer())+
                "#"+musicTag.getTrackDR()+
                "#"+musicTag.getMeasuredDR()+
                "#"+musicTag.getMeasuredSamplingRate()+
                "</##> ";
        return comment;
    }

    private static boolean isFieldChanged(boolean compilation, boolean compilation1) {
        return compilation !=compilation1;
    }

    private static boolean isFieldChanged(int rating, int rating1) {
        return (rating!=rating1);
    }

    private static boolean isFieldChanged(String text1, String text2) {
        return !StringUtils.equals(text1, text2);
    }

    @SuppressLint("DefaultLocale")
    private static String getMetadataTrackGainKeys(MusicTag tag) {
        // -metadata language="eng"
        // AIF, mp3, mp4, m4a - ID3v2
        // flac ogg - Vorbis comments
        // wave - not support - use bwf
        //https://bytesandbones.wordpress.com/2017/03/16/audio-nomalization-with-ffmpeg-using-loudnorm-ebur128-filter/
// https://whoislewys.com/2018/10/18/audio_norm_guides_are_evil_and_immoral/
        // The four tags are REPLAYGAIN_TRACK_GAIN, REPLAYGAIN_TRACK_PEAK,
        // REPLAYGAIN_ALBUM_GAIN and REPLAYGAIN_ALBUM_PEAK.

        /*
    "REPLAYGAIN_TRACK_GAIN",
    "REPLAYGAIN_TRACK_PEAK",
    "REPLAYGAIN_TRACK_RANGE",
    "REPLAYGAIN_ALBUM_GAIN",
    "REPLAYGAIN_ALBUM_PEAK",
    "REPLAYGAIN_ALBUM_RANGE",
    "REPLAYGAIN_REFERENCE_LOUDNESS"


    TRACK_DYNAMIC_RANGE
         */
        if(isMPegFile(tag)) {
            return getMetadataTrackKeysForMp3(tag);
        }else if(isWavFile(tag)) {
            return getMetadataTrackKeysForWave(tag);
        }

        String tags = "";

        if(isAIFFile(tag) || isMp4File(tag)) {
            tags = " -write_id3v2 1 ";
        }

        if(tag.getTrackDR() == 0.0) {
            tags = tags +
                    getMetadataTrackKey(KEY_TRACK_DYNAMIC_RANGE, "");
        }else {
            tags = tags +
                    getMetadataTrackKey(KEY_TRACK_DYNAMIC_RANGE, tag.getTrackDR());
        }

       // if(tag.getTrackLoudness() == 0.0) {
            tags = tags +
                    getMetadataTrackKey(KEY_TRACK_LOUDNESS, "");
      /*  }else {
            tags = tags +
                    getMetadataTrackKey(KEY_TRACK_LOUDNESS, tag.getTrackLoudness());
        }*/

        if(tag.getTrackRG() == 0.0) {
            tags = tags +
                    getMetadataTrackKey(KEY_TRACK_GAIN, "") +
                    getMetadataTrackKey(KEY_TRACK_PEAK, "");
        }else {
            tags = tags +
                    getMetadataTrackKey(KEY_TRACK_GAIN, String.format("%,.2f dB", tag.getTrackRG())) +
                    getMetadataTrackKey(KEY_TRACK_PEAK, String.format("%,.6f", tag.getTrackTruePeak()));
        }
       // if(tag.getAlbumRG() == 0.0) {
            tags = tags +
                    getMetadataTrackKey(KEY_ALBUM_GAIN, "") +
                    getMetadataTrackKey(KEY_ALBUM_PEAK, "");

        if(tag.getMeasuredDR() == 0.0) {
            tags = tags +
                    getMetadataTrackKey(KEY_MUSICMATE_DR, "");
        }else {
            tags = tags +
                    getMetadataTrackKey(KEY_MUSICMATE_DR, tag.getMeasuredDR());
        }
       /* }else {
            tags = tags +
                    getMetadataTrackKey(KEY_ALBUM_GAIN, String.format("%,.2f dB", tag.getAlbumRG())) +
                    getMetadataTrackKey(KEY_ALBUM_PEAK, String.format("%,.6f", tag.getAlbumTruePeak()));
        } */

        /*
        tags = tags + METADATA_KEY+" "+KEY_TRACK_GAIN+"=\""+in()+"\" ";
        tags = tags + METADATA_KEY+" "+KEY_TRACK_RANGE+"=\""+tag.getTrackRange()+"\" ";
        tags = tags + METADATA_KEY+" "+KEY_REFERENCE_LOUDNESS+"=\""+tag.getTrackLoudness()+"\" ";
        tags = tags + METADATA_KEY+" "+KEY_REFERENCE_TRUEPEAK+"=\""+tag.getTrackTruePeek()+"\" "; */
        return tags;
    }

    public static void detectMQA(MusicTag tag, long millis) {
        final Object lock = new Object();
        new Thread(() -> {
            detectMQA(tag);
            synchronized (lock) {
                lock.notify();
            }
        }).start();
        synchronized (lock) {
            try {
                // Wait for specific millis and release the lock.
                // If blockingMethod is done during waiting time, it will wake
                // me up and give me the lock, and I will finish directly.
                // Otherwise, when the waiting time is over and the
                // blockingMethod is still
                // running, I will reacquire the lock and finish.
                lock.wait(millis);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void detectMQA(MusicTag tag) {
        if(!MusicTagUtils.isFLACFile(tag)) return; // scan only flac
        if(tag.isMqaScanned()) return; //prevent re scan
        try {
            NativeLib lib = new NativeLib();
            String mqaInfo = StringUtils.trimToEmpty(lib.getMQAInfo(tag.getPath()));
            // MQA Studio|96000
            // MQA|96000
            if(!isEmpty(mqaInfo) && mqaInfo.contains("|")) {
                String[] tags = mqaInfo.split("\\|");
                tag.setMqaInd(trimToEmpty(tags[0]));
                tag.setMqaSampleRate(toLong(tags[1]));
                tag.setMqaScanned(true);
            }else {
                tag.setMqaInd("None");
                tag.setMqaScanned(true);
            }
        }catch (Exception ex) {
            tag.setMqaInd("None");
            tag.setMqaScanned(true);
            Log.e(TAG, "detectMQA", ex);
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

    private static void readFileInfo(Context context, MusicTag tag) {
        File file = new File(tag.getPath());
        tag.setFileLastModified(file.lastModified());
        tag.setFileSize(file.length());
        tag.setSimpleName(DocumentFileCompat.getBasePath(context, tag.getPath()));
        tag.setStorageId(DocumentFileCompat.getStorageId(context, tag.getPath()));
    }

    private static boolean isLossless(MusicTag tag) {
        if(Constants.MEDIA_ENC_FLAC.equalsIgnoreCase(tag.getFileFormat())) return true;
        if(Constants.MEDIA_ENC_ALAC.equalsIgnoreCase(tag.getFileFormat())) return true;
        if(Constants.MEDIA_ENC_AIFF.equalsIgnoreCase(tag.getFileFormat())) return true;
        return Constants.MEDIA_ENC_WAVE.equalsIgnoreCase(tag.getFileFormat());
    }

    private static void parseFormatInfo(MusicTag tag) {
        String FORMAT_START = "[FORMAT]";
        String FORMAT_END = "[/FORMAT]";
        int start = tag.getData().indexOf(FORMAT_START);
        int end = tag.getData().indexOf(FORMAT_END,start);
        if(start >0 && end >0 ) {
            String info = trimToEmpty(tag.getData().substring(start+FORMAT_START.length(), end));
            String []lines = info.split("\n");
            Map<String, String> tags = new HashMap<>();
            for (String line: lines) {
                if(!isEmpty(line) && line.contains("=")) {
                    String []vals = line.split("=");
                    tags.put(toUpperCase(vals[0]), getField(vals, 1)); // all upper case
                    //tags.put(StringUtils.toLowwerCase(vals[0]), trimToEmpty(vals[1])); // all lower case
                    tags.put(vals[0], getField(vals, 1)); // as is from file
                }
            }

            // media info
            /*tag.setFileFormat(tags.get(KEY_FORMAT_NAME)); // format_name
            if(tag.getFileFormat().contains(",")) {
                // found mov,mp4,m4a,...
                // use information from encoding
                if(!isEmpty(tag.getAudioEncoding())) {
                    tag.setFileFormat(getWord(tag.getAudioEncoding()," ",0));
                }
            } */
            tag.setAudioStartTime(toDouble(tags.get(KEY_START_TIME))); // start_time
            tag.setAudioDuration(toDouble(tags.get(KEY_DURATION))); // duration
            tag.setAudioBitRate(toLong(tags.get(KEY_BIT_RATE))); // bit_rate

            // KEY_TAG_ALBUM
            tag.setAlbum(getTagforKey(tags, KEY_TAG_ALBUM));

            //KEY_TAG_ALBUM_ARTIST
            tag.setAlbumArtist(getTagforKey(tags, KEY_TAG_ALBUM_ARTIST));

            // KEY_TAG_ARTIST
            tag.setArtist(getTagforKey(tags, KEY_TAG_ARTIST));

            //KEY_TAG_COMPOSER
            tag.setComposer(getTagforKey(tags, KEY_TAG_COMPOSER));

            //KEY_TAG_COMPILATION
            tag.setCompilation(toBoolean(getTagforKey(tags, KEY_TAG_COMPILATION)));

            // YEAR
            tag.setYear(getTagforKey(tags, KEY_TAG_YEAR));

            //KEY_TAG_COMMENT
            tag.setComment(getTagforKey(tags, KEY_TAG_COMMENT));

            //KEY_TAG_DISCNUMBER
            tag.setDisc(getTagforKey(tags, KEY_TAG_DISC));

            //KEY_TAG_GENRE
            tag.setGenre(getTagforKey(tags, KEY_TAG_GENRE));

            //KEY_TAG_GROUPING
            tag.setGrouping(getTagforKey(tags, KEY_TAG_GROUPING));

            //KEY_TAG_TRACK
            tag.setTrack(getTagforKey(tags, KEY_TAG_TRACK));

            //KEY_TAG_MEDIA
            tag.setMediaType(getTagforKey(tags, KEY_TAG_MEDIA));

            //KEY_TAG_RATING
            tag.setRating(toInt(getTagforKey(tags, KEY_TAG_RATING)));

            //KEY_TAG_QUALITY
            tag.setMediaQuality(getTagforKey(tags, KEY_TAG_QUALITY));

            //KEY_TAG_TITLE
            tag.setTitle(getTagforKey(tags, KEY_TAG_TITLE));

            // KEY_TRACK_GAIN
            tag.setTrackRG(toDouble(getTagforKey(tags, KEY_TRACK_GAIN)));

            //KEY_TRACK_PEAK
            tag.setTrackTruePeak(toDouble(getTagforKey(tags, KEY_TRACK_PEAK)));

            // KEY_ALBUM_GAIN
           // tag.setAlbumRG(toDouble(getTagforKey(tags, KEY_ALBUM_GAIN)));

            //KEY_ALBUM_PEAK
           // tag.setAlbumTruePeak(toDouble(getTagforKey(tags, KEY_ALBUM_PEAK)));

            // publisher
            tag.setPublisher(getTagforKey(tags, KEY_TAG_PUBLISHER));

            // MQA from tag and encoder
            String encoder = getTagforKey(tags, KEY_TAG_ENCODER);
            String mqaEncoder = getTagforKey(tags, KEY_TAG_MQA_ENCODER);
            if(!MusicTagUtils.isFLACFile(tag)) {
                tag.setMqaInd("None");
            }else if(encoder.contains(KEY_MQA) || !isEmpty(mqaEncoder)) {
                tag.setMqaInd("MQA");
                tag.setMqaSampleRate(toLong(getTagforKey(tags, KEY_TAG_MQA_ORIGINAL_SAMPLERATE)));
                tag.setMqaScanned(false);
            }

            // read Quick time Specific tags
            if(isMp4File(tag)) {
                tag.setTitle(getTagforKey(tags, KEY_TAG_MP4_TITLE));
                tag.setAlbumArtist(getTagforKey(tags, KEY_TAG_MP4_ALBUM_ARTIST));
                tag.setComposer(getTagforKey(tags, KEY_TAG_MP4_COMPOSER));
               // tag.setCompilation(toBoolean(getTagforKey(tags, KEY_TAG_MP4_COMPILATION)));
               // tag.setMediaType(getTagforKey(tags, KEY_TAG_MP4_MEDIA));
               // tag.setMediaQuality(getTagforKey(tags, KEY_TAG_MP4_QUALITY));
                tag.setPublisher(getTagforKey(tags, KEY_TAG_MP4_PUBLISHER));
                tag.setYear(getTagforKey(tags, KEY_TAG_MP4_YEAR));
            }

            // read Wave specific tags
            if(MusicTagUtils.isWavFile(tag)) {
                tag.setMediaType(getTagforKey(tags, KEY_TAG_WAVE_MEDIA));
                tag.setPublisher(getTagforKey(tags, KEY_TAG_WAVE_PUBLISHER));
                tag.setYear(getTagforKey(tags, KEY_TAG_WAVE_YEAR));
                parseMMComment(tag);
            }

        }
    }

    private static void parseMMComment(MusicTag tag) {
        String comment = StringUtils.trimToEmpty(tag.getComment());
        /*
         StringUtils.trimToEmpty(musicTag.getDisc())+
                "#"+StringUtils.trimToEmpty(musicTag.getGrouping())+
                "#"+StringUtils.trimToEmpty(musicTag.getMediaQuality())+
                "#"+musicTag.getRating()+
                "#"+StringUtils.trimToEmpty(musicTag.getAlbumArtist())+
                "#"+StringUtils.trimToEmpty(musicTag.getComposer())+
                "#"+musicTag.getTrackDR()+
                "#"+musicTag.getMeasuredDR()+
                "#"+musicTag.getMeasuredSamplingRate()+
         */
        int start = comment.indexOf("<##>");
        int end = comment.indexOf("</##>");
        if(start >= 0 && end > start) {
            // found metadata comment
            String metadata = comment.substring(start+4, end);
            if(comment.length()>(end+5)) {
                comment = comment.substring(end+5);
            }else {
                comment = "";
            }
            String []text = metadata.split("#",-1);

            tag.setDisc(getField(text, 0));
            tag.setGrouping(getField(text, 1));
            tag.setMediaQuality(getField(text,2));
            tag.setRating(toInt(getField(text, 3)));
            tag.setAlbumArtist(getField(text, 4));
            tag.setComposer(getField(text, 5));
            tag.setTrackDR(toDouble(getField(text, 6)));
            tag.setMeasuredDR(toDouble(getField(text, 7)));
            tag.setMeasuredSamplingRate(toLong(getField(text, 8)));
        }

        tag.setComment(StringUtils.trimToEmpty(comment));
    }

    private static String getTagforKey(Map<String, String> tags, String key1) {
        if(tags.containsKey(KEY_TAG+key1.toUpperCase())) {
            return trimToEmpty(tags.get(KEY_TAG+key1.toUpperCase()));
        //}else if (tags.containsKey(KEY_TAG+key1.toLowerCase())) {
        //    return trimToEmpty(tags.get(KEY_TAG+key1.toLowerCase()));
        //if(tags.containsKey(KEY_TAG+key1)) {
        //    return trimToEmpty(tags.get(KEY_TAG+key1));
        }
        return "";
    }

    private static String getValueForKey(Map<String, String> tags, String key) {
        if(tags.containsKey(toUpperCase(key))) {
            return trimToEmpty(tags.get(toUpperCase(key)));
        }
        return "";
    }

    private static void parseStreamInfo(MusicTag tag, String output) {
        // need to parse from whole output to support ALAC/AAC on M4A container
        // Stream #0:0: Audio:
        // Stream #0:0[0x1](eng): Audio:
        // Stream #0:1[0x0]: Video:
        Pattern audioPattern = Pattern.compile("(?m)^\\s*Stream.*?(?:Audio):\\s*([\\s\\S]*?)(?=^\\s*Metadata:|\\Z)");
        Pattern videoPattern = Pattern.compile("(?m)^\\s*Stream.*?(?:Video):\\s*([\\s\\S]*?)(?=^\\s*Metadata:|\\Z)");
        Matcher matcher = audioPattern.matcher(output) ;//tag.getData());
        if (matcher.find()) {
            String info = matcher.group(1);

            String [] tags = info.split(",", -1);
            tag.setAudioEncoding(getField(tags, 0));
            tag.setAudioSampleRate(parseSampleRate(tags, 1));
            tag.setAudioChannels(parseChannels(tags, 2));
            tag.setAudioBitsDepth(parseBitDepth(tags, 3));
        }

        matcher = videoPattern.matcher(output); //tag.getData());
        if (matcher.find()) {
            String info = matcher.group(1);

           // String [] tags = info.split(",");
            tag.setEmbedCoverArt(getWord(info,",",0));
        }else {
            tag.setEmbedCoverArt("");
        }
    }

    private static int parseBitDepth(String[] tags, int i) {
        if(tags.length>i) {
            if (tags[0].contains("dsd")) {
                return 1; // dsd
            } else if (tags[i].contains("s24")) {
                return 24;
            } else if (tags[i].contains("s32")) {
                if (tags[i].contains("24")) {
                    return 24;
                }
                return 32;
            } else if (tags[i].contains("s16")) {
                return 16;
            }
        }
        return 0;
    }

    private static String parseChannels(String[] tags, int i) {
        if(tags.length>i) {
            String text = trimToEmpty(tags[i]);
            if("stereo".equalsIgnoreCase(text)) {
                return "2";
            }
            if("2 channels".equalsIgnoreCase(text)) {
                return "2";
            }
            return text;
        }
        return "";
    }

    private static long parseSampleRate(String[] tags, int i) {
        if(tags.length>i && tags[i].endsWith("Hz")) {
            String txt = trimToEmpty(tags[i].replace("Hz", ""));
            return toLong(txt);
        }
        return 0L;
    }

    private static String getField(String[] tags, int i) {
        if(tags.length>i) {
            return trimToEmpty(tags[i]);
        }
        return "";
    }

    public static void calculateSNR () {
    /*
    Dynamic range refers to the difference between the loudest and quietest parts of a signal, measured in dB (decibels).

    Maximum dynamic range in decibels = number of bits x 6
    8 x 6 = 48dB of dynamic range – less than the dynamic range of analog tape recorders.

16 x 6 = 96dB of dynamic range – ample room for recording.

24 x 6 = 144 dB of dynamic range – high resolution dynamic range.

The definition of signal-to-noise ratio (SNR) is the difference in level between the maximum input your gear can handle before noise creeps into the signal.

     */

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
                options = options + " -y -vn -c:a flac -compression_level " + cLevel; //alac
            }else {
                options = options + " -y -vn -c:a flac -compression_level 0 ";
            }
       // }else if (targetPath.toLowerCase().endsWith(".mp3")) {
            // convert to 320k bitrate
      //      options = " -ar 44100 -ab 320k ";
       // }else if (srcPath.toLowerCase().endsWith(".dsf")){
            // convert from dsf to 24 bits, 48 kHz
            // use lowpass filter to eliminate distortion in the upper frequencies.
       //     options = " -af \"lowpass=24000, volume=6dB\" -sample_fmt s32 -ar 48000 ";
        }else if (targetPath.toLowerCase().endsWith(".m4a")) {
            if(bitDept > 16) {
                options = " -sample_fmt s32 -y -acodec pcm_s"+bitDept+"be "; //aif
               // options = " -sample_fmt s32 -y -acodec alac "; //alac
                //options = " -y -vn -c:a alac "; //alac
            }else {
               // options = " -y -acodec alac "; //alac
                options = " -y -vn -c:a alac "; //alac
            }
        }

        Log.i(TAG, "Converting: "+ srcPath);

        String ext = FileUtils.getExtension(srcPath);
        String targetExt = FileUtils.getExtension(targetPath).toLowerCase(Locale.US);
        File dir = context.getExternalCacheDir();
        String tmpPath = "/tmp/"+ DigestUtils.md5Hex(srcPath)+"."+ext;
        dir = new File(dir, tmpPath);
        if(!dir.getParentFile().exists()) {
            dir.getParentFile().mkdirs();
        }
        tmpPath = dir.getAbsolutePath();
        FileSystem.copy(context, srcPath, tmpPath);

        String tmpTarget = tmpPath.replace("."+ext, "_NEWFMT."+targetExt);

       // targetPath = escapePathForFFMPEG(targetPath);

       // String cmd = " -hide_banner -nostats -i "+escapeFileName(srcPath)+" "+options+" \""+targetPath+"\"";
        String cmd = " -hide_banner -nostats -i "+tmpPath+" "+options+" \""+tmpTarget+"\"";
        Log.i(TAG, "Converting with cmd: "+ cmd);

       // FFmpegKit.executeAsync(cmd, session -> callbak.onFinish(ReturnCode.isSuccess(session.getReturnCode())));
       try {
           FFmpegSession session = FFmpegKit.execute(cmd);

           if (!ReturnCode.isCancel(session.getReturnCode())) {
               FileSystem.move(context, tmpTarget, targetPath);
               return true;
           }
       }finally {
           FileSystem.delete(context, tmpPath);
       }
       return false;
    }

    private static String escapeFileName(String srcPath) {
        srcPath = srcPath.replace("'", "\\'");
        return "'"+srcPath+"'";
    }

    private static String getFFmpegOutputData(FFmpegSession session) {
        List<com.arthenica.ffmpegkit.Log> logs = session.getLogs();
        StringBuilder buff = new StringBuilder();
        String keyword = "Output #0,";
        String keyword2 = "[Parsed_";
        boolean foundTag = false;
        for (com.arthenica.ffmpegkit.Log log : logs) {
            String msg =  log.getMessage();
            String trimedMsg = trim(msg,"");
            if (trimedMsg.startsWith(keyword)) {
                foundTag = true;
            }else if (trimedMsg.startsWith(keyword2)) {
                foundTag = true;
            }
           /* if (foundTag && msg.startsWith(keyword2)) {
                foundTag = false;
            } */
            if ((!foundTag)) continue;
            buff.append(msg);
        }

        return buff.toString();
    }

    private static String getFFmpegOutputData2(FFmpegSession session) {
        List<com.arthenica.ffmpegkit.Log> logs = session.getLogs();
        StringBuilder buff = new StringBuilder();
        String keyword = "Integrated loudness:";
        String keyword2 = "-70.0 LUFS";
        boolean foundTag = false;
        for (com.arthenica.ffmpegkit.Log log : logs) {
            String msg = trimToEmpty(log.getMessage());
            if (!foundTag) { // finding start keyword
                if (msg.contains(keyword) && !msg.contains(keyword2)) {
                    foundTag = true;
                }
            }
            if (!foundTag) continue;
            buff.append(msg);
        }

        return buff.toString();
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

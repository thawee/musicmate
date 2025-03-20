package apincer.android.mmate.codec;

import static apincer.android.mmate.codec.FFMpegHelper.KEY_BIT_RATE;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_DURATION;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_START_TIME;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_ALBUM;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_ALBUM_ARTIST;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_ARTIST;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_COMMENT;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_COMPILATION;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_COMPOSER;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_DISC;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_GENRE;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_GROUPING;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_MP4_ALBUM_ARTIST;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_MP4_COMPOSER;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_MP4_PUBLISHER;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_MP4_TITLE;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_MP4_YEAR;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_TITLE;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_TRACK;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_WAVE_ALBUM_ARTIST;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_WAVE_COMPOSER;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_WAVE_DISC;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_WAVE_GROUP;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_WAVE_MEDIA;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_WAVE_PUBLISHER;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_WAVE_QUALITY;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_WAVE_YEAR;
import static apincer.android.mmate.codec.FFMpegHelper.KEY_TAG_YEAR;
import static apincer.android.mmate.utils.MusicTagUtils.isMp4File;
import static apincer.android.mmate.utils.StringUtils.getWord;
import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.toBoolean;
import static apincer.android.mmate.utils.StringUtils.toDouble;
import static apincer.android.mmate.utils.StringUtils.toDurationSeconds;
import static apincer.android.mmate.utils.StringUtils.toLong;
import static apincer.android.mmate.utils.StringUtils.toUpperCase;
import static apincer.android.mmate.utils.StringUtils.trim;
import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import android.content.Context;
import android.util.Log;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.FFprobeSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import apincer.android.mmate.Constants;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.LogHelper;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.utils.FileUtils;

public class FFMPegReader extends TagReader {
    private static final String TAG = "FFMPegReader";
    private final Context context;

    public FFMPegReader(Context context) {
        this.context = context;
    }


public static class Loudness {
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

    protected List<MusicTag> read(String path) {
        Log.d(TAG, "read: "+path);
        MusicTag tag = extractTagFromFile(path);
        return List.of(tag);
    }

    protected List<MusicTag> readFully(String path) {
        Log.d(TAG, "readFully: "+path);
        MusicTag tag = extractTagFromFile(path);
        //detectMQA(tag,50000); // timeout 50 seconds
        return List.of(tag);
    }

    public MusicTag extractTagFromFile(String path) {

        String cmd ="-hide_banner -nostats -i \""+path+"\" -f null -";
        LogHelper.setFFMpegOn();
        FFmpegSession session = FFmpegKit.execute(cmd);
        if (ReturnCode.isSuccess(session.getReturnCode())) {
            LogHelper.setFFMpegOff();
            String data = getFFmpegOutputData(session);
            String output = session.getOutput();
            MusicTag tag = new MusicTag();
            tag.setData("\nRunning Time:"+session.getDuration()+"\n"+data);
            tag.setPath(path);
            readFileInfo(context,tag);
            parseStreamInfo(tag,output);
            detectFileFormat(tag);
            parseDurationInfo(tag, output);

            parseTagsInfo(tag);
            return tag;
        }else {
            LogHelper.setFFMpegOff();
            // try to get from file name
            MusicTag tag = new MusicTag();
            String data = getFFmpegOutputData(session);
            String output = session.getOutput();
            tag.setData("Running Time (error):"+session.getDuration()+"\n"+data);
            tag.setPath(path);
            readFileInfo(context,tag);
            parseStreamInfo(tag,output);
            detectFileFormat(tag);
            parseDurationInfo(tag,output);
            parseTagsInfo(tag);
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
       // tag.setRating(toInt(getValueForKey(tags, KEY_TAG_RATING)));

        //KEY_TAG_QUALITY
        tag.setMediaQuality(getValueForKey(tags, KEY_TAG_QUALITY));

        //KEY_TAG_TITLE
        tag.setTitle(getValueForKey(tags, KEY_TAG_TITLE));
        if(isEmpty(tag.getTitle())) {
            tag.setTitle(FileUtils.getFileName(tag.getPath()));
        }

        // KEY_TRACK_GAIN
       // tag.setTrackRG(toDouble(getValueForKey(tags, KEY_MM_TRACK_GAIN)));

        //KEY_TRACK_PEAK
       // tag.setTrackTruePeak(toDouble(getValueForKey(tags, KEY_MM_TRACK_PEAK)));

        // KEY_ALBUM_GAIN
       // tag.setAlbumRG(toDouble(getValueForKey(tags, KEY_ALBUM_GAIN)));

        //KEY_ALBUM_PEAK
       // tag.setAlbumTruePeak(toDouble(getValueForKey(tags, KEY_ALBUM_PEAK)));

        // publisher
        tag.setPublisher(getValueForKey(tags, KEY_TAG_PUBLISHER));

        // replay gain
        // tag.setTrackLoudness(toDouble(getValueForKey(tags, KEY_TRACK_LOUDNESS)));
       /* tag.setTrackRG(gainToDouble(getValueForKey(tags, KEY_TAG_TRACK_GAIN)));
        tag.setTrackTP(toDouble(getValueForKey(tags, KEY_TAG_TRACK_PEAK)));
        tag.setDynamicRange(toDouble(getValueForKey(tags, KEY_MM_TRACK_DR)));
        tag.setDynamicRangeScore(toDouble(getValueForKey(tags, KEY_MM_TRACK_DR_SCORE)));
        tag.setUpscaledScore(toDouble(getValueForKey(tags, KEY_MM_TRACK_UPSCALED)));
        tag.setResampledScore(toDouble(getValueForKey(tags, KEY_MM_TRACK_RESAMPLED)));
*/

        // read Quick time Specific tags
        if(isMp4File(tag)) {
            tag.setTitle(getValueForKey(tags, KEY_TAG_MP4_TITLE));
            tag.setAlbumArtist(getValueForKey(tags, KEY_TAG_MP4_ALBUM_ARTIST));
            tag.setComposer(getValueForKey(tags, KEY_TAG_MP4_COMPOSER));
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

            parseDurationInfo(tag, session.getOutput());
            parseTagsInfo(tag);
            // parseReplayGain(tag);
            parseOverallDRMeter(tag, data);
            detectFileFormat(tag);
            return tag;
        }else {
            // try to get from file name
            MusicTag tag = new MusicTag();
            tag.setData(session.getOutput());
            tag.setPath(path);
            detectFileFormat(tag);
            session.cancel();
            Log.d(TAG, session.getOutput());
            return tag;
        }
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
                    tags.put(toUpperCase(vals[0]), extractField(vals, 1)); // all upper case
                    //tags.put(StringUtils.toLowwerCase(vals[0]), trimToEmpty(vals[1])); // all lower case
                    tags.put(vals[0], extractField(vals, 1)); // as is from file
                }
            }

            // media info
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
          //  tag.setRating(toInt(getTagforKey(tags, KEY_TAG_RATING)));

            //KEY_TAG_QUALITY
            tag.setMediaQuality(getTagforKey(tags, KEY_TAG_QUALITY));

            //KEY_TAG_TITLE
            tag.setTitle(getTagforKey(tags, KEY_TAG_TITLE));

            // KEY_TRACK_GAIN
            //tag.setTrackRG(toDouble(getTagforKey(tags, KEY_TAG_TRACK_GAIN)));

            //KEY_TRACK_PEAK
            //tag.setTrackTP(toDouble(getTagforKey(tags, KEY_TAG_TRACK_PEAK)));

            // KEY_ALBUM_GAIN
           // tag.setAlbumRG(toDouble(getTagforKey(tags, KEY_ALBUM_GAIN)));

            //KEY_ALBUM_PEAK
           // tag.setAlbumTruePeak(toDouble(getTagforKey(tags, KEY_ALBUM_PEAK)));

            // publisher
            tag.setPublisher(getTagforKey(tags, KEY_TAG_PUBLISHER));

            // MQA from tag and encoder

            // read Quick time Specific tags
            if(isMp4File(tag)) {
                tag.setTitle(getTagforKey(tags, KEY_TAG_MP4_TITLE));
                tag.setAlbumArtist(getTagforKey(tags, KEY_TAG_MP4_ALBUM_ARTIST));
                tag.setComposer(getTagforKey(tags, KEY_TAG_MP4_COMPOSER));
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

            tag.setDisc(extractField(text, 0));
            tag.setGrouping(extractField(text, 1));
            tag.setMediaQuality(extractField(text,2));
         //   tag.setRating(toInt(extractField(text, 3)));
            tag.setAlbumArtist(extractField(text, 4));
            tag.setComposer(extractField(text, 5));
            tag.setDynamicRangeScore(toDouble(extractField(text, 6)));
            tag.setDynamicRange(toDouble(extractField(text, 7)));
          //  tag.setMeasuredSamplingRate(toLong(extractField(text, 8)));
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
            tag.setAudioEncoding(extractField(tags, 0));
            tag.setAudioSampleRate(parseSampleRate(tags));
            tag.setAudioChannels(parseChannels(tags));
            tag.setAudioBitsDepth(parseBitDepth(tags));
        }

        matcher = videoPattern.matcher(output); //tag.getData());
        if (matcher.find()) {
            String info = matcher.group(1);

           // String [] tags = info.split(",");
            tag.setCoverartMime(getWord(info,",",0));
        }else {
            tag.setCoverartMime("");
        }
    }

    private static int parseBitDepth(String[] tags) {
        int bitDepthIndex = 3;
        if(tags.length>bitDepthIndex) {
            if (tags[0].contains("dsd")) {
                return 1; // dsd
            } else if (tags[bitDepthIndex].contains("s24")) {
                return 24;
            } else if (tags[bitDepthIndex].contains("s32")) {
                if (tags[bitDepthIndex].contains("24")) {
                    return 24;
                }
                return 32;
            } else if (tags[bitDepthIndex].contains("s16")) {
                return 16;
            }
        }
        return 0;
    }

    private static String parseChannels(String[] tags) {
        int chIndex = 2;
        if(tags.length>chIndex) {
            String text = trimToEmpty(tags[chIndex]);
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

    private static long parseSampleRate(String[] tags) {
        int smIndex = 1;
        if(tags.length>smIndex && tags[smIndex].endsWith("Hz")) {
            String txt = trimToEmpty(tags[smIndex].replace("Hz", ""));
            return toLong(txt);
        }
        return 0L;
    }

 /*
    public static void calculateSNR () {
    Dynamic range refers to the difference between the loudest and quietest parts of a signal, measured in dB (decibels).

    Maximum dynamic range in decibels = number of bits x 6
    8 x 6 = 48dB of dynamic range – less than the dynamic range of analog tape recorders.

16 x 6 = 96dB of dynamic range – ample room for recording.

24 x 6 = 144 dB of dynamic range – high resolution dynamic range.

The definition of signal-to-noise ratio (SNR) is the difference in level between the maximum input your gear can handle before noise creeps into the signal.
    } */

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
            if ((!foundTag)) continue;
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

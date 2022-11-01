package apincer.android.mmate.ffmpeg;

import static java.lang.StrictMath.log10;
import static java.lang.StrictMath.min;
import static apincer.android.mmate.utils.MusicTagUtils.getFileSizeRatio;
import static apincer.android.mmate.utils.StringUtils.getWord;
import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.toBoolean;
import static apincer.android.mmate.utils.StringUtils.toDouble;
import static apincer.android.mmate.utils.StringUtils.toInt;
import static apincer.android.mmate.utils.StringUtils.toLong;
import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import android.content.Context;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.FFprobeSession;
import com.arthenica.ffmpegkit.Log;
import com.arthenica.ffmpegkit.ReturnCode;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import apincer.android.mmate.fs.FileSystem;
import apincer.android.mmate.fs.MusicFileProvider;
import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mqaidentifier.NativeLib;
import apincer.android.utils.FileUtils;
import timber.log.Timber;

public class FFMPegUtils {
    private static final String KEY_BIT_RATE = "bit_rate";
    private static final String KEY_FORMAT_NAME = "format_name";
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
    private static final String KEY_TAG_DISCNUMBER = "DISCNUMBER";
    private static final String KEY_TAG_GENRE = "GENRE";
    private static final String KEY_TAG_GROUPING = "GROUPING";
    private static final String KEY_TAG_TRACK = "track";
    private static final String KEY_TAG_PUBLISHER = "PUBLISHER";
    private static final String KEY_TAG_LANGUAGE = "LANGUAGE";
    private static final String KEY_TAG_ENCODER = "ENCODER";
    private static final String KEY_TAG_MQAORIGINALSAMPLERATE = "ORIGINALSAMPLERATE";
    private static final String KEY_TAG_MEDIA = "MEDIA";
    private static final String KEY_TAG_RATING = "RATING";
    private static final String KEY_TAG_QUALITY = "QUALITY";
    private static final String KEY_TAG_TITLE = "TITLE";
    // ICRD = Date
    private static final String KEY_TAG_WAVE_ARTIST = "IART"; //artist
    private static final String KEY_TAG_WAVE_ALBUM = "IPRD"; // album
    private static final String KEY_TAG_WAVE_COMMENT = "ICMT"; // comment
    private static final String KEY_TAG_WAVE_GENRE = "IGNR"; //genre
    private static final String KEY_TAG_WAVE_TRACK = "IPRT"; //track
    private static final String KEY_TAG_WAVE_PUBLISHER = "ICOP"; //copy right
    private static final String KEY_TAG_WAVE_LANGUAGE = "ILNG"; // language
    private static final String KEY_TAG_WAVE_TITLE = "INAM"; //title
    private static final String KEY_TRACK_GAIN = "REPLAYGAIN_TRACK_GAIN";
    private static final String KEY_TRACK_RANGE = "REPLAYGAIN_TRACK_RANGE";
    private static final String KEY_REFERENCE_LOUDNESS = "REPLAYGAIN_REFERENCE_LOUDNESS";
    private static final String KEY_REFERENCE_TRUEPEAK = "REPLAYGAIN_REFERENCE_TRUEPEAK"; // added by thawee
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

    public static Loudness getLoudness(String path) {
        try {
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
            //String cmd = "-i \""+tag.getPath()+"\" -af ebur128= -f null -";
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
    }

    public static MusicTag readMusicTag(String path) {
       // String cmd ="-hide_banner -of default=noprint_wrappers=0 -show_format -print_format json \""+path+"\"";
        String cmd ="-hide_banner -of default=noprint_wrappers=0 -show_format \""+path+"\"";

        FFprobeSession session = FFprobeKit.execute(cmd);
        if (ReturnCode.isSuccess(session.getReturnCode())) {
            String data = session.getOutput();
            MusicTag tag = new MusicTag();
            tag.setData(data);
            tag.setPath(path);
            readFileInfo(tag);
            parseStreamInfo(tag);
            parseFormatInfo(tag);
            tag.setLossless(isLossless(tag));
            tag.setFileSizeRatio(getFileSizeRatio(tag));
            return tag;
        }else {
            // try to get from file name
            MusicTag tag = new MusicTag();
            tag.setData(session.getOutput());
            tag.setPath(path);
            session.cancel();
            Timber.d(session.getOutput());
            return tag;
        }
    }

    public static boolean writeTrackGain(Context context, MusicTag tag) {
        if(MusicTagUtils.isWavFile(tag)) return false; // wave file not support track gain
        /// check free space on storage
        // ffmpeg write to new tmp file
        // ffmpeg -i aiff.aiff -map 0 -y -codec copy -write_id3v2 1 -metadata "artist-sort=emon feat sort" aiffout.aiff
        // ffmpeg -hide_banner -i aiff.aiff -map 0 -y -codec copy -metadata "artist-sort=emon feat sort" aiffout.aiff
        String srcPath = tag.getPath();
        File dir = context.getExternalCacheDir();
        //File dir = FileSystem.getDownloadPath(context, "tmp");
        String targetPath = "/tmp/"+ DigestUtils.md5Hex(srcPath)+"."+tag.getFileFormat();
        dir = new File(dir, targetPath);
        if(!dir.getParentFile().exists()) {
            dir.getParentFile().mkdirs();
        }
        targetPath = dir.getAbsolutePath();
        String metadataKeys = getMetadataTrackGainKeys(tag);

        String cmd = " -hide_banner -nostats -i \"" + srcPath + "\" -map 0 -y -codec copy "+metadataKeys+ "\""+targetPath+"\"";
        //ffmpeg -nostats -i ~/Desktop/input.wav -filter_complex ebur128=peak=true -f null -
        // String cmd = "-i \""+path+"\" -af loudnorm=I=-16:TP=-1.5:LRA=11:print_format=json -f null -";
        FFmpegSession session = FFmpegKit.execute(cmd);
        if(ReturnCode.isSuccess(session.getReturnCode())) {
            // success
            // move file
            FileSystem.moveFile(context, targetPath, srcPath);
            return true;
        }else {
            // fail, delete tmp file;
            File tmp = new File(targetPath);
            if(tmp.exists()) {
                FileSystem.delete(context, tmp);
            }
            return false;
        }
    }


    public static boolean writeTrackFields(Context context, MusicTag tag) {
        /// check free space on storage
        // ffmpeg write to new tmp file
        // ffmpeg -i aiff.aiff -map 0 -y -codec copy -write_id3v2 1 -metadata "artist-sort=emon feat sort" aiffout.aiff
        // ffmpeg -hide_banner -i aiff.aiff -map 0 -y -codec copy -metadata "artist-sort=emon feat sort" aiffout.aiff
        String srcPath = tag.getPath();
        File dir = context.getExternalCacheDir();
        //File dir = FileSystem.getDownloadPath(context, "tmp");
        String targetPath = "/tmp/"+ DigestUtils.md5Hex(srcPath)+"."+tag.getFileFormat();
        dir = new File(dir, targetPath);
        if(!dir.getParentFile().exists()) {
            dir.getParentFile().mkdirs();
        }
        targetPath = dir.getAbsolutePath();
        String metadataKeys = getMetadataTrackKeys(tag.getOriginTag(), tag);
        if(isEmpty(metadataKeys)) return false; // co change to write to change

        String cmd = " -hide_banner -nostats -i \"" + srcPath + "\" -map 0 -y -codec copy "+metadataKeys+ "\""+targetPath+"\"";
        //ffmpeg -nostats -i ~/Desktop/input.wav -filter_complex ebur128=peak=true -f null -
        // String cmd = "-i \""+path+"\" -af loudnorm=I=-16:TP=-1.5:LRA=11:print_format=json -f null -";
        FFmpegSession session = FFmpegKit.execute(cmd);
        if(ReturnCode.isSuccess(session.getReturnCode())) {
            // success
            // move file
            // FIXME : for test
            FileSystem.delete(context, new File(srcPath+"_TAGS."+tag.getFileFormat()));
            FileSystem.moveFile(context, targetPath, srcPath+"_TAGS."+tag.getFileFormat());
           // FileSystem.moveFile(context, targetPath, srcPath);
            FileRepository.newInstance(context).scanMusicFile(new File(srcPath+"_TAGS."+tag.getFileFormat()));
            return true;
        }else {
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
        if(MusicTagUtils.isWavFile(tag)) {
            return getMetadataTrackKeysForWave( origin,  tag);
        }
        String tags = "";
        if(isFieldChanged(origin.getTitle(), tag.getTitle())) {
            tags = tags + METADATA_KEY + " " + KEY_TAG_TITLE + "=\"" + tag.getTitle() + "\" ";
        }
        if(isFieldChanged(origin.getAlbum(), tag.getAlbum())) {
            tags = tags + METADATA_KEY + " " + KEY_TAG_ALBUM + "=\"" + tag.getAlbum() + "\" ";
        }
        if(isFieldChanged(origin.getArtist(), tag.getArtist())) {
            tags = tags + METADATA_KEY + " " + KEY_TAG_ARTIST + "=\"" + tag.getArtist() + "\" ";
        }
        if(isFieldChanged(origin.getAlbumArtist(), tag.getAlbumArtist())) {
        tags = tags + METADATA_KEY+" "+KEY_TAG_ALBUM_ARTIST+"=\""+tag.getAlbumArtist()+"\" ";
        }
        if(isFieldChanged(origin.getComposer(), tag.getComposer())) {
        tags = tags + METADATA_KEY+" "+KEY_TAG_COMPOSER+"=\""+tag.getComposer()+"\" ";
        }
        if(isFieldChanged(origin.isCompilation(), tag.isCompilation())) {
        tags = tags + METADATA_KEY+" "+KEY_TAG_COMPILATION+"=\""+(tag.isCompilation()?1:0)+"\" ";
        }
        if(isFieldChanged(origin.getComment(), tag.getComment())) {
        tags = tags + METADATA_KEY+" "+KEY_TAG_COMMENT+"=\""+tag.getComment()+"\" ";
        }
        if(isFieldChanged(origin.getDisc(), tag.getDisc())) {
        tags = tags + METADATA_KEY+" "+KEY_TAG_DISCNUMBER+"=\""+tag.getDisc()+"\" ";
        }
        if(isFieldChanged(origin.getGenre(), tag.getGenre())) {
        tags = tags + METADATA_KEY+" "+KEY_TAG_GENRE+"=\""+tag.getGenre()+"\" ";
        }
        if(isFieldChanged(origin.getGrouping(), tag.getGrouping())) {
        tags = tags + METADATA_KEY+" "+KEY_TAG_GROUPING+"=\""+tag.getGrouping()+"\" ";
        }
        if(isFieldChanged(origin.getPublisher(), tag.getPublisher())) {
        tags = tags + METADATA_KEY+" "+KEY_TAG_PUBLISHER+"=\""+tag.getPublisher()+"\" ";
        }
        if(isFieldChanged(origin.getLanguage(), tag.getLanguage())) {
        tags = tags + METADATA_KEY+" "+KEY_TAG_LANGUAGE+"=\""+tag.getLanguage()+"\" ";
        }
        if(isFieldChanged(origin.getMediaType(), tag.getMediaType())) {
        tags = tags + METADATA_KEY+" "+KEY_TAG_MEDIA+"=\""+tag.getMediaType()+"\" ";
        }
        if(isFieldChanged(origin.getMediaQuality(), tag.getMediaQuality())) {
        tags = tags + METADATA_KEY+" "+KEY_TAG_QUALITY+"=\""+tag.getMediaQuality()+"\" ";
        }
        if(isFieldChanged(origin.getRating(), tag.getRating())) {
        tags = tags + METADATA_KEY+" "+KEY_TAG_RATING+"=\""+tag.getRating()+"\" ";
        }
        if(isFieldChanged(origin.getTrack(), tag.getTrack())) {
            tags = tags + METADATA_KEY + " " + KEY_TAG_TRACK + "=\"" + tag.getTrack() + "\" ";
        }
        return tags;
    }

    private static String getMetadataTrackKeysForWave(MusicTag origin, MusicTag tag) {
        // need to include all metadata
        String tags = "";
        if(!isEmpty(tag.getTitle())) {
            tags = tags + METADATA_KEY + " " + KEY_TAG_WAVE_TITLE + "=\"" + tag.getTitle() + "\" ";
        }
        if(!isEmpty(tag.getAlbum())) {
            tags = tags + METADATA_KEY + " " + KEY_TAG_WAVE_ALBUM + "=\"" + tag.getAlbum() + "\" ";
        }
        if(!isEmpty(tag.getArtist())) {
            tags = tags + METADATA_KEY + " " + KEY_TAG_WAVE_ARTIST + "=\"" + tag.getArtist() + "\" ";
        }

        if(!isEmpty(tag.getGenre())) {
            tags = tags + METADATA_KEY+" "+KEY_TAG_WAVE_GENRE+"=\""+tag.getGenre()+"\" ";
        }
        if(!isEmpty(tag.getPublisher())) {
            tags = tags + METADATA_KEY+" "+KEY_TAG_WAVE_PUBLISHER+"=\""+tag.getPublisher()+"\" ";
        }
        if(!isEmpty(tag.getLanguage())) {
            tags = tags + METADATA_KEY+" "+KEY_TAG_WAVE_LANGUAGE+"=\""+tag.getLanguage()+"\" ";
        }
        if(!isEmpty(tag.getTrack())) {
            tags = tags + METADATA_KEY + " " + KEY_TAG_WAVE_TRACK + "=\"" + tag.getTrack() + "\" ";
        }
        /*
        if(isFieldChanged(origin.getGrouping(), tag.getGrouping())) {
            tags = tags + METADATA_KEY+" "+KEY_TAG_WAVE_GROUPING+"=\""+tag.getGrouping()+"\" ";
        }
        if(isFieldChanged(origin.getAlbumArtist(), tag.getAlbumArtist())) {
            tags = tags + METADATA_KEY+" "+KEY_TAG_WAVE_ALBUM_ARTIST+"=\""+tag.getAlbumArtist()+"\" ";
        }
        if(isFieldChanged(origin.getComposer(), tag.getComposer())) {
            tags = tags + METADATA_KEY+" "+KEY_TAG_COMPOSER_WAVE+"=\""+tag.getComposer()+"\" ";
        }
        if(isFieldChanged(origin.isCompilation(), tag.isCompilation())) {
            tags = tags + METADATA_KEY+" "+KEY_TAG_COMPILATION_WAVE+"=\""+(tag.isCompilation()?1:0)+"\" ";
        }
        if(isFieldChanged(origin.getDisc(), tag.getDisc())) {
            tags = tags + METADATA_KEY+" "+KEY_TAG_WAVE_DISCNUMBER+"=\""+tag.getDisc()+"\" ";
        }
        if(isFieldChanged(origin.getMediaType(), tag.getMediaType())) {
            tags = tags + METADATA_KEY+" "+KEY_TAG_WAVE_MEDIA+"=\""+tag.getMediaType()+"\" ";
        }
        if(isFieldChanged(origin.getMediaQuality(), tag.getMediaQuality())) {
            tags = tags + METADATA_KEY+" "+KEY_TAG_WAVE_QUALITY+"=\""+tag.getMediaQuality()+"\" ";
        }
        if(isFieldChanged(origin.getRating(), tag.getRating())) {
            tags = tags + METADATA_KEY+" "+KEY_TAG_WAVE_RATING+"=\""+tag.getRating()+"\" ";
        } */
        String comment = getWaveComment(tag);
        if(!isEmpty(comment)) {
            tags = tags + METADATA_KEY+" "+KEY_TAG_WAVE_COMMENT+"=\""+tag.getComment()+"\" ";
        }
        return tags;
    }

    private static String getWaveComment(MusicTag musicTag) {
        String comment = "<MusicMate>"+StringUtils.trimToEmpty(musicTag.getMediaType());
        comment = comment+"#:"+StringUtils.trimToEmpty(musicTag.getDisc());
        comment = comment+"#:"+StringUtils.trimToEmpty(musicTag.getGrouping());
        comment = comment+"#:"+StringUtils.trimToEmpty(musicTag.getMediaQuality());
        comment = comment+"</MusicMate> "+StringUtils.trimToEmpty(musicTag.getComment());
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

    private static String getMetadataTrackGainKeys(MusicTag tag) {
        // -metadata language="eng"
        String tags = "";
        tags = tags + METADATA_KEY+" "+KEY_TRACK_GAIN+"=\""+tag.getTrackGain()+"\" ";
        tags = tags + METADATA_KEY+" "+KEY_TRACK_RANGE+"=\""+tag.getTrackRange()+"\" ";
        tags = tags + METADATA_KEY+" "+KEY_REFERENCE_LOUDNESS+"=\""+tag.getTrackLoudness()+"\" ";
        tags = tags + METADATA_KEY+" "+KEY_REFERENCE_TRUEPEAK+"=\""+tag.getTrackTruePeek()+"\" ";
        return tags;
    }

    public static void detectMQA(MusicTag tag) {
        if(!MusicTagUtils.isFlacFile(tag)) return; // scan only flac
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
            Timber.e(ex);
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

    private static void readFileInfo(MusicTag tag) {
        File file = new File(tag.getPath());
        tag.setFileLastModified(file.lastModified());
    }

    private static boolean isLossless(MusicTag tag) {
        if("flac".equalsIgnoreCase(tag.getFileFormat())) return true;
        if("alac".equalsIgnoreCase(tag.getFileFormat())) return true;
        if("aiff".equalsIgnoreCase(tag.getFileFormat())) return true;
        return "wav".equalsIgnoreCase(tag.getFileFormat());
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
                    tags.put(vals[0], trimToEmpty(vals[1]));
                }
            }

            // media info
            tag.setFileSize(toLong(tags.get(KEY_SIZE))); // size
            tag.setFileFormat(tags.get(KEY_FORMAT_NAME)); // format_name
            if(tag.getFileFormat().contains(",")) {
                // found mov,mp4,m4a,...
                // use information from encoding
                if(!isEmpty(tag.getAudioEncoding())) {
                    tag.setFileFormat(getWord(tag.getAudioEncoding()," ",0));
                }
            }
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

            //KEY_TAG_COMMENT
            tag.setComment(getTagforKey(tags, KEY_TAG_COMMENT));
            if(MusicTagUtils.isWavFile(tag)) {
                parseWaveComment(tag);
            }

            //KEY_TAG_DISCNUMBER
            tag.setDisc(getTagforKey(tags, KEY_TAG_DISCNUMBER));

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
            tag.setTrackGain(toDouble(getTagforKey(tags, KEY_TRACK_GAIN)));

            // KEY_TRACK_RANGE
            tag.setTrackRange(toDouble(getTagforKey(tags, KEY_TRACK_RANGE)));

            //KEY_REFERENCE_LOUDNESS
            tag.setTrackLoudness(toDouble(getTagforKey(tags, KEY_REFERENCE_LOUDNESS)));

            //KEY_REFERENCE_TRUEPEAK
            tag.setTrackTruePeek(toDouble(getTagforKey(tags, KEY_REFERENCE_TRUEPEAK)));

            // publisher
            tag.setPublisher(getTagforKey(tags, KEY_TAG_PUBLISHER));

            // language
            tag.setLanguage(getTagforKey(tags, KEY_TAG_LANGUAGE));

            // MQA from tag and encoder
            String encoder = getTagforKey(tags, KEY_TAG_ENCODER);
            if(!MusicTagUtils.isFlacFile(tag)) {
                tag.setMqaInd("None");
            }else if(encoder.contains(KEY_MQA)) {
                tag.setMqaInd("MQA");
                tag.setMqaSampleRate(toLong(getTagforKey(tags, KEY_TAG_MQAORIGINALSAMPLERATE)));
                tag.setMqaScanned(false);
            }
        }
    }

    private static void parseWaveComment(MusicTag tag) {
        String comment = StringUtils.trimToEmpty(tag.getComment());

        /*
         String comment = "/#"+StringUtils.trimToEmpty(pendingMetadata.getSource());
            comment = comment+"##"+StringUtils.trimToEmpty(pendingMetadata.getDisc());
            comment = comment+"##"+StringUtils.trimToEmpty(pendingMetadata.getGrouping());
            comment = comment+"##"+StringUtils.trimToEmpty(pendingMetadata.isAudiophile()?Constants.AUDIOPHILE:"");
            comment = comment+"#/"+StringUtils.trimToEmpty(pendingMetadata.getComment());
         */
        int start = comment.indexOf("<MusicMate>");
        int end = comment.indexOf("</MusicMate>");
        if(start >= 0 && end > start) {
            // found metadata comment
            String metadata = comment.substring(start+11, end);
            if(comment.length()>(end+12)) {
                comment = comment.substring(end+12);
            }else {
                comment = "";
            }
            String []text = metadata.split("#:");
            if(text.length >=1) {
                // Source
                tag.setMediaType(StringUtils.trimToEmpty(text[0]));
            }
            if(text.length >=2) {
                // Disc
                tag.setDisc(StringUtils.trimToEmpty(text[1]));
            }
            if(text.length >=3) {
                // Grouping
                tag.setGrouping(StringUtils.trimToEmpty(text[2]));
            }
            if(text.length >=4) {
                // Audiophile
                tag.setMediaQuality(StringUtils.trimToEmpty(text[3]));
            }
        }

        tag.setComment(StringUtils.trimToEmpty(comment));
    }

    private static String getTagforKey(Map<String, String> tags, String key1) {
        if(tags.containsKey(KEY_TAG+key1.toUpperCase())) {
            return trimToEmpty(tags.get(KEY_TAG+key1.toUpperCase()));
        }else if (tags.containsKey(KEY_TAG+key1.toLowerCase())) {
            return trimToEmpty(tags.get(KEY_TAG+key1.toLowerCase()));
        }
        return "";
    }

    private static void parseStreamInfo(MusicTag tag) {
        // Stream #0:0: Audio:
        // Stream #0:0[0x1](eng): Audio:
        // Stream #0:1[0x0]: Video:
        //String KEYWORD_AUDIO = "Stream #0:0: Audio:";
        /*
        String KEYWORD_AUDIO = "Stream #0:0: Audio:";
        String KEYWORD_EMBED_COVER_ART = "Stream #0:1: Video:";
        int audioStart = tag.getData().indexOf(KEYWORD_AUDIO);
        if(audioStart > 0) {
            String info = tag.getData().substring(audioStart+KEYWORD_AUDIO.length(), tag.getData().indexOf("\n", audioStart));
            String [] tags = info.split(",");
            tag.setAudioEncoding(parseString(tags, 0));
            tag.setAudioSampleRate(parseSampleRate(tags, 1));
            tag.setAudioChannels(parseChannels(tags, 2));
            tag.setAudioBitsDepth(parseBitDepth(tags, 3));
        } */
        Pattern audioPattern = Pattern.compile("(?m)^\\s*Stream.*?(?:Audio):\\s*([\\s\\S]*?)(?=^\\s*Metadata:|\\Z)");
        Pattern videoPattern = Pattern.compile("(?m)^\\s*Stream.*?(?:Video):\\s*([\\s\\S]*?)(?=^\\s*Metadata:|\\Z)");
        Matcher matcher = audioPattern.matcher(tag.getData());
        if (matcher.find()) {
            String info = matcher.group(1);

            String [] tags = info.split(",");
            tag.setAudioEncoding(parseString(tags, 0));
            tag.setAudioSampleRate(parseSampleRate(tags, 1));
            tag.setAudioChannels(parseChannels(tags, 2));
            tag.setAudioBitsDepth(parseBitDepth(tags, 3));
        }

        matcher = videoPattern.matcher(tag.getData());
        if (matcher.find()) {
            String info = matcher.group(1);

           // String [] tags = info.split(",");
            tag.setEmbedCoverArt(getWord(info,",",0));
        }else {
            tag.setEmbedCoverArt("");
        }


    /*
        int coverArtStart = tag.getData().indexOf(KEYWORD_EMBED_COVER_ART);
        if(coverArtStart > 0) {
            String info = tag.getData().substring(coverArtStart+KEYWORD_EMBED_COVER_ART.length(), tag.getData().indexOf("\n", coverArtStart));
            String [] tags = info.split(",");
            tag.setEmbedCoverArt(parseString(tags, 0));
        }else {
            tag.setEmbedCoverArt("");
        } */
    }

    private static int parseBitDepth(String[] tags, int i) {
        if(tags.length>i) {
            if(tags[0].contains("dsd")) {
                return 1; // dsd
            }else if(tags[i].contains("s16")) {
                return 16;
            }else if(tags[i].contains("s32")) {
                return 24;
            }
        }
        return 0; //
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

    private static String parseString(String[] tags, int i) {
        if(tags.length>i) {
            return trimToEmpty(tags[i]);
        }
        return "";
    }

    public static void covert(String srcPath, String targetPath, CallBack callbak) {
        String options="";
        if (targetPath.endsWith(".mp3")) {
            // convert to 320k bitrate
            options = " -ar 44100 -ab 320k ";
        }else if (srcPath.endsWith(".dsf")){
            // convert from dsf to 24 bits, 48 kHz
            // use lowpass filter to eliminate distortion in the upper frequencies.
            options = " -af \"lowpass=24000, volume=6dB\" -sample_fmt s32 -ar 48000 ";
        }

        String cmd = " -hide_banner -nostats -i \""+srcPath+"\" "+options+" \""+targetPath+"\"";

        FFmpegKit.executeAsync(cmd, session -> callbak.onFinish(ReturnCode.isSuccess(session.getReturnCode())));
    }

    private static String getFFmpegOutputData(FFmpegSession session) {
        List<Log> logs = session.getLogs();
        StringBuilder buff = new StringBuilder();
        String keyword = "Integrated loudness:";
        String keyword2 = "-70.0 LUFS";
        boolean foundTag = false;
        for (Log log : logs) {
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
    }
}

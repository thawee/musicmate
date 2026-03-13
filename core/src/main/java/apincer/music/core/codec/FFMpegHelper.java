package apincer.music.core.codec;

import static apincer.music.core.utils.StringUtils.toDouble;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.antonkarpenko.ffmpegkit.FFmpegKit;
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig;
import com.antonkarpenko.ffmpegkit.FFmpegSession;
import com.antonkarpenko.ffmpegkit.Level;
import com.antonkarpenko.ffmpegkit.ReturnCode;
import com.antonkarpenko.ffmpegkit.Session;

import java.io.File;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.provider.FileSystem;
import apincer.music.core.database.MusicTag;
import apincer.music.core.utils.LogHelper;
import apincer.android.utils.FileUtils;

public class FFMpegHelper {
    public interface AnalysisCallback {
        void onResult(int score, String verdict);
        void onError(String message);
    }

    private static final String TAG = "FFMpegHelper";

    @Deprecated
    public static void extractCoverArt(MusicTag tag, File pathFile) {
        Log.d(TAG, "extractCoverArt: from:"+tag.getPath()+", to:"+pathFile);
        String targetPath = pathFile.getAbsolutePath();
       // targetPath = escapePathForFFMPEG(targetPath);
        String options = " -c:v copy ";

        String cmd = " -hide_banner -nostats -i \"" + tag.getPath() + "\" " + options + " \"" + targetPath + "\"";
        LogHelper.setFFMpegOff();
        FFmpegKit.execute(cmd); // do not clear the result
    }

    public static void extractCoverArt(String path, File pathFile, GenerateCallback callback) {
        try {
            Log.d(TAG, "extractCoverArt: from:"+path+", to:"+pathFile);
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
    //public static final String KEY_TAG_WAVE_MEDIA = "IMED";
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
    //public static final String KEY_TAG_AIF_MEDIA = "MEDIA";
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

    /**
     * Converts an audio file to a different format using FFmpeg.
     *
     * <p>This method handles the conversion of an audio file from {@code srcPath} to
     * {@code targetPath} using the specified audio parameters. It supports special
     * filtering for DSF input and applies specific bit depths and compression levels
     * based on the target format.
     *
     * <p>The conversion is performed in a temporary directory. Only upon successful
     * completion will the temporary file be moved to the final {@code targetPath}.
     *
     * <p><b>Format-Specific Logic:</b>
     * <ul>
     * <li><b>DSF Input:</b> A 24kHz lowpass filter and 6dB volume gain are applied.
     * <li><b>FLAC Output:</b> Uses {@code cLevel} for compression (defaulting to 5)
     * and {@code bitDept} for the sample format (s16, s24, s32).</li>
     * <li><b>M4A (ALAC) Output:</b> Uses {@code bitDept} for the sample format
     * (s16, s24, s32).</li>
     * <li><b>AIFF (PCM) Output:</b> Uses {@code bitDept} to select the specific
     * PCM codec (pcm_s16be, pcm_s24be, pcm_s32be).</li>
     * <li><b>MP3 Output:</b> Ignores {@code bitDept} and encodes to 320k bitrate.</li>
     * </ul>
     *
     * @param context    The Android {@link Context} used for file system operations.
     * @param srcPath    The absolute file path of the source audio file to convert.
     * @param targetPath The absolute file path where the converted file should be saved.
     * If this file already exists, a suffix ("_001") will be appended.
     * @param cLevel     The desired compression level. Primarily used for FLAC (0-12).
     * An invalid value will result in a default (e.g., 5 for FLAC).
     * @param bitDept    The desired output bit depth (16, 24, or 32). This is only
     * applied to formats that support it (FLAC, ALAC, AIFF).
     * @return {@code true} if the conversion was successful and the file was moved
     * to {@code targetPath}, {@code false} otherwise (e.g., FFmpeg failure,
     * cancellation, or file I/O error).
     */
    public static boolean convert(Context context, String srcPath, String targetPath, int cLevel, int bitDept) {
        String options = "";

        if(bitDept ==1) {
            bitDept = 24; // dsd
        }

        // 1. Handle DSF input filters
        // We only apply the filter and resampler here.
        // The bit depth (-sample_fmt) will be set later based on the `bitDept` parameter.
        if (srcPath.toLowerCase().endsWith(".dsf")) {
            // convert from dsf
            // use lowpass filter to eliminate distortion and resample.
            options += " -af \"lowpass=24000, volume=6dB\" -ar 48000 ";
        }

        // 2. Determine the output sample format string based on bitDept
        // This will be used for codecs that respect -sample_fmt (like FLAC and ALAC).
        String sampleFmt = "";
        if (bitDept == 16) {
            sampleFmt = " -sample_fmt s16 ";
        } else if (bitDept == 24) {
            sampleFmt = " -sample_fmt s24 ";
        } else if (bitDept == 32) {
            sampleFmt = " -sample_fmt s32 ";
        }
        // If bitDept is 0 or another value, we don't pass the flag,
        // letting FFmpeg choose a suitable default.

        // 3. Set codec options based on target file extension
        String targetExt = FileUtils.getExtension(targetPath.toLowerCase(Locale.US));

        if (targetExt.endsWith("flac")) {
            // FLAC respects -sample_fmt for bit depth.
            // Use 5 as a default compression if cLevel is invalid.
            int compression = (cLevel >= 0 && cLevel <= 12) ? cLevel : 5;
            options += sampleFmt + " -y -vn -c:a flac -compression_level " + compression;

        } else if (targetExt.endsWith("mp3")) {
            // MP3 is lossy and doesn't have a PCM bit depth.
            // We ignore `bitDept` and `sampleFmt`.
            options += " -y -vn -c:a libmp3lame -b:a 320k ";

        } else if (targetExt.endsWith("m4a")) {
            // Assuming ALAC (Apple Lossless), which respects -sample_fmt.
            // Your old code `pcm_s...be` was for raw PCM, not ALAC. This is correct.
            options += sampleFmt + " -y -vn -c:a alac ";

        } else if (targetExt.endsWith("aiff")) {
            // For uncompressed PCM like AIFF, we set the bit depth
            // by choosing the specific codec name. We ignore `sampleFmt`.
            if (bitDept == 24) {
                options += " -y -vn -c:a pcm_s24be "; // 24-bit Big Endian
            } else if (bitDept == 32) {
                options += " -y -vn -c:a pcm_s32be "; // 32-bit Big Endian
            } else {
                // Default to 16-bit for AIFF
                options += " -y -vn -c:a pcm_s16be "; // 16-bit Big Endian
            }
        } else {
            Log.e(TAG, "Unsupported target format: " + targetPath);
            return false;
        }

        Log.i(TAG, "Converting: " + srcPath);

        String ext = FileUtils.getExtension(srcPath);

        String tmpTarget = srcPath.replace("." + ext, "_NEWFMT." + targetExt);

        String cmd = " -hide_banner -nostats -i \"" + srcPath + "\" " + options + " \"" + tmpTarget + "\"";
        Log.i(TAG, "Converting with cmd: " + cmd);

        try {
            LogHelper.setFFMpegOff();
            FFmpegSession session = FFmpegKit.execute(cmd);

            // *** IMPORTANT FIX ***
            // You must check for SUCCESS, not just "not cancel".
            // A failed (but not cancelled) session would have been treated as a success.
            if (ReturnCode.isSuccess(session.getReturnCode())) {
                Log.i(TAG, "Conversion successful: " + srcPath);
                File targetFile = new File(targetPath);
                if (targetFile.exists()) {
                    // Consider a better way to handle existing files
                    // This logic is risky, e.g., "song.v1.mp3" -> "song_001.v1.mp3"
                    targetPath = targetPath.replaceFirst("\\.(?=[^\\.]+$)", "_001.");
                }

                if(FileSystem.move(context, tmpTarget, targetPath)) {
                   // FileRepository.newInstance(context).scanMusicFile(new File(targetPath),true); // re scan file
                    return true;
                } else {
                    Log.e(TAG, "Failed to move temp file to final target");
                    return false;
                }
            } else {
                // Conversion failed or was cancelled
                Log.e(TAG, String.format("Conversion failed. RC: %s. Logs:\n%s",
                        session.getReturnCode(), session.getAllLogsAsString()));
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "FFmpeg execution threw an exception", e);
            return false;
        } finally {
            // Always clean up the temp *source* file
           // FileSystem.delete(tmpPath);
            // Also clean up the temp *target* file in case of failure
            FileSystem.delete(tmpTarget);
        }
    }

    public static void generateSpectrum(Context context, MediaTrack track, GenerateCallback callback) {
        String inputPath = track.getPath();
        String outputPath = context.getCacheDir() + "/spectrogram.jpg";
        FileSystem.delete(outputPath);

        int sampleRate = (int) track.getAudioSampleRate();
        // Cap visual max frequency at 24kHz for better scannability,
        // but keep highFreqVolume check raw for accuracy.
        int visualMaxFreq = Math.min(sampleRate / 2, 24000);

        /*
        // 1. Added -vn for speed
        // 2. Added escaped quotes for file path safety
        // 3. Changed s=600x800 (Portrait) - perfect for mobile cards
        @SuppressLint("DefaultLocale") String ffmpegCommand = String.format(
                "-y -threads 4 -vn -i \"%s\" " +
                        "-filter_complex \"[0:a]asplit[v_in][a_in];" +
                        "[v_in]showspectrumpic=s=600x800:legend=1:color=plasma:scale=log:fscale=lin:mode=separate:stop=%d:limit=1:gain=0.05:drange=120[v_out];" +
                        "[a_in]highpass=f=20000,astats=metadata=1:reset=1[a_out]\" " +
                        "-map \"[v_out]\" -q:v 2 %s " +
                        "-map \"[a_out]\" -f null -",
                inputPath, visualMaxFreq, outputPath
        ); */

        // We split the audio into 3 streams now:
        // 1. Visual (Spectrogram)
        // 2. High-Freq Check (Air), 16000 for lossy upscale
        @SuppressLint("DefaultLocale") String ffmpegCommand = String.format(
                "-y -threads 8 -vn -i \"%s\" " +
                        "-filter_complex \"[0:a]asplit=2[v_in][a_high];" +
                        "[v_in]showspectrumpic=s=600x800:legend=1:color=plasma:scale=log:fscale=lin:mode=separate:stop=%d:limit=1:gain=0.05:drange=120[v_out];" +
                        "[a_high]highpass=f=16000,astats=metadata=1:reset=1[high_out]\" " +
                        "-map \"[v_out]\" -q:v 2 %s " +
                        "-map \"[high_out]\" -f null -",
                inputPath, visualMaxFreq, outputPath
        );

        FFmpegKit.executeAsync(ffmpegCommand, session -> {
            String log = session.getOutput();
            if (ReturnCode.isSuccess(session.getReturnCode())) {

                // 1. Get High-Frequency Energy (Air) from the first metadata stream
                // FFmpeg labels these as Stream #1 and Stream #2 in the log
                double highFreqVolume = parseValue(log, "RMS", "Stream #1");
                //boolean hasHighFreqEnergy = highFreqVolume > -100.0;
                // At 16kHz, 'hasHighFreqEnergy' is more likely to be true for good files.
                boolean hasHighFreqEnergy = highFreqVolume > -90.0; // Slightly more 'room' than -100dB

                // 2. Get Real Bit Depth and Dynamics from the second metadata stream (Full Range)
                int realBitDepth = (int) parseValue(log, "BitDepth", "Stream #1");
                // If the high-freq region is silent (0 bits), fall back to container info
                //if(realBitDepth == 0) realBitDepth = track.getAudioBitsDepth();

                String qualityStatus;

                /*
                if (realBitDepth <= 16) {
                    if (sampleRate >= 44100 && !hasHighFreqEnergy) {
                        // A 48kHz or 96kHz file with NO energy above 16kHz is a definitive 'Fake'
                        qualityStatus = "High-frequency 'air' is limited (common for MP3).";
                    } else {
                      //  qualityStatus = "16-bit Lossless";
                        qualityStatus = "High Fidelity: High-frequency detail is limited.";
                    }
                } else {
                    if (highFreqVolume < -110.0) {
                        //qualityStatus = "24-bit Hi-Res";
                        qualityStatus = "High Fidelity: Full-range spectral detail is present.";
                    } else {
                       // qualityStatus = "24-bit Studio";
                        qualityStatus = "High Fidelity: High-frequency detail is limited.";
                    }
                }
                 */

                String technicalNote = ".";

                if (realBitDepth == 0) {
                    // Keep the container bit-depth for the UI badge (e.g., "24-bit")
                    realBitDepth = track.getAudioBitsDepth();

                    if (sampleRate > 48000) {
                        technicalNote = ", large file size but limited audio details";
                    } else {
                        technicalNote = ", the high-end is \"rolled off\"";
                    }
                }

                if (realBitDepth <= 16) {
                    if (sampleRate >= 44100 && !hasHighFreqEnergy) {
                        // No energy above 16kHz = likely MP3 source or very dark vintage
                        qualityStatus = "High-frequency 'air' is limited (common for MP3)";
                    } else {
                        // Energy present = True CD Quality
                        qualityStatus = "High Fidelity: Full-range spectral detail is present";
                    }
                } else {
                    // 24-bit Logic: We WANT to see energy here for it to be "Full-range"
                    if (hasHighFreqEnergy) {
                        qualityStatus = "High Fidelity: Full-range spectral detail is present";
                    } else {
                        // It's 24-bit, but the high-end is empty
                        qualityStatus = "High Fidelity: High-frequency detail is limited";
                    }
                }
                String finalStatus = qualityStatus + technicalNote;

                Log.d("MusicMate", "Validated: " + finalStatus + " (" + realBitDepth + "-bit)");
                callback.onGenerated(outputPath, qualityStatus, realBitDepth, highFreqVolume);
            } else {
                callback.onError("Analysis failed");
                Log.e("MusicMate", "FFmpeg Error: " + session.getFailStackTrace());
            }
        });
    }

    private static double parseValue(String log, String key, String sectionKey) {
        // 1. Isolate the specific section (e.g., "Overall" or "Stream #1")
        String sectionData = "";
        if (log.contains(sectionKey)) {
            // We take the text starting from the LAST occurrence of the section key
            // because FFmpeg prints channel stats first, then the Overall stats.
            sectionData = log.substring(log.lastIndexOf(sectionKey));
        } else {
            sectionData = log;
        }

        String regex = "";
        switch (key) {
            case "RMS":
                // Matches "RMS level dB: -14.50"
                regex = "RMS level dB:\\s+(-?\\d+\\.?\\d*)";
                break;
            case "Peak":
                // Matches "Peak level dB: -0.10"
                regex = "Peak level dB:\\s+(-?\\d+\\.?\\d*)";
                break;
            case "BitDepth":
                // Matches "Bit depth: 24/24/0/0" -> captures 24
                regex = "Bit depth:\\s+(\\d+)";
                break;
            default:
                // Generic fallback for other astats keys (Crest factor, DC offset, etc.)
                regex = key + ":\\s+(-?\\d+\\.?\\d*)";
                break;
        }

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(sectionData);

        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                return (key.equals("BitDepth")) ? 0 : -144.0;
            }
        }

        return (key.equals("BitDepth")) ? 0 : -144.0;
    }

   /* private static double parseValue(String log, String key, String sectionKey) {
        // 1. Find the "Overall" section first to avoid per-channel data
        String overallSection = "";
        //if (log.contains("Overall")) {
        if (log.contains(sectionKey)) {
            //overallSection = log.substring(log.lastIndexOf("Overall"));
            overallSection = log.substring(log.lastIndexOf(sectionKey));
        } else {
            overallSection = log;
        }

        // 2. Updated Regex to match "RMS level dB" and "Bit depth"
        // For Bit Depth, we only want the first number (e.g., 18 from 18/32/32/32)
        String regex = "";
        if (key.equals("RMS")) {
            regex = "RMS level dB:\\s+(-?\\d+\\.?\\d*)";
        } else if (key.equals("BitDepth")) {
            regex = "Bit depth:\\s+(\\d+)";
        }else {
            regex = key + ":\\s+(-?\\d+\\.?\\d*)";
        }

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(overallSection);

        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        return (key.equals("BitDepth")) ? 0 : -144.0;
    } */

    private static double parseDouble(String log, String key) {
        Pattern pattern = Pattern.compile(key + ":\\s+(-?\\d+\\.?\\d*)");
        Matcher matcher = pattern.matcher(log);
        double value = 0;
        while (matcher.find()) {
            try { value = Double.parseDouble(matcher.group(1)); } catch (Exception e) {}
        }
        return value;
    }

    private static double parseRMSVolume(String log) {
        Pattern pattern = Pattern.compile("Overall.RMS_level:\\s+(-?\\d+\\.?\\d*)");
        Matcher matcher = pattern.matcher(log);
        double rms = -144.0;
        while (matcher.find()) {
            try {
                rms = Double.parseDouble(matcher.group(1));
            } catch (Exception e) { /* ignore */ }
        }
        return rms;
    }

    // Update your interface to handle the new data
    public interface GenerateCallback {
        void onGenerated(String path, String qualityStatus, int realBits, double highFreqDb);
        void onError(String error);
    }

    public static void generateWaveform(Context context, MediaTrack track, GenerateCallback callback) {
        String inputPath = track.getPath();
        String outputPath = context.getCacheDir() + "/waveform/" + track.getId() + ".jpg";
       // String colorHex = "#6200EE@0.8";

        File file = new File(outputPath);
        if(!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        // Command for a clean, professional mono waveform
        String cmd = String.format(
                //"-y -threads 4 -i \"%s\" -filter_complex \"aformat=channel_layouts=mono,showwavespic=s=1024x200:colors=#6200EE@0.8:scale=log\" -vframes 1 %s",
                //"-y -threads 4 -i \"%s\" -filter_complex \"aformat=channel_layouts=mono,showwavespic=s=1024x200:colors='white':scale=log\" -vframes 1 -q:v 2 %s",
                "-y -threads 4 -i \"%s\" -filter_complex \"aformat=channel_layouts=mono,showwavespic=s=1080x240:colors='#D3D3D3':scale=sqrt\" -vframes 1 -q:v 1 %s",
                inputPath, outputPath
        );

        FFmpegKit.executeAsync(cmd, session -> {
            if (ReturnCode.isSuccess(session.getReturnCode())) {
               // callback.onGenerated(outputPath);
            } else {
                callback.onError("Waveform failed");
            }
        });
    }

    public static void verifyQuality(String filePath, AnalysisCallback callback) {
        // We analyze a 10-second slice starting at the 60s mark (to skip silence)
        // We use the 'astats' filter to measure the Peak and RMS levels
        // The 'firequalizer' is used as a bypass to check for energy above 20kHz
       // String cmd = String.format("-ss 30 -t 10 -i \"%s\" -af \"firequalizer=gain='if(gt(f,20000),0,-inf)',astats=metadata=1\" -f null -", filePath);
        String cmd = String.format(
                "-y -i \"%s\" -ss 60 -t 10 -map 0:a -vn -filter:a \"firequalizer=gain='if(gt(f,20000),0,-inf)',astats=metadata=1\" -f null -",
                filePath
        );
        FFmpegSession session = FFmpegKit.execute(cmd);
            if (ReturnCode.isSuccess(session.getReturnCode())) {
                String logs = session.getOutput();
                int score = calculateScoreFromLogs(logs);
                String verdict = getVerdict(score);
                callback.onResult(score, verdict);
            } else {
                callback.onError("Analysis failed: " + session.getFailStackTrace());
            }
    }

    public static void verifyQualityAsync(String filePath, AnalysisCallback callback) {
        // We analyze a 10-second slice starting at the 60s mark (to skip silence)
        // We use the 'astats' filter to measure the Peak and RMS levels
        // The 'firequalizer' is used as a bypass to check for energy above 20kHz
        // String cmd = String.format("-ss 30 -t 10 -i \"%s\" -af \"firequalizer=gain='if(gt(f,20000),0,-inf)',astats=metadata=1\" -f null -", filePath);
        String cmd = String.format(
                "-y -i \"%s\" -ss 60 -t 10 -map 0:a -vn -filter:a \"firequalizer=gain='if(gt(f,20000),0,-inf)',astats=metadata=1\" -f null -",
                filePath
        );
        FFmpegKit.executeAsync(cmd, session -> {
            if (ReturnCode.isSuccess(session.getReturnCode())) {
                String logs = session.getOutput();
                int score = calculateScoreFromLogs(logs);
                String verdict = getVerdict(score);
                callback.onResult(score, verdict);
            } else {
                callback.onError("Analysis failed: " + session.getFailStackTrace());
            }
        });
    }

    private static int calculateScoreFromLogs(String logs) {
        // We look for 'RMS level' in the logs.
        // If the RMS level of frequencies ABOVE 20kHz is very low (e.g., < -90dB),
        // it means there's almost no high-frequency data (Fake Lossless).
        double rmsHigh = parseRmsLevel(logs);

        if (rmsHigh > -50.0) return 100; // Strong Hi-Res presence
        if (rmsHigh > -70.0) return 85;  // Standard CD Quality
        if (rmsHigh > -85.0) return 60;  // Likely 320kbps Upscale
        if (rmsHigh > -100.0) return 30; // Likely 128kbps Upscale
        return 10; // "Empty" High frequencies
    }
    private static double parseRmsLevel(String logs) {
        Pattern pattern = Pattern.compile("RMS level dB: (-?\\d+\\.\\d+)");
        Matcher matcher = pattern.matcher(logs);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        return -120.0; // Default floor
    }

    private static String getVerdict(int score) {
        /*if (score >= 85) return "Verified Lossless ✅";
        if (score >= 60) return "Suspected Transcode ⚠️";
        return "Likely Fake / Low Quality 🚩";
        */
        String verdict;
        if (score >= 90) verdict = "Master Quality";
        else if (score >= 75) verdict = "High Fidelity";
        else if (score >= 50) verdict = "Potential Upsampled";
        else verdict = "High-Cut Detected";

        return verdict;
    }
}

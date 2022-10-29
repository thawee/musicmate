package apincer.android.mmate.utils;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.Log;
import com.arthenica.ffmpegkit.ReturnCode;

import java.util.List;

import timber.log.Timber;

public class FFMPegUtils {
    public static class Loudness {
        public String getIntegratedLoudness() {
            return integratedLoudness;
        }

        public String getLoudnessRange() {
            return loudnessRange;
        }

        public String getTruePeak() {
            return truePeak;
        }

        String integratedLoudness;
        String loudnessRange;
        String truePeak;

        public Loudness(String integrated, String range, String peak) {
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
            String cmd = " -hide_banner -i \"" + path + "\" -filter_complex ebur128=peak=true -f null -";
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
                return new Loudness(integrated, range, peak);
            }
        } catch (Exception ex) {
            Timber.e(ex);
        }
        return null;
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

        String cmd = " -hide_banner -i \""+srcPath+"\" "+options+" \""+targetPath+"\"";

        FFmpegKit.executeAsync(cmd, session -> callbak.onFinish(ReturnCode.isSuccess(session.getReturnCode())));
    }

    private static String getFFmpegOutputData(FFmpegSession session) {
        List<Log> logs = session.getLogs();
        StringBuilder buff = new StringBuilder();
        String keyword = "Integrated loudness:";
        String keyword2 = "-70.0 LUFS";
        boolean foundTag = false;
        for (Log log : logs) {
            String msg = StringUtils.trimToEmpty(log.getMessage());
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
}

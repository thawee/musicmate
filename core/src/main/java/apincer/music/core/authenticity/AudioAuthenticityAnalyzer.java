package apincer.music.core.authenticity;

import android.util.Log;

import com.antonkarpenko.ffmpegkit.FFmpegKit;
import com.antonkarpenko.ffmpegkit.FFmpegSession;
import com.antonkarpenko.ffmpegkit.ReturnCode;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

import apincer.music.core.codec.FFMpegHelper;
import apincer.music.core.utils.MusicMateExecutors;

/**
 * Verify Lossless: decodes 30 seconds of a track once and reads its upper spectrum for the marks that
 * upsampling and lossy encoding leave ({@link SpectralCliffDetector}), plus its peak and RMS level.
 */
public class AudioAuthenticityAnalyzer {

    private static final String TAG = "AudioAnalyzer";
    /** Seconds analyzed, from 30 s in; from the start when the track is shorter. */
    static final int WINDOW_SECONDS = 30;
    private static final int START_SECONDS = 30;

    public interface Callback {
        void onResult(AudioAnalysisResult result);
        void onError(String error);
    }

    /** Analyzes {@code inputPath} off the calling thread; the callback also runs off the main thread. */
    public static void analyze(String inputPath, int sampleRate, int bitDepth, Callback callback) {
        MusicMateExecutors.execute(() -> {
            AudioAnalysisResult result;
            try {
                result = analyzeNow(inputPath, bitDepth);
            } catch (IOException | RuntimeException e) {
                Log.e(TAG, "Analysis failed: " + inputPath, e);
                callback.onError(e.getMessage() != null ? e.getMessage() : e.toString());
                return;
            }
            callback.onResult(result);
        });
    }

    static AudioAnalysisResult analyzeNow(String inputPath, int bitDepth) throws IOException {
        String lower = inputPath.toLowerCase(Locale.ROOT);
        AudioAnalysisResult r = new AudioAnalysisResult();
        r.bitDepth = bitDepth;
        if (lower.endsWith(".dsf") || lower.endsWith(".dff")) {
            // DSD's noise shaping fills the ultrasonic range by design; the spectral marks do not apply
            r.kind = null;
            r.verdict = "Not checked: DSD";
            r.cutoffHz = Double.NaN;
            r.peak = r.rms = Double.NaN;
            return r;
        }
        File pcm = File.createTempFile("verify-", ".wav");
        try {
            Wav wav = decode(inputPath, START_SECONDS, pcm);
            if (wav.seconds() < 10) {
                wav = decode(inputPath, 0, pcm);
            }
            measure(wav, pcm, r);
        } finally {
            if (!pcm.delete() && pcm.exists()) Log.w(TAG, "Could not delete " + pcm);
        }
        return r;
    }

    /** Decodes {@link #WINDOW_SECONDS} from {@code start} to 32-bit float stereo WAV at the source rate. */
    private static Wav decode(String inputPath, int start, File out) throws IOException {
        String cmd = "-y -hide_banner -nostats -ss " + start + " -t " + WINDOW_SECONDS
                + " -i " + FFMpegHelper.input(inputPath)
                + " -map 0:a:0 -ac 2 -c:a pcm_f32le -f wav \"" + out.getAbsolutePath() + "\"";
        FFmpegSession session = FFmpegKit.execute(cmd);
        if (!ReturnCode.isSuccess(session.getReturnCode())) {
            throw new IOException("Could not decode the audio (FFmpeg " + session.getReturnCode() + ")");
        }
        return Wav.read(out);
    }

    private static void measure(Wav wav, File file, AudioAnalysisResult r) throws IOException {
        SpectrumAccumulator spectrum = new SpectrumAccumulator(wav.sampleRate);
        double peak = 0, sumSquares = 0;
        long count = 0;
        try (FileChannel ch = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            ch.position(wav.dataOffset);
            ByteBuffer buf = ByteBuffer.allocate(1 << 16).order(ByteOrder.LITTLE_ENDIAN);
            long left = wav.dataBytes;
            while (left > 0 && ch.read(buf) > 0) {
                buf.flip();
                while (buf.remaining() >= 8 && left >= 8) {
                    float l = buf.getFloat(), rt = buf.getFloat();
                    left -= 8;
                    peak = Math.max(peak, Math.max(Math.abs(l), Math.abs(rt)));
                    sumSquares += (double) l * l + (double) rt * rt;
                    count += 2;
                    spectrum.add((l + rt) / 2);
                }
                buf.compact();
            }
        }
        r.sampleRate = wav.sampleRate;
        r.peak = count > 0 ? toDb(peak) : Double.NaN;
        r.rms = count > 0 ? toDb(Math.sqrt(sumSquares / count)) : Double.NaN;
        if (spectrum.frames() == 0) {
            r.kind = SpectralCliffDetector.Kind.CANT_TELL;
            r.cutoffHz = Double.NaN;
            r.verdict = "Can't tell: too short to analyze";
            return;
        }
        SpectralCliffDetector.Finding finding = SpectralCliffDetector.classify(spectrum.bandsDb(), wav.sampleRate, r.bitDepth);
        r.kind = finding.kind;
        r.cutoffHz = finding.edgeHz;
        r.verdict = verdict(finding);
    }

    static String verdict(SpectralCliffDetector.Finding f) {
        switch (f.kind) {
            case UPSAMPLED_FROM_44: return "Upsampled from 44.1 kHz";
            case UPSAMPLED_FROM_48: return "Upsampled from 48 kHz";
            case UPSAMPLED_FROM_44_IMAGES: return "Upsampled from 44.1 kHz (resampler images)";
            case UPSAMPLED_FROM_48_IMAGES: return "Upsampled from 48 kHz (resampler images)";
            case LOSSY: return String.format(Locale.US, "Lossy source, cutoff about %.1f kHz", f.edgeHz / 1000);
            case NO_SIGN_OF_UPSAMPLING: return "No sign of upsampling";
            case NO_LOSSY_CUTOFF: return "No lossy cutoff found";
            default: return "Can't tell: no high-frequency content";
        }
    }

    private static double toDb(double linear) {
        return linear > 0 ? 20 * Math.log10(linear) : -144.0;
    }

    /** The parts of a WAV file this needs: its rate and where its samples are. */
    static final class Wav {
        final int sampleRate;
        final long dataOffset;
        final long dataBytes;

        Wav(int sampleRate, long dataOffset, long dataBytes) {
            this.sampleRate = sampleRate;
            this.dataOffset = dataOffset;
            this.dataBytes = dataBytes;
        }

        double seconds() {
            return sampleRate > 0 ? dataBytes / 8.0 / sampleRate : 0;
        }

        static Wav read(File file) throws IOException {
            try (FileChannel ch = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
                ByteBuffer head = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
                ch.read(head, 0);
                head.flip();
                if (head.remaining() < 12 || head.getInt(0) != 0x46464952 || head.getInt(8) != 0x45564157) {
                    throw new IOException("Not a WAV file");
                }
                int rate = 0;
                long pos = 12, size = ch.size();
                ByteBuffer chunk = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
                while (pos + 8 <= size) {
                    chunk.clear();
                    ch.read(chunk, pos);
                    int id = chunk.getInt(0);
                    long len = chunk.getInt(4) & 0xffffffffL;
                    if (id == 0x20746d66) { // "fmt "
                        ByteBuffer fmt = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
                        ch.read(fmt, pos + 8);
                        rate = fmt.getInt(4);
                    } else if (id == 0x61746164) { // "data"
                        return new Wav(rate, pos + 8, Math.min(len, size - pos - 8));
                    }
                    pos += 8 + len + (len & 1);
                }
                throw new IOException("No audio data");
            }
        }
    }
}

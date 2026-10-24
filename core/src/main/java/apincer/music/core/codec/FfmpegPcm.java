package apincer.music.core.codec;

import android.content.Context;
import android.util.Log;

import com.antonkarpenko.ffmpegkit.FFmpegKit;
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig;
import com.antonkarpenko.ffmpegkit.FFmpegSession;
import com.antonkarpenko.ffmpegkit.FFprobeKit;
import com.antonkarpenko.ffmpegkit.MediaInformation;
import com.antonkarpenko.ffmpegkit.StreamInformation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Any audio file FFmpeg reads, presented as a WAV image for local playback: Media3 has no AIFF or
 * DSD extractor, and ALAC .m4a plays silently on some phones (Samsung). ALAC and AIFF stay
 * lossless at their own rate and depth; DSD becomes 32-bit float PCM at {@link #DSD_PCM_RATE}.
 * FFmpeg puts DSD's 0 dB at -6 dBFS (a DSD64 test track peaked at -6.1 dBFS), and float keeps
 * any hotter master from clipping inside FFmpeg. The frame count comes from
 * ffprobe; readers pad or trim to it so the WAV header stays true.
 */
public final class FfmpegPcm {
    private static final String TAG = "FfmpegPcm";
    /** 88.2 kHz keeps everything up to 44 kHz and leaves out most of DSD's ultrasonic noise. */
    public static final int DSD_PCM_RATE = 88_200;
    /** Playback sessions get their own threads, so a stuck pipe never holds FFmpegKit's shared pool. */
    private static final ExecutorService SESSIONS = Executors.newCachedThreadPool();

    public final int sampleRate;
    public final int channels;
    public final int bitsPerSample;
    public final boolean floatSamples;
    public final boolean dsd;
    public final long numFrames;

    FfmpegPcm(int sampleRate, int channels, int bitsPerSample, boolean floatSamples, boolean dsd, long numFrames) {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.bitsPerSample = bitsPerSample;
        this.floatSamples = floatSamples;
        this.dsd = dsd;
        this.numFrames = numFrames;
    }

    /** Reads the first audio stream's format and length with ffprobe. */
    public static FfmpegPcm probe(File file) throws IOException {
        MediaInformation info = FFprobeKit.getMediaInformation(file.getAbsolutePath()).getMediaInformation();
        if (info == null || info.getStreams() == null) throw new IOException("ffprobe failed: " + file);
        for (StreamInformation stream : info.getStreams()) {
            if (!"audio".equals(stream.getType())) continue;
            Long channels = stream.getNumberProperty("channels");
            String bits = stream.getStringProperty("bits_per_raw_sample");
            if (bits == null || "0".equals(bits)) bits = stream.getStringProperty("bits_per_sample");
            double seconds = seconds(stream.getStringProperty("duration_ts"), stream.getTimeBase(),
                    stream.getStringProperty("duration") != null ? stream.getStringProperty("duration") : info.getDuration());
            FfmpegPcm pcm = of(stream.getCodec(), parseInt(stream.getSampleRate()), stream.getSampleFormat(),
                    parseInt(bits), channels != null ? channels.intValue() : 0, seconds);
            if (pcm == null) throw new IOException("Audio format unknown: " + file);
            return pcm;
        }
        throw new IOException("No audio stream: " + file);
    }

    /** The output format for a stream ffprobe describes; null when its rate, channels or length are unknown. */
    static FfmpegPcm of(String codec, int streamRate, String sampleFormat, int bits, int channels, double seconds) {
        boolean dsd = codec != null && codec.startsWith("dsd_");
        int rate = dsd ? DSD_PCM_RATE : streamRate;
        if (rate <= 0 || channels <= 0 || !(seconds > 0)) return null;
        boolean floating = dsd || (sampleFormat != null && (sampleFormat.startsWith("flt") || sampleFormat.startsWith("dbl")));
        int depth = floating ? 32 : bits > 24 ? 32 : bits > 16 ? 24 : 16;
        return new FfmpegPcm(rate, channels, depth, floating, dsd, Math.round(seconds * rate));
    }

    /** Seconds from duration_ts in time_base units ("1/44100"), else the plain duration. */
    static double seconds(String durationTs, String timeBase, String duration) {
        try {
            if (durationTs != null && timeBase != null && timeBase.contains("/")) {
                String[] tb = timeBase.split("/");
                return Long.parseLong(durationTs) * Double.parseDouble(tb[0]) / Double.parseDouble(tb[1]);
            }
            return duration != null ? Double.parseDouble(duration) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int parseInt(String value) {
        try {
            return value != null ? Integer.parseInt(value) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public int frameSize() {
        return channels * bitsPerSample / 8;
    }

    public long wavLength() {
        return FlacToWav.HEADER_SIZE + numFrames * frameSize();
    }

    public byte[] header() {
        short tag = floatSamples ? FlacToWav.WAVE_FORMAT_IEEE_FLOAT : FlacToWav.WAVE_FORMAT_PCM;
        return FlacToWav.wavHeader(tag, sampleRate, channels, bitsPerSample, numFrames * frameSize());
    }

    /** FFmpeg arguments that write raw PCM from {@code startFrame} on; the input seek decodes up to it, so it is exact. */
    List<String> arguments(String input, long startFrame, String output) {
        List<String> args = new ArrayList<>(List.of("-nostdin", "-v", "error"));
        if (startFrame > 0) args.addAll(List.of("-ss", String.format(Locale.ROOT, "%.6f", startFrame / (double) sampleRate)));
        args.addAll(List.of("-i", input, "-map", "0:a:0"));
        if (dsd) args.addAll(List.of("-ar", String.valueOf(sampleRate)));
        String pcm = floatSamples ? "f32le" : "s" + bitsPerSample + "le";
        args.addAll(List.of("-f", pcm, "-acodec", "pcm_" + pcm, "-y", output));
        return args;
    }

    /**
     * The PCM samples of {@code file} from {@code startFrame} on, decoded by FFmpeg into a named
     * pipe. Blocks until FFmpeg starts writing; closing the stream stops FFmpeg.
     */
    public InputStream open(Context context, File file, long startFrame) throws IOException {
        String pipe = FFmpegKitConfig.registerNewFFmpegPipe(context);
        AtomicBoolean readerOpened = new AtomicBoolean();
        FFmpegSession session = FFmpegKit.executeWithArgumentsAsync(
                arguments(file.getAbsolutePath(), startFrame, pipe).toArray(new String[0]), done -> {
                    // If ffmpeg failed before opening the pipe, open it once so the reader is not stuck
                    if (!readerOpened.get()) {
                        try (FileOutputStream unblock = new FileOutputStream(pipe)) {
                            Log.w(TAG, "ffmpeg ended before writing: " + done.getReturnCode() + " " + file.getName());
                        } catch (IOException ignored) {
                            // the reader is already gone
                        }
                    }
                }, SESSIONS);
        FileInputStream in;
        try {
            in = new FileInputStream(pipe); // blocks until ffmpeg opens the pipe
        } catch (IOException e) {
            FFmpegKit.cancel(session.getSessionId());
            FFmpegKitConfig.closeFFmpegPipe(pipe);
            throw e;
        }
        readerOpened.set(true);
        return new InputStream() {
            @Override
            public int read() throws IOException {
                return in.read();
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                return in.read(buffer, offset, length);
            }

            @Override
            public void close() throws IOException {
                // A seek or skip closes early: stop ffmpeg instead of leaving it blocked on the pipe
                FFmpegKit.cancel(session.getSessionId());
                try {
                    in.close();
                } finally {
                    FFmpegKitConfig.closeFFmpegPipe(pipe);
                }
            }
        };
    }
}

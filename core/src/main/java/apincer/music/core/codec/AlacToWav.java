package apincer.music.core.codec;

import android.content.Context;
import android.util.Log;

import com.antonkarpenko.ffmpegkit.FFmpegKit;
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig;
import com.antonkarpenko.ffmpegkit.FFprobeKit;
import com.antonkarpenko.ffmpegkit.MediaInformation;
import com.antonkarpenko.ffmpegkit.StreamInformation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An ALAC (.m4a) file presented as a WAV file (PCM at the file's own rate and bit depth:
 * lossless), for TVs that cannot play ALAC. Android's MediaExtractor does not recognise ALAC on
 * some phones (Samsung reports "audio/unknown"), so FFmpegKit decodes it into a named pipe.
 * Start positions are sample-exact (atrim=start_sample). The sample count comes from ffprobe;
 * the stream is padded or trimmed to it so Content-Length stays true.
 */
public final class AlacToWav {
    private static final String TAG = "AlacToWav";

    public final int sampleRate;
    public final int channels;
    public final int bitsPerSample;
    public final long numSamples;
    private final int frameSize;
    private final Context context;

    private AlacToWav(Context context, int sampleRate, int channels, int bitsPerSample, long numSamples) {
        this.context = context;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.bitsPerSample = bitsPerSample;
        this.numSamples = numSamples;
        this.frameSize = channels * bitsPerSample / 8;
    }

    /** Reads the ALAC stream's format and exact length with ffprobe. */
    public static AlacToWav open(Context context, File m4a, int fileBitsPerSample) throws IOException {
        MediaInformation info = FFprobeKit.getMediaInformation(m4a.getAbsolutePath()).getMediaInformation();
        if (info == null || info.getStreams() == null) throw new IOException("ffprobe failed: " + m4a);
        for (StreamInformation stream : info.getStreams()) {
            if (!"alac".equals(stream.getCodec())) continue;
            int rate = Integer.parseInt(stream.getSampleRate());
            Long channels = stream.getNumberProperty("channels");
            String rawBits = stream.getStringProperty("bits_per_raw_sample");
            int bits = rawBits != null ? Integer.parseInt(rawBits) : fileBitsPerSample;
            bits = bits > 16 ? 24 : 16;
            long samples = sampleCount(stream.getStringProperty("duration_ts"), stream.getStringProperty("time_base"), rate);
            if (channels == null || samples <= 0) throw new IOException("ALAC length unknown: " + m4a);
            return new AlacToWav(context, rate, channels.intValue(), bits, samples);
        }
        throw new IOException("No ALAC stream: " + m4a);
    }

    /** duration_ts in time_base units ("1/44100") converted to sample frames. */
    static long sampleCount(String durationTs, String timeBase, int sampleRate) {
        if (durationTs == null || timeBase == null || !timeBase.contains("/")) return -1;
        String[] tb = timeBase.split("/");
        double seconds = Long.parseLong(durationTs) * Double.parseDouble(tb[0]) / Double.parseDouble(tb[1]);
        return Math.round(seconds * sampleRate);
    }

    public long wavLength() {
        return FlacToWav.HEADER_SIZE + numSamples * frameSize;
    }

    public long byteAtSeconds(double seconds) {
        long frame = Math.min(numSamples, Math.max(0, (long) (seconds * sampleRate)));
        return FlacToWav.HEADER_SIZE + frame * frameSize;
    }

    public byte[] header() {
        return FlacToWav.wavHeader(sampleRate, channels, bitsPerSample, numSamples * frameSize);
    }

    /** Writes bytes {@code from}..{@code to} (inclusive) of the WAV image of {@code m4a}. */
    public void write(File m4a, long from, long to, FlacToWav.ByteSink sink) throws IOException {
        to = Math.min(to, wavLength() - 1);
        if (from > to) return;
        if (from < FlacToWav.HEADER_SIZE) {
            byte[] header = header();
            int end = (int) Math.min(to, FlacToWav.HEADER_SIZE - 1);
            sink.write(header, (int) from, end - (int) from + 1);
            if (to < FlacToWav.HEADER_SIZE) return;
            from = FlacToWav.HEADER_SIZE;
        }
        long dataFrom = from - FlacToWav.HEADER_SIZE;
        long remaining = to - from + 1;
        long startFrame = dataFrom / frameSize;
        int skip = (int) (dataFrom % frameSize);

        String pcm = bitsPerSample == 24 ? "s24le" : "s16le";
        String pipe = FFmpegKitConfig.registerNewFFmpegPipe(context);
        List<String> args = new ArrayList<>(List.of("-nostdin", "-v", "error", "-i", m4a.getAbsolutePath(), "-map", "0:a:0"));
        if (startFrame > 0) args.addAll(List.of("-af", "atrim=start_sample=" + startFrame)); // sample-exact start
        args.addAll(List.of("-f", pcm, "-acodec", "pcm_" + pcm, "-y", pipe));
        AtomicBoolean readerOpened = new AtomicBoolean();
        FFmpegKit.executeWithArgumentsAsync(args.toArray(new String[0]), session -> {
            // If ffmpeg failed before opening the pipe, open it once so the reader is not stuck
            if (!readerOpened.get()) {
                try (FileOutputStream unblock = new FileOutputStream(pipe)) {
                    Log.w(TAG, "ffmpeg ended before writing: " + session.getReturnCode());
                } catch (IOException ignored) {
                    // the reader is already gone
                }
            }
        });
        byte[] buffer = new byte[64 * 1024];
        try (FileInputStream in = new FileInputStream(pipe)) { // blocks until ffmpeg opens the pipe
            readerOpened.set(true);
            int n;
            while (remaining > 0 && (n = in.read(buffer)) > 0) {
                int offset = 0;
                if (skip > 0) {
                    int dropped = Math.min(skip, n);
                    skip -= dropped;
                    offset = dropped;
                }
                int length = (int) Math.min(n - offset, remaining);
                if (length > 0) {
                    sink.write(buffer, offset, length);
                    remaining -= length;
                }
            }
            // Closing the pipe early (remaining == 0) makes ffmpeg stop with a write error
        } finally {
            FFmpegKitConfig.closeFFmpegPipe(pipe);
        }
        // ffmpeg produced fewer samples than ffprobe reported: pad so Content-Length stays true
        java.util.Arrays.fill(buffer, (byte) 0);
        while (remaining > 0) {
            int n = (int) Math.min(buffer.length, remaining);
            sink.write(buffer, 0, n);
            remaining -= n;
        }
    }
}

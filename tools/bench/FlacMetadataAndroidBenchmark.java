import android.os.Debug;
import io.nayuki.flac.common.StreamInfo;
import io.nayuki.flac.decode.FlacDecoder;
import io.nayuki.flac.encode.BitOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Locale;

/** Isolated ART metadata probe, not the MusicMate process or an audio playback benchmark. */
public final class FlacMetadataAndroidBenchmark {
    private static volatile long consumed;

    private static long stat(String name) {
        String value = Debug.getRuntimeStat("art.gc." + name);
        if (value == null) throw new AssertionError("Runtime stat unavailable: " + name);
        return Long.parseLong(value);
    }

    private static long scan(File file) throws Exception {
        try (FlacDecoder decoder = new FlacDecoder(file)) {
            while (decoder.readAndHandleMetadataBlock() != null) { }
            if (decoder.streamInfo.numSamples != 96000 || decoder.streamInfo.sampleDepth != 24)
                throw new AssertionError("STREAMINFO changed");
            return decoder.streamInfo.numSamples;
        }
    }

    public static void main(String[] args) throws Exception {
        File file = Files.createTempFile(new File(args[1]).toPath(), "metadata-", ".flac").toFile();
        try {
            try (BitOutputStream bits = new BitOutputStream(Files.newOutputStream(file.toPath()))) {
                bits.writeInt(32, 0x664C6143);
                StreamInfo info = new StreamInfo();
                info.minBlockSize = info.maxBlockSize = 4096;
                info.sampleRate = 96000;
                info.numChannels = 2;
                info.sampleDepth = 24;
                info.numSamples = 96000;
                info.write(true, bits);
            }
            Field workspace = FlacDecoder.class.getDeclaredField("frameDec");
            workspace.setAccessible(true);
            try (FlacDecoder decoder = new FlacDecoder(file)) {
                while (decoder.readAndHandleMetadataBlock() != null) { }
                boolean eager = workspace.get(decoder) != null;
                if (eager != args[0].startsWith("before")) throw new AssertionError("Wrong decoder variant");
            }
            for (int i = 0; i < 64; i++) consumed = scan(file);
            for (int sample = 0; sample < 3; sample++) {
                int operations = 128;
                long gcBefore = stat("gc-count"), gcTimeBefore = stat("gc-time");
                long allocated = stat("bytes-allocated"), start = System.nanoTime(), checksum = 0;
                for (int i = 0; i < operations; i++) checksum += scan(file);
                long elapsed = System.nanoTime() - start;
                allocated = stat("bytes-allocated") - allocated;
                long gc = stat("gc-count") - gcBefore, gcTime = stat("gc-time") - gcTimeBefore;
                consumed = checksum;
                System.out.printf(Locale.ROOT,
                        "{\"label\":\"%s\",\"sample\":%d,\"operations\":%d,\"bytes_per_op\":%.3f,"
                        + "\"nanos_per_op\":%.3f,\"gc_count_delta\":%d,\"gc_time_ms_delta\":%d,\"max_heap_bytes\":%d}%n",
                        args[0], sample, operations, (double) allocated / operations, (double) elapsed / operations,
                        gc, gcTime, Runtime.getRuntime().maxMemory());
            }
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }
}

import com.sun.management.ThreadMXBean;
import io.nayuki.flac.common.StreamInfo;
import io.nayuki.flac.decode.FlacDecoder;
import io.nayuki.flac.decode.FlacLowLevelInput;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;

/** Host metadata micro test: synthetic valid metadata, no audio frames or audio decoding. */
public final class FlacMetadataSkipBenchmark {
    private static final int WARMUPS = 64;
    private static final int SAMPLES = 3;
    private static final int OPERATIONS = 128;
    private static volatile long consumed;
    private final File file;
    private final long length;
    private final Method read;
    private final Method legacy;
    private final boolean streaming;
    private final Field input;
    private final Field metadataEnd;

    private FlacMetadataSkipBenchmark(File file, boolean streaming) throws Exception {
        this.file = file;
        length = file.length();
        this.streaming = streaming;
        legacy = FlacDecoder.class.getMethod("readAndHandleMetadataBlock");
        read = streaming ? FlacDecoder.class.getMethod("readMetadataForAudio") : legacy;
        input = FlacDecoder.class.getDeclaredField("input");
        input.setAccessible(true);
        metadataEnd = FlacDecoder.class.getDeclaredField("metadataEndPos");
        metadataEnd.setAccessible(true);
    }

    private long scan(boolean preflight) throws Exception {
        try (FlacDecoder decoder = new FlacDecoder(file)) {
            if (streaming) read.invoke(decoder);
            else while (read.invoke(decoder) != null) { }
            StreamInfo info = decoder.streamInfo;
            if (info == null || info.minBlockSize != 4096 || info.maxBlockSize != 4096
                    || info.minFrameSize != 0 || info.maxFrameSize != 0
                    || info.sampleRate != 96000 || info.numChannels != 2
                    || info.sampleDepth != 24 || info.numSamples != 96000
                    || !Arrays.equals(info.md5Hash, ZERO_MD5) || decoder.seekTable != null)
                throw new AssertionError("STREAMINFO changed");
            FlacLowLevelInput source = (FlacLowLevelInput) input.get(decoder);
            if (metadataEnd.getLong(decoder) != length || source.getLength() != length
                    || source.getPosition() != length || source.getBitPosition() != 0
                    || source.readByte() != -1)
                throw new AssertionError("Metadata did not finish exactly at EOF");
            if (preflight) {
                if (legacy.invoke(decoder) != null)
                    throw new AssertionError("Metadata completion not retained");
                if (streaming) read.invoke(decoder);
                if (source.getPosition() != length)
                    throw new AssertionError("Repeated metadata read moved input");
            }
            return info.numSamples + source.getPosition();
        }
    }

    private static final byte[] ZERO_MD5 = new byte[16];

    private static String json(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4)
            throw new IllegalArgumentException("LABEL FIXTURE legacy|streaming EXPECTED_CLASS_ORIGIN");
        File origin = new File(FlacDecoder.class.getProtectionDomain().getCodeSource()
                .getLocation().toURI()).getCanonicalFile();
        if (!origin.equals(new File(args[3]).getCanonicalFile()))
            throw new AssertionError("Wrong decoder class origin: " + origin);
        boolean streaming;
        if (args[2].equals("streaming")) streaming = true;
        else if (args[2].equals("legacy")) streaming = false;
        else throw new IllegalArgumentException("Unknown metadata API: " + args[2]);
        FlacMetadataSkipBenchmark benchmark = new FlacMetadataSkipBenchmark(
                new File(args[1]).getCanonicalFile(), streaming);
        ThreadMXBean allocation = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!allocation.isThreadAllocatedMemorySupported())
            throw new AssertionError("Allocation counter unavailable");
        allocation.setThreadAllocatedMemoryEnabled(true);
        consumed = benchmark.scan(true);
        for (int i = 0; i < WARMUPS; i++) consumed = benchmark.scan(false);
        long thread = Thread.currentThread().getId();
        for (int sample = 0; sample < SAMPLES; sample++) {
            long allocated = allocation.getThreadAllocatedBytes(thread);
            long start = System.nanoTime(), checksum = 0;
            for (int i = 0; i < OPERATIONS; i++) checksum += benchmark.scan(false);
            long elapsed = System.nanoTime() - start;
            allocated = allocation.getThreadAllocatedBytes(thread) - allocated;
            consumed = checksum;
            System.out.printf(Locale.ROOT,
                    "{\"label\":%s,\"fixture\":%s,\"api\":%s,\"decoder_origin\":%s,"
                    + "\"fixture_bytes\":%d,\"warmups\":%d,\"sample\":%d,\"operations\":%d,"
                    + "\"allocated_bytes\":%d,\"elapsed_nanos\":%d,"
                    + "\"bytes_per_op\":%.3f,\"nanos_per_op\":%.3f,\"checksum\":%d}%n",
                    json(args[0]), json(benchmark.file.getName()), json(args[2]),
                    json(origin.toString()), benchmark.length, WARMUPS, sample, OPERATIONS,
                    allocated, elapsed, (double) allocated / OPERATIONS,
                    (double) elapsed / OPERATIONS, checksum);
        }
    }
}

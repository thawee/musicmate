import com.sun.management.ThreadMXBean;
import io.nayuki.flac.common.StreamInfo;
import io.nayuki.flac.decode.FlacDecoder;
import io.nayuki.flac.encode.BitOutputStream;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.util.Locale;

/** Metadata-only fixture: valid STREAMINFO with no audio frames; not an audio throughput test. */
public final class FlacMetadataBenchmark {
    private static volatile long consumed;

    private static long scan(File file) throws Exception {
        try (FlacDecoder decoder = new FlacDecoder(file)) {
            while (decoder.readAndHandleMetadataBlock() != null) { }
            if (decoder.streamInfo.numSamples != 96000 || decoder.streamInfo.sampleDepth != 24)
                throw new AssertionError("STREAMINFO changed");
            return decoder.streamInfo.numSamples;
        }
    }

    public static void main(String[] args) throws Exception {
        if (!new File(FlacDecoder.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .getCanonicalFile().equals(new File(args[1]).getCanonicalFile()))
            throw new AssertionError("Wrong decoder class origin");
        File file = Files.createTempFile("sonic-flac-metadata-", ".flac").toFile();
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
            ThreadMXBean allocation = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            if (!allocation.isThreadAllocatedMemorySupported()) throw new AssertionError("Allocation counter unavailable");
            allocation.setThreadAllocatedMemoryEnabled(true);
            for (int i = 0; i < 64; i++) consumed = scan(file);
            for (int sample = 0; sample < 3; sample++) {
                int operations = 128;
                long thread = Thread.currentThread().getId();
                long allocated = allocation.getThreadAllocatedBytes(thread);
                long start = System.nanoTime(), checksum = 0;
                for (int i = 0; i < operations; i++) checksum += scan(file);
                long elapsed = System.nanoTime() - start;
                allocated = allocation.getThreadAllocatedBytes(thread) - allocated;
                consumed = checksum;
                System.out.printf(Locale.ROOT,
                        "{\"label\":\"%s\",\"sample\":%d,\"operations\":%d,\"bytes_per_op\":%.3f,\"nanos_per_op\":%.3f}%n",
                        args[0], sample, operations, (double) allocated / operations, (double) elapsed / operations);
            }
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }
}

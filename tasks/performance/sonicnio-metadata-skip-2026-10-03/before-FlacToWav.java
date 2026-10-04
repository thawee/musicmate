package apincer.music.core.codec;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import io.nayuki.flac.common.StreamInfo;
import io.nayuki.flac.decode.FlacDecoder;

/**
 * A FLAC file presented as a WAV file (PCM, the FLAC's own sample rate and bit depth: lossless),
 * for TVs that cannot play FLAC. The WAV is never stored: {@link #write} decodes any byte range
 * of it on demand with the pure-Java decoder, seeking sample-accurately, so byte-range and time
 * seeks work. The WAV data is exactly the samples the FLAC's STREAMINFO MD5 describes.
 */
public final class FlacToWav {
    public static final int HEADER_SIZE = 44;

    /** Receives converted bytes. */
    public interface ByteSink {
        void write(byte[] data, int offset, int length) throws IOException;
    }

    public final int sampleRate;
    public final int channels;
    public final int bitsPerSample;   // WAV container: the FLAC depth rounded up to whole bytes
    public final long numSamples;     // per channel
    private final int sampleDepth;    // FLAC depth
    private final int frameSize;      // bytes per sample frame (all channels)

    private FlacToWav(StreamInfo info) {
        this.sampleRate = info.sampleRate;
        this.channels = info.numChannels;
        this.sampleDepth = info.sampleDepth;
        this.bitsPerSample = (info.sampleDepth + 7) / 8 * 8;
        this.numSamples = info.numSamples;
        this.frameSize = channels * bitsPerSample / 8;
    }

    /** Reads the FLAC's STREAMINFO; throws if the file is not FLAC or its length is unknown. */
    public static FlacToWav open(File flac) throws IOException {
        try (FlacDecoder decoder = new FlacDecoder(flac)) {
            while (decoder.readAndHandleMetadataBlock() != null) {
                // metadata only
            }
            StreamInfo info = decoder.streamInfo;
            if (info == null || info.numSamples <= 0) throw new IOException("FLAC length unknown: " + flac);
            return new FlacToWav(info);
        }
    }

    /** Size of the whole WAV file in bytes. */
    public long wavLength() {
        return HEADER_SIZE + numSamples * frameSize;
    }

    /** Byte position in the WAV of the sample frame at {@code seconds}. */
    public long byteAtSeconds(double seconds) {
        long frame = Math.min(numSamples, Math.max(0, (long) (seconds * sampleRate)));
        return HEADER_SIZE + frame * frameSize;
    }

    /** The 44-byte canonical WAV header (WAVE_FORMAT_PCM). */
    public byte[] header() {
        return wavHeader(sampleRate, channels, bitsPerSample, numSamples * frameSize);
    }

    /** A 44-byte WAVE_FORMAT_PCM header; sizes are capped at 4 GB, as the format requires. */
    static byte[] wavHeader(int sampleRate, int channels, int bitsPerSample, long dataLength) {
        int frameSize = channels * bitsPerSample / 8;
        ByteBuffer h = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        h.put(new byte[]{'R', 'I', 'F', 'F'}).putInt((int) Math.min(0xFFFFFFFFL, 36 + dataLength));
        h.put(new byte[]{'W', 'A', 'V', 'E', 'f', 'm', 't', ' '}).putInt(16);
        h.putShort((short) 1).putShort((short) channels).putInt(sampleRate)
                .putInt(sampleRate * frameSize).putShort((short) frameSize).putShort((short) bitsPerSample);
        h.put(new byte[]{'d', 'a', 't', 'a'}).putInt((int) Math.min(0xFFFFFFFFL, dataLength));
        return h.array();
    }

    /** Writes bytes {@code from}..{@code to} (inclusive) of the WAV image of {@code flac} to {@code sink}. */
    public void write(File flac, long from, long to, ByteSink sink) throws IOException {
        to = Math.min(to, wavLength() - 1);
        if (from > to) return;
        if (from < HEADER_SIZE) {
            byte[] header = header();
            int end = (int) Math.min(to, HEADER_SIZE - 1);
            sink.write(header, (int) from, end - (int) from + 1);
            if (to < HEADER_SIZE) return;
            from = HEADER_SIZE;
        }
        long dataFrom = from - HEADER_SIZE;
        long remaining = to - from + 1;
        long frame = dataFrom / frameSize;
        int skip = (int) (dataFrom % frameSize); // start inside a frame

        int bytesPerSample = bitsPerSample / 8;
        int shift = bitsPerSample - sampleDepth; // left-justify 12/20-bit samples in their container
        byte[] out = new byte[64 * 1024];
        int outLength = 0;
        int[][] samples = new int[channels][65536];
        try (FlacDecoder decoder = new FlacDecoder(flac)) {
            while (decoder.readAndHandleMetadataBlock() != null) {
                // skip to the audio
            }
            int blockSize = frame > 0 ? decoder.seekAndReadAudioBlock(frame, samples, 0)
                    : decoder.readAudioBlock(samples, 0);
            while (remaining > 0) {
                if (blockSize <= 0) {
                    // Fewer samples than STREAMINFO declared: pad with silence to keep Content-Length true
                    if (outLength > 0) {
                        sink.write(out, 0, outLength); // the decoded bytes come first
                        outLength = 0;
                    }
                    java.util.Arrays.fill(out, (byte) 0);
                    while (remaining > 0) {
                        int n = (int) Math.min(out.length, remaining);
                        sink.write(out, 0, n);
                        remaining -= n;
                    }
                    return;
                }
                for (int i = 0; i < blockSize && remaining > 0; i++) {
                    for (int ch = 0; ch < channels; ch++) {
                        int value = samples[ch][i] << shift;
                        for (int b = 0; b < bytesPerSample; b++) {
                            if (skip > 0) {
                                skip--;
                                continue;
                            }
                            if (remaining == 0) break;
                            out[outLength++] = (byte) (value >> (8 * b));
                            remaining--;
                            if (outLength == out.length) {
                                sink.write(out, 0, outLength);
                                outLength = 0;
                            }
                        }
                    }
                }
                blockSize = remaining > 0 ? decoder.readAudioBlock(samples, 0) : 0;
            }
            if (outLength > 0) sink.write(out, 0, outLength);
        }
    }
}

package apincer.android.mmate.codec;

import static apincer.android.mmate.Constants.MEDIA_ENC_AIFF;
import static apincer.android.mmate.Constants.MEDIA_ENC_ALAC;
import static apincer.android.mmate.Constants.MEDIA_ENC_FLAC;
import static apincer.android.mmate.Constants.MEDIA_ENC_WAVE;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import org.jcodec.codecs.wav.WavDemuxer;
import org.jcodec.common.AudioFormat;
import org.jcodec.common.DemuxerTrack;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Packet;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import apincer.android.mmate.repository.MusicTag;
import io.nayuki.flac.common.StreamInfo;
import io.nayuki.flac.decode.FlacDecoder;

public class AudioDecoder {
    private static final String TAG = "AudioDecoder";

    /**
     * Decode audio file to PCM bytes using JCodec
     *
     * @param tag           Path to audio file
     * @param maxDurationSeconds Maximum duration to decode in seconds
     * @return PCM audio bytes
     * @throws IOException if decoding fails
     */
    public static byte[] decodeAudio(MusicTag tag, int maxDurationSeconds) throws IOException {
        String extension = tag.getAudioEncoding().toUpperCase();

        return switch (extension) {
           // case MEDIA_ENC_WAVE -> decodeWav(tag.getPath(), maxDurationSeconds);
            case MEDIA_ENC_ALAC -> decodeAlac(tag.getPath(), maxDurationSeconds);
            case MEDIA_ENC_FLAC -> decodeFlac(tag.getPath(), maxDurationSeconds);
            case MEDIA_ENC_AIFF -> decodeAiff(tag.getPath(), maxDurationSeconds);
            default -> decodeAndroid(tag.getPath(), maxDurationSeconds);
        };
    }

    public static byte[] decodeAlac(String filePath, int maxDurationSeconds) throws IOException {
        // Create JCodec demuxer for ALAC decoding
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("File not found: " + filePath);
        }

        DemuxerTrack audioTrack = null;
        org.jcodec.containers.mp4.demuxer.MP4Demuxer demuxer = null;

        try {
            // Get MP4 demuxer for ALAC (ALAC is typically in MP4/M4A container)
            demuxer = org.jcodec.containers.mp4.demuxer.MP4Demuxer.createMP4Demuxer(
                    org.jcodec.common.io.NIOUtils.readableChannel(file));

            // Find audio track
            audioTrack = demuxer.getAudioTracks().get(0);
            if (audioTrack == null) {
                throw new IOException("No audio track found in file.");
            }

            // Get audio format information
            org.jcodec.common.AudioFormat format = audioTrack.getMeta().getAudioCodecMeta().getFormat();
            int sampleRate = format.getSampleRate();
            int channels = format.getChannels();
            int bytesPerSample = (format.getSampleSizeInBits() + 7) / 8;

            // Calculate buffer size based on duration
            int bufferSize = sampleRate * channels * bytesPerSample * maxDurationSeconds;
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(bufferSize);

            // Decode frames
            Packet packet;
            int totalBytesRead = 0;
            while ((packet = audioTrack.nextFrame()) != null && totalBytesRead < bufferSize) {
                ByteBuffer frame = packet.getData();
                int frameSize = frame.remaining();

                if (frameSize > 0) {
                    byte[] frameData = new byte[frameSize];
                    frame.get(frameData);
                    outputStream.write(frameData);
                    totalBytesRead += frameSize;
                }

                // Stop if we've decoded enough audio data
                if (totalBytesRead >= bufferSize) {
                    break;
                }
            }

            return outputStream.toByteArray();
        } finally {
            // Close resources
            if (demuxer != null) {
                org.jcodec.common.io.NIOUtils.closeQuietly(demuxer);
            }
        }
    }

    public static byte[] decodeAndroid(String audioFile, int durationInSeconds) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(audioFile);

        MediaFormat format = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime!= null && mime.startsWith("audio/")) {
                extractor.selectTrack(i);
                break;
            }
        }

        if (format == null) {
            throw new IOException("No audio track found in file.");
        }

        MediaCodec codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
        codec.configure(format, null, null, 0);
        codec.start();

        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int bitPerSample =  getBitsPerSample(format);
        int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int bufferSize = sampleRate * channels * (bitPerSample / 8) * durationInSeconds;

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        boolean isEOS = false;

        while (!isEOS) {
            int inIndex = codec.dequeueInputBuffer(10000);
            if (inIndex >= 0) {
                ByteBuffer buffer = codec.getInputBuffer(inIndex);
                if (buffer != null) {
                    int sampleSize = extractor.readSampleData(buffer, 0);
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isEOS = true;
                    } else {
                        codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                }
            }

            int outIndex = codec.dequeueOutputBuffer(info, 10000);
            while (outIndex >= 0) {
                ByteBuffer buffer = codec.getOutputBuffer(outIndex);
                if (buffer != null) {
                    byte[] chunk = new byte[info.size];
                    buffer.get(chunk);
                    buffer.clear();
                    byteArrayOutputStream.write(chunk);
                }
                codec.releaseOutputBuffer(outIndex, false);
                outIndex = codec.dequeueOutputBuffer(info, 0);
            }
            if (byteArrayOutputStream.size() > bufferSize) break;
        }

        codec.stop();
        codec.release();
        extractor.release();

        return byteArrayOutputStream.toByteArray();
    }


    private static byte[] decodeWav(String filePath, int maxDurationSeconds) throws IOException {
        SeekableByteChannel ch = null;
        try {
            ch = NIOUtils.readableChannel(new File(filePath));
            WavDemuxer demuxer = new WavDemuxer(ch);
            AudioFormat format = demuxer.getMeta().getAudioCodecMeta().getFormat();

            int bytesPerSecond = format.getSampleRate() * format.getChannels() * (format.getSampleSizeInBits() / 8);
            int maxBytes = bytesPerSecond * maxDurationSeconds;

            ByteArrayOutputStream baos = new ByteArrayOutputStream(maxBytes);
            ByteBuffer buffer = ByteBuffer.allocate(8192);

            int totalRead = 0;
            while (totalRead < maxBytes) {
                buffer.clear();
                Packet frame = demuxer.nextFrame();
                if (frame == null) break;
                int read = frame.getData().remaining();
                if (read <= 0) break;

                buffer.flip();
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                baos.write(data);

                totalRead += read;
            }

            return baos.toByteArray();
        } finally {
            NIOUtils.closeQuietly(ch);
        }
    }

    public static byte[] decodeFlac(String filePath, int maxDurationSeconds) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("File not found: " + filePath);
        }

        try (FlacDecoder decoder = new FlacDecoder(file)) {
            // Create a FlacDecoder instance

            // Handle metadata header blocks
            while (decoder.readAndHandleMetadataBlock() != null);

            // Read the stream info to get audio parameters
            StreamInfo streamInfo = decoder.streamInfo;
            if (streamInfo == null) {
                throw new IOException("Failed to read FLAC stream info from file: " + filePath);
            }

            int sampleRate = streamInfo.sampleRate;
            int channels = streamInfo.numChannels;
            int bitsPerSample = streamInfo.sampleDepth;
            int bytesPerSample = (bitsPerSample + 7) / 8;

            // Calculate buffer size based on max duration
            int bufferSize = sampleRate * channels * bytesPerSample * maxDurationSeconds;
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(bufferSize);

            // Decode audio samples - using 2D array as required by the decoder
            // First dimension: channels, Second dimension: samples
            int[][] samples = new int[channels][65536];  // Buffer for decoded samples
            int totalBytesRead = 0;

            try {
                while (totalBytesRead < bufferSize) {
                    int blockSize = decoder.readAudioBlock(samples, 0);
                    if (blockSize <= 0) break;  // End of stream

                    // Convert samples to bytes and write to output stream
                    // Process each sample across all channels
                    for (int i = 0; i < blockSize; i++) {
                        // Process each channel
                        for (int ch = 0; ch < channels; ch++) {
                            int sample = samples[ch][i];

                            // Write sample as bytes (little-endian)
                            for (int j = 0; j < bytesPerSample; j++) {
                                outputStream.write((sample >> (j * 8)) & 0xFF);
                            }
                        }
                    }

                    totalBytesRead += blockSize * channels * bytesPerSample;
                    if (totalBytesRead >= bufferSize) break;
                }
            } catch (EOFException e) {
                // End of file reached
            }

            return outputStream.toByteArray();
        }
    }


    public static byte[] decodeAiff(String filePath, int maxDurationSeconds) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            // Read AIFF header
            byte[] header = new byte[12];
            if (fis.read(header) != 12) {
                throw new IOException("Invalid AIFF file - too short");
            }

            // Verify "FORM" chunk
            if (!"FORM".equals(new String(header, 0, 4))) {
                throw new IOException("Not an AIFF file - missing FORM chunk");
            }

            // Verify "AIFF" format
            if (!"AIFF".equals(new String(header, 8, 4))) {
                throw new IOException("Not an AIFF file - missing AIFF identifier");
            }

            // Find and process chunks
            int sampleRate = 0;
            int channels = 0;
            int bitDepth = 0;
            long dataOffset = 0;
            int dataSize = 0;

            // Read chunks until we find COMM and SSND
            byte[] chunkHeader = new byte[8];
            while (fis.read(chunkHeader) == 8) {
                String chunkId = new String(chunkHeader, 0, 4);
                int chunkSize = ((chunkHeader[4] & 0xFF) << 24) |
                        ((chunkHeader[5] & 0xFF) << 16) |
                        ((chunkHeader[6] & 0xFF) << 8) |
                        (chunkHeader[7] & 0xFF);

                if ("COMM".equals(chunkId)) {
                    // Parse COMM chunk for format info
                    byte[] commData = new byte[chunkSize];
                    if (fis.read(commData) != chunkSize) {
                        throw new IOException("Incomplete COMM chunk");
                    }

                    channels = ((commData[0] & 0xFF) << 8) | (commData[1] & 0xFF);
                    bitDepth = ((commData[6] & 0xFF) << 8) | (commData[7] & 0xFF);

                    // Parse 80-bit IEEE 754 extended precision float for sample rate
                    // Simplified version - actual implementation would need full 80-bit parsing
                    int exponent = ((commData[8] & 0x7F) << 8) | (commData[9] & 0xFF);
                    sampleRate = (int)Math.pow(2, exponent - 16383);
                }
                else if ("SSND".equals(chunkId)) {
                    // Found sound data
                    dataOffset = fis.getChannel().position();
                    dataSize = chunkSize;

                    // Skip offset and blockSize fields (8 bytes)
                    fis.skip(8);
                    dataOffset += 8;
                    dataSize -= 8;

                    // Break as we now have everything we need
                    break;
                }
                else {
                    // Skip unknown chunk
                    fis.skip(chunkSize);
                }
            }

            if (sampleRate == 0 || channels == 0 || bitDepth == 0 || dataOffset == 0) {
                throw new IOException("Missing required AIFF chunks");
            }

            // Calculate how much data to read
            int bytesPerSample = bitDepth / 8;
            int bytesPerSecond = sampleRate * channels * bytesPerSample;
            int bytesToRead = Math.min(dataSize, bytesPerSecond * maxDurationSeconds);

            // Read PCM data
            fis.getChannel().position(dataOffset);
            byte[] audioData = new byte[bytesToRead];
            int bytesRead = fis.read(audioData);

            // Convert from big-endian (AIFF) to little-endian (PCM) if needed
            if (bytesPerSample > 1) {
                for (int i = 0; i < bytesRead; i += bytesPerSample) {
                    for (int j = 0; j < bytesPerSample / 2; j++) {
                        byte temp = audioData[i + j];
                        audioData[i + j] = audioData[i + bytesPerSample - 1 - j];
                        audioData[i + bytesPerSample - 1 - j] = temp;
                    }
                }
            }

            return audioData;
        }
    }

    public static int getBitsPerSample(MediaFormat format) {
        try {
            return format.getInteger("bits-per-sample");
        }catch (Exception ex) {
            int pcmEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT);
            return switch (pcmEncoding) {
                case android.media.AudioFormat.ENCODING_PCM_8BIT -> 8;
                case android.media.AudioFormat.ENCODING_PCM_16BIT -> 16;
                case android.media.AudioFormat.ENCODING_PCM_FLOAT -> 32; // Float typically uses 32 bits
                case android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24;
                case android.media.AudioFormat.ENCODING_PCM_32BIT -> 32;
                default ->
                        throw new IllegalArgumentException("Unsupported PCM encoding: " + pcmEncoding);
            };
        }
    }
}

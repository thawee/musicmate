package apincer.android.mmate.repository;

import static java.util.Arrays.sort;
import static apincer.android.mmate.Constants.IND_RESAMPLED_INVALID;
import static apincer.android.mmate.Constants.IND_RESAMPLED_NONE;
import static apincer.android.mmate.Constants.IND_UPSCALED_INVALID;
import static apincer.android.mmate.Constants.IND_UPSCALED_NONE;
import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.toLong;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Comparator;

import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mqaidentifier.NativeLib;

public class MusicAnalyser {
    private static final String TAG = "MusicAnalyser";
    public static final String NOT_FOUND = "NF";
    public static final String NOT_SCAN = "NS";

    private static final double BLOCKSIZE_SECONDS = 3; // Example value
    private static final int MIN_BLOCK_COUNT = 1; // Example value
    private static final double UPMOST_BLOCKS_RATIO = 0.2; // Example value
    private static final int NTH_HIGHEST_PEAK = 2; // Example value

    public static final double MAX_DR_8BIT = 49.8;
    public static final double MAX_DR_16BIT = 96.33;
    public static final double MAX_DR_24BIT = 144.49;

    public static final long MQA_MAGIC_WORD = 0xbe0498c88L;

    public double getDynamicRange() {
        return dynamicRange;
    }

    public String getUpscaled() {
        return upscaled;
    }

    public String getResampled() {
        return resampled;
    }

    private double dynamicRange = 0.0;
    private double dynamicRangeScore = 0.0;
    private String upscaled = IND_UPSCALED_NONE;
    private String resampled = IND_RESAMPLED_NONE;

    public boolean isMQA() {
        return isMQA;
    }

    public boolean isMQAStudio() {
        return isMQAStudio;
    }

    public long getOriginalSampleRate() {
        return originalSampleRate;
    }

    private boolean isMQA = false;
    private boolean isMQAStudio = false;
    private long originalSampleRate;

    public static boolean analystFull(MusicTag tag) {
        MusicAnalyser analyser = new MusicAnalyser();
        analyser.analyst(tag);
        tag.setDynamicRange(analyser.getDynamicRange());
        tag.setDynamicRangeScore(analyser.getDynamicRangeScore());
        if (MusicTagUtils.isFLACFile(tag)) {
            analyser.detectMQANative(tag);
            if(analyser.isMQAStudio) {
                tag.setMqaInd("MQA Studio");
            }else if(analyser.isMQA) {
                tag.setMqaInd("MQA");
            }else {
                tag.setMqaInd(NOT_FOUND);
            }
            tag.setMqaSampleRate(analyser.getOriginalSampleRate());
        }
        return true;
    }

    private void detectMQANative(MusicTag tag) {

        if(!NOT_SCAN.equals(tag.getMqaInd())) return; //prevent re scan
        try {
            NativeLib lib = new NativeLib();
            String mqaInfo = StringUtils.trimToEmpty(lib.getMQAInfo(tag.getPath()));
            // MQA Studio|96000
            // MQA|96000
            if(!isEmpty(mqaInfo) && mqaInfo.contains("|")) {
                String[] tags = mqaInfo.split("\\|", -1);
               // tag.setMqaInd(trimToEmpty(tags[0]));
               // tag.setMqaSampleRate(toLong(tags[1]));
                isMQA = true;
                originalSampleRate = toLong(tags[1]);
                if(tags[0].contains("Studio")) {
                    isMQAStudio = true;
                }
                //  tag.setMqaScanned(true);
            }else {
                isMQA = false;
                isMQAStudio = false;
              //  tag.setMqaInd(NOT_FOUND);
                //   tag.setMqaScanned(true);
            }
        }catch (Exception ex) {
            isMQA = false;
            isMQAStudio = false;
           // tag.setMqaInd(NOT_FOUND);
            // tag.setMqaScanned(true);
            Log.e(TAG, "detectMQA", ex);
        }
    }

    public boolean analyst(MusicTag tag) {
        int durationInSeconds = 30; //20;
            try {
                byte[] audioData = getAudioBytes(tag.getPath(), durationInSeconds);
               // int[][] samples = convertToSamples(audioData, MusicTagUtils.getChannels(tag), tag.audioBitsDepth);
                        //  double a = calculateDynamicRange(samples, (int) tag.getAudioSampleRate());
                //calculateDynamicRange(samples);
                dynamicRange = calculateDynamicRange(audioData, tag.audioBitsDepth);

                //dynamicRange(samples, (int)tag.getAudioSampleRate(), samples[0].length);
                //calculateDynamicRangeScore(this.dynamicRange, getMaxDynamicRange(tag.getAudioBitsDepth()));
               // calculateDRScore(samples, durationInSeconds, (int) tag.getAudioSampleRate(), 2);
               // upscaled = checkUpscaled(samples) ? IND_UPSCALED_BAD : IND_UPSCALED_GOOD;
               // resampled = checkResampled(samples, tag.getAudioSampleRate()) ? IND_RESAMPLED_BAD : IND_RESAMPLED_GOOD;

             //   byte[] audioData = getAudioBytes(tag.getPath(), durationInSeconds);
              //  double dr = calculateDynamicRange(audioData, tag.getAudioBitsDepth());
              //  dynamicRangeScore = calculateDynamicRangeScore(audioData, tag.getAudioBitsDepth());
                int windowSizeMs = 50; // Window size for RMS calculation in milliseconds
                double noiseFloor = -60; // Noise floor in dB
                dynamicRangeScore = calculateDynamicRangeMeter(audioData, tag.getAudioBitsDepth(), (int) tag.getAudioSampleRate(), windowSizeMs, noiseFloor);

               // System.out.println("Dynamic Range: " + dr + " dB");
                //System.out.println("DR: "+getDynamicRange()+", New DR:"+dr);
                //checkMQA(audioData, tag.getAudioBitsDepth(), tag.getAudioSampleRate());
               // boolean mqa = detectMQA(audioData, tag.getAudioBitsDepth());
               // System.out.println("MQA:" + mqa);
                System.out.println("DR: " + getDynamicRange() + ", DRS: " + getDynamicRangeScore() + ", MQA: " + isMQA + ",  MQA Studio: " + isMQAStudio + ", OriginalSampleRate: " + getOriginalSampleRate());
                return true;
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "analyst: " + e.getMessage());
                upscaled = IND_UPSCALED_INVALID;
                resampled = IND_RESAMPLED_INVALID;
                // dynamicRangeScore = 0.00;
                // dynamicRange = 0.00;
                return true;
            } catch (OutOfMemoryError e) {
                // Attempt to run garbage collection
                System.gc();
                Log.w(TAG, "analyst: OutOfMemoryError - " + e.getMessage());
                return false;
            } catch (Exception e) {
                //  e.printStackTrace();
                Log.e(TAG, "analyst", e);
                return false;
            }
    }

    public static byte[] getAudioBytes(String audioFile, int durationInSeconds) throws IOException {
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

    public double[][] extractPCM(String inputFilePath, int durationInSeconds) {
        double[][] pcmData = null;
        try {
            MediaExtractor extractor = new MediaExtractor();
            extractor.setDataSource(inputFilePath);
            MediaFormat format = extractor.getTrackFormat(0);
            extractor.selectTrack(0);

            MediaCodec codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format, null, null, 0);
            codec.start();

           // int durationInSeconds = 20;
            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int bitPerSample =  getBitsPerSample(format);
            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int bufferSize = sampleRate * channels * (bitPerSample / 8) * durationInSeconds;
            ByteBuffer pcmBuffer = ByteBuffer.allocate(bufferSize);

            ByteBuffer inputBuffer;
            ByteBuffer outputBuffer;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            while (true) {
                int inputBufferIndex = codec.dequeueInputBuffer(10000);
                if (inputBufferIndex >= 0) {
                    inputBuffer = codec.getInputBuffer(inputBufferIndex);
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        break;
                    } else {
                        codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                }

                int outputBufferIndex = codec.dequeueOutputBuffer(info, 10000);
                if (outputBufferIndex >= 0) {
                    outputBuffer = codec.getOutputBuffer(outputBufferIndex);
                    if (outputBuffer != null) {
                        // Process the output buffer
                        try {
                            pcmBuffer.put(outputBuffer);
                        }catch (Exception ex) {
                            break;
                        }
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false);
                }
            }

            codec.stop();
            codec.release();
            extractor.release();

            pcmBuffer.flip();
            int numSamples = pcmBuffer.remaining() / (2 * channels);
            pcmData = new double[channels][numSamples];

            for (int i = 0; i < numSamples; i++) {
                for (int ch = 0; ch < channels; ch++) {
                   // pcmData[ch][i] = pcmBuffer.getShort() / 32768.0;
                    pcmData[ch][i] = getSampleRate(pcmBuffer.getShort(), bitPerSample);
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }


        return pcmData;
    }

    private boolean checkUpscaled(int[][] samples) {
        int threshold = 256; // Threshold for detecting upscaling (for 16-bit to 24-bit)
        int upscaleCount = 0;
        int totalSamples = 0;

        for (int[] channel : samples) {
            for (int sample : channel) {
                // Check if the sample value is a multiple of the threshold
                if (sample % threshold == 0) {
                    upscaleCount++;
                }
                totalSamples++;
            }
        }

        // If a significant portion of samples are multiples of the threshold, it might be upscaled
        double upscaleRatio = (double) upscaleCount / totalSamples;
        return upscaleRatio ==1 ; //> 0.5; //0.5; // Adjust this ratio based on your criteria
    }

    private double getSampleRate(double sample, int sampleFormat) {
        return switch (sampleFormat) {
            case 8 -> (sample - 0x80) / 128.0; // 8-bit
            case 16 -> sample / 32768.0; // 16-bit
            case 24 -> sample / 8388608.0; // 24-bit
           // case 32 -> sample / 2147483648.0; // 32-bit
            default -> sample;
        };
    }

    public static int getBitsPerSample(MediaFormat format) {
        try {
            return format.getInteger("bits-per-sample");
        }catch (Exception ex) {
            int pcmEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
            return switch (pcmEncoding) {
                case AudioFormat.ENCODING_PCM_8BIT -> 8;
                case AudioFormat.ENCODING_PCM_16BIT -> 16;
                case AudioFormat.ENCODING_PCM_FLOAT -> 32; // Float typically uses 32 bits
                case AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24;
                case AudioFormat.ENCODING_PCM_32BIT -> 32;
                default ->
                        throw new IllegalArgumentException("Unsupported PCM encoding: " + pcmEncoding);
            };
        }
    }

    public double getDynamicRangeScore() {
        return dynamicRangeScore;
    }

    protected void checkMQA2(double[][] samples, int bps, long sampleRate) {
        long buffer = Integer.toUnsignedLong(0);
        long buffer1 = Integer.toUnsignedLong(0);
        long buffer2 = Integer.toUnsignedLong(0);
        final int pos = (int) (bps - Integer.toUnsignedLong(16)); //16; // aim for 16th bit
        int maxSamples = (int) (sampleRate * 4); // Number of samples for 3 seconds

        long unsignedOne = Integer.toUnsignedLong(1);
        long unsignedF = 0xFFFFFFFFFL;

        // c/c++ - vector of array[2]
        // java - double[2][] samples

        int length = samples[0].length;
        maxSamples = Math.min(maxSamples, length);
        double[][] newSamples= flipArray(samples, maxSamples);
       // for(int i=0; i< maxSamples;i++) {
        for (double []sample: newSamples) {
            double ch1Data = sample[0];
            double ch2Data = sample[1];
           // long xorResult = XOR(ch1Data, ch2Data);
           // long bit = (xorResult >>> pos);
          // bit = bit & unsignedOne;
           // long bit1 = (xorResult >>> (pos + 1));
           // long bit2 = (xorResult >>> (pos + 2));
           // System.out.printf("bit: 0x%010X, bit1: 0x%010X, bit2: 0x%010X%n", bit, bit1, bit2);

           // buffer |= bit;
           // buffer1 |= bit1;
           // buffer2 |= bit2;
            buffer |= XOR(ch1Data, ch2Data) >>> pos;
            buffer1 |= XOR(ch1Data, ch2Data) >>> (pos+1);
            buffer2 |= XOR(ch1Data, ch2Data) >>> (pos+2);

          //  System.out.printf("xorResult: 0x%010X, buffer: 0x%010X, buffer1: 0x%010X, buffer2: 0x%010X%n",xorResult, buffer, buffer1, buffer2);

            if (checkMQAMagicWord(buffer, samples[0], pos, 1)) return;
            if (checkMQAMagicWord(buffer1, samples[0], pos, 1)) return;
            if (checkMQAMagicWord(buffer2, samples[0], pos, 1)) return;

            buffer = (buffer << unsignedOne) & unsignedF;
            buffer1 = (buffer1 << unsignedOne) & unsignedF;
            buffer2 = (buffer2 << unsignedOne) & unsignedF;
        }
    }

    private double[][] flipArray(double[][] samples, int maxSamples) {
        double[][] transposed = new double[maxSamples][samples.length];
        for (int i = 0; i < samples.length; i++) {
            for (int j = 0; j < maxSamples; j++) {
                transposed[j][i] = samples[i][j];
            }
        }
        return transposed;
    }

    private long RIGHTSHIF(double value, int shiftAmount) {
        long longValue = Double.doubleToRawLongBits(value);
        return (longValue >> shiftAmount);
    }

    private long XOR(double v, double v1) {
        // Convert double to long using Double.doubleToLongBits
        long aBits = Double.doubleToRawLongBits(v);
        long bBits = Double.doubleToRawLongBits(v1);

        // Perform bitwise XOR
        return aBits ^ bBits;
    }

    protected void checkMQA(byte[] soundData, int bps, long sampleRate) {
        int maxSamples = (int) (sampleRate * 4); // Number of samples for 3 seconds

     //   int length = samples[0].length;
      //  maxSamples = Math.min(maxSamples, length);
       // double[] newSamples= convert2DTo1D(samples, maxSamples);int sampleWidth = bps / 8;

        int sampwidth = bps / 8;
        int[] samples;

        if (sampwidth == 3) {
            samples = iterI24AsI32(soundData);
        } else if (sampwidth == 2) {
            samples = iterI16AsI32(soundData);
        } else {
            throw new IllegalArgumentException("Input must be 16- or 24-bit");
        }

        final int MAGIC = 0xbe0498c8;
        boolean foundMagic = false;
        for (int p = 16; p < 24; p++) {
            for (int i = 0; i < samples.length - 1; i += 2) {
                long x = Double.doubleToRawLongBits(samples[i]);
                long y = Double.doubleToRawLongBits(samples[i + 1]);
               // long buffer = ((x ^ y) >>> p & 1);
                long buffer = (((x ^ y) >>> p) & 1);
               // System.out.printf("MAGIC: 0x%010X, buffer: 0x%010X%n", MAGIC, buffer);
                String text = String.format("0x%010X", buffer);
                if(text.contains("0498C8")) {
                    System.out.printf("MQA_MAGIC_WORD: 0x%010X, buffer: 0x%010X%n", MQA_MAGIC_WORD, buffer);
                }
                if (buffer == MQA_MAGIC_WORD) {
                    foundMagic = true;
                    break;
                }
            }
            if (foundMagic) break;
        }

        if (foundMagic) {
            System.out.println("\u001B[1;31m MQA syncword present.  \u001B[0m");
        } else {
            System.out.println("\u001B[1;32m Didn't find an MQA syncword. \u001B[0m");
        }
    }

    public static int twosComplement(int n, int bits) {
        int mask = 1 << (bits - 1);
        return -(n & mask) + (n & ~mask);
    }

    public static int[] iterI24AsI32(byte[] data) {
        int[] result = new int[data.length / 3];
        for (int i = 0; i < data.length; i += 3) {
            if(i+2 >= data.length) break;
            int l = data[i] & 0xFF;
            int h = ((data[i + 1] & 0xFF) << 8) | (data[i + 2] & 0xFF);
            result[i / 3] = twosComplement((h << 8) | l, 24) << 8;
        }
        return result;
    }

    public static int[] iterI16AsI32(byte[] data) {
        int[] result = new int[data.length / 2];
        for (int i = 0; i < data.length; i += 2) {
            if(i+1 >= data.length) break;
            int x = ((data[i + 1] & 0xFF) << 8) | (data[i] & 0xFF);
            result[i / 2] = x << 16;
        }
        return result;
    }

    private boolean checkMQAMagicWord(long buffer, double[] s, int pos, int index) {
        System.out.printf("MQA_MAGIC_WORD: 0x%010X, buffer: 0x%010X%n", MQA_MAGIC_WORD, buffer);
        String text = String.format("0x%010X", buffer);
        if(text.contains("8C88")) {
            System.out.printf("MQA_MAGIC_WORD: 0x%010X, buffer: 0x%010X%n", MQA_MAGIC_WORD, buffer);
        }
        if (buffer == MQA_MAGIC_WORD) { // MQA magic word
            this.isMQA = true;
            // Get Original Sample Rate
            long orsf = 0;
            for (int m = (index+3); m < (index+7); m++) { // TODO: this needs fix (orsf is 5 bits)
                double cur = s[m];
               // int j = ((cur ^ s[index+1]) >> (pos + 1)) & 1;
                long j = RIGHTSHIF(XOR(cur,s[index+1]), pos + 1) & 1;
                orsf |= j << (6 - m);
            }
            this.originalSampleRate = decodeSampleRate(orsf);

            // Get MQA Studio
            long provenance = 0;
            for (int m = (index+29); m < (index+34); m++) {
                double cur = s[m];
                //int j = ((cur ^ s[index+1]) >> (pos + 1)) & 1;
                long j = RIGHTSHIF(XOR(cur,s[index+1]),pos + 1) & 1;
                provenance |= j << (33 - m);
            }
            this.isMQAStudio = provenance > 8;

            // We are done, return true
            return true;
        }
        return false;
    }

    private int decodeSampleRate(long c) {
        /*
         * If LSB is 0 then base is 44100 else 48000
         * 3 MSB need to be rotated and raised to the power of 2 (so 1, 2, 4, 8, ...)
         * output is base * multiplier
         */
        int base = (c & 1) == 0 ? 44100 : 48000;

        int multiplier = 1 << (((c >> 3) & 1) | (((c >> 2) & 1) << 1) | (((c >> 1) & 1) << 2));
        // Double for DSD
        if (multiplier > 16) multiplier *= 2;

        return base * multiplier;
    }

    private double roundUp2(double value, int decimal) {
        BigDecimal bd = new BigDecimal(Double.toString(value));
        bd = bd.setScale(decimal, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private double roundUp(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }

    private static int[] byteArrayToIntArray(byte[] byteArray, int bitDepth) {
        int bytesPerSample = bitDepth / 8;
        int intArrayLength = byteArray.length / bytesPerSample;
        int[] intArray = new int[intArrayLength];
        for (int i = 0; i < intArrayLength; i++) {
            int value = 0;
            for (int j = 0; j < bytesPerSample; j++) {
                value |= (byteArray[i * bytesPerSample + j] & 0xFF) << (8 * j);
            }
            intArray[i] = value;
        }
        return intArray;
    }

    ///////////////

    public static int[][] convertToSamples(byte[] audioData, int numChannels, int sampleSizeInBits) {
        int bytesPerSample = sampleSizeInBits / 8;
        int numSamples = audioData.length / (numChannels * bytesPerSample);
        int[][] samples = new int[numChannels][numSamples];

        for (int i = 0; i < numSamples; i++) {
            for (int channel = 0; channel < numChannels; channel++) {
                int sampleIndex = (i * numChannels + channel) * bytesPerSample;
                int sample = 0;

                for (int byteIndex = 0; byteIndex < bytesPerSample; byteIndex++) {
                    sample |= (audioData[sampleIndex + byteIndex] & 0xFF) << (byteIndex * 8);
                }

                // Handle sign extension for 24-bit samples
                if (sampleSizeInBits == 24 && (sample & 0x800000) != 0) {
                    sample |= 0xFF000000;
                }

                samples[channel][i] = sample;
            }
        }

        return samples;
    }

    ///////
    public static double calculateDynamicRangeMeter(byte[] audioData, int bitDepth, int sampleRate, int windowSizeMs, double noiseFloor) {
        if (audioData == null || audioData.length == 0) {
            throw new IllegalArgumentException("Audio data cannot be null or empty");
        }
        if (bitDepth != 16 && bitDepth != 24) {
            throw new IllegalArgumentException("Unsupported bit depth: " + bitDepth);
        }

        int bytesPerSample = bitDepth / 8;
        int samplesPerWindow = (sampleRate * windowSizeMs) / 1000;
        int totalSamples = audioData.length / bytesPerSample;

        double[] rmsValues = new double[totalSamples / samplesPerWindow];
        int rmsIndex = 0;

        for (int i = 0; i < totalSamples; i += samplesPerWindow) {
            double sum = 0;
            int sampleCount = 0;

            for (int j = 0; j < samplesPerWindow && (i + j) < totalSamples; j++) {
                int sample = 0;

                // Extract sample value based on bit depth
                switch (bitDepth) {
                    case 16:
                        sample = ByteBuffer.wrap(audioData, (i + j) * bytesPerSample, bytesPerSample)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .getShort(); // 16-bit signed value
                        break;
                    case 24:
                        int index = (i + j) * bytesPerSample;
                        sample = ((audioData[index + 2] << 16) | ((audioData[index + 1] & 0xFF) << 8) | (audioData[index] & 0xFF));
                        if ((audioData[index + 2] & 0x80) != 0) { // Sign extension for 24-bit
                            sample |= 0xFF000000;
                        }
                        break;
                }

                sum += sample * sample;
                sampleCount++;
            }
            if(rmsIndex>=rmsValues.length) break;

            if (sampleCount > 0) {
                double rms = Math.sqrt(sum / sampleCount);
                if(Double.isNaN(rms)) continue;

                // convert RMS to decibels (dB)
                double rmsDb = 20 * Math.log10(rms);
                if(rmsDb > noiseFloor) { //Ignore values below the noise floor
                    rmsValues[rmsIndex++] = rmsDb;
                }
            }
        }

        // sort RMS values to exclude extreme outliers
        Arrays.sort(rmsValues, 0, rmsIndex);

        // use 95th percentile and 5th percentile for max and moin RMS
        int lowerIndex = (int)(0.05 * rmsIndex);
        int upperIndex = (int)(0.95 * rmsIndex);

        //Calculate the dynamic range
        double maxRMS = rmsValues[upperIndex];
        double minRMS = rmsValues[lowerIndex];

        // Compute dynamic range meter value
        return maxRMS - minRMS;
    }

    ////////
    public static double calculateDynamicRange(byte[] audioData, int bitDepth) {
        if (bitDepth != 16 && bitDepth != 24) {
            throw new IllegalArgumentException("Unsupported bit depth. Only 16-bit and 24-bit are supported.");
        }

        int sampleCount = bitDepth == 16 ? audioData.length / 2 : audioData.length / 3;
        double[] samples = new double[sampleCount];

        // Convert byte array to samples (16-bit or 24-bit PCM)
        for (int i = 0; i < sampleCount; i++) {
            if (bitDepth == 16) {
                int low = audioData[2 * i] & 0xFF;
                int high = audioData[2 * i + 1] & 0xFF;
                samples[i] = (short) ((high << 8) | low);
            } else if (bitDepth == 24) {
                int low = audioData[3 * i] & 0xFF;
                int mid = audioData[3 * i + 1] & 0xFF;
                int high = audioData[3 * i + 2] & 0xFF;
                samples[i] = (high << 16) | (mid << 8) | low;
                if (samples[i] > 0x7FFFFF) {
                    samples[i] -= 0x1000000; // Convert to signed 24-bit
                }
            }
        }

        // Calculate peak level and noise floor
        double peakLevel = 0;
        double noiseFloor = Double.MAX_VALUE;

        for (double sample : samples) {
            double absSample = Math.abs(sample);
            if (absSample > peakLevel) {
                peakLevel = absSample;
            }
            if (absSample != 0 && absSample < noiseFloor) {
                noiseFloor = absSample;
            }
        }

        // If there are no non-zero samples, the dynamic range is zero
        if (noiseFloor == Double.MAX_VALUE) {
            noiseFloor = 0;
        }

        // Calculate dynamic range in decibels
        double dynamicRange;
        if (noiseFloor == 0) {
            dynamicRange = Double.POSITIVE_INFINITY; // Infinite dynamic range
        } else {
            dynamicRange = 20 * Math.log10(peakLevel / noiseFloor);
        }

        return dynamicRange;
    }

    ///
    public boolean detectMQA(byte[] audioData, int bitDepth) {
        if (bitDepth != 16 && bitDepth != 24) {
            throw new IllegalArgumentException("Unsupported bit depth. Only 16-bit and 24-bit are supported.");
        }

        int sampleCount = bitDepth == 16 ? audioData.length / 2 : audioData.length / 3;

        // Convert byte array to samples (16-bit or 24-bit PCM)
        int [] samples = new int[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            int sample;
            if (bitDepth == 16) {
                int low = audioData[2 * i] & 0xFF;
                int high = audioData[2 * i + 1] & 0xFF;
                sample = (short) ((high << 8) | low);
            } else { // 24-bit
                int low = audioData[3 * i] & 0xFF;
                int mid = audioData[3 * i + 1] & 0xFF;
                int high = audioData[3 * i + 2] & 0xFF;
                sample = (high << 16) | (mid << 8) | low;
                if (sample > 0x7FFFFF) {
                    sample -= 0x1000000; // Convert to signed 24-bit
                }
            }
            samples[i] = sample;
        }

        long buffer = Integer.toUnsignedLong(0);
        long buffer1 = Integer.toUnsignedLong(0);
        long buffer2 = Integer.toUnsignedLong(0);
        final int pos = (int) (bitDepth - Integer.toUnsignedLong(16)); //16; // aim for 16th bit

        long unsignedOne = Integer.toUnsignedLong(1);
        long unsignedF = 0xFFFFFFFFFL;
        for (int i=0; i< sampleCount-1;i++) {
            int ch1Data = samples[i];
            int ch2Data = samples[i+1];
            buffer |= (ch1Data ^ ch2Data) >>> pos;
            buffer1 |= (ch1Data ^ ch2Data) >>> (pos + 1);
            buffer2 |= (ch1Data ^ ch2Data) >>> (pos + 2);

            if (checkMQAMagicWord(buffer, samples, pos, i)) return true;
            if (checkMQAMagicWord(buffer1, samples, pos, i)) return true;
            if (checkMQAMagicWord(buffer2, samples, pos, i)) return true;

            buffer = (buffer << unsignedOne) & unsignedF;
            buffer1 = (buffer1 << unsignedOne) & unsignedF;
            buffer2 = (buffer2 << unsignedOne) & unsignedF;
        }

        return false;
    }

    private boolean checkMQAMagicWord(long buffer, int[] s, int pos, int index) {
       // System.out.printf("MQA_MAGIC_WORD: 0x%010X, buffer: 0x%010X%n", MQA_MAGIC_WORD, buffer);
        String text = String.format("0x%010X", buffer);
        if(text.contains("8C88")) {
            System.out.printf("MQA_MAGIC_WORD: 0x%010X, buffer: 0x%010X%n", MQA_MAGIC_WORD, buffer);
        }
        if (buffer == MQA_MAGIC_WORD) { // MQA magic word
            this.isMQA = true;
            // Get Original Sample Rate
            long orsf = 0;
            for (int m = (index+3); m < (index+7); m++) { // TODO: this needs fix (orsf is 5 bits)
                double cur = s[m];
                // int j = ((cur ^ s[index+1]) >> (pos + 1)) & 1;
                long j = RIGHTSHIF(XOR(cur,s[index+1]), pos + 1) & 1;
                orsf |= j << (6 - m);
            }
            this.originalSampleRate = decodeSampleRate(orsf);

            // Get MQA Studio
            long provenance = 0;
            for (int m = (index+29); m < (index+34); m++) {
                double cur = s[m];
                //int j = ((cur ^ s[index+1]) >> (pos + 1)) & 1;
                long j = RIGHTSHIF(XOR(cur,s[index+1]),pos + 1) & 1;
                provenance |= j << (33 - m);
            }
            this.isMQAStudio = provenance > 8;

            // We are done, return true
            return true;
        }
        return false;
    }
}

package apincer.android.mmate.repository;

import static java.util.Arrays.sort;
import static apincer.android.mmate.Constants.IND_RESAMPLED_BAD;
import static apincer.android.mmate.Constants.IND_RESAMPLED_GOOD;
import static apincer.android.mmate.Constants.IND_RESAMPLED_INVALID;
import static apincer.android.mmate.Constants.IND_RESAMPLED_NONE;
import static apincer.android.mmate.Constants.IND_UPSCALED_BAD;
import static apincer.android.mmate.Constants.IND_UPSCALED_GOOD;
import static apincer.android.mmate.Constants.IND_UPSCALED_INVALID;
import static apincer.android.mmate.Constants.IND_UPSCALED_NONE;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import org.jtransforms.fft.DoubleFFT_1D;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;

public class MusicAnalyser {
    private static final String TAG = "MusicAnalyser";
    // private static final double BLOCKSIZE_SECONDS = 0.2; //3;
   // private static final double UPMOST_BLOCKS_RATIO = 1; //0.2; //0.2;

    private static final double BLOCKSIZE_SECONDS = 3; // Example value
    private static final int MIN_BLOCK_COUNT = 1; // Example value
    private static final double UPMOST_BLOCKS_RATIO = 0.2; // Example value
    private static final int NTH_HIGHEST_PEAK = 2; // Example value
   // private static final double MIN_DURATION = MIN_BLOCK_COUNT * BLOCKSIZE_SECONDS;

    public static final double MAX_DR_8BIT = 49.8;
    public static final double MAX_DR_16BIT = 96.33;
    public static final double MAX_DR_24BIT = 144.49;

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

   // public boolean isSupported(MusicTag tag) {
    //    return true; //MusicTagUtils.isFLACFile(tag);
   // }

    public boolean analyst(MusicTag tag) {
        int durationInSeconds = 20;
        double[][] samples = extractPCM(tag.getPath(), durationInSeconds);

        if(samples != null) {

            // Decode input FLAC file
            //  File file = new File(tag.getPath());

            // try (FlacDecoder dec = new FlacDecoder(file)) {
            //    int duration = 16; // seconds
            //  int[][] samples = getSamples(dec, duration);
            try {
              //  double a = calculateDynamicRange(samples, (int) tag.getAudioSampleRate());
                calculateDynamicRange(samples);
                //dynamicRange(samples, (int)tag.getAudioSampleRate(), samples[0].length);
                //calculateDynamicRangeScore(this.dynamicRange, getMaxDynamicRange(tag.getAudioBitsDepth()));
                calculateDRScore(samples, durationInSeconds, (int) tag.getAudioSampleRate(), 2);
                upscaled = checkUpscaled(samples) ? IND_UPSCALED_BAD : IND_UPSCALED_GOOD;
                resampled = checkResampled(samples, tag.getAudioSampleRate()) ? IND_RESAMPLED_BAD : IND_RESAMPLED_GOOD;

                checkMQA(samples, tag.getAudioBitsDepth(), tag.getAudioSampleRate());
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
        return false;
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

    private double getMaxDynamicRange(int audioBitsDepth) {
        if (audioBitsDepth >= 24) {
            return MAX_DR_24BIT;
        }else if (audioBitsDepth >= 16) {
            return MAX_DR_16BIT;
        }else {
            return  MAX_DR_8BIT;
        }
    }

    /*
    audio data (2D array: channels x samples)
    /*
    /*
    private @NonNull int[][] getSamples(FlacDecoder decoder, int duration) throws IOException {
        // Handle metadata header blocks
        while (decoder.readAndHandleMetadataBlock() != null) ;
        StreamInfo streamInfo = decoder.streamInfo;
        if (streamInfo.sampleDepth % 8 != 0)
            throw new UnsupportedOperationException("Only whole-byte sample depth supported");

        int sampleRate = streamInfo.sampleRate;
        int numSamples = duration * sampleRate; //20 * sampleRate;  // 20 seconds duration

        // Initialize the sample segment array
        int[][] samples = new int[streamInfo.numChannels][numSamples];

        int totalSamples =0;
        while (totalSamples < numSamples) {
            int samplesRead = decoder.readAudioBlock(samples, 0);
            if (samplesRead == 0) break; // End of stream
            totalSamples += samplesRead;
        }

        decoder.close();

        return samples;
    } */

    private boolean checkUpscaled(double[][] samples) {
        int threshold = 256; // Threshold for detecting upscaling (for 16-bit to 24-bit)
        int upscaleCount = 0;
        int totalSamples = 0;

        for (double[] channel : samples) {
            for (double sample : channel) {
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

    private boolean checkResampled(double[][] samples, long newSampleRate) {
        int totalSamples = samples.length;
        double[] fftData = new double[totalSamples];
        DoubleFFT_1D fft = new DoubleFFT_1D(totalSamples);

        // Convert samples to double and perform FFT on the first channel
        System.arraycopy(samples[0], 0, fftData, 0, totalSamples);
        fft.realForward(fftData);

        // Analyze the frequency spectrum
        double maxFrequency = newSampleRate / 2.0;
        double frequencyResolution = newSampleRate / (double) totalSamples;
        boolean resampled = false;

        for (int i = 0; i < totalSamples / 2; i++) {
            double frequency = i * frequencyResolution;
            if (frequency > maxFrequency / 2.0 && fftData[i] > 0.1) { // Threshold for detecting aliasing
                resampled = true;
                break;
            }
        }

        return resampled;
    }

   private void calculateDynamicRange(double[][] samples) {
            double minSample =  Integer.MAX_VALUE;
            double maxSample = Integer.MIN_VALUE;
            boolean samplesProcessed = false;

            for (double[] channelSamples : samples) {
                for (double sample : channelSamples) {
                    samplesProcessed = true;
                    double absSample = Math.abs(sample);
                    absSample = absSample==0?1:absSample;
                    if (absSample < minSample) {
                        minSample = absSample;
                    }
                    if (absSample > maxSample) {
                        maxSample = absSample;
                    }
                }
            }

            if (samplesProcessed) {
                this.dynamicRange = roundUp(20 * Math.log10(maxSample / minSample), 2);
            }
    }

    private void calculateDRScore(double[][] samples, int duration, int sampleRate, int channels) {//} MusicTag tag)   {
       long frames = (long) duration * sampleRate;

        int blockSize = (int) (BLOCKSIZE_SECONDS * sampleRate);
        int totalBlocks = (int) Math.ceil((double) frames / blockSize);

        double[][] blockRms = new double[totalBlocks][channels];
        double[][] blockPeak = new double[totalBlocks][channels];

        analyzeBlockLevels(samples, totalBlocks, blockSize, channels, blockRms, blockPeak, sampleRate);

        double[] peakPressure = calculatePeakPressure(blockPeak, channels);
        double[] pre2 = calculateUpmostBlocksRms(blockRms, totalBlocks, channels);

        double[] drScore = calculateDrScore(peakPressure, pre2, channels);
        updateDynamicRangeScore(drScore);
        /*
       // double[] rmsPressure = new double[channels];
        double[] peakPressure = new double[channels];
        for (int i = 0; i < channels; i++) {
            int finalI = i;
           // rmsPressure[i] = Math.sqrt(Arrays.stream(blockRms).mapToDouble(b -> b[finalI] * b[finalI]).average().orElse(0));
            peakPressure[i] = Arrays.stream(blockPeak).mapToDouble(b -> b[finalI]).max().orElse(0);
        }

        double upmostBlocks = (totalBlocks * UPMOST_BLOCKS_RATIO);
        double[][] upmostBlocksRms = Arrays.copyOfRange(blockRms, (int)(totalBlocks - upmostBlocks), totalBlocks);

        double[] pre2 = new double[channels];
        for (int i = 0; i < channels; i++) {
            int finalI = i;
            double pre0 = Arrays.stream(upmostBlocksRms).mapToDouble(b -> b[finalI] * b[finalI]).sum();
            pre2[i] = Math.sqrt(pre0 / upmostBlocks);
        }

        double[] drScore = new double[channels];
        for (int i = 0; i < channels; i++) {
            drScore[i] = 20 * Math.log10(peakPressure[i] / pre2[i]);
           // System.out.println("dr: "+drScore[i]);
            if(drScore[i] > dynamicRangeScore) {
                dynamicRangeScore = roundUp(drScore[i],0);
            }
        } */
    }

    private double[] calculatePeakPressure(double[][] blockPeak, int channels) {
        double[] peakPressure = new double[channels];
        for (int i = 0; i < channels; i++) {
            int finalI = i;
            peakPressure[i] = Arrays.stream(blockPeak).mapToDouble(b -> b[finalI]).max().orElse(0);
        }
        return peakPressure;
    }

    private double[] calculateUpmostBlocksRms(double[][] blockRms, int totalBlocks, int channels) {
        double upmostBlocks = totalBlocks * UPMOST_BLOCKS_RATIO;
        double[][] upmostBlocksRms = Arrays.copyOfRange(blockRms, (int) (totalBlocks - upmostBlocks), totalBlocks);

        double[] pre2 = new double[channels];
        for (int i = 0; i < channels; i++) {
            int finalI = i;
            double pre0 = Arrays.stream(upmostBlocksRms).mapToDouble(b -> b[finalI] * b[finalI]).sum();
            pre2[i] = Math.sqrt(pre0 / upmostBlocks);
        }
        return pre2;
    }

    private double[] calculateDrScore(double[] peakPressure, double[] pre2, int channels) {
        double[] drScore = new double[channels];
        for (int i = 0; i < channels; i++) {
            drScore[i] = 20 * Math.log10(peakPressure[i] / pre2[i]);
        }
        return drScore;
    }

    private void updateDynamicRangeScore(double[] drScore) {
        for (double score : drScore) {
            if (score > dynamicRangeScore) {
                dynamicRangeScore = roundUp(score, 0);
            }
        }
    }

    @Deprecated
    public void dynamicRange(int[][] data, int sampleRate, int frames) throws Exception {
        int blocksize = (int) Math.round(BLOCKSIZE_SECONDS * sampleRate);
        int totalBlocks = (int) Math.ceil((double) frames / blocksize);
        if (totalBlocks < MIN_BLOCK_COUNT) {
            throw new Exception("File cannot be shorter than " + (MIN_BLOCK_COUNT * BLOCKSIZE_SECONDS) + " seconds");
        }

        double[][][] blockLevels = analyzeBlockLevels(data, totalBlocks, blocksize);
        double[][] blockRms = blockLevels[0];
        double[][] blockPeak = blockLevels[1];
        double rmsPressure = Math.sqrt(Arrays.stream(blockRms).flatMapToDouble(Arrays::stream).map(x -> x * x).average().orElse(0));
        double peakPressure = Arrays.stream(blockPeak).flatMapToDouble(Arrays::stream).max().orElse(0);

        int upmostBlocks = (int) Math.round(totalBlocks * UPMOST_BLOCKS_RATIO);
        double[] upmostBlocksRms = Arrays.stream(blockRms).flatMapToDouble(Arrays::stream).sorted().skip(blockRms.length - upmostBlocks).toArray();
        double pre0 = Arrays.stream(upmostBlocksRms).map(x -> x * x).sum();
        double pre2 = Math.sqrt(pre0 / upmostBlocks);

        double[] drScore = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            double peak = blockPeak[blockPeak.length - NTH_HIGHEST_PEAK][i];
            drScore[i] = pre2 > 0 ? 20 * Math.log10(peak / pre2) : 0.0;
        }
        System.out.println("Done");
    }

    @Deprecated
    public static double calculateDynamicRange(double[][] data, int sampleRate, int period) {
        int BLOCK_SIZE_MS = period * 1000; //3000; // Block size in milliseconds

        int blockSize = (sampleRate * BLOCK_SIZE_MS) / 1000; // Convert block size to samples
        int totalBlocks = (int) Math.ceil((double) data[0].length / blockSize);

        double[][] blockRms = new double[totalBlocks][data.length];
        double[][] blockPeak = new double[totalBlocks][data.length];

        for (int blockIndex = 0; blockIndex < totalBlocks; blockIndex++) {
            int start = blockIndex * blockSize;
            int end = Math.min(start + blockSize, data[0].length);

            for (int ch = 0; ch < data.length; ch++) {
                double[] block = Arrays.copyOfRange(data[ch], start, end);
                blockRms[blockIndex][ch] = calculateRMS(block);
                blockPeak[blockIndex][ch] = calculatePeak(block);
            }
        }

        double[] rmsValues = Arrays.stream(blockRms).flatMapToDouble(Arrays::stream).toArray();
        double[] peakValues = Arrays.stream(blockPeak).flatMapToDouble(Arrays::stream).toArray();

        double rmsMean = Arrays.stream(rmsValues).average().orElse(0);
        double peakMax = Arrays.stream(peakValues).max().orElse(0);

        return 20 * Math.log10(peakMax / rmsMean);
    }

    private static double calculateRMS(double[] block) {
        double sum = 0;
        for (double sample : block) {
            sum += sample * sample;
        }
        return Math.sqrt(sum / block.length);
    }

    private static double calculatePeak(double[] block) {
        return Arrays.stream(block).map(Math::abs).max().orElse(0);
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

    public double[][][] analyzeBlockLevels(int[][] data, int totalBlocks, int blocksize) {
        int channels = data.length;
        double[][] blockRms = new double[totalBlocks][channels];
        double[][] blockPeak = new double[totalBlocks][channels];

        for (int nn = 0; nn < totalBlocks; nn++) {
            int start = nn * blocksize;
            int end = Math.min(start + blocksize, data[0].length);
            int[][] block = new int[channels][end - start];

            for (int ch = 0; ch < channels; ch++) {
                System.arraycopy(data[ch], start, block[ch], 0, end - start);
                double[] interim = Arrays.stream(block[ch]).mapToDouble(x -> 2 * Math.pow(Math.abs(x), 2)).toArray();
                blockRms[nn][ch] = Math.sqrt(Arrays.stream(interim).average().orElse(0));
                blockPeak[nn][ch] = Arrays.stream(block[ch]).map(Math::abs).max().orElse(0);
            }
        }

        // Sort the arrays
        for (int ch = 0; ch < channels; ch++) {
            final int channel = ch;
            sort(blockRms, Comparator.comparingDouble(a -> a[channel]));
            sort(blockPeak, Comparator.comparingDouble(a -> a[channel]));
        }

        return new double[][][]{blockRms, blockPeak};
    }

    private void analyzeBlockLevels(double[][] samples, int totalBlocks, int blocksize, int channels, double[][] blockRms, double[][] blockPeak, int sampleRate)   {
       try {
           for (int i = 0; i < totalBlocks; i++) {
               for (int j = 0; j < channels; j++) {
                   double[] channelData = new double[blocksize];
                   for (int k = 0; k < blocksize; k++) {
                       channelData[k] = getSampleRate(samples[j][k], sampleRate);
                   }
                   blockRms[i][j] = Math.sqrt(Arrays.stream(channelData).map(d -> d * d).average().orElse(0));
                   blockPeak[i][j] = Arrays.stream(channelData).map(Math::abs).max().orElse(0);

                   // Debug statements
                  // System.out.printf("Block %d, Channel %d - RMS: %f, Peak: %f%n", i, j, blockRms[i][j], blockPeak[i][j]);
               }
           }
       }catch (Exception ex) {
           Log.e(TAG, "analyzeBlockLevels", ex);
       }
    }

    public double getDynamicRangeScore() {
        return dynamicRangeScore;
    }

    private void checkMQA(double[][] samples, int bps, long sampleRate) {
        long buffer = 0;
        long buffer1 = 0;
        long buffer2 = 0;
        final int pos = bps - 16; // aim for 16th bit
        int maxSamples = (int) (sampleRate * 3); // Number of samples for 3 seconds
/*
        int sampleCount = 0;
        for (int i = 0; i < channels; i++) {
            for (int j = 0; j < indexLength; j++) {
                transposed[j+i] = samples[i][j];
                if (sampleCount >= maxSamples) {
                    break;
                }
                sampleCount++;
            }
        } */

        double[] transposed = convert2DTo1D(samples, maxSamples);

        for (int i=0;  i< transposed.length-1;i=i+2) {
           // System.out.printf("s["+i+"]: 0x%08X, s["+(i+1)+"]: 0x%08X%n", transposed[i],transposed[i+1]);
               // int xorResult = transposed[i] ^ transposed[i+1];
               double xorResult = XOR(transposed[i], transposed[i+1]);

               // long bit = (xorResult >> pos) & 1;
               // long bit1 = (xorResult >> (pos + 1)) & 1;
               // long bit2 = (xorResult >> (pos + 2)) & 1;
             long bit = RIGHTSHIF(xorResult, pos) & 1;
             long bit1 = RIGHTSHIF(xorResult,(pos + 1)) & 1;
             long bit2 = RIGHTSHIF(xorResult,(pos + 2)) & 1;

            buffer |= bit;
            buffer1 |= bit1;
            buffer2 |= bit2;
            //      System.out.printf("buffer: 0x%010X, buffer1: 0x%010X, buffer2: 0x%010X%n", buffer, buffer1, buffer2);

                if (checkMQAMagicWord(buffer, transposed, pos, i)) return;
                if (checkMQAMagicWord(buffer1, transposed, pos, i)) return;
                if (checkMQAMagicWord(buffer2, transposed, pos, i)) return;

                buffer <<= 1;
                buffer1 <<= 1;
               buffer2 <<= 1;
        }
    }

    private long RIGHTSHIF(double value, int shiftAmount) {
        long longValue = Double.doubleToLongBits(value);
        return (longValue >> shiftAmount);
    }

    private long XOR(double v, double v1) {
        // Convert double to long using Double.doubleToLongBits
        long aBits = Double.doubleToLongBits(v);
        long bBits = Double.doubleToLongBits(v1);

        // Perform bitwise XOR
        return (aBits ^ bBits);
    }

    private double[] convert2DTo1D(double[][] twoDArray, int maxSamples) {
        int chs = twoDArray.length;
        int cols = twoDArray[0].length;
        maxSamples = Math.min(maxSamples, chs * cols);
        double[] oneDArray = new double[maxSamples];

        int index = 0;
        for (double[] doubles : twoDArray) {
            for (int j = 0; j < cols; j++) {
                if (index >= maxSamples) {
                    break;
                }
                oneDArray[index++] = doubles[j];
            }
        }

        return oneDArray;
    }

    private boolean checkMQAMagicWord(long buffer, double[] s, int pos, int index) {
        if (buffer == 0xbe0498c88L) { // MQA magic word
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
}

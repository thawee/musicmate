package apincer.android.mmate.repository;

import static apincer.android.mmate.Constants.IND_RESAMPLED_BAD;
import static apincer.android.mmate.Constants.IND_RESAMPLED_GOOD;
import static apincer.android.mmate.Constants.IND_RESAMPLED_INVALID;
import static apincer.android.mmate.Constants.IND_RESAMPLED_NONE;
import static apincer.android.mmate.Constants.IND_UPSCALED_BAD;
import static apincer.android.mmate.Constants.IND_UPSCALED_GOOD;
import static apincer.android.mmate.Constants.IND_UPSCALED_INVALID;
import static apincer.android.mmate.Constants.IND_UPSCALED_NONE;

import android.util.Log;

import org.jtransforms.fft.DoubleFFT_1D;

import java.io.File;
import java.util.Arrays;

import apincer.android.mmate.utils.MusicTagUtils;
import io.nayuki.flac.common.StreamInfo;
import io.nayuki.flac.decode.DataFormatException;
import io.nayuki.flac.decode.FlacDecoder;

public class MusicAnalyser {
    private static final String TAG = "MusicAnalyser";
    private static final int BLOCKSIZE_SECONDS = 4; //3;
    private static final double UPMOST_BLOCKS_RATIO = 0.2; //0.2;


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

    public boolean analyst(MusicTag tag) {
        if(!MusicTagUtils.isFLACFile(tag)) return  false;

        // Decode input FLAC file
        File file = new File(tag.getPath());
        StreamInfo streamInfo;
        int[][] samples;
        try (FlacDecoder dec = new FlacDecoder(file)) {
            // Handle metadata header blocks
            while (dec.readAndHandleMetadataBlock() != null) ;
            streamInfo = dec.streamInfo;
            if (streamInfo.sampleDepth % 8 != 0)
                throw new UnsupportedOperationException("Only whole-byte sample depth supported");

            // Decode every block
            samples = new int[streamInfo.numChannels][(int) streamInfo.numSamples];
            for (int off = 0; ; ) {
                int len = dec.readAudioBlock(samples, off);
                if (len == 0)
                    break;
                off += len;
            }

            calculateDR(samples);
            calculateDRScore(samples, tag);
            upscaled = isUpscaled(samples)?IND_UPSCALED_BAD:IND_UPSCALED_GOOD;
            // System.out.println("Is the audio upscaled? " + upscaled);

            resampled = isResampled(samples, tag.getAudioSampleRate())?IND_RESAMPLED_BAD:IND_RESAMPLED_GOOD;
            // System.out.println("Is the audio resampled? " + resampled);

            return true;
        }catch (DataFormatException e) {
            Log.e(TAG, "analyst", e);
            upscaled = IND_UPSCALED_INVALID;
            resampled = IND_RESAMPLED_INVALID;
            dynamicRangeScore = -1;
            dynamicRange = -1;
            return true;
        } catch (Exception e) {
          //  e.printStackTrace();
            Log.e(TAG, "analyst", e);
            return false;
        }
    }

    public static boolean isUpscaled(int[][] samples) {
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
        return upscaleRatio > 0.5; // Adjust this ratio based on your criteria
    }

    public static boolean isResampled(int[][] samples, long newSampleRate) {
        int totalSamples = samples.length;
        double[] fftData = new double[totalSamples];
        DoubleFFT_1D fft = new DoubleFFT_1D(totalSamples);

        // Convert samples to double and perform FFT on the first channel
        for (int i = 0; i < totalSamples; i++) {
            fftData[i] = samples[0][i];
        }
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

   private void calculateDR(int[][] samples) {
            int minSample =  Integer.MAX_VALUE;
            int maxSample = Integer.MIN_VALUE;
            boolean samplesProcessed = false;

            for (int[] channelSamples : samples) {
                for (double sample : channelSamples) {
                    samplesProcessed = true;
                    int absSample = (int) Math.abs(sample);
                    if (absSample != 0 && absSample < minSample) {
                        minSample = absSample;
                    }
                    if (absSample > maxSample) {
                        maxSample = absSample;
                    }
                }
            }

            // Print min and max sample values
           // System.out.println("Min Sample: " + minSample);
           // System.out.println("Max Sample: " + maxSample);

            if (samplesProcessed) {
                dynamicRange = 20 * Math.log10((double) maxSample / minSample);
           //     System.out.println("Dynamic Range: " + dynamicRange + " dB");
           // } else {
            //    System.out.println("Error: No valid samples processed or minSample is zero.");
            }
    }

    private void calculateDRScore(int[][] samples, MusicTag tag)   {
        int sampleRate = (int) tag.getAudioSampleRate();
        int channels = 2; //format.getChannels();
        long frames = (long) tag.getAudioDuration() * sampleRate;

        int blockSize = BLOCKSIZE_SECONDS * sampleRate;
        int totalBlocks = (int) Math.ceil((double) frames / blockSize);

        double[][] blockRms = new double[totalBlocks][channels];
        double[][] blockPeak = new double[totalBlocks][channels];

        analyzeBlockLevels(samples, totalBlocks, blockSize, channels, blockRms, blockPeak, sampleRate);

       // double[] rmsPressure = new double[channels];
        double[] peakPressure = new double[channels];
        for (int i = 0; i < channels; i++) {
            int finalI = i;
           // rmsPressure[i] = Math.sqrt(Arrays.stream(blockRms).mapToDouble(b -> b[finalI] * b[finalI]).average().orElse(0));
            peakPressure[i] = Arrays.stream(blockPeak).mapToDouble(b -> b[finalI]).max().orElse(0);
        }

        int upmostBlocks = (int) (totalBlocks * UPMOST_BLOCKS_RATIO);
        double[][] upmostBlocksRms = Arrays.copyOfRange(blockRms, totalBlocks - upmostBlocks, totalBlocks);

        double[] pre2 = new double[channels];
        for (int i = 0; i < channels; i++) {
            int finalI = i;
            double pre0 = Arrays.stream(upmostBlocksRms).mapToDouble(b -> b[finalI] * b[finalI]).sum();
            pre2[i] = Math.sqrt(pre0 / upmostBlocks);
        }

        double[] drScore = new double[channels];
        for (int i = 0; i < channels; i++) {
            drScore[i] = 20 * Math.log10(peakPressure[i] / pre2[i]);
            if(drScore[i] > dynamicRangeScore) {
                dynamicRangeScore = drScore[i];
            }
        }

        //System.out.println("Dynamic Range Score: " + Arrays.toString(drScore));
       // System.out.println("Peak Pressure: " + Arrays.toString(peakPressure));
       // System.out.println("RMS Pressure: " + Arrays.toString(rmsPressure));

    }

    private static double getSample(int sample, int sampleFormat) {
        return switch (sampleFormat) {
            case 8 -> (sample - 0x80) / 128.0; // 8-bit
            case 16 -> sample / 32768.0; // 16-bit
            case 24 -> sample / 8388608.0; // 24-bit
            case 32 -> sample / 2147483648.0; // 32-bit
            default -> sample;
        };
    }

    private static void analyzeBlockLevels(int[][] samples, int totalBlocks, int blocksize, int channels, double[][] blockRms, double[][] blockPeak, int sampleRate)   {
       try {
           for (int i = 0; i < totalBlocks; i++) {
               for (int j = 0; j < channels; j++) {
                   double[] channelData = new double[blocksize];
                   for (int k = 0; k < blocksize; k++) {
                       channelData[k] = getSample(samples[j][k], sampleRate);
                   }
                   blockRms[i][j] = Math.sqrt(Arrays.stream(channelData).map(d -> d * d).average().orElse(0));
                   blockPeak[i][j] = Arrays.stream(channelData).map(Math::abs).max().orElse(0);
               }
           }
       }catch (Exception ex) {
           Log.e(TAG, "analyzeBlockLevels", ex);
       }
    }

    public double getDynamicRangeScore() {
        return dynamicRangeScore;
    }
}

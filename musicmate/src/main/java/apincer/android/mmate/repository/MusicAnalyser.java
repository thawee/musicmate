package apincer.android.mmate.repository;

import static apincer.android.mmate.utils.Utils.runGcIfNeeded;

import android.util.Log;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import apincer.android.mmate.codec.AudioDecoder;
import apincer.android.mmate.utils.MusicTagUtils;

public class MusicAnalyser {
    private static final String TAG = "MusicAnalyser";
    //public static final String NOT_FOUND = "NF";
    //public static final String NOT_SCAN = "NS";
    //Scores > 0.65 strongly indicate upscaled audio (artificially increased from lower resolution)
    public static final double THRESHOLD_UPSCALED = 0.65;
    //A score of 0.70 or higher strongly indicates resampling has occurred
    public static final double THRESHOLD_RESAMPLED = 0.70;

    // Change from String to double for numerical representation
    private double upscaledScore = 0.0;
    private double resampledScore = 0.0;

    public double getDynamicRange() {
        return dynamicRange;
    }

    public double getUpScaledScore() {
        return upscaledScore;
    }

    public double getReSampledScore() {
        return resampledScore;
    }

    private double dynamicRange = 0.0;
    private double dynamicRangeScore = 0.0;

    private int sampleRate;
    private int bitsPerSample;
    private int channels;
    private List<int[]> samples = new ArrayList<>();

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

    public static boolean analyse(MusicTag tag) {
        MusicAnalyser analyser = new MusicAnalyser();
        analyser.doAnalyst(tag);
        tag.setDynamicRange(analyser.getDynamicRange());
        tag.setDynamicRangeScore(analyser.getDynamicRangeScore());
        tag.setMqaSampleRate(analyser.getOriginalSampleRate());
        tag.setResampledScore(analyser.getReSampledScore());
        tag.setUpscaledScore(analyser.getUpScaledScore());
        if(analyser.isMQAStudio) {
            tag.setMqaInd("MQA Studio");
        }else if(analyser.isMQA) {
            tag.setMqaInd("MQA");
        }else if (MusicTagUtils.isHiRes(tag)) {
            tag.setMqaInd("HR");
        }else {
            tag.setMqaInd("SQ");
        }

        return true;
    }

    private void doAnalyst(MusicTag tag) {
        int durationInSeconds = 30; //20;
        this.sampleRate = (int) tag.getAudioSampleRate();
        this.bitsPerSample = tag.getAudioBitsDepth();
        this.channels = 2;
            try {
                byte[] audioData = AudioDecoder.decodeAudio(tag, durationInSeconds);
                dynamicRange = calculateDynamicRange(audioData, tag.audioBitsDepth);

                upscaledScore = detectUpscaledAudio(audioData);
                resampledScore = detectResampledAudio(audioData);

                dynamicRangeScore = calculateDRMeter(audioData);

                if (MusicTagUtils.isFLACFile(tag)) {
                    enhancedMQADetection(audioData);
                }
                Log.i(TAG, tag.getPath());
                Log.i(TAG, "DR: " + getDynamicRange() + "dB, DRS: " + getDynamicRangeScore() + ", UpScaledScore: "+getUpScaledScore()+", ReSampledScore: "+getReSampledScore()+", MQA: " + isMQA() + ", MQA Studio: " + isMQAStudio() + ", OriginalSampleRate: " + getOriginalSampleRate());

                // Only run GC if memory usage is high
                runGcIfNeeded();
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "analyst: " + e.getMessage());
            } catch (OutOfMemoryError e) {
                // Attempt to run garbage collection
                System.gc();
                Log.w(TAG, "analyst: OutOfMemoryError - " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "analyst", e);
            }
    }

    /**
     * Normalize audio sample based on bit depth
     */
    private double normalizeAudioSample(int sample) {
        // Normalize sample value to range [-1.0, 1.0]
        double normalizedValue;

        if (bitsPerSample == 16) {
            // 16-bit range: -32768 to 32767
            normalizedValue = sample / 32768.0;
        } else if (bitsPerSample == 24) {
            // 24-bit range: -8388608 to 8388607
            normalizedValue = sample / 8388608.0;
        } else if (bitsPerSample == 32) {
            // Assume 32-bit int
            normalizedValue = sample / 2147483648.0;
        } else {
            // Default fallback
            normalizedValue = sample / Math.pow(2, bitsPerSample - 1);
        }

        return normalizedValue;
    }

    public double getDynamicRangeScore() {
        return dynamicRangeScore;
    }

    ////////
    /**
     * Calculate dynamic range based on Pleasurize Music Foundation's DR standard
     * @param audioData Raw PCM audio data
     * @return Dynamic Range value in dB
     */
    public double calculateDRMeter(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return 0.0;
        }

        // Process the audio data first if needed
        if (samples.isEmpty()) {
            processAudioData(audioData);
        }

        // For accurate DR measurement, we need to:
        // 1. Split audio into blocks (typically 3 seconds)
        // 2. Find peak and RMS values for each block
        // 3. Take the top 20% loudest blocks
        // 4. Calculate DR as (peak - RMS) in dB

        int blockSize = sampleRate * 3; // 3-second blocks
        List<Block> blocks = new ArrayList<>();
        double[] channelPeaks = new double[channels];

        // Initialize peaks
        Arrays.fill(channelPeaks, 0.0);

        // Process audio in blocks
        for (int blockStart = 0; blockStart < samples.size(); blockStart += blockSize) {
            int blockEnd = Math.min(blockStart + blockSize, samples.size());
            double[] rmsPower = new double[channels];
            double[] blockPeaks = new double[channels];

            // Initialize block values
            Arrays.fill(rmsPower, 0.0);
            Arrays.fill(blockPeaks, 0.0);

            // Process each sample in the block
            for (int i = blockStart; i < blockEnd; i++) {
                int[] frame = samples.get(i);

                for (int ch = 0; ch < Math.min(frame.length, channels); ch++) {
                    double normalizedSample = normalizeAudioSample(frame[ch]);

                    // Update block peak
                    double absSample = Math.abs(normalizedSample);
                    blockPeaks[ch] = Math.max(blockPeaks[ch], absSample);

                    // Update overall peak
                    channelPeaks[ch] = Math.max(channelPeaks[ch], absSample);

                    // Accumulate squared value for RMS
                    rmsPower[ch] += normalizedSample * normalizedSample;
                }
            }

            // Calculate final RMS for the block
            int samplesInBlock = blockEnd - blockStart;
            for (int ch = 0; ch < channels; ch++) {
                if (samplesInBlock > 0) {
                    rmsPower[ch] = Math.sqrt(rmsPower[ch] / samplesInBlock);

                    // Store block data for later processing
                    blocks.add(new Block(blockPeaks[ch], rmsPower[ch], ch));
                }
            }
        }

        // If we have no valid blocks, return 0
        if (blocks.isEmpty()) {
            return 0.0;
        }

        // Sort blocks by RMS level (loudness) for each channel
        Map<Integer, List<Block>> channelBlocks = blocks.stream()
                .collect(Collectors.groupingBy(Block::getChannel));

        double totalDR = 0.0;
        int channelCount = 0;

        // Calculate DR for each channel
        for (int ch = 0; ch < channels; ch++) {
            List<Block> blocksForChannel = channelBlocks.getOrDefault(ch, Collections.emptyList());
            if (blocksForChannel ==null || blocksForChannel.isEmpty()) continue;

            // Sort by RMS (loudness) in descending order
            blocksForChannel.sort(Comparator.comparing(Block::getRms).reversed());

            // Take top 20% loudest blocks
            int topBlockCount = Math.max(1, blocksForChannel.size() / 5);
            double sumRms = 0.0;
            double maxPeak = 0.0;

            for (int i = 0; i < topBlockCount; i++) {
                Block block = blocksForChannel.get(i);
                sumRms += block.getRms();
                maxPeak = Math.max(maxPeak, block.getPeak());
            }

            // Calculate average RMS of the loudest blocks
            double avgRms = sumRms / topBlockCount;

            // Convert to dB scale
            double peakDb = 20 * Math.log10(Math.max(maxPeak, 1e-6));
            double rmsDb = 20 * Math.log10(Math.max(avgRms, 1e-6));

            // DR = peak dB - RMS dB
            double channelDR = peakDb - rmsDb;

            // Ensure realistic DR value (typically between 1-20 dB)
            channelDR = Math.min(20.0, Math.max(1.0, channelDR));

            totalDR += channelDR;
            channelCount++;
        }

        // Average the DR across all channels
       // return channelCount > 0 ? totalDR / channelCount : 0.0;
        double drs = channelCount > 0 ? totalDR / channelCount : 0.0;
        // Round to 2 decimal places
        return Math.round(drs * 100.0) / 100.0;
    }

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
            } else {
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

       // return dynamicRange;
        // Round to 2 decimal places
       return Math.round(dynamicRange * 100.0) / 100.0;
    }

    /**
     * Returns original Sample rate (in Hz) from waveform bytecode.
     *
     * @param c 4bit bytecode
     * @return original sample rate in Hz
     */
    public static int originalSampleRateDecoder(int c) {
        final int base = (c & 1) != 0 ? 48000 : 44100;
        int multiplier = 1 << (((c >> 3) & 1) | (((c >> 2) & 1) << 1) | (((c >> 1) & 1) << 2));

        // Double for DSD
        if (multiplier > 16) multiplier *= 2;

        return base * multiplier;
    }

    /**
     * Process audio data with improved memory and CPU efficiency
     * @param audioData Raw PCM audio data
     */
    public void processAudioData(byte[] audioData) {
        int bytesPerSample = bitsPerSample / 8;
        int frameSize = bytesPerSample * channels;

        // Calculate maximum samples needed for analysis
        // Typically we don't need to process more than 30 seconds of audio
        int maxFrames = Math.min(sampleRate * 30, audioData.length / frameSize);

        // Free previous samples if they exist to save memory
        if (samples != null && !samples.isEmpty()) {
            samples.clear();
        }

        // Initialize with exact capacity to avoid resizing
        samples = new ArrayList<>(maxFrames);

        // Use a strided approach for longer files - only process every Nth frame
        // to get a representative sample across the whole file
        int stride = 1;
        if (audioData.length / frameSize > maxFrames) {
            stride = (audioData.length / frameSize) / maxFrames;
        }

        // Process frames with stride
        for (int frameIdx = 0; frameIdx < audioData.length / frameSize; frameIdx += stride) {
            if (samples.size() >= maxFrames) break;

            int offset = frameIdx * frameSize;
            int[] frame = new int[channels];

            // Extract sample values for each channel in this frame
            for (int ch = 0; ch < channels; ch++) {
                int sampleOffset = offset + (ch * bytesPerSample);

                // Ensure we don't read past the end of data
                if (sampleOffset + bytesPerSample > audioData.length) continue;

                // Read sample based on bit depth
                if (bitsPerSample == 16) {
                    // Read 16-bit sample (little-endian)
                    int low = audioData[sampleOffset] & 0xFF;
                    int high = audioData[sampleOffset + 1] & 0xFF;
                    frame[ch] = (short)((high << 8) | low);
                }
                else if (bitsPerSample == 24) {
                    // Read 24-bit sample (little-endian)
                    int low = audioData[sampleOffset] & 0xFF;
                    int mid = audioData[sampleOffset + 1] & 0xFF;
                    int high = audioData[sampleOffset + 2] & 0xFF;

                    // Combine bytes into a 24-bit value
                    int value = (high << 16) | (mid << 8) | low;

                    // Sign-extend if negative
                    if ((value & 0x800000) != 0) {
                        value |= 0xFF000000;
                    }

                    frame[ch] = value;
                }
                else if (bitsPerSample == 32) {
                    // Read 32-bit sample (little-endian)
                    int byte1 = audioData[sampleOffset] & 0xFF;
                    int byte2 = audioData[sampleOffset + 1] & 0xFF;
                    int byte3 = audioData[sampleOffset + 2] & 0xFF;
                    int byte4 = audioData[sampleOffset + 3] & 0xFF;

                    frame[ch] = (byte4 << 24) | (byte3 << 16) | (byte2 << 8) | byte1;
                }
            }

            samples.add(frame);
        }

       // System.out.println("Processed " + samples.size() + " audio frames efficiently");
    }

    /**
     * Enhanced MQA detection with improved reliability and verification
     * @param audioData Raw PCM audio data
     */
    public void enhancedMQADetection(byte[] audioData) {
        // Process audio data to extract samples if not already done
        if (samples.isEmpty()) {
            processAudioData(audioData);
        }

        // MQA magic pattern to detect (0xbe0498c88 in binary)
        final long MQA_MAGIC = 0xbe0498c88L;
        final long MASK = 0xFFFFFFFFFL;

        // Result counters for verification
        int magicPatternCount = 0;

        // Check multiple bit positions to improve detection reliability
        for (int bitOffset = 0; bitOffset < 3; bitOffset++) {
            // Target bit position (typically around LSB+8)
            final int pos = (bitsPerSample - 16) + bitOffset;

            // Reset bit buffers for this position
            long buffer = 0;

            // Scan through samples
            for (int i = 0; i < samples.size(); i++) {
                int[] s = samples.get(i);
                if (s.length < 2) continue; // Skip non-stereo frames

                // XOR left and right channel and extract bit at position
                buffer = ((buffer << 1) | (((s[0] ^ s[1]) >> pos) & 1L)) & MASK;

                // Compare with MQA magic word
                if (buffer == MQA_MAGIC) {
                    magicPatternCount++;

                    // Extract MQA metadata at this position
                    extractMQAMetadata(i, pos);

                    // Success - break after first strong detection
                    if (this.isMQA) {
                        return;
                    }
                }
            }
        }

        // Fallback - try alternate detection method if primary method failed
        if (!this.isMQA && magicPatternCount > 0) {
            // We found some patterns but couldn't extract metadata
            // Assume basic MQA with default settings
            this.isMQA = true;
            this.isMQAStudio = false;
            this.originalSampleRate = estimateMostLikelySampleRate();

           // System.out.println("MQA detected with fallback method at bit position " + detectedPosition);
            return;
        }

        // No MQA detected after trying all methods
        this.isMQA = false;
        this.isMQAStudio = false;
        this.originalSampleRate = 0;
    }

    /**
     * Extract MQA metadata from the audio stream
     * @param startIdx Index where MQA marker was found
     * @param bitPos Bit position where MQA was detected
     */
    private void extractMQAMetadata(int startIdx, int bitPos) {
        // Flag that MQA was detected
        this.isMQA = true;

        try {
            // Extract Original Sample Rate Field (ORSF)
            byte orsf = 0;
            for (int m = 3; m < 7; m++) {
                if (startIdx + m >= samples.size()) break; // Bounds check
                int[] cur = samples.get(startIdx + m);
                int bit = ((cur[0] ^ cur[1]) >> bitPos) & 1;
                orsf |= (byte) (bit << (6 - m));
            }
            this.originalSampleRate = originalSampleRateDecoder(orsf);

            // Extract Provenance/Studio info (bits 29-33)
            byte provenance = 0;
            for (int m = 29; m < 34; m++) {
                if (startIdx + m >= samples.size()) break; // Bounds check
                int[] cur = samples.get(startIdx + m);
                int bit = ((cur[0] ^ cur[1]) >> bitPos) & 1;
                provenance |= (byte) (bit << (33 - m));
            }

            // MQA Studio is indicated by provenance > 8
            this.isMQAStudio = provenance > 8;

            // Validate the extraction
            if (this.originalSampleRate <= 0 || this.originalSampleRate > 384000) {
                // Invalid sample rate detected, likely a false positive
                // Try to extract again at a different offset
                if (startIdx + 50 < samples.size()) {
                    orsf = 0;
                    for (int m = 3; m < 7; m++) {
                        int[] cur = samples.get(startIdx + m + 2); // Try with offset
                        int bit = ((cur[0] ^ cur[1]) >> bitPos) & 1;
                        orsf |= (byte) (bit << (6 - m));
                    }
                    long alternateSampleRate = originalSampleRateDecoder(orsf);

                    // Use alternate sample rate if it seems valid
                    if (alternateSampleRate > 0 && alternateSampleRate <= 384000) {
                        this.originalSampleRate = alternateSampleRate;
                    } else {
                        // If still invalid, estimate based on file properties
                        this.originalSampleRate = estimateMostLikelySampleRate();
                    }
                } else {
                    // Not enough samples for re-extraction, use estimation
                    this.originalSampleRate = estimateMostLikelySampleRate();
                }
            }

        } catch (Exception e) {
            // Handle extraction errors
           // System.err.println("Error extracting MQA metadata: " + e.getMessage());
            this.isMQA = true; // We still detected the magic word
            this.isMQAStudio = false;
            this.originalSampleRate = estimateMostLikelySampleRate();
        }
    }

    /**
     * Estimate the most likely original sample rate for MQA content
     * based on the file's properties when direct extraction fails
     * @return Estimated original sample rate in Hz
     */
    private long estimateMostLikelySampleRate() {
        // If the current sample rate is a known MQA value, use a mapping table
        switch (this.sampleRate) {
            case 44100:
            case 48000:
                return 96000; // Most common for 44.1/48kHz files

            case 88200:
            case 96000:
                return 192000; // Most common for 88.2/96kHz files

            case 176400:
            case 192000:
                return 352800; // Most common for 176.4/192kHz files

            default:
                // If we can't determine from file properties, use reasonable defaults
                if (this.sampleRate <= 48000) {
                    return 96000; // Most common MQA encoded rate
                } else if (this.sampleRate <= 96000) {
                    return 192000;
                } else {
                    return 352800; // Highest commonly supported MQA rate
                }
        }
    }

    /**
     * Helper class to store block analysis data
     */
    private static class Block {
        private final double peak;
        private final double rms;
        private final int channel;

        public Block(double peak, double rms, int channel) {
            this.peak = peak;
            this.rms = rms;
            this.channel = channel;
        }

        public double getPeak() {
            return peak;
        }

        public double getRms() {
            return rms;
        }

        public int getChannel() {
            return channel;
        }
    }

    /**
     * Detect upscaled audio with improved spectral analysis
     * @param audioData Raw PCM audio data
     * @return confidence score between 0 and 1 (higher = more likely upscaled)
     */
    public double detectUpscaledAudio(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return 0;
        }

        // Process the audio data
        if (samples.isEmpty()) {
            processAudioData(audioData);
        }

        // Parameters for spectral analysis
        final int fftSize = 4096;
        final int halfFFT = fftSize / 2;
        final int minSamplesNeeded = fftSize * 10; // Need enough samples for reliable detection

        // If we don't have enough samples, we can't make a reliable determination
        if (samples.size() < minSamplesNeeded) {
            return 0;
        }

        // Average magnitude spectrum
        double[] avgSpectrum = new double[halfFFT];
        Arrays.fill(avgSpectrum, 0.0);

        // Calculate number of FFT frames to analyze
        int numFrames = Math.min(20, samples.size() / fftSize);

        // Apply FFT to multiple frames for more reliable detection
        for (int frame = 0; frame < numFrames; frame++) {
            // Extract audio frame (use left channel for analysis)
            double[] audioFrame = new double[fftSize];

            // Get samples for this frame
            int startIdx = frame * fftSize;
            for (int i = 0; i < fftSize && (startIdx + i) < samples.size(); i++) {
                // Use left channel (0) for analysis
                audioFrame[i] = normalizeAudioSample(samples.get(startIdx + i)[0]);
            }

            // Apply window function to reduce spectral leakage
            applyHannWindow(audioFrame);

            // Compute FFT (use a library like JTransforms in practice)
            double[] spectrum = computeFFTMagnitude(audioFrame);

            // Accumulate spectrum
            for (int i = 0; i < halfFFT; i++) {
                avgSpectrum[i] += spectrum[i];
            }
        }

        // Normalize spectrum
        for (int i = 0; i < halfFFT; i++) {
            avgSpectrum[i] /= numFrames;
        }

        // Calculate spectral features for upscaling detection
        double highFrequencyEnergy = getHighFrequencyEnergy(avgSpectrum);
        double spectralRolloff = findSpectralRolloff(avgSpectrum, 0.85);

        // Detect spectral gap or cliff (typical in upscaled content)
        boolean hasSpectralCliff = detectSpectralCliff(avgSpectrum);

        // Calculate upscaling confidence score based on multiple factors
        double upscalingConfidence = 0.0;

        // Weights for different features
        final double W_HIGH_FREQ = 0.35;
        final double W_ROLLOFF = 0.30;
        final double W_CLIFF = 0.35;

        // High-res audio should have significant high-frequency content
        // Lower energy = higher confidence of upscaling
        if (sampleRate > 48000) {
            upscalingConfidence += W_HIGH_FREQ * (1.0 - Math.min(1.0, highFrequencyEnergy / 0.05));
        }

        // Check for normalized rolloff frequency (should be high for true high-res)
        // Lower rolloff = higher confidence of upscaling
        if (sampleRate > 48000) {
            upscalingConfidence += W_ROLLOFF * (1.0 - Math.min(1.0, spectralRolloff / 0.3));
        }

        // Direct detection of spectral cliff is strong evidence of upscaling
        if (hasSpectralCliff) {
            upscalingConfidence += W_CLIFF;
        }

        // Cap confidence at 1.0
        upscalingConfidence = Math.min(1.0, upscalingConfidence);

       // System.out.println("Upscaling detection confidence: " + upscalingConfidence);

       // return upscalingConfidence;
        // Round to 2 decimal places
        return Math.round(upscalingConfidence * 100.0) / 100.0;
    }

    private double getHighFrequencyEnergy(double[] avgSpectrum) {
        double expectedCutoff;

        // Expected cutoff frequency based on bit depth and sample rate
        if (bitsPerSample <= 16) {
            expectedCutoff = 0.45; // Normalized frequency (just below Nyquist)
        } else {
            // For higher bit depths, we expect content up to Nyquist
            expectedCutoff = 0.48;
        }

        // Calculate actual spectral content
        return calculateHighFrequencyEnergy(avgSpectrum, expectedCutoff);
    }

    /**
     * Apply Hann window to audio frame to reduce spectral leakage
     */
    private void applyHannWindow(double[] frame) {
        for (int i = 0; i < frame.length; i++) {
            double windowCoeff = 0.5 * (1 - Math.cos(2 * Math.PI * i / (frame.length - 1)));
            frame[i] *= windowCoeff;
        }
    }

    /**
     * Compute magnitude spectrum from windowed audio frame
     */
    private double[] computeFFTMagnitude(double[] windowedFrame) {
        // Ensure input length is power of 2
        int fftSize = windowedFrame.length;
        double[] paddedInput = padToPowerOfTwo(windowedFrame);

        // Use Apache Commons Math for FFT
        FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);
        Complex[] complex = transformer.transform(paddedInput, TransformType.FORWARD);

        int halfFFT = fftSize / 2;
        double[] magnitudeSpectrum = new double[halfFFT];

        for (int i = 0; i < halfFFT; i++) {
            magnitudeSpectrum[i] = complex[i].abs() / fftSize;
        }

        return magnitudeSpectrum;
    }

    /**
     * Calculate high frequency energy ratio
     */
    private double calculateHighFrequencyEnergy(double[] spectrum, double thresholdFreq) {
        int thresholdBin = (int)(thresholdFreq * spectrum.length);

        double highFreqEnergy = 0;
        double totalEnergy = 0;

        for (int i = 0; i < spectrum.length; i++) {
            double energy = spectrum[i] * spectrum[i];
            totalEnergy += energy;

            if (i >= thresholdBin) {
                highFreqEnergy += energy;
            }
        }

        return totalEnergy > 0 ? highFreqEnergy / totalEnergy : 0;
    }

    /**
     * Find spectral rolloff point (frequency below which X% of energy is contained)
     */
    private double findSpectralRolloff(double[] spectrum, double percentile) {
        double totalEnergy = 0;
        for (double v : spectrum) {
            totalEnergy += v * v;
        }

        double energyThreshold = totalEnergy * percentile;
        double cumulativeEnergy = 0;

        for (int i = 0; i < spectrum.length; i++) {
            cumulativeEnergy += spectrum[i] * spectrum[i];
            if (cumulativeEnergy >= energyThreshold) {
                return (double) i / spectrum.length;
            }
        }

        return 1.0; // Default to Nyquist if threshold not reached
    }

    /**
     * Detect spectral cliff (sudden drop in spectrum typical of upscaled audio)
     */
    private boolean detectSpectralCliff(double[] spectrum) {
        // Smooth spectrum for analysis
        double[] smoothed = new double[spectrum.length];
        int smoothingWindow = 5;

        // Simple moving average smoothing
        for (int i = 0; i < spectrum.length; i++) {
            int count = 0;
            double sum = 0;

            for (int j = Math.max(0, i - smoothingWindow);
                 j < Math.min(spectrum.length, i + smoothingWindow + 1); j++) {
                sum += spectrum[j];
                count++;
            }

            smoothed[i] = sum / count;
        }

        // Look for sharp transitions in spectrum
        double maxDerivative = 0;
        int cliffPosition = -1;

        for (int i = 1; i < smoothed.length - 1; i++) {
            double derivative = Math.abs(smoothed[i+1] - smoothed[i-1]) / 2.0;

            if (derivative > maxDerivative) {
                maxDerivative = derivative;
                cliffPosition = i;
            }
        }

        // Calculate normalized position and magnitude of cliff
        double normalizedPosition = (double) cliffPosition / spectrum.length;
        double cliffRatio = 0;

        if (cliffPosition > 0) {
            // Calculate ratio of average energy before and after cliff
            double beforeCliff = 0;
            double afterCliff = 0;

            for (int i = 0; i < cliffPosition; i++) {
                beforeCliff += smoothed[i];
            }
            beforeCliff /= cliffPosition;

            for (int i = cliffPosition; i < smoothed.length; i++) {
                afterCliff += smoothed[i];
            }
            afterCliff /= (smoothed.length - cliffPosition);

            cliffRatio = beforeCliff > 0 ? afterCliff / beforeCliff : 0;
        }

        // Detect cliff based on position and magnitude
        // A real cliff should be:
        // 1. Located between 0.2 and 0.8 of Nyquist (typical for upsampled content)
        // 2. Have a significant ratio between before/after energy
        return normalizedPosition > 0.2 && normalizedPosition < 0.8 && cliffRatio < 0.3;
    }

    /**
     * Detect resampled audio using phase analysis and spectral features
     *
     * @param audioData Raw PCM audio data
     * @return confidence score between 0 and 1 (higher = more likely resampled)
     */
    public double detectResampledAudio(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return 0;
        }

        // Process the audio data if needed
        if (samples.isEmpty()) {
            processAudioData(audioData);
        }

        // Perform basic spectral analysis first
        final int fftSize = 4096;
        final int halfFFT = fftSize / 2;
        final int numFrames = Math.min(10, samples.size() / fftSize);

        if (numFrames < 2) return 0.0; // Not enough data

        // Average magnitude spectrum
        double[] avgSpectrum = new double[halfFFT];
        Arrays.fill(avgSpectrum, 0.0);

        for (int frame = 0; frame < numFrames; frame++) {
            double[] audioFrame = new double[fftSize];

            // Get samples for this frame
            int startIdx = frame * fftSize;
            for (int i = 0; i < fftSize && (startIdx + i) < samples.size(); i++) {
                audioFrame[i] = normalizeAudioSample(samples.get(startIdx + i)[0]);
            }

            applyHannWindow(audioFrame);
            double[] magnitude = computeFFTMagnitude(audioFrame);

            for (int i = 0; i < halfFFT; i++) {
                avgSpectrum[i] += magnitude[i];
            }
        }

        // Normalize
        for (int i = 0; i < halfFFT; i++) {
            avgSpectrum[i] /= numFrames;
        }

        // Simple feature: Energy distribution
        double highFreqEnergy = 0;
        double totalEnergy = 0;

        for (int i = 0; i < halfFFT; i++) {
            double energy = avgSpectrum[i] * avgSpectrum[i];
            totalEnergy += energy;

            if (i > halfFFT * 0.7) { // Upper 30%
                highFreqEnergy += energy;
            }
        }

        double highFreqRatio = totalEnergy > 0 ? highFreqEnergy / totalEnergy : 0;

        // A simplistic heuristic - resampled files often lack high frequency energy

        //return Math.max(0, 1.0 - (highFreqRatio * 8.0));
        // Round to 2 decimal places
        double score = Math.max(0, 1.0 - (highFreqRatio * 8.0));
        return Math.round(score * 100.0) / 100.0;
    }

    // Helper method to ensure FFT input is a power of 2 length
    private double[] padToPowerOfTwo(double[] input) {
        int powerOfTwo = 1;
        while (powerOfTwo < input.length) {
            powerOfTwo *= 2;
        }

        if (powerOfTwo == input.length) {
            return input;
        }

        double[] padded = new double[powerOfTwo];
        System.arraycopy(input, 0, padded, 0, input.length);
        return padded;
    }

}

package apincer.music.core.authenticity;

public class AudioAnalysisResult {

    public int sampleRate;
    public int bitDepth;

    public double rms;
    public double peak;
    public double noiseFloor;

    public double lowBandRms;
    public double highBandRms;

    public double spectralFlatness;
    public double spectralEntropy;

    public double dynamicRange;

    public String verdict;
}

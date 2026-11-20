package apincer.music.core.playback;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A measured EBU R128 integrated loudness and true peak, and the ReplayGain 2.0 gain that follows. */
public final class Loudness {
    /** ReplayGain 2.0 plays every track at -18 LUFS. */
    public static final double REFERENCE_LUFS = -18.0;

    private static final Pattern SUMMARY_LUFS = Pattern.compile("Integrated loudness:\\s*I:\\s*(-?[\\d.]+|-inf) LUFS");
    private static final Pattern SUMMARY_PEAK = Pattern.compile("True peak:\\s*Peak:\\s*(-?[\\d.]+|-inf) dBFS");

    public final double integratedLufs;
    public final double truePeakDbfs;
    public final double seconds;

    public Loudness(double integratedLufs, double truePeakDbfs, double seconds) {
        this.integratedLufs = integratedLufs;
        this.truePeakDbfs = truePeakDbfs;
        this.seconds = seconds;
    }

    public double gainDb() {
        return REFERENCE_LUFS - integratedLufs;
    }

    /** The peak as a linear sample value, the form REPLAYGAIN_*_PEAK tags store. */
    public double peakLinear() {
        return Math.pow(10.0, truePeakDbfs / 20.0);
    }

    /**
     * Reads the Summary block of an ffmpeg {@code ebur128=peak=true} run. The per-frame lines
     * before it carry running "I:" values, so only the summary is trusted.
     * @return null when the log has no complete summary or the track is silent
     */
    public static Loudness parseEbur128(String log, double seconds) {
        if (log == null) return null;
        int summary = log.lastIndexOf("Summary:");
        if (summary < 0) return null;
        String tail = log.substring(summary);
        Matcher lufs = SUMMARY_LUFS.matcher(tail);
        Matcher peak = SUMMARY_PEAK.matcher(tail);
        if (!lufs.find() || !peak.find()) return null;
        double integrated = parse(lufs.group(1));
        // Silence measures -70 LUFS (the absolute gate) or -inf; levelling it would mean +52 dB
        if (Double.isNaN(integrated) || integrated <= -70.0) return null;
        return new Loudness(integrated, parse(peak.group(1)), seconds);
    }

    /**
     * The album's loudness from its tracks: the duration-weighted mean of their energy, and the
     * highest peak. It approximates measuring the album as one file, minus the cross-track gating.
     */
    public static Loudness album(List<Loudness> tracks) {
        if (tracks == null || tracks.isEmpty()) return null;
        double energy = 0;
        double seconds = 0;
        double peak = Double.NEGATIVE_INFINITY;
        for (Loudness track : tracks) {
            double weight = track.seconds > 0 ? track.seconds : 1.0;
            energy += weight * Math.pow(10.0, track.integratedLufs / 10.0);
            seconds += weight;
            peak = Math.max(peak, track.truePeakDbfs);
        }
        return new Loudness(10.0 * Math.log10(energy / seconds), peak, seconds);
    }

    private static double parse(String value) {
        return "-inf".equals(value) ? Double.NEGATIVE_INFINITY : Double.parseDouble(value);
    }
}

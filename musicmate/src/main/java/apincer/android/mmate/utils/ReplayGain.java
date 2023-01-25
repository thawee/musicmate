package apincer.android.mmate.utils;

import java.util.List;

import apincer.android.mmate.objectbox.MusicTag;

public class ReplayGain {
    public static final double RG2_REFERENCE = -18.00;
    public static double max_true_peak_level = -1.0; // dBTP; default as per EBU Tech 3343
    public static void calculate(List<MusicTag> tags) {
        //https://github.com/Moonbase59/loudgain/blob/master/src/loudgain.c
        double albumGain = RG2_REFERENCE - getAlbumLoudness(tags); //scan -> album_gain
        double albumPeak = getAlbumTruePeak(tags);

        for(MusicTag tag: tags) {
            double trackGain = loudnessToReplayGain(tag.getTrackLoudness());  //scan -> track_gain
            double tgain = 1.0; // "gained" track peak
            double tnew;
            double tpeak = Math.pow(10.0, max_true_peak_level / 20.0); // track peak limit
            double again = 1.0; // "gained" album peak
            double anew;
            double apeak = Math.pow(10.0, max_true_peak_level / 20.0); // album peak limit

            // Check if track will clip, and correct

            // track peak after gain
            tgain = Math.pow(10.0, trackGain / 20.0) * tag.getTrackTruePeak();
            tnew = tgain;

            // album peak after gain
            //again = pow(10.0, scan -> album_gain / 20.0) * scan -> album_peak;
            again = Math.pow(10.0, albumGain / 20.0) * albumPeak;
            anew = again;

            if (tgain > tpeak) {
                // set new track peak = minimum of peak after gain and peak limit
                tnew = Math.min(tgain, tpeak);
                final double ttemp = tgain/tnew;
                trackGain = trackGain - (Math.log10(ttemp) * 20.0);
            }

            if (again > apeak) {
                anew = Math.min(again, apeak);
                final double atemp = again/anew;
                albumGain = albumGain - (Math.log10(atemp) * 20.0);
            }
        }
    }

    private static double loudnessToReplayGain(double loudness) {
        return RG2_REFERENCE - loudness;
    }

    private static double getAlbumTruePeak(List<MusicTag> tags) {
        double peak = 0.0d;
        for(MusicTag tag: tags) {
            peak = Math.max(peak, tag.getTrackTruePeak());
        }
        return peak;
    }

    private static double getAlbumLoudness(List<MusicTag> tags) {
        double loudness = 0.0d;

        return loudness;
    }

    private static double getAlbumRange(List<MusicTag> tags) {
        double range = 0.0d;

        return range;
    }
}

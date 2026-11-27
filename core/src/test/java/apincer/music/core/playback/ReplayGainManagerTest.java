package apincer.music.core.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag;

public class ReplayGainManagerTest {

    private static final double EPSILON = 0.001;

    @Test
    public void testParseGain() {
        assertEquals(-6.50, ReplayGainManager.parseGain("-6.50 dB"), EPSILON);
        assertEquals(-6.50, ReplayGainManager.parseGain("-6.50db"), EPSILON);
        assertEquals(2.35, ReplayGainManager.parseGain("+2.35 dB"), EPSILON);
        assertEquals(0.0, ReplayGainManager.parseGain("0.0 dB"), EPSILON);
        assertEquals(4.12, ReplayGainManager.parseGain("4.12"), EPSILON);
        assertEquals(0.0, ReplayGainManager.parseGain("invalid"), EPSILON);
        assertEquals(0.0, ReplayGainManager.parseGain(null), EPSILON);
    }

    @Test
    public void testParsePeak() {
        assertEquals(0.988231, ReplayGainManager.parsePeak("0.988231"), EPSILON);
        assertEquals(1.0, ReplayGainManager.parsePeak("1.0"), EPSILON);
        // dB peak specification
        double expectedPeak = Math.pow(10.0, -1.0 / 20.0);
        assertEquals(expectedPeak, ReplayGainManager.parsePeak("-1.0 dB"), EPSILON);
        assertEquals(1.0, ReplayGainManager.parsePeak("invalid"), EPSILON);
        assertEquals(1.0, ReplayGainManager.parsePeak(null), EPSILON);
    }

    @Test
    public void decimalAndScientificTagFormatsRemainSupported() {
        assertEquals(-6.5, ReplayGainManager.parseGain(" -6.5 DB "), EPSILON);
        assertEquals(0.25, ReplayGainManager.parseGain("+.25 Db"), EPSILON);
        assertEquals(-6.5, ReplayGainManager.parseGain("-6.5e0 dB"), EPSILON);
        assertEquals(0.5, ReplayGainManager.parsePeak("5e-1"), EPSILON);
        assertEquals(1.2, ReplayGainManager.parsePeak("1.2"), EPSILON);
        assertEquals(Math.pow(10.0, 3.0 / 20.0), ReplayGainManager.parsePeak("+3 DB"), EPSILON);
        assertEquals(0.000001, ReplayGainManager.parsePeak("-120 dB"), 0.00000001);
    }

    @Test
    public void corruptGainTagsAreAbsentRatherThanZeroDb() throws Exception {
        String[] invalid = {"invalid", "NaN", "Infinity", "-Infinity dB", "1e999", "999 dB",
                "-999 dB", "+1+2 dB", "1 dB junk", "1dbdb", "0x1.0p0"};
        for (String text : invalid) {
            VorbisCommentTag tag = new VorbisCommentTag();
            tag.setField("REPLAYGAIN_TRACK_GAIN", text);
            tag.setField("REPLAYGAIN_ALBUM_GAIN", text);
            ReplayGainManager.ReplayGainInfo info = new ReplayGainManager.ReplayGainInfo();
            ReplayGainManager.parseVorbisReplayGain(tag, info);
            assertFalse(text, info.hasTrackGain);
            assertFalse(text, info.hasAlbumGain);
            assertEquals(text, 0.0, ReplayGainManager.parseGain(text), 0.0);
            assertEquals(ReplayGainManager.Leveling.Kind.NO_TAGS,
                    ReplayGainManager.level("track", RG2, 0f, true, info, false).kind);
        }
        VorbisCommentTag zeroTag = new VorbisCommentTag();
        zeroTag.setField("REPLAYGAIN_TRACK_GAIN", "0 dB");
        ReplayGainManager.ReplayGainInfo info = new ReplayGainManager.ReplayGainInfo();
        ReplayGainManager.parseVorbisReplayGain(zeroTag, info);
        assertTrue(info.hasTrackGain);
    }

    @Test
    public void corruptPeakTagsUseFullScale() {
        String[] invalid = {"NaN", "Infinity", "-Infinity", "0", "-0.5", "1e999", "1e-999",
                "999", "999 dB", "-Infinity dB", "NaN dB", "broken dB", "1dbdb"};
        for (String text : invalid) {
            assertEquals(text, 1.0, ReplayGainManager.parsePeak(text), 0.0);
        }
    }

    private static ReplayGainManager.ReplayGainInfo tags(double trackDb, double trackPeak) {
        ReplayGainManager.ReplayGainInfo info = new ReplayGainManager.ReplayGainInfo();
        info.trackGainDb = trackDb;
        info.trackPeak = trackPeak;
        info.hasTrackGain = true;
        return info;
    }

    private static final float RG2 = -18f;
    private static final float STREAMING = -14f;

    @Test
    public void offLeavesVolumeAloneEvenWithTags() {
        ReplayGainManager.Leveling l = ReplayGainManager.level("off", STREAMING, 3f, true, tags(-6, 0.5));
        assertEquals(ReplayGainManager.Leveling.Kind.OFF, l.kind);
        assertEquals(1.0f, l.volume, 0f);
    }

    @Test
    public void appliedGainIsTrackGainPlusPreampAtTheReferenceTarget() {
        ReplayGainManager.Leveling l = ReplayGainManager.level("track", RG2, -3f, true, tags(-4.2, 0.5));
        assertEquals(ReplayGainManager.Leveling.Kind.APPLIED, l.kind);
        assertEquals(-7.2, l.appliedDb(), 0.01);
        assertFalse(l.album);
    }

    @Test
    public void streamingTargetCutsLoudMastersFourDbLess() {
        ReplayGainManager.Leveling l = ReplayGainManager.level("track", STREAMING, 0f, true, tags(-10.0, 0.9));
        assertEquals(-6.0, l.appliedDb(), 0.01);
    }

    @Test
    public void positiveGainIsNeverBoostedPastZeroDb() {
        ReplayGainManager.Leveling l = ReplayGainManager.level("track", RG2, 0f, false, tags(5.0, 0.3));
        assertEquals(1.0f, l.volume, 0f);
        assertEquals(0.0, l.appliedDb(), 0.001);
    }

    @Test
    public void guardedQuietTrackIsRaisedOnlyUntilItsPeakReachesTheLimiterThreshold() {
        ReplayGainManager.Leveling room = ReplayGainManager.level("track", STREAMING, 0f, true, tags(1.0, 0.4));
        assertEquals(5.0, room.appliedDb(), 0.01);
        ReplayGainManager.Leveling capped = ReplayGainManager.level("track", STREAMING, 0f, true, tags(6.0, 0.8));
        assertEquals(-2.2 - 20 * Math.log10(0.8), capped.appliedDb(), 0.001);
        ReplayGainManager.Leveling badPeak = ReplayGainManager.level("track", STREAMING, 0f, true, tags(30.0, 0.001));
        assertEquals(12.0, badPeak.appliedDb(), 0.01);
    }

    @Test
    public void clippingGuardLowersGainToThePeak() {
        // -1 dB gain with a 1.2 peak would be limited; the guard puts the peak at the limiter threshold
        ReplayGainManager.Leveling guarded = ReplayGainManager.level("track", RG2, 0f, true, tags(-1.0, 1.2));
        assertEquals(-2.2 - 20 * Math.log10(1.2), guarded.appliedDb(), 0.001);
        ReplayGainManager.Leveling unguarded = ReplayGainManager.level("track", RG2, 0f, false, tags(-1.0, 1.2));
        assertEquals(-1.0, unguarded.appliedDb(), 0.01);
    }

    @Test
    public void clippingGuardAlsoCutsAnAttenuatedTrackWhosePeakWouldBeLimited() {
        // A -1 dB cut leaves a full-scale peak at -1 dBFS, above the -2.2 dB limiter threshold
        ReplayGainManager.Leveling guarded = ReplayGainManager.level("track", RG2, 0f, true, tags(-1.0, 1.0));
        assertEquals(-2.2, guarded.appliedDb(), 0.001);
    }

    @Test
    public void albumModeUsesAlbumGainAndFallsBackToTrack() {
        ReplayGainManager.ReplayGainInfo info = tags(-4.0, 0.5);
        ReplayGainManager.Leveling fallback = ReplayGainManager.level("album", RG2, 0f, true, info);
        assertFalse(fallback.album);
        assertEquals(-4.0, fallback.appliedDb(), 0.01);

        info.albumGainDb = -6.0;
        info.albumPeak = 0.5;
        info.hasAlbumGain = true;
        ReplayGainManager.Leveling album = ReplayGainManager.level("album", RG2, 0f, true, info);
        assertTrue(album.album);
        assertEquals(-6.0, album.appliedDb(), 0.01);
    }

    @Test
    public void untaggedTrackIsTreatedAsALoudMasterAtTheTarget() {
        ReplayGainManager.Leveling streaming = ReplayGainManager.level("track", STREAMING, 0f, true, null);
        assertEquals(ReplayGainManager.Leveling.Kind.NO_TAGS, streaming.kind);
        assertEquals(-5.0, streaming.appliedDb(), 0.01);
        ReplayGainManager.Leveling reference = ReplayGainManager.level("track", RG2, -3f, true, new ReplayGainManager.ReplayGainInfo());
        assertEquals(-12.0, reference.appliedDb(), 0.01);
    }

    @Test
    public void untaggedTrackIsNeverRaisedByThePreamp() {
        ReplayGainManager.Leveling raised = ReplayGainManager.level("track", STREAMING, 9f, true, null);
        assertEquals(ReplayGainManager.Leveling.Kind.NO_TAGS, raised.kind);
        assertEquals(1.0f, raised.volume, 0f);
    }

    @Test
    public void unknownTracksStayUnchangedUnderTheDefaultPolicy() {
        for (float preamp : new float[]{-12f, 0f, 12f}) {
            ReplayGainManager.Leveling untagged = ReplayGainManager.level("track", STREAMING, preamp, true, null, false);
            assertEquals(ReplayGainManager.Leveling.Kind.NO_TAGS, untagged.kind);
            assertEquals(1.0f, untagged.volume, 0f);
        }
        ReplayGainManager.Leveling tagged = ReplayGainManager.level("track", RG2, -3f, true, tags(-4, 0.5), false);
        assertEquals(-7.0, tagged.appliedDb(), 0.01);
        ReplayGainManager.Leveling legacy = ReplayGainManager.level("track", STREAMING, 0f, true, null, true);
        assertEquals(-5.0, legacy.appliedDb(), 0.01);
    }

    @Test
    public void directlyConstructedInvalidGainsAreTreatedAsAbsent() {
        for (double gain : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 999, -999}) {
            ReplayGainManager.ReplayGainInfo info = tags(gain, 0.5);
            info.hasAlbumGain = true;
            info.albumGainDb = gain;
            ReplayGainManager.Leveling untagged = ReplayGainManager.level("album", RG2, -3f, true, info, false);
            assertEquals(ReplayGainManager.Leveling.Kind.NO_TAGS, untagged.kind);
            assertEquals(1.0f, untagged.volume, 0f);
        }
        ReplayGainManager.ReplayGainInfo info = tags(-4, 0.5);
        info.hasAlbumGain = true;
        info.albumGainDb = Double.NaN;
        ReplayGainManager.Leveling fallback = ReplayGainManager.level("album", RG2, 0f, true, info);
        assertFalse(fallback.album);
        assertEquals(-4.0, fallback.appliedDb(), 0.01);
    }

    @Test
    public void directlyConstructedInvalidPeaksCannotAllowBoost() {
        for (double peak : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0, -1, 999}) {
            ReplayGainManager.ReplayGainInfo info = tags(6, peak);
            // An unknown peak counts as full scale, so the guard holds it at the limiter threshold
            float guarded = (float) ReplayGainManager.LIMITER_THRESHOLD;
            ReplayGainManager.Leveling track = ReplayGainManager.level("track", RG2, 0f, true, info);
            assertEquals(guarded, track.volume, 0f);
            info.albumGainDb = 6;
            info.albumPeak = peak;
            info.hasAlbumGain = true;
            ReplayGainManager.Leveling album = ReplayGainManager.level("album", RG2, 0f, true, info);
            assertTrue(album.album);
            assertEquals(guarded, album.volume, 0f);
        }
    }

    @Test
    public void nonfiniteSettingsFallBackToReferenceTargetAndZeroPreamp() {
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            ReplayGainManager.Leveling tagged = ReplayGainManager.level("track", invalid, invalid, true, tags(-6, 0.5));
            assertEquals(-6.0, tagged.appliedDb(), 0.01);
            ReplayGainManager.Leveling untagged = ReplayGainManager.level("track", invalid, invalid, true, null);
            assertEquals(-9.0, untagged.appliedDb(), 0.01);
        }
    }

    @Test
    public void testGainLinearCalculation() {
        // Gain calculation formula: linear = 10^(gainDb / 20)
        double gain0Db = Math.pow(10.0, 0.0 / 20.0);
        assertEquals(1.0, gain0Db, EPSILON);

        double gainMinus6Db = Math.pow(10.0, -6.0206 / 20.0);
        assertEquals(0.5, gainMinus6Db, EPSILON);

        // Clipping guard: if (linear * peak > threshold) linear = threshold / peak
        double peak = 1.2;
        double requestedLinear = 1.5;
        double threshold = ReplayGainManager.LIMITER_THRESHOLD;
        assertTrue("Linear * peak exceeds the limiter threshold", requestedLinear * peak > threshold);
        double safeLinear = threshold / peak;
        assertTrue("Safe linear * peak stays at the threshold", safeLinear * peak <= threshold + EPSILON);
    }
}

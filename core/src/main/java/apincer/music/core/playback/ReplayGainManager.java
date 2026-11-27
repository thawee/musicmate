package apincer.music.core.playback;

import android.content.Context;
import android.util.Log;
import android.util.LruCache;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.id3.AbstractID3v2Frame;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.ID3v22Tag;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX;
import org.jaudiotagger.tag.mp4.Mp4Tag;
import org.jaudiotagger.tag.mp4.field.Mp4TagReverseDnsField;
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import apincer.music.core.Settings;
import apincer.music.core.codec.TagReader;
import apincer.music.core.provider.FileSystem;

/**
 * Audiophile ReplayGain Manager & Active Playback Leveling Engine.
 *
 * <p>Supports reading, parsing, caching, and writing standardized ReplayGain 2.0 / EBU R128
 * metadata across FLAC (Vorbis Comments), MP3 (ID3v2 TXXX/RVA2), and M4A/ALAC (MP4 tags).
 * Compatible with Poweramp, USB Audio Player PRO (UAPP), Foobar2000, and Neutron.</p>
 */
public class ReplayGainManager {

    private static final String TAG = "ReplayGainManager";
    private static volatile ReplayGainManager INSTANCE;

    private static final String MP4_ITUNES = "----:com.apple.iTunes:";

    /** Loudness that ReplayGain 2 tags are computed against. */
    static final double REFERENCE_LUFS = -18.0;
    /** Assumed loudness of a track without tags, near a typical modern master. */
    static final double UNTAGGED_MASTER_LUFS = -9.0;
    /** +12 dB, so a bad peak tag cannot make a track deafening. */
    static final double MAX_BOOST = 3.981;
    /** -2.2 dBFS, where the playback true-peak limiter starts reducing; a raised peak above it is only limited. */
    public static final double LIMITER_THRESHOLD = Math.pow(10.0, -2.2 / 20.0);
    // Broad sanity limits: preserve quiet recordings and intersample peaks, reject corrupt tags.
    private static final double MAX_GAIN_DB = 60.0;
    private static final double MAX_PEAK = 64.0;
    private static final Pattern REPLAY_GAIN_NUMBER = Pattern.compile(
            "([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)\\s*(dB)?",
            Pattern.CASE_INSENSITIVE);

    // Cache parsed ReplayGain info for up to 500 tracks to avoid disk I/O on hot paths
    private final LruCache<String, ReplayGainInfo> cache = new LruCache<>(500);

    public static class ReplayGainInfo {
        public double trackGainDb = 0.0;
        public double trackPeak = 1.0;
        public double albumGainDb = 0.0;
        public double albumPeak = 1.0;
        public boolean hasTrackGain = false;
        public boolean hasAlbumGain = false;
    }

    /** What volume leveling does to one track. */
    public static final class Leveling {
        public enum Kind { OFF, NO_TAGS, APPLIED }

        public static final Leveling OFF = new Leveling(Kind.OFF, 1.0f, false);

        public final Kind kind;
        /** Linear scale applied to the samples, in [0.01, 4]; above 1 only when the track's peak leaves room. */
        public final float volume;
        /** True when the album gain was used rather than the track gain. */
        public final boolean album;

        Leveling(Kind kind, float volume, boolean album) {
            this.kind = kind;
            this.volume = volume;
            this.album = album;
        }

        /** The gain the listener actually hears, in dB. */
        public double appliedDb() {
            return 20.0 * Math.log10(volume);
        }
    }

    private ReplayGainManager() {}

    public static ReplayGainManager getInstance() {
        if (INSTANCE == null) {
            synchronized (ReplayGainManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ReplayGainManager();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Retrieves ReplayGain metadata for the specified file path, leveraging the LRU cache
     * or reading the audio file tags on demand.
     */
    public ReplayGainInfo getReplayGain(String filePath) {
        if (filePath == null || filePath.isEmpty()) return null;

        ReplayGainInfo cached = cache.get(filePath);
        if (cached != null) return cached;

        try {
            // Through the granted folder when scoped storage hides the path
            AudioFile audioFile = TagReader.readAudioFile(new File(filePath));
            Tag tag = audioFile.getTag();
            if (tag == null) return null;

            ReplayGainInfo info = new ReplayGainInfo();

            if (tag instanceof FlacTag flacTag) {
                VorbisCommentTag vorbis = flacTag.getVorbisCommentTag();
                if (vorbis != null) {
                    parseVorbisReplayGain(vorbis, info);
                }
            } else if (tag instanceof VorbisCommentTag vorbisTag) {
                parseVorbisReplayGain(vorbisTag, info);
            } else if (tag instanceof AbstractID3v2Tag id3Tag) {
                parseId3ReplayGain(id3Tag, info);
            } else if (tag instanceof Mp4Tag mp4Tag) {
                parseMp4ReplayGain(mp4Tag, info);
            } else {
                // Fallback tag check
                String tg = tag.getFirst("REPLAYGAIN_TRACK_GAIN");
                if (tg != null && !tg.isEmpty()) {
                    setGain(info, tg, false);
                }
            }

            cache.put(filePath, info);
            return info;

        } catch (Exception e) {
            Log.w(TAG, "Failed to read ReplayGain tags from " + filePath + ": " + e.getMessage());
            return null;
        }
    }

    /** Drops one cached tag read so library scans can reflect changes made outside this process. */
    public void invalidate(String filePath) {
        if (filePath != null) cache.remove(filePath);
    }

    /** Reads the leveling settings and this file's tags; does file I/O on a cache miss. */
    public Leveling leveling(Context context, String path) {
        if (context == null || path == null) return Leveling.OFF;
        String mode = Settings.getReplayGainMode(context);
        if ("off".equalsIgnoreCase(mode)) return Leveling.OFF;
        return level(mode, Settings.getReplayGainTarget(context), Settings.getReplayGainPreamp(context),
                Settings.isReplayGainPreventClipping(context), getReplayGain(path),
                "attenuate".equals(Settings.getReplayGainUntaggedPolicy(context)));
    }

    public static Leveling level(String mode, float targetLufs, float preAmpDb, boolean preventClipping, ReplayGainInfo info) {
        return level(mode, targetLufs, preAmpDb, preventClipping, info, true);
    }

    public static Leveling level(String mode, float targetLufs, float preAmpDb, boolean preventClipping,
                                 ReplayGainInfo info, boolean attenuateUntagged) {
        if ("off".equalsIgnoreCase(mode)) return Leveling.OFF;
        if (!Float.isFinite(targetLufs)) targetLufs = (float) REFERENCE_LUFS;
        if (!Float.isFinite(preAmpDb)) preAmpDb = 0f;
        // Tags carry the gain to the ReplayGain 2 reference; a louder target needs less cut
        double targetOffsetDb = targetLufs - REFERENCE_LUFS;

        double gainDb;
        double peak;
        boolean album = false;
        if (info != null && "album".equalsIgnoreCase(mode) && info.hasAlbumGain && isValidGain(info.albumGainDb)) {
            gainDb = info.albumGainDb;
            peak = validPeakOrDefault(info.albumPeak);
            album = true;
        } else if (info != null && info.hasTrackGain && isValidGain(info.trackGainDb)) {
            gainDb = info.trackGainDb;
            peak = validPeakOrDefault(info.trackPeak);
        } else {
            if (!attenuateUntagged) return new Leveling(Leveling.Kind.NO_TAGS, 1.0f, false);
            // Untagged tracks are assumed to be typical modern masters; with no peak known they are never raised
            double untaggedDb = Math.min(0.0, targetLufs - UNTAGGED_MASTER_LUFS + preAmpDb);
            return new Leveling(Leveling.Kind.NO_TAGS, (float) Math.max(0.01, Math.pow(10.0, untaggedDb / 20.0)), false);
        }

        double linearScale = Math.pow(10.0, (gainDb + targetOffsetDb + preAmpDb) / 20.0);

        // With the guard, the loudest sample may reach but not pass the limiter threshold; without it, only lower
        double ceiling = preventClipping ? Math.min(LIMITER_THRESHOLD / peak, MAX_BOOST) : 1.0;
        return new Leveling(Leveling.Kind.APPLIED, (float) Math.max(0.01, Math.min(linearScale, ceiling)), album);
    }

    /**
     * Writes ReplayGain 2.0 tags into the file so this app and other players (Poweramp, foobar2000,
     * UAPP) level it. A null {@code album} leaves any album tags already in the file alone.
     * @return false when the file could not be written or its tag format has no ReplayGain fields
     */
    public boolean writeReplayGainToFile(String filePath, Loudness track, Loudness album) {
        if (filePath == null || filePath.isEmpty() || track == null) return false;
        File file = new File(filePath);
        ReplayGainInfo before = getReplayGain(filePath);
        try {
            boolean[] supported = {true};
            FileSystem.access().edit(file, workingFile -> {
                AudioFile audioFile = AudioFileIO.read(workingFile);
                Tag tag = audioFile.getTagOrCreateAndSetDefault();
                if (tag instanceof FlacTag flacTag) tag = flacTag.getVorbisCommentTag();
                if (tag == null || !writeFields(tag, "TRACK", track) || (album != null && !writeFields(tag, "ALBUM", album))) {
                    supported[0] = false;
                    return;
                }
                audioFile.commit();
            });
            if (!supported[0]) {
                Log.w(TAG, "No ReplayGain fields for the tag format of " + filePath);
                return false;
            }

            ReplayGainInfo info = new ReplayGainInfo();
            info.trackGainDb = track.gainDb();
            info.trackPeak = track.peakLinear();
            info.hasTrackGain = true;
            if (album != null) {
                info.albumGainDb = album.gainDb();
                info.albumPeak = album.peakLinear();
                info.hasAlbumGain = true;
            } else if (before != null && before.hasAlbumGain) {
                info.albumGainDb = before.albumGainDb;
                info.albumPeak = before.albumPeak;
                info.hasAlbumGain = true;
            }
            cache.put(filePath, info);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to write ReplayGain to " + filePath, e);
            return false;
        }
    }

    /** Sets REPLAYGAIN_{scope}_GAIN and _PEAK; false when the tag format has nowhere to put them. */
    private boolean writeFields(Tag tag, String scope, Loudness loudness) throws Exception {
        String gainKey = "REPLAYGAIN_" + scope + "_GAIN";
        String peakKey = "REPLAYGAIN_" + scope + "_PEAK";
        String gain = String.format(Locale.US, "%.2f dB", loudness.gainDb());
        String peak = String.format(Locale.US, "%.6f", loudness.peakLinear());
        if (tag instanceof VorbisCommentTag vorbis) {
            vorbis.setField(gainKey, gain);
            vorbis.setField(peakKey, peak);
        } else if (tag instanceof AbstractID3v2Tag id3) {
            addOrUpdateTxxx(id3, gainKey, gain);
            addOrUpdateTxxx(id3, peakKey, peak);
        } else if (tag instanceof Mp4Tag mp4) {
            mp4.setField(new Mp4TagReverseDnsField(MP4_ITUNES + gainKey.toLowerCase(Locale.US), "com.apple.iTunes", gainKey.toLowerCase(Locale.US), gain));
            mp4.setField(new Mp4TagReverseDnsField(MP4_ITUNES + peakKey.toLowerCase(Locale.US), "com.apple.iTunes", peakKey.toLowerCase(Locale.US), peak));
        } else {
            return false;
        }
        return true;
    }

    static void parseVorbisReplayGain(VorbisCommentTag vorbis, ReplayGainInfo info) {
        String tg = vorbis.getFirst("REPLAYGAIN_TRACK_GAIN");
        if (tg != null && !tg.isEmpty()) {
            setGain(info, tg, false);
        }
        String tp = vorbis.getFirst("REPLAYGAIN_TRACK_PEAK");
        if (tp != null && !tp.isEmpty()) {
            info.trackPeak = parsePeak(tp);
        }
        String ag = vorbis.getFirst("REPLAYGAIN_ALBUM_GAIN");
        if (ag != null && !ag.isEmpty()) {
            setGain(info, ag, true);
        }
        String ap = vorbis.getFirst("REPLAYGAIN_ALBUM_PEAK");
        if (ap != null && !ap.isEmpty()) {
            info.albumPeak = parsePeak(ap);
        }
    }

    private void parseId3ReplayGain(AbstractID3v2Tag id3, ReplayGainInfo info) {
        List<org.jaudiotagger.tag.TagField> txxxFields = id3.getFields("TXXX");
        if (txxxFields != null) {
            for (org.jaudiotagger.tag.TagField field : txxxFields) {
                if (field instanceof AbstractID3v2Frame frame && frame.getBody() instanceof FrameBodyTXXX body) {
                    String desc = body.getDescription();
                    String text = body.getText();
                    if ("REPLAYGAIN_TRACK_GAIN".equalsIgnoreCase(desc)) {
                        setGain(info, text, false);
                    } else if ("REPLAYGAIN_TRACK_PEAK".equalsIgnoreCase(desc)) {
                        info.trackPeak = parsePeak(text);
                    } else if ("REPLAYGAIN_ALBUM_GAIN".equalsIgnoreCase(desc)) {
                        setGain(info, text, true);
                    } else if ("REPLAYGAIN_ALBUM_PEAK".equalsIgnoreCase(desc)) {
                        info.albumPeak = parsePeak(text);
                    }
                }
            }
        }
    }

    private void parseMp4ReplayGain(Mp4Tag mp4, ReplayGainInfo info) {
        String tg = mp4.getFirst("----:com.apple.iTunes:replaygain_track_gain");
        if (tg != null && !tg.isEmpty()) {
            setGain(info, tg, false);
        }
        String tp = mp4.getFirst("----:com.apple.iTunes:replaygain_track_peak");
        if (tp != null && !tp.isEmpty()) {
            info.trackPeak = parsePeak(tp);
        }
        String ag = mp4.getFirst("----:com.apple.iTunes:replaygain_album_gain");
        if (ag != null && !ag.isEmpty()) {
            setGain(info, ag, true);
        }
        String ap = mp4.getFirst("----:com.apple.iTunes:replaygain_album_peak");
        if (ap != null && !ap.isEmpty()) {
            info.albumPeak = parsePeak(ap);
        }
    }

    private void addOrUpdateTxxx(AbstractID3v2Tag tag, String key, String value) {
        if (value == null || value.isEmpty()) return;
        try {
            FrameBodyTXXX body = new FrameBodyTXXX();
            body.setDescription(key);
            body.setText(value);

            AbstractID3v2Frame frame = (tag instanceof ID3v22Tag) ? tag.createFrame("TXX") : tag.createFrame("TXXX");
            frame.setBody(body);
            tag.setField(frame);
        } catch (Exception e) {
            Log.w(TAG, "Failed to write TXXX " + key + ": " + e.getMessage());
        }
    }

    private static boolean isValidGain(double gain) {
        return Double.isFinite(gain) && Math.abs(gain) <= MAX_GAIN_DB;
    }

    private static double validPeakOrDefault(double peak) {
        return Double.isFinite(peak) && peak > 0.0 && peak <= MAX_PEAK ? peak : 1.0;
    }

    private static void setGain(ReplayGainInfo info, String text, boolean album) {
        double gain = parseGainValue(text);
        boolean valid = isValidGain(gain);
        if (album) {
            info.albumGainDb = valid ? gain : 0.0;
            info.hasAlbumGain = valid;
        } else {
            info.trackGainDb = valid ? gain : 0.0;
            info.hasTrackGain = valid;
        }
    }

    private static double parseGainValue(String text) {
        if (text == null) return Double.NaN;
        Matcher number = REPLAY_GAIN_NUMBER.matcher(text.trim());
        if (!number.matches()) return Double.NaN;
        try {
            double gain = Double.parseDouble(number.group(1));
            return isValidGain(gain) ? gain : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    public static double parseGain(String gainStr) {
        double gain = parseGainValue(gainStr);
        return isValidGain(gain) ? gain : 0.0;
    }

    public static double parsePeak(String peakStr) {
        if (peakStr == null) return 1.0;
        Matcher number = REPLAY_GAIN_NUMBER.matcher(peakStr.trim());
        if (!number.matches()) return 1.0;
        try {
            double peak = Double.parseDouble(number.group(1));
            if (number.group(2) != null) peak = Math.pow(10.0, peak / 20.0);
            return validPeakOrDefault(peak);
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }
}

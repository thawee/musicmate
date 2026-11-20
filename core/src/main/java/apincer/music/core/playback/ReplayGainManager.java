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
                    info.trackGainDb = parseGain(tg);
                    info.hasTrackGain = true;
                }
            }

            cache.put(filePath, info);
            return info;

        } catch (Exception e) {
            Log.w(TAG, "Failed to read ReplayGain tags from " + filePath + ": " + e.getMessage());
            return null;
        }
    }

    /** Reads the leveling settings and this file's tags; does file I/O on a cache miss. */
    public Leveling leveling(Context context, String path) {
        if (context == null || path == null) return Leveling.OFF;
        String mode = Settings.getReplayGainMode(context);
        if ("off".equalsIgnoreCase(mode)) return Leveling.OFF;
        return level(mode, Settings.getReplayGainTarget(context), Settings.getReplayGainPreamp(context),
                Settings.isReplayGainPreventClipping(context), getReplayGain(path));
    }

    public static Leveling level(String mode, float targetLufs, float preAmpDb, boolean preventClipping, ReplayGainInfo info) {
        if ("off".equalsIgnoreCase(mode)) return Leveling.OFF;
        // Tags carry the gain to the ReplayGain 2 reference; a louder target needs less cut
        double targetOffsetDb = targetLufs - REFERENCE_LUFS;

        double gainDb;
        double peak;
        boolean album = false;
        if (info != null && "album".equalsIgnoreCase(mode) && info.hasAlbumGain) {
            gainDb = info.albumGainDb;
            peak = info.albumPeak > 0 ? info.albumPeak : (info.trackPeak > 0 ? info.trackPeak : 1.0);
            album = true;
        } else if (info != null && info.hasTrackGain) {
            gainDb = info.trackGainDb;
            peak = info.trackPeak > 0 ? info.trackPeak : 1.0;
        } else {
            // Untagged tracks are assumed to be typical modern masters; with no peak known they are never raised
            double untaggedDb = Math.min(0.0, targetLufs - UNTAGGED_MASTER_LUFS + preAmpDb);
            return new Leveling(Leveling.Kind.NO_TAGS, (float) Math.pow(10.0, untaggedDb / 20.0), false);
        }

        double linearScale = Math.pow(10.0, (gainDb + targetOffsetDb + preAmpDb) / 20.0);

        // With the guard, the loudest sample may reach but not pass full scale; without it, only lower
        double ceiling = preventClipping ? Math.min(1.0 / peak, MAX_BOOST) : 1.0;
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

    private void parseVorbisReplayGain(VorbisCommentTag vorbis, ReplayGainInfo info) {
        String tg = vorbis.getFirst("REPLAYGAIN_TRACK_GAIN");
        if (tg != null && !tg.isEmpty()) {
            info.trackGainDb = parseGain(tg);
            info.hasTrackGain = true;
        }
        String tp = vorbis.getFirst("REPLAYGAIN_TRACK_PEAK");
        if (tp != null && !tp.isEmpty()) {
            info.trackPeak = parsePeak(tp);
        }
        String ag = vorbis.getFirst("REPLAYGAIN_ALBUM_GAIN");
        if (ag != null && !ag.isEmpty()) {
            info.albumGainDb = parseGain(ag);
            info.hasAlbumGain = true;
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
                        info.trackGainDb = parseGain(text);
                        info.hasTrackGain = true;
                    } else if ("REPLAYGAIN_TRACK_PEAK".equalsIgnoreCase(desc)) {
                        info.trackPeak = parsePeak(text);
                    } else if ("REPLAYGAIN_ALBUM_GAIN".equalsIgnoreCase(desc)) {
                        info.albumGainDb = parseGain(text);
                        info.hasAlbumGain = true;
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
            info.trackGainDb = parseGain(tg);
            info.hasTrackGain = true;
        }
        String tp = mp4.getFirst("----:com.apple.iTunes:replaygain_track_peak");
        if (tp != null && !tp.isEmpty()) {
            info.trackPeak = parsePeak(tp);
        }
        String ag = mp4.getFirst("----:com.apple.iTunes:replaygain_album_gain");
        if (ag != null && !ag.isEmpty()) {
            info.albumGainDb = parseGain(ag);
            info.hasAlbumGain = true;
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

    public static double parseGain(String gainStr) {
        if (gainStr == null) return 0.0;
        String clean = gainStr.replace("dB", "").replace("db", "").replace("+", "").trim();
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public static double parsePeak(String peakStr) {
        if (peakStr == null) return 1.0;
        String clean = peakStr.trim();
        try {
            if (clean.toLowerCase(Locale.US).contains("db")) {
                double peakDb = parseGain(clean);
                return Math.pow(10.0, peakDb / 20.0);
            }
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }
}

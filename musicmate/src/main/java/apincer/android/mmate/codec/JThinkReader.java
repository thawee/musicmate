package apincer.android.mmate.codec;

import static apincer.android.mmate.repository.FileRepository.isMediaFileExist;
import static apincer.android.mmate.repository.MusicAnalyser.process;
import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.toBoolean;
import static apincer.android.mmate.utils.StringUtils.toDouble;
import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import android.content.Context;
import android.os.Looper;
import android.util.Log;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.audio.mp3.MP3AudioHeader;
import org.jaudiotagger.audio.mp4.Mp4AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.TagOptionSingleton;
import org.jaudiotagger.tag.TagTextField;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.id3.valuepair.TextEncoding;
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag;
import org.jaudiotagger.tag.wav.WavInfoTag;
import org.jaudiotagger.tag.wav.WavTag;

import java.io.File;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.LogHelper;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;

public class JThinkReader extends TagReader{
    private static final String TAG = "JThinkReader";
    private final Context context;
    private static boolean tagOptionsInitialized = false;

    // Reusable map for temporary tag storage
    private final Map<String, String> tempTagsMap = new HashMap<>();

    private static final String KEY_TAG_WAVE_GROUP = "IKEY";
    private static final String KEY_TAG_WAVE_TRACK = "IPRT"; //track
    private static final String KEY_TAG_WAVE_ALBUM_ARTIST = "IENG";
    private static final String KEY_TAG_WAVE_QUALITY = "ISBJ";

    public static final String KEY_TAG_MP3_COMMENT = "COMMENT";

    public JThinkReader(Context context) {
        LogHelper.initial();
        this.context = context;

        // Initialize tag options once
        synchronized(JThinkReader.class) {
            if (!tagOptionsInitialized) {
                setupTagOptions();
                tagOptionsInitialized = true;
            }
        }
    }

    @Override
    protected List<MusicTag> read(String mediaPath) {
        Log.i(TAG, "read: path - " + mediaPath);
        MusicTag tag = new MusicTag();
        tag.setPath(mediaPath);
        readFileInfo(context, tag);
        tag.setAudioStartTime(0);

        AudioFile audioFile = getAudioFile(mediaPath);
        if(audioFile != null) {
            readHeader(audioFile, tag);
            readTags(audioFile, tag);
        }
        return Collections.singletonList(tag);
    }

    @Override
    protected List<MusicTag> readFully(String mediaPath) {
        // Ensure this method is called from a background thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "Tag reading should not be done on the main thread");
        }

        Log.i(TAG, "readFully: path - " + mediaPath);
        MusicTag tag = new MusicTag();
        tag.setPath(mediaPath);
        readFileInfo(context, tag);
        tag.setAudioStartTime(0);

        AudioFile audioFile = getAudioFile(mediaPath);
        if (audioFile != null) {
            readHeader(audioFile, tag);
            readTags(audioFile, tag);
            process(tag);
        }

        return Collections.singletonList(tag);
    }

    private void readHeader(AudioFile read, MusicTag metadata) {
        try {
            AudioHeader header = read.getAudioHeader();
            if(header != null) {
                // Batch set properties to reduce method calls
                metadata.setAudioEncoding(detectAudioEncoding(read, header));
                metadata.setAudioSampleRate(header.getSampleRateAsNumber());
                metadata.setAudioBitsDepth(header.getBitsPerSample());
                metadata.setAudioBitRate(header.getBitRateAsNumber() * 1000);

                // Set duration based on header type
                if (header instanceof MP3AudioHeader || header instanceof Mp4AudioHeader) {
                    metadata.setAudioDuration(header.getPreciseTrackLength());
                } else {
                    metadata.setAudioDuration(header.getTrackLength());
                }

                // Set channel info
                String channels = header.getChannels();
                metadata.setAudioChannels(channels);
                if (channels != null && channels.toLowerCase().contains("stereo")) {
                    metadata.setAudioChannels("2");
                }
            }
        }catch (Exception ex) {
            Log.e(TAG, "readHeader: ",ex);
        }
    }

    private AudioFile getAudioFile(String path) {
        if (!isMediaFileExist(path)) {
            return null;
        }

        try {
            return AudioFileIO.read(new File(path));
        } catch (CannotReadException | IOException | TagException | ReadOnlyFileException |
                 InvalidAudioFrameException |
                 BufferUnderflowException e) {
            Log.e(TAG, "getAudioFile: "+path +" - "+ e.getMessage());
        }
        return null;
    }

    private static void setupTagOptions() {
        TagOptionSingleton instance = TagOptionSingleton.getInstance();
        instance.setAndroid(true);
        instance.setId3v23DefaultTextEncoding(TextEncoding.ISO_8859_1);
        instance.setId3v24DefaultTextEncoding(TextEncoding.UTF_8);
        instance.setId3v24UnicodeTextEncoding(TextEncoding.UTF_16);
    }

    private void readTags(AudioFile audioFile, MusicTag metadata) {
        // Ensure this method is called from a background thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "Tag reading should not be done on the main thread");
        }

        Tag tag = audioFile.getTag();
        if (tag != null && !tag.isEmpty()) {
            try {
                // Get title or use filename
                String title = getId3TagValue(tag, FieldKey.TITLE);
                metadata.setTitle(!isEmpty(title) ? title : audioFile.getFile().getName());

                // Read common metadata fields in batch
                metadata.setAlbum(getId3TagValue(tag, FieldKey.ALBUM));
                metadata.setArtist(getId3TagValue(tag, FieldKey.ARTIST));
                metadata.setAlbumArtist(getId3TagValue(tag, FieldKey.ALBUM_ARTIST));
                metadata.setGenre(getId3TagValue(tag, FieldKey.GENRE));
                metadata.setYear(getId3TagValue(tag, FieldKey.YEAR));
                metadata.setTrack(getId3TagValue(tag, FieldKey.TRACK));
                metadata.setComposer(getId3TagValue(tag, FieldKey.COMPOSER));
                metadata.setCompilation(toBoolean(getId3TagValue(tag, FieldKey.IS_COMPILATION)));

                // Check for artwork
                if (tag.getFirstArtwork() != null) {
                    metadata.setCoverartMime(tag.getFirstArtwork().getMimeType());
                }

                // Process format-specific tags
                processFormatSpecificTags(tag, metadata);
            } catch (Exception e) {
                Log.e(TAG, "Error reading tags: ", e);
            }
        }
    }

    private void processFormatSpecificTags(Tag tag, MusicTag metadata) {
        // Clear reusable map for this operation
        tempTagsMap.clear();

        // Parse custom tag fields into map
        parseCustomTagFields(tag, tempTagsMap);

        // Extract values from map that we're interested in
        metadata.setPublisher(tempTagsMap.get(KEY_TAG_PUBLISHER));
        metadata.setMediaQuality(tempTagsMap.get(KEY_TAG_QUALITY));
        metadata.setMediaType(tempTagsMap.get(KEY_TAG_MEDIA));

        // Handle format-specific tags
        if (MusicTagUtils.isWavFile(metadata)) {
            processWaveSpecificTags(metadata, tempTagsMap, tag);
        } else if (MusicTagUtils.isMPegFile(metadata)) {
            processMpegSpecificTags(metadata, tempTagsMap);
        } else {
            // Standard files support these tags directly
            metadata.setDisc(getId3TagValue(tag, FieldKey.DISC_NO));
            metadata.setGrouping(getId3TagValue(tag, FieldKey.GROUPING));
            metadata.setMediaQuality(getId3TagValue(tag, FieldKey.QUALITY));
            metadata.setMediaType(getId3TagValue(tag, FieldKey.MEDIA));
            metadata.setComment(getId3TagValue(tag, FieldKey.COMMENT));
        }
    }

    private void processWaveSpecificTags(MusicTag metadata, Map<String, String> tags, Tag tag) {
        // Get wave-specific tags
        metadata.setGrouping(tags.get(KEY_TAG_WAVE_GROUP));
        metadata.setAlbumArtist(tags.get(KEY_TAG_WAVE_ALBUM_ARTIST));
        metadata.setTrack(tags.get(KEY_TAG_WAVE_TRACK));
        metadata.setMediaQuality(tags.get(KEY_TAG_WAVE_QUALITY));

        // Process special comment format for WAV
        String comment = tag.getFirst(FieldKey.COMMENT);
        if (!isEmpty(comment)) {
            processWaveComment(metadata, comment);
        }
    }

    private void processMpegSpecificTags(MusicTag metadata, Map<String, String> tags) {
        String comment = tags.get(KEY_TAG_MP3_COMMENT);
        if (!isEmpty(comment)) {
            processWaveComment(metadata, comment);
        }
    }

    private void processWaveComment(MusicTag metadata, String comment) {
        int start = comment.indexOf("<##>");
        int end = comment.indexOf("</##>");

        if (start >= 0 && end > start) {
            // Found metadata comment
            String mdata = comment.substring(start + 4, end);

            // Extract user comment portion (if any)
            if (comment.length() > (end + 5)) {
                comment = comment.substring(end + 5);
            } else {
                comment = "";
            }

            // Parse metadata fields
            String[] text = mdata.split("#", -1);

            // Batch set values to avoid multiple conditionals
            int fieldCount = text.length;
            if (fieldCount > 0) metadata.setDisc(extractField(text, 0));
            if (fieldCount > 1) metadata.setGrouping(extractField(text, 1));
            if (fieldCount > 2) metadata.setMediaQuality(extractField(text, 2));
            // Skip field 3 (rating) as it's commented out
            if (fieldCount > 4) metadata.setAlbumArtist(extractField(text, 4));
            if (fieldCount > 5) metadata.setComposer(extractField(text, 5));
            if (fieldCount > 6) metadata.setDynamicRangeScore(toDouble(extractField(text, 6)));
            if (fieldCount > 7) metadata.setDynamicRange(toDouble(extractField(text, 7)));
        }

        metadata.setComment(comment);
    }

    private String getId3TagValue(Tag id3Tag, FieldKey key) {
        if (id3Tag != null && id3Tag.hasField(key)) {
            return StringUtils.trimToEmpty(id3Tag.getFirst(key));
        }
        return "";
    }

    private void parseCustomTagFields(Tag tag, Map<String, String> resultMap) {
        try {
            if (tag instanceof VorbisCommentTag) {
                parseVorbisTags((VorbisCommentTag) tag, resultMap);
            } else if (tag instanceof FlacTag) {
                parseVorbisTags(((FlacTag) tag).getVorbisCommentTag(), resultMap);
            } else if (tag instanceof WavTag) {
                parseWaveTags(((WavTag) tag).getInfoTag(), resultMap);
            } else {
                parseId3Tags(tag, resultMap);
            }
        } catch (Exception e) {
            Log.e(TAG, "parseCustomTagFields error: ", e);
        }
    }

    private void parseWaveTags(WavInfoTag infoTag, Map<String, String> resultMap) {
        if (infoTag == null) return;

        List<TagTextField> fields = infoTag.getUnrecognisedFields();
        for (TagTextField field : fields) {
            resultMap.put(field.getId(), trimToEmpty(field.getContent()));
        }
    }

    private static void parseId3Tags(Tag tag, Map<String, String> resultMap) {
        String id = null;

        if (tag.hasField("TXXX")) {
            id = "TXXX";
        } else if (tag.hasField("RGAD")) {
            id = "RGAD";
        } else if (tag.hasField("RVA2")) {
            id = "RVA2";
        }

        if (id == null) {
            return;
        }

        for (TagField field : tag.getFields(id)) {
            String fieldStr = field.toString();
            if (isEmpty(fieldStr)) continue;

            String[] data = fieldStr.split(";");
            if (data.length < 2) continue;

            String key = data[0].substring(13, data[0].length() - 1).toUpperCase();
            resultMap.put(key, extractId3Val(data[1]));
        }
    }

    private static void parseVorbisTags(VorbisCommentTag tag, Map<String, String> resultMap) {
        if (tag == null) return;

        List<TagField> list = tag.getAll();
        for (TagField field : list) {
            String id = field.getId();
            if (!isEmpty(id)) {
                resultMap.put(id, StringUtils.trimToEmpty(tag.getFirst(id)));
            }
        }
    }

    private MusicTag readTags2(AudioFile audioFile, MusicTag metadata) {
            Tag tag = audioFile.getTag();
            if (tag != null && !tag.isEmpty()) {

                String title = getId3TagValue(tag, FieldKey.TITLE);
                if (!StringUtils.isEmpty(title)) {
                    metadata.setTitle(title);
                }else {
                    // file name as title
                    metadata.setTitle(audioFile.getFile().getName());
                }
                metadata.setAlbum(getId3TagValue(tag, FieldKey.ALBUM));
                metadata.setArtist(getId3TagValue(tag, FieldKey.ARTIST));
                metadata.setAlbumArtist(getId3TagValue(tag, FieldKey.ALBUM_ARTIST));
                metadata.setGenre(getId3TagValue(tag, FieldKey.GENRE));
                metadata.setYear(getId3TagValue(tag, FieldKey.YEAR));
                metadata.setTrack(getId3TagValue(tag, FieldKey.TRACK));
                metadata.setComposer(getId3TagValue(tag, FieldKey.COMPOSER));
                metadata.setCompilation(toBoolean(getId3TagValue(tag, FieldKey.IS_COMPILATION)));
                if(tag.getFirstArtwork() != null) {
                    metadata.setCoverartMime(tag.getFirstArtwork().getMimeType());
                }

                // read replay gain fields
                Map<String, String> tags = parseCustomTagFields(tag);

                if (tags.containsKey(KEY_TAG_PUBLISHER)) {
                    metadata.setPublisher(tags.get(KEY_TAG_PUBLISHER));
                }
                if (tags.containsKey(KEY_TAG_QUALITY)) {
                    metadata.setMediaQuality(tags.get(KEY_TAG_QUALITY));
                }
                if (tags.containsKey(KEY_TAG_MEDIA)) {
                    metadata.setMediaQuality(tags.get(KEY_TAG_MEDIA));
                }

                // MQA, detect by mqa identifier

                //  if ("wav".equalsIgnoreCase(metadata.getFileExtension()) || "dsf".equalsIgnoreCase(metadata.getFileExtension())) {
                if (MusicTagUtils.isWavFile(metadata)) {
                    // wave, not support disk no, grouping, media, quality - write to comment
                    // parseWaveCommentTag(metadata, getId3TagValue(tag, FieldKey.COMMENT));
                    metadata.setGrouping(tags.get(KEY_TAG_WAVE_GROUP));
                    metadata.setAlbumArtist(tags.get(KEY_TAG_WAVE_ALBUM_ARTIST));
                    metadata.setTrack(tags.get(KEY_TAG_WAVE_TRACK));
                    metadata.setMediaQuality(tags.get(KEY_TAG_WAVE_QUALITY));

                    String comment = tag.getFirst(FieldKey.COMMENT);
                    int start = comment.indexOf("<##>");
                    int end = comment.indexOf("</##>");
                    if (start >= 0 && end > start) {
                        // found metadata comment
                        String mdata = comment.substring(start + 4, end);
                        if (comment.length() > (end + 5)) {
                            comment = comment.substring(end + 5);
                        } else {
                            comment = "";
                        }

                        String[] text = mdata.split("#", -1);

                        metadata.setDisc(extractField(text, 0));
                        metadata.setGrouping(extractField(text, 1));
                        metadata.setMediaQuality(extractField(text, 2));
                      //  metadata.setRating(toInt(extractField(text, 3)));
                        metadata.setAlbumArtist(extractField(text, 4));
                        metadata.setComposer(extractField(text, 5));
                        metadata.setDynamicRangeScore(toDouble(extractField(text, 6)));
                        metadata.setDynamicRange(toDouble(extractField(text, 7)));
                    }
                    metadata.setComment(comment);
                }else if (MusicTagUtils.isMPegFile(metadata)) {
                    String comment = tags.get(KEY_TAG_MP3_COMMENT);
                    if(!isEmpty(comment)) {
                        int start = comment.indexOf("<##>");
                        int end = comment.indexOf("</##>");
                        if (start >= 0 && end > start) {
                            // found metadata comment
                            String mdata = comment.substring(start + 4, end);
                            if (comment.length() > (end + 5)) {
                                comment = comment.substring(end + 5);
                            } else {
                                comment = "";
                            }

                            String[] text = mdata.split("#", -1);

                            metadata.setDisc(extractField(text, 0));
                            metadata.setGrouping(extractField(text, 1));
                            metadata.setMediaQuality(extractField(text, 2));
                         //   metadata.setRating(toInt(extractField(text, 3)));
                            metadata.setAlbumArtist(extractField(text, 4));
                            metadata.setComposer(extractField(text, 5));
                            metadata.setDynamicRangeScore(toDouble(extractField(text, 6)));
                            metadata.setDynamicRange(toDouble(extractField(text, 7)));
                        }
                        metadata.setComment(comment);
                    }
                } else {
                    // WAV file not support these fields
                    metadata.setDisc(getId3TagValue(tag, FieldKey.DISC_NO));
                    metadata.setGrouping(getId3TagValue(tag, FieldKey.GROUPING));
                    metadata.setMediaQuality(getId3TagValue(tag, FieldKey.QUALITY));
                    metadata.setMediaType(getId3TagValue(tag, FieldKey.MEDIA));
                    metadata.setComment(getId3TagValue(tag, FieldKey.COMMENT));
                }
            }
            return metadata;
    }

    private Map<String, String> parseCustomTagFields(Tag tag) {
        Map<String, String> tags = null;
        try {
            if (tag instanceof VorbisCommentTag) {
                tags = parseVorbisTags((VorbisCommentTag) tag);
            } else if (tag instanceof FlacTag) {
                tags = parseVorbisTags(((FlacTag) tag).getVorbisCommentTag());
            } else if (tag instanceof WavTag) {
                tags = parseWaveTags(((WavTag) tag).getInfoTag());
            } else {
                tags = parseId3Tags(tag);
            }
        } catch (Exception e) {
            Log.e(TAG, "parseCustomTagFields", e);
        }
        return tags;
    }

    private Map<String, String> parseWaveTags(WavInfoTag infoTag) {
        Map<String, String> tags = new HashMap<>();
        List<TagTextField> fields = infoTag.getUnrecognisedFields();
        for(TagTextField field: fields) {
            tags.put(field.getId(), trimToEmpty(field.getContent()));
        }
        return tags;
    }

    private static Map<String, String> parseId3Tags(Tag tag) {
        String id = null;

        if (tag.hasField("TXXX")) {
            id = "TXXX";
        } else if (tag.hasField("RGAD")) {    // may support legacy metadata formats: RGAD, RVA2
            id = "RGAD";
        } else if (tag.hasField("RVA2")) {
            id = "RVA2";
        }

        Map<String, String> tags = new HashMap<>();

        for (TagField field : tag.getFields(id)) {
            String[] data = field.toString().split(";");

            data[0] = data[0].substring(13, data[0].length() - 1).toUpperCase();

            tags.put(data[0], extractId3Val(data[1]));
        }

        return tags;
    }

    private static String extractId3Val(String datum) {
        if(isEmpty(datum)) return "";
        String val = trimToEmpty(datum);
        if(datum.contains("=")) {
            val = datum.split("=",-1)[1];
            val = val.substring(1, val.length()-1);
        }
        return trimToEmpty(val);
    }

    private static Map<String, String> parseVorbisTags(VorbisCommentTag tag) {
        Map<String, String> tags = new HashMap<>();

        List<TagField> list  = tag.getAll();
        for (TagField field: list) {
            tags.put(field.getId(), StringUtils.trimToEmpty(tag.getFirst(field.getId())));
        }

        return tags;
    }
}

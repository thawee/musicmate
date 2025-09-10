package apincer.android.mmate.codec;

import static apincer.android.mmate.repository.FileRepository.isMediaFileExist;
import static apincer.android.mmate.codec.MusicAnalyser.analyse;
import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.toBoolean;
import static apincer.android.mmate.utils.StringUtils.toLong;
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
import org.jaudiotagger.tag.reference.ID3V2Version;
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag;
import org.jaudiotagger.tag.wav.WavInfoTag;
import org.jaudiotagger.tag.wav.WavTag;

import java.io.File;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.repository.database.MusicTag;
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

    //public static final String KEY_TAG_MP3_COMMENT = "COMMENT";

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
    protected MusicTag readBasicTag(String mediaPath) {
        Log.i(TAG, "readBasicTag: " + mediaPath);
        // Ensure this method is called from a background thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "Tag reading should not be done on the main thread");
            return null;
        }

        MusicTag tag = new MusicTag();

        // Only read file info, skip audio processing
        tag.setPath(mediaPath);
        readFileInfo(context, tag);
        tag.setAudioStartTime(0);

        AudioFile audioFile = getAudioFile(mediaPath);
        if(audioFile != null) {
            readHeader(audioFile, tag);
            readTags(audioFile, tag);
            tag.setQualityInd(MusicTagUtils.getQualityIndicator(tag));
        }

        return tag;
    }

    @Override
    protected boolean readFullTag(MusicTag tag) {
        Log.i(TAG, "readFullTag: " + tag.getPath());

        // Ensure this method is called from a background thread
       // if (Looper.myLooper() == Looper.getMainLooper()) {
       //     Log.w(TAG, "Tag reading should not be done on the main thread");
        //    return false;
       // }
        try {
            readFileInfo(context, tag);
            tag.setAudioStartTime(0);

            AudioFile audioFile = getAudioFile(tag.getPath());
            if (audioFile != null) {
                readHeader(audioFile, tag);
                readTags(audioFile, tag);
                tag.setQualityInd(MusicTagUtils.getQualityIndicator(tag));
            }
            analyse(tag);
            return true;
        }catch (Exception ex) {
            Log.d("readFullTag", ex.getMessage());
        }

        return false;
    }

    @Override
    protected boolean readExtras(MusicTag tag) {
        Log.i(TAG, "readExtras: " + tag.getPath());
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "Tag reading should not be done on the main thread");
            return false;
        }

        try {
            return analyse(tag);
        }catch (Exception ex) {
            Log.d("readFullTag", ex.getMessage());
        }

        return false;
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
        instance.setID3V2Version(ID3V2Version.ID3_V24);
        instance.setId3v23DefaultTextEncoding(TextEncoding.ISO_8859_1);
        instance.setId3v24DefaultTextEncoding(TextEncoding.UTF_8);
        instance.setId3v24UnicodeTextEncoding(TextEncoding.UTF_16);
    }

    private void readTags(AudioFile audioFile, MusicTag metadata) {
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
                metadata.setGrouping(getId3TagValue(tag, FieldKey.GROUPING));
                metadata.setYear(getId3TagValue(tag, FieldKey.YEAR));
                metadata.setTrack(getId3TagValue(tag, FieldKey.TRACK));
                metadata.setComposer(getId3TagValue(tag, FieldKey.COMPOSER));
                metadata.setCompilation(toBoolean(getId3TagValue(tag, FieldKey.IS_COMPILATION)));

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
        metadata.setQualityRating(tempTagsMap.get(KEY_TAG_QUALITY));
        if (tempTagsMap.containsKey(KEY_TAG_MQA_ENCODER)) {
            metadata.setQualityInd("MQA");
            metadata.setMqaSampleRate(toLong(tempTagsMap.get(KEY_TAG_ORIGINALSAMPLERATE)));
            if(metadata.getMqaSampleRate() == 0) {
                metadata.setMqaSampleRate(metadata.getAudioSampleRate());
            }
        }

        // Handle format-specific tags
        if (MusicTagUtils.isWavFile(metadata)) {
            metadata.setGrouping(getId3TagValue(tag, FieldKey.RECORD_LABEL));
           // processWaveSpecificTags(metadata, tempTagsMap, tag);
       // } else if (MusicTagUtils.isMPegFile(metadata)) {
       //     processMpegSpecificTags(metadata, tempTagsMap);
        } else {
            // Standard files support these tags directly
            metadata.setDisc(getId3TagValue(tag, FieldKey.DISC_NO));
            metadata.setGrouping(getId3TagValue(tag, FieldKey.GROUPING));
            metadata.setQualityRating(getId3TagValue(tag, FieldKey.QUALITY));
          //  metadata.setMediaType(getId3TagValue(tag, FieldKey.MEDIA));
            metadata.setComment(getId3TagValue(tag, FieldKey.COMMENT));
        }
    }

    private void processWaveSpecificTags(MusicTag metadata, Map<String, String> tags, Tag tag) {
        // Get wave-specific tags
        metadata.setGrouping(tags.get(KEY_TAG_WAVE_GROUP));
        metadata.setAlbumArtist(tags.get(KEY_TAG_WAVE_ALBUM_ARTIST));
        metadata.setTrack(tags.get(KEY_TAG_WAVE_TRACK));
        metadata.setQualityRating(tags.get(KEY_TAG_WAVE_QUALITY));
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

    private static String extractId3Val(String datum) {
        if(isEmpty(datum)) return "";
        String val = trimToEmpty(datum);
        if(datum.contains("=")) {
            val = datum.split("=",-1)[1];
            val = val.substring(1, val.length()-1);
        }
        return trimToEmpty(val);
    }

}

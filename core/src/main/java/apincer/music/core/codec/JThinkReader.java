package apincer.music.core.codec;

import static apincer.music.core.codec.MusicAnalyser.analyse;
import static apincer.music.core.repository.FileRepository.isMediaFileExist;
import static apincer.music.core.utils.StringUtils.isEmpty;
import static apincer.music.core.utils.StringUtils.toBoolean;
import static apincer.music.core.utils.StringUtils.toLong;
import static apincer.music.core.utils.StringUtils.trimToEmpty;

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
import org.jaudiotagger.tag.id3.AbstractID3v2Frame;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX;
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

import apincer.music.core.model.AudioTag;
import apincer.music.core.model.Track;
import apincer.music.core.utils.LogHelper;
import apincer.music.core.utils.TagUtils;
import apincer.music.core.utils.StringUtils;

public class JThinkReader extends TagReader{
    private static final String TAG = "JThinkReader";
    private final Context context;
    private static boolean tagOptionsInitialized = false;

    // Reusable map for temporary tag storage
    private final Map<String, String> tempTagsMap = new HashMap<>();

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
    protected AudioTag readBasicTag(String mediaPath) {
        //Log.i(TAG, "readBasicTag: " + mediaPath);
        // Ensure this method is called from a background thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "Tag reading should not be done on the main thread");
            return null;
        }

        AudioTag tag = new AudioTag(generateId(mediaPath,0));

        // Only read file info, skip audio processing
        tag.setPath(mediaPath);
        readFileInfo(context, tag);
        tag.setAudioStartTime(0);

        AudioFile audioFile = getAudioFile(mediaPath);
        if(audioFile != null) {
            readHeader(audioFile, tag);
            readTags(audioFile, tag);
            tag.setQualityInd(TagUtils.getQualityIndicator(tag));
        }

        return tag;
    }

    @Override
    protected boolean readFullTag(Track tag) {
        Log.i(TAG, "readFullTag: " + tag.getPath());

        // Ensure this method is called from a background thread
        try {
            readFileInfo(context, tag);
            tag.setAudioStartTime(0);

            AudioFile audioFile = getAudioFile(tag.getPath());
            if (audioFile != null) {
                readHeader(audioFile, tag);
                readTags(audioFile, tag);
                tag.setQualityInd(TagUtils.getQualityIndicator(tag));
            }
            analyse(tag);
            return true;
        }catch (Exception ex) {
            Log.d("readFullTag", ex.getMessage());
        }

        return false;
    }

    @Override
    protected boolean readExtras(Track tag) {
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

    private void readHeader(AudioFile read, Track metadata) {
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

    private void readTags(AudioFile audioFile, Track metadata) {
        Tag tag = audioFile.getTag();
        if (tag != null && !tag.isEmpty()) {
            try {
                // Get title or use filename
                String title = getTagValue(tag, FieldKey.TITLE);
                metadata.setTitle(!isEmpty(title) ? title : audioFile.getFile().getName());

                // Read common metadata fields in batch
                metadata.setAlbum(getTagValue(tag, FieldKey.ALBUM));
                metadata.setArtist(getTagValue(tag, FieldKey.ARTIST));
                metadata.setAlbumArtist(getTagValue(tag, FieldKey.ALBUM_ARTIST));
                metadata.setGenre(getTagValue(tag, FieldKey.GENRE));
                metadata.setYear(getTagValue(tag, FieldKey.YEAR));
                metadata.setTrack(getTagValue(tag, FieldKey.TRACK));
                //metadata.setBpm(getTagValue(tag, FieldKey.BPM));
                metadata.setComposer(getTagValue(tag, FieldKey.COMPOSER));
                metadata.setCompilation(toBoolean(getTagValue(tag, FieldKey.IS_COMPILATION)));

                // Process format-specific tags
                processFormatSpecificTags(tag, metadata);
            } catch (Exception e) {
                Log.e(TAG, "Error reading tags: ", e);
            }
        }
    }

    private void processFormatSpecificTags(Tag tag, Track metadata) {
        // Clear reusable map for this operation
        tempTagsMap.clear();

        // Parse custom tag fields into map
        parseCustomTagFields(tag, tempTagsMap);

        // Extract values from map that we're interested in
        metadata.setPublisher(tempTagsMap.get(KEY_TAG_PUBLISHER));
       // metadata.setQualityRating(tempTagsMap.get(KEY_TAG_QUALITY));
        if (tempTagsMap.containsKey(KEY_TAG_MQA_ENCODER)) {
            metadata.setQualityInd("MQA");
            metadata.setMqaSampleRate(toLong(tempTagsMap.get(KEY_TAG_ORIGINALSAMPLERATE)));
            if(metadata.getMqaSampleRate() == 0) {
                metadata.setMqaSampleRate(metadata.getAudioSampleRate());
            }
        }

        if (!TagUtils.isWavFile(metadata)) {
            // Standard files support these tags directly
           // metadata.setDisc(getTagValue(tag, FieldKey.DISC_NO));
           // metadata.setQualityRating(getTagValue(tag, FieldKey.QUALITY));
            metadata.setComment(getTagValue(tag, FieldKey.COMMENT));
        }else {
            // wave file
            Map<String, String> tags = parseTxx(tag); //parseWaveGenre(metadata.getComment());
            //if (metadata.getGenre().equals(tags.get("GENRE"))) {
                metadata.setGenre(tags.get("GENRE"));
                metadata.setStyle(tags.get("STYLE"));
                metadata.setMood(tags.get("MOOD"));
              //  metadata.setRegion(tags.get("REGION"));
            //}
        }

        if(TagUtils.isFLACFile(metadata)) {
            metadata.setMood(tag.getFirst("MOOD"));
            metadata.setStyle(tag.getFirst("STYLE"));
           // metadata.setRegion(tag.getFirst("REGION"));
        }else if(TagUtils.isAIFFile(metadata) || TagUtils.isAACFile(metadata) || TagUtils.isALACFile(metadata)) {
            metadata.setMood(tag.getFirst("----:com.apple.iTunes:MOOD"));
            metadata.setStyle(tag.getFirst("----:com.apple.iTunes:STYLE"));
           // metadata.setRegion(tag.getFirst("----:com.apple.iTunes:REGION"));
        }else if (tag instanceof AbstractID3v2Tag id3) {
            List<TagField> moodFields = id3.getFields("TXXX");
            for (TagField field : moodFields) {
                if (field instanceof AbstractID3v2Frame frame) {
                    String desc = frame.getBody().getObjectValue("Description").toString();
                    String value = frame.getBody().getObjectValue("Text").toString();

                    if ("MOOD".equalsIgnoreCase(desc)) metadata.setMood(value);
                    if ("STYLE".equalsIgnoreCase(desc)) metadata.setStyle(value);
                   // if ("REGION".equalsIgnoreCase(desc)) metadata.setRegion(value);
                }
            }
        }
    }

    private Map<String, String> parseTxx(Tag tag) {
        List<TagField> fields = tag.getFields("TXXX");
        Map<String, String> mapped = new HashMap<>();
        for (TagField field : fields) {
            AbstractID3v2Frame frame = (AbstractID3v2Frame) field;
            FrameBodyTXXX body = (FrameBodyTXXX) frame.getBody();

            String key = body.getDescription();
            String value = body.getText();
            mapped.put(key, value);
        }
        return mapped;
    }

    private Map<String, String> parseWaveGenre(String raw) {
        Map<String, String> result = new HashMap<>();

        if (raw == null) return result;

        String[] parts = raw.split(";");

        //GENRE = "genre;style;mood;region"
        if(parts.length >0) {
            result.put("GENRE", parts[0]);
        }
        if(parts.length > 1) {
            result.put("STYLE", parts[1]);
        }
        if(parts.length > 2) {
            result.put("MOOD", parts[2]);
        }
        if(parts.length > 3) {
            result.put("REGION", parts[3]);
        }

        return result;
    }

    private String getTagValue(Tag tag, FieldKey key) {
        if (tag != null && tag.hasField(key)) {
            return StringUtils.trimToEmpty(tag.getFirst(key));
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

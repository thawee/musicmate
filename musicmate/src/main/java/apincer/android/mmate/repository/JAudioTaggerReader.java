package apincer.android.mmate.repository;

import static apincer.android.mmate.repository.FileRepository.isMediaFileExist;
import static apincer.android.mmate.repository.MQADetector.detectMQA;
import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.toBoolean;
import static apincer.android.mmate.utils.StringUtils.toDouble;
import static apincer.android.mmate.utils.StringUtils.toInt;
import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import android.content.Context;
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

import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;

public class JAudioTaggerReader extends TagReader{
    private static final String TAG = "JAudioTaggerReader";
    private static final List<MusicTag> EMPTY_LIST = null;

    public JAudioTaggerReader() {
    }

    private static final String KEY_TAG_WAVE_GROUP = "IKEY";
    private static final String KEY_TAG_WAVE_TRACK = "IPRT"; //track
    private static final String KEY_TAG_WAVE_ALBUM_ARTIST = "IENG";
    private static final String KEY_TAG_WAVE_QUALITY = "ISBJ";

    public static final String KEY_TAG_MP3_COMMENT = "COMMENT";

    @Override
    public List<MusicTag> readMusicTag(Context context, String mediaPath) {
        AudioFile read = getAudioFile(mediaPath);
        if(read != null) {
            MusicTag tag = new MusicTag();
            tag.setPath(mediaPath);
            readFileInfo(context, tag);
            readHeader(read, tag);
            readTags(read, tag);
            //detectMQA(tag);
            tag.setAudioStartTime(0);
           // detectMQA(tag,5000); // timeout 5 seconds
            return Collections.singletonList(tag);
        }
        return EMPTY_LIST;
    }

    @Override
    public List<MusicTag> readFullMusicTag(Context context, String mediaPath) {
        Log.d(TAG, "readFullMusicTag: path - "+mediaPath);
        AudioFile read = getAudioFile(mediaPath);
        if(read != null) {
            MusicTag tag = new MusicTag();
            tag.setPath(mediaPath);
            readFileInfo(context, tag);
            readHeader(read, tag);
            readTags(read, tag);
            detectMQA(tag);
            tag.setAudioStartTime(0);
            // detectMQA(tag,5000); // timeout 5 seconds
            return Collections.singletonList(tag);
        }
        return EMPTY_LIST;
    }

    private void readHeader(AudioFile read, MusicTag metadata) {
        try {
            AudioHeader header = read.getAudioHeader();
            long sampleRate = header.getSampleRateAsNumber(); //44100/48000 Hz
            int bitPerSample = header.getBitsPerSample(); //16/24/32
            long bitRate = header.getBitRateAsNumber() * 1000; //128/256/320
            //metadata.setLossless(header.isLossless());
            metadata.setAudioEncoding(detectAudioEncoding(read,header.isLossless()));
            if (header instanceof MP3AudioHeader) {
                metadata.setAudioDuration(header.getPreciseTrackLength());
            } else if (header instanceof Mp4AudioHeader) {
                metadata.setAudioDuration(header.getPreciseTrackLength());
            } else {
                metadata.setAudioDuration(header.getTrackLength());
            }
            metadata.setAudioBitRate(bitRate);
            metadata.setAudioBitsDepth(bitPerSample);
            metadata.setAudioSampleRate(sampleRate);
            metadata.setAudioChannels(header.getChannels());
            if (header.getChannels().toLowerCase().contains("stereo")) {
                metadata.setAudioChannels("2");
            }
        }catch (Exception ex) {
            Log.e(TAG, "readHeader: ",ex);
        }
    }

    private AudioFile getAudioFile(String path) {
        try {
            if(isMediaFileExist(path)) {
                setupTagOptionsForReading();
                return AudioFileIO.read(new File(path));
            }
        } catch (CannotReadException | IOException | TagException | ReadOnlyFileException |
                 InvalidAudioFrameException |
                 BufferUnderflowException e) {
            Log.e(TAG, "getAudioFile: "+path, e);
        }
        return null;
    }

    private static void setupTagOptionsForReading() {
        TagOptionSingleton.getInstance().setAndroid(true);
        TagOptionSingleton.getInstance().setId3v23DefaultTextEncoding(TextEncoding.ISO_8859_1); // default = ISO_8859_1
        TagOptionSingleton.getInstance().setId3v24DefaultTextEncoding(TextEncoding.UTF_8); // default = ISO_8859_1
        TagOptionSingleton.getInstance().setId3v24UnicodeTextEncoding(TextEncoding.UTF_16); // default UTF-16

    }

    private MusicTag readTags(AudioFile audioFile, MusicTag metadata) {
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
            //    metadata.setRating(toInt(getId3TagValue(tag, FieldKey.RATING)));
               // metadata.setPublisher(getId3TagValue(tag, FieldKey.COPYRIGHT));
                //metadata.setBpm(toInt(getId3TagValue(tag, FieldKey.BPM)));
                //  metadata.setEncoder(getId3TagValue(tag, FieldKey.ENCODER));
                metadata.setCompilation(toBoolean(getId3TagValue(tag, FieldKey.IS_COMPILATION)));
                if(tag.getFirstArtwork() != null) {
                    metadata.setCoverartMime(tag.getFirstArtwork().getMimeType());
                }

                // read replay gain fields
                Map<String, String> tags = parseCustomTagFields(tag);
                if (tags.containsKey(KEY_TAG_TRACK_GAIN)) {
                    metadata.setTrackRG(extractDouble(tags.get(KEY_TAG_TRACK_GAIN), " dB"));
                }
                if (tags.containsKey(KEY_TAG_TRACK_PEAK)) {
                    metadata.setTrackTP(toDouble(tags.get(KEY_TAG_TRACK_PEAK)));
                }
                if (tags.containsKey(KEY_MM_TRACK_DR_SCORE)) {
                    metadata.setDynamicRangeScore(toDouble(tags.get(KEY_MM_TRACK_DR_SCORE)));
                }
                if (tags.containsKey(KEY_MM_TRACK_DR)) {
                    metadata.setDynamicRange(toDouble(tags.get(KEY_MM_TRACK_DR)));
                }
                if (tags.containsKey(KEY_MM_TRACK_UPSCALED)) {
                    metadata.setUpscaledInd(toInt(tags.get(KEY_MM_TRACK_UPSCALED)));
                }
                if (tags.containsKey(KEY_MM_TRACK_RESAMPLED)) {
                    metadata.setResampledInd(toInt(tags.get(KEY_MM_TRACK_RESAMPLED)));
                }
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

    private String getId3TagValue(Tag id3Tag, FieldKey key) {
        if(id3Tag!=null && id3Tag.hasField(key)) {
            return StringUtils.trimToEmpty(id3Tag.getFirst(key));
        }
        return "";
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

            if (data[0].equals("TRACK")) {data[0] = KEY_TAG_TRACK_GAIN;}
            // else if (data[0].equals("ALBUM")) {data[0] = REPLAYGAIN_ALBUM_GAIN;}

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

        //
        if (tag.hasField(KEY_MM_TRACK_DR)) {
            tags.put(KEY_MM_TRACK_DR, StringUtils.trimToEmpty(tag.getFirst(KEY_MM_TRACK_DR)));
        }
        if (tag.hasField(KEY_MM_TRACK_DR_SCORE)) {
            tags.put(KEY_MM_TRACK_DR_SCORE, StringUtils.trimToEmpty(tag.getFirst(KEY_MM_TRACK_DR_SCORE)));
        }
        if (tag.hasField(KEY_MM_TRACK_UPSCALED)) {
            tags.put(KEY_MM_TRACK_UPSCALED, StringUtils.trimToEmpty(tag.getFirst(KEY_MM_TRACK_UPSCALED)));
        }
        if (tag.hasField(KEY_MM_TRACK_RESAMPLED)) {
            tags.put(KEY_MM_TRACK_RESAMPLED, StringUtils.trimToEmpty(tag.getFirst(KEY_MM_TRACK_RESAMPLED)));
        }

        return tags;
    }

    private double extractDouble(String val, String suffix) {
        // extract number before match suffix
        if(val == null) return 0L;
        if(val.endsWith(suffix)) {
            String txt = trimToEmpty(val.replace(suffix, ""));
            return toDouble(txt);
        }
        return 0L;
    }
}

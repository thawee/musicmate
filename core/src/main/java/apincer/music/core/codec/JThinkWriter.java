package apincer.music.core.codec;

import static apincer.music.core.repository.FileRepository.isMediaFileExist;
import static apincer.music.core.utils.StringUtils.isEmpty;
import static apincer.music.core.utils.StringUtils.trimToEmpty;

import android.content.Context;
import android.util.Log;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.TagOptionSingleton;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.ID3v24Frame;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX;
import org.jaudiotagger.tag.mp4.field.Mp4TagReverseDnsField;
import org.jaudiotagger.tag.reference.ID3V2Version;
import org.jaudiotagger.tag.vorbiscomment.VorbisAlbumArtistSaveOptions;
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag;
import org.jaudiotagger.tag.wav.WavTag;

import java.io.File;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;

import apincer.music.core.model.Track;
import apincer.music.core.utils.LogHelper;
import apincer.music.core.utils.TagUtils;

public class JThinkWriter extends  TagWriter {
    private static final String TAG = "JThinkWriter";
    private final Context context;
    private static boolean tagOptionsInitialized = false;

    public JThinkWriter(Context context) {
        this.context = context;
        LogHelper.initial();
        // Initialize tag options once when the writer is created
        synchronized(JThinkWriter.class) {
            if (!tagOptionsInitialized) {
                setupTagOptions();
                tagOptionsInitialized = true;
            }
        }
    }

    @Override
    protected void writeTag(Track tag) {
        if (tag==null) {
            return;
        }
        Log.i(TAG, "writeTag: "+tag.getPath());
        // write new tag
        try {
            AudioFile audioFile = getAudioFile(tag.getPath());

            if (audioFile == null) {
                return;
            }

            Tag newTag = audioFile.getTagOrCreateDefault();
            if(!(newTag instanceof ID3v24Tag || newTag instanceof WavTag)) {
                // wave not support encoding
                newTag.setEncoding(StandardCharsets.UTF_8);
            }

            // Batch set tag fields to reduce individual operations
            setAllTagFields(newTag, tag);

            // Commit changes to file
            audioFile.commit();
        }catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void setAllTagFields(Tag tag, Track musicTag) throws FieldDataInvalidException {
        boolean isWav = TagUtils.isWavFile(musicTag);

        // Set all common fields in one method to reduce method call overhead
        // --- Common fields ---
        setTagField(FieldKey.TITLE, trimToEmpty(musicTag.getTitle()), tag);
        setTagField(FieldKey.ALBUM, trimToEmpty(musicTag.getAlbum()), tag);
        setTagField(FieldKey.ALBUM_ARTIST, trimToEmpty(musicTag.getAlbumArtist()), tag);
        setTagField(FieldKey.ARTIST, trimToEmpty(musicTag.getArtist()), tag);
        setTagField(FieldKey.TRACK, trimToEmpty(musicTag.getTrack()), tag);
        setTagField(FieldKey.COMPOSER, trimToEmpty(musicTag.getComposer()), tag);

        if(!isWav) {
            setTagField(FieldKey.GENRE, trimToEmpty(musicTag.getGenre()), tag);
            setTagField(FieldKey.COMMENT, cleanupComment(musicTag.getComment()), tag);
            setTagField(FieldKey.YEAR, trimToEmpty(musicTag.getYear()), tag);
            //setTagField(FieldKey.DISC_NO, trimToEmpty(musicTag.getDisc()), tag);
            setTagField(FieldKey.IS_COMPILATION, Boolean.toString(musicTag.isCompilation()), tag);
        }else {
            // wave file
            String genreTags = formatWaveGenre(musicTag.getGenre(), musicTag.getStyle(), musicTag.getMood());
            if (tag instanceof WavTag) {
                WavTag wavTag = (WavTag) tag;
                AbstractID3v2Tag id3 = wavTag.getID3Tag();

                if (id3 == null) {
                    id3 = new ID3v24Tag();
                    wavTag.setID3Tag(id3);
                }
                // Keep standard field clean
                id3.setField(FieldKey.GENRE, safe(musicTag.getGenre()));

                // Store full structured data safely
               // id3.setField(FieldKey.COMMENT, genreTags);
                addTxxx(id3, "STYLE", safe(musicTag.getStyle()));
                addTxxx(id3, "MOOD", safe(musicTag.getMood()));
               // addTxxx(id3, "REGION", safe(musicTag.getRegion()));
            }
        }

        if(TagUtils.isFLACFile(musicTag)) {
            FlacTag flacTag = (FlacTag) tag;
            VorbisCommentTag vorbis = flacTag.getVorbisCommentTag();
            if (vorbis != null) {
                addVorbisField(vorbis, "MOOD", safe(musicTag.getMood()));
                addVorbisField(vorbis, "STYLE", safe(musicTag.getStyle()));
               // addVorbisField(vorbis, "REGION", safe(musicTag.getRegion()));
            }
        }else if(TagUtils.isAIFFile(musicTag) || TagUtils.isAACFile(musicTag) || TagUtils.isALACFile(musicTag)) {
            addIfNotNull(tag, createItunesField("MOOD", safe(musicTag.getMood())));
            addIfNotNull(tag, createItunesField("STYLE", safe(musicTag.getStyle())));
           // addIfNotNull(tag, createItunesField("REGION", safe(musicTag.getRegion())));
        }
    }

    private void addIfNotNull(Tag tag, TagField field) {
        if (field != null) {
            try {
                tag.addField(field);
            } catch (Exception e) {
                Log.e(TAG, "Failed to add field", e);
            }
        }
    }

    private void addTxxx(AbstractID3v2Tag tag, String key, String value) {
        if (value == null || value.isEmpty()) return;

        try {
            FrameBodyTXXX body = new FrameBodyTXXX();
            body.setDescription(key);
            body.setText(value);

            ID3v24Frame frame = new ID3v24Frame("TXXX");
            frame.setBody(body);

            tag.addField(frame);

        } catch (Exception e) {
            Log.e(TAG, "Failed to add TXXX " + key, e);
        }
    }

    private void addVorbisField(VorbisCommentTag tag, String key, String value) {
        if (value == null || value.isEmpty()) return;

        try {
            TagField field = tag.createField(key, value);
            tag.setField(field); // or addField(field)
        } catch (Exception e) {
            Log.e(TAG, "Failed to set Vorbis field " + key, e);
        }
    }

    private TagField createItunesField(String key, String value) {
        if (value == null || value.isEmpty()) return null;

        return new Mp4TagReverseDnsField(
                "com.apple.iTunes",
                "MusicMate",
                key,
                value
        );
    }

    private String formatWaveGenre(String genre, String style, String mood) {
        StringBuilder sb = new StringBuilder();

        sb.append(safe(genre));
        sb.append(";").append(safe(style));
        sb.append(";").append(safe(mood));
        //sb.append(";").append(safe(region));

        return sb.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String cleanupComment(String comment) {
        if(isEmpty(comment)) return "";
        else if(comment.contains("<##>")) {
            // Remove all content between <##> and </##>, including the tags themselves.
            comment = comment.replaceAll("<##>.*?</##>", "");
        }
        return trimToEmpty(comment);
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
            Log.e(TAG, "getAudioFile: "+path, e);
        }
        return null;
    }

    void setTagField(FieldKey fieldKey, String value, Tag tag) throws FieldDataInvalidException {
        try {
            tag.setField(fieldKey, trimToEmpty(value));
        } catch (FieldDataInvalidException ignored) {
            Log.w(TAG, "Failed to set field " + fieldKey + ": " + ignored.getMessage());
           // throw e;
        }
    }

    private static void setupTagOptions() {
        TagOptionSingleton instance = TagOptionSingleton.getInstance();
        instance.setAndroid(true);
        instance.setResetTextEncodingForExistingFrames(true);
        instance.setID3V2Version(ID3V2Version.ID3_V24);
        instance.setWriteMp3GenresAsText(true);
        instance.setWriteMp4GenresAsText(true);
        instance.setPadNumbers(true);
        instance.setRemoveTrailingTerminatorOnWrite(true);
        instance.setLyrics3Save(true);
        instance.setVorbisAlbumArtistSaveOptions(VorbisAlbumArtistSaveOptions.WRITE_ALBUMARTIST_AND_DELETE_JRIVER_ALBUMARTIST);
    }
}

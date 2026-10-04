package apincer.music.core.codec;

import static apincer.music.core.repository.FileRepository.isMediaFileExist;
import static apincer.music.core.utils.StringUtils.isEmpty;
import static apincer.music.core.utils.StringUtils.trimToEmpty;

import android.content.Context;
import android.util.Log;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.TagOptionSingleton;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.id3.AbstractID3v2Frame;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.ID3v22Tag;
import org.jaudiotagger.tag.id3.ID3v23Tag;
import org.jaudiotagger.tag.id3.ID3v24Frame;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;
import org.jaudiotagger.tag.mp4.field.Mp4TagReverseDnsField;
import org.jaudiotagger.tag.reference.ID3V2Version;
import org.jaudiotagger.tag.vorbiscomment.VorbisAlbumArtistSaveOptions;
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag;
import org.jaudiotagger.tag.wav.WavInfoTag;
import org.jaudiotagger.tag.wav.WavTag;

import java.io.File;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import apincer.music.core.model.Track;
import apincer.music.core.utils.LogHelper;
import apincer.music.core.utils.StringUtils;
import apincer.music.core.utils.TagUtils;

public class JThinkWriter extends TagWriter {
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
    protected boolean writeTag(Track tag) {
        if (tag == null || tag.getPath() == null) {
            return false;
        }
        Log.i(TAG, "writeTag: " + tag.getPath());
        try {
            AudioFile audioFile = getAudioFile(tag.getPath());
            if (audioFile == null) {
                Log.e(TAG, "writeTag: audio file is null or unreadable for " + tag.getPath());
                return false;
            }

            Tag newTag = audioFile.getTagOrCreateDefault();
            if (!(newTag instanceof ID3v24Tag || newTag instanceof WavTag)) {
                // wave does not support encoding parameter
                newTag.setEncoding(StandardCharsets.UTF_8);
            }

            // Batch set tag fields to reduce individual operations
            setAllTagFields(newTag, tag);

            // Commit changes to file
            audioFile.commit();
            return true;
        } catch (CannotWriteException e) {
            Log.e(TAG, "writeTag cannot write: " + tag.getPath(), e);
            return false;
        } catch (Exception ex) {
            Log.e(TAG, "writeTag unexpected error for " + tag.getPath() + ": " + ex.getMessage(), ex);
            return false;
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

        if (!isWav) {
            setTagField(FieldKey.GENRE, trimToEmpty(musicTag.getGenre()), tag);
            setTagField(FieldKey.COMMENT, cleanupComment(musicTag.getComment()), tag);
            setTagField(FieldKey.YEAR, trimToEmpty(musicTag.getYear()), tag);
            //setTagField(FieldKey.DISC_NO, trimToEmpty(musicTag.getDisc()), tag);
            setTagField(FieldKey.IS_COMPILATION, Boolean.toString(musicTag.isCompilation()), tag);
        } else {
            // Dual-chunk WAV support: write to both WavInfoTag (RIFF INFO chunk) and ID3Tag chunk
            if (tag instanceof WavTag wavTag) {
                // 1. Write to standard RIFF INFO chunk for legacy car stereos & players
                WavInfoTag infoTag = wavTag.getInfoTag();
                if (infoTag == null) {
                    infoTag = new WavInfoTag();
                    wavTag.setInfoTag(infoTag);
                }
                setTagField(FieldKey.TITLE, trimToEmpty(musicTag.getTitle()), infoTag);
                setTagField(FieldKey.ARTIST, trimToEmpty(musicTag.getArtist()), infoTag);
                setTagField(FieldKey.ALBUM, trimToEmpty(musicTag.getAlbum()), infoTag);
                setTagField(FieldKey.GENRE, trimToEmpty(musicTag.getGenre()), infoTag);
                setTagField(FieldKey.YEAR, trimToEmpty(musicTag.getYear()), infoTag);
                setTagField(FieldKey.TRACK, trimToEmpty(musicTag.getTrack()), infoTag);

                // 2. Write to ID3 chunk for modern audiophile players
                AbstractID3v2Tag id3 = wavTag.getID3Tag();
                if (id3 == null) {
                    id3 = new ID3v24Tag();
                    wavTag.setID3Tag(id3);
                }
                setTagField(FieldKey.TITLE, trimToEmpty(musicTag.getTitle()), id3);
                setTagField(FieldKey.ARTIST, trimToEmpty(musicTag.getArtist()), id3);
                setTagField(FieldKey.ALBUM_ARTIST, trimToEmpty(musicTag.getAlbumArtist()), id3);
                setTagField(FieldKey.ALBUM, trimToEmpty(musicTag.getAlbum()), id3);
                setTagField(FieldKey.GENRE, trimToEmpty(musicTag.getGenre()), id3);
                setTagField(FieldKey.YEAR, trimToEmpty(musicTag.getYear()), id3);
                setTagField(FieldKey.TRACK, trimToEmpty(musicTag.getTrack()), id3);
                setTagField(FieldKey.COMPOSER, trimToEmpty(musicTag.getComposer()), id3);
                setTagField(FieldKey.COMMENT, cleanupComment(musicTag.getComment()), id3);

                addTxxx(id3, "STYLE", safe(musicTag.getStyle()));
                addTxxx(id3, "MOOD", safe(musicTag.getMood()));
                addTxxx(id3, "ORIGIN", safe(musicTag.getOrigin()));
            }
        }

        if (TagUtils.isFLACFile(musicTag)) {
            FlacTag flacTag = (FlacTag) tag;
            VorbisCommentTag vorbis = flacTag.getVorbisCommentTag();
            if (vorbis != null) {
                addVorbisField(vorbis, "MOOD", safe(musicTag.getMood()));
                addVorbisField(vorbis, "STYLE", safe(musicTag.getStyle()));
                addVorbisField(vorbis, "ORIGIN", safe(musicTag.getOrigin()));
            }
        } else if (TagUtils.isAIFFile(musicTag) || TagUtils.isAACFile(musicTag) || TagUtils.isALACFile(musicTag)) {
            addIfNotNull(tag, createItunesField("MOOD", safe(musicTag.getMood())));
            addIfNotNull(tag, createItunesField("STYLE", safe(musicTag.getStyle())));
            addIfNotNull(tag, createItunesField("ORIGIN", safe(musicTag.getOrigin())));
        } else if (tag instanceof AbstractID3v2Tag id3Tag) {
            addTxxx(id3Tag, "STYLE", safe(musicTag.getStyle()));
            addTxxx(id3Tag, "MOOD", safe(musicTag.getMood()));
            addTxxx(id3Tag, "ORIGIN", safe(musicTag.getOrigin()));
        }

        // Embedded Cover Art Writing
        if (!isEmpty(musicTag.getAlbumArtFilename())) {
            File artFile = new File(musicTag.getAlbumArtFilename());
            if (artFile.exists() && artFile.length() > 0) {
                try {
                    Artwork artwork = ArtworkFactory.createArtworkFromFile(artFile);
                    if (artwork != null) {
                        tag.setField(artwork);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to set embedded artwork: " + e.getMessage());
                }
            }
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

            AbstractID3v2Frame frame = (tag instanceof ID3v22Tag) ? tag.createFrame("TXX") : tag.createFrame("TXXX");
            frame.setBody(body);

            tag.setField(frame);

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
            // Check if this is a multi-value field
            if (isMultiValueField(fieldKey) && StringUtils.isMultiValue(value)) {
                setMultiValueField(fieldKey, value, tag);
            } else {
                tag.setField(fieldKey, trimToEmpty(value));
            }
        } catch (FieldDataInvalidException ignored) {
            Log.w(TAG, "Failed to set field " + fieldKey + ": " + ignored.getMessage());
        }
    }
    
    /**
     * Set a multi-value field (Artist, Album Artist, Composer, Genre).
     * Writes multiple values using ID3v2 multi-value separator (null character).
     */
    private void setMultiValueField(FieldKey fieldKey, String value, Tag tag) throws FieldDataInvalidException {
        // Split by our internal separators and rejoin with ID3v2 separator
        List<String> values = StringUtils.splitMultiValue(value);
        if (values.isEmpty()) {
            tag.setField(fieldKey, "");
            return;
        }
        
        // For ID3v2, multi-values are separated by "/"
        // For VorbisComment/FLAC, multi-values are separated by null
        // jaudiotagger handles this automatically when using setField with List
        tag.setField(fieldKey, String.join("/", values));
    }
    
    /**
     * Check if a field supports multi-values.
     */
    private boolean isMultiValueField(FieldKey fieldKey) {
        return fieldKey == FieldKey.ARTIST || 
               fieldKey == FieldKey.ALBUM_ARTIST || 
               fieldKey == FieldKey.COMPOSER ||
               fieldKey == FieldKey.GENRE;
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

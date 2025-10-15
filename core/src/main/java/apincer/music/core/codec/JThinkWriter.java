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
import org.jaudiotagger.tag.TagOptionSingleton;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.jaudiotagger.tag.reference.ID3V2Version;
import org.jaudiotagger.tag.vorbiscomment.VorbisAlbumArtistSaveOptions;
import org.jaudiotagger.tag.wav.WavTag;

import java.io.File;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;

import apincer.music.core.database.MusicTag;
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
    protected boolean writeTag(MusicTag tag) {
        if (tag==null) {
            return false;
        }
        Log.i(TAG, "writeTag: "+tag.getPath());
        // write new tag
        try {
            AudioFile audioFile = getAudioFile(tag.getPath());

            if (audioFile == null) {
                return false;
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
            return true;
        }catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }

    private void setAllTagFields(Tag tag, MusicTag musicTag) throws FieldDataInvalidException {
        // Set all common fields in one method to reduce method call overhead
        setTagField(FieldKey.TITLE, trimToEmpty(musicTag.getTitle()), tag);
        setTagField(FieldKey.ALBUM, trimToEmpty(musicTag.getAlbum()), tag);
        setTagField(FieldKey.ALBUM_ARTIST, trimToEmpty(musicTag.getAlbumArtist()), tag);
        setTagField(FieldKey.ARTIST, trimToEmpty(musicTag.getArtist()), tag);
        setTagField(FieldKey.GENRE, trimToEmpty(musicTag.getGenre()), tag);
        setTagField(FieldKey.COMMENT, cleanupComment(musicTag.getComment()), tag);
        setTagField(FieldKey.TRACK, trimToEmpty(musicTag.getTrack()), tag);
        setTagField(FieldKey.COMPOSER, trimToEmpty(musicTag.getComposer()), tag);

        // WAVE not support Grouping, use RECORD_LABEL
        if(TagUtils.isWavFile(musicTag)) {
            setTagField(FieldKey.RECORD_LABEL, trimToEmpty(musicTag.getGrouping()), tag);
        }else {
            setTagField(FieldKey.YEAR, trimToEmpty(musicTag.getYear()), tag);
            setTagField(FieldKey.DISC_NO, trimToEmpty(musicTag.getDisc()), tag);
            setTagField(FieldKey.GROUPING, trimToEmpty(musicTag.getGrouping()), tag);
            setTagField(FieldKey.IS_COMPILATION, Boolean.toString(musicTag.isCompilation()), tag);
        }

        // Extended fields
       // setTagField(FieldKey.MEDIA, trimToEmpty(musicTag.getMediaType()), tag);
        setTagField(FieldKey.QUALITY, trimToEmpty(musicTag.getQualityRating()), tag);
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

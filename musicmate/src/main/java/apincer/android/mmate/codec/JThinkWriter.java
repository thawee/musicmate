package apincer.android.mmate.codec;

import static apincer.android.mmate.repository.FileRepository.isMediaFileExist;

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
import org.jaudiotagger.tag.reference.ID3V2Version;
import org.jaudiotagger.tag.vorbiscomment.VorbisAlbumArtistSaveOptions;

import java.io.File;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;

import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.LogHelper;

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
            newTag.setEncoding(StandardCharsets.UTF_8);

            // Batch set tag fields to reduce individual operations
            setAllTagFields(newTag, tag);

            //setTagFieldsCommon(newTag, tag);
            //setTagFieldsExtended(newTag, tag);
            //setCustomTagsFields(audioFile, tag);

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
        setTagField(FieldKey.TITLE, musicTag.getTitle(), tag);
        setTagField(FieldKey.ALBUM, musicTag.getAlbum(), tag);
        setTagField(FieldKey.ALBUM_ARTIST, musicTag.getAlbumArtist(), tag);
        setTagField(FieldKey.ARTIST, musicTag.getArtist(), tag);
        setTagField(FieldKey.GENRE, musicTag.getGenre(), tag);
        setTagField(FieldKey.COMMENT, musicTag.getComment(), tag);
        setTagField(FieldKey.GROUPING, musicTag.getGrouping(), tag);
        setTagField(FieldKey.TRACK, musicTag.getTrack(), tag);
        setTagField(FieldKey.DISC_NO, musicTag.getDisc(), tag);
        setTagField(FieldKey.YEAR, musicTag.getYear(), tag);
        setTagField(FieldKey.COMPOSER, musicTag.getComposer(), tag);
        setTagField(FieldKey.IS_COMPILATION, Boolean.toString(musicTag.isCompilation()), tag);

        // Extended fields
        setTagField(FieldKey.MEDIA, musicTag.getMediaType(), tag);
        setTagField(FieldKey.QUALITY, musicTag.getMediaQuality(), tag);
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

    /*
    void setCustomTagsFields(AudioFile audioFile, MusicTag tag) {
        setTagFieldCustom(audioFile, KEY_MM_TRACK_DR, Double.toString(tag.getDynamicRange()));
        setTagFieldCustom(audioFile, KEY_MM_TRACK_DR_SCORE,Double.toString(tag.getDynamicRangeScore()));
        setTagFieldCustom(audioFile, KEY_MM_TRACK_UPSCALED,Double.toString(tag.getUpscaledScore()));
        setTagFieldCustom(audioFile, KEY_MM_TRACK_RESAMPLED,Double.toString(tag.getResampledScore()));
    } */

    void setTagField(FieldKey fieldKey, String value, Tag tag) throws FieldDataInvalidException {
        try {
            tag.setField(fieldKey, value);
        } catch (FieldDataInvalidException e) {
            Log.w(TAG, "Failed to set field " + fieldKey + ": " + e.getMessage());
            throw e;
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

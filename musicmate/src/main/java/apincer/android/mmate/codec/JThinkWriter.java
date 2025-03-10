package apincer.android.mmate.codec;

import static apincer.android.mmate.repository.FileRepository.isMediaFileExist;
import static apincer.android.mmate.codec.TagReader.KEY_MM_TRACK_DR;
import static apincer.android.mmate.codec.TagReader.KEY_MM_TRACK_DR_SCORE;
import static apincer.android.mmate.codec.TagReader.KEY_MM_TRACK_RESAMPLED;
import static apincer.android.mmate.codec.TagReader.KEY_MM_TRACK_UPSCALED;

import android.content.Context;
import android.util.Log;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.KeyNotFoundException;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.TagOptionSingleton;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.id3.AbstractID3Tag;
import org.jaudiotagger.tag.id3.AbstractID3v2Frame;
import org.jaudiotagger.tag.id3.ID3v1Tag;
import org.jaudiotagger.tag.id3.ID3v22Tag;
import org.jaudiotagger.tag.id3.ID3v23Frame;
import org.jaudiotagger.tag.id3.ID3v23Tag;
import org.jaudiotagger.tag.id3.ID3v24Frame;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX;
import org.jaudiotagger.tag.mp4.Mp4Tag;
import org.jaudiotagger.tag.mp4.field.Mp4TagReverseDnsField;
import org.jaudiotagger.tag.reference.ID3V2Version;
import org.jaudiotagger.tag.vorbiscomment.VorbisAlbumArtistSaveOptions;
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag;

import java.io.File;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;

import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.LogHelper;

public class JThinkWriter extends  TagWriter {
    private static final String TAG = "JThinkWriter";
    private final Context context;

    public JThinkWriter(Context context) {
        this.context = context;
        LogHelper.initial();
    }

    @Override
    protected boolean writeTag(MusicTag tag) {
        if (tag==null) {
            return false;
        }
        Log.i(TAG, "writeTag: "+tag.getPath());
        // prepare new tag
        // reset tag
       /* if(MusicTagUtils.isWavFile(tag)) {
            // need to reset tag for wave file
            try {
                setWavComment(tag);
                resetWavFile(new File(tag.getPath()));
            }catch (Exception ex) {
                ex.printStackTrace();
            }
        } */
        // write new tag
        try {
            AudioFile audioFile = getAudioFile(tag.getPath());
            assert audioFile != null;
            Tag newTag = audioFile.getTagOrCreateDefault();
            setTagFieldsCommon(newTag, tag);
            setTagFieldsExtended(newTag, tag);
            setCustomTagsFields(audioFile, tag);
            audioFile.commit();
            return true;
        }catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }

    private AudioFile getAudioFile(String path) {
        try {
            if(isMediaFileExist(path)) {
                setupTagOptions();
                return AudioFileIO.read(new File(path));
            }
        } catch (CannotReadException | IOException | TagException | ReadOnlyFileException |
                 InvalidAudioFrameException |
                 BufferUnderflowException e) {
            Log.e(TAG, "getAudioFile: "+path, e);
        }
        return null;
    }

    void setTagFieldsCommon(Tag tag, MusicTag musicTag) throws FieldDataInvalidException {
        tag.setEncoding(StandardCharsets.UTF_8);
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
    }

    void setTagFieldsExtended(Tag tag, MusicTag musicTag) throws FieldDataInvalidException {
        setTagField(FieldKey.MEDIA, musicTag.getMediaType(), tag);
        setTagField(FieldKey.QUALITY, musicTag.getMediaQuality(), tag);
    }

    void setCustomTagsFields(AudioFile audioFile, MusicTag tag) {
        setTagFieldCustom(audioFile, KEY_MM_TRACK_DR, Double.toString(tag.getDynamicRange()));
        setTagFieldCustom(audioFile, KEY_MM_TRACK_DR_SCORE,Double.toString(tag.getDynamicRangeScore()));
        setTagFieldCustom(audioFile, KEY_MM_TRACK_UPSCALED,Double.toString(tag.getUpscaledScore()));
        setTagFieldCustom(audioFile, KEY_MM_TRACK_RESAMPLED,Double.toString(tag.getResampledScore()));
    }

    void setTagField(FieldKey fieldKey, String value, Tag tag) throws FieldDataInvalidException {
        tag.setField(fieldKey, value);
    }

    /**
     * This will write a custom ID3 tag (TXXX). This works only with MP3 files
     * (Flac with ID3-Tag not tested).
     *
     * @param description The description of the custom tag i.e. "cataloger"
     * There can only be one custom TXXX tag with that description in one MP3
     * file
     * @param text The actual text to be written into the new tag field
     * @return True if the tag has been properly written, false otherwise
     */
    public static boolean setTagFieldCustom(AudioFile audioFile, String description, String text) {
        // Get the tag from the audio file
        // If there is no ID3Tag create an ID3v2.3 tag
        Tag tag = audioFile.getTagOrCreateAndSetDefault();
        if (tag instanceof AbstractID3Tag) {
            FrameBodyTXXX txxxBody = new FrameBodyTXXX();
            txxxBody.setDescription(description);
            txxxBody.setText(text);
            // If there is only a ID3v1 tag, copy data into new ID3v2.3 tag
            if (!(tag instanceof ID3v23Tag || tag instanceof ID3v24Tag)) {
                Tag newTagV23 = null;
                if (tag instanceof ID3v1Tag) {
                    newTagV23 = new ID3v23Tag((ID3v1Tag) tag); // Copy old tag data
                }
                if (tag instanceof ID3v22Tag) {
                    newTagV23 = new ID3v23Tag((ID3v22Tag) tag); // Copy old tag data
                }
                audioFile.setTag(newTagV23);
                tag = newTagV23;
            }

            AbstractID3v2Frame frame = null;
            if (tag instanceof ID3v23Tag) {
                if (((ID3v23Tag) audioFile.getTag()).getInvalidFrames() > 0) {
                   // throw new IOException("read some invalid frames!");
                    return false;
                }
                frame = new ID3v23Frame("TXXX");
            } else if (tag instanceof ID3v24Tag) {
                if (((ID3v24Tag) audioFile.getTag()).getInvalidFrames() > 0) {
                   // throw new IOException("read some invalid frames!");
                    return false;
                }
                frame = new ID3v24Frame("TXXX");
            }

            frame.setBody(txxxBody);

            try {
                tag.setField(frame);
            } catch (FieldDataInvalidException e) {
                //Logger.getLogger(TrackAnalyzer.class.getName()).log(Level.SEVERE, null, e);
                return false;
            }
        } else if (tag instanceof FlacTag) {
            try {
                ((FlacTag) tag).setField(description, text);
            } catch (KeyNotFoundException | FieldDataInvalidException ex) {
                //Logger.getLogger(TrackAnalyzer.class.getName()).log(Level.SEVERE, null, ex);
                return false;
            }
        } else if (tag instanceof Mp4Tag) {
            //TagField field = new Mp4TagTextField("----:com.apple.iTunes:"+description, text);
            TagField field;
            field = new Mp4TagReverseDnsField(Mp4TagReverseDnsField.IDENTIFIER
                    + ":" + "com.apple.iTunes" + ":" + description,
                    "com.apple.iTunes", description, text);
            //TagField field = new Mp4TagTextField(description, text);
            try {
                tag.setField(field);
            } catch (FieldDataInvalidException ex) {
                //Logger.getLogger(TrackAnalyzer.class.getName()).log(Level.SEVERE, null, ex);
                return false;
            }
        } else if (tag instanceof VorbisCommentTag) {
            try {
                ((VorbisCommentTag) tag).setField(description, text);
            } catch (KeyNotFoundException | FieldDataInvalidException ex) {
                return false;
            }
        } else {
            // tag not implemented
            return false;
        }

        return true;
    }

    private static void setupTagOptions() {
        TagOptionSingleton.getInstance().setAndroid(true);
        TagOptionSingleton.getInstance().setResetTextEncodingForExistingFrames(true);
        TagOptionSingleton.getInstance().setID3V2Version(ID3V2Version.ID3_V24);
        TagOptionSingleton.getInstance().setWriteMp3GenresAsText(true);
        TagOptionSingleton.getInstance().setWriteMp4GenresAsText(true);
        TagOptionSingleton.getInstance().setPadNumbers(true);
        TagOptionSingleton.getInstance().setRemoveTrailingTerminatorOnWrite(true);
        // TagOptionSingleton.getInstance().setRemoveID3FromFlacOnSave(true);
        TagOptionSingleton.getInstance().setLyrics3Save(true);
        TagOptionSingleton.getInstance().setVorbisAlbumArtistSaveOptions(VorbisAlbumArtistSaveOptions.WRITE_ALBUMARTIST_AND_DELETE_JRIVER_ALBUMARTIST);
    }
}

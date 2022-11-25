package apincer.android.mmate.repository;

import static apincer.android.mmate.Constants.REPLAYGAIN_REFERENCE_LOUDNESS;
import static apincer.android.mmate.Constants.REPLAYGAIN_TRACK_GAIN;
import static apincer.android.mmate.Constants.REPLAYGAIN_TRACK_RANGE;
import static apincer.android.mmate.repository.FFMPeg.writeTrackFields;
import static apincer.android.mmate.repository.FileRepository.getFolderArtworkAsStream;
import static apincer.android.mmate.repository.FileRepository.isMediaFileExist;
import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.toBoolean;
import static apincer.android.mmate.utils.StringUtils.toDouble;
import static apincer.android.mmate.utils.StringUtils.toInt;

import android.content.Context;

import com.anggrayudi.storage.file.DocumentFileCompat;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.SupportedFileFormat;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.audio.mp3.MP3AudioHeader;
import org.jaudiotagger.audio.mp4.Mp4AudioHeader;
import org.jaudiotagger.audio.wav.WavOptions;
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
import org.jaudiotagger.tag.id3.valuepair.TextEncoding;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.mp4.Mp4Tag;
import org.jaudiotagger.tag.mp4.field.Mp4TagReverseDnsField;
import org.jaudiotagger.tag.reference.ID3V2Version;
import org.jaudiotagger.tag.vorbiscomment.VorbisAlbumArtistSaveOptions;
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag;
import org.jaudiotagger.tag.wav.WavInfoTag;
import org.jaudiotagger.tag.wav.WavTag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import apincer.android.mmate.Constants;
import apincer.android.mmate.fs.MusicMateArtwork;
import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.utils.FileUtils;
import timber.log.Timber;

public class JAudioTagger {
    public boolean saveMediaArtwork(MusicTag item, Artwork artwork) {
        if (item == null || item.getPath() == null || artwork==null) {
            return false;
        }
        try {
            // boolean isCacheMode = false;
            File file = new File(item.getPath());

            AudioFile audioFile = buildAudioFile(file.getAbsolutePath(),"rw");
            assert audioFile != null;
            Tag tag = audioFile.getTagOrCreateDefault();
            if (tag != null) {
                tag.deleteArtworkField();
                tag.addField(artwork);
                audioFile.commit();
            }
        } catch(Exception ex) {
            Timber.e(ex);
        }
        return true;
    }

    public static byte[] getArtworkAsByte(MusicTag mediaItem) {
        InputStream ins = getArtworkAsStream(mediaItem);
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            int n;
            byte[] buffer = new byte[1024];
            while ((n = ins.read(buffer)) > 0) {
                outputStream.write(buffer, 0, n);
            }
            return outputStream.toByteArray();
        }catch (Exception ex) {}
        return null;
    }

    public static AudioFile buildAudioFile(String path, String mode) {
        try {
            if(isMediaFileExist(path)) {
                setupTagOptionsForReading();
                if(mode!=null && mode.contains("w")) {
                    setupTagOptionsForWriting();
                }
                return AudioFileIO.read(new File(path));
            }
        } catch (CannotReadException | IOException | TagException | ReadOnlyFileException | InvalidAudioFrameException | BufferUnderflowException e) {
            Timber.i(e);
        }
        return null;
    }

    private static void setupTagOptionsForReading() {
        TagOptionSingleton.getInstance().setAndroid(true);
        TagOptionSingleton.getInstance().setId3v23DefaultTextEncoding(TextEncoding.ISO_8859_1); // default = ISO_8859_1
        TagOptionSingleton.getInstance().setId3v24DefaultTextEncoding(TextEncoding.UTF_8); // default = ISO_8859_1
        TagOptionSingleton.getInstance().setId3v24UnicodeTextEncoding(TextEncoding.UTF_16); // default UTF-16

    }

    private static void setupTagOptionsForWriting() {
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


    private void setJAudioTagger(MusicTag musicTag) {
        if (musicTag==null) {
            return;
        }
        // prepare new tag
        // reset tag
        if(MusicTagUtils.isWavFile(musicTag)) {
            // need to reset tag for wave file
            try {
                setWavComment(musicTag);
                resetWavFile(new File(musicTag.getPath()));
            }catch (Exception ex) {
                Timber.e(ex);
            }
        }
        // write new tag
        try {
            AudioFile audioFile = buildAudioFile(musicTag.getPath(), "rw");
            assert audioFile != null;
            Tag tag = audioFile.getTagOrCreateDefault();
            setTagFieldsCommon(tag, musicTag);
            setTagFieldsExtended(tag, musicTag);
            audioFile.commit();
        }catch (Exception ex) {
            Timber.e(ex);
        }
    }

    private void setWavComment(MusicTag musicTag) {
        String comment = "/#"+StringUtils.trimToEmpty(musicTag.getMediaType());
        comment = comment+"#:"+StringUtils.trimToEmpty(musicTag.getDisc());
        comment = comment+"#:"+StringUtils.trimToEmpty(musicTag.getGrouping());
        comment = comment+"#:"+StringUtils.trimToEmpty(musicTag.getMediaQuality());
        comment = comment+"#/ "+StringUtils.trimToEmpty(musicTag.getComment());
        musicTag.setComment(comment);
    }


    private static boolean isValidJAudioTagger(String path) {
        try {
            String ext = StringUtils.trimToEmpty(FileUtils.getExtension(path));
            SupportedFileFormat.valueOf(ext.toUpperCase());
            return true;
        }catch(Exception ex) {
            return false;
        }
    }



    public void readFileHeader(Context context, File file, MusicTag metadata, String mediaPath) {
        metadata.setPath(mediaPath);
        metadata.setFileLastModified(file.lastModified());
        if(isEmpty(metadata.getTitle())) {
            metadata.setTitle(FileUtils.getFileName(mediaPath));
        }
        if(isEmpty(metadata.getFileFormat())) {
            metadata.setFileFormat(FileUtils.getExtension(mediaPath).toUpperCase());
        }
        if(isEmpty(metadata.getAudioEncoding())) {
            metadata.setAudioEncoding(metadata.getFileFormat());
        }
        if(isEmpty(metadata.getSimpleName())) {
            metadata.setSimpleName(DocumentFileCompat.getBasePath(context, mediaPath));
        }
        if(isEmpty(metadata.getStorageId())) {
            metadata.setStorageId(DocumentFileCompat.getStorageId(context, mediaPath));
        }
    }

    private MusicTag readJAudioTagger(Context context, MusicTag metadata, String path) {
        if(isValidJAudioTagger(path)) {
            File file = new File(path);
            metadata.setFileLastModified(file.lastModified());
            metadata.setFileSize(file.length());
            readFileHeader(context, file, metadata, path);

            AudioFile audioFile = buildAudioFile(path, "r");
            if (audioFile == null) {
                // cannot read file, return as is
                return metadata;
            }
            readJAudioHeader(audioFile, metadata); //16/24/32 bit and 44.1/48/96/192 kHz

            Tag tag = audioFile.getTag();
            if (tag != null && !tag.isEmpty()) {
                String title = getId3TagValue(tag, FieldKey.TITLE);
                if(!StringUtils.isEmpty(title)) {
                    metadata.setTitle(title);
                }
                metadata.setAlbum(getId3TagValue(tag, FieldKey.ALBUM));
                metadata.setArtist(getId3TagValue(tag, FieldKey.ARTIST));
                metadata.setAlbumArtist(getId3TagValue(tag, FieldKey.ALBUM_ARTIST));
                metadata.setGenre(getId3TagValue(tag, FieldKey.GENRE));
                metadata.setYear(getId3TagValue(tag, FieldKey.YEAR));
                metadata.setTrack(getId3TagValue(tag, FieldKey.TRACK));
                metadata.setComposer(getId3TagValue(tag, FieldKey.COMPOSER));
                metadata.setRating(toInt(getId3TagValue(tag, FieldKey.RATING)));
                //metadata.setBpm(toInt(getId3TagValue(tag, FieldKey.BPM)));
                //  metadata.setEncoder(getId3TagValue(tag, FieldKey.ENCODER));
                metadata.setCompilation(toBoolean(getId3TagValue(tag, FieldKey.IS_COMPILATION)));

                // read replay gain fields
                Map<String, String> tags = parseCustomTagFields(tag);
                if(tags.containsKey(REPLAYGAIN_TRACK_GAIN)) {
                    metadata.setTrackGain(toDouble(getTagFieldCustom(tag, REPLAYGAIN_TRACK_GAIN)));
                }
                if(tags.containsKey(REPLAYGAIN_TRACK_RANGE)) {
                    metadata.setTrackRange(toDouble(getTagFieldCustom(tag, REPLAYGAIN_TRACK_RANGE)));
                }
                if(tags.containsKey(REPLAYGAIN_REFERENCE_LOUDNESS)) {
                    metadata.setTrackLoudness(toDouble(getTagFieldCustom(tag, REPLAYGAIN_REFERENCE_LOUDNESS)));
                }

                //  if ("wav".equalsIgnoreCase(metadata.getFileExtension()) || "dsf".equalsIgnoreCase(metadata.getFileExtension())) {
                if(MusicTagUtils.isWavFile(metadata)) {
                    // wave, not support disk no, grouping, media, quality - write to comment
                    parseWaveCommentTag(metadata, getId3TagValue(tag, FieldKey.COMMENT));
                } else {
                    // WAV file not support these fields
                    metadata.setDisc(getId3TagValue(tag, FieldKey.DISC_NO));
                    metadata.setGrouping(getId3TagValue(tag, FieldKey.GROUPING));
                    metadata.setMediaQuality(getId3TagValue(tag, FieldKey.QUALITY));
                    metadata.setMediaType(getId3TagValue(tag, FieldKey.MEDIA));
                    metadata.setComment(getId3TagValue(tag, FieldKey.COMMENT));
                }

                // check MQA Tag
                // detectMQA(metadata);

            }

            return metadata;
        }
        return null;
    }


    private void parseWaveCommentTag(MusicTag mediaTag, String comment) {
        comment = StringUtils.trimToEmpty(comment);

        /*
         String comment = "/#"+StringUtils.trimToEmpty(pendingMetadata.getSource());
            comment = comment+"##"+StringUtils.trimToEmpty(pendingMetadata.getDisc());
            comment = comment+"##"+StringUtils.trimToEmpty(pendingMetadata.getGrouping());
            comment = comment+"##"+StringUtils.trimToEmpty(pendingMetadata.isAudiophile()?Constants.AUDIOPHILE:"");
            comment = comment+"#/"+StringUtils.trimToEmpty(pendingMetadata.getComment());
         */
        int start = comment.indexOf("/#");
        int end = comment.indexOf("#/");
        if(start >= 0 && end > start) {
            // found metadata comment
            String metadata = comment.substring(start+2, end);
            if(comment.length()>(end+2)) {
                comment = comment.substring(end+2);
            }else {
                comment = "";
            }
            String []text = metadata.split("#:");
            if(text.length >=1) {
                // Source
                mediaTag.setMediaType(StringUtils.trimToEmpty(text[0]));
            }
            if(text.length >=2) {
                // Disc
                mediaTag.setDisc(StringUtils.trimToEmpty(text[1]));
            }
            if(text.length >=3) {
                // Grouping
                mediaTag.setGrouping(StringUtils.trimToEmpty(text[2]));
            }
            if(text.length >=4) {
                // Audiophile
                mediaTag.setMediaQuality(StringUtils.trimToEmpty(text[3]));
            }
        }

        mediaTag.setComment(StringUtils.trimToEmpty(comment));

    }


    private String getId3TagValue(Tag id3Tag, FieldKey key) {
        if(id3Tag!=null && id3Tag.hasField(key)) {
            return StringUtils.trimToEmpty(id3Tag.getFirst(key));
        }
        return "";
    }

    public void setJAudioTagger(String targetPath, MusicTag tag) {
        if (targetPath == null || tag == null) {
            return;
        }

        // File file = new File(targetPath);

        // setupTagOptionsForWriting();
        // AudioFile audioFile = buildAudioFile(file.getAbsolutePath(),"rw");

        // save default tags
        //Tag existingTags = audioFile.getTagOrCreateAndSetDefault();
        MusicTag newTag = tag.clone();
        newTag.setPath(targetPath);
        setJAudioTagger(tag);
    }


    private void setTagFieldsReplayGain(MusicTag tag) {
        // write track replay gain to file
        // Replay Gain V2.0 standard (That means: -18 LUFS reference, "dB" units, and uppercase 'REPLAYGAIN_*' tags.)
        // 1. should remove old tag lower case and upper cse
        // 2. write new tag with upper case

        // flac, aiff, mp4, mp3, wav?
        /*
        "REPLAYGAIN_TRACK_GAIN", -- gain
    "REPLAYGAIN_TRACK_PEAK", -- true peak
    "REPLAYGAIN_TRACK_RANGE", -- loudness range
    "REPLAYGAIN_REFERENCE_LOUDNESS" -- loudness integrated
         */

        try {
            AudioFile audioFile = buildAudioFile(tag.getPath(), "rw");
            assert audioFile != null;
            setTagFieldCustom(audioFile, "REPLAYGAIN_TRACK_GAIN",Double.toString(tag.getTrackGain()));
            setTagFieldCustom(audioFile, "REPLAYGAIN_TRACK_RANGE",Double.toString(tag.getTrackRange()));
            setTagFieldCustom(audioFile, "REPLAYGAIN_REFERENCE_LOUDNESS",Double.toString(tag.getTrackLoudness()));

            audioFile.commit();
        } catch (CannotWriteException |IOException e) {
            Timber.e(e);
        }
    }

    public boolean setMusicTag(Context context, MusicTag item, String artworkPath, String collectionPath) {
        boolean status;
        try {
            Artwork artwork = null;
            if(!StringUtils.isEmpty(artworkPath)) {
                File artworkFile = new File(artworkPath);
                if(artworkFile.exists()) {
                    try {
                        artwork = MusicMateArtwork.createArtworkFromFile(artworkFile);
                    } catch (IOException e) {
                        Timber.e(e);
                    }
                }
            }
            setMusicTag(context, item, collectionPath);
            saveMediaArtwork(item, artwork);
            status = true;
        }catch (Exception|OutOfMemoryError ex) {
            status = false;
        }

        return status;
    }


    public void setMusicTag(Context context, MusicTag item, String collectionPath) throws Exception{
        if (item == null || item.getPath() == null) {
            return;
        }

        if(item.getOriginTag()==null) {
            return;
        }

        item.setMusicManaged(StringUtils.compare(item.getPath(),collectionPath));
        if(isValidJAudioTagger(item.getPath())) {
        //if(FFMPeg.isSupportedFileFormat(item.getPath())) {
            //  File file = new File(item.getPath());
            // setupTagOptionsForWriting();
            // AudioFile audioFile = buildAudioFile(file.getAbsolutePath(), "rw");

            // save default tags
            // assert audioFile != null;
            // Tag existingTags = audioFile.getTagOrCreateAndSetDefault();
             setJAudioTagger(item);
            writeTrackFields(context, item);
            item.setOriginTag(null); // reset pending tag
            MusicTagRepository.saveTag(item);
        }
    }

    private void readJAudioHeader(AudioFile read, MusicTag metadata) {
        try {
            AudioHeader header = read.getAudioHeader();
            long sampleRate = header.getSampleRateAsNumber(); //44100/48000 Hz
            int bitPerSample = header.getBitsPerSample(); //16/24/32
            long bitRate = header.getBitRateAsNumber(); //128/256/320
            metadata.setLossless(header.isLossless());
            //metadata.setFileSize(read.getFile().length());
            metadata.setAudioEncoding(detectAudioEncoding(read,header.isLossless()));
            if (header instanceof MP3AudioHeader) {
                metadata.setAudioDuration(header.getPreciseTrackLength());
            } else if (header instanceof Mp4AudioHeader) {
                metadata.setAudioDuration(header.getPreciseTrackLength());
            } else {
                metadata.setAudioDuration(header.getTrackLength());
            }
            /*
            if(header.getTrackLength()>0) {
                int length = header.getTrackLength();
                metadata.setAudioDuration(length);
            }else {
                // calculate duration
                //duration = filesize in bytes / (samplerate * #of channels * (bitspersample / 8 ))
                long duration = metadata.getFileSize() / (sampleRate+ch) * (bitPerSample);
                metadata.setAudioDuration(duration);
            } */
            metadata.setAudioBitRate(bitRate);
            metadata.setAudioBitsDepth(bitPerSample);
            metadata.setAudioSampleRate(sampleRate);
            metadata.setAudioChannels(header.getChannels());
            if (header.getChannels().toLowerCase().contains("stereo")) {
                metadata.setAudioChannels("2");
            }
        }catch (Exception ex) {
            Timber.e(ex);
        }
    }


    private String detectAudioEncoding(AudioFile read, boolean isLossless) {
        String encType = read.getExt();
        if(StringUtils.isEmpty(encType)) return "";

        if("m4a".equalsIgnoreCase(encType)) {
            if(isLossless) {
                encType = Constants.MEDIA_ENC_ALAC;
            }else {
                encType = Constants.MEDIA_ENC_AAC;
            }
        }else if("wav".equalsIgnoreCase(encType)) {
            encType = Constants.MEDIA_ENC_WAVE;
        }else if("aif".equalsIgnoreCase(encType)) {
            encType = Constants.MEDIA_ENC_AIFF;
        }else if("flac".equalsIgnoreCase(encType)) {
            encType = Constants.MEDIA_ENC_FLAC;
        }else if("mp3".equalsIgnoreCase(encType)) {
            encType = Constants.MEDIA_ENC_MP3;
        }else if("dsf".equalsIgnoreCase(encType)) {
            encType =  Constants.MEDIA_ENC_DSF;
        }else if("dff".equalsIgnoreCase(encType)) {
            encType =  Constants.MEDIA_ENC_DFF;
        }else if("iso".equalsIgnoreCase(encType)) {
            encType =  Constants.MEDIA_ENC_SACD;
        }
        return  encType.toUpperCase();
    }


    private static InputStream getEmbedArtworkAsStream(MusicTag mediaItem) {
        try {
            if(isValidJAudioTagger(mediaItem.getPath())) {
                AudioFile audioFile = buildAudioFile(mediaItem.getPath(), "r");

                if (audioFile == null) {
                    return null;
                }
                Artwork artwork = audioFile.getTagOrCreateDefault().getFirstArtwork();
                if (null != artwork) {
                    byte[] artworkData = artwork.getBinaryData();
                    return new ByteArrayInputStream(artworkData);
                }
            }
        }catch(Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }


    private static InputStream getArtworkAsStream(MusicTag item) {
        InputStream input = getEmbedArtworkAsStream(item);
        if (input == null) {
            input = getFolderArtworkAsStream(item);
        }
        return input;
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
        setTagField(FieldKey.RATING, Integer.toString(musicTag.getRating()), tag);
        // setTagField(FieldKey.BPM, Integer.toString(musicTag.getBpm()), tag);
        setTagField(FieldKey.IS_COMPILATION, Boolean.toString(musicTag.isCompilation()), tag);
    }

    void setTagFieldsExtended(Tag tag, MusicTag musicTag) throws FieldDataInvalidException {
        setTagField(FieldKey.MEDIA, musicTag.getMediaType(),tag);
        setTagField(FieldKey.QUALITY, musicTag.getMediaQuality(),tag);
    }

    void resetMp3File(File mp3File) throws Exception {
        AudioFile audio = AudioFileIO.read(mp3File);
        Tag tag = new ID3v24Tag();
        resetCommonTagFields(tag);
        audio.setTag(tag);
        audio.commit();
    }

    void resetM4aFile(File m4aFile) throws Exception {
        AudioFile audio = AudioFileIO.read(m4aFile);
        Tag tag = new Mp4Tag();
        resetCommonTagFields(tag);
        audio.setTag(tag);
        audio.commit();
    }

    void resetWavFile(File waveFile) throws Exception {
        AudioFile audio = AudioFileIO.read(waveFile);
        WavTag wavTag = new WavTag(WavOptions.READ_ID3_ONLY);
        wavTag.setID3Tag(new ID3v24Tag());
        wavTag.setInfoTag(new WavInfoTag());
        resetCommonTagFields(wavTag);
        audio.setTag(wavTag);
        audio.commit();
    }

    void resetFlacFile(File flacFile) throws Exception {
        AudioFile audio = AudioFileIO.read(flacFile);
        Tag tag = new FlacTag();
        resetCommonTagFields(tag);
        audio.setTag(tag);
        audio.commit();
    }

    void resetCommonTagFields(Tag tag) throws FieldDataInvalidException {
        setTagField(FieldKey.TITLE, "", tag);
        setTagField(FieldKey.ALBUM, "", tag);
        setTagField(FieldKey.ALBUM_ARTIST, "", tag);
        setTagField(FieldKey.ARTIST, "", tag);
        setTagField(FieldKey.GENRE, "", tag);
        setTagField(FieldKey.COMMENT, "", tag);
        setTagField(FieldKey.GROUPING, "", tag);
        setTagField(FieldKey.TRACK, Integer.toString(0), tag);
        setTagField(FieldKey.DISC_NO, Integer.toString(0), tag);
        setTagField(FieldKey.YEAR, Integer.toString(0), tag);
        setTagField(FieldKey.BPM, Integer.toString(0), tag);
        setTagField(FieldKey.RATING, Integer.toString(0), tag);
        setTagField(FieldKey.IS_COMPILATION, Boolean.toString(false), tag);
    }

    void setTagField(FieldKey fieldKey, String value, Tag tag) throws FieldDataInvalidException {
        tag.setField(fieldKey, value);
    }

    /**
     * This will write a custom ID3 tag (TXXX). This works only with MP3 files
     * (Flac with ID3-Tag not tested).
     *
     * @param description The description of the custom tag i.e. "catalognr"
     * There can only be one custom TXXX tag with that description in one MP3
     * file
     * @param text The actual text to be written into the new tag field
     * @return True if the tag has been properly written, false otherwise
     */
    public static boolean setTagFieldCustom(AudioFile audioFile, String description, String text) throws IOException {
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
                    throw new IOException("read some invalid frames!");
                }
                frame = new ID3v23Frame("TXXX");
            } else if (tag instanceof ID3v24Tag) {
                if (((ID3v24Tag) audioFile.getTag()).getInvalidFrames() > 0) {
                    throw new IOException("read some invalid frames!");
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

    /**
     * This will read a custom ID3 tag (TXXX). This works only with MP3 files
     * (Flac with ID3-Tag not tested).
     *
     * @param description The description of the custom tag i.e. "catalognr"
     * There can only be one custom TXXX tag with that description in one MP3
     * file
     * @return text The actual text to be written into the new tag field
     */
    public static String getTagFieldCustom(Tag tag, String description)  {
        // Get the tag from the audio file
        if (tag instanceof AbstractID3Tag) {

        } else if (tag instanceof FlacTag) {
            try {
                return ((FlacTag) tag).getFirst(description);
            } catch (KeyNotFoundException ex) {
                return null;
            }
        } else if (tag instanceof Mp4Tag) {

        } else if (tag instanceof VorbisCommentTag) {

        } else {
            // tag not implemented
            return null;
        }

        return null;
    }


    private Map<String, String> parseCustomTagFields(Tag tag) {
        Map<String, String> tags = null;
        try {
            if (tag instanceof VorbisCommentTag) {
                tags = parseVorbisTags((VorbisCommentTag) tag);
            } else if (tag instanceof FlacTag) {
                tags = parseVorbisTags(((FlacTag) tag).getVorbisCommentTag());
            } else {
                tags = parseId3Tags(tag);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return tags;
    }

    private static Map<String, String> parseId3Tags(Tag tag) throws Exception {
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

            if (data[0].equals("TRACK")) {data[0] = REPLAYGAIN_TRACK_GAIN;}
            // else if (data[0].equals("ALBUM")) {data[0] = REPLAYGAIN_ALBUM_GAIN;}

            tags.put(data[0], StringUtils.trimToEmpty(data[1]));
        }

        return tags;
    }

    private static Map<String, String> parseVorbisTags(VorbisCommentTag tag) {
        Map<String, String> tags = new HashMap<>();

        if (tag.hasField(REPLAYGAIN_TRACK_GAIN)) {
            tags.put(REPLAYGAIN_TRACK_GAIN, StringUtils.trimToEmpty(tag.getFirst(REPLAYGAIN_TRACK_GAIN)));
        }
        if (tag.hasField(REPLAYGAIN_TRACK_RANGE)) {
            tags.put(REPLAYGAIN_TRACK_RANGE, StringUtils.trimToEmpty(tag.getFirst(REPLAYGAIN_TRACK_RANGE)));
        }
        if (tag.hasField(REPLAYGAIN_REFERENCE_LOUDNESS)) {
            tags.put(REPLAYGAIN_REFERENCE_LOUDNESS, StringUtils.trimToEmpty(tag.getFirst(REPLAYGAIN_REFERENCE_LOUDNESS)));
        }

        return tags;
    }
}

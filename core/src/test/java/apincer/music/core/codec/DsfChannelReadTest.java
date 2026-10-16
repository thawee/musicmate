package apincer.music.core.codec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.tag.FieldKey;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;

/** DSF files hidden from paths under scoped storage are read from a channel on the granted document. */
public class DsfChannelReadTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File fixture(String name) throws Exception {
        File file = new File(temporaryFolder.getRoot(), name);
        try (InputStream in = getClass().getResourceAsStream("/storage/fixture.dsf")) {
            Files.copy(in, file.toPath());
        }
        return file;
    }

    @Test
    public void readsTheSameTagsAndHeaderFromAChannelAsFromThePath() throws Exception {
        File dsf = fixture("song.dsf");
        AudioFile tagged = AudioFileIO.read(dsf);
        tagged.getTagOrCreateAndSetDefault().setField(FieldKey.TITLE, "Through a channel");
        tagged.commit();

        AudioFile byPath = AudioFileIO.read(dsf);
        AudioFile byChannel;
        try (FileChannel channel = FileChannel.open(dsf.toPath())) {
            channel.position(123); // a reused channel is read from the start
            byChannel = AudioFileIO.read(dsf, channel);
        }

        assertEquals("Through a channel", byChannel.getTag().getFirst(FieldKey.TITLE));
        assertEquals("dsf", byChannel.getExt());
        assertEquals(byPath.getAudioHeader().getSampleRateAsNumber(), byChannel.getAudioHeader().getSampleRateAsNumber());
        assertEquals(byPath.getAudioHeader().getTrackLength(), byChannel.getAudioHeader().getTrackLength());
        assertEquals(byPath.getAudioHeader().getChannels(), byChannel.getAudioHeader().getChannels());
    }

    @Test
    public void otherFormatsRefuseChannelReads() throws Exception {
        File flac = fixture("song.flac");
        try (FileChannel channel = FileChannel.open(flac.toPath())) {
            assertThrows(CannotReadException.class, () -> AudioFileIO.read(flac, channel));
        }
    }
}

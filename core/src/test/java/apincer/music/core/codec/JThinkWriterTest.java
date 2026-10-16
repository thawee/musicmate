package apincer.music.core.codec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

import apincer.music.core.model.AudioTag;

public class JThinkWriterTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    /** A file with no tag yet gets one; the new tag used to be created but never attached, so DSF failed. */
    @Test
    public void writesTagsToAFileThatHasNone() throws Exception {
        File dsf = new File(temporaryFolder.getRoot(), "untagged.dsf");
        try (InputStream in = getClass().getResourceAsStream("/storage/fixture.dsf")) {
            Files.copy(in, dsf.toPath());
        }
        assertEquals(null, AudioFileIO.read(dsf).getTag());

        AudioTag track = new AudioTag(1L);
        track.setPath(dsf.getAbsolutePath());
        track.setTitle("First Title");
        track.setArtist("Some Artist");

        assertTrue(new JThinkWriter(null).writeTag(track));
        assertEquals("First Title", AudioFileIO.read(dsf).getTag().getFirst(FieldKey.TITLE));
        assertEquals("Some Artist", AudioFileIO.read(dsf).getTag().getFirst(FieldKey.ARTIST));
    }
}

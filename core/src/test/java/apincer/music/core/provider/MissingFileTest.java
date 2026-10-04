package apincer.music.core.provider;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MissingFileTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void missingParentIsUnavailableRatherThanConfirmedDeletion() {
        File track = new File(temporaryFolder.getRoot(), "unmounted-volume/album/song.flac");

        assertFalse(FileSystem.isConfirmedMissingFile(track.getPath()));
    }

    @Test
    public void accessibleParentWithAbsentLeafConfirmsDeletion() throws IOException {
        File album = temporaryFolder.newFolder("album");
        File track = new File(album, "deleted.flac");

        assertTrue(FileSystem.isConfirmedMissingFile(track.getPath()));
    }

    @Test
    public void existingTrackIsNotMissing() throws IOException {
        File track = temporaryFolder.newFile("song.flac");

        assertFalse(FileSystem.isConfirmedMissingFile(track.getPath()));
    }
}

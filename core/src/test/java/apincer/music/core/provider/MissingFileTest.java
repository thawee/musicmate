package apincer.music.core.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

    @Test
    public void deletedAlbumFolderOnAMountedVolumeConfirmsDeletion() throws IOException {
        File volume = temporaryFolder.newFolder("volume");
        new File(volume, "Music/Other Artist").mkdirs();
        File track = new File(volume, "Music/Gone Artist/Gone Album/song.flac");

        assertTrue(FileSystem.isConfirmedMissingFile(track.getPath(), volume));
    }

    @Test
    public void anUnmountedVolumeIsNotEvidenceOfDeletion() {
        File volume = new File(temporaryFolder.getRoot(), "1234-ABCD");
        File track = new File(volume, "Music/Album/song.flac");

        assertFalse(FileSystem.isConfirmedMissingFile(track.getPath(), volume));
    }

    @Test
    public void aFolderStillListingTheMissingPartIsNotDeletion() throws IOException {
        File volume = temporaryFolder.newFolder("volume2");
        File album = new File(volume, "Music/Album");
        album.mkdirs();
        // The album exists and the song is absent: deleted. Its sibling song is still there.
        new File(album, "kept.flac").createNewFile();

        assertTrue(FileSystem.isConfirmedMissingFile(new File(album, "gone.flac").getPath(), volume));
        assertFalse(FileSystem.isConfirmedMissingFile(new File(album, "kept.flac").getPath(), volume));
    }

    @Test
    public void volumeRootsOfAndroidStoragePaths() {
        assertEquals(new File("/storage/emulated/0"), FileSystem.volumeRoot(new File("/storage/emulated/0/Music/a.flac")));
        assertEquals(new File("/storage/1234-ABCD"), FileSystem.volumeRoot(new File("/storage/1234-ABCD/Music/a.flac")));
        assertNull(FileSystem.volumeRoot(new File("/sdcard/Music/a.flac")));
        assertNull(FileSystem.volumeRoot(new File("/storage/a.flac")));
    }
}

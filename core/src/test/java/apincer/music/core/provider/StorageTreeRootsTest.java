package apincer.music.core.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Map;

public class StorageTreeRootsTest {
    private static final Map<String, String> VOLUMES = Map.of(
            "primary", "/storage/emulated/0",
            "1A2B-3C4D", "/storage/1A2B-3C4D");

    @Test
    public void treeDocumentIdsMapToPaths() {
        assertEquals("/storage/emulated/0/Music", StorageTreeRoots.absolutePath("primary:Music", VOLUMES));
        assertEquals("/storage/emulated/0/Download/Hi-Res",
                StorageTreeRoots.absolutePath("primary:Download/Hi-Res", VOLUMES));
        assertEquals("/storage/1A2B-3C4D/Music", StorageTreeRoots.absolutePath("1A2B-3C4D:Music", VOLUMES));
        assertEquals("A volume root has an empty relative path",
                "/storage/1A2B-3C4D", StorageTreeRoots.absolutePath("1A2B-3C4D:", VOLUMES));
        assertEquals("/storage/emulated/0/Music", StorageTreeRoots.absolutePath("primary:Music/", VOLUMES));
    }

    @Test
    public void unknownVolumesAndMalformedIdsMapToNothing() {
        assertNull("An unmounted SD card has no path", StorageTreeRoots.absolutePath("9999-0000:Music", VOLUMES));
        assertNull(StorageTreeRoots.absolutePath("Music", VOLUMES));
        assertNull(StorageTreeRoots.absolutePath(":Music", VOLUMES));
    }

    @Test
    public void pathsMapToDocumentIds() {
        StorageTreeRoots.Root music = StorageTreeRoots.root("tree", "primary:Music", VOLUMES);

        assertEquals("primary:Music/Artist/Album/01 Song.dsf",
                music.documentId("/storage/emulated/0/Music/Artist/Album/01 Song.dsf"));
        assertEquals("primary:Music", music.documentId("/storage/emulated/0/Music"));
        assertNull("A sibling folder with the same prefix is not covered",
                music.documentId("/storage/emulated/0/Music2/a.flac"));
        assertNull(music.documentId("/storage/emulated/0/Download/a.flac"));
    }

    @Test
    public void sdCardPathsKeepTheVolumeUuid() {
        StorageTreeRoots.Root sd = StorageTreeRoots.root("tree", "1A2B-3C4D:Music", VOLUMES);

        assertEquals("1A2B-3C4D:Music/a.flac", sd.documentId("/storage/1A2B-3C4D/Music/a.flac"));
        assertFalse(sd.covers("/storage/emulated/0/Music/a.flac"));
    }

    @Test
    public void theNarrowestRootWins() {
        StorageTreeRoots.Root music = StorageTreeRoots.root("music", "primary:Music", VOLUMES);
        StorageTreeRoots.Root album = StorageTreeRoots.root("album", "primary:Music/Album", VOLUMES);
        List<StorageTreeRoots.Root> roots = List.of(music, album);

        assertSame(album, StorageTreeRoots.find(roots, "/storage/emulated/0/Music/Album/a.flac"));
        assertSame(music, StorageTreeRoots.find(roots, "/storage/emulated/0/Music/Other/a.flac"));
        assertNull(StorageTreeRoots.find(roots, "/storage/emulated/0/Download/a.flac"));
        assertTrue(music.covers("/storage/emulated/0/Music"));
    }
}

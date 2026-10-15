package apincer.music.core.provider;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class PathMusicFileAccessTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final PathMusicFileAccess access = new PathMusicFileAccess();

    @After
    public void restoreDefaultAccess() {
        FileSystem.setAccess(new PathMusicFileAccess());
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    public void editWorksOnTheTargetItself() throws Exception {
        File target = temporaryFolder.newFile("song.flac");
        Files.write(target.toPath(), bytes("old tags"));

        access.edit(target, working -> {
            assertEquals(target, working);
            Files.write(working.toPath(), bytes("new tags"));
        });

        assertArrayEquals(bytes("new tags"), Files.readAllBytes(target.toPath()));
    }

    @Test
    public void atomicWriteReplacesTheTarget() throws Exception {
        File target = temporaryFolder.newFile("Cover.jpg");
        Files.write(target.toPath(), bytes("old cover"));

        access.writeAtomically(target, out -> out.write(bytes("new cover")));

        assertArrayEquals(bytes("new cover"), Files.readAllBytes(target.toPath()));
        assertEquals("No temporary file may be left beside the target",
                1, temporaryFolder.getRoot().listFiles().length);
    }

    @Test
    public void failedAtomicWriteKeepsTheOriginalAndLeavesNoTemporaryFile() throws Exception {
        File target = temporaryFolder.newFile("Cover.jpg");
        Files.write(target.toPath(), bytes("old cover"));

        try {
            access.writeAtomically(target, out -> {
                out.write(bytes("half"));
                throw new IOException("download failed");
            });
            fail("The writer's failure must reach the caller");
        } catch (IOException expected) {
            // expected
        }

        assertArrayEquals(bytes("old cover"), Files.readAllBytes(target.toPath()));
        assertEquals(1, temporaryFolder.getRoot().listFiles().length);
    }

    @Test
    public void atomicWriteRefusesAMissingFolder() {
        File missing = new File(temporaryFolder.getRoot(), "Vanished Album");
        File target = new File(missing, "Cover.jpg");

        try {
            access.writeAtomically(target, out -> out.write(bytes("cover")));
            fail("A missing album folder must not be recreated");
        } catch (IOException expected) {
            // expected
        }
        assertFalse(missing.exists());
    }

    @Test
    public void copyOverwritesTheTargetAndCreatesItsFolder() throws Exception {
        File source = temporaryFolder.newFile("folder.jpg");
        Files.write(source.toPath(), bytes("cover"));
        File target = new File(temporaryFolder.getRoot(), "Artist/Album/folder.jpg");

        access.copy(source, target);
        Files.write(source.toPath(), bytes("cover v2"));
        access.copy(source, target);

        assertArrayEquals(bytes("cover v2"), Files.readAllBytes(target.toPath()));
        assertTrue(source.exists());
    }

    @Test
    public void moveWithoutReplaceRefusesAnOccupiedTarget() throws Exception {
        File source = temporaryFolder.newFile("a.flac");
        File target = temporaryFolder.newFile("b.flac");
        Files.write(source.toPath(), bytes("incoming"));
        Files.write(target.toPath(), bytes("existing"));

        assertFalse(access.move(source, target, false));
        assertArrayEquals(bytes("existing"), Files.readAllBytes(target.toPath()));
        assertArrayEquals(bytes("incoming"), Files.readAllBytes(source.toPath()));
    }

    @Test
    public void moveWithReplaceOverwritesLikeRenameTo() throws Exception {
        File source = temporaryFolder.newFile("a.flac");
        File target = temporaryFolder.newFile("b.flac");
        Files.write(source.toPath(), bytes("incoming"));
        Files.write(target.toPath(), bytes("existing"));

        assertTrue(access.move(source, target, true));
        assertArrayEquals(bytes("incoming"), Files.readAllBytes(target.toPath()));
        assertFalse(source.exists());
    }

    @Test
    public void moveCreatesTheTargetFolder() throws Exception {
        File source = temporaryFolder.newFile("a.flac");
        File target = new File(temporaryFolder.getRoot(), "Artist/Album/01 a.flac");

        assertTrue(access.move(source, target, false));
        assertTrue(target.isFile());
    }

    @Test
    public void deleteAndMkdirs() throws Exception {
        File file = temporaryFolder.newFile("a.flac");
        File dir = new File(temporaryFolder.getRoot(), "x/y");

        assertTrue(access.delete(file));
        assertFalse(file.exists());
        assertFalse("Deleting a missing file reports failure", access.delete(file));
        assertTrue(access.mkdirs(dir));
        assertTrue(dir.isDirectory());
        assertTrue("An existing folder counts as success", access.mkdirs(dir));
    }

    @Test
    public void fileSystemDelegatesToTheInstalledAccess() throws Exception {
        List<String> calls = new ArrayList<>();
        FileSystem.setAccess(new PathMusicFileAccess() {
            @Override public boolean move(File source, File target, boolean replaceExisting) {
                calls.add("move " + source.getName() + " " + target.getName() + " " + replaceExisting);
                return super.move(source, target, replaceExisting);
            }
            @Override public boolean delete(File file) {
                calls.add("delete " + file.getName());
                return super.delete(file);
            }
            @Override public void copy(File source, File target) throws IOException {
                calls.add("copy " + source.getName() + " " + target.getName());
                super.copy(source, target);
            }
        });
        File a = temporaryFolder.newFile("a.flac");
        File c = new File(temporaryFolder.getRoot(), "c.flac");

        FileSystem.move(null, a.getAbsolutePath(), new File(temporaryFolder.getRoot(), "b.flac").getAbsolutePath());
        FileSystem.rename(null, new File(temporaryFolder.getRoot(), "b.flac").getAbsolutePath(), c.getAbsolutePath());
        FileSystem.copyFile(c, new File(temporaryFolder.getRoot(), "d.flac"));
        FileSystem.delete(c);

        assertEquals(List.of("move a.flac b.flac false", "move b.flac c.flac true",
                "copy c.flac d.flac", "delete c.flac"), calls);
    }
}

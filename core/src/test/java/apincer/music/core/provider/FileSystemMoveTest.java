package apincer.music.core.provider;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class FileSystemMoveTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void occupiedDestinationPreservesBothRecordings() throws Exception {
        File source = temporaryFolder.newFile("incoming.flac");
        File destination = temporaryFolder.newFile("library.flac");
        byte[] incomingRecording = "new recording".getBytes(StandardCharsets.UTF_8);
        byte[] existingRecording = "existing recording".getBytes(StandardCharsets.UTF_8);
        Files.write(source.toPath(), incomingRecording);
        Files.write(destination.toPath(), existingRecording);

        boolean moved = FileSystem.move(null, source.getAbsolutePath(), destination.getAbsolutePath());

        assertArrayEquals("An occupied library destination must never be overwritten",
                existingRecording, Files.readAllBytes(destination.toPath()));
        assertArrayEquals("Rejecting a collision must retain the incoming recording",
                incomingRecording, Files.readAllBytes(source.toPath()));
        assertFalse("A rejected move must report failure", moved);
    }

    @Test
    public void unusedDestinationReceivesCompleteRecordingAndRemovesSource() throws Exception {
        File source = temporaryFolder.newFile("incoming.flac");
        File destination = new File(temporaryFolder.getRoot(), "library.flac");
        byte[] recording = "complete recording".getBytes(StandardCharsets.UTF_8);
        Files.write(source.toPath(), recording);

        boolean moved = FileSystem.move(null, source.getAbsolutePath(), destination.getAbsolutePath());

        assertTrue(moved);
        assertArrayEquals(recording, Files.readAllBytes(destination.toPath()));
        assertFalse(source.exists());
    }

    @Test
    public void conversionSkipsOccupiedDestinationsAndReturnsActualOutputPath() throws Exception {
        File source = temporaryFolder.newFile("conversion.tmp");
        File destination = temporaryFolder.newFile("song.mp3");
        File previousConversion = temporaryFolder.newFile("song_001.mp3");
        File expectedOutput = new File(temporaryFolder.getRoot(), "song_002.mp3");
        byte[] convertedRecording = "converted audio".getBytes(StandardCharsets.UTF_8);
        byte[] existingRecording = "original mp3".getBytes(StandardCharsets.UTF_8);
        byte[] previousRecording = "previous conversion".getBytes(StandardCharsets.UTF_8);
        Files.write(source.toPath(), convertedRecording);
        Files.write(destination.toPath(), existingRecording);
        Files.write(previousConversion.toPath(), previousRecording);

        String actualPath = FileSystem.moveToAvailablePath(
                source.getAbsolutePath(), destination.getAbsolutePath());

        assertEquals("The caller must receive the path to the newly converted audio",
                expectedOutput.getAbsolutePath(), actualPath);
        assertArrayEquals(convertedRecording, Files.readAllBytes(new File(actualPath).toPath()));
        assertArrayEquals(existingRecording, Files.readAllBytes(destination.toPath()));
        assertArrayEquals(previousRecording, Files.readAllBytes(previousConversion.toPath()));
        assertFalse("A completed move must remove the temporary conversion", source.exists());
    }

    @Test
    public void conversionUsesRequestedDestinationWhenAvailable() throws Exception {
        File source = temporaryFolder.newFile("conversion.tmp");
        File destination = new File(temporaryFolder.getRoot(), "song.mp3");
        byte[] convertedRecording = "converted audio".getBytes(StandardCharsets.UTF_8);
        Files.write(source.toPath(), convertedRecording);

        String actualPath = FileSystem.moveToAvailablePath(
                source.getAbsolutePath(), destination.getAbsolutePath());

        assertEquals(destination.getAbsolutePath(), actualPath);
        assertArrayEquals(convertedRecording, Files.readAllBytes(destination.toPath()));
        assertFalse(source.exists());
    }
}

package apincer.music.core.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

public class ReplaceJournalTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void entriesSurviveANewInstanceUntilRemoved() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "replace-journal.txt");
        ReplaceJournal.Entry flac = new ReplaceJournal.Entry("/storage/emulated/0/Music/A/01 Song.flac", "01 Song.flac.mmtmp");
        ReplaceJournal.Entry cover = new ReplaceJournal.Entry("/storage/emulated/0/Music/A/Cover.jpg", "Cover.jpg.mmtmp");

        new ReplaceJournal(file).add(flac);
        new ReplaceJournal(file).add(cover);
        List<ReplaceJournal.Entry> afterRestart = new ReplaceJournal(file).entries();

        assertEquals(2, afterRestart.size());
        assertEquals(flac.targetPath, afterRestart.get(0).targetPath);
        assertEquals("01 Song.flac.mmtmp", afterRestart.get(0).tempName);

        ReplaceJournal journal = new ReplaceJournal(file);
        journal.remove(flac);
        assertEquals(1, journal.entries().size());
        journal.remove(cover);
        assertTrue(journal.entries().isEmpty());
        assertFalse("An empty journal leaves no file behind", file.exists());
    }

    @Test
    public void recoveryKeepsWhicheverCopyIsComplete() {
        assertEquals("Original present: the temp may be partial",
                ReplaceJournal.Recovery.DELETE_TEMP, ReplaceJournal.recovery(true, true));
        assertEquals("Original deleted: the temp was complete before that",
                ReplaceJournal.Recovery.RENAME_TEMP, ReplaceJournal.recovery(true, false));
        assertEquals(ReplaceJournal.Recovery.NONE, ReplaceJournal.recovery(false, true));
        assertEquals(ReplaceJournal.Recovery.NONE, ReplaceJournal.recovery(false, false));
    }

    @Test
    public void pathsWithSpacesAndUnicodeRoundTrip() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "j.txt");
        ReplaceJournal journal = new ReplaceJournal(file);
        ReplaceJournal.Entry thai = new ReplaceJournal.Entry("/storage/emulated/0/Music/เพลง/01 ก.flac", "01 ก.flac.mmtmp");

        journal.add(thai);

        assertEquals(thai.targetPath, journal.entries().get(0).targetPath);
        assertEquals(thai.tempName, journal.entries().get(0).tempName);
    }
}

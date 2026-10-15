package apincer.music.core.provider;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Records each safe replace (write a temp sibling, delete the original, rename the temp) while it runs,
 * so a replace cut short by a crash can be finished or undone at the next start.
 * One line per entry: the target's absolute path, a tab, the temp file's name.
 */
final class ReplaceJournal {

    /** What a leftover entry needs, from what is on disk now. */
    enum Recovery {
        /** The original is still there, so the temp may be incomplete: delete the temp. */
        DELETE_TEMP,
        /** The original was deleted after the temp was complete: rename the temp to the original name. */
        RENAME_TEMP,
        /** Nothing is left to do. */
        NONE
    }

    static Recovery recovery(boolean tempExists, boolean targetExists) {
        if (!tempExists) return Recovery.NONE;
        return targetExists ? Recovery.DELETE_TEMP : Recovery.RENAME_TEMP;
    }

    static final class Entry {
        final String targetPath;
        final String tempName;

        Entry(String targetPath, String tempName) {
            this.targetPath = targetPath;
            this.tempName = tempName;
        }

        String line() {
            return targetPath + "\t" + tempName;
        }
    }

    private final File file;

    ReplaceJournal(File file) {
        this.file = file;
    }

    synchronized void add(Entry entry) throws IOException {
        List<String> lines = read();
        lines.add(entry.line());
        write(lines);
    }

    synchronized void remove(Entry entry) throws IOException {
        List<String> lines = read();
        if (lines.remove(entry.line())) write(lines);
    }

    synchronized List<Entry> entries() throws IOException {
        List<Entry> entries = new ArrayList<>();
        for (String line : read()) {
            int tab = line.indexOf('\t');
            if (tab > 0) entries.add(new Entry(line.substring(0, tab), line.substring(tab + 1)));
        }
        return entries;
    }

    private List<String> read() throws IOException {
        if (!file.isFile()) return new ArrayList<>();
        List<String> lines = new ArrayList<>();
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            if (!line.isEmpty()) lines.add(line);
        }
        return lines;
    }

    private void write(List<String> lines) throws IOException {
        if (lines.isEmpty()) {
            Files.deleteIfExists(file.toPath());
            return;
        }
        File dir = file.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        File temp = new File(dir, file.getName() + ".new");
        Files.write(temp.toPath(), lines, StandardCharsets.UTF_8);
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}

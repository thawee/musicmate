package apincer.music.core.provider;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Every change to files in the music library goes through this interface, so the app can work with
 * direct file paths (all-files access) or with a granted folder (scoped storage).
 * Files in the app's own directories may use it too; implementations handle both.
 */
public interface MusicFileAccess {

    /** Changes a file in place, for example by committing new tags with jaudiotagger. */
    @FunctionalInterface
    interface FileEditor {
        void edit(File workingFile) throws Exception;
    }

    /** Produces the complete new content of a file. */
    @FunctionalInterface
    interface ContentWriter {
        void write(OutputStream out) throws IOException;
    }

    /**
     * Runs {@code editor} on a file that becomes {@code target}. The editor may get {@code target}
     * itself or a working copy with the same name. Rollback depends on the implementation: the path
     * implementation edits {@code target} in place, so a failure part-way can leave it partly written;
     * an implementation that edits a copy replaces {@code target} only after the editor succeeds.
     */
    void edit(File target, FileEditor editor) throws Exception;

    /**
     * Replaces {@code target} with the writer's output; if the writer throws, {@code target} is unchanged.
     * The target folder must exist; this never creates it.
     */
    void writeAtomically(File target, ContentWriter writer) throws IOException;

    /** Copies {@code source} over {@code target}, creating the target folder if needed. */
    void copy(File source, File target) throws IOException;

    /** Moves or renames {@code source}, creating the target folder if needed. */
    boolean move(File source, File target, boolean replaceExisting);

    boolean delete(File file);

    boolean mkdirs(File dir);
}

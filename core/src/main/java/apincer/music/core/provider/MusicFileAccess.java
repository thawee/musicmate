package apincer.music.core.provider;

import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.function.Predicate;

/**
 * Every change to files in the music library goes through this interface, so the app can work with
 * direct file paths (all-files access) or with a granted folder (scoped storage). So do reads of files
 * that scoped storage hides from paths (DSF, covers, .cue) and folder listings.
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

    /** Deletes a file or an empty folder; a folder with anything in it is kept. */
    boolean delete(File file);

    boolean mkdirs(File dir);

    /** Opens {@code file} for reading. Closing the channel releases everything it holds. */
    FileChannel openRead(File file) throws IOException;

    /** A content URI that reads {@code file} when its path cannot be read, otherwise null. */
    Uri readUri(File file);

    /**
     * The FFmpeg input argument for {@code file}: its quoted path, or an ffmpeg-kit "saf:" input when
     * only a granted folder can read it. A "saf:" input is good for one FFmpeg run.
     */
    String ffmpegInput(File file);

    /** The files and folders directly in {@code dir}, including ones only a granted folder shows. */
    List<File> list(File dir);

    /** Files under {@code dir} at any depth whose names pass {@code nameFilter}. */
    List<File> walk(File dir, Predicate<String> nameFilter) throws IOException;
}

package apincer.music.core.provider;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Works on file paths directly; needs all-files access for files outside the app's own directories. */
public class PathMusicFileAccess implements MusicFileAccess {
    private static final String TAG = "PathMusicFileAccess";

    @Override
    public void edit(File target, FileEditor editor) throws Exception {
        editor.edit(target);
    }

    @Override
    public void writeAtomically(File target, ContentWriter writer) throws IOException {
        File dir = target.getAbsoluteFile().getParentFile();
        if (dir == null || !dir.isDirectory()) {
            // A vanished album folder must not be recreated just to hold one file
            throw new IOException("Folder does not exist: " + dir);
        }
        // Write beside the destination so the final rename is atomic on its filesystem.
        File replacement = File.createTempFile(".mm-", ".tmp", dir);
        try {
            try (FileOutputStream out = new FileOutputStream(replacement)) {
                writer.write(out);
                out.getFD().sync();
            }
            Files.move(replacement.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            replacement.delete();
        }
    }

    @Override
    public void copy(File source, File target) throws IOException {
        if (!source.exists()) throw new IOException("Source file does not exist: " + source);
        if (source.getAbsoluteFile().equals(target.getAbsoluteFile())) return;
        mkdirs(target.getAbsoluteFile().getParentFile());
        try (FileChannel in = new FileInputStream(source).getChannel();
             FileChannel out = new FileOutputStream(target).getChannel()) {
            long size = in.size();
            for (long position = 0; position < size; ) {
                long copied = out.transferFrom(in, position, size - position);
                // The source shrank while copying; stop instead of looping forever
                if (copied <= 0) throw new IOException("Source ended early: " + source);
                position += copied;
            }
        }
    }

    @Override
    public boolean move(File source, File target, boolean replaceExisting) {
        if (!source.exists()) return false;
        mkdirs(target.getAbsoluteFile().getParentFile());
        try {
            if (replaceExisting) {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source.toPath(), target.toPath());
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "move " + source + " -> " + target, e);
            return false;
        }
    }

    @Override
    public boolean delete(File file) {
        if (!file.exists()) return false;
        try {
            Files.deleteIfExists(file.toPath());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "delete " + file, e);
            return false;
        }
    }

    @Override
    public boolean mkdirs(File dir) {
        return dir == null || dir.isDirectory() || dir.mkdirs() || dir.isDirectory();
    }
}

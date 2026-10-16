package apincer.music.core.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.util.Log;

import com.antonkarpenko.ffmpegkit.FFmpegKitConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Uses file paths when the app holds all-files access or the file is in the app's own storage, and
 * otherwise works through a folder the user granted in the system folder picker. Replacing a file in a
 * granted folder writes a "name.mmtmp" sibling, deletes the original and renames the sibling, with a
 * journal so a replace cut short by a crash is finished or undone by {@link #recover()}. Reads use the
 * path whenever it is readable (audio MediaStore indexes), and the granted folder only for the rest.
 */
public class AndroidMusicFileAccess implements MusicFileAccess {
    private static final String TAG = "AndroidMusicFileAccess";
    static final String TEMP_SUFFIX = ".mmtmp";
    private static final String BINARY = "application/octet-stream";

    private final Context context;
    private final ContentResolver resolver;
    private final PathMusicFileAccess paths = new PathMusicFileAccess();
    private final ReplaceJournal journal;
    private final List<String> appDirs;
    private volatile List<StorageTreeRoots.Root> roots;

    public AndroidMusicFileAccess(Context context) {
        this(context, new File(context.getApplicationContext().getFilesDir(), "replace-journal.txt"));
    }

    /** Keeps the replace journal in {@code journalFile}: one line per replace, target path, a tab, temp name. */
    public AndroidMusicFileAccess(Context context, File journalFile) {
        this.context = context.getApplicationContext();
        this.resolver = this.context.getContentResolver();
        this.journal = new ReplaceJournal(journalFile);
        this.appDirs = appDirectories(this.context);
    }

    private static List<String> appDirectories(Context context) {
        List<File> dirs = new ArrayList<>();
        dirs.add(context.getDataDir());
        dirs.add(context.getCacheDir());
        for (File dir : context.getExternalFilesDirs(null)) dirs.add(dir);
        for (File dir : context.getExternalCacheDirs()) dirs.add(dir);
        for (File dir : context.getObbDirs()) dirs.add(dir);
        List<String> paths = new ArrayList<>();
        for (File dir : dirs) if (dir != null) paths.add(dir.getAbsolutePath());
        return paths;
    }

    /** A file in a granted folder. */
    private final class Doc {
        final StorageTreeRoots.Root root;
        final File file;
        final Uri uri;

        Doc(StorageTreeRoots.Root root, File file) {
            this.root = root;
            this.file = file;
            this.uri = DocumentsContract.buildDocumentUriUsingTree(
                    Uri.parse(root.treeUri), root.documentId(file.getAbsolutePath()));
        }

        Doc parent() {
            File parent = file.getParentFile();
            return parent != null && root.covers(parent.getAbsolutePath()) ? new Doc(root, parent) : null;
        }

        Doc child(String name) {
            return new Doc(root, new File(file, name));
        }

        /** Calls {@code visitor} with each name in this folder and whether it is a folder. */
        void children(ChildVisitor visitor) {
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                    Uri.parse(root.treeUri), root.documentId(file.getAbsolutePath()));
            try (Cursor cursor = resolver.query(children, new String[]{
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
                while (cursor != null && cursor.moveToNext()) {
                    visitor.visit(cursor.getString(0),
                            DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(1)));
                }
            }
        }

        boolean exists() {
            return mimeType() != null;
        }

        /** The document's MIME type, {@link DocumentsContract.Document#MIME_TYPE_DIR} for a folder; null when missing. */
        String mimeType() {
            try (Cursor cursor = resolver.query(uri,
                    new String[]{DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
                return cursor != null && cursor.moveToFirst() ? cursor.getString(0) : null;
            } catch (Exception e) {
                return null; // the provider reports a missing document by throwing
            }
        }
    }

    private interface ChildVisitor {
        void visit(String name, boolean folder);
    }

    /** The granted document for {@code file}, or null when file paths are the way to reach it. */
    private Doc doc(File file) {
        if (Environment.isExternalStorageManager() || inAppStorage(file)) return null;
        List<StorageTreeRoots.Root> known = roots;
        Doc doc = known == null ? null : doc(known, file);
        if (doc == null) {
            // A folder may have been granted since the last look
            known = StorageTreeRoots.load(context);
            roots = known;
            doc = doc(known, file);
        }
        return doc;
    }

    private Doc doc(List<StorageTreeRoots.Root> known, File file) {
        StorageTreeRoots.Root root = StorageTreeRoots.find(known, file.getAbsolutePath());
        return root == null ? null : new Doc(root, file.getAbsoluteFile());
    }

    private boolean inAppStorage(File file) {
        String path = file.getAbsolutePath();
        for (String dir : appDirs) {
            if (path.equals(dir) || path.startsWith(dir + "/")) return true;
        }
        return false;
    }

    /** Forgets cached grants, for example after one was released. */
    public void refreshRoots() {
        roots = null;
    }

    @Override
    public void edit(File target, FileEditor editor) throws Exception {
        Doc doc = doc(target);
        if (doc == null) {
            paths.edit(target, editor);
            return;
        }
        File dir = Files.createTempDirectory(context.getCacheDir().toPath(), "edit-").toFile();
        File working = new File(dir, target.getName());
        try {
            try (InputStream in = resolver.openInputStream(doc.uri)) {
                if (in == null) throw new IOException("Cannot read " + target);
                Files.copy(in, working.toPath());
            }
            editor.edit(working);
            replace(doc, out -> Files.copy(working.toPath(), out));
        } finally {
            File[] leftovers = dir.listFiles();
            if (leftovers != null) for (File leftover : leftovers) leftover.delete();
            dir.delete();
        }
    }

    @Override
    public void writeAtomically(File target, ContentWriter writer) throws IOException {
        Doc doc = doc(target);
        if (doc == null) {
            paths.writeAtomically(target, writer);
        } else {
            replace(doc, writer);
        }
    }

    private void replace(Doc doc, ContentWriter writer) throws IOException {
        Doc parent = doc.parent();
        if (parent == null || !parent.exists()) {
            // A vanished album folder must not be recreated just to hold one file
            throw new IOException("Folder does not exist: " + doc.file.getParentFile());
        }
        String name = doc.file.getName();
        // Unique per replace, so two replaces of one file never share a temp
        String tempName = name + "." + Long.toHexString(System.nanoTime()) + TEMP_SUFFIX;
        Doc temp = parent.child(tempName);

        ReplaceJournal.Entry entry = new ReplaceJournal.Entry(doc.file.getAbsolutePath(), tempName);
        journal.add(entry);
        boolean originalGone = false;
        try {
            Uri created = createExact(parent, tempName, BINARY);
            try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(created, "w")) {
                if (pfd == null) throw new IOException("Cannot write " + tempName);
                try (FileOutputStream out = new FileOutputStream(pfd.getFileDescriptor())) {
                    writer.write(out);
                    out.flush();
                    out.getFD().sync();
                }
            }
            if (doc.exists() && !DocumentsContract.deleteDocument(resolver, doc.uri)) {
                throw new IOException("Cannot replace " + doc.file);
            }
            originalGone = true;
            renameExact(created, name);
            journal.remove(entry);
        } catch (IOException | RuntimeException e) {
            if (!originalGone) {
                // The original is untouched; drop the incomplete copy
                try {
                    if (temp.exists()) DocumentsContract.deleteDocument(resolver, temp.uri);
                } catch (Exception ignored) {
                    // recover() deletes it later
                }
                if (!temp.exists()) journal.remove(entry);
            }
            // Otherwise the complete copy stays under its temp name and recover() renames it
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
    }

    private static boolean hasName(String documentId, String name) {
        return documentId.endsWith("/" + name) || documentId.endsWith(":" + name);
    }

    /** Creates a document named exactly {@code name}; the provider would add " (1)" on a clash. */
    private Uri createExact(Doc parent, String name, String mimeType) throws IOException {
        Uri created = DocumentsContract.createDocument(resolver, parent.uri, mimeType, name);
        if (created == null) throw new IOException("Cannot create " + name + " in " + parent.file);
        if (!hasName(DocumentsContract.getDocumentId(created), name)) {
            DocumentsContract.deleteDocument(resolver, created);
            throw new IOException("Name already taken: " + new File(parent.file, name));
        }
        return created;
    }

    private Uri renameExact(Uri uri, String name) throws IOException {
        Uri renamed = DocumentsContract.renameDocument(resolver, uri, name);
        if (renamed == null || !hasName(DocumentsContract.getDocumentId(renamed), name)) {
            throw new IOException("Cannot rename " + DocumentsContract.getDocumentId(uri) + " to " + name);
        }
        return renamed;
    }

    private boolean exists(File file, Doc doc) {
        return doc != null ? doc.exists() : file.exists();
    }

    private InputStream open(File file, Doc doc) throws IOException {
        if (doc == null) return new FileInputStream(file);
        InputStream in = resolver.openInputStream(doc.uri);
        if (in == null) throw new IOException("Cannot read " + file);
        return in;
    }

    @Override
    public void copy(File source, File target) throws IOException {
        Doc sourceDoc = doc(source);
        Doc targetDoc = doc(target);
        if (sourceDoc == null && targetDoc == null) {
            paths.copy(source, target);
            return;
        }
        if (!exists(source, sourceDoc)) throw new IOException("Source file does not exist: " + source);
        if (source.getAbsoluteFile().equals(target.getAbsoluteFile())) return;
        if (!mkdirs(target.getAbsoluteFile().getParentFile())) {
            throw new IOException("Cannot create folder " + target.getParentFile());
        }
        try (InputStream in = open(source, sourceDoc)) {
            writeAtomically(target, in::transferTo);
        }
    }

    @Override
    public boolean move(File source, File target, boolean replaceExisting) {
        Doc sourceDoc = doc(source);
        Doc targetDoc = doc(target);
        if (sourceDoc == null && targetDoc == null) return paths.move(source, target, replaceExisting);
        try {
            if (!exists(source, sourceDoc)) return false;
            if (!replaceExisting && exists(target, targetDoc)) return false;
            if (sourceDoc != null && targetDoc != null
                    && sourceDoc.root.volumeId.equals(targetDoc.root.volumeId)
                    && moveOnVolume(sourceDoc, targetDoc)) {
                return true;
            }
            // Another volume, or one side is app storage: copy, then remove the source
            copy(source, target);
            if (!delete(source)) Log.w(TAG, "Copied but could not remove " + source);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "move " + source + " -> " + target, e);
            return false;
        }
    }

    /** Renames or moves within one volume; false when the caller should copy instead. */
    private boolean moveOnVolume(Doc source, Doc target) throws IOException {
        Doc sourceParent = source.parent();
        Doc targetParent = target.parent();
        if (sourceParent == null || targetParent == null) return false;
        if (!mkdirs(targetParent.file)) return false;
        String name = target.file.getName();
        if (target.exists() && !DocumentsContract.deleteDocument(resolver, target.uri)) {
            throw new IOException("Cannot replace " + target.file);
        }
        if (sourceParent.file.equals(targetParent.file)) {
            renameExact(source.uri, name);
            return true;
        }
        // Rename first, so a failed move leaves the file where the library expects it
        Uri current = source.uri;
        boolean renamed = !source.file.getName().equals(name);
        if (renamed) {
            if (sourceParent.child(name).exists()) return false; // the new name is taken here; copy instead
            current = renameExact(source.uri, name);
        }
        Uri moved;
        try {
            moved = DocumentsContract.moveDocument(resolver, current, sourceParent.uri, targetParent.uri);
        } catch (Exception e) {
            Log.w(TAG, "moveDocument failed, copying instead: " + source.file, e);
            moved = null;
        }
        if (moved == null && renamed) renameExact(current, source.file.getName());
        return moved != null;
    }

    @Override
    public boolean delete(File file) {
        Doc doc = doc(file);
        if (doc == null) return paths.delete(file);
        try {
            String mimeType = doc.mimeType();
            if (mimeType == null) return false;
            // The provider deletes a folder with everything in it, even files a path listing cannot see
            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType) && !list(doc).isEmpty()) {
                Log.w(TAG, "Not deleting a folder that is not empty: " + file);
                return false;
            }
            return DocumentsContract.deleteDocument(resolver, doc.uri);
        } catch (Exception e) {
            Log.e(TAG, "delete " + file, e);
            return false;
        }
    }

    @Override
    public boolean mkdirs(File dir) {
        if (dir == null) return true;
        Doc doc = doc(dir);
        if (doc == null) return paths.mkdirs(dir);
        if (doc.exists()) return true;
        Doc parent = doc.parent();
        if (parent == null || !mkdirs(parent.file)) return false;
        try {
            createExact(parent, dir.getName(), DocumentsContract.Document.MIME_TYPE_DIR);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "mkdirs " + dir, e);
            return doc.exists();
        }
    }

    @Override
    public FileChannel openRead(File file) throws IOException {
        Uri uri = readUri(file);
        if (uri == null) return paths.openRead(file);
        ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r");
        if (pfd == null) throw new IOException("Cannot read " + file);
        // The stream owns the descriptor, and closing the channel closes the stream
        return new ParcelFileDescriptor.AutoCloseInputStream(pfd).getChannel();
    }

    @Override
    public Uri readUri(File file) {
        // Checked first, so readable audio and app storage never pay for the grant lookup
        if (Files.isReadable(file.toPath())) return null;
        Doc doc = doc(file);
        return doc == null ? null : doc.uri;
    }

    @Override
    public String ffmpegInput(File file) {
        Uri uri = readUri(file);
        return uri == null ? paths.ffmpegInput(file) : FFmpegKitConfig.getSafParameterForRead(context, uri);
    }

    @Override
    public List<File> list(File dir) {
        Doc doc = doc(dir);
        if (doc == null) return paths.list(dir);
        try {
            return list(doc);
        } catch (Exception e) {
            Log.e(TAG, "list " + dir, e);
            return new ArrayList<>();
        }
    }

    /** Throws when the folder cannot be listed, so {@link #delete} never takes that for empty. */
    private List<File> list(Doc dir) {
        List<File> files = new ArrayList<>();
        dir.children((name, folder) -> files.add(new File(dir.file, name)));
        return files;
    }

    @Override
    public List<File> walk(File dir, Predicate<String> nameFilter) throws IOException {
        if (Environment.isExternalStorageManager()) return paths.walk(dir, nameFilter);
        List<StorageTreeRoots.Root> known = StorageTreeRoots.load(context);
        roots = known;
        List<File> found = new ArrayList<>();
        ArrayDeque<File> folders = new ArrayDeque<>();
        folders.add(dir.getAbsoluteFile());
        while (!folders.isEmpty()) {
            File folder = folders.poll();
            // Each folder on its own: a grant may cover only part of the walk
            Doc doc = inAppStorage(folder) ? null : doc(known, folder);
            if (doc != null) {
                try {
                    doc.children((name, isFolder) -> {
                        File child = new File(folder, name);
                        if (isFolder) folders.add(child);
                        else if (nameFilter.test(name)) found.add(child);
                    });
                } catch (Exception e) {
                    Log.e(TAG, "walk " + folder, e);
                }
            } else {
                File[] children = folder.listFiles();
                if (children == null) continue;
                for (File child : children) {
                    if (child.isDirectory()) folders.add(child);
                    else if (nameFilter.test(child.getName())) found.add(child);
                }
            }
        }
        return found;
    }

    /** Finishes or undoes replaces cut short by a crash. Run once at start, off the main thread. */
    public void recover() {
        List<ReplaceJournal.Entry> entries;
        try {
            entries = journal.entries();
        } catch (IOException e) {
            Log.e(TAG, "Cannot read the replace journal", e);
            return;
        }
        for (ReplaceJournal.Entry entry : entries) {
            File target = new File(entry.targetPath);
            try {
                Doc targetDoc = doc(target);
                Doc parent = targetDoc == null ? null : targetDoc.parent();
                ReplaceJournal.Recovery recovery;
                if (targetDoc != null && parent != null) {
                    Doc temp = parent.child(entry.tempName);
                    recovery = ReplaceJournal.recovery(temp.exists(), targetDoc.exists());
                    switch (recovery) {
                        case DELETE_TEMP -> DocumentsContract.deleteDocument(resolver, temp.uri);
                        case RENAME_TEMP -> renameExact(temp.uri, target.getName());
                        case NONE -> { }
                    }
                } else {
                    File temp = new File(target.getParentFile(), entry.tempName);
                    recovery = ReplaceJournal.recovery(temp.exists(), target.exists());
                    switch (recovery) {
                        case DELETE_TEMP -> Files.delete(temp.toPath());
                        case RENAME_TEMP -> Files.move(temp.toPath(), target.toPath());
                        case NONE -> { }
                    }
                }
                Log.i(TAG, "Interrupted replace of " + target + ": " + recovery);
                journal.remove(entry);
            } catch (Exception e) {
                // Keep the entry; the next start tries again
                Log.e(TAG, "Cannot recover the replace of " + target, e);
            }
        }
    }
}

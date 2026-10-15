package apincer.music.core.provider;

import android.content.Context;
import android.content.UriPermission;
import android.net.Uri;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps the folders the user granted through the system folder picker to file paths, so library code
 * can keep identifying tracks by path. A granted tree "primary:Music" covers /storage/emulated/0/Music.
 */
public final class StorageTreeRoots {
    public static final String AUTHORITY = "com.android.externalstorage.documents";
    static final String PRIMARY_VOLUME = "primary";

    private StorageTreeRoots() {
    }

    /** One granted folder. */
    public static final class Root {
        public final String treeUri;
        public final String volumeId;
        public final String volumeDir;
        public final String absolutePath;

        Root(String treeUri, String volumeId, String volumeDir, String absolutePath) {
            this.treeUri = treeUri;
            this.volumeId = volumeId;
            this.volumeDir = volumeDir;
            this.absolutePath = absolutePath;
        }

        public boolean covers(String path) {
            return path.equals(absolutePath) || path.startsWith(absolutePath + "/");
        }

        /** Document ID of {@code path} on this root's volume, or null when the root does not cover it. */
        public String documentId(String path) {
            return covers(path) ? StorageTreeRoots.documentId(volumeId, volumeDir, path) : null;
        }
    }

    /** "primary:Music/A" on a volume mounted at /storage/emulated/0 is /storage/emulated/0/Music/A. */
    static String absolutePath(String treeDocumentId, Map<String, String> volumeDirs) {
        int colon = treeDocumentId.indexOf(':');
        if (colon <= 0) return null;
        String volumeDir = volumeDirs.get(treeDocumentId.substring(0, colon));
        if (volumeDir == null) return null;
        String relative = treeDocumentId.substring(colon + 1);
        while (relative.endsWith("/")) relative = relative.substring(0, relative.length() - 1);
        return relative.isEmpty() ? volumeDir : volumeDir + "/" + relative;
    }

    static String documentId(String volumeId, String volumeDir, String path) {
        if (path.equals(volumeDir)) return volumeId + ":";
        if (!path.startsWith(volumeDir + "/")) return null;
        return volumeId + ":" + path.substring(volumeDir.length() + 1);
    }

    static Root root(String treeUri, String treeDocumentId, Map<String, String> volumeDirs) {
        String path = absolutePath(treeDocumentId, volumeDirs);
        if (path == null) return null;
        String volumeId = treeDocumentId.substring(0, treeDocumentId.indexOf(':'));
        return new Root(treeUri, volumeId, volumeDirs.get(volumeId), path);
    }

    /** The narrowest root covering {@code path}, or null. */
    static Root find(List<Root> roots, String path) {
        Root best = null;
        for (Root root : roots) {
            if (root.covers(path) && (best == null || root.absolutePath.length() > best.absolutePath.length())) {
                best = root;
            }
        }
        return best;
    }

    /** Mounted volumes by document-ID prefix: "primary" or the SD card's UUID. */
    static Map<String, String> volumeDirs(Context context) {
        Map<String, String> dirs = new HashMap<>();
        StorageManager storage = context.getSystemService(StorageManager.class);
        if (storage == null) return dirs;
        for (StorageVolume volume : storage.getStorageVolumes()) {
            File dir = volume.getDirectory();
            if (dir == null) continue;
            String id = volume.isPrimary() ? PRIMARY_VOLUME : volume.getUuid();
            if (id != null) dirs.put(id, dir.getAbsolutePath());
        }
        return dirs;
    }

    /** Folders granted with both read and write access through the external storage provider. */
    public static List<Root> load(Context context) {
        Map<String, String> volumeDirs = volumeDirs(context);
        List<Root> roots = new ArrayList<>();
        for (UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
            Uri uri = permission.getUri();
            if (!permission.isReadPermission() || !permission.isWritePermission()) continue;
            if (!AUTHORITY.equals(uri.getAuthority()) || !DocumentsContract.isTreeUri(uri)) continue;
            Root root = root(uri.toString(), DocumentsContract.getTreeDocumentId(uri), volumeDirs);
            if (root != null) roots.add(root);
        }
        return roots;
    }

    /** The folder a tree URI from the system folder picker stands for, or null for an unknown volume. */
    public static String pathOf(Context context, Uri treeUri) {
        return absolutePath(DocumentsContract.getTreeDocumentId(treeUri), volumeDirs(context));
    }

    /** A document URI for {@code path}, used to open the folder picker there; null off any volume. */
    public static Uri documentUri(Context context, String path) {
        for (Map.Entry<String, String> volume : volumeDirs(context).entrySet()) {
            String id = documentId(volume.getKey(), volume.getValue(), path);
            if (id != null) return DocumentsContract.buildDocumentUri(AUTHORITY, id);
        }
        return null;
    }

    /** The narrowest granted root covering {@code file}, or null. */
    public static Root find(Context context, File file) {
        return find(load(context), file.getAbsolutePath());
    }
}

package apincer.android.mmate.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import apincer.android.mmate.R
import apincer.android.mmate.ui.compose.coversFolder
import apincer.music.core.provider.StorageMode
import apincer.music.core.provider.StorageTreeRoots
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.StorageId
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/**
 * Folder access in the Play build, which has no all-files access: MusicMate can change only folders
 * the user granted in the system folder picker. The GitHub build has access everywhere.
 */
object MusicFolderAccess {

    /** True when [path] can be changed: all-files access, or a granted folder covers it. */
    @JvmStatic
    fun canWrite(context: Context, path: String): Boolean =
        StorageMode.usesAllFilesAccess(context) || StorageTreeRoots.find(context, File(path)) != null

    /** Organize moves files under the primary volume's Music folder (FileRepository.buildCollectionPath). */
    @JvmStatic
    fun organizeRoot(context: Context): String =
        DocumentFileCompat.buildAbsolutePath(context, StorageId.PRIMARY, "Music")

    @JvmStatic
    fun hasOrganizeAccess(context: Context): Boolean = canWrite(context, organizeRoot(context))

    /** Explains why Organize needs the Music folder, then opens the system folder picker there. */
    @JvmStatic
    fun askForOrganizeAccess(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        MaterialAlertDialogBuilder(activity, R.style.AlertDialogTheme)
            .setTitle(R.string.organize_needs_music_title)
            .setMessage(R.string.organize_needs_music_message)
            .setPositiveButton(R.string.organize_needs_music_action) { _, _ ->
                launcher.launch(FolderGrantActivity.createIntent(activity, organizeRoot(activity)))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** The folder a [FolderGrantActivity] result granted, or null when the user backed out. */
    @JvmStatic
    fun grantedPath(result: ActivityResult): String? =
        if (result.resultCode == Activity.RESULT_OK) result.data?.getStringExtra(FolderGrantActivity.EXTRA_PATH) else null

    /** True when [root] is [path] or one of its parent folders. */
    @JvmStatic
    fun covers(root: String, path: String): Boolean = coversFolder(root, path)
}

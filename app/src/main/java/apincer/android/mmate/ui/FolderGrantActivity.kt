package apincer.android.mmate.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import apincer.music.core.provider.StorageTreeRoots

/**
 * Asks the user for a music folder in the system folder picker and keeps read and write access to it,
 * for use without all-files access. Returns the folder's path in [EXTRA_PATH]. The picker itself
 * refuses the root of a volume and the Download root.
 */
class FolderGrantActivity : ComponentActivity() {

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        val path = tree?.let {
            contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            StorageTreeRoots.pathOf(this, it)
        }
        if (path == null) {
            setResult(RESULT_CANCELED)
        } else {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_PATH, path).setData(tree))
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            pickFolder.launch(intent.getStringExtra(EXTRA_INITIAL_PATH)?.let { StorageTreeRoots.documentUri(this, it) })
        }
    }

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_INITIAL_PATH = "initial_path"

        fun createIntent(context: Context, initialPath: String?): Intent =
            Intent(context, FolderGrantActivity::class.java).putExtra(EXTRA_INITIAL_PATH, initialPath)
    }
}

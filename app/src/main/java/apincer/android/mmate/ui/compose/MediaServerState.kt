package apincer.android.mmate.ui.compose

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MediaServerState {
    var serverStatusText by mutableStateOf("Server Stopped")
    var isServerRunning by mutableStateOf(false)
    var isNetworkAvailable by mutableStateOf(true)
    var serverUrl by mutableStateOf("")
    var broadcastInfo by mutableStateOf("")
    var qrCodeBitmap by mutableStateOf<Bitmap?>(null)
    /** Live server summary, refreshed by the Server tab while it is shown and the server runs. */
    var diagnostics by mutableStateOf("")
    /** Where the Server tab reads the summary from; set while the server runs. */
    var diagnosticsSource: (() -> String)? = null
}

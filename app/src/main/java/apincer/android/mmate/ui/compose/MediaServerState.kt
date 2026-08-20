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
    var currentEngine by mutableStateOf("httpcore")
    var engineDescription by mutableStateOf("")
}

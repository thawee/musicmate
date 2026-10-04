package apincer.android.mmate.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import apincer.android.mmate.service.MediaServerManager
import apincer.music.core.server.spi.MediaServerHub
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MediaServerViewModel @Inject constructor(
    private val serverManager: MediaServerManager
) : ViewModel() {

    init {
        serverManager.doBindService()
    }

    fun getServerStatus(): LiveData<MediaServerHub.ServerStatus> = serverManager.serverStatus

    fun getServerLocationUrl(): String? = serverManager.serverLocationUrl

    fun getLibraryName(): String? = serverManager.libraryName

    fun startServer() {
        serverManager.startServer()
    }

    fun stopServer() {
        serverManager.stopServer()
    }

    fun restartServer() {
        serverManager.restartServer()
    }

    override fun onCleared() {
        super.onCleared()
        serverManager.doUnbindService()
    }
}

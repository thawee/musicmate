package apincer.android.mmate.ui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;
import apincer.android.mmate.service.MediaServerManager;
import apincer.music.core.server.spi.MediaServerHub;

@HiltViewModel
public class MediaServerViewModel extends ViewModel {

    private final MediaServerManager serverManager;

    @Inject
    public MediaServerViewModel(MediaServerManager serverManager) {
        this.serverManager = serverManager;
        // The ViewModel can bind to the service when it's created.
        serverManager.doBindService();
    }

    public LiveData<MediaServerHub.ServerStatus> getServerStatus() {
        return serverManager.getServerStatus();
    }

    public String getServerLocationUrl() {
        return serverManager.getServerLocationUrl();
    }

    public void startServer() {
        serverManager.startServer();
    }

    public void stopServer() {
        serverManager.stopServer();
    }

    // The ViewModel handles unbinding when it is cleared (i.e., when the UI is permanently gone).
    @Override
    protected void onCleared() {
        super.onCleared();
        serverManager.doUnbindService();
    }

    public String getLibraryName() {
        return serverManager.getLibraryName();
    }
}
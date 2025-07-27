package apincer.android.mmate.ui;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.model.SearchCriteria;
import apincer.android.mmate.repository.TagRepository;

interface OperationState {}

class IdleState implements OperationState {}

class InProgressState implements OperationState {
    public final int progress;
    public final String message;
    public final String currentFile;

    public InProgressState(int progress, String message, String currentFile) {
        this.progress = progress;
        this.message = message;
        this.currentFile = currentFile;
    }
}

class SuccessState implements OperationState {
    public final String message;
    public SuccessState(String message) {
        this.message = message;
    }
}

class ErrorState implements OperationState {
    public final String errorMessage;
    public ErrorState(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}

public class MainViewModel extends AndroidViewModel {
    private final ExecutorService backgroundExecutor; // For background tasks
    private final FileRepository repository;

    private final MutableLiveData<List<MusicTag>> musicTagsLiveData = new MutableLiveData<>();
    //private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

   // 1. LiveData for the list of music items
    private final MutableLiveData<List<MusicTag>> _musicItems = new MutableLiveData<>();
    public final LiveData<List<MusicTag>> musicItems = _musicItems;

    private final MutableLiveData<Boolean> _musicItemsLoading = new MutableLiveData<>(false);
    public final LiveData<Boolean> musicItemsLoading = _musicItemsLoading;


    private SearchCriteria currentCriteria;

    // 2. LiveData for the currently playing song
    private final MutableLiveData<MusicTag> _nowPlayingSong = new MutableLiveData<>();
    public final LiveData<MusicTag> nowPlayingSong = _nowPlayingSong;

    // 3. LiveData for operation progress (e.g., encoding)
    private final MutableLiveData<OperationState> _encodingOperationState = new MutableLiveData<>(new IdleState());
    public final LiveData<OperationState> encodingOperationState = _encodingOperationState;

    public MainViewModel(Application application) {
        super(application);
        repository = FileRepository.newInstance(application);
        this.backgroundExecutor = Executors.newFixedThreadPool(2); // Example executor
    }

    /*
    @Deprecated
    public LiveData<List<MusicTag>> getMusicTags() {
        return musicTagsLiveData;
    } */

    public void loadMusicItems(SearchCriteria criteria) {
        currentCriteria = criteria;
        _musicItemsLoading.setValue(true);

        backgroundExecutor.execute(() -> {
            try {
                List<MusicTag> items = TagRepository.findMediaTag(criteria);
                _musicItems.postValue(items);
                _musicItemsLoading.postValue(false);
            } catch (Exception e) {
                // Handle error
                _musicItems.postValue(Collections.emptyList());
                _musicItemsLoading.postValue(false);
            }
        });
    }

    // --- Setting the now playing song ---
    public void setNowPlaying(MusicTag song) {
        if (song == null) {
            _nowPlayingSong.postValue(null);
            return;
        }
       // backgroundExecutor.execute(() -> {
           // try {
               // MusicTag song = TagRepository.getMusicTag(songId); // Assuming blocking
                _nowPlayingSong.postValue(song);
           // } catch (Exception e) {
           //     _nowPlayingSong.postValue(null);
          //  }
        //});
    }

    // --- Simulating an encoding operation ---
    public void startEncodingOperation(List<MusicTag> filesToEncode /*, other params */) {
        _encodingOperationState.postValue(new InProgressState(0, "Starting encoding...", null));

        backgroundExecutor.execute(() -> {
            try {
                for (int i = 0; i < filesToEncode.size(); i++) {
                    MusicTag file = filesToEncode.get(i);
                    // Simulate processing a file
                    Thread.sleep(1000); // Simulate work
                    int progress = ((i + 1) * 100) / filesToEncode.size();
                    _encodingOperationState.postValue(
                            new InProgressState(progress, "Encoding: " + file.getTitle(), file.getTitle())
                    );
                }
                _encodingOperationState.postValue(new SuccessState("All files encoded successfully!"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interruption status
                _encodingOperationState.postValue(new ErrorState("Encoding interrupted: " + e.getMessage()));
            } catch (Exception e) {
                _encodingOperationState.postValue(new ErrorState("Encoding failed: " + e.getMessage()));
            }
        });
    }

    public void resetOperationState() {
        _encodingOperationState.postValue(new IdleState());
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        backgroundExecutor.shutdownNow(); // Important to clean up your executors
    }

    public void deleteMediaItems(List<MusicTag> selections) {
        for(MusicTag tag: selections) {
            repository.deleteMediaItem(tag);
        }
        // Reload data after deletion
        //loadMusicTags(currentCriteria);
        loadMusicItems(currentCriteria);
    }

    public void moveMediaItems(List<MusicTag> selections) {
        for(MusicTag tag: selections) {
            repository.importAudioFile(tag);
        }
        // Reload data after moving
        //loadMusicTags(currentCriteria);
        loadMusicItems(currentCriteria);
    }

    public static class MusicViewModelFactory implements ViewModelProvider.Factory {
        private final Application application;

        public MusicViewModelFactory(Application application) {
            this.application = application;
        }

        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            if (modelClass.isAssignableFrom(MainViewModel.class)) {
                return (T) new MainViewModel(application);
            }
            throw new IllegalArgumentException("Unknown ViewModel class");
        }
    }
}

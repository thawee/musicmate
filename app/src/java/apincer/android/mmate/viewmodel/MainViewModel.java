package apincer.android.mmate.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import apincer.android.mmate.repository.database.MusicTag;
import apincer.android.mmate.repository.model.SearchCriteria;
import apincer.android.mmate.repository.TagRepository;

public class MainViewModel extends AndroidViewModel {
    private final ExecutorService backgroundExecutor; // For background tasks

   // 1. LiveData for the list of music items
    private final MutableLiveData<List<MusicTag>> _musicItems = new MutableLiveData<>();
    public final LiveData<List<MusicTag>> musicItems = _musicItems;

    private final MutableLiveData<Boolean> _musicItemsLoading = new MutableLiveData<>(false);
    public final LiveData<Boolean> musicItemsLoading = _musicItemsLoading;

    private SearchCriteria currentCriteria;

    public MainViewModel(Application application) {
        super(application);
        this.backgroundExecutor = Executors.newFixedThreadPool(2); // Example executor
    }

    public void loadMusicItems() {
        loadMusicItems(currentCriteria);
    }

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

    @Override
    protected void onCleared() {
        super.onCleared();
        backgroundExecutor.shutdownNow(); // Important to clean up your executors
    }

    public static class MusicViewModelFactory implements ViewModelProvider.Factory {
        private final Application application;

        public MusicViewModelFactory(Application application) {
            this.application = application;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            if (modelClass.isAssignableFrom(MainViewModel.class)) {
                return (T) new MainViewModel(application);
            }
            throw new IllegalArgumentException("Unknown ViewModel class");
        }
    }
}

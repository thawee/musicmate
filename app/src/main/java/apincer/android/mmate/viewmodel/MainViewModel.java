package apincer.android.mmate.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import apincer.music.core.database.MusicTag;
import apincer.music.core.model.SearchCriteria;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class MainViewModel extends ViewModel {
    private final ExecutorService backgroundExecutor; // For background tasks
    private final TagRepository repos;
    private final FileRepository fileRepos;

   // 1. LiveData for the list of music items
    private final MutableLiveData<List<MusicTag>> _musicItems = new MutableLiveData<>();
    public final LiveData<List<MusicTag>> musicItems = _musicItems;

    private final MutableLiveData<Boolean> _musicItemsLoading = new MutableLiveData<>(false);
    public final LiveData<Boolean> musicItemsLoading = _musicItemsLoading;

    private SearchCriteria currentCriteria;

    @Inject
    public MainViewModel(FileRepository fileRepos, TagRepository repos) {
        super();
        this.repos = repos;
        this.fileRepos = fileRepos;
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
                List<MusicTag> items = repos.findMediaTag(criteria);
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

    public TagRepository getTagRepository() {
        return repos;
    }

    public FileRepository getFileRepository() {
        return fileRepos;
    }
}

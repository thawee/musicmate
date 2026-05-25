package apincer.android.mmate.ui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import apincer.android.mmate.ui.MusicTagAdapter;
import apincer.music.core.model.SearchCriteria;
import apincer.music.core.model.Track;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class MainViewModel extends ViewModel {
    private final ExecutorService backgroundExecutor; // For background tasks
    private final TagRepository repos;
    private final FileRepository fileRepos;

   // 1. LiveData for the list of music items
    private final MutableLiveData<List<Track>> _musicItems = new MutableLiveData<>();
    public final LiveData<List<Track>> musicItems = _musicItems;

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

    private int currentPage = 0;
    private static final int PAGE_SIZE = 500;
    private boolean isLastPage = false;

    public void loadMusicItems() {
        loadMusicItems(currentCriteria);
    }

    public void loadMoreMusicItems() {
        if (Boolean.TRUE.equals(_musicItemsLoading.getValue()) || isLastPage) return;

        _musicItemsLoading.setValue(true);
        backgroundExecutor.execute(() -> {
            try {
                List<Track> items = repos.findMusic(currentCriteria, currentPage * PAGE_SIZE, PAGE_SIZE);
                if (!items.isEmpty()) {
                    List<Track> currentItems = _musicItems.getValue();
                    if (currentItems == null) {
                        currentItems = new java.util.ArrayList<>();
                    } else {
                        currentItems = new java.util.ArrayList<>(currentItems); // Create mutable copy
                    }
                    currentItems.addAll(items);
                    _musicItems.postValue(currentItems);
                    currentPage++;
                    if (items.size() < PAGE_SIZE) {
                        isLastPage = true;
                    }
                } else {
                    isLastPage = true;
                }
                _musicItemsLoading.postValue(false);
            } catch (Exception e) {
                _musicItemsLoading.postValue(false);
            }
        });
    }

    public void loadMusicItems(SearchCriteria criteria) {
        currentCriteria = criteria;
        currentPage = 0;
        isLastPage = false;
        _musicItemsLoading.setValue(true);

        backgroundExecutor.execute(() -> {
            try {
                List<Track> items = repos.findMusic(criteria, 0, PAGE_SIZE);
                _musicItems.postValue(items);
                if (items.size() < PAGE_SIZE) {
                    isLastPage = true;
                } else {
                    currentPage = 1;
                }
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

    public void search(MusicTagAdapter adapter, String query) {
        adapter.search(query);
        loadMusicItems(adapter.getCriteria());
    }
}

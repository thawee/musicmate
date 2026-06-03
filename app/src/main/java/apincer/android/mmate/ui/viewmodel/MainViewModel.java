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
import apincer.music.core.model.SearchResultStats;
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

    private final MutableLiveData<SearchResultStats> _searchStats = new MutableLiveData<>();
    public final LiveData<SearchResultStats> searchStats = _searchStats;

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

    public void loadUntilFound(Track target, Runnable onLoaded) {
        if (target == null) {
            if (onLoaded != null) onLoaded.run();
            return;
        }

        // Check if already loaded
        List<Track> currentItems = _musicItems.getValue();
        if (currentItems != null && currentItems.contains(target)) {
            if (onLoaded != null) onLoaded.run();
            return;
        }
        
        if (isLastPage) {
            if (onLoaded != null) onLoaded.run();
            return;
        }

        _musicItemsLoading.setValue(true);
        backgroundExecutor.execute(() -> {
            try {
                boolean found = false;
                List<Track> current = _musicItems.getValue();
                if (current == null) {
                    current = new java.util.ArrayList<>();
                } else {
                    current = new java.util.ArrayList<>(current);
                }

                while (!found && !isLastPage) {
                    List<Track> items = repos.findMusic(currentCriteria, currentPage * PAGE_SIZE, PAGE_SIZE);
                    if (items.isEmpty()) {
                        isLastPage = true;
                        break;
                    }
                    current.addAll(items);
                    currentPage++;
                    if (items.size() < PAGE_SIZE) {
                        isLastPage = true;
                    }
                    if (items.contains(target)) {
                        found = true;
                    }
                }

                _musicItems.postValue(current);
                _musicItemsLoading.postValue(false);
                
                if (onLoaded != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onLoaded);
                }
            } catch (Exception e) {
                _musicItemsLoading.postValue(false);
                if (onLoaded != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onLoaded);
                }
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

                // Fetch total stats matching criteria from DB
                SearchResultStats stats = repos.getSearchStats(criteria);
                _searchStats.postValue(stats);
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

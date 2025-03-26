package apincer.android.mmate.ui.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.List;

import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.SearchCriteria;
import apincer.android.mmate.repository.TagRepository;
import apincer.android.mmate.worker.MusicMateExecutors;

public class MainViewModel extends AndroidViewModel {
    private final FileRepository repository;
    private final MutableLiveData<List<MusicTag>> musicTagsLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private SearchCriteria currentCriteria;

    public MainViewModel(Application application) {
        super(application);
        repository = FileRepository.newInstance(application);
    }

    public LiveData<List<MusicTag>> getMusicTags() {
        return musicTagsLiveData;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public void loadMusicTags(SearchCriteria criteria) {
        currentCriteria = criteria;
        isLoading.setValue(true);

        MusicMateExecutors.execute(() -> {
            List<MusicTag> tags = TagRepository.findMediaTag(criteria);
            musicTagsLiveData.postValue(tags);
            isLoading.postValue(false);
        });
    }

    public void deleteMediaItems(List<MusicTag> selections) {
        for(MusicTag tag: selections) {
            repository.deleteMediaItem(tag);
        }
        // Reload data after deletion
        loadMusicTags(currentCriteria);
    }

    public void moveMediaItems(List<MusicTag> selections) {
        for(MusicTag tag: selections) {
            repository.importAudioFile(tag);
        }
        // Reload data after moving
        loadMusicTags(currentCriteria);
    }
}

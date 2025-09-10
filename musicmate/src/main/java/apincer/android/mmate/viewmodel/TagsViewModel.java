package apincer.android.mmate.viewmodel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import apincer.android.mmate.codec.MusicAnalyser;
import apincer.android.mmate.repository.database.MusicTag;
import apincer.android.mmate.repository.TagRepository;
import apincer.android.mmate.codec.TagWriter;
import android.content.Context;

public class TagsViewModel extends ViewModel {

    private final ExecutorService executorService;

    public static final String MULTIPLE_ALBUMS = "[Multiple Albums]";
    public static final String MULTIPLE_ARTISTS = "[Multiple Artists]";
    public static final String MULTIPLE_ALBUM_ARTISTS = "[Multiple Album Artists]";
    public static final String MULTIPLE_EMPTY = "";

    // --- Core Data ---
    private final MutableLiveData<List<MusicTag>> _editItems = new MutableLiveData<>(Collections.emptyList());
    public final LiveData<List<MusicTag>> editItems = _editItems;

    private final MutableLiveData<MusicTag> _displayTag = new MutableLiveData<>();
    public final LiveData<MusicTag> displayTag = _displayTag;

    // --- Operation Status (e.g., for DR Measurement, Saving) ---
    private final MutableLiveData<OperationStatus> _drMeasurementStatus = new MutableLiveData<>(new Idle());
    public final LiveData<OperationStatus> drMeasurementStatus = _drMeasurementStatus;

    public TagsViewModel(ExecutorService executorService /*, Context applicationContext */) {
        this.executorService = executorService;
        // this.applicationContext = applicationContext;
    }

    public void processAudioTagEditEvent(List<MusicTag> items) {
        //_searchCriteria.postValue(criteria);
        _editItems.postValue(items); // Post a copy
        rebuildDisplayTag(items);
    }

    public void updateWithPlayingSong(MusicTag playingSong) {
        if (playingSong == null) return;
        // This is a simplified version of your updatePreview logic
        // You might need more sophisticated logic to decide if the current editItems
        // should be completely replaced or if this is just for preview.
        TagRepository.load(playingSong); // Assuming this is synchronous or you handle async
        List<MusicTag> singleItemList = new ArrayList<>();
        singleItemList.add(playingSong.copy()); // Work with a copy
        _editItems.postValue(singleItemList);
        rebuildDisplayTag(singleItemList);
    }

    private void rebuildDisplayTag(List<MusicTag> currentItems) {
        if (currentItems == null || currentItems.isEmpty()) {
            _displayTag.postValue(null);
            return;
        }
        // This is your logic from TagsActivity.buildDisplayTag()
        // For simplicity, I'm just taking the first item. Adapt as needed.
        MusicTag newDisplayTag = currentItems.get(0).copy(); // Make a copy for display
        if (currentItems.size() > 1) {
            // Your logic to create a "common" or summary tag
            newDisplayTag.setTitle("["+currentItems.size() + " songs selected]");
            // Use the generic helper for each field
            newDisplayTag.setAlbum(getCommonStringValue(currentItems, MusicTag::getAlbum, MULTIPLE_ALBUMS, MULTIPLE_EMPTY));
            newDisplayTag.setArtist(getCommonStringValue(currentItems, MusicTag::getArtist, MULTIPLE_ARTISTS, MULTIPLE_EMPTY));
            newDisplayTag.setAlbumArtist(getCommonStringValue(currentItems, MusicTag::getAlbumArtist, MULTIPLE_ALBUM_ARTISTS, MULTIPLE_EMPTY));
            newDisplayTag.setGenre(getCommonStringValue(currentItems, MusicTag::getGenre, MULTIPLE_EMPTY, MULTIPLE_EMPTY));
            newDisplayTag.setGrouping(getCommonStringValue(currentItems, MusicTag::getGrouping, MULTIPLE_EMPTY, MULTIPLE_EMPTY));
            newDisplayTag.setTrack(getCommonStringValue(currentItems, MusicTag::getTrack, MULTIPLE_EMPTY, MULTIPLE_EMPTY));
            newDisplayTag.setYear(getCommonStringValue(currentItems, MusicTag::getYear, MULTIPLE_EMPTY, MULTIPLE_EMPTY));
            newDisplayTag.setDisc(getCommonStringValue(currentItems, MusicTag::getDisc, MULTIPLE_EMPTY, MULTIPLE_EMPTY));
           // newDisplayTag.setMediaType(getCommonStringValue(currentItems, MusicTag::getMediaType, MULTIPLE_EMPTY, MULTIPLE_EMPTY));
            newDisplayTag.setPublisher(getCommonStringValue(currentItems, MusicTag::getPublisher, MULTIPLE_EMPTY, MULTIPLE_EMPTY));
            newDisplayTag.setQualityRating(getCommonStringValue(currentItems, MusicTag::getQualityRating, MULTIPLE_EMPTY, MULTIPLE_EMPTY));
        }
        _displayTag.postValue(newDisplayTag);
    }

    /**
     * Generic method to find a common string value from a list of MusicTags for a given field.
     *
     * @param tags List of MusicTag objects.
     * @param valueExtractor A function that takes a MusicTag and returns the String value of the desired field.
     * @param multipleValueString The string to return if multiple distinct non-null values are found, or if mixing nulls and non-nulls.
     * @param defaultValueForNullCommon If all non-null tags have a null value for the field, what to return (e.g., "", or null).
     * @return The common string value, multipleValueString, or defaultValueForNullCommon.
     */
    public static String getCommonStringValue(List<MusicTag> tags,
                                              Function<MusicTag, String> valueExtractor,
                                              String multipleValueString,
                                              String defaultValueForNullCommon) {
        if (tags == null || tags.isEmpty()) {
            return multipleValueString; // Or some other default for empty list
        }

        List<String> values = tags.stream()
                .filter(Objects::nonNull) // Ignore null MusicTag objects
                .map(valueExtractor)      // Extract the specific field's value (can be null)
                .toList();

        if (values.isEmpty()) {
            // All MusicTag objects in the original list were null
            return multipleValueString; // Or appropriate default
        }

        long distinctCount = values.stream().distinct().limit(2).count();

        if (distinctCount > 1) {
            return multipleValueString;
        } else { // distinctCount is 1 (or 0 if values was empty, handled above)
            String commonValue = values.get(0); // This is the common value (could be null)
            return commonValue != null ? commonValue : defaultValueForNullCommon;
        }
    }

    // --- Actions ---

    public void measureDynamicRange(Context context /* Pass context here if unavoidable for TagWriter */) {
        List<MusicTag> items = _editItems.getValue();
        if (items == null || items.isEmpty()) {
            _drMeasurementStatus.postValue(new Error("No items to process."));
            return;
        }

        _drMeasurementStatus.postValue(new Loading("Starting DR measurement..."));

        CompletableFuture.runAsync(() -> {
            int successCount = 0;
            for (int i = 0; i < items.size(); i++) {
                MusicTag tag = items.get(i);
                try {
                    _drMeasurementStatus.postValue(new ProgressUpdate( (i * 100) / items.size(), "Analysing: " + tag.getTitle()));
                    if (MusicAnalyser.analyse(tag)) { // Assuming analyse modifies the tag
                        TagWriter.writeTagToFile(context, tag); // BE CAREFUL with context in ViewModel
                        TagRepository.saveTag(tag); // Assuming this updates your persistent storage
                        successCount++;
                    }
                } catch (Exception ex) {
                    // Log error for this specific tag
                    // You might want a more granular error reporting mechanism
                    System.err.println("Error processing DR for " + tag.getPath() + ": " + ex.getMessage());
                }
            }
            // After processing, update editItems and displayTag if tags were modified
            // This might require re-fetching or carefully updating the existing LiveData list
            List<MusicTag> updatedItems = new ArrayList<>(items); // Or refetch
            _editItems.postValue(updatedItems); // This will trigger observers
            rebuildDisplayTag(updatedItems);     // This will also trigger observers

            if(successCount == items.size()) {
                _drMeasurementStatus.postValue(new Success("DR measurement completed for all items."));
            } else if (successCount > 0) {
                _drMeasurementStatus.postValue(new Success("DR measurement partially completed ("+successCount+"/"+items.size()+"). Check logs for errors."));
            } else {
                _drMeasurementStatus.postValue(new Error("DR measurement failed for all items."));
            }

        }, executorService).exceptionally(ex -> {
            _drMeasurementStatus.postValue(new Error("DR measurement failed: " + ex.getMessage()));
            return null;
        });
    }

    public void resetDrMeasurementStatus() {
        _drMeasurementStatus.postValue(new Idle());
    }

    public void refreshDisplayTag() {
        List<MusicTag> items = _editItems.getValue();
        List<MusicTag> updatedItems = new ArrayList<>(); // Or refetch
        //should reload music tags
        items.forEach(musicTag -> {
            TagRepository.load(musicTag);
            updatedItems.add(musicTag);
        });
        _editItems.postValue(updatedItems); // This will trigger observers
        rebuildDisplayTag(updatedItems);     // This will also trigger observers
    }

    // --- ViewModel Factory ---
    public static class TagsViewModelFactory implements ViewModelProvider.Factory {
        private final ExecutorService executorService;

        public TagsViewModelFactory(ExecutorService executorService /*, Context applicationContext */) {
            this.executorService = executorService;
        }

        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            if (modelClass.isAssignableFrom(TagsViewModel.class)) {
                return (T) new TagsViewModel(executorService /*, applicationContext */);
            }
            throw new IllegalArgumentException("Unknown ViewModel class");
        }
    }
}

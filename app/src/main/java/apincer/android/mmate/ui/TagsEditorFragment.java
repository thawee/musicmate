package apincer.android.mmate.ui;

import static apincer.music.core.utils.StringUtils.isEmpty;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

import apincer.music.core.model.Track;
import apincer.music.core.utils.ThaiEncodingUtils;
import apincer.music.core.utils.FilenamePatternDetector;
import apincer.music.core.utils.MusicMateExecutors;
import apincer.android.mmate.R;
import apincer.android.mmate.coil3.CoverartFetcher;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.utils.MusicPathTagParser;
import apincer.android.mmate.utils.TagUIUtils;
import apincer.music.core.utils.StringUtils;
import co.lujun.androidtagview.ColorFactory;
import co.lujun.androidtagview.TagContainerLayout;
import coil3.ImageLoader;
import coil3.SingletonImageLoader;
import coil3.request.ImageRequest;
import coil3.target.ImageViewTarget;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class TagsEditorFragment extends Fragment {
    private static final String TAG = "TagsEditorFragment";
    protected Context context;
    protected TagsActivity tagsActivity;
    private TextView previewTitle;
    private TextView previewPath;
    private ImageView previewCoverart;
    private TextInputEditText txtTitle;
    private AutoCompleteTextView txtArtist;
    private TextInputEditText txtAlbum;
    private AutoCompleteTextView txtAlbumArtist;
    private TextInputEditText txtTrack;
    private TextInputEditText txtYear;
    private AutoCompleteTextView txtOrigin;
    private AutoCompleteTextView txtGenre;
    private AutoCompleteTextView txtStyle;
    private AutoCompleteTextView txtMood;
    private AutoCompleteTextView txtPublisher;

    private NestedScrollView scrollView;

    @Inject
    TagRepository tagRepos;
    @Inject
    FileRepository fileRepos;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.context = context;
        this.tagsActivity = (TagsActivity) getActivity();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_editor_tags, container, false);
        previewTitle = v.findViewById(R.id.preview_title);
        previewPath = v.findViewById(R.id.editor_pathname);
        previewCoverart = v.findViewById(R.id.preview_coverart);
        // input fields
        txtTitle = v.findViewById(R.id.input_title);
        txtArtist = v.findViewById(R.id.input_artist);
        txtAlbum = v.findViewById(R.id.input_album);
        txtAlbumArtist = v.findViewById(R.id.input_album_artist);
        txtTrack = v.findViewById(R.id.input_track);
        txtYear = v.findViewById(R.id.input_year);
        txtGenre = v.findViewById(R.id.input_genre);
        txtStyle = v.findViewById(R.id.input_style);
        txtOrigin = v.findViewById(R.id.input_origin);
        txtMood = v.findViewById(R.id.input_mood);
        //txtGrouping = v.findViewById(R.id.input_grouping);
        txtPublisher = v.findViewById(R.id.input_publisher);
        //qualityDropdown = v.findViewById(R.id.mediaQualityDropdown);

        // --- FIX #1: Fix the NestedScrollView crash ---
        NestedScrollView myScrollView = v.findViewById(R.id.editor_scroll_view);
        myScrollView.setNestedScrollingEnabled(false);

        // --- FIX #2: Clear focus on scroll ---
        myScrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v1, scrollX, scrollY, oldScrollX, oldScrollY) -> {

            // Check if the user is actively scrolling
            if (scrollY != oldScrollY) {
                View currentFocus = requireActivity().getCurrentFocus();
                if (currentFocus != null) {
                    // Clear focus from the EditText
                    currentFocus.clearFocus();

                    // Hide the keyboard
                    //hideKeyboard(currentFocus);
                }
            }
        });

        // popup list — static lists (safe on main thread)
        setupListValuePopupFullList(txtGenre, TagRepository.getDefaultGenreList(getContext()));
        setupListValuePopupFullList(txtStyle, TagRepository.getDefaultStyleList(getContext()));
        setupListValuePopupFullList(txtOrigin, TagRepository.getDefaultOriginList(getContext()));
        setupListValuePopupFullList(txtMood, TagRepository.getDefaultMoodList(getContext()));

        // DB-backed lists — load off main thread
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        Context appContext = getContext().getApplicationContext();
        MusicMateExecutors.execute(() -> {
            List<String> artists = tagRepos.getArtistList();
            List<String> publishers = tagRepos.getDefaultPublisherList(appContext);
            mainHandler.post(() -> {
                if (isAdded()) {
                    setupListValuePopup(txtArtist, artists, 1);
                    setupListValuePopup(txtPublisher, publishers, 1);
                }
            });
        });

        return v;
    }

    private void setupListValuePopup(AutoCompleteTextView input, List<String> dropdownList, int minChar) {
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(getContext(), R.layout.item_dropdown_dark, dropdownList);
        input.setAdapter(arrayAdapter);
        input.setThreshold(minChar);
    }

    private void setupListValuePopupFullList(AutoCompleteTextView input, List<String> dropdownList) {
        ArrayAdapter<String> arrayAdapter = new NoFilterArrayAdapter<>(getContext(), R.layout.item_dropdown_dark, dropdownList);
        input.setAdapter(arrayAdapter);
        input.setThreshold(0);

        // Disable keyboard input — dropdown only
       // input.setKeyListener(null);
       // input.setFocusable(false);
       // input.setClickable(true);

        // Always open dropdown when clicked
        input.setOnClickListener(v -> input.showDropDown());

        // Optional: dark popup background
        input.setDropDownBackgroundResource(R.color.black_transparent_64);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Track musicTag = tagsActivity.getDisplayTag();
        if(musicTag != null) {
            doPreviewMusicInfo(musicTag);
            initEditorInputs(musicTag);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    public void doShowReadTagsPreview() {
        List<Track> editItems = tagsActivity.getEditItems();
        if (editItems.isEmpty()) return;
        Track firstItem = editItems.get(0);

        View cview = getLayoutInflater().inflate(R.layout.view_actionview_tags_from_filename, null);
        TextView filename = cview.findViewById(R.id.full_filename);
        filename.setText(firstItem.getSimpleName());

        EditText title = cview.findViewById(R.id.title);
        EditText artist = cview.findViewById(R.id.artist);
        EditText album = cview.findViewById(R.id.album);
        EditText track = cview.findViewById(R.id.track);

        TextView titleLabel = cview.findViewById(R.id.title_label);
        TextView artistLabel = cview.findViewById(R.id.artist_label);
        TextView albumLabel = cview.findViewById(R.id.album_label);
        TextView trackLabel = cview.findViewById(R.id.track_label);
        TextView sepLabel = cview.findViewById(R.id.btn_add_sep);
        TextView dashLabel = cview.findViewById(R.id.btn_add_dash);
        TextView dotLabel = cview.findViewById(R.id.btn_add_dot);
        TextView spaceLabel = cview.findViewById(R.id.btn_add_space);
        TextView freeTextLabel = cview.findViewById(R.id.btn_add_free_text);
        Button btnAutoDetect = cview.findViewById(R.id.btn_auto_detect);
        Spinner spinnerPresets = cview.findViewById(R.id.spinner_presets);

        title.setText(firstItem.getTitle());
        artist.setText(firstItem.getArtist());
        album.setText(firstItem.getAlbum());
        track.setText(firstItem.getTrack());

        TagContainerLayout mTagListLayout = cview.findViewById(R.id.tagcontainerLayout);
        mTagListLayout.setTheme(ColorFactory.NONE);
        mTagListLayout.setTagBackgroundColor(Color.TRANSPARENT);
        
        // Auto-detect pattern from all selected files
        List<String> allFilenames = new ArrayList<>();
        for (Track item : editItems) {
            allFilenames.add(item.getSimpleName());
        }
        List<String> detectedPattern = FilenamePatternDetector.detectPattern(allFilenames);
        mTagListLayout.setTags(detectedPattern);
        
        mTagListLayout.setTagContainerChangeListener(() -> {
            updatePreview(mTagListLayout.getTags(), firstItem, title, artist, album, track);
        });
        
        // Setup preset spinner
        String[] presetNames = FilenamePatternDetector.getPresetNames();
        List<String[]> presets = FilenamePatternDetector.getPresets();
        ArrayAdapter<String> presetAdapter = new ArrayAdapter<>(requireContext(), 
                android.R.layout.simple_spinner_item, presetNames);
        presetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPresets.setAdapter(presetAdapter);
        spinnerPresets.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < presets.size()) {
                    mTagListLayout.setTags(Arrays.asList(presets.get(position)));
                    // Update preview
                    updatePreview(mTagListLayout.getTags(), firstItem, title, artist, album, track);
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        
        // Auto-detect button - re-analyze all files
        btnAutoDetect.setOnClickListener(v -> {
            List<String> filenames = new ArrayList<>();
            for (Track item : editItems) {
                filenames.add(item.getSimpleName());
            }
            List<String> pattern = FilenamePatternDetector.detectPattern(filenames);
            mTagListLayout.setTags(pattern);
            updatePreview(pattern, firstItem, title, artist, album, track);
        });
        mTagListLayout.setOnTagClickListener(new co.lujun.androidtagview.TagView.OnTagClickListener() {

            @Override
            public void onTagClick(int position, String text) {

            }

            @Override
            public void onTagLongClick(final int position, String text) {
            }

            @Override
            public void onSelectedTagDrag(int position, String text){
                // ...
            }

            @Override
            public void onTagCrossClick(int position) {
                if (position < mTagListLayout.getChildCount()) {
                    mTagListLayout.removeTag(position);
                }
            }
        });
        titleLabel.setOnClickListener(v -> mTagListLayout.addTag("title"));
        albumLabel.setOnClickListener(v -> mTagListLayout.addTag("album"));
        artistLabel.setOnClickListener(v -> mTagListLayout.addTag("artist"));
        trackLabel.setOnClickListener(v -> mTagListLayout.addTag("track"));
        sepLabel.setOnClickListener(v -> mTagListLayout.addTag("/"));
        dashLabel.setOnClickListener(v -> mTagListLayout.addTag("-"));
        dotLabel.setOnClickListener(v -> mTagListLayout.addTag("."));
        spaceLabel.setOnClickListener(v -> mTagListLayout.addTag("sp"));
        freeTextLabel.setOnClickListener(v -> {
            // Create an AlertDialog with an EditText for input
            AlertDialog.Builder builder = new MaterialAlertDialogBuilder(requireActivity(), R.style.AlertDialogTheme);
            View inputView = getLayoutInflater().inflate(R.layout.dialog_text_input, null);
            EditText editText = inputView.findViewById(R.id.input_text);

            builder.setTitle(R.string.enter_custom_text)
                    .setView(inputView)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        String customText = editText.getText().toString().trim();
                        if (!customText.isEmpty()) {
                            mTagListLayout.addTag(customText);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null);

            AlertDialog dialog = builder.create();
            dialog.show();

            // Show keyboard automatically
            editText.requestFocus();
            editText.post(() -> {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                        requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            });
        });

        View btnPreview = cview.findViewById(R.id.btn_preview_bar);
        View btnOK = cview.findViewById(R.id.button_ok);
        View btnCancel = cview.findViewById(R.id.button_cancel);
        btnPreview.setOnClickListener(v -> {
            title.setText("");
            artist.setText("");
            album.setText("");
            track.setText("");
            try {
                List<String> list = mTagListLayout.getTags();// that will return TagModel List
                MusicPathTagParser parser = new MusicPathTagParser();
                List<Track> previewItems = tagsActivity.getEditItems();
                if (previewItems.isEmpty()) return;
                Track item = previewItems.get(0);
                Track mdata = item.copy();
                parser.parse(mdata, list);
                title.setText(StringUtils.trimToEmpty(mdata.getTitle()));
                artist.setText(StringUtils.trimToEmpty(mdata.getArtist()));
                album.setText(StringUtils.trimToEmpty(mdata.getAlbum()));
                track.setText(StringUtils.trimToEmpty(mdata.getTrack()));
            }catch (Exception ex) {
                Log.e(TAG, "doShowReadTagsPreview",ex);
            }
        });
        
        // Helper method to update preview
        // (defined as lambda-friendly method)
        
        AlertDialog alert = new MaterialAlertDialogBuilder(requireActivity(), R.style.AlertDialogTheme)
                .setTitle("")
                .setView(cview)
                .setCancelable(true)
                .create();
        alert.requestWindowFeature(Window.FEATURE_NO_TITLE);
        alert.setCanceledOnTouchOutside(false);
        // make popup round corners
        alert.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        btnOK.setOnClickListener(v -> {
            List<String> list = mTagListLayout.getTags();
            MusicPathTagParser parser = new MusicPathTagParser();
            List<Track> items = tagsActivity.getEditItems();
            for(Track item:items) {
                String mediaPath =  item.getPath();
                File file = new File(mediaPath);
                if(!file.exists()) continue;
                parser.parse(item, list);
            }
            // display only
            //tagsActivity.refreshDisplayTag();
            tagsActivity.rebuildDisplayTag(items);
            alert.dismiss();
        });
        btnCancel.setOnClickListener(v -> alert.dismiss());
        alert.show();
    }

    public void doSaveMediaItem() {
        tagsActivity.startProgressBar();

        // Get a snapshot of items to avoid concurrent modification
        final List<Track> itemsToSave = new ArrayList<>(tagsActivity.getEditItems());
        final int totalItems = itemsToSave.size();
        final AtomicInteger completedCount = new AtomicInteger(0);

        // lose focus all dropdown
        View currentFocus = requireActivity().getCurrentFocus();
        if (currentFocus != null) {
            // Clear focus from the EditText
            currentFocus.clearFocus();

            // Hide the keyboard
            //hideKeyboard(currentFocus);
        }

        // Process tags in a thread pool more efficiently
        CompletableFuture<Void> processingFuture = CompletableFuture.runAsync(() -> {
            // Build pending tags in bulk first
            for (Track item : itemsToSave) {
                buildPendingTags(item);
            }
        }).thenCompose(unused -> {

            // Process all items in parallel but with controlled concurrency
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (Track tag : itemsToSave) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        boolean status = fileRepos.setMusicTag(tag);
                        int current = completedCount.incrementAndGet();
                        tagsActivity.updateProgressBar(current + "/" + totalItems);
                        // Post events one at a time
                    } catch (Exception e) {
                        Log.e(TAG, "doSaveMediaItem", e);
                    }
                }, MusicMateExecutors.getExecutorService());
                futures.add(future);
            }

            // Wait for all futures to complete
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        }).thenAccept(unused -> tagsActivity.refreshDisplayTag());

        // Handle completion
        processingFuture.whenComplete((result, exception) -> {
            if (exception == null) {
                tagsActivity.setSaved(true);
            } else {
                Log.e(TAG, "Error saving tags", exception);
            }

            // Update UI
            tagsActivity.refreshDisplayTag();
        });
    }

    private void buildPendingTags(Track tagUpdate) {
        tagUpdate.setTitle(buildTag(txtTitle, tagUpdate.getTitle()));
        tagUpdate.setTrack(buildTag(txtTrack, tagUpdate.getTrack(), tagUpdate.getTrack()));
        tagUpdate.setAlbum(buildTag(txtAlbum, tagUpdate.getAlbum()));
        tagUpdate.setArtist(buildTag(txtArtist, tagUpdate.getArtist()));
        tagUpdate.setAlbumArtist(buildTag(txtAlbumArtist, tagUpdate.getAlbumArtist()));
        tagUpdate.setGenre(buildTag(txtGenre, tagUpdate.getGenre(), tagUpdate.getGenre()));
        tagUpdate.setMood(buildTag(txtMood, tagUpdate.getMood(), tagUpdate.getMood()));
        tagUpdate.setStyle(buildTag(txtStyle, tagUpdate.getStyle(), tagUpdate.getStyle()));
        tagUpdate.setOrigin(buildTag(txtOrigin, tagUpdate.getOrigin(),tagUpdate.getOrigin()));
        tagUpdate.setPublisher(buildTag(txtPublisher, tagUpdate.getPublisher()));
        tagUpdate.setYear(buildTag(txtYear, tagUpdate.getYear()));
    }

    private String buildTag(TextInputEditText txt, String oldVal) {
        String text = StringUtils.trimToEmpty(String.valueOf(txt.getText()));
        if(isEmpty(text) || " - ".equals(text)) {
            return "";
        }else if (isMultiValuesMarker(text)) {
            return oldVal;
        }
        return text;
    }

    private String buildTag(TextView txt, String oldVal) {
        String text = StringUtils.trimToEmpty(String.valueOf(txt.getText()));
        if(isEmpty(text) || " - ".equals(text)) {
            return "";
        }else if (isMultiValuesMarker(text)) {
            return oldVal;
        }
        return text;
    }

    private String buildTag(TextView txt, String oldVal, String defaultVal) {
        String text = StringUtils.trimToEmpty(String.valueOf(txt.getText()));
        if(isEmpty(text) || " - ".equals(text)) {
            return "";
        }else if (isMultiValuesMarker(text)) {
            return oldVal;
        }
        return text;
    }

    private boolean isMultiValuesMarker(String text) {
        if(text == null) return true;
        return text.startsWith("[") && text.endsWith("]");
    }

    public void doFormatTags() {
        tagsActivity.startProgressBar();
        CompletableFuture.supplyAsync(
                () -> {
                    int thaiFixedCount = 0;
                    int totalFormatted = 0;
                    for (Track tag : tagsActivity.getEditItems()) {
                        boolean thaiFixed = false;
                        if (ThaiEncodingUtils.isGarbledThai(tag.getTitle())) {
                            tag.setTitle(ThaiEncodingUtils.fixThaiEncoding(tag.getTitle()));
                            thaiFixed = true;
                        }
                        if (ThaiEncodingUtils.isGarbledThai(tag.getArtist())) {
                            tag.setArtist(ThaiEncodingUtils.fixThaiEncoding(tag.getArtist()));
                            thaiFixed = true;
                        }
                        if (ThaiEncodingUtils.isGarbledThai(tag.getAlbum())) {
                            tag.setAlbum(ThaiEncodingUtils.fixThaiEncoding(tag.getAlbum()));
                            thaiFixed = true;
                        }
                        if (ThaiEncodingUtils.isGarbledThai(tag.getAlbumArtist())) {
                            tag.setAlbumArtist(ThaiEncodingUtils.fixThaiEncoding(tag.getAlbumArtist()));
                            thaiFixed = true;
                        }
                        if (ThaiEncodingUtils.isGarbledThai(tag.getGenre())) {
                            tag.setGenre(ThaiEncodingUtils.fixThaiEncoding(tag.getGenre()));
                            thaiFixed = true;
                        }
                        if (ThaiEncodingUtils.isGarbledThai(tag.getComposer())) {
                            tag.setComposer(ThaiEncodingUtils.fixThaiEncoding(tag.getComposer()));
                            thaiFixed = true;
                        }
                        if (thaiFixed) thaiFixedCount++;

                        tag.setTitle(StringUtils.formatTitle(tag.getTitle()));
                        tag.setArtist(StringUtils.formatArtists(tag.getArtist()));
                        tag.setAlbum(StringUtils.formatTitle(tag.getAlbum()));
                        tag.setAlbumArtist(StringUtils.formatTitle(tag.getAlbumArtist()));
                        tag.setGenre(StringUtils.formatTitle(tag.getGenre()));
                        if (!StringUtils.isEmpty(tag.getTrack())) {
                            tag.setTrack(StringUtils.formatTrack(tag.getTrack()));
                        }
                        if (StringUtils.isEmpty(tag.getAlbum())) {
                            tag.setAlbum(StringUtils.formatTitle(TagUIUtils.getDefaultAlbum(tag)));
                        }
                        totalFormatted++;
                    }
                    return new int[]{totalFormatted, thaiFixedCount};
                }
        ).thenAccept(
                result -> {
                    int total = result[0];
                    int thaiFixed = result[1];
                    tagsActivity.redisplayTag();
                    tagsActivity.stopProgressBar();

                    String msg = "Reformatted " + total + " track(s)";
                    if (thaiFixed > 0) {
                        msg += " (Fixed Thai encoding on " + thaiFixed + ")";
                    }
                    Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                }
        ).exceptionally(
                throwable -> {
                    tagsActivity.redisplayTag();
                    tagsActivity.stopProgressBar();
                    return null;
                }
        );
    }

    void initEditorInputs(Track tag) {
        if (tag == null) return;
        doPreviewMusicInfo(tag);

        txtTitle.setText(tag.getTitle());
        txtArtist.setText(tag.getArtist());
        txtAlbum.setText(tag.getAlbum());
        txtAlbumArtist.setText(tag.getAlbumArtist());
        txtTrack.setText(tag.getTrack());
        txtYear.setText(tag.getYear());
        txtGenre.setText(tag.getGenre());
        txtMood.setText(tag.getMood());
        txtStyle.setText(tag.getStyle());
        txtOrigin.setText(tag.getOrigin());
        txtPublisher.setText(tag.getPublisher());

        // Build a targeted dropdown for txtAlbumArtist: default presets + current track artist / album artist
        setupTargetedAlbumArtistDropdown(tag);

        txtTitle.invalidate();
        txtArtist.invalidate();
        txtAlbum.invalidate();
        txtAlbumArtist.invalidate();

        // quality
        //qualityDropdown.setText(tag.getQualityRating());
    }

    private void setupTargetedAlbumArtistDropdown(Track tag) {
        if (getContext() == null) return;
        List<String> list = new ArrayList<>();
        // 1. Defaults: Various Artists, Soundtrack, etc.
        String[] defaults = getContext().getResources().getStringArray(R.array.default_album_artist);
        for (String d : defaults) {
            String trimmed = StringUtils.trimToEmpty(d);
            if (!trimmed.isEmpty() && !list.contains(trimmed)) {
                list.add(trimmed);
            }
        }
        // 2. Current Track Artist
        if (tag != null) {
            String artist = StringUtils.trimToEmpty(tag.getArtist());
            if (!artist.isEmpty() && !list.contains(artist)) {
                list.add(artist);
            }
            String albumArtist = StringUtils.trimToEmpty(tag.getAlbumArtist());
            if (!albumArtist.isEmpty() && !list.contains(albumArtist)) {
                list.add(albumArtist);
            }
        }
        setupListValuePopupFullList(txtAlbumArtist, list);
    }

    private void doPreviewMusicInfo(Track tag) {
        ImageLoader imageLoader = SingletonImageLoader.get(getContext());
        ImageRequest request = CoverartFetcher.builder(getContext(), tag)
                                .data(tag)
                .target(new ImageViewTarget(previewCoverart))
               // .error(imageRequest -> CoverartFetcher.getDefaultCover(getContext()))
                .build();
        imageLoader.enqueue(request);

        previewTitle.setText(TagUIUtils.getFormattedTitle(getContext(),tag));
        previewPath.setText(tag.getSimpleName());
    }
    
    /**
     * Update preview fields based on the current pattern and file.
     */
    private void updatePreview(List<String> pattern, Track item, 
                              EditText title, EditText artist, 
                              EditText album, EditText track) {
        try {
            MusicPathTagParser parser = new MusicPathTagParser();
            Track mdata = item.copy();
            parser.parse(mdata, pattern);
            title.setText(StringUtils.trimToEmpty(mdata.getTitle()));
            artist.setText(StringUtils.trimToEmpty(mdata.getArtist()));
            album.setText(StringUtils.trimToEmpty(mdata.getAlbum()));
            track.setText(StringUtils.trimToEmpty(mdata.getTrack()));
        } catch (Exception ex) {
            Log.e(TAG, "updatePreview", ex);
        }
    }

}

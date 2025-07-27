package apincer.android.mmate.ui;

import static apincer.android.mmate.utils.StringUtils.isEmpty;

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
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import apincer.android.mmate.Constants;
import apincer.android.mmate.R;
import apincer.android.mmate.provider.CoverartFetcher;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.TagRepository;
import apincer.android.mmate.utils.MusicPathTagParser;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mmate.worker.MusicMateExecutors;
import co.lujun.androidtagview.ColorFactory;
import co.lujun.androidtagview.TagContainerLayout;
import coil3.ImageLoader;
import coil3.SingletonImageLoader;
import coil3.request.ImageRequest;
import coil3.target.ImageViewTarget;

public class TagsEditorFragment extends Fragment {
    private static final String TAG = "TagsEditorFragment";
    protected Context context;
    protected TagsActivity tagsActivity;
    private View previewPanel;
    private TextView previewTitle;
    private TextView previewArtist;
    private TextView previewAlbum;
    private TextView previewPath;
    private ImageView previewCoverart;
    private TextInputEditText txtTitle;
   // private TextInputEditText txtArtist;
    private AutoCompleteTextView txtArtist;
    private TextInputEditText txtAlbum;
    private AutoCompleteTextView txtAlbumArtist;
    private TextInputEditText txtDisc;
    private TextInputEditText txtTrack;
    private TextInputEditText txtYear;
    private AutoCompleteTextView txtGenre;
    // private TextInputEditText txtGrouping;
    private AutoCompleteTextView txtGrouping;
    private AutoCompleteTextView txtMediaType;
    private AutoCompleteTextView txtPublisher;
    private FloatingActionButton fabSav;
    private AutoCompleteTextView qualityDropdown;
   //private PowerSpinnerView fileQuality;
    private volatile boolean bypassChange = false;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.context = context;
        this.tagsActivity = (TagsActivity) getActivity();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.view_music_info_editor, container, false);
        previewPanel = v.findViewById(R.id.preview_panel);
        previewTitle = v.findViewById(R.id.preview_title);
        previewArtist = v.findViewById(R.id.preview_artist);
        previewAlbum = v.findViewById(R.id.preview_album);
        previewPath = v.findViewById(R.id.editor_pathname);
        previewCoverart = v.findViewById(R.id.preview_coverart);
        // input fields
        txtTitle = v.findViewById(R.id.input_title);
       // txtArtist = v.findViewById(R.id.input_artist);
        txtArtist = v.findViewById(R.id.input_artist);
        txtAlbum = v.findViewById(R.id.input_album);
        txtAlbumArtist = v.findViewById(R.id.input_album_artist);
        txtDisc = v.findViewById(R.id.input_disc);
        txtTrack = v.findViewById(R.id.input_track);
        txtYear = v.findViewById(R.id.input_year);
        txtGenre = v.findViewById(R.id.input_genre);
        txtGrouping = v.findViewById(R.id.input_grouping);
        txtMediaType = v.findViewById(R.id.input_media_type);
        txtPublisher = v.findViewById(R.id.input_publisher);
        qualityDropdown = v.findViewById(R.id.mediaQualityDropdown);
        //fileQuality = v.findViewById(R.id.mediaFileQuality);
        fabSav = v.findViewById(R.id.fab_save);

        fabSav.setOnClickListener(view -> doSaveMediaItem());

        // popup list
        String[] qualityList = getResources().getStringArray(R.array.file_qualities);
        setupListValuePopupFullList(qualityDropdown, Arrays.asList(qualityList));
       // setupListValuePopup(txtArtist, TagRepository.getArtistList(), 3, false);
        setupListValuePopup(txtArtist, TagRepository.getArtistList(), 1);
        setupListValuePopup(txtAlbumArtist, TagRepository.getDefaultAlbumArtistList(getContext()),1);
        setupListValuePopup(txtGenre, TagRepository.getDefaultGenreList(getContext()),1);
        //TagRepository.getDefaultGenreList(getContext());
        setupListValuePopupFullList(txtGrouping, TagRepository.getDefaultGroupingList(getContext()));
        // setupListValuePopup(txtGrouping, TagRepository.getGroupingList(getContext()),1);
        setupListValuePopup(txtPublisher, TagRepository.getDefaultPublisherList(getContext()),3);
        setupListValuePopup(txtMediaType, Constants.getSourceList(requireContext()),1);

        return v;
    }

    private void setupListValuePopup(AutoCompleteTextView input, List<String> dropdownList, int minChar) {
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, dropdownList);
        input.setAdapter(arrayAdapter);
        input.setThreshold(minChar);
    }

    private void setupListValuePopupFullList(AutoCompleteTextView input, List<String> dropdownList) {
        ArrayAdapter<String> arrayAdapter = new NoFilterArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, dropdownList);
        input.setAdapter(arrayAdapter);
        input.setThreshold(0);
    }

    private void setupFileQualityList() {

        String[] qualityOptions = getResources().getStringArray(R.array.file_qualities);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getContext(),
                R.layout.dropdown_item, // Create this layout with a simple TextView
                qualityOptions
        );
        qualityDropdown.setAdapter(adapter);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MusicTag musicTag = tagsActivity.getDisplayTag();
        if(musicTag != null) {
            doPreviewMusicInfo(musicTag);
            initEditorInputs(musicTag);
        }
    }

    @Override
    public void onDestroyView() {
        //dismissPowerMenu();
        super.onDestroyView();
    }

    @Override
    public void onPause() {
       // dismissPowerMenu();
        super.onPause();
    }

   /*private void dismissPowerMenu() {
        if (powerMenu != null) {
            powerMenu.dismiss();
            powerMenu = null;
        }
    }*/

    public Toolbar.OnMenuItemClickListener getOnMenuItemClickListener() {
        return item -> {if (item.getItemId() == R.id.menu_editor_read_tag) {
                doShowReadTagsPreview();
            } else if (item.getItemId() == R.id.menu_editor_format_tag) {
                doFormatTags();
            }else if (item.getItemId() == R.id.menu_editor_save) {
                doSaveMediaItem();
            }
            return false;
        };
    }

    private void doShowReadTagsPreview() {
        View cview = getLayoutInflater().inflate(R.layout.view_actionview_tags_from_filename, null);
        TextView filename = cview.findViewById(R.id.full_filename);
        filename.setText(tagsActivity.getEditItems().get(0).getSimpleName());

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
        //TextView uscLabel = cview.findViewById(R.id.btn_add_uscore);
        TextView dotLabel = cview.findViewById(R.id.btn_add_dot);
        TextView spaceLabel = cview.findViewById(R.id.btn_add_space);
        TextView freeTextLabel = cview.findViewById(R.id.btn_add_free_text);

        title.setText(tagsActivity.getEditItems().get(0).getTitle());
        artist.setText(tagsActivity.getEditItems().get(0).getArtist());
        album.setText(tagsActivity.getEditItems().get(0).getAlbum());
        track.setText(tagsActivity.getEditItems().get(0).getTrack());

        TagContainerLayout mTagListLayout = cview.findViewById(R.id.tagcontainerLayout);
        mTagListLayout.setTheme(ColorFactory.NONE);
        mTagListLayout.setTagBackgroundColor(Color.TRANSPARENT);
        List<String> tags = new ArrayList<>();
        tags.add("/");
        tags.add("track");
        tags.add("-");
        tags.add("title");
        mTagListLayout.setTags(tags);
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
        //uscLabel.setOnClickListener(v -> mTagListLayout.addTag("_"));
        dotLabel.setOnClickListener(v -> mTagListLayout.addTag("."));
        spaceLabel.setOnClickListener(v -> mTagListLayout.addTag("sp"));
       // freeTextLabel.setOnClickListener(v -> mTagListLayout.addTag("?"));
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
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
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
        View btnOK = cview.findViewById(R.id.btn_ok);
        View btnCancel = cview.findViewById(R.id.btn_cancel);
        btnPreview.setOnClickListener(v -> {
            title.setText("");
            artist.setText("");
            album.setText("");
            track.setText("");
            try {
                List<String> list = mTagListLayout.getTags();// that will return TagModel List
                MusicPathTagParser parser = new MusicPathTagParser();
                MusicTag item = tagsActivity.getEditItems().get(0);
                MusicTag mdata = item.copy();
                parser.parse(mdata, list);
                title.setText(StringUtils.trimToEmpty(mdata.getTitle()));
                artist.setText(StringUtils.trimToEmpty(mdata.getArtist()));
                album.setText(StringUtils.trimToEmpty(mdata.getAlbum()));
                track.setText(StringUtils.trimToEmpty(mdata.getTrack()));
            }catch (Exception ex) {
                Log.e(TAG, "doShowReadTagsPreview",ex);
            }
        });

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
            List<MusicTag> items = tagsActivity.getEditItems();
            for(MusicTag item:items) {
                String mediaPath =  item.getPath();
                File file = new File(mediaPath);
                if(!file.exists()) continue;
                parser.parse(item, list);
            }
            tagsActivity.refreshDisplayTag();
            alert.dismiss();
        });
        btnCancel.setOnClickListener(v -> alert.dismiss());
        alert.show();
    }

    private void doSaveMediaItem() {
        tagsActivity.startProgressBar();

        // Get a snapshot of items to avoid concurrent modification
        final List<MusicTag> itemsToSave = new ArrayList<>(tagsActivity.getEditItems());
        final int totalItems = itemsToSave.size();
        final AtomicInteger completedCount = new AtomicInteger(0);

        // Process tags in a thread pool more efficiently
        CompletableFuture<Void> processingFuture = CompletableFuture.runAsync(() -> {
            // Build pending tags in bulk first
            for (MusicTag item : itemsToSave) {
                buildPendingTags(item);
            }
        }).thenCompose(unused -> {
            // Create repository once
            FileRepository repos = FileRepository.newInstance(tagsActivity.getApplicationContext());

            // Process all items in parallel but with controlled concurrency
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (MusicTag tag : itemsToSave) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        boolean status = repos.setMusicTag(tag);
                        int current = completedCount.incrementAndGet();
                        tagsActivity.updateProgressBar(current + "/" + totalItems);
                        tagsActivity.refreshDisplayTag();
                        // Post events one at a time
                    } catch (Exception e) {
                        Log.e(TAG, "doSaveMediaItem", e);
                    }
                }, MusicMateExecutors.getExecutorService());
                futures.add(future);
            }

            // Wait for all futures to complete
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        });

        // Handle completion
        processingFuture.whenComplete((result, exception) -> {
            if (exception != null) {
                Log.e(TAG, "Error saving tags", exception);
            }

            // Update UI
            tagsActivity.refreshDisplayTag();

        });
    }

    private void buildPendingTags(MusicTag tagUpdate) {
        if(tagUpdate.getOriginTag()==null) {
            // save original tag, the first copy is actual original
            tagUpdate.setOriginTag(tagUpdate.copy());
        }
        MusicTag originTag = tagUpdate.getOriginTag();

        tagUpdate.setTitle(buildTag(txtTitle, originTag.getTitle()));
        tagUpdate.setTrack(buildTag(txtTrack, originTag.getTrack()));
        tagUpdate.setAlbum(buildTag(txtAlbum, originTag.getAlbum()));
        tagUpdate.setAlbumArtist(buildTag(txtAlbumArtist, originTag.getAlbumArtist()));
        tagUpdate.setArtist(buildTag(txtArtist, originTag.getArtist()));
        tagUpdate.setGrouping(buildTag(txtGrouping, originTag.getGrouping()));
        tagUpdate.setGenre(buildTag(txtGenre, originTag.getGenre()));
        tagUpdate.setPublisher(buildTag(txtPublisher, originTag.getPublisher()));
        tagUpdate.setYear(buildTag(txtYear, originTag.getYear()));
        tagUpdate.setDisc(buildTag(txtDisc, originTag.getDisc()));
        tagUpdate.setMediaType(buildTag(txtMediaType, originTag.getMediaType()));
        tagUpdate.setMediaQuality(buildQualityTag());
    }

    private String buildQualityTag() {
        return qualityDropdown.getText().toString();
    }

    private String buildTag(TextInputEditText txt, String oldVal) {
        String text = StringUtils.trimToEmpty(String.valueOf(txt.getText()));
        if(isEmpty(text) || isMultiValuesMarker(text)) {
            return oldVal;
        }
        return text;
    }

    private String buildTag(TextView txt, String oldVal) {
        String text = StringUtils.trimToEmpty(String.valueOf(txt.getText()));
        if(isEmpty(text) || isMultiValuesMarker(text)) {
            return oldVal;
        }
        return text;
    }

    private boolean isMultiValuesMarker(String text) {
        if(text == null) return true;
        return text.startsWith("[") && text.endsWith("]");
    }


    private void doFormatTags() {
        tagsActivity.startProgressBar();
        CompletableFuture.runAsync(
                () -> {
                    for(MusicTag tag:tagsActivity.getEditItems()) {
                        MusicTag mItem = tag.copy();
                        tag.setOriginTag(mItem);
                        tag.setTitle(StringUtils.formatTitle(tag.getTitle()));
                        tag.setArtist(StringUtils.formatArtists(tag.getArtist()));
                        tag.setAlbum(StringUtils.formatTitle(tag.getAlbum()));
                        tag.setAlbumArtist(StringUtils.formatTitle(tag.getAlbumArtist()));
                        tag.setGenre(StringUtils.formatTitle(tag.getGenre()));
                        if(!StringUtils.isEmpty(tag.getTrack())) {
                            tag.setTrack(StringUtils.formatTrack(tag.getTrack()));
                        }
                        // clean albumArtist if same value as artist
                        if(tag.getAlbumArtist().equals(tag.getArtist())) {
                            tag.setAlbumArtist("");
                        }
                        // if album empty, add single
                        if(StringUtils.isEmpty(tag.getAlbum())) {
                            tag.setAlbum(StringUtils.formatTitle(MusicTagUtils.getDefaultAlbum(tag)));
                        }
                    }
                }
        ).thenAccept(
                unused -> {
                    tagsActivity.refreshDisplayTag();
                    tagsActivity.stopProgressBar();
                    // set updated item on main activity
                   // tagsActivity.runOnUiThread(() -> {
                       // bypassChange = true;
                       // bindViews();
                       // bypassChange = false;
                   // });
                }
        ).exceptionally(
                throwable -> {
                    tagsActivity.refreshDisplayTag();
                    tagsActivity.stopProgressBar();
                    return null;
                }
        );
    }

    void initEditorInputs(MusicTag tag) {
        doPreviewMusicInfo(tag);

        txtTitle.setText(tag.getTitle());
        txtArtist.setText(tag.getArtist());
        txtAlbum.setText(tag.getAlbum());
        txtAlbumArtist.setText(tag.getAlbumArtist());
        txtDisc.setText(tag.getDisc());
        txtTrack.setText(tag.getTrack());
        txtYear.setText(tag.getYear());
        txtGenre.setText(tag.getGenre());
        if(TagRepository.getDefaultGroupingList(getContext()).contains(tag.getGrouping())) {
            txtGrouping.setText(tag.getGrouping());
        }
        txtMediaType.setText(tag.getMediaType());
        txtPublisher.setText(tag.getPublisher());

        txtTitle.invalidate();
        txtArtist.invalidate();
        txtAlbum.invalidate();
        txtAlbumArtist.invalidate();

        // quality
        qualityDropdown.setText(tag.getMediaQuality());
    }

    private void doPreviewMusicInfo(MusicTag tag) {
        ImageLoader imageLoader = SingletonImageLoader.get(getContext());
        ImageRequest request = CoverartFetcher.builder(getContext(), tag)
                                .data(tag)
                .target(new ImageViewTarget(previewCoverart))
                .error(imageRequest -> CoverartFetcher.getDefaultCover(getContext()))
                .build();
        imageLoader.enqueue(request);

        previewTitle.setText(MusicTagUtils.getFormattedTitle(getContext(),tag));
        previewArtist.setText(tag.getArtist());
        previewAlbum.setText(tag.getAlbum());
        previewPath.setText(tag.getSimpleName());
    }

    static class NoFilterArrayAdapter<T> extends ArrayAdapter<T> {

        private final List<T> items;
        private final Filter disabledFilter;

        public NoFilterArrayAdapter(@NonNull Context context, int resource, @NonNull List<T> items) {
            super(context, resource, items);
            this.items = items;
            this.disabledFilter = new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    results.values = NoFilterArrayAdapter.this.items; // Always return the full list
                    results.count = NoFilterArrayAdapter.this.items.size();
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    notifyDataSetChanged();
                }
            };
        }

        @NonNull
        @Override
        public Filter getFilter() {
            return disabledFilter;
        }
    }
}

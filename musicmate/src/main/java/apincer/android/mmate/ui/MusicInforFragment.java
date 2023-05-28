package apincer.android.mmate.ui;

import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.toLowwerCase;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.textfield.TextInputEditText;
import com.skydoves.powermenu.CircularEffect;
import com.skydoves.powermenu.MenuAnimation;
import com.skydoves.powermenu.PowerMenu;
import com.skydoves.powermenu.PowerMenuItem;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import apincer.android.mmate.Constants;
import apincer.android.mmate.R;
import apincer.android.mmate.broadcast.AudioTagEditResultEvent;
import apincer.android.mmate.fs.MusicCoverArtProvider;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTagRepository;
import apincer.android.mmate.utils.MusicPathTagParser;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import co.lujun.androidtagview.ColorFactory;
import co.lujun.androidtagview.TagContainerLayout;
import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;
import coil.transform.RoundedCornersTransformation;

public class MusicInforFragment extends Fragment {
    private static final String TAG = MusicInforFragment.class.getName();
    protected Context context;
    protected TagsActivity tagsActivity;
    AlertDialog progressDialog;
    private TextView previewTitle;
    private TextView previewArtist;
    private TextView previewAlbum;
    private TextView previewPath;
    private ImageView previewCoverart;
    private TextInputEditText txtTitle;
    private TextInputEditText txtArtist;
    private TextInputEditText txtAlbum;
    private TextInputEditText txtAlbumArtist;
    private TextInputEditText txtDisc;
    private TextInputEditText txtTrack;
    private TextInputEditText txtYear;
    private TextInputEditText txtGenre;
    private TextInputEditText txtGrouping;
    private TextInputEditText txtMediaType;
    private TextInputEditText txtPublisher;
    private MaterialRadioButton rdAudiophile;
    private MaterialRadioButton rdRecommended;
    private MaterialRadioButton rdRegular;
    private PowerMenu powerMenu;
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
        previewTitle = v.findViewById(R.id.preview_title);
        previewArtist = v.findViewById(R.id.preview_artist);
        previewAlbum = v.findViewById(R.id.preview_album);
        previewPath = v.findViewById(R.id.editor_pathname);
        previewCoverart = v.findViewById(R.id.preview_coverart);
        // input fields
        txtTitle = v.findViewById(R.id.input_title);
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
        // quality
        rdAudiophile = v.findViewById(R.id.mediaQualityAudioPhile);
        rdRecommended = v.findViewById(R.id.mediaQualityRecommended);
        rdRegular = v.findViewById(R.id.mediaQualityRegular);

        // popup list
        setupListValuePopup(txtArtist, MusicTagRepository.getArtistList(), 3, false);
        setupListValuePopup(txtAlbumArtist, MusicTagRepository.getDefaultAlbumArtistList(getContext()),1, false);
        setupListValuePopup(txtGenre, MusicTagRepository.getDefaultGenreList(getContext()),3, true);
        setupListValuePopup(txtGrouping, MusicTagRepository.getDefaultGroupingList(getContext()),1, true);
        setupListValuePopup(txtPublisher, MusicTagRepository.getDefaultPublisherList(getContext()),3, false);
        setupListValuePopup(txtMediaType, Constants.getSourceList(requireContext()),1, true);

        return v;
    }

    private void setupListValuePopup(TextInputEditText textInput, @NonNull List<String> defaultList, int minChar, boolean autoSelect) {
        textInput.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if(bypassChange) return;
                String txt = toLowwerCase(charSequence.toString());
                if(powerMenu!=null) {
                    powerMenu.dismiss();
                }
                if(txt.length()>=minChar) {
                    // popup list
                    List<PowerMenuItem> items = new ArrayList<>();
                    defaultList.stream()
                            .filter(rValue -> toLowwerCase(rValue).contains(txt))
                            .limit(10)
                            .forEach(s -> items.add(new PowerMenuItem(s)));
                   // if(items.size()>0 && !(items.size()==1 && txt.equalsIgnoreCase(String.valueOf(items.get(0).getTitle())))) {
                    if((!autoSelect) || items.size()>=1) {
                        powerMenu = new PowerMenu.Builder(context)
                                .setWidth(textInput.getWidth()+24)
                                .setLifecycleOwner(getViewLifecycleOwner())
                                .addItemList(items) // list has "Novel", "Poetry", "Art"
                                .setAnimation(MenuAnimation.SHOW_UP_CENTER) // Animation start point (TOP | LEFT).
                                .setMenuRadius(8f) // sets the corner radius.
                                .setMenuShadow(8f) // sets the shadow.
                                .setTextColor(ContextCompat.getColor(context, R.color.grey800))
                                .setTextGravity(Gravity.START)
                                .setTextSize(12)
                                .setCircularEffect(CircularEffect.INNER) // Shows circular revealed effects for the content view of the popup menu.
                                .setSelectedTextColor(Color.WHITE)
                                .setMenuColor(ContextCompat.getColor(getContext(), R.color.material_color_blue_grey_100))
                                .setSelectedMenuColor(ContextCompat.getColor(context, R.color.colorPrimary))
                                .setAutoDismiss(true)
                                .setOnMenuItemClickListener((position, item) -> {
                                    bypassChange = true;
                                    textInput.setText(item.title);
                                    powerMenu = null;
                                    bypassChange = false;
                                })
                                .build();
                        powerMenu.setShowBackground(false); // do not showing background.
                        //powerMenu.setFocusable(true); // makes focusing only on the menu popup.
                        int height = powerMenu.getContentViewHeight();
                        powerMenu.showAsDropDown(textInput,0, (-1)*height*(items.size()+1)); // view is an anchor
                    }else if(autoSelect && items.size()==1) {
                        bypassChange = true;
                        textInput.setText(items.get(0).title);
                        bypassChange = false;
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MusicTag musicTag = tagsActivity.buildDisplayTag();
        doPreviewMusicInfo(musicTag);
        initEditorInputs(musicTag);
    }

    public Toolbar.OnMenuItemClickListener getOnMenuItemClickListener() {
        return item -> {
            if (item.getItemId() == R.id.menu_editor_read_tag) {
                doShowReadTagsPreview();
            } else if (item.getItemId() == R.id.menu_editor_format_tag) {
                doFormatTags();
            }
            if (item.getItemId() == R.id.menu_editor_save) {
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
        TextView uscLabel = cview.findViewById(R.id.btn_add_uscore);
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
        uscLabel.setOnClickListener(v -> mTagListLayout.addTag("_"));
        dotLabel.setOnClickListener(v -> mTagListLayout.addTag("."));
        spaceLabel.setOnClickListener(v -> mTagListLayout.addTag("sp"));
        freeTextLabel.setOnClickListener(v -> mTagListLayout.addTag("?"));

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
                MusicTag mdata = item.clone();
                parser.parse(mdata, list);
                title.setText(StringUtils.trimToEmpty(mdata.getTitle()));
                artist.setText(StringUtils.trimToEmpty(mdata.getArtist()));
                album.setText(StringUtils.trimToEmpty(mdata.getAlbum()));
                track.setText(StringUtils.trimToEmpty(mdata.getTrack()));
                //item.setOriginTag(null); // clear pending tag
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
            for(MusicTag item:tagsActivity.getEditItems()) {
                String mediaPath =  item.getPath();
                File file = new File(mediaPath);
                if(!file.exists()) continue;
                parser.parse(item, list);
            }
            bypassChange = true;
            bindViews();
            bypassChange = false;
            alert.dismiss();
        });
        btnCancel.setOnClickListener(v -> alert.dismiss());
        alert.show();
    }

    private void bindViews() {
        MusicTag tag = tagsActivity.buildDisplayTag();
        doPreviewMusicInfo(tag);
        initEditorInputs(tag);
        tagsActivity.updateTitlePanel();
    }

    private void doSaveMediaItem() {
        startProgressBar();
        CompletableFuture.runAsync(
                () -> {
                    for(MusicTag item:tagsActivity.getEditItems()) {
                            buildPendingTags(item);
                    }
                    FileRepository repos = FileRepository.newInstance(tagsActivity.getApplicationContext());
                    for(MusicTag tag: tagsActivity.getEditItems()) {
                        try {
                            boolean status = repos.setMusicTag(tag);
                            AudioTagEditResultEvent message = new AudioTagEditResultEvent(AudioTagEditResultEvent.ACTION_UPDATE, status?Constants.STATUS_SUCCESS:Constants.STATUS_FAIL, tag);
                            EventBus.getDefault().postSticky(message);
                        } catch (Exception e) {
                            Log.e(TAG, "doSaveMediaItem",e);
                        }
                    }
                }
        ).thenAccept(
                unused -> {
                    stopProgressBar();
                    // set updated item on main activity
                    bindViews();
                }
        ).exceptionally(
                throwable -> {
                    stopProgressBar();
                    return null;
                }
        );
    }


    private void buildPendingTags(MusicTag tagUpdate) {
        if(tagUpdate.getOriginTag()==null) {
            // save original tag
            tagUpdate.setOriginTag(tagUpdate.clone());
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
        if (rdAudiophile.isChecked()) {
            return Constants.QUALITY_AUDIOPHILE;
        } else if (rdRecommended.isChecked()) {
            return Constants.QUALITY_RECOMMENDED;
        }
        return "";
    }

    private String buildTag(TextInputEditText txt, String oldVal) {
        String text = StringUtils.trimToEmpty(String.valueOf(txt.getText()));
        if(StringUtils.MULTI_VALUES.equalsIgnoreCase(text) ) {
            return oldVal;
        }
        return text;
    }


    private void doFormatTags() {
        startProgressBar();
        CompletableFuture.runAsync(
                () -> {
                    for(MusicTag tag:tagsActivity.getEditItems()) {
                        MusicTag mItem = tag.clone();
                        tag.setOriginTag(mItem);
                        tag.setTitle(StringUtils.formatTitle(tag.getTitle()));
                        if(!StringUtils.isEmpty(tag.getArtist()) && tag.getArtist().contains(",")) {
                            // split reformat to ;
                            tag.setArtist(tag.getArtist().replaceAll(",",";"));
                        }
                        tag.setArtist(StringUtils.formatTitle(tag.getArtist()));
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
                            tag.setAlbum(MusicTagUtils.getDefaultAlbum(tag));
                        }
                    }
                }
        ).thenAccept(
                unused -> {
                    stopProgressBar();
                    // set updated item on main activity
                    bypassChange = true;
                    bindViews();
                    bypassChange = false;
                }
        ).exceptionally(
                throwable -> {
                    stopProgressBar();
                    return null;
                }
        );
    }

    private void initEditorInputs(MusicTag tag) {
        txtTitle.setText(tag.getTitle());
        txtArtist.setText(tag.getArtist());
        txtAlbum.setText(tag.getAlbum());
        txtAlbumArtist.setText(tag.getAlbumArtist());
        txtDisc.setText(tag.getDisc());
        txtTrack.setText(tag.getTrack());
        txtYear.setText(tag.getYear());
        txtGenre.setText(tag.getGenre());
        txtGrouping.setText(tag.getGrouping());
        txtMediaType.setText(tag.getMediaType());
        txtPublisher.setText(tag.getPublisher());

        // quality
        rdAudiophile.setChecked(Constants.QUALITY_AUDIOPHILE.equalsIgnoreCase(tag.getMediaQuality()));
        rdRecommended.setChecked(Constants.QUALITY_RECOMMENDED.equalsIgnoreCase(tag.getMediaQuality()));
        rdRegular.setChecked(isEmpty(tag.getMediaQuality()));
    }

    private void doPreviewMusicInfo(MusicTag tag) {
        ImageLoader imageLoader = Coil.imageLoader(tagsActivity.getApplicationContext());
        ImageRequest request = new ImageRequest.Builder(tagsActivity.getApplicationContext())
                //.data(MusicTagUtils.getCoverArt(tagsActivity.getApplicationContext(), tag))
                .data(MusicCoverArtProvider.getUriForMusicTag(tag))
                .transformations(new RoundedCornersTransformation())
                .crossfade(false)
                .target(previewCoverart)
                .build();
        imageLoader.enqueue(request);

        previewTitle.setText(MusicTagUtils.getFormattedTitle(getContext(),tag));
        previewArtist.setText(tag.getArtist());
        previewAlbum.setText(tag.getAlbum());
        previewPath.setText(tag.getSimpleName());

    }

    private void startProgressBar() {
        tagsActivity.runOnUiThread(() -> {
            try {
                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(tagsActivity, R.style.AlertDialogTheme);
                dialogBuilder.setView(R.layout.progress_dialog_layout);
                dialogBuilder.setCancelable(false);
                progressDialog = dialogBuilder.create();
                progressDialog.show();
            }catch (Exception ex) {
                Log.e(TAG, "startProgressBar",ex);
            }
        });
    }

    private void stopProgressBar() {
        tagsActivity.runOnUiThread(() -> {
            if(progressDialog!=null) {
                progressDialog.dismiss();
                progressDialog = null;
            }
        });
    }
}

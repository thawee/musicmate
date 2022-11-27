package apincer.android.mmate.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.ViewPropertyAnimatorListenerAdapter;
import androidx.fragment.app.Fragment;
import androidx.palette.graphics.Palette;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.skydoves.powerspinner.IconSpinnerAdapter;
import com.skydoves.powerspinner.IconSpinnerItem;
import com.skydoves.powerspinner.PowerSpinnerView;

import org.apache.commons.io.IOUtils;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import apincer.android.mmate.Constants;
import apincer.android.mmate.R;
import apincer.android.mmate.broadcast.AudioTagEditResultEvent;
import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTagRepository;
import apincer.android.mmate.utils.BitmapHelper;
import apincer.android.mmate.utils.ColorUtils;
import apincer.android.mmate.utils.MusicPathTagParser;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mmate.utils.UIUtils;
import co.lujun.androidtagview.ColorFactory;
import co.lujun.androidtagview.TagContainerLayout;
import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;
import coil.target.Target;
import me.zhanghai.android.materialratingbar.MaterialRatingBar;
import timber.log.Timber;

public class TagsEditorFragment extends Fragment {
    private TagsActivity mainActivity;
    private List<MusicTag> mediaItems;
    private ViewHolder viewHolder;
    private FloatingActionButton fabSaveAction;
    private FloatingActionButton fabMainAction;
    private PopupMenu popupMenu;
    //private String coverArtPath;
    //private ProgressBar progressBar;
    AlertDialog progressDialog;

    final int DRAWABLE_LEFT = 0;
    final int DRAWABLE_TOP = 1;
    final int DRAWABLE_RIGHT = 2;
    final int DRAWABLE_BOTTOM = 3;

    private final ActivityResultLauncher<PickVisualMediaRequest> launcher = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), this::doSelectedCoverArt
    );

    public TagsEditorFragment() {

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root= inflater.inflate(R.layout.fragment_editor, container, false);
        viewHolder = new ViewHolder(root);
        setupFab(root);
        setupFabMenus(root);
        toggleSaveFabAction();

        return root;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(popupMenu !=null) {
            popupMenu.dismiss();
        }
        if(progressDialog!=null) {
            progressDialog.dismiss();
        }
    }

    private void setupFabMenus(View root) {
        fabMainAction = root.findViewById(R.id.fabMain);
        fabMainAction.setOnClickListener(view1 -> doShowPopupMenu());
        popupMenu = new PopupMenu(requireContext(), fabMainAction);
        popupMenu.getMenuInflater().inflate(R.menu.menu_editor_tools, popupMenu.getMenu());
            UIUtils.makePopForceShowIcon(popupMenu);
        popupMenu.setOnMenuItemClickListener(item -> {
                if(item.getItemId() == R.id.menu_editor_tool_read_tag) {
                    doShowReadTagsPreview();
                }else if(item.getItemId() == R.id.menu_editor_tool_format_tag) {
                    doFormatTags();
                }if(item.getItemId() == R.id.menu_editor_tool_pick_coverart) {
                    doPickCoverart();
               // }if(item.getItemId() == R.id.menu_editor_refresh) {
               //     doReloadTagFromFile();
                }
                return false;
            });
    }

    private void doShowPopupMenu() {
        PopupMenu popup = new PopupMenu(requireContext(), fabMainAction);
        popup.getMenuInflater().inflate(R.menu.menu_editor_tools, popup.getMenu());
        UIUtils.makePopForceShowIcon(popup);
        popup.setOnMenuItemClickListener(item -> {
            if(item.getItemId() == R.id.menu_editor_tool_read_tag) {
                doShowReadTagsPreview();
            }else if(item.getItemId() == R.id.menu_editor_tool_format_tag) {
                doFormatTags();
            }if(item.getItemId() == R.id.menu_editor_tool_pick_coverart) {
                doPickCoverart();
            }
             return false;
        });
        popup.show();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mainActivity = (TagsActivity) getActivity();
        mediaItems = Objects.requireNonNull(mainActivity).getEditItems();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewHolder.bindViewHolder(mainActivity.buildDisplayTag());
        viewHolder.resetState();
        toggleSaveFabAction();
    }

    private void doPickCoverart() {
        ActivityResultContracts.PickVisualMedia.VisualMediaType mediaType = (ActivityResultContracts.PickVisualMedia.VisualMediaType) ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE;
        PickVisualMediaRequest request = new PickVisualMediaRequest.Builder()
                .setMediaType(mediaType)
                .build();
        launcher.launch(request);
    }

    private void doSelectedCoverArt(Uri uri) {
        try {
            // get content and save to local file
            String type = getApplicationContext().getContentResolver().getType(uri);
            type = type.replace('/', '.');
            type = type.replace("*", ".png");
            InputStream ins = getApplicationContext().getContentResolver().openInputStream(uri);
            File dir = getApplicationContext().getExternalCacheDir();
            File path = new File(dir, "tmp."+type);
            IOUtils.copy(ins, new FileOutputStream(path));

            // preview
            //this.coverArtPath = path.getAbsolutePath();
            viewHolder.coverartChanged = true;
           // doPreviewCoverArt(coverArtPath);
            toggleSaveFabAction();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startProgressBar() {
        getActivity().runOnUiThread(() -> {
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme);
            dialogBuilder.setView(R.layout.progress_dialog_layout);
            dialogBuilder.setCancelable(false);
            progressDialog = dialogBuilder.create();
            progressDialog.show();
        });
    }

    private void stopProgressBar() {
        getActivity().runOnUiThread(() -> {
            if(progressDialog!=null) {
                progressDialog.dismiss();
                progressDialog = null;
            }
        });
    }

    private void setupFab(View view) {
        fabSaveAction = view.findViewById(R.id.fab_save_media);
        // save tag action
        fabSaveAction.setOnClickListener(view1 -> doSaveMediaItem());
        //fabSaveAction.showLoadingAnimation(false);
    }

    private void doSaveMediaItem() {
        if(!(viewHolder.tagChanged || viewHolder.coverartChanged)) {
            return;
        }

        fabSaveAction.setVisibility(View.GONE);
        //fabSaveAction.setEnabled(false); // not accept click

        startProgressBar();
        CompletableFuture.runAsync(
                new Runnable() {
                    @Override
                    public void run() {
                        if(viewHolder.tagChanged) {
                            for(MusicTag item:mediaItems) {
                                buildPendingTags(item);
                            }
                        }
                        FileRepository repos = FileRepository.newInstance(getApplicationContext());
                        for(MusicTag tag: mediaItems) {
                            try {
                                boolean status = repos.setMusicTag(tag);
                                AudioTagEditResultEvent message = new AudioTagEditResultEvent(AudioTagEditResultEvent.ACTION_UPDATE, status?Constants.STATUS_SUCCESS:Constants.STATUS_FAIL, tag);
                                EventBus.getDefault().postSticky(message);
                            } catch (Exception e) {
                                Timber.e(e);
                            }
                        }
                    }
                }
        ).thenAccept(
                unused -> {
                    fabSaveAction.setEnabled(false);

                    stopProgressBar();
                    // set updated item on main activity
                    viewHolder.ignoreTagChanged = true;
                    viewHolder.bindViewHolder(mainActivity.buildDisplayTag());
                    //viewHolder.ignoreTagChanged = false;
                    //viewHolder.resetState();
                    //toggleSaveFabAction();
                    //mainActivity.buildDisplayTag();
                    mainActivity.updateTitlePanel();
                }
        ).exceptionally(
                throwable -> {
                    stopProgressBar();
                    return null;
                }
        );

      //  UIUtils.hideKeyboard(requireActivity());
    }

    private void buildPendingTags(MusicTag tagUpdate) {
        // update new tag with data from UI
        if(!viewHolder.tagChanged) {
            return;
        }

        if(tagUpdate.getOriginTag()==null) {
            // save original tag
            tagUpdate.setOriginTag(tagUpdate.clone());
        }
        MusicTag originTag = tagUpdate.getOriginTag();

        tagUpdate.setTitle(buildTag(viewHolder.mTitleView, originTag.getTitle()));
        tagUpdate.setTrack(buildTag(viewHolder.mTrackView, originTag.getTrack()));
        tagUpdate.setAlbum(buildTag(viewHolder.mAlbumView, originTag.getAlbum()));
        tagUpdate.setAlbumArtist(buildTag(viewHolder.mAlbumArtistView, originTag.getAlbumArtist()));
        tagUpdate.setArtist(buildTag(viewHolder.mArtistView, originTag.getArtist()));
        tagUpdate.setComposer(buildTag(viewHolder.mComposerView, originTag.getComposer()));
        tagUpdate.setComment(buildTag(viewHolder.mCommentView, originTag.getComment()));
        tagUpdate.setGrouping(buildTag(viewHolder.mGroupingView, originTag.getGrouping()));
        tagUpdate.setGenre(buildTag(viewHolder.mGenreView, originTag.getGenre()));
        tagUpdate.setPublisher(buildTag(viewHolder.mPublisherView, originTag.getPublisher()));
        tagUpdate.setLanguage(buildTag(viewHolder.mLanguageView, originTag.getLanguage()));
        tagUpdate.setYear(buildTag(viewHolder.mYearView, originTag.getYear()));
        tagUpdate.setDisc(buildTag(viewHolder.mDiscView, originTag.getDisc()));
        tagUpdate.setMediaType(buildTag(viewHolder.mMediaSourceView, originTag.getMediaType()));
        tagUpdate.setMediaQuality(buildTag(viewHolder.mMediaSourceQualityView, originTag.getMediaQuality()));
        tagUpdate.setRating(buildTag(viewHolder.mRatingBar));
    }

    private int buildTag(MaterialRatingBar mRatingBar) {
       return (int) mRatingBar.getRating();
    }

    private String buildTag(TextView textView, String oldVal) {
        String text = getText(textView);
        if(StringUtils.MULTI_VALUES.equalsIgnoreCase(text) ) {
            return oldVal;
        }
        return text;
    }

    private String getText(TextView textView) {
        return StringUtils.trimToEmpty(String.valueOf(textView.getText()));
    }

    private void doPreviewCoverArt(String coverArtPath) {
        ImageLoader imageLoader = Coil.imageLoader(getApplicationContext());
        ImageRequest request = new ImageRequest.Builder(getApplicationContext())
                .data(new File(coverArtPath))
                .allowHardware(false)
                .placeholder(R.drawable.progress)
                .error(R.drawable.ic_broken_image_black_24dp)
                .target(viewHolder.mCoverArt)
                .build();
        imageLoader.enqueue(request);
    }

    private class ViewHolder {
        private MusicTag displayTag;
        private final EditText mTitleView;
        private final AutoCompleteTextView mArtistView;
        private final EditText mAlbumView;
        private final AutoCompleteTextView mAlbumArtistView;
        private final AutoCompleteTextView mGenreView;
        private final EditText mYearView;
        private final EditText mComposerView;
        private final AutoCompleteTextView mGroupingView;
        private final AutoCompleteTextView mPublisherView;
        private final AutoCompleteTextView mLanguageView;
        private final EditText mCommentView;
        private final EditText mTrackView;
        private final EditText mDiscView;
        private final View mEditorCardView;
        //private final TriStateToggleButton mAudiophileButton;
        private final MaterialRatingBar mRatingBar;
        private final PowerSpinnerView mMediaSourceView;
        private final List<IconSpinnerItem> mMediaSourceItems;
        private final PowerSpinnerView mMediaSourceQualityView;
        private final List<IconSpinnerItem> mMediaSourceQualityItems;
        private final View mEditorPanel;
        private final TextView mPathNameView;
        private final TextView mFileNameView;
        private final ImageView mCoverArt;

        private final ViewTextWatcher mTextWatcher;
        protected volatile boolean tagChanged;
        protected volatile boolean ignoreTagChanged = false;
        protected volatile boolean coverartChanged;

        @SuppressLint("ClickableViewAccessibility")
        public ViewHolder(View view) {
            tagChanged = false;
            coverartChanged = false;
            mTextWatcher =new ViewTextWatcher();

            mEditorCardView = view.findViewById(R.id.editorCardView);
            mEditorPanel = view.findViewById(R.id.editorPanel);
            mCoverArt = view.findViewById(R.id.editor_cover_art);

            // music mate info
            mRatingBar = view.findViewById(R.id.media_rating);
            mRatingBar.setOnRatingChangeListener((ratingBar, rating) -> {
                setTagChanged();
            });

            /*
            mAudiophileButton = view.findViewById(R.id.media_audiophile);
            mAudiophileButton.setOnToggleChanged((toggleStatus, booleanToggleStatus, toggleIntValue) -> {
                tagChanged = true;
                toggleSaveFabAction();
            });*/
            mMediaSourceQualityView = view.findViewById(R.id.media_source_quality);
            IconSpinnerAdapter qualityAdapter = new IconSpinnerAdapter(mMediaSourceQualityView);
            mMediaSourceQualityItems = new ArrayList<>();
            mMediaSourceQualityItems.add(new IconSpinnerItem("",null));
            mMediaSourceQualityItems.add(new IconSpinnerItem(Constants.QUALITY_AUDIOPHILE, null));
            mMediaSourceQualityItems.add(new IconSpinnerItem(Constants.QUALITY_RECOMMENDED, null));
            mMediaSourceQualityItems.add(new IconSpinnerItem(Constants.QUALITY_POOR, null));

            qualityAdapter.setItems(mMediaSourceQualityItems);
            mMediaSourceQualityView.setSpinnerAdapter(qualityAdapter);
            mMediaSourceQualityView.setOnSpinnerItemSelectedListener((i, o, i1, t1) -> {
                setTagChanged();
            });

            mMediaSourceView = view.findViewById(R.id.media_source);
            IconSpinnerAdapter adapter = new IconSpinnerAdapter(mMediaSourceView);
            mMediaSourceItems = new ArrayList<>();
            List<String> srcs = Constants.getSourceList(requireContext());
            mMediaSourceItems.add(new IconSpinnerItem("",null));
            for(String src : srcs) {
                Bitmap ico = MusicTagUtils.getMediaTypeIcon(getContext(),src);
                mMediaSourceItems.add(new IconSpinnerItem(src,BitmapHelper.bitmapToDrawable(requireContext(), ico)));
            }

            adapter.setItems(mMediaSourceItems);
            mMediaSourceView.setSpinnerAdapter(adapter);
            mMediaSourceView.setOnSpinnerItemSelectedListener((i, o, i1, t1) -> {
                setTagChanged();
            });

            mPathNameView = view.findViewById(R.id.editor_pathname);
            mFileNameView = view.findViewById(R.id.editor_filename);

            // title
            mTitleView = setupTextEdit(view, R.id.tag_title);

            // artist
            mArtistView = setupAutoCompleteTextView(view, R.id.tag_artist);
            List<String> list = MusicTagRepository.getArtistList();
            ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(getApplicationContext(), R.layout.dialog_item_list, list);
            //Used to specify minimum number of
            //characters the user has to type in order to display the drop down hint.
            mArtistView.setThreshold(2);
            mArtistView.setAdapter(arrayAdapter);
            mArtistView.setOnTouchListener((v, event) -> {
                if(event.getAction() == MotionEvent.ACTION_UP) {
                    if(event.getRawX() >= (mArtistView.getRight() - mArtistView.getCompoundDrawables()[DRAWABLE_RIGHT].getBounds().width())) {
                        // your action here
                        mArtistView.showDropDown();
                        return true;
                    }
                }
                return false;
            });

            // album
            mAlbumView = setupTextEdit(view, R.id.tag_album);

            // album artist
            mAlbumArtistView = setupAutoCompleteTextView(view, R.id.tag_album_artist);
            list = MusicTagRepository.getDefaultAlbumArtistList(requireContext());
            arrayAdapter = new ArrayAdapter<>(getApplicationContext(), R.layout.dialog_item_list, list);
            //Used to specify minimum number of
            //characters the user has to type in order to display the drop down hint.
            mAlbumArtistView.setThreshold(2);
            mAlbumArtistView.setAdapter(arrayAdapter);
            mAlbumArtistView.setOnTouchListener((v, event) -> {

                if(event.getAction() == MotionEvent.ACTION_UP) {
                    if(event.getRawX() >= (mAlbumArtistView.getRight() - mAlbumArtistView.getCompoundDrawables()[DRAWABLE_RIGHT].getBounds().width())) {
                        // your action here
                        mAlbumArtistView.showDropDown();
                        return true;
                    }
                }
                return false;
            });
            // year
            mYearView = setupTextEdit(view, R.id.tag_year);

            // disc no
            mDiscView = setupTextEdit(view, R.id.tag_disc);

            // track
            mTrackView = setupTextEdit(view, R.id.tag_track);

            // genre
            mGenreView = setupAutoCompleteTextView(view,R.id.tag_genre);
            list = MusicTagRepository.getDefaultGenreList(getContext());
            arrayAdapter = new ArrayAdapter<>(getApplicationContext(), R.layout.dialog_item_list, list);
            //Used to specify minimum number of
            //characters the user has to type in order to display the drop down hint.
           // mGenreView.setThreshold(1);

            mGenreView.setAdapter(arrayAdapter);
            mGenreView.setOnTouchListener((v, event) -> {
                if(event.getAction() == MotionEvent.ACTION_UP) {
                    if(event.getRawX() >= (mGenreView.getRight() - mGenreView.getCompoundDrawables()[DRAWABLE_RIGHT].getBounds().width())) {
                        // your action here
                        mGenreView.showDropDown();
                        //return true;
                    }
                }
                return false;
            });

            // grouping
            mGroupingView = setupAutoCompleteTextView(view, R.id.tag_group);
            list = MusicTagRepository.getDefaultGroupingList(requireContext());
            arrayAdapter = new ArrayAdapter<>(getApplicationContext(), R.layout.dialog_item_list, list);
            //Used to specify minimum number of
            //characters the user has to type in order to display the drop down hint.
            mGroupingView.setThreshold(1);
            mGroupingView.setAdapter(arrayAdapter);
            mGroupingView.setOnTouchListener((v, event) -> {
                if(event.getAction() == MotionEvent.ACTION_UP) {
                    if(event.getRawX() >= (mGroupingView.getRight() - mGroupingView.getCompoundDrawables()[DRAWABLE_RIGHT].getBounds().width())) {
                        // your action here
                        mGroupingView.showDropDown();
                        return true;
                    }
                }
                return false;
            });

            // publisher
            mPublisherView = setupAutoCompleteTextView(view, R.id.tag_publisher);
            list = MusicTagRepository.getDefaultPublisherList(requireContext());
            arrayAdapter = new ArrayAdapter<>(getApplicationContext(), R.layout.dialog_item_list, list);
            //Used to specify minimum number of
            //characters the user has to type in order to display the drop down hint.
            mPublisherView.setThreshold(1);
            mPublisherView.setAdapter(arrayAdapter);
            mPublisherView.setOnTouchListener((v, event) -> {
                if(event.getAction() == MotionEvent.ACTION_UP) {
                    if(event.getRawX() >= (mPublisherView.getRight() - mPublisherView.getCompoundDrawables()[DRAWABLE_RIGHT].getBounds().width())) {
                        // your action here
                        mPublisherView.showDropDown();
                        return true;
                    }
                }
                return false;
            });

            //Languge
            mLanguageView = setupAutoCompleteTextView(view, R.id.tag_language);
            list = MusicTagRepository.getDefaultLanguageList(requireContext());
            arrayAdapter = new ArrayAdapter<>(getApplicationContext(), R.layout.dialog_item_list, list);
            //Used to specify minimum number of
            //characters the user has to type in order to display the drop down hint.
            mLanguageView.setThreshold(1);
            mLanguageView.setAdapter(arrayAdapter);
            mLanguageView.setOnTouchListener((v, event) -> {
                if(event.getAction() == MotionEvent.ACTION_UP) {
                    if(event.getRawX() >= (mLanguageView.getRight() - mLanguageView.getCompoundDrawables()[DRAWABLE_RIGHT].getBounds().width())) {
                        // your action here
                        mLanguageView.showDropDown();
                        return true;
                    }
                }
                return false;
            });


            //composer
            mComposerView = setupTextEdit(view, R.id.tag_composer);

            // comment
            mCommentView = setupTextEdit(view, R.id.tag_comment);
        }

        private void setTagChanged() {
            if(ignoreTagChanged) return;
            if(!tagChanged) {
                tagChanged = true;
                toggleSaveFabAction();
            }
        }

        private EditText setupTextEdit(View view, int viewId) {
            EditText  textInput = view.findViewById(viewId);
            textInput.addTextChangedListener(mTextWatcher);
            return textInput;
        }

        /*
        private PowerSpinnerView setupPowerSpinner(View view, int viewId) {
            PowerSpinnerView  textInput = view.findViewById(viewId);
            textInput.addTextChangedListener(mTextWatcher);
            return textInput;
        } */

        private AutoCompleteTextView setupAutoCompleteTextView(View view, int viewId) {
            AutoCompleteTextView  textInput = view.findViewById(viewId);
            textInput.addTextChangedListener(mTextWatcher);
            return textInput;
        }

        void resetState() {
            tagChanged = false;
            coverartChanged = false;
            mEditorCardView.clearFocus();
            toggleSaveFabAction();
        }

        public void bindViewHolder(MusicTag mediaTag) {
            // music mate info
            this.displayTag = mediaTag;
            if(displayTag ==null) return;

            int sIndex =0;
            for(int indx=0; indx < mMediaSourceQualityItems.size();indx++) {
                IconSpinnerItem item = mMediaSourceQualityItems.get(indx);
                if(item.getText().equals(mediaTag.getMediaQuality())) {
                    sIndex = indx;
                    break;
                }
            }
            mMediaSourceQualityView.selectItemByIndex(sIndex);

            mRatingBar.setRating(mediaTag.getRating());
            File file = new File(mediaTag.getPath());
            mPathNameView.setText(Objects.requireNonNull(file.getParentFile()).getAbsolutePath());
            mFileNameView.setText(file.getName());

            ImageLoader imageLoader = Coil.imageLoader(getApplicationContext());
            ImageRequest request = new ImageRequest.Builder(getApplicationContext())
                    .data(MusicTagUtils.getCoverArt(requireContext(), displayTag))
                .allowHardware(false)
                .placeholder(R.drawable.progress)
                .error(R.drawable.ic_broken_image_black_24dp)
                .target(new Target() {
                    @Override
                    public void onStart(@Nullable Drawable drawable) {

                    }

                    @Override
                    public void onError(@Nullable Drawable drawable) {
                    }

                    @Override
                    public void onSuccess(@NonNull Drawable drawable) {
                        try {
                            mCoverArt.setImageDrawable(drawable);
                            Bitmap bmp = BitmapHelper.drawableToBitmap(drawable);
                            Palette palette = Palette.from(bmp).generate();
                            int frameColor = palette.getMutedColor(ContextCompat.getColor(requireContext(),R.color.bgColor));
                            mEditorPanel.setBackgroundColor(ColorUtils.TranslateDark(frameColor, 80));
                        }catch (Exception ex) {
                            Timber.e(ex);
                        }
                    }
                })
                .build();
            imageLoader.enqueue(request);

            int selectedIndex =0;
            for(int indx=0; indx < mMediaSourceItems.size();indx++) {
                IconSpinnerItem item = mMediaSourceItems.get(indx);
                if(item.getText().equals(mediaTag.getMediaType())) {
                    selectedIndex = indx;
                    break;
                }
            }
            mMediaSourceView.selectItemByIndex(selectedIndex);

            // title
            mTitleView.setText(StringUtils.trimToEmpty(displayTag.getTitle()));
            // artist
            mArtistView.setText(StringUtils.trimToEmpty(displayTag.getArtist()));

            //genre
            mGenreView.setText(StringUtils.trimToEmpty(displayTag.getGenre()));

            // album
            mAlbumView.setText(StringUtils.trimToEmpty(displayTag.getAlbum()));
            // album artist
            mAlbumArtistView.setText(StringUtils.trimToEmpty(displayTag.getAlbumArtist()));

            mGroupingView.setText(StringUtils.trimToEmpty(displayTag.getGrouping()));
            // album artist
            mCommentView.setText(StringUtils.trimToEmpty(displayTag.getComment()));

            // album artist
            mTrackView.setText(StringUtils.trimToEmpty(displayTag.getTrack()));
            // composer
            mComposerView.setText(StringUtils.trimToEmpty(displayTag.getComposer()));

            // publisher
            mPublisherView.setText(StringUtils.trimToEmpty(displayTag.getPublisher()));

            // language
            mLanguageView.setText(StringUtils.trimToEmpty(displayTag.getLanguage()));

            //Disc
            mDiscView.setText(StringUtils.trimToEmpty(displayTag.getDisc()));
            // Year
            mYearView.setText(StringUtils.trimToEmpty(displayTag.getYear()));

            //reset silent flag
            ignoreTagChanged = false;
        }

        public  class ViewTextWatcher implements TextWatcher {
            public ViewTextWatcher() {
            }
            public void afterTextChanged(Editable editable) {
                setTagChanged();
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        }
    }

    private void toggleSaveFabAction(){
        if(viewHolder.tagChanged || viewHolder.coverartChanged) {
            ViewCompat.animate(fabSaveAction)
                    .scaleX(1f).scaleY(1f)
                    .alpha(1f).setDuration(100)
                    .setStartDelay(300L)
                    .setListener(new ViewPropertyAnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(@NonNull View view) {
                            view.setVisibility(View.VISIBLE);
                            view.setEnabled(true);
                        }
                    })
                    .start();
        }else {
            ViewCompat.animate(fabSaveAction)
                    .scaleX(0f).scaleY(0f)
                    .alpha(0f).setDuration(50)
                    .setListener(new ViewPropertyAnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(@NonNull View view) {
                            view.setVisibility(View.GONE);
                        }
                    })
                    .start();
        }
    }

    private void doFormatTags() {
        startProgressBar();
        CompletableFuture.runAsync(
                () -> {
                    for(MusicTag tag:mediaItems) {
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
                    viewHolder.bindViewHolder(mainActivity.buildDisplayTag());
                    toggleSaveFabAction();
                }
        ).exceptionally(
                throwable -> {
                    stopProgressBar();
                    return null;
                }
        );
    }
/*
    private void doChangeCharset(String charset) {
       // hideFabMenus();
        startProgressBar();
        if(StringUtils.isEmpty(charset)) {
            // re-load from media file
            for (MediaItem item : mediaItems) {
                MediaItemRepository.getInstance(getActivity().getApplication()).readMetadata(item.getMetadata());
            }
        }else {
            for(MediaItem item: mediaItems) {
                MediaMetadata tag = item.getPendingMetadataOrCreate();
                tag.setTitle(StringUtils.encodeText(tag.getTitle(),charset));
                tag.setArtist(StringUtils.encodeText(tag.getArtist(),charset));
                tag.setAlbum(StringUtils.encodeText(tag.getAlbum(),charset));
                tag.setAlbumArtist(StringUtils.encodeText(tag.getAlbumArtist(),charset));
                tag.setGenre(StringUtils.encodeText(tag.getGenre(),charset));
                tag.setComment(StringUtils.encodeText(tag.getComment(),charset));
                tag.setComposer(StringUtils.encodeText(tag.getComment(),charset));
                tag.setGrouping(StringUtils.encodeText(tag.getGrouping(),charset));
            }
        }
        viewHolder.bindViewHolder(buildDisplayTag());
        stopProgressBar();
    } */

    private void doShowReadTagsPreview() {
       // hideFabMenus();
        View cview = getLayoutInflater().inflate(R.layout.view_actionview_tags_from_filename, null);

        TextView filename = cview.findViewById(R.id.full_filename);
        filename.setText(mediaItems.get(0).getSimpleName());

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

        //  title.set(false);
        title.setText(mediaItems.get(0).getTitle());
      //  artist.setEnabled(false);
        artist.setText(mediaItems.get(0).getArtist());
      //  album.setEnabled(false);
        album.setText(mediaItems.get(0).getAlbum());
      //  track.setEnabled(false);
        track.setText(mediaItems.get(0).getTrack());

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
        freeTextLabel.setOnClickListener(v -> mTagListLayout.addTag("*"));

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
                MusicTag item = mediaItems.get(0);
                MusicTag mdata = item.clone();
                parser.parse(mdata, list);
                title.setText(StringUtils.trimToEmpty(mdata.getTitle()));
                artist.setText(StringUtils.trimToEmpty(mdata.getArtist()));
                album.setText(StringUtils.trimToEmpty(mdata.getAlbum()));
                track.setText(StringUtils.trimToEmpty(mdata.getTrack()));
                //item.setOriginTag(null); // clear pending tag
            }catch (Exception ex) {
                Timber.d(ex);
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
            for(MusicTag item:mediaItems) {
                String mediaPath =  item.getPath();
                File file = new File(mediaPath);
                if(!file.exists()) continue;
                parser.parse(item, list);
            }
            viewHolder.bindViewHolder(mainActivity.buildDisplayTag());
            alert.dismiss();
        });
        btnCancel.setOnClickListener(v -> alert.dismiss());
        alert.show();
    }

/*
    private void doShowCharsetOptions() {
        hideFabMenus();
        final Map<Integer,String> charsetList = new HashMap<>();
        MediaMetadata matadata = mediaItems.get(0).getMetadata();
        String title = matadata.getTitle()+ StringUtils.ARTIST_SEP+ MusicListeningService.getSubtitle(matadata.getAlbum(),matadata.getArtist());

        ArrayList<String> other = new ArrayList<>();
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        Set<String> encodings = prefs.getStringSet("preference_matadata_encodings",null);
        if(encodings==null || encodings.isEmpty() ) {
            return;
        }

        int indx=0;
        for(String charset:encodings) {
            other.add(StringUtils.encodeText(title, charset));
            charsetList.put(indx++, charset);
        }

        CustomAlertDialogue.Builder alert = new CustomAlertDialogue.Builder(getActivity())
                .setStyle(CustomAlertDialogue.Style.ACTIONSHEET)
                .setTitle("Please select readable metadata...")
                .setTitleColor(R.color.text_default)
                .setCancelText(getString(R.string.alert_btn_cancel))
                .setOnCancelClicked((view, dialog) -> dialog.dismiss())
                .setOthers(other)
                .setOnItemClickListener((adapterView, view, i, l) -> {
                    CustomAlertDialogue.getInstance().dismiss();
                    doChangeCharset(charsetList.get(i));
                })
                .setDecorView(getActivity().getWindow().getDecorView())
                .build();
        alert.show();
    } */

    private Context getApplicationContext() {
        return requireActivity().getApplicationContext();
    }
}
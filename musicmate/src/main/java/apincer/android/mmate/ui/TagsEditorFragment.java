package apincer.android.mmate.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
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
import com.qw.photo.CoCo;
import com.qw.photo.callback.CoCoAdapter;
import com.qw.photo.pojo.PickResult;
import com.skydoves.powerspinner.IconSpinnerAdapter;
import com.skydoves.powerspinner.IconSpinnerItem;
import com.skydoves.powerspinner.PowerSpinnerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import apincer.android.mmate.Constants;
import apincer.android.mmate.R;
import apincer.android.mmate.fs.EmbedCoverArtProvider;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.repository.AudioTagRepository;
import apincer.android.mmate.ui.widget.TriStateToggleButton;
import apincer.android.mmate.utils.AudioTagUtils;
import apincer.android.mmate.utils.BitmapHelper;
import apincer.android.mmate.utils.ColorUtils;
import apincer.android.mmate.utils.MediaTagParser;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mmate.utils.UIUtils;
import apincer.android.mmate.work.UpdateAudioFileWorker;
import co.lujun.androidtagview.ColorFactory;
import co.lujun.androidtagview.TagContainerLayout;
import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;
import coil.target.Target;
import me.zhanghai.android.materialratingbar.MaterialRatingBar;
import timber.log.Timber;

//import dev.ronnie.github.imagepicker.ImagePicker;
//import dev.ronnie.github.imagepicker.ImageResult;

public class TagsEditorFragment extends Fragment {
    public static final int REQUEST_GET_CONTENT_IMAGE = 555;
    private TagsActivity mainActivity;
    private List<AudioTag> mediaItems;
    private ViewHolder viewHolder;
    private FloatingActionButton fabSaveAction;
    private FloatingActionButton fabMainAction;
   // private ImagePicker imagePicker; // = new ImagePicker(this);
    //private Uri coverArtUri;
    private String coverArtPath;
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    final int DRAWABLE_LEFT = 0;
    final int DRAWABLE_TOP = 1;
    final int DRAWABLE_RIGHT = 2;
    final int DRAWABLE_BOTTOM = 3;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
       // imagePicker = new ImagePicker(this);
       /* imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                Uri uri = result.getData().getData();
                // Use the uri to load the image
                setCoverArtUri(uri);
            }
        }); */
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

    private void setupFabMenus(View root) {
        fabMainAction = root.findViewById(R.id.fabMain);
        fabMainAction.setOnClickListener(view1 -> doShowPopupMenu());
    }

    private void doShowPopupMenu() {
        PopupMenu popup = new PopupMenu(getContext(), fabMainAction);
        popup.getMenuInflater().inflate(R.menu.menu_editor_tools, popup.getMenu());
        UIUtils.makePopForceShowIcon(popup);
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if(item.getItemId() == R.id.menu_editor_tool_read_tag) {
                    doShowReadTagsPreview();
                }else if(item.getItemId() == R.id.menu_editor_tool_format_tag) {
                    doFormatTags();
                }if(item.getItemId() == R.id.menu_editor_tool_pick_coverart) {
                    doPickCoverart();
                }
                 return false;
            }
        });
        popup.show();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mainActivity = (TagsActivity) getActivity();
        mediaItems = mainActivity.getEditItems();
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewHolder.bindViewHolder(mainActivity.buildDisplayTag(true));
        viewHolder.resetState();
        toggleSaveFabAction();
    }

    private void doPickCoverart() {
       // Intent myIntent = new Intent(MediaBrowserActivity.this, TagsActivity.class);
      //   imagePicker = new ImagePickerActivityClass(getContext(), this, requireActivity().getActivityResultRegistry(), null, this);
        //set to true if you want all features(crop,rotate,zoomIn,zoomOut)
        //by Default it's value is set to false (only crop feature is enabled)
        // imagePicker.cropOptions(true);
       // editorLauncher.launch(myIntent);

        //Gallery
      /*  imagePicker.pickFromStorage(new Function1<ImageResult<? extends Uri>, Unit>() {
            @Override
            public Unit invoke(ImageResult<? extends Uri> imageResult) {
                if (imageResult instanceof ImageResult.Success) {
                    Uri uri = ((ImageResult.Success<Uri>) imageResult).getValue();
                    setCoverArtUri(uri);
                }
                return null;
            }
        }); */
        CoCo.with(this)
                    .pick()
               // .then()
               // .crop()
                .start(new CoCoAdapter<PickResult>() {
                    @Override
                    public void onFailed(@NonNull Exception exception) {
                        super.onFailed(exception);
                    }

                    @Override
                    public void onSuccess(PickResult pickResult) {
                        super.onSuccess(pickResult);
                        setCoverArtPath(pickResult.getLocalPath());
                    }
                });

      //  imagePicker.choosePhotoFromGallery();
        String [] mimetypes = {"image/png",
                "image/jpg",
                "image/jpeg"};
      /*  Intent myIntent = ImagePicker.with(getActivity())
                .crop()
                .maxResultSize(1024, 1024, true)	//Final image resolution will be less than 620 x 620
                .galleryOnly()	//User can only select image from Gallery
                .galleryMimeTypes(mimetypes)
                .createIntent();	//Default Request Code is ImagePicker.REQUEST_CODE
        imagePickerLauncher.launch(myIntent); */

       /* ImagePicker.with(this)
                .galleryMimeTypes(new String[]{"image/png", "image/jpg", "image/jpeg"})
                .galleryOnly()	//User can only select image from Gallery
                .cropSquare()	    	//Crop image(Optional), Check Customization for more option
                .compress(1024)			//Final image size will be less than 1 MB(Optional)
                .maxResultSize(1080, 1080)	//Final image resolution will be less than 1080 x 1080(Optional)
                //  Path: /data/data/package/cache/ImagePicker
                .saveDir(new File(mainActivity.getCacheDir(), "ImagePicker"))
                .start(); */
    }

    private void setCoverArtPath(String path) {
        this.coverArtPath = path;
        viewHolder.coverartChanged = coverArtPath!=null;
        doPreviewCoverArt();
        toggleSaveFabAction();
    }

    private void startProgressBar() {
       // if(mMaterialProgressBar!=null) {
       //     mMaterialProgressBar.setVisibility(View.VISIBLE);
       // }
    }

    private void stopProgressBar() {
        //if(mMaterialProgressBar!=null) {
        //    mMaterialProgressBar.setVisibility(View.GONE);
        //}
    }

    private void setupProgressBar(View view) {
        //mMaterialProgressBar = view.findViewById(R.id.progress_bar);
        //mMaterialProgressBar.setVisibility(View.GONE);
    }

    private void setupFab(View view) {
        fabSaveAction = view.findViewById(R.id.fab_save_media);
        // save tag action
        fabSaveAction.setOnClickListener(view1 -> doSaveMediaItem());
    }

    private void doSaveMediaItem() {
        if(!(viewHolder.tagChanged || viewHolder.coverartChanged)) {
            return;
        }

        startProgressBar();
        if(viewHolder.tagChanged) {
            for(AudioTag item:mediaItems) {
                buildPendingTags(item);
            }
        }


      /*  String path = null;
        if(coverArtUri!=null) {
            coverArtUri.getPath();
        } */
        UpdateAudioFileWorker.startWorker(getContext(), mediaItems, coverArtPath);
      //  MediaItemIntentService.startService(getApplicationContext(), Constants.COMMAND_SAVE,mediaItems, artworkPath);

        viewHolder.resetState();
        toggleSaveFabAction();
        mainActivity.buildDisplayTag(false);
        mainActivity.updateTitlePanel();
        UIUtils.hideKeyboard(getActivity());
    }

    private void buildPendingTags(AudioTag tagUpdate) {
        // update new tag with data from UI
        if(!viewHolder.tagChanged) {
            return;
        }
        AudioTag tag = tagUpdate.clone();
        tagUpdate.setOriginTag(tag);

        tagUpdate.setTitle(buildTag(viewHolder.mTitleView, tagUpdate.getTitle()));
        tagUpdate.setTrack(buildTag(viewHolder.mTrackView, tagUpdate.getTrack()));
        tagUpdate.setAlbum(buildTag(viewHolder.mAlbumView, tagUpdate.getAlbum()));
        tagUpdate.setAlbumArtist(buildTag(viewHolder.mAlbumArtistView, tagUpdate.getAlbumArtist()));
        tagUpdate.setArtist(buildTag(viewHolder.mArtistView, tagUpdate.getArtist()));
        tagUpdate.setComposer(buildTag(viewHolder.mComposerView, tagUpdate.getComposer()));
        tagUpdate.setComment(buildTag(viewHolder.mCommentView, tagUpdate.getComment()));
        tagUpdate.setGrouping(buildTag(viewHolder.mGroupingView, tagUpdate.getGrouping()));
        tagUpdate.setGenre(buildTag(viewHolder.mGenreView, tagUpdate.getGenre()));
        tagUpdate.setYear(buildTag(viewHolder.mYearView, tagUpdate.getYear()));
        tagUpdate.setDisc(buildTag(viewHolder.mDiscView, tagUpdate.getDisc()));
        tagUpdate.setSource(buildTag(viewHolder.mMediaSourceView, tagUpdate.getSource()));
        tagUpdate.setAudiophile(buildTag(viewHolder.mAudiophileButton, tagUpdate.isAudiophile()));
        tagUpdate.setRating(buildTag(viewHolder.mRatingBar, tagUpdate.getRating()));
    }

    private int buildTag(MaterialRatingBar mRatingBar, int rating) {
       return (int) mRatingBar.getRating();
    }

    private String buildTag(TextView textView, String oldVal) {
        String text = getText(textView);
        if(!StringUtils.MULTI_VALUES.equalsIgnoreCase(text) ) {
            return text;
        }
        return oldVal;
    }

    private boolean buildTag(TriStateToggleButton view, boolean oldVal) {
        if(view.getToggleStatus() == TriStateToggleButton.ToggleStatus.off) {
            return false;
        }else if(view.getToggleStatus() == TriStateToggleButton.ToggleStatus.on) {
            return true;
        }else {
            return oldVal;
        }
    }

    private String getText(TextView textView) {
        return StringUtils.trimToEmpty(String.valueOf(textView.getText()));
    }
/*
    public void setCoverArtUri(Uri coverArtUri) {
            this.coverArtUri = coverArtUri;
            viewHolder.coverartChanged = coverArtUri!=null;
            doPreviewCoverArt();
            toggleSaveFabAction();
    } */

    private void doPreviewCoverArt() {
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
        private AudioTag displayTag;
        private EditText mTitleView;
        private AutoCompleteTextView mArtistView;
        private EditText mAlbumView;
        private AutoCompleteTextView mAlbumArtistView;
        private AutoCompleteTextView mGenreView;
        private EditText mYearView;
        private EditText mComposerView;
        private AutoCompleteTextView mGroupingView;
        private EditText mCommentView;
        private EditText mTrackView;
        private EditText mDiscView;
        private View mEditorCardView;
        private TriStateToggleButton mAudiophileButton;
        private MaterialRatingBar mRatingBar;
        private PowerSpinnerView mMediaSourceView;
        private List<IconSpinnerItem> mMediaSourceItems;
        private View mEditorPanel;
        private TextView mPathNameView;
        private TextView mFileNameView;
        private ImageView mCoverArt;

        private ViewTextWatcher mTextWatcher;
        protected volatile boolean tagChanged;
        protected volatile boolean coverartChanged;

        public ViewHolder(View view) {
            tagChanged = false;
            coverartChanged = false;
            mTextWatcher =new ViewTextWatcher();
            AudioTagRepository repository = AudioTagRepository.getInstance();  //new AudioTagRepository();

            mEditorCardView = view.findViewById(R.id.editorCardView);
            mEditorPanel = view.findViewById(R.id.editorPanel);
            mCoverArt = view.findViewById(R.id.editor_cover_art);

            // music mate info
            mRatingBar = view.findViewById(R.id.media_rating);
            mRatingBar.setOnRatingChangeListener((ratingBar, rating) -> {
                tagChanged = true;
                toggleSaveFabAction();
            });

            mAudiophileButton = view.findViewById(R.id.media_audiophile);
            mAudiophileButton.setOnToggleChanged((toggleStatus, booleanToggleStatus, toggleIntValue) -> {
                tagChanged = true;
                toggleSaveFabAction();
            });

            mMediaSourceView = view.findViewById(R.id.media_source);
            IconSpinnerAdapter adapter = new IconSpinnerAdapter(mMediaSourceView);
            mMediaSourceItems = new ArrayList<>();
            List<String> srcs = Constants.getSourceList(getContext());
           // Collections.sort(srcs);
            for(String src : srcs) {
                int rescId = AudioTagUtils.getSourceRescId(src);
                if(rescId == -1) {
                    mMediaSourceItems.add(new IconSpinnerItem(src, null));
                }else {
                    mMediaSourceItems.add(new IconSpinnerItem(src, ContextCompat.getDrawable(getContext(), rescId)));
                }
                /*
                if(src.equals(Constants.SRC_APPLE)) {
                    mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_APPLE, ContextCompat.getDrawable(getContext(), R.drawable.icon_itune)));
                }else if(src.equals(Constants.SRC_CD)) {
                    mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_CD, ContextCompat.getDrawable(getContext(), R.drawable.icon_cd)));
                }else if(src.equals(Constants.SRC_JOOX)) {
                    mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_JOOX, ContextCompat.getDrawable(getContext(), R.drawable.icon_joox)));
                }else if(src.equals(Constants.SRC_QOBUZ)) {
                    mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_QOBUZ, ContextCompat.getDrawable(getContext(), R.drawable.icon_qobuz)));
                }else if(src.equals(Constants.SRC_SACD)) {
                    mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_SACD, ContextCompat.getDrawable(getContext(), R.drawable.icon_sacd)));
                }else if(src.equals(Constants.SRC_SPOTIFY)) {
                    mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_SPOTIFY, ContextCompat.getDrawable(getContext(), R.drawable.icon_spotify)));
                }else if(src.equals(Constants.SRC_TIDAL)) {
                    mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_TIDAL, ContextCompat.getDrawable(getContext(), R.drawable.icon_tidal)));
                }else if(src.equals(Constants.SRC_VINYL)) {
                    mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_VINYL, ContextCompat.getDrawable(getContext(), R.drawable.icon_vinyl)));
                }else {
                    mMediaSourceItems.add(new IconSpinnerItem(src, null));
                } */
               // mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_2L, ContextCompat.getDrawable(getContext(), R.drawable.icon_cd)));
               // mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_APPLE, ContextCompat.getDrawable(getContext(), R.drawable.icon_cd)));
               // mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_CD, ContextCompat.getDrawable(getContext(), R.drawable.icon_cd)));
               // mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_HD_TRACKS, ContextCompat.getDrawable(getContext(), R.drawable.icon_cd)));
              //  mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_JOOX, ContextCompat.getDrawable(getContext(), R.drawable.icon_joox)));
              //  mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_NATIVE_DSD, ContextCompat.getDrawable(getContext(), R.drawable.icon_cd)));
              //  mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_QOBUZ, ContextCompat.getDrawable(getContext(), R.drawable.icon_qobuz)));
              //  mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_SACD, ContextCompat.getDrawable(getContext(), R.drawable.icon_sacd)));
               // mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_SPOTIFY, ContextCompat.getDrawable(getContext(), R.drawable.icon_spotify)));
                //mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_TIDAL, ContextCompat.getDrawable(getContext(), R.drawable.icon_tidal)));
               // mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_VINYL, ContextCompat.getDrawable(getContext(), R.drawable.icon_vinyl)));
            }
           // mMediaSourceItems.add(0, new IconSpinnerItem(Constants.SRC_NONE, null));
            adapter.setItems(mMediaSourceItems);
            mMediaSourceView.setSpinnerAdapter(adapter);
            mMediaSourceView.setOnSpinnerItemSelectedListener((i, o, i1, t1) -> {
                tagChanged = true;
                toggleSaveFabAction();
            });

            mPathNameView = view.findViewById(R.id.editor_pathname);
            mFileNameView = view.findViewById(R.id.editor_filename);

            // title
            mTitleView = setupTextEdit(view, R.id.tag_title);

            // artist
            mArtistView = setupAutoCompleteTextView(view, R.id.tag_artist);
            List<String> list = repository.getArtistList();
            ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(getApplicationContext(),R.layout.dialog_item_list, list);
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
           // ArrayAdapter<String> albumAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.select_dialog_item,repository.getAlbumList().toArray(new String[0]));
           // mAlbumView.setThreshold(2);//will start working from second character
          //  mAlbumView.setAdapter(albumAdapter); //setting the adapter data into the AutoCompleteTextView

            // album artist
            mAlbumArtistView = setupAutoCompleteTextView(view, R.id.tag_album_artist);
            list = repository.getAlbumArtistList(getContext());
            arrayAdapter = new ArrayAdapter<String>(getApplicationContext(),R.layout.dialog_item_list, list);
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
            list = repository.getGenreList(getContext());
            arrayAdapter = new ArrayAdapter<String>(getApplicationContext(),R.layout.dialog_item_list, list);
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
            list = repository.getDefaultGroupingList(getContext());
            arrayAdapter = new ArrayAdapter<String>(getApplicationContext(),R.layout.dialog_item_list, list);
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


            //composer
            mComposerView = setupTextEdit(view, R.id.tag_composer);

            // comment
            mCommentView = setupTextEdit(view, R.id.tag_comment);
        }

        private EditText setupTextEdit(View view, int viewId) {
            EditText  textInput = view.findViewById(viewId);
            textInput.addTextChangedListener(mTextWatcher);
            return textInput;
        }

        private PowerSpinnerView setupPowerSpinner(View view, int viewId) {
            PowerSpinnerView  textInput = view.findViewById(viewId);
            textInput.addTextChangedListener(mTextWatcher);
            return textInput;
        }

        private AutoCompleteTextView setupAutoCompleteTextView(View view, int viewId) {
            AutoCompleteTextView  textInput = view.findViewById(viewId);
            textInput.addTextChangedListener(mTextWatcher);
            return textInput;
        }

        void resetState() {
            tagChanged = false;
            coverartChanged = false;
            toggleSaveFabAction();
            mEditorCardView.clearFocus();
        }

        public void bindViewHolder(AudioTag mediaTag) {
            // music mate info
            this.displayTag = mediaTag;
            if(mediaTag.isAudiophile()) {
                mAudiophileButton.setToggleStatus(TriStateToggleButton.ToggleStatus.on);
            }else {
                mAudiophileButton.setToggleStatus(TriStateToggleButton.ToggleStatus.mid);
            }
            mRatingBar.setRating(mediaTag.getRating());
            File file = new File(mediaTag.getPath());
            mPathNameView.setText(file.getParentFile().getAbsolutePath());
            mFileNameView.setText(file.getName());

            ImageLoader imageLoader = Coil.imageLoader(getApplicationContext());
            ImageRequest request = new ImageRequest.Builder(getApplicationContext())
                .data(EmbedCoverArtProvider.getUriForMediaItem(displayTag))
                    .size(640, 640)
                //.allowHardware(false)
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
                            int frameColor = palette.getMutedColor(ContextCompat.getColor(getContext() ,R.color.bgColor));
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
                if(item.getText().equals(mediaTag.getSource())) {
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
        }

        public  class ViewTextWatcher implements TextWatcher {
            public ViewTextWatcher() {
            }
            public void afterTextChanged(Editable editable) {
                tagChanged = true;
                toggleSaveFabAction();
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
                    .alpha(1f).setDuration(200)
                    .setStartDelay(300L)
                    .setListener(new ViewPropertyAnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(View view) {
                            view.setVisibility(View.VISIBLE);
                        }
                    })
                    .start();
        }else {
            ViewCompat.animate(fabSaveAction)
                    .scaleX(0f).scaleY(0f)
                    .alpha(0f).setDuration(100)
                    .setListener(new ViewPropertyAnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(View view) {
                            view.setVisibility(View.GONE);
                        }
                    })
                    .start();
        }
    }

    private void doFormatTags() {
       // hideFabMenus();
        startProgressBar();
        for(AudioTag tag:mediaItems) {
            AudioTag mItem = tag.clone();
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
                tag.setAlbum(AudioTagUtils.getDefaultAlbum(tag));
            }
        }
        // set updated item on main activity
        viewHolder.bindViewHolder(mainActivity.buildDisplayTag(false));
        stopProgressBar();
        toggleSaveFabAction();
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
        List<String> tags = new ArrayList<String>();
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
                MediaTagParser parser = new MediaTagParser();
                AudioTag item = mediaItems.get(0);
                AudioTag mdata = item.clone();
                parser.parse(mdata, list);
                if (mdata != null) {
                    title.setText(StringUtils.trimToEmpty(mdata.getTitle()));
                    artist.setText(StringUtils.trimToEmpty(mdata.getArtist()));
                    album.setText(StringUtils.trimToEmpty(mdata.getAlbum()));
                    track.setText(StringUtils.trimToEmpty(mdata.getTrack()));
                    //item.setOriginTag(null); // clear pending tag
                }
            }catch (Exception ex) {
                Timber.d(ex);
            }
        });

        AlertDialog alert = new MaterialAlertDialogBuilder(getActivity(), R.style.AlertDialogTheme)
                .setTitle("")
                .setView(cview)
                .setCancelable(true)
                .create();
        alert.requestWindowFeature(Window.FEATURE_NO_TITLE);
        alert.setCanceledOnTouchOutside(false);
        // make popup round corners
        alert.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        btnOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<String> list = mTagListLayout.getTags();
                MediaTagParser parser = new MediaTagParser();
                for(AudioTag item:mediaItems) {
                    String mediaPath =  item.getPath();
                    File file = new File(mediaPath);
                    if(!file.exists()) continue;
                    parser.parse(item, list);
                }
                viewHolder.bindViewHolder(mainActivity.buildDisplayTag(false));
                alert.dismiss();
            }
        });
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alert.dismiss();
            }
        });
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
        return getActivity().getApplicationContext();
    }
}
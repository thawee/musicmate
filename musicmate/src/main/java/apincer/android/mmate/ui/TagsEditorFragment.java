package apincer.android.mmate.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
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
import com.google.android.material.snackbar.Snackbar;
import com.skydoves.powerspinner.IconSpinnerAdapter;
import com.skydoves.powerspinner.IconSpinnerItem;
import com.skydoves.powerspinner.PowerSpinnerView;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.Constants;
import apincer.android.mmate.R;
import apincer.android.mmate.fs.EmbedCoverArtProvider;
import apincer.android.mmate.fs.MusicFileProvider;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.repository.AudioTagRepository;
import apincer.android.mmate.service.MusicListeningService;
import apincer.android.mmate.ui.widget.TriStateToggleButton;
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

public class TagsEditorFragment extends Fragment {
    public static final int REQUEST_GET_CONTENT_IMAGE = 555;
    private TagsActivity mainActivity;
    private List<AudioTag> mediaItems;
    private ViewHolder viewHolder;
    private FloatingActionButton fabSaveAction;
    //private Snackbar mSnackbar;
    private File pendingCoverartFile;
    private FloatingActionButton fabMainAction;
    private ActivityResultLauncher startForResultFromGallery;

    final int DRAWABLE_LEFT = 0;
    final int DRAWABLE_TOP = 1;
    final int DRAWABLE_RIGHT = 2;
    final int DRAWABLE_BOTTOM = 3;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root= inflater.inflate(R.layout.fragment_editor, container, false);
        viewHolder = new ViewHolder(root);
        setupImagePicker();
        setupFab(root);
        setupFabMenus(root);
        //setupMenuBar(root);
        toggleSaveFabAction();
        return root;
    }

    private void setupImagePicker() {
        startForResultFromGallery = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
            @Override
            public void onActivityResult(ActivityResult result) {
                if (result.getResultCode() == Activity.RESULT_OK){
                  /*  try {
                        if (result.getData() != null){
                            Uri selectedImageUri = result.getData().getData();
                            Bitmap bitmap = BitmapFactory.decodeStream(getBaseContext().getContentResolver().openInputStream(selectedImageUri));
                            // set bitmap to image view here........
                            binding.menuFragmentCircularProfileImageview.setImageBitmap(bitmap)
                        }
                    }catch (Exception exception){
                        Log.d("TAG",""+exception.getLocalizedMessage());
                    } */

                    try {
                        if (result.getData() == null && result.getData().getData()!=null) {
                            return;
                        }
                        InputStream input = getApplicationContext().getContentResolver().openInputStream(result.getData().getData());
                        File outputDir = getApplicationContext().getCacheDir(); // context being the Activity pointer
                        pendingCoverartFile = new File(outputDir, "tmp_cover_art");
                        if (pendingCoverartFile.exists()) {
                            pendingCoverartFile.delete();
                        }
                        FileOutputStream output = new FileOutputStream(new File(outputDir, "tmp_cover_art"));
                        // MediaFileRepository.getInstance(getActivity().getApplication()).copy(input, pendingCoverartFile);
                        IOUtils.copy(input, output);
                        output.close();

                        mainActivity.doPreviewCoverArt(pendingCoverartFile);
                        viewHolder.coverartChanged = true;
                        toggleSaveFabAction();
                    }catch (IOException ex) {
                        Timber.e( ex);
                    }
                }
            }
        });
    }

    /*
    private void setupMenuBar(View root) {
        TextView btnInfo = root.findViewById(R.id.editor_btns);
        //buttons

        int textColor = ContextCompat.getColor(getContext(), R.color.grey200);
        float labelTextSize = UIUtils.sp2px(getActivity().getApplication(), 10);
        SimplifySpanBuild spanBtn = new SimplifySpanBuild("");
        spanBtn.appendMultiClickable(new SpecialClickableUnit(btnInfo, (tv, clickableSpan) -> {
            //  doDeleteMediaItems(Collections.singletonList(mediaItem));
            doDeleteMediaItem();
        }).setNormalTextColor(textColor), new SpecialTextUnit("< Delete Songs").setTextSize(12))
                .append(new SpecialLabelUnit(" | ", Color.GRAY, labelTextSize, Color.TRANSPARENT).showBorder(Color.BLACK, 2).setPadding(5).setPaddingLeft(10).setPaddingRight(10).setGravity(SpecialGravity.CENTER))
                .appendMultiClickable(new SpecialClickableUnit(btnInfo, (tv, clickableSpan) -> {
           // doWebSearch(mediaItem);
            doWebSearch();
        }).setNormalTextColor(textColor), new SpecialTextUnit("< Web Search >").setTextSize(12))
                .append(new SpecialLabelUnit(" | ", Color.GRAY, labelTextSize, Color.TRANSPARENT).showBorder(Color.BLACK, 2).setPadding(5).setPaddingLeft(10).setPaddingRight(10).setGravity(SpecialGravity.CENTER))

                .appendMultiClickable(new SpecialClickableUnit(btnInfo, (tv, clickableSpan) -> {
                   // doMoveMediaItems(Collections.singletonList(mediaItem));
                    doMoveMediaItem();
                }).setNormalTextColor(textColor), new SpecialTextUnit("Import Songs >").setTextSize(12))
                .append("");
                //.append(new SpecialLabelUnit(" | ", Color.GRAY, labelTextSize, Color.TRANSPARENT).showBorder(Color.BLACK, 2).setPadding(5).setPaddingLeft(10).setPaddingRight(10).setGravity(SpecialGravity.CENTER));

                    // MetadataActivity.startActivity(MediaBrowserActivity.this, Collections.singletonList(mediaItem));
                    // TagsActivity.startActivity(MediaBrowserActivity.this, Collection
        // format, read, cover art
        // spanBtn.append(" xxxx ");
        // Google, Analyser, Edit, Delete, Manage
        btnInfo.setText(spanBtn.build());
    } */

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
    public void onResume() {
        super.onResume();
        // Register for the particular broadcast based on ACTION string
       // IntentFilter filter = new IntentFilter(AudioFileRepository.ACTION);
       // LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mFileManagerReceiver, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister the listener when the application is paused
      //  LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mFileManagerReceiver);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewHolder.bindViewHolder(mainActivity.buildDisplayTag(true));
        viewHolder.resetState();
        toggleSaveFabAction();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_GET_CONTENT_IMAGE) {
            try {
                if (data == null || data.getData() == null) {
                    return;
                }
                InputStream input = getApplicationContext().getContentResolver().openInputStream(data.getData());
                File outputDir = getApplicationContext().getCacheDir(); // context being the Activity pointer
                pendingCoverartFile = new File(outputDir, "tmp_cover_art");
                if (pendingCoverartFile.exists()) {
                    pendingCoverartFile.delete();
                }
                FileOutputStream output = new FileOutputStream(new File(outputDir, "tmp_cover_art"));
               // MediaFileRepository.getInstance(getActivity().getApplication()).copy(input, pendingCoverartFile);
                IOUtils.copy(input, output);
                output.close();

               mainActivity.doPreviewCoverArt(pendingCoverartFile);
                viewHolder.coverartChanged = true;
                toggleSaveFabAction();
            }catch (IOException ex) {
                Timber.e( ex);
            }
        }
    }

    private void doPickCoverart() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        //startActivityForResult(intent,REQUEST_GET_CONTENT_IMAGE);
        startForResultFromGallery.launch(intent);
    }

    /*
    private void doSaveCoverart() {
            File theFilePath = MediaFileRepository.getDownloadPath(mediaItems.get(0).getTitle()+".png");
            AudioFileRepository.getInstance(getActivity().getApplication()).saveArtworkToFile(mediaItems.get(0), theFilePath.getAbsolutePath());
			ToastUtils.showActionMessageEditor(getActivity(), fabMainAction, 1,0,1, "success", "Save Artwork to "+theFilePath.getName());

    } */

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
        String artworkPath = null;
        if(viewHolder.coverartChanged && pendingCoverartFile!=null) {
            artworkPath = pendingCoverartFile.getAbsolutePath();
        }
        if(viewHolder.tagChanged) {
            for(AudioTag item:mediaItems) {
                buildPendingTags(item);
            }
        }

        UpdateAudioFileWorker.startWorker(getContext(), mediaItems, artworkPath);
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

        private ViewTextWatcher mTextWatcher;
        protected boolean tagChanged;
        protected boolean coverartChanged;

        public ViewHolder(View view) {
            tagChanged = false;
            coverartChanged = false;
            mTextWatcher =new ViewTextWatcher();
            AudioTagRepository repository = AudioTagRepository.getInstance();  //new AudioTagRepository();

            mEditorCardView = view.findViewById(R.id.editorCardView);
            mEditorPanel = view.findViewById(R.id.editorPanel);

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
            mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_NONE, null));
            mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_2L, ContextCompat.getDrawable(getContext(), R.drawable.icon_cd)));
            mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_APPLE, ContextCompat.getDrawable(getContext(), R.drawable.icon_cd)));
            mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_CD, ContextCompat.getDrawable(getContext(), R.drawable.icon_cd)));
            mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_HD_TRACKS, ContextCompat.getDrawable(getContext(), R.drawable.icon_cd)));
            mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_JOOX, ContextCompat.getDrawable(getContext(), R.drawable.icon_joox)));
            mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_NATIVE_DSD, ContextCompat.getDrawable(getContext(), R.drawable.icon_cd)));
            mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_QOBUZ, ContextCompat.getDrawable(getContext(), R.drawable.icon_qobuz)));
            mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_SACD, ContextCompat.getDrawable(getContext(), R.drawable.icon_sacd)));
            mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_SPOTIFY, ContextCompat.getDrawable(getContext(), R.drawable.icon_spotify)));
            mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_TIDAL, ContextCompat.getDrawable(getContext(), R.drawable.icon_tidal)));
            mMediaSourceItems.add(new IconSpinnerItem(Constants.SRC_VINYL, ContextCompat.getDrawable(getContext(), R.drawable.icon_vinyl)));
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
            list = repository.getAlbumArtistList();
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
            list = repository.getGroupingList(getContext());
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
            if(StringUtils.isEmpty(tag.getAlbum()) && !StringUtils.isEmpty(tag.getArtist())) {
                tag.setAlbum(tag.getArtist()+" - Single");
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
    private void doAnalyser() {
        Intent intent = getActivity().getPackageManager().getLaunchIntentForPackage("com.andrewkhandr.aspect");
        if (intent != null && mediaItems.size()==1) {
            ApplicationInfo ai = MusicListeningService.getInstance().getApplicationInfo("com.andrewkhandr.aspect");
            if (ai != null) {
                intent.setAction(Intent.ACTION_SEND);
                MimeTypeMap mime = MimeTypeMap.getSingleton();

                String path = mediaItems.get(0).getPath();
                File file = new File(path);
                String type = mime.getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(path));
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Uri apkURI = MusicFileProvider.getUriForFile(path);
                    // Uri apkURI = FileProvider.getUriForFile(getApplicationContext(), "ru.zdevs.zarchiver.system.FileProvider", file);
                    intent.setDataAndType(apkURI, type);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else {
                    intent.setDataAndType(Uri.fromFile(file), type);
                }
                startActivity(intent);
            }
        }
    } */

    private void doWebSearch() {
        try {
            String text = "";
            int size = mediaItems.size();
            AudioTag item = mediaItems.get(0);
            // title and artist
                if(size ==1 && !StringUtils.isEmpty(item.getTitle())) {
                    text = text+" "+item.getTitle();
                }
                if(!StringUtils.isEmpty(item.getAlbum())) {
                    text = text+" "+item.getAlbum();
                }
                if(!StringUtils.isEmpty(item.getArtist())) {
                    text = text+" "+item.getArtist();
                }
            String search= URLEncoder.encode(text, "UTF-8");
            Uri uri = Uri.parse("http://www.google.com/#q=" + search);
            Intent gSearchIntent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(gSearchIntent);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        /*
        Uri uri = Uri.parse("http://www.google.com/#q=" + Search);
        Intent searchIntent = new Intent(Intent.ACTION_WEB_SEARCH);
        searchIntent.putExtra(SearchManager.QUERY, uri);
        startActivity(searchIntent);
        */
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

    /*
    // Define the callback for what to do when data is received
    private BroadcastReceiver mFileManagerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int resultCode = intent.getIntExtra(Constants.KEY_RESULT_CODE, Activity.RESULT_CANCELED);
            if (resultCode == Activity.RESULT_OK) {
                String command = intent.getStringExtra(Constants.KEY_COMMAND);
                String status = intent.getStringExtra(Constants.KEY_STATUS);
                String message = intent.getStringExtra(Constants.KEY_MESSAGE);
                int successCount = intent.getIntExtra(Constants.KEY_SUCCESS_COUNT,0);
                int errorCount = intent.getIntExtra(Constants.KEY_ERROR_COUNT,0);
                int pendingTotal = intent.getIntExtra(Constants.KEY_PENDING_TOTAL, 0);

                ToastUtils.showActionMessageEditor(getActivity(), fabMainAction, successCount,errorCount,pendingTotal, status, message);

                if("success".equalsIgnoreCase(status)) {
                   // Toasty.success(getActivity(), percent+message, Toast.LENGTH_LONG, true).show();
                    if((successCount+errorCount)==pendingTotal) {
                        stopProgressBar();
                        // remove snackbar
                        if(mSnackbar!=null) {
                            mSnackbar.dismiss();
                            mSnackbar = null;
                        }
                        if (Constants.COMMAND_DELETE.equalsIgnoreCase(command)) {
                            // back to main activity after delete all
                            getActivity().onBackPressed();
                        }else {
                            // refresh view
                            viewHolder.bindViewHolder(mainActivity.buildDisplayTag(true)); // silent mode
                            viewHolder.resetState();
                            toggleSaveFabAction();
                        }
                        mainActivity.updateTitlePanel();
                    }
                }
            }
        }
    }; */
}
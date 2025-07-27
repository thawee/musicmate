package apincer.android.mmate.ui;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static apincer.android.mmate.utils.MusicTagUtils.getRating;
import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.trim;
import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.vanniktech.textbuilder.TextBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import apincer.android.mmate.Constants;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.notification.AudioTagEditEvent;
import apincer.android.mmate.notification.AudioTagPlayingEvent;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.provider.CoverartFetcher;
import apincer.android.mmate.ui.view.ResolutionView;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mmate.utils.UIUtils;
import apincer.android.mmate.worker.DeleteAudioFileWorker;
import apincer.android.mmate.worker.ImportAudioFileWorker;
import apincer.android.mmate.worker.MusicMateExecutors;
import cn.iwgang.simplifyspan.SimplifySpanBuild;
import cn.iwgang.simplifyspan.unit.SpecialClickableUnit;
import cn.iwgang.simplifyspan.unit.SpecialTextUnit;
import coil3.ImageLoader;
import coil3.SingletonImageLoader;
import coil3.request.CachePolicy;
import coil3.request.ImageRequest;
import coil3.size.Size;
import coil3.target.ImageViewTarget;
import sakout.mehdi.StateViews.StateView;

public class TagsActivity extends AppCompatActivity {
    private static final String TAG = "TagsActivity";

    private TagsViewModel viewModel;

    private ImageView coverArtView;
    private TabLayout tabLayout;
    private Toolbar toolbar;
    private AppBarLayout appBarLayout;

    private TextView titleView;
    private TextView artistView ;
    private TextView albumView ;
    private TextView genreView;
    private TextView encInfo;
    private TextView pathInfo;
    private View pathInfoLine;
   private View hiresView;
   private ResolutionView resolutionView;
    private TextView ratingView;

    private int toolbar_from_color;
    private int toolbar_to_color;
   // FileRepository repos;
    private Fragment activeFragment;

    private ImageView playerBtn;
    private View playerPanel;
    private TextView playerName;
   // private boolean closePreview = true;

    private boolean previewState = true;

    private AlertDialog progressDialog;
    private TextView progressLabel;
    private boolean finishOnTimeout = false;

    private final AtomicLong lastProgressUpdate = new AtomicLong(0);
    private final long PROGRESS_UPDATE_THROTTLE_MS = 100; // Limit updates to every 100ms

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
       // stopProgressBar();
        dismissProgressDialog(); // Ensure progress dialog is dismissed
    }

    private void doMeasureDR() {
        viewModel.measureDynamicRange(getApplicationContext());
    }

    @Override
    protected void onPause() {
        super.onPause();
        dismissProgressDialog();
        stopProgressBar();
    }
    @Override
    protected void onResume() {
        super.onResume();
    }

    private TextView tagInfo;
    //private SearchCriteria criteria;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);

        // set status bar color to black
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(this, android.R.color.black));

        setContentView(R.layout.activity_tags);

        ExecutorService executor = MusicMateExecutors.getExecutorService(); // Get your executor
        TagsViewModel.TagsViewModelFactory factory = new TagsViewModel.TagsViewModelFactory(executor /*, getApplicationContext() if needed and handled carefully */);
        viewModel = new ViewModelProvider(this, factory).get(TagsViewModel.class);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        // Your business logic to handle the back pressed event
        OnBackPressedCallback onBackPressedCallback = new TagsActivity.BackPressedCallback(true);
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);

        coverArtView = findViewById(R.id.panel_cover_art);
        CollapsingToolbarLayout toolBarLayout = findViewById(R.id.toolbar_layout);
        int statusBarHeight = getStatusBarHeight();
        int height = UIUtils.getScreenHeight(this);
        toolBarLayout.getLayoutParams().height = height + statusBarHeight + 70;
        toolbar_from_color = ContextCompat.getColor(getApplicationContext(), apincer.android.library.R.color.colorPrimary);
        toolbar_to_color = ContextCompat.getColor(getApplicationContext(), apincer.android.library.R.color.colorPrimary);
        StateView mStateView = findViewById(R.id.status_page);
        mStateView.hideStates();

        setupTitlePanelViews(); // Method to findViewById all title panel views
        setupActionButtons();   // Method to setOnClickListener for buttons like btnDelete, btnMDR etc.

        // --- Observe LiveData from ViewModel ---
        observeViewModel();

        // If you need to re-process an event on configuration change,
        // the ViewModel might need to hold the raw event data or re-trigger.
        // For simplicity, assuming EventBus delivers sticky events if the Activity is recreated.

        // Initial setup for ViewPager might depend on data, or can be done once
        // Consider if setupPageViewer() needs data from ViewModel before being called
        // If data is ready, call it. If not, observe some "dataReady" LiveData.
        // For now, let's assume it can be setup and fragments will observe ViewModel.
        setupPageViewer();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        setupMenuToolbar();
        return true;
    }

    @Subscribe(threadMode = ThreadMode.MAIN,sticky = true)
    public void onMessageEvent(AudioTagEditEvent event) {
        if (event != null && event.getItems() != null && !event.getItems().isEmpty()) {
            viewModel.processAudioTagEditEvent((Collection<MusicTag>) event.getItems(), event.getSearchCriteria());
            // No need to stopProgressBar here, ViewModel's status LiveData will handle UI
            EventBus.getDefault().removeStickyEvent(event); // Consume the event
        }

        // call from EventBus on preview/edit selected tags from main screen
    }

    @Subscribe(threadMode = ThreadMode.MAIN,sticky = true)
    public void onMessageEvent(AudioTagPlayingEvent event) {
        if (MusixMateApp.getPlayerControl().isPlaying()) {
            playerBtn.setVisibility(VISIBLE);
            MusicTag tag = event.getPlayingSong();
            if (tag != null) {
                // Check if the current view should be updated by the playing song
                // This logic might depend on user preferences or current state (closePreview)
                // For now, let's assume you want to update if a song is playing
                viewModel.updateWithPlayingSong(tag);
                // No need to stopProgressBar, ViewModel's status LiveData will handle UI
            }
        }
        EventBus.getDefault().removeStickyEvent(event); // Optional: if you want to consume it

    }

    private void setupPageViewer() {
        //final AppBarLayout appBarLayout = findViewById(R.id.appbar);
        appBarLayout = findViewById(R.id.appbar);
        ViewPager2 viewPager = findViewById(R.id.viewpager);

        tabLayout = findViewById(R.id.tabLayout);
        TagsTabLayoutAdapter adapter = new TagsTabLayoutAdapter(getSupportFragmentManager(), getLifecycle());

        // adapter.addNewTab(new TagsMusicBrainzFragment(), "MUSICBRAINZ");
        adapter.addNewTab(new TagsEditorFragment(), "TAGs");
        adapter.addNewTab(new TagsTechnicalFragment(), "for Nerd");
        viewPager.setAdapter(adapter);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                activeFragment = adapter.fragments.get(position);
                setupMenuToolbar();
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
            }
        });

        TabLayoutMediator tabLayoutMediator = new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> tab.setText(adapter.getPageTitle(position)));
        tabLayoutMediator.attach();

        appBarLayout.addOnOffsetChangedListener(new OffSetChangeListener());

        findViewById(R.id.btnEdit).setOnClickListener(v -> appBarLayout.setExpanded(false, true));
        findViewById(R.id.btnDelete).setOnClickListener(v -> doDeleteMediaItems());
        findViewById(R.id.btnMDR).setOnClickListener(v -> doMeasureDR());
        findViewById(R.id.btnImport).setOnClickListener(v -> doMoveMediaItems());
        if (viewModel.displayTag != null && viewModel.displayTag.getValue()!= null) {
            findViewById(R.id.btnAspect).setOnClickListener(view -> ApplicationUtils.startAspect(this, viewModel.displayTag.getValue()));
            findViewById(R.id.btnWebSearch).setOnClickListener(view -> ApplicationUtils.webSearch(this, viewModel.displayTag.getValue()));
            findViewById(R.id.btnExplorer).setOnClickListener(view -> ApplicationUtils.startFileExplorer(this, viewModel.displayTag.getValue()));

        }
    }

    private void setupTitlePanelViews() {
        titleView = findViewById(R.id.panel_title);
        artistView = findViewById(R.id.panel_artist);
        albumView = findViewById(R.id.panel_album);
        genreView = findViewById(R.id.panel_genre);
        encInfo = findViewById(R.id.panel_enc);
        pathInfo = findViewById(R.id.panel_path);
        pathInfoLine = findViewById(R.id.panel_path_line);
        tagInfo = findViewById(R.id.panel_tag);
        hiresView = findViewById(R.id.icon_hires);
        resolutionView = findViewById(R.id.icon_resolution);
        ratingView = findViewById(R.id.rating);

        playerName = findViewById(R.id.music_player_name);
        playerBtn = findViewById(R.id.music_player);
        playerPanel = findViewById(R.id.music_player_panel);
    }

    private void setupActionButtons() {
        findViewById(R.id.btnEdit).setOnClickListener(v -> appBarLayout.setExpanded(false, true));
        findViewById(R.id.btnDelete).setOnClickListener(v -> doDeleteMediaItems()); // This method would now likely call a ViewModel method
        findViewById(R.id.btnMDR).setOnClickListener(v -> {
            // Pass context carefully if needed by lower layers called by ViewModel
            viewModel.measureDynamicRange(getApplicationContext());
        });
        findViewById(R.id.btnImport).setOnClickListener(v -> doMoveMediaItems()); // Call ViewModel
        findViewById(R.id.btnWebSearch).setOnClickListener(v -> ApplicationUtils.webSearch(this, viewModel.displayTag.getValue()));
        findViewById(R.id.btnExplorer).setOnClickListener(v -> ApplicationUtils.startFileExplorer(this, viewModel.displayTag.getValue()));
        findViewById(R.id.btnAspect).setOnClickListener(v -> ApplicationUtils.startAspect(this, viewModel.displayTag.getValue()));
    }

    private void observeViewModel() {

        viewModel.displayTag.observe(this, musicTag -> {
            // This is your new 'displayTag'
            updateTitlePanel(musicTag); // Pass the new displayTag to the update method
            // Update cover art using Coil based on this new displayTag
            loadImages(musicTag);
            updateViewPagers(musicTag);
            dismissProgressDialog();
        });

        viewModel.drMeasurementStatus.observe(this, this::handleOperationStatus);
    }

    private void updateViewPagers(MusicTag musicTag) {
        if (activeFragment instanceof TagsEditorFragment) {
            ((TagsEditorFragment) activeFragment).initEditorInputs(musicTag);
        }
    }

    protected void updateTitlePanel(MusicTag currentDisplayTag) {
        if(MusixMateApp.getPlayerControl().isPlaying()) {
            playerBtn.setVisibility(VISIBLE);
            playerPanel.setVisibility(VISIBLE);
            playerName.setVisibility(VISIBLE);
            playerName.setText(StringUtils.truncate(MusixMateApp.getPlayerControl().getPlayerInfo().getPlayerName(), 20));
            playerBtn.setBackground(MusixMateApp.getPlayerControl().getPlayerInfo().getPlayerIconDrawable());
        }else {
            playerBtn.setVisibility(GONE);
            playerPanel.setVisibility(GONE);
            playerName.setVisibility(GONE);
        }

        if (currentDisplayTag == null) {
            // Handle null case, maybe clear fields or show placeholder
            titleView.setText("");
            artistView.setText("");
            // ... clear other fields ...
            return;
        }

        titleView.setText(trim(currentDisplayTag.getTitle(), " - "));
        artistView.setText(trim(currentDisplayTag.getArtist(), " - "));

        // load resolution, quality, coverArt
        loadImages(currentDisplayTag);
        resolutionView.setMusicItem(currentDisplayTag);

        TextBuilder builder = new TextBuilder(getApplicationContext());
        int ratingColor = getColor(R.color.audiophile_background); // getDRScoreColor(holder.mContext, (int) tag.getDynamicRangeScore());
        int rating = getRating(currentDisplayTag);
        int i=0;
        for(;i<rating;i++) {
            //builder.addColoredText("*", ratingColor);
            builder.addColoredText("\u272D", ratingColor);
        }
        while(i <5) {
           // builder.addText("*");
            builder.addText("\u2729");
            i++;
        }
        builder.into(ratingView);
        //  qualityView.setImageBitmap(IconProviders.createQualityIcon(getApplicationContext(), displayTag));

        if(MusicTagUtils.isDSD(currentDisplayTag) || MusicTagUtils.isHiRes(currentDisplayTag)) {
            hiresView.setVisibility(VISIBLE);
        }else {
            hiresView.setVisibility(GONE);
        }

        artistView.setPaintFlags(artistView.getPaintFlags()| Paint.UNDERLINE_TEXT_FLAG);
        artistView.setOnClickListener(view -> {
            //filter by artist
            doBackToMainActivity(Constants.FILTER_TYPE_ARTIST, currentDisplayTag.getArtist());

        });
        if(isEmpty(currentDisplayTag.getAlbum())) {
            albumView.setText(String.format("[%s]", MusicTagUtils.getDefaultAlbum(currentDisplayTag)));
        }else {
            albumView.setText(currentDisplayTag.getAlbum());
            albumView.setPaintFlags(albumView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            albumView.setOnClickListener(view -> {
                // filter by album
                doBackToMainActivity(Constants.FILTER_TYPE_ALBUM, currentDisplayTag.getAlbum());
            });
        }
        String mediaTypeAndPublisher;
        if((!isEmpty(currentDisplayTag.getAlbumArtist()))) {
            mediaTypeAndPublisher = " " +currentDisplayTag.getAlbumArtist()+" ";
        }else {
            mediaTypeAndPublisher = " " +StringUtils.SYMBOL_SEP+" ";
        }

        genreView.setText(mediaTypeAndPublisher);

        // Tag
        int linkNorTextColor = ContextCompat.getColor(getApplicationContext(), R.color.white);
        int linkPressBgColor = ContextCompat.getColor(getApplicationContext(), R.color.grey200);
        SimplifySpanBuild tagSpan = new SimplifySpanBuild("");
        tagSpan.append(new SpecialTextUnit(StringUtils.SYMBOL_SEP).setTextSize(14).useTextBold());

        tagSpan.appendMultiClickable(new SpecialClickableUnit(tagInfo, (tv, clickableSpan) -> {
                    doBackToMainActivity(Constants.FILTER_TYPE_GENRE, currentDisplayTag.getGenre());
                }).setNormalTextColor(linkNorTextColor).setPressBgColor(linkPressBgColor),
                new SpecialTextUnit(isEmpty(currentDisplayTag.getGenre())?Constants.UNKNOWN:currentDisplayTag.getGenre()).setTextSize(14).useTextBold().showUnderline());

        tagSpan.append(new SpecialTextUnit(StringUtils.SYMBOL_SEP).setTextSize(14).useTextBold());
        tagSpan.appendMultiClickable(new SpecialClickableUnit(tagInfo, (tv, clickableSpan) -> {
                    doBackToMainActivity(Constants.FILTER_TYPE_GROUPING, currentDisplayTag.getGrouping());
                }).setNormalTextColor(linkNorTextColor).setPressBgColor(linkPressBgColor),
                new SpecialTextUnit(isEmpty(currentDisplayTag.getGrouping())?" - ":currentDisplayTag.getGrouping()).setTextSize(14).useTextBold().showUnderline());

        tagSpan.append(new SpecialTextUnit(StringUtils.SYMBOL_SEP).setTextSize(14).useTextBold());
        tagInfo.setText(tagSpan.build());

        // Path Info
        String simplePath = currentDisplayTag.getSimpleName();
        if(simplePath.contains("/")) {
            simplePath = simplePath.substring(0, simplePath.lastIndexOf("/"));
        }
        boolean inLibrary = MusicTagUtils.isManagedInLibrary(getApplicationContext(), currentDisplayTag);
        // String matePath = repos.buildCollectionPath(displayTag, true);
        if(inLibrary) {
            pathInfoLine.setBackgroundColor(getColor(R.color.grey400));
        }else {
            pathInfoLine.setBackgroundColor(getColor(R.color.warn_not_in_managed_dir));
        }
        pathInfo.setText(simplePath);

        pathInfo.setOnClickListener(view -> {
            File file = new File(currentDisplayTag.getPath());
            if (!file.isDirectory()) {
                file = file.getParentFile();
            }
            String filterPath = file.getAbsolutePath() + File.separator;
            doBackToMainActivity(Constants.FILTER_TYPE_PATH, filterPath);
        });

        // ENC info
        try {
            int metaInfoTextSize = 12; //10
            int encColor = ContextCompat.getColor(getApplicationContext(), R.color.material_color_blue_grey_200);
            SimplifySpanBuild spannableEnc = new SimplifySpanBuild("");
            // spannableEnc.append(new SpecialTextUnit(StringUtils.SEP_LEFT,encColor));

            // bps
           // spannableEnc.append(new SpecialTextUnit(StringUtils.formatAudioBitsDepth(currentDisplayTag.getAudioBitsDepth()), encColor).setTextSize(metaInfoTextSize));
           // spannableEnc.append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP, encColor).setTextSize(metaInfoTextSize));
          //  spannableEnc.append(new SpecialTextUnit(StringUtils.formatAudioSampleRate(currentDisplayTag.getAudioSampleRate(), true), encColor).setTextSize(metaInfoTextSize));
           // spannableEnc.append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP, encColor).setTextSize(metaInfoTextSize));

            spannableEnc.append(new SpecialTextUnit(StringUtils.formatAudioBitRate(currentDisplayTag.getAudioBitRate()),encColor).setTextSize(metaInfoTextSize));

            String labelDr = trim(MusicTagUtils.getDynamicRangeScore(currentDisplayTag), "--");
            spannableEnc.append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP).setTextSize(metaInfoTextSize))
                    .append(new SpecialTextUnit("DR"+labelDr, encColor).setTextSize(metaInfoTextSize));

            spannableEnc.append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP).setTextSize(metaInfoTextSize))
                    .append(new SpecialTextUnit(StringUtils.formatDurationAsMinute(currentDisplayTag.getAudioDuration()), encColor).setTextSize(metaInfoTextSize));

            spannableEnc.append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP).setTextSize(metaInfoTextSize))
                    .append(new SpecialTextUnit(StringUtils.formatStorageSize(currentDisplayTag.getFileSize()), encColor).setTextSize(metaInfoTextSize));

            encInfo.setText(spannableEnc.build());
        }catch (Exception ex) {
            Log.e(TAG, "updateTitlePanel", ex);
        }
    }

    private void handleOperationStatus(OperationStatus status) {
        if (status instanceof Idle) {
            dismissProgressDialog();
        } else if (status instanceof Loading) {
            showProgressDialog(((Loading) status).message, -1, null);
        } else if (status instanceof ProgressUpdate) {
            ProgressUpdate pu = (ProgressUpdate) status;
            showProgressDialog("Processing...", pu.progress, pu.currentStep);
        } else if (status instanceof Success) {
            dismissProgressDialog();
            // Show a Toast or Snackbar with ((Success) status).message
            //ApplicationUtils.showToast(this, ((ListenableWorker.Result.Success) status).message);
            viewModel.resetDrMeasurementStatus(); // Go back to Idle
        } else if (status instanceof Error) {
            dismissProgressDialog();
            // Show a Toast or Snackbar with ((Error) status).errorMessage
           // ApplicationUtils.showErrorToast(this, ((Error) status).errorMessage);
            viewModel.resetDrMeasurementStatus(); // Go back to Idle
        }
    }

    private void showProgressDialog(String title, int progress, String currentStep) {

        // If you have a ProgressBar
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = null; // Allow it to be recreated
    }

    private void doBackToMainActivity(String filterType, String filterText) {

        Intent resultIntent = new Intent();
        resultIntent.putExtra(Constants.KEY_FILTER_TYPE, filterType);
        resultIntent.putExtra(Constants.KEY_FILTER_KEYWORD, trimToEmpty(filterText));

        // Set the result to RESULT_OK and pass the intent containing the data
        setResult(AppCompatActivity.RESULT_OK, resultIntent);

        finish();
    }

    // Create a separate method for image loading to reduce clutter
    private void loadImages(MusicTag displayTag) {
        // Load all images in parallel
        ImageLoader imageLoader = SingletonImageLoader.get(getApplicationContext());

        // Cover art with higher priority
        ImageRequest coverRequest = CoverartFetcher.builder(getApplicationContext(), displayTag)
                .size(Size.ORIGINAL)
                .data(displayTag)
                .target(new ImageViewTarget(coverArtView))
                .memoryCachePolicy(CachePolicy.ENABLED)
                .error(imageRequest -> CoverartFetcher.getDefaultCover(getApplicationContext()))
                .build();

        // Enqueue all requests
        imageLoader.enqueue(coverRequest);
    }

    public int getStatusBarHeight() {
        int result = 0;
        @SuppressLint({"DiscouragedApi", "InternalInsetResource"}) int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    public void doDeleteMediaItems() {
        String text = "";
        List<MusicTag> editItems = getEditItems();
        if(editItems.size()>1) {
            text = "Move songs to the Trash?";
        }else {
            text = "Move this song to the Trash?";
        }

        final BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this); // Or pass 'context'
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_trash_dialog, null);
        bottomSheetDialog.setContentView(sheetView);

        Button cancelButton = sheetView.findViewById(R.id.button_cancel);
        Button moveToTrashButton = sheetView.findViewById(R.id.button_move_to_trash);
        TextView title = sheetView.findViewById(R.id.bottom_sheet_title);

        title.setText(text);
        cancelButton.setOnClickListener(v -> bottomSheetDialog.dismiss());

        moveToTrashButton.setOnClickListener(v -> {
            startProgressBar();
            DeleteAudioFileWorker.startWorker(getApplicationContext(), editItems);

            // set timeout to finish, 3 seconds
            finishOnTimeout = true;
            MusicMateExecutors.schedule(() -> {
                if(finishOnTimeout) {
                    finish(); // back to prev activity
                }
            }, 3);
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    public void doMoveMediaItems() {
        startProgressBar();
        ImportAudioFileWorker.startWorker(getApplicationContext(), getEditItems());

        // set timeout to finish, 5 seconds
        finishOnTimeout = true;
        MusicMateExecutors.schedule(() -> {
            if(finishOnTimeout) {
                viewModel.refreshDisplayTag();
                //finish(); // back to prev activity
            }
        }, 5);

    }

    public void setupMenuEditor(Toolbar.OnMenuItemClickListener listener) {
            toolbar.getMenu().clear();
            toolbar.inflateMenu(R.menu.menu_tag_editor);
            toolbar.setOnMenuItemClickListener(listener);
    }

    public void setupMenuTechnical(Toolbar.OnMenuItemClickListener listener) {
            toolbar.getMenu().clear();
            toolbar.inflateMenu(R.menu.menu_tag_technical);
            toolbar.setOnMenuItemClickListener(listener);
    }
    public void setupMenuPreview(Toolbar.OnMenuItemClickListener listener) {
        toolbar.getMenu().clear();
    }

    public List<MusicTag> getEditItems() {
        return viewModel.editItems.getValue();
    }

    public MusicTag getDisplayTag() {
        return viewModel.displayTag.getValue();
    }

    public void refreshDisplayTag() {
        viewModel.refreshDisplayTag();
    }

    private class BackPressedCallback extends OnBackPressedCallback {
        public BackPressedCallback(boolean enabled) {
            super(enabled);
        }

        @Override
        public void handleOnBackPressed() {
            if (previewState) {
                // In preview mode, return to main activity
                finish();
            } else {
                // In edit mode, return to preview mode
                appBarLayout.setExpanded(true, true);

                // Refresh display tag to show any changes
                viewModel.refreshDisplayTag();
            }
        }
    }

    class OffSetChangeListener implements AppBarLayout.OnOffsetChangedListener {
        double prevScrollOffset = -1;
        // Reuse these objects to avoid GC pressure
        private final ArgbEvaluator argbEvaluator = new ArgbEvaluator();
        private ObjectAnimator tabColorAnimation;
        // Track state to avoid redundant updates
        private boolean wasFullyExpanded = true;
        private boolean wasFullyCollapsed = false;

        @Override
        public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
            double vScrollOffset = Math.abs(verticalOffset);
            // Only continue if there's an actual change
            if(vScrollOffset == prevScrollOffset) return;
            prevScrollOffset = vScrollOffset;

            double scrollRatio = 1.0 / appBarLayout.getTotalScrollRange() * vScrollOffset;

            // Scale cover art
            double scale = (1 - (scrollRatio * 0.2));
            coverArtView.setScaleX((float) scale);
            coverArtView.setScaleY((float) scale);

            // Fade toolbar title
            fadeToolbarTitle(scrollRatio);

            // Fully expanded state
            boolean isFullyExpanded = verticalOffset == 0;
            if (isFullyExpanded && !wasFullyExpanded) {
                // State change: fully EXPANDED
                wasFullyExpanded = true;
                wasFullyCollapsed = false;
                previewState = true;
                setupMenuToolbar();

                // No need to rebuild the display tag if it's not dirty
                viewModel.refreshDisplayTag();
            }
            // Fully collapsed state
            else if (Math.abs(verticalOffset) == appBarLayout.getTotalScrollRange() && !wasFullyCollapsed) {
                // State change: fully COLLAPSED
                wasFullyCollapsed = true;
                wasFullyExpanded = false;
                previewState = false;
                setupMenuToolbar();
            }

            // Handle tab layout color fade based on scroll position
            if (scrollRatio >= 0.8 && tabColorAnimation == null) {
                // Animate to toolbar color only when needed
                tabColorAnimation = ObjectAnimator.ofObject(
                        tabLayout,
                        "backgroundColor",
                        argbEvaluator,
                        ContextCompat.getColor(getApplicationContext(), R.color.bgColor),
                        toolbar_to_color);
                tabColorAnimation.setDuration(200); // Shorter duration for better performance
                tabColorAnimation.start();
            } else if (scrollRatio < 0.8 && tabColorAnimation == null) {
                // Animate back to background color
                tabColorAnimation = ObjectAnimator.ofObject(
                        tabLayout,
                        "backgroundColor",
                        argbEvaluator,
                        //tabLayout.getBackground() != null ?
                        //        tabLayout.getBackground() :
                                Color.TRANSPARENT,
                        ContextCompat.getColor(getApplicationContext(), R.color.bgColor));
                tabColorAnimation.setDuration(200);
                tabColorAnimation.start();
            }

            // Clean up animation reference when done
            if (tabColorAnimation != null && tabColorAnimation.isRunning()) {
                tabColorAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        tabColorAnimation = null;
                    }
                });
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Handle the back button click
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private Toolbar.OnMenuItemClickListener getOnMenuItemClickListener() {
            return item -> {
                if (item.getItemId() == R.id.menu_preview_calculate_dr) {
                    doMeasureDR();
                }
                return false;
            };
    }

    private void setupMenuToolbar() {
        if (previewState) {
            setupMenuPreview(getOnMenuItemClickListener());
        }else if(activeFragment!= null) {
            if (activeFragment instanceof TagsEditorFragment) {
                setupMenuEditor(((TagsEditorFragment) activeFragment).getOnMenuItemClickListener());
            } else if (activeFragment instanceof TagsTechnicalFragment) {
                setupMenuTechnical(((TagsTechnicalFragment) activeFragment).getOnMenuItemClickListener());
            }
        }
    }

    private void fadeToolbarTitle(double scale) {
        if (toolbar != null) {
            for (int i = 0; i < toolbar.getChildCount(); i++) {
                if (toolbar.getChildAt(i) instanceof TextView title) {
                    //You now have the title textView. Do something with it
                    title.setAlpha((float) scale);
                }
            }
        }
    }

    /**
     * Starts the progress bar with animation
     */
    public void startProgressBar() {
        runOnUiThread(() -> {
            try {
                if (progressDialog != null) {
                    progressDialog.dismiss();
                }

                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
                View v = getLayoutInflater().inflate(R.layout.animated_progress_dialog_layout, null);
                progressLabel = v.findViewById(R.id.process_label);

                // Get the animation view
                //LottieAnimationView animationView = v.findViewById(R.id.lottie_animation);
                // Ensure animation is playing
               // animationView.playAnimation();

                dialogBuilder.setView(v);
                dialogBuilder.setCancelable(true);
                progressDialog = dialogBuilder.create();
                progressDialog.setCanceledOnTouchOutside(true);

                if(progressDialog.getWindow() != null) {
                    progressDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

                    // Add fade-in animation for the dialog
                    Window window = progressDialog.getWindow();
                    window.setWindowAnimations(R.style.DialogAnimation);
                }

                // Show the dialog with animation
                progressDialog.show();

                // Add additional entrance animation for content
                View dialogContent = v.findViewById(R.id.lottie_animation);
                if (dialogContent != null) {
                    dialogContent.setAlpha(0f);
                    dialogContent.animate()
                            .alpha(1f)
                            .setDuration(400)
                            .setInterpolator(new AccelerateDecelerateInterpolator())
                            .start();
                }

            } catch (Exception ex) {
                Log.e(TAG, "startProgressBar", ex);
            }
        });
    }

    /**
     * Updates the progress bar text with animation and throttling to prevent UI overload
     * @param label Text to display in the progress bar
     */
    public void updateProgressBar(final String label) {
        // Check if enough time has passed since the last update to avoid too many UI updates
        long now = System.currentTimeMillis();
        long lastUpdate = lastProgressUpdate.get();
        if (now - lastUpdate < PROGRESS_UPDATE_THROTTLE_MS) {
            return; // Skip this update to avoid overloading the UI thread
        }

        // Try to update the timestamp - if another thread beat us, return
        if (!lastProgressUpdate.compareAndSet(lastUpdate, now)) {
            return;
        }

        runOnUiThread(() -> {
            if(progressDialog != null && progressLabel != null && !isFinishing()) {
                // Animate text change
                progressLabel.setAlpha(0.5f);
                progressLabel.animate()
                        .alpha(1f)
                        .setDuration(300)
                        .start();

                progressLabel.setText(label);
            }
        });
    }

    /**
     * Stops the progress bar with exit animation
     */
    public void stopProgressBar() {
        if(progressDialog != null) {
            runOnUiThread(() -> {
                try {
                    // Add exit animation
                    View dialogView = progressDialog.findViewById(R.id.lottie_animation);
                    if (dialogView != null) {
                        dialogView.animate()
                                .alpha(0f)
                                .setDuration(300)
                                .withEndAction(() -> {
                                    try {
                                        progressDialog.dismiss();
                                        progressDialog = null;
                                    } catch (Exception ignored) {}
                                })
                                .start();
                    } else {
                        progressDialog.dismiss();
                        progressDialog = null;
                    }
                } catch (Exception ignored) {}
            });
        }
    }
}
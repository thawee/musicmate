package apincer.android.mmate.ui;

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
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import apincer.android.mmate.Constants;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.Settings;
import apincer.android.mmate.R;
import apincer.android.mmate.codec.TagWriter;
import apincer.android.mmate.notification.AudioTagEditEvent;
import apincer.android.mmate.notification.AudioTagEditResultEvent;
import apincer.android.mmate.notification.AudioTagPlayingEvent;
import apincer.android.mmate.provider.IconProviders;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicAnalyser;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.TagRepository;
import apincer.android.mmate.repository.SearchCriteria;
import apincer.android.mmate.provider.CoverartFetcher;
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

    private static final Object editItemsLock = new Object();
    private static final ArrayList<MusicTag> editItems = new ArrayList<>();
    private volatile MusicTag displayTag;
    private ImageView coverArtView;
   // private ImageView reflectionView;
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
   // private TextView drView;
    private TextView fileTypeView;
    private ImageView hiresView;
   // private ImageView audiophileView;
    private ImageView resolutionView;
    private ImageView qualityView;

    private int toolbar_from_color;
    private int toolbar_to_color;
    FileRepository repos;
    private Fragment activeFragment;

    private ImageView playerBtn;
    private View playerPanel;
    private boolean closePreview = true;

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
        stopProgressBar();
    }

    private void doMeasureDR() {
        CompletableFuture.runAsync(
                () -> {
                    startProgressBar();
                    for(MusicTag tag:this.getEditItems()) {
                        try {
                            if (MusicAnalyser.analyse(tag)) {
                                TagWriter.writeTagToFile(getApplicationContext(), tag);
                                TagRepository.saveTag(tag);
                            }
                        }catch (Exception ex) {
                            Log.e(TAG, "doMeasureDR", ex);
                        }
                    }

                    displayTag = buildDisplayTag();
                    runOnUiThread(this::updateTitlePanel);
                    stopProgressBar();
                }
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopProgressBar();
    }
    @Override
    protected void onResume() {
        super.onResume();
        if(getEditItems()==null) {
            Intent myIntent = new Intent(TagsActivity.this, MainActivity.class);
            startActivity(myIntent);
        }
        if(MusixMateApp.getPlayerControl().isPlaying() && getEditItems().size() == 1) {
            if(MusixMateApp.getPlayerControl().getPlayingSong().equals(getEditItems().get(0))) {
                closePreview = false;
            }
        }
    }

    private TextView tagInfo;
    private SearchCriteria criteria;

    public List<MusicTag> getEditItems() {
        synchronized (editItemsLock) {
            return new ArrayList<>(editItems); // Return a copy to prevent concurrent modification
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if(Settings.isOnNightModeOnly(getApplicationContext())) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM); //must place before super.onCreate();
        }
        super.onCreate(savedInstanceState);

        // set status bar color to black
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(this, android.R.color.black));

        setContentView(R.layout.activity_tags);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        // Your business logic to handle the back pressed event
        OnBackPressedCallback onBackPressedCallback = new TagsActivity.BackPressedCallback(true);
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);

        repos = FileRepository.newInstance(getApplicationContext());

        coverArtView = findViewById(R.id.panel_cover_art);
        CollapsingToolbarLayout toolBarLayout = findViewById(R.id.toolbar_layout);
        int statusBarHeight = getStatusBarHeight();
        int height = UIUtils.getScreenHeight(this);
        toolBarLayout.getLayoutParams().height = height + statusBarHeight + 70;
        toolbar_from_color = ContextCompat.getColor(getApplicationContext(), apincer.android.library.R.color.colorPrimary);
        toolbar_to_color = ContextCompat.getColor(getApplicationContext(), apincer.android.library.R.color.colorPrimary);
        StateView mStateView = findViewById(R.id.status_page);
        mStateView.hideStates();
        setUpTitlePanel();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        setupMenuToolbar();
        return true;
    }

    @Subscribe(threadMode = ThreadMode.MAIN,sticky = true)
    public void onMessageEvent(AudioTagEditEvent event) {
        // call from EventBus on preview/edit selected tags from main screen
        try {
            criteria = event.getSearchCriteria();

            // Process data on a background thread
            CompletableFuture.runAsync(() -> {
                synchronized (editItemsLock) {
                    editItems.clear();
                    editItems.addAll(event.getItems());
                    displayTagDirty = true;
                    displayTag = buildDisplayTag();
                }

                if(displayTag != null) {
                    runOnUiThread(() -> {
                        updateTitlePanel();
                        setUpPageViewer();
                        stopProgressBar();
                    });
                }
            }, MusicMateExecutors.getExecutorService()).exceptionally(ex -> {
                Log.e(TAG, "Error processing event", ex);
                stopProgressBar();
                return null;
            });
        } catch (Exception e) {
            Log.e(TAG, "onMessageEvent", e);
            stopProgressBar();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN,sticky = true)
    public void onMessageEvent(AudioTagPlayingEvent event) {
        finishOnTimeout = false;
        // call from now playing listener
        if(MusixMateApp.getPlayerControl().isPlaying()) {
            playerBtn.setVisibility(View.VISIBLE);
            if (!closePreview) {
                MusicTag tag = event.getPlayingSong();
                if(tag != null) {
                    TagRepository.load(tag);
                    updatePreview(tag);
                    stopProgressBar();
                }
            }
        }
    }

    private void updatePreview(MusicTag playingSong) {
        try {
            if (playingSong == null) return;

            MusicTag tagCopy = playingSong.copy(); // Work with a copy

            synchronized (editItemsLock) {
                editItems.clear();
                editItems.add(tagCopy);
                displayTag = buildDisplayTag();
            }

            if (displayTag != null) {
                runOnUiThread(() -> {
                    updateTitlePanel();
                    setUpPageViewer();
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "updatePreview", e);
        }
    }

    private void setUpPageViewer() {
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
        findViewById(R.id.btnDelete).setOnClickListener(v -> doDeleteMediaItem());
        findViewById(R.id.btnMDR).setOnClickListener(v -> doMeasureDR());
        findViewById(R.id.btnImport).setOnClickListener(v -> doMoveMediaItem());
        findViewById(R.id.btnAspect).setOnClickListener(view -> ApplicationUtils.startAspect(this, displayTag));
        findViewById(R.id.btnWebSearch).setOnClickListener(view -> ApplicationUtils.webSearch(this,displayTag));
        findViewById(R.id.btnExplorer).setOnClickListener(view -> ApplicationUtils.startFileExplorer(this,displayTag));
    }

    private void setUpTitlePanel() {
        titleView = findViewById(R.id.panel_title);
        artistView = findViewById(R.id.panel_artist);
        albumView = findViewById(R.id.panel_album);
        genreView = findViewById(R.id.panel_genre);
        encInfo = findViewById(R.id.panel_enc);
        pathInfo = findViewById(R.id.panel_path);
        pathInfoLine = findViewById(R.id.panel_path_line);
        tagInfo = findViewById(R.id.panel_tag);
        hiresView = findViewById(R.id.icon_hires);
      //  audiophileView = findViewById(R.id.icon_audiophile);
        resolutionView = findViewById(R.id.icon_resolution);
       // drView = findViewById(R.id.icon_dr);
        qualityView = findViewById(R.id.icon_quality);

        fileTypeView = findViewById(R.id.icon_file_type);
        playerBtn = findViewById(R.id.music_player);
        playerPanel = findViewById(R.id.music_player_panel);
        playerPanel.setOnClickListener(view -> {
            startProgressBar();
           // closePreview = false;
            playerPanel.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.shape_play_next_background_refresh));
            MusixMateApp.getPlayerControl().playNextSong(getApplicationContext());
        });
        playerPanel.setOnLongClickListener(view -> {
            closePreview = !closePreview;
            if(closePreview) {
                playerPanel.setBackground(ContextCompat.getDrawable(getApplicationContext(), apincer.android.library.R.drawable.bg_transparent));
            }else {
                playerPanel.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.shape_play_next_background_refresh));
            }
            return true;
        });
    }

    protected void updateTitlePanel() {
        if(MusixMateApp.getPlayerControl().isPlaying()) {
            playerBtn.setVisibility(View.VISIBLE);
            playerPanel.setVisibility(View.VISIBLE);
            playerBtn.setBackground(MusixMateApp.getPlayerControl().getPlayerInfo().getPlayerIconDrawable());
            if(closePreview) {
                playerPanel.setBackground(ContextCompat.getDrawable(getApplicationContext(), apincer.android.library.R.drawable.bg_transparent));
            }else {
                playerPanel.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.shape_play_next_background_refresh));
            }
        }else {
            playerBtn.setVisibility(View.GONE);
            playerPanel.setVisibility(View.GONE);
        }
        if(getEditItems().size()>1) {
            String title = getString(R.string.title_many, getEditItems().size());
            titleView.setText(title);
        }else {
            titleView.setText(trim(displayTag.getTitle(), " - "));
        }
        artistView.setText(trim(displayTag.getArtist(), " - "));

        // load resolution, quality, coverArt
        loadImages(displayTag);

        //  qualityView.setImageBitmap(IconProviders.createQualityIcon(getApplicationContext(), displayTag));

        if(MusicTagUtils.isDSD(displayTag) || MusicTagUtils.isHiRes(displayTag)) {
            hiresView.setVisibility(View.VISIBLE);
        }else {
            hiresView.setVisibility(View.GONE);
        }

        Drawable resolutionBackground = IconProviders.getFileFormatBackground(getApplicationContext(), displayTag);
        fileTypeView.setBackground(resolutionBackground);
        fileTypeView.setText(trimToEmpty(displayTag.getAudioEncoding()).toUpperCase(Locale.US));

        artistView.setPaintFlags(artistView.getPaintFlags()| Paint.UNDERLINE_TEXT_FLAG);
        artistView.setOnClickListener(view -> {
            //filter by artist
            if(criteria!=null) {
                criteria.setFilterType(Constants.FILTER_TYPE_ARTIST);
                criteria.setFilterText(displayTag.getArtist());
            }
            doOpenMainActivity(criteria);
        });
        if(isEmpty(displayTag.getAlbum())) {
            albumView.setText(String.format("[%s]", MusicTagUtils.getDefaultAlbum(displayTag)));
        }else {
            albumView.setText(displayTag.getAlbum());
            albumView.setPaintFlags(albumView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            albumView.setOnClickListener(view -> {
                // filter by album
                if (criteria != null) {
                    criteria.setFilterType(Constants.FILTER_TYPE_ALBUM);
                    criteria.setFilterText(displayTag.getAlbum());
                }
                doOpenMainActivity(criteria);
            });
        }
        String mediaTypeAndPublisher;
        if((!isEmpty(displayTag.getAlbumArtist()))) {
            mediaTypeAndPublisher = " " +displayTag.getAlbumArtist()+" ";
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
                    if(!isEmpty(displayTag.getGenre())) {
                        if (criteria != null) {
                            criteria.setFilterType(Constants.FILTER_TYPE_GENRE);
                            criteria.setFilterText(trimToEmpty(displayTag.getGenre()));
                        }
                        doOpenMainActivity(criteria);
                    }
                }).setNormalTextColor(linkNorTextColor).setPressBgColor(linkPressBgColor),
                new SpecialTextUnit(isEmpty(displayTag.getGenre())?Constants.UNKNOWN:displayTag.getGenre()).setTextSize(14).useTextBold().showUnderline());

        tagSpan.append(new SpecialTextUnit(StringUtils.SYMBOL_SEP).setTextSize(14).useTextBold());
        tagSpan.appendMultiClickable(new SpecialClickableUnit(tagInfo, (tv, clickableSpan) -> {
                    if(criteria!=null) {
                        criteria.setFilterType(Constants.FILTER_TYPE_GROUPING);
                        criteria.setFilterText(trimToEmpty(displayTag.getGrouping()));
                    }
                    doOpenMainActivity(criteria);
                }).setNormalTextColor(linkNorTextColor).setPressBgColor(linkPressBgColor),
                new SpecialTextUnit(isEmpty(displayTag.getGrouping())?" - ":displayTag.getGrouping()).setTextSize(14).useTextBold().showUnderline());

        tagSpan.append(new SpecialTextUnit(StringUtils.SYMBOL_SEP).setTextSize(14).useTextBold());
        tagInfo.setText(tagSpan.build());

        // Path Info
        String simplePath = displayTag.getSimpleName();
        if(simplePath.contains("/")) {
            simplePath = simplePath.substring(0, simplePath.lastIndexOf("/"));
        }
        boolean inLibrary = MusicTagUtils.isManagedInLibrary(getApplicationContext(), displayTag);
       // String matePath = repos.buildCollectionPath(displayTag, true);
        if(inLibrary) {
            pathInfoLine.setBackgroundColor(getColor(R.color.grey400));
        }else {
            pathInfoLine.setBackgroundColor(getColor(R.color.warn_not_in_managed_dir));
        }
        pathInfo.setText(simplePath);

        pathInfo.setOnClickListener(view -> {
            if (criteria != null && displayTag.getPath() != null) {
                criteria.setFilterType(Constants.FILTER_TYPE_PATH);
                File file = new File(displayTag.getPath());
                if (!file.isDirectory()) {
                    file = file.getParentFile();
                }
                String filterPath = file.getAbsolutePath() + File.separator;
                criteria.setFilterText(filterPath);
                doOpenMainActivity(criteria);
            }
        });

        // ENC info
        try {
            int metaInfoTextSize = 12; //10
            int encColor = ContextCompat.getColor(getApplicationContext(), R.color.material_color_blue_grey_200);
            SimplifySpanBuild spannableEnc = new SimplifySpanBuild("");
           // spannableEnc.append(new SpecialTextUnit(StringUtils.SEP_LEFT,encColor));

            // bps
            spannableEnc.append(new SpecialTextUnit(StringUtils.formatAudioBitsDepth(displayTag.getAudioBitsDepth()), encColor).setTextSize(metaInfoTextSize));
            spannableEnc.append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP, encColor).setTextSize(metaInfoTextSize));
            spannableEnc.append(new SpecialTextUnit(StringUtils.formatAudioSampleRate(displayTag.getAudioSampleRate(), true), encColor).setTextSize(metaInfoTextSize));
            spannableEnc.append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP, encColor).setTextSize(metaInfoTextSize));

            spannableEnc.append(new SpecialTextUnit(StringUtils.formatAudioBitRate(displayTag.getAudioBitRate()),encColor).setTextSize(metaInfoTextSize));
            spannableEnc.append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP).setTextSize(metaInfoTextSize))
                   // .append(new SpecialTextUnit(StringUtils.formatDuration(displayTag.getAudioDuration(), true), encColor).setTextSize(metaInfoTextSize))
                    .append(new SpecialTextUnit(StringUtils.formatDurationAsMinute(displayTag.getAudioDuration()), encColor).setTextSize(metaInfoTextSize))

                    .append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP).setTextSize(metaInfoTextSize))
                    .append(new SpecialTextUnit(StringUtils.formatStorageSize(displayTag.getFileSize()), encColor).setTextSize(metaInfoTextSize));
                   // .append(new SpecialTextUnit(StringUtils.SEP_RIGHT, encColor));
            encInfo.setText(spannableEnc.build());
        }catch (Exception ex) {
            Log.e(TAG, "updateTitlePanel", ex);
        }
    }

    private void doOpenMainActivity(SearchCriteria criteria) {
      //  Intent myIntent = new Intent(this, MainActivity.class);
        if(criteria!=null) {
            MusixMateApp.getInstance().setSearchCriteria(criteria);
           // ApplicationUtils.setSearchCriteria(myIntent, criteria);
        }
        //startActivity(myIntent);
        finish();
    }

    // Add this field to track when display tag needs rebuilding
    private volatile boolean displayTagDirty = true;

    // Mark display tag as dirty whenever edit items change
    public void markDisplayTagDirty() {
        displayTagDirty = true;
    }

    // Create a separate method for image loading to reduce clutter
    private void loadImages(MusicTag displayTag) {
        // Load all images in parallel
        ImageLoader imageLoader = SingletonImageLoader.get(getApplicationContext());

        // Resolution icon
        ImageRequest resolutionRequest = new ImageRequest.Builder(getApplicationContext())
                .data(IconProviders.getResolutionIcon(getApplicationContext(), displayTag))
                .target(new ImageViewTarget(resolutionView))
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build();

        // Quality icon
        ImageRequest qualityRequest = new ImageRequest.Builder(getApplicationContext())
                .data(IconProviders.getTrackQualityIcon(getApplicationContext(), displayTag))
                .target(new ImageViewTarget(qualityView))
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build();

        // Cover art with higher priority
        ImageRequest coverRequest = CoverartFetcher.builder(getApplicationContext(), displayTag)
                .size(Size.ORIGINAL)
                .data(displayTag)
                .target(new ImageViewTarget(coverArtView))
                .memoryCachePolicy(CachePolicy.ENABLED)
                .error(imageRequest -> CoverartFetcher.getDefaultCover(getApplicationContext()))
                .build();

        // Enqueue all requests
        imageLoader.enqueue(resolutionRequest);
        imageLoader.enqueue(qualityRequest);
        imageLoader.enqueue(coverRequest);
    }

    protected MusicTag buildDisplayTag() {
        if(editItems.isEmpty()) return null;

        MusicTag displayTag = editItems.get(0);
        if(editItems.size()==1) {
            return displayTag.copy();
        }

        displayTag = displayTag.copy();
        for (int i=1;i<editItems.size();i++) {
            MusicTag item = editItems.get(i);
            if(!StringUtils.equals(displayTag.getTitle(), item.getTitle())) {
                displayTag.setTitle(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getTrack(), item.getTrack())) {
                displayTag.setTrack(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getAlbum(), item.getAlbum())) {
                displayTag.setAlbum(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getArtist(), item.getArtist())) {
                displayTag.setArtist(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getAlbumArtist(), item.getAlbumArtist())) {
                displayTag.setAlbumArtist(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getGenre(), item.getGenre())) {
                displayTag.setGenre(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getYear(), item.getYear())) {
                displayTag.setYear(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getDisc(), item.getDisc())) {
                displayTag.setDisc(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getComment(), item.getComment())) {
                displayTag.setComment(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getComposer(), item.getComposer())) {
                displayTag.setComposer(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getGrouping(), item.getGrouping())) {
                displayTag.setGrouping(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getMediaType(), item.getMediaType())) {
                displayTag.setMediaType(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getPublisher(), item.getPublisher())) {
                displayTag.setPublisher(StringUtils.MULTI_VALUES);
            }
        }
        return displayTag;
    }

    public int getStatusBarHeight() {
        int result = 0;
        @SuppressLint({"DiscouragedApi", "InternalInsetResource"}) int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    public void doDeleteMediaItem() {
        String text = "Delete ";
        if(editItems.size()>1) {
            text = text + editItems.size() + " songs?";
        }else {
            text = text + "'"+editItems.get(0).getTitle()+"' song?";
        }

        MaterialAlertDialogBuilder builder =  new MaterialAlertDialogBuilder(TagsActivity.this, R.style.AlertDialogTheme)
                .setIcon(R.drawable.ic_round_delete_forever_24)
                .setTitle("Delete Songs")
                .setMessage(text)
                .setPositiveButton("DELETE", (dialogInterface, i) -> {
                    startProgressBar();
                    if(getEditItems().size()==1) {
                        MusicMateExecutors.executeParallel(() -> {
                            try {
                                boolean status = repos.deleteMediaItem(getEditItems().get(0));
                                AudioTagEditResultEvent message = new AudioTagEditResultEvent(AudioTagEditResultEvent.ACTION_DELETE, status?Constants.STATUS_SUCCESS:Constants.STATUS_FAIL, getEditItems().get(0));
                                EventBus.getDefault().postSticky(message);
                            } catch (Exception e) {
                                Log.e(TAG, "doDeleteMediaItem", e);
                            }
                        });
                    }else {
                        DeleteAudioFileWorker.startWorker(getApplicationContext(), editItems);
                        dialogInterface.dismiss();
                        Intent resultIntent = new Intent();
                        if (criteria != null) {
                            ApplicationUtils.setSearchCriteria(resultIntent, criteria); //resultIntent.putExtra(Constants.KEY_SEARCH_CRITERIA, criteria);
                        }
                        setResult(RESULT_OK, resultIntent);
                    }


                    // set timeout to finish, 3 seconds
                    finishOnTimeout = true;
                    MusicMateExecutors.schedule(() -> {
                        if(finishOnTimeout) {
                            finish(); // back to prev activity
                        }
                        }, 3);
                })
                .setNeutralButton("CANCEL", (dialogInterface, i) -> dialogInterface.dismiss());
        AlertDialog alertDialog = builder.create();
        if(alertDialog.getWindow() != null) {
            alertDialog.getWindow().setGravity(Gravity.BOTTOM);
        }
        alertDialog.show();
    }

    public void doMoveMediaItem() {
        startProgressBar();
        ImportAudioFileWorker.startWorker(getApplicationContext(), editItems);
        Intent resultIntent = new Intent();
        if(criteria!=null) {
            ApplicationUtils.setSearchCriteria(resultIntent,criteria);
        }
        setResult(RESULT_OK, resultIntent);

            // set timeout to finish, 5 seconds
            finishOnTimeout = true;
            MusicMateExecutors.schedule(() -> {
                if(finishOnTimeout) {
                    finish(); // back to prev activity
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

    private class BackPressedCallback extends OnBackPressedCallback {
        public BackPressedCallback(boolean enabled) {
            super(enabled);
        }

        @Override
        public void handleOnBackPressed() {
            if (previewState) {
                // In preview mode, return to main activity
                if (criteria != null) {
                    Intent resultIntent = new Intent();
                    ApplicationUtils.setSearchCriteria(resultIntent, criteria);
                    setResult(RESULT_OK, resultIntent);
                }
                finish();
            } else {
                // In edit mode, return to preview mode
                appBarLayout.setExpanded(true, true);

                // Refresh display tag to show any changes
                markDisplayTagDirty();
                displayTag = buildDisplayTag();
                updateTitlePanel();
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
                if (displayTagDirty) {
                    displayTag = buildDisplayTag();
                }
                updateTitlePanel();
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
            if (criteria != null) {
                Intent resultIntent = new Intent();
                ApplicationUtils.setSearchCriteria(resultIntent, criteria);
                setResult(RESULT_OK, resultIntent);
            }
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
                LottieAnimationView animationView = v.findViewById(R.id.lottie_animation);
                // Ensure animation is playing
                animationView.playAnimation();

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
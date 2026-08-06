package apincer.android.mmate.ui;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static apincer.android.mmate.utils.UIUtils.dpToPx;
import static apincer.music.core.Constants.FLAC_BALANCE_COMPRESS_LEVEL;
import static apincer.music.core.Constants.FLAC_FAST_COMPRESS_LEVEL;
import static apincer.music.core.Constants.FLAC_MAXIMUM_COMPRESS_LEVEL;
import static apincer.music.core.Constants.KEY_FILTER_KEYWORD;
import static apincer.music.core.Constants.KEY_FILTER_TYPE;
import static apincer.music.core.utils.StringUtils.SYMBOL_ENC_SEP;
import static apincer.music.core.utils.StringUtils.isEmpty;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Bitmap;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import androidx.core.graphics.ColorUtils;
import androidx.palette.graphics.Palette;

import apincer.android.mmate.coil3.CoverartFetcher;
import apincer.android.mmate.ui.view.AudioHubBottomSheet;
import coil3.BitmapImage;
import coil3.Image;
import coil3.SingletonImageLoader;
import coil3.request.ImageRequest;
import coil3.target.Target;
import apincer.music.core.playback.PlaybackState;
import apincer.music.core.utils.PlayerNameUtils;
import apincer.android.utils.FileUtils;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.anggrayudi.storage.file.DocumentFileCompat;
import com.balsikandar.crashreporter.ui.CrashReporterActivity;
import com.developer.filepicker.model.DialogConfigs;
import com.developer.filepicker.model.DialogProperties;
import com.developer.filepicker.view.FilePickerDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;

import androidx.core.graphics.Insets;
import android.view.ViewGroup.MarginLayoutParams;
import apincer.android.mmate.R;
import apincer.android.mmate.service.MediaServerManager;
import apincer.android.mmate.service.MusicMateServiceImpl;
import apincer.android.mmate.utils.PermissionUtils;
import apincer.music.core.Constants;
import apincer.music.core.Settings;
import apincer.music.core.model.Track;
import apincer.music.core.playback.spi.PlaybackService;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.PlaylistRepository;
import apincer.music.core.model.SearchCriteria;
import apincer.music.core.model.SearchResultStats;
import apincer.music.core.repository.TagRepository;
import apincer.android.mmate.ui.view.BottomOffsetDecoration;


// import apincer.android.mmate.ui.widget.RatioSegmentedProgressBarDrawable;
import apincer.music.core.utils.ApplicationUtils;
import apincer.music.core.utils.StringUtils;
import apincer.android.mmate.utils.UIUtils;
import apincer.android.mmate.ui.viewmodel.MainViewModel;
import apincer.android.mmate.worker.FileOperationTask;
import apincer.android.mmate.worker.ScanAudioFileWorker;
import apincer.android.residemenu.ResideMenu;
import dagger.hilt.android.AndroidEntryPoint;
import me.stellarsand.android.fastscroll.FastScrollerBuilder;

/**
 * Main Activity for MusicMate application
 */
@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // Constants
    private static final int RECYCLEVIEW_ITEM_SCROLLING_OFFSET = 16;
    private static final int RECYCLEVIEW_ITEM_OFFSET = 48;
    private static final double MAX_PROGRESS_BLOCK = 10.00;
    private static final double MAX_PROGRESS = 100.00;

    // File format constants
    public static final String FILE_FLAC = "FLAC";
    public static final String FILE_AIFF = "AIFF";
    public static final String FILE_MP3 = "MP3";
    private static final String FILE_ALAC = "M4A";

    // Activity result launcher
    ActivityResultLauncher<Intent> tagViewResultLauncher;

    // ViewModel
    private MainViewModel viewModel;

    // UI components
    private ResideMenu mResideMenu;
    private MusicTagAdapter adapter;
    private SelectionTracker<Long> mTracker;
    private final List<Track> selections = new ArrayList<>();
    private View mHeaderPanel;
    private ImageView mBackButton;
    private SearchView headerSearchView;
    private TextView headerStatText;

    private RecyclerView mRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private WorkInfo.State lastWorkState = null;
    private View scanProgressDots;
    private android.animation.AnimatorSet dotAnimator = null;
    private View emptyStateView;
    private FloatingActionButton fabScrollToTop;

    private TextView nowPlayingLabel;
    private ImageView mBlurBackground;

    // Action mode
    private ActionModeCallback actionModeCallback;
    private ActionMode actionMode;

    // State variables
    private long lastScrollEventTime = 0;
    private boolean isScrollStoppingTouch = false;
    private volatile boolean busy;
    private Track previouslyPlaying;
    private PlaybackState lastPlaybackState;

    private PlaybackService playbackService;
    private boolean isPlaybackServiceBound = false;

    @Inject
    FileOperationTask operationTask;

    @Inject
    MediaServerManager mediaServerManager;

    private final android.os.Handler scrollHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable scrollRunnable;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @SuppressLint("CheckResult")
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // Get the binder from the service and set the mediaServerService instance
            MusicMateServiceImpl.MusicMateServiceImplBinder binder = (MusicMateServiceImpl.MusicMateServiceImplBinder) service;
            playbackService = binder.getPlaybackService();
            isPlaybackServiceBound = true;
            adapter.setPlaybackService(playbackService);
            playbackService.subscribePlaybackState(
                    playbackState -> setNowPlaying(playbackService.getNowPlayingSong(), playbackState),
                    throwable -> Log.e(TAG, "Error in PlaybackState subscription", throwable));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isPlaybackServiceBound = false;
            playbackService = null;
        }
    };

    private void setNowPlaying(Track song, PlaybackState playbackState) {
        if (song != null) {
            mRecyclerView.post(() -> {
                lastPlaybackState = playbackState;
                if(!song.equals(previouslyPlaying)) {
                    if (Settings.isListFollowNowPlaying(getBaseContext()) && (actionMode == null)) {
                        // only scrolled on first event for each song
                        if (scrollRunnable != null) {
                            scrollHandler.removeCallbacks(scrollRunnable);
                        }
                        scrollRunnable = () -> {
                            if (!busy) {
                                scrollToSong(song);
                            }
                        };
                        scrollHandler.postDelayed(scrollRunnable, 500); // 0.5 seconds
                    }

                    // refresh music list
                    adapter.notifyItemChanged(previouslyPlaying);
                    adapter.notifyItemChanged(song);
                }
                if (nowPlayingLabel != null) {
                    nowPlayingLabel.setText(R.string.app_name);
                }
                previouslyPlaying = song;
                updateGlassyPanelsColor(song);
                updateFloatingPlaybackBar(song, playbackState);
            });
        } else {
            mRecyclerView.post(() -> {
                if (nowPlayingLabel != null) {
                    nowPlayingLabel.setText(R.string.app_name);
                }
                lastPlaybackState = playbackState;
                previouslyPlaying = null;
                updateFloatingPlaybackBar(null, playbackState);
            });
        }
    }

    /** Builds a compact signal-quality label for the bottom nav bar, e.g. "FLAC · 352.8kHz / 24bit" */
    private String buildSignalSummary(Track song) {
        if (song == null) return "Awaiting sound";
        StringBuilder sb = new StringBuilder();

        String enc = song.getAudioEncoding();
        if (enc != null && !enc.isEmpty()) {
            sb.append(enc.toUpperCase(java.util.Locale.US));
        }

        long sr = song.getAudioSampleRate();
        if (sr > 0) {
            if (sb.length() > 0) sb.append(" · ");
            if (sr % 1000 == 0) {
                sb.append(sr / 1000).append(" kHz");
            } else {
                sb.append(String.format(java.util.Locale.US, "%.1f kHz", sr / 1000.0));
            }
        }

        int bits = song.getAudioBitsDepth();
        if (bits > 0) {
            sb.append(" / ").append(bits).append("bit");
        }

        return sb.length() > 0 ? sb.toString() : song.getAudioEncoding();
    }

    private void updateFloatingPlaybackBar(Track song, PlaybackState playbackState) {
        if (song != null && (actionMode == null)) {
            if (barTrackTitle != null) {
                barTrackTitle.setText(song.getTitle());
            }
            if (barTargetSubtitle != null) {
                barTargetSubtitle.setVisibility(View.VISIBLE);
                if (isPlaybackServiceBound && playbackService != null && playbackService.getPlayer() != null) {
                    barTargetSubtitle.setText(PlayerNameUtils.getDropdownPlayerLabel(playbackService.getPlayer()));
                } else {
                    barTargetSubtitle.setText(song.getArtist());
                }
            }
            if (barAlbumArt != null) {
                barAlbumArt.setVisibility(View.VISIBLE);
                ImageRequest request = CoverartFetcher.builder(this, song)
                        .data(song)
                        .size(240, 240)
                        .target(new coil3.target.ImageViewTarget(barAlbumArt))
                        .build();
                SingletonImageLoader.get(this).enqueue(request);
            }
        } else {
            if (barTrackTitle != null) {
                barTrackTitle.setText(R.string.app_name);
            }
            if (barTargetSubtitle != null) {
                barTargetSubtitle.setVisibility(View.VISIBLE);
                if (isPlaybackServiceBound && playbackService != null && playbackService.getPlayer() != null) {
                    barTargetSubtitle.setText(PlayerNameUtils.getDropdownPlayerLabel(playbackService.getPlayer()));
                } else {
                    barTargetSubtitle.setText("Select target player");
                }
            }
            if (barAlbumArt != null) {
                barAlbumArt.setVisibility(View.VISIBLE);
                barAlbumArt.setImageResource(R.drawable.ic_now_playing_idle);
            }
        }
    }

    private void updateGlassyPanelsColor(Track song) {
        ImageRequest request = CoverartFetcher.builder(this, song)
                .data(song)
                .size(300, 300) // Small size is fine for heavy blur
                .target(new Target() {
                    @Override
                    public void onStart(@Nullable Image placeholder) {}

                    @Override
                    public void onSuccess(@NonNull Image image) {
                        if (image instanceof BitmapImage) {
                            Bitmap bitmap = ((BitmapImage) image).getBitmap();
                            
                            // Apply to full screen background with heavy blur
                            if (mBlurBackground != null) {
                                mBlurBackground.setImageBitmap(bitmap);
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    mBlurBackground.setRenderEffect(
                                            RenderEffect.createBlurEffect(100f, 100f, Shader.TileMode.CLAMP)
                                    );
                                }
                            }

                            // Palette needs direct pixel access, so we cannot use hardware bitmaps.
                            // If the bitmap is hardware-accelerated, we must copy it to a software-compatible config.
                            Bitmap paletteBitmap = bitmap;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.getConfig() == Bitmap.Config.HARDWARE) {
                                paletteBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                            }

                            Palette.from(paletteBitmap).generate(palette -> {
                                if (palette != null) {
                                    int color = palette.getVibrantColor(palette.getMutedColor(Color.DKGRAY));
                                    applyGlassyColor(color);
                                }
                            });
                        }
                    }

                    @Override
                    public void onError(@Nullable Image errorDrawable) {}
                })
                .build();
        SingletonImageLoader.get(this).enqueue(request);
    }

    private void applyGlassyColor(int color) {
        int alphaColor = ColorUtils.setAlphaComponent(color, 64); // ~25% opacity for better glass effect
        if (mHeaderPanel != null && mHeaderPanel.getBackground() != null) {
            mHeaderPanel.getBackground().setTint(alphaColor);
            mHeaderPanel.getBackground().setTintMode(PorterDuff.Mode.SRC_ATOP);
        }
        View bottomNav = findViewById(R.id.bottom_navigation_container);
        if (bottomNav != null && bottomNav.getBackground() != null) {
            bottomNav.getBackground().setTint(alphaColor);
            bottomNav.getBackground().setTintMode(PorterDuff.Mode.SRC_ATOP);
        }
        if (fabScrollToTop != null && fabScrollToTop.getBackground() != null) {
            fabScrollToTop.getBackground().setTint(alphaColor);
            fabScrollToTop.getBackground().setTintMode(PorterDuff.Mode.SRC_ATOP);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Setup night mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);

        // Start the server here, where we are guaranteed to be in the foreground!
        mediaServerManager.startServer();

        // Enable Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        //Enable Dynamic Colors
        DynamicColors.applyToActivitiesIfAvailable(getApplication());

        tagViewResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == AppCompatActivity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null && data.hasExtra(KEY_FILTER_TYPE)) {
                            String filterType = data.getStringExtra(KEY_FILTER_TYPE);
                            String filterText = data.getStringExtra(KEY_FILTER_KEYWORD);
                            if (adapter.getCriteria() != null) {
                                adapter.getCriteria().setFilterType(filterType);
                                adapter.getCriteria().setFilterText(filterText);
                            }
                            viewModel.loadMusicItems(adapter.getCriteria());
                        } else {
                            viewModel.reloadMusicItems();
                        }
                    }
                });

        // Setup status bar
        Window window = getWindow();
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
        // If the background is dark, use light icons
        insetsController.setAppearanceLightStatusBars(false);

        // Get search criteria from intent
        SearchCriteria searchCriteria = ApplicationUtils.getSearchCriteria(getIntent());

        // Setup back press handler
        OnBackPressedCallback onBackPressedCallback = new BackPressedCallback(true);
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);

        // Set content view
        setContentView(R.layout.activity_main);

        // Get the ViewModel. Hilt handles all the factory creation for you.
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        // Setup UI components
        setupHeaderPanel();
        setupBottomAppBar();
        setupRecycleView(searchCriteria);
        setupResideMenus();

        // Observe ViewModel LiveData
        setupObserveViewModel();

        // load music items
        viewModel.loadMusicItems(adapter.getCriteria());

        // Bind to the MediaServerService as soon as this service is created
        Intent intent = new Intent(this, MusicMateServiceImpl.class);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    @SuppressLint("CheckResult")
    private void setupObserveViewModel() {
        viewModel.musicItems.observe(this, musicTags -> {
            mRecyclerView.post(() -> {
                // If user is in selection mode, preserve active selection keys
                List<Long> selectedPositions = null;
                if (actionMode != null && mTracker != null && mTracker.hasSelection()) {
                    selectedPositions = new ArrayList<>();
                    mTracker.getSelection().forEach(selectedPositions::add);
                }

                // Save layout manager state to restore scroll position
                android.os.Parcelable state = null;
                if (mRecyclerView.getLayoutManager() != null) {
                    state = mRecyclerView.getLayoutManager().onSaveInstanceState();
                }

                adapter.setMusicTags(musicTags);
                swipeRefreshLayout.setRefreshing(false);
                // Update header after adapter is populated; stats observer will correct later
                updateHeaderPanel(viewModel.searchStats.getValue());

                if (state != null && mRecyclerView.getLayoutManager() != null) {
                    mRecyclerView.getLayoutManager().onRestoreInstanceState(state);
                }

                if (selectedPositions != null && !selectedPositions.isEmpty() && mTracker != null) {
                    for (Long pos : selectedPositions) {
                        mTracker.select(pos);
                    }
                }
            });
            if (musicTags == null || musicTags.isEmpty()) {
                emptyStateView.setVisibility(View.VISIBLE);
                swipeRefreshLayout.setVisibility(View.GONE);
            } else {
                emptyStateView.setVisibility(View.GONE);
                swipeRefreshLayout.setVisibility(View.VISIBLE);
            }
        });

        // When DB aggregate stats arrive, refresh the subtitle with accurate totals
        viewModel.searchStats.observe(this, stats -> updateHeaderPanel(stats));

        viewModel.musicItemsLoading.observe(this, isLoading -> mRecyclerView.post(() -> swipeRefreshLayout.setRefreshing(isLoading)));

        WorkManager.getInstance(getApplicationContext())
                .getWorkInfosForUniqueWorkLiveData("MusicScanWork")
                .observe(this, workInfos -> {
                    if (workInfos != null && !workInfos.isEmpty()) {
                        WorkInfo workInfo = workInfos.get(0);
                        WorkInfo.State currentState = workInfo.getState();
                        boolean isRunning = currentState == WorkInfo.State.RUNNING;
                        if (isRunning) {
                            if (scanProgressDots != null) {
                                scanProgressDots.setVisibility(View.VISIBLE);
                                startDotAnimation();
                            }
                            int progress = workInfo.getProgress().getInt("progress_value", 0);
                            int total = workInfo.getProgress().getInt("total_files", 0);
                            if (total > 0) {
                                headerStatText.setText("Scanning: " + progress + "/" + total + " files");
                            } else {
                                headerStatText.setText("Scanning...");
                            }
                        } else if (currentState.isFinished()) {
                            if (scanProgressDots != null) {
                                scanProgressDots.setVisibility(View.GONE);
                                stopDotAnimation();
                            }
                            // Only trigger reload/ui update if we transitioned from RUNNING to a finished state
                            if (lastWorkState == WorkInfo.State.RUNNING) {
                                viewModel.loadMusicItems(adapter.getCriteria());
                            }
                        }
                        lastWorkState = currentState;
                    }
                });
    }

    private void setupHeaderPanel() {
        mBlurBackground = findViewById(R.id.main_background_blur);
        mHeaderPanel = findViewById(R.id.header_panel);
        mBackButton = findViewById(R.id.header_back_btn);
        headerSearchView = findViewById(R.id.search_view);
        headerStatText = findViewById(R.id.header_stats_text);

        // Handle Status Bar Insets for Header
        ViewCompat.setOnApplyWindowInsetsListener(mHeaderPanel, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), systemBars.top + (int)dpToPx(this, 8), 
                        v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        // Glassy effect is handled by semi-transparent background drawables
        // and dynamic tints in applyGlassyColor().
        // setRenderEffect is disabled here to keep text and icons sharp.

        setupSearchView();
        openSearch();
    }

    private void setupBottomAppBar() {
        // Find components
        View bottomNav = findViewById(R.id.bottom_navigation_container);
        // setSupportActionBar(bottomNav); // Removed as CardView is not a Toolbar

        // Handle Navigation Bar Insets for Bottom Capsule
        if (bottomNav != null) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomNav, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                if (v.getLayoutParams() instanceof MarginLayoutParams mlp) {
                    mlp.bottomMargin = systemBars.bottom + (int)dpToPx(this, 16);
                    v.setLayoutParams(mlp);
                }
                return insets;
            });
            bottomNav.setElevation(8f);
        }

        View leftMenu = findViewById(R.id.navigation_collections);
        ImageView rightMenu = findViewById(R.id.navigation_settings);

        // Setup menu click listeners
        leftMenu.setOnClickListener(v -> doShowLeftMenus());
        rightMenu.setOnClickListener(v -> doShowRightMenus());

        setupFloatingPlaybackBar();
    }

    private View floatingPlaybackBar;
    private ImageView barAlbumArt;
    private TextView barTrackTitle;
    private TextView barTargetSubtitle;

    private void setupFloatingPlaybackBar() {
        floatingPlaybackBar = findViewById(R.id.docked_playback_bar);
        if (floatingPlaybackBar == null) return;

        barAlbumArt = findViewById(R.id.bar_album_art);
        barTrackTitle = findViewById(R.id.bar_track_title);
        barTargetSubtitle = findViewById(R.id.bar_target_subtitle);

        View titleContainer = findViewById(R.id.bar_title_container);
        View.OnClickListener openNowPlayingListener = v -> {
            AudioHubBottomSheet sheet = AudioHubBottomSheet.newInstance(AudioHubBottomSheet.TAB_NOW_PLAYING);
            sheet.show(getSupportFragmentManager(), AudioHubBottomSheet.TAG);
        };

        if (titleContainer != null) {
            titleContainer.setOnClickListener(openNowPlayingListener);
            titleContainer.setOnLongClickListener(v -> {
                doShowSignalPath();
                return true;
            });
        }

        if (barAlbumArt != null) {
            barAlbumArt.setOnClickListener(openNowPlayingListener);
        }
    }

    public PlaybackState getLastPlaybackState() {
        return lastPlaybackState;
    }

    public MusicTagAdapter getAdapter() {
        return adapter;
    }

    public void doShowSignalPath() {

        if(playbackService != null && playbackService.getPlayer() != null) {
            if(!playbackService.getPlayer().isStreaming()) {
                playbackService.switchPlayer(playbackService.getPlayer(), true);
            }
        }

        AudioHubBottomSheet bottomSheet = AudioHubBottomSheet.newInstance(AudioHubBottomSheet.TAB_SIGNAL_PATH);
        bottomSheet.show(getSupportFragmentManager(), AudioHubBottomSheet.TAG);
    }

    private void setupRecycleView(SearchCriteria searchCriteria) {
        if (searchCriteria == null) {
            searchCriteria = new SearchCriteria(SearchCriteria.TYPE.LIBRARY);
        }

        // Initialize adapter
        adapter = new MusicTagAdapter(viewModel.getTagRepository(), searchCriteria);
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                updateHeaderPanel();
            }
        });

        // Setup RecyclerView
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        emptyStateView = findViewById(R.id.empty_state_view);
        scanProgressDots = findViewById(R.id.scan_progress_dots);
        int spinnerOffset = getResources().getDimensionPixelSize(R.dimen.dimen_56_dp); // Example offset
        swipeRefreshLayout.setProgressViewOffset(false, 0, spinnerOffset);

        // --- Set the listener ---
        swipeRefreshLayout.setOnRefreshListener(() -> {
            // This method is called when the user swipes
            // 1. Load your new data here (e.g., make a network call)
            viewModel.loadMusicItems();
        });

        fabScrollToTop = findViewById(R.id.fab_scroll_to_top);
        // Instant scroll for very long lists
        fabScrollToTop.setOnClickListener(v -> {
            mRecyclerView.stopScroll();
            mRecyclerView.scrollToPosition(0);
            mRecyclerView.postDelayed(() ->
                    mRecyclerView.smoothScrollBy(0, 0), 10);
        });

        ViewCompat.setOnApplyWindowInsetsListener(fabScrollToTop, (v, insets) -> {
            // Get the system bar insets (which include the navigation bar)
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;

            // Get the base margin you want from your dimensions
            int baseMargin = getResources().getDimensionPixelSize(R.dimen.dimen_64_dp);

            // Get the view's existing layout parameters
            ViewGroup.LayoutParams params = v.getLayoutParams();

            // Check if they are MarginLayoutParams (which they should be for a FAB)
            if (params instanceof ViewGroup.MarginLayoutParams marginParams) {

                // Set the new bottom margin by adding the base margin and the inset
                marginParams.bottomMargin = baseMargin + bottomInset;

                // Re-apply the updated layout parameters to the view
                v.setLayoutParams(marginParams);
            }

            // Return the insets so other views can consume them
            return insets;
        });

        mRecyclerView = findViewById(R.id.recycler_view);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setAdapter(adapter);
        //Tune performance
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setItemViewCacheSize(10);
        mRecyclerView.setItemAnimator(null);

        // Add bottom padding to ensure last items scroll cleanly above bottom navigation dock
        RecyclerView.ItemDecoration itemDecoration = new BottomOffsetDecoration((int)dpToPx(this, 96), 12);
        mRecyclerView.addItemDecoration(itemDecoration);
        mRecyclerView.setPreserveFocusAfterLayout(true);

        // add on item touch listener to detect and block touch selections that stop a fast move/scroll
        mRecyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                int action = e.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    if (rv.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
                        isScrollStoppingTouch = true;
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    rv.post(() -> isScrollStoppingTouch = false);
                }
                return false;
            }
        });

        // add on scroll listener
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (dy != 0 || dx != 0) {
                    lastScrollEventTime = SystemClock.elapsedRealtime();
                }

                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) return;

                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                // --- FAB behavior ---
                if (dy > 0) {
                    // Scrolling down (list moving up)
                    if (fabScrollToTop.isShown()) {
                        fabScrollToTop.hide();
                    }
                } else if (dy < 0) {
                    // Scrolling up (list moving down)
                    // Only show if we're not at the very top
                    if (firstVisibleItemPosition > 0 && !fabScrollToTop.isShown()) {
                        fabScrollToTop.show();
                    }
                }

                // Safety check: Always hide if we're at the top
                if (firstVisibleItemPosition == 0 && fabScrollToTop.isShown()) {
                    fabScrollToTop.hide();
                }

                // --- Pagination ---
                if (dy > 0) { // scrolling down
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    
                    // Trigger load more when we're close to the bottom (e.g. within 10 items)
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 10
                            && firstVisibleItemPosition >= 0) {
                        viewModel.loadMoreMusicItems();
                    }
                }
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                    // We are DRAGGING (state 1) or SETTLING (state 2).
                    // Update the timestamp *every time* the state changes to non-idle.
                    lastScrollEventTime = SystemClock.elapsedRealtime();
                } else {
                    // We are IDLE (state 0).
                    // Update the timestamp ONE LAST TIME as we become idle.
                    // This ensures the guard is active for the next 500ms.
                    lastScrollEventTime = SystemClock.elapsedRealtime();
                }
            }
        });

        MusicTagAdapter.OnListItemClick onListItemClick = (view, position) -> {
            if (isSelectionBlocked()) return;

            if (mTracker != null && mTracker.hasSelection()) {
                if (mTracker.isSelected((long) position)) {
                    mTracker.deselect((long) position);
                } else {
                    mTracker.select((long) position);
                }
                return;
            }

            Track tag = adapter.getMusicTag(position);
            if(tag == null) return;

            if(tag.isContainer()) {
                doStartRefresh(tag.getContainerType(), tag.getTitle());
            } else {
                doShowEditActivity(Collections.singletonList(tag));
            }
        };
        adapter.setClickListener(onListItemClick);
        adapter.setOnCoverArtClickListener((view, position) -> {
            if (isSelectionBlocked()) return;

            if (mTracker != null && mTracker.hasSelection()) {
                if (mTracker.isSelected((long) position)) {
                    mTracker.deselect((long) position);
                } else {
                    mTracker.select((long) position);
                }
                return;
            }

            Track tag = adapter.getMusicTag(position);
            if (tag != null && !tag.isContainer()) {
                if (isPlaybackServiceBound && playbackService != null) {
                    playbackService.playSong(tag);
                } else {
                    doShowEditActivity(Collections.singletonList(tag));
                }
            }
        });

            // Setup selection tracker
            mTracker = new SelectionTracker.Builder<>(
                    "selection-id",
                    mRecyclerView,
                    new MusicTagAdapter.KeyProvider(),
                    new MusicTagAdapter.DetailsLookup(mRecyclerView),
                    StorageStrategy.createLongStorage())
                    .withSelectionPredicate(new MusicTrackSelectionPredicate(adapter))
                    .build();
            adapter.injectTracker(mTracker);

            // Setup selection observer
            SelectionTracker.SelectionObserver<Long> observer = new SelectionTracker.SelectionObserver<>() {
                @Override
                public void onSelectionChanged() {
                        int count = mTracker.getSelection().size();
                        selections.clear();
                        if (count > 0) {
                            mTracker.getSelection().forEach(item -> {
                                Track tag = adapter.getMusicTag(item.intValue());
                                if (tag != null) {
                                    selections.add(tag);
                                }
                            });
                            if (actionMode == null) {
                                actionMode = startSupportActionMode(actionModeCallback);
                            }
                        } else if (actionMode != null) {
                            actionMode.finish();
                            return;
                        }
                        if (actionMode != null) {
                            actionMode.setTitle(StringUtils.formatSongSize(count));
                            actionMode.invalidate();
                        }
                }
            };
            mTracker.addObserver(observer);

        // Setup fast scroller
        new FastScrollerBuilder(mRecyclerView)
                .useMd1Style()
                .setPadding(0, 0, 8, 0)
                //.setThumbDrawable(Objects.requireNonNull(
                //        ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_fastscroll_thumb)))
                .setPopupTextProvider((view, position) -> {
                    Track track = adapter.getMusicTag(position);
                    if(track != null && !isEmpty(track.getTitle())) return track.getTitle().subSequence(0,1);
                    return "-";
                })
                .build();

        // Initialize action mode callback
        actionModeCallback = new ActionModeCallback();
    }

    private void setupResideMenus() {
        // Attach to current activity
        mResideMenu = new ResideMenu(this);
        mResideMenu.setBackground(R.drawable.bg);
        mResideMenu.attachToActivity(this);
        mResideMenu.setScaleValue(0.54f);
        mResideMenu.setSwipeDirectionDisable(ResideMenu.DIRECTION_LEFT);
        mResideMenu.setSwipeDirectionDisable(ResideMenu.DIRECTION_RIGHT);
        mResideMenu.setOnMenuItemClickListener(item -> {
            onOptionsItemSelected(item);
            mResideMenu.closeMenu();
        });

        // Create menus
        mResideMenu.setMenuRes(R.menu.menu_music_mate, ResideMenu.DIRECTION_RIGHT);
        mResideMenu.setMenuRes(R.menu.menu_music_collection, ResideMenu.DIRECTION_LEFT);
    }

    private void doShowLeftMenus() {
        if (Settings.isShowStorageSpace(getApplicationContext())) {
            @SuppressLint("InflateParams") View storageView = getLayoutInflater().inflate(R.layout.view_header_left_menu, null);
            // Explicitly set layout params because inflating with null root discards them
            android.widget.RelativeLayout.LayoutParams params = new android.widget.RelativeLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            int marginPx = (int) (12 * getResources().getDisplayMetrics().density);
            params.setMargins(marginPx, marginPx, marginPx, marginPx);
            storageView.setLayoutParams(params);

            LinearLayout panel = storageView.findViewById(R.id.storage_bar);
            TextView totalSongText = storageView.findViewById(R.id.header_total_songs);
            TextView totalDurationText = storageView.findViewById(R.id.header_total_duration);

            // Re-use the already-computed SearchResultStats from the ViewModel (avoids a redundant DB query)
            SearchResultStats stats = viewModel.searchStats.getValue();
            long songCount = (stats != null) ? stats.getTotalCount() : 0;
            double totalDuration = (stats != null) ? stats.getTotalDuration() : 0;

            totalSongText.setText(StringUtils.formatSongSize(songCount));
            totalDurationText.setText(StringUtils.formatDuration(totalDuration, true));
            UIUtils.buildStoragesStatus(getApplication(), panel);

            mResideMenu.setLeftHeader(storageView);
        }
        mResideMenu.openMenu(ResideMenu.DIRECTION_LEFT);
    }

    private void doShowRightMenus() {
        mResideMenu.openMenu(ResideMenu.DIRECTION_RIGHT);
    }

    private void updateHeaderPanel() {
        updateHeaderPanel(null);
    }

    private void updateHeaderPanel(SearchResultStats stats) {
        SearchCriteria.TYPE type = adapter.getCriteria().getType();
        Drawable icon = ContextCompat.getDrawable(getBaseContext(), R.drawable.bg_transparent);

        // Prefer DB aggregate stats for accurate category-wide totals.
        // Fall back to adapter counts if stats not yet available (e.g. initial load).
        // For top-level category directories (where keyword is empty and type is not LIBRARY),
        // we display the category count itself, which is the total items in the adapter.
        boolean isTopLevelCategoryDir = isEmpty(adapter.getCriteria().getKeyword())
                && !SearchCriteria.TYPE.LIBRARY.equals(type);
        boolean hasActiveFilter = adapter.hasFilter();
        int count = (isTopLevelCategoryDir || hasActiveFilter) ? adapter.getTotalItems()
                : ((stats != null) ? stats.getTotalCount() : adapter.getTotalItems());
        long totalSize = hasActiveFilter ? adapter.getTotalSize() : ((stats != null) ? stats.getTotalSize() : adapter.getTotalSize());
        double totalDuration = hasActiveFilter ? adapter.getTotalDuration() : ((stats != null) ? stats.getTotalDuration() : adapter.getTotalDuration());

        String statText = "";
        if(!isEmpty(adapter.getCriteria().getKeyword())) {
            // total songs
            if(count > 0) {
                statText = StringUtils.formatSongSize(count) + " Songs";
            }

            // filter details or duration
            if(isEmpty(adapter.getCriteria().getFilterType()) && count > 0) {
                statText = statText + SYMBOL_ENC_SEP + StringUtils.formatStorageSize(totalSize) + SYMBOL_ENC_SEP + StringUtils.formatDuration(totalDuration, true);
            } else {
                String filterText = adapter.getCriteria().getFilterText();
                if ("Folder".equals(adapter.getCriteria().getFilterType())) {
                    filterText = StringUtils.truncate(DocumentFileCompat.getBasePath(getApplicationContext(), filterText), 38, StringUtils.TruncateType.PREFIX);
                } else {
                    filterText = StringUtils.truncate(filterText, 38, StringUtils.TruncateType.SUFFIX);
                }
                if(!isEmpty(filterText)) {
                    statText = statText + " · [" + filterText + "]";
                }
            }

            // can back to higher category, except type library
            if(SearchCriteria.TYPE.LIBRARY.equals(type)){
                mBackButton.setImageDrawable(icon);
                mHeaderPanel.setOnClickListener(null);
            } else {
                mBackButton.setImageDrawable(ContextCompat.getDrawable(getBaseContext(), R.drawable.rounded_arrow_shape_up_24));
                mHeaderPanel.setOnClickListener(view -> {
                    adapter.resetFilter();
                    adapter.getCriteria().setKeyword(null);
                    viewModel.loadMusicItems(adapter.getCriteria());
                });
            }
        } else {
            // top-level category: show total count + category label
            if(count > 0) {
                statText = StringUtils.formatSongSize(count) + " " + StringUtils.formatTitle(adapter.getHeaderLabel());
                // Also show total storage + duration for the all-songs view (Library)
                if (stats != null && SearchCriteria.TYPE.LIBRARY.equals(type) && isEmpty(adapter.getCriteria().getFilterType())) {
                    statText = statText + SYMBOL_ENC_SEP + StringUtils.formatStorageSize(totalSize) + SYMBOL_ENC_SEP + StringUtils.formatDuration(totalDuration, true);
                }
            }

            // Allow back navigation to Library (All Songs) from top-level category lists (e.g. Codecs, Artists, Genres)
            if(SearchCriteria.TYPE.LIBRARY.equals(type)){
                mBackButton.setImageDrawable(icon);
                mHeaderPanel.setOnClickListener(null);
            } else {
                mBackButton.setImageDrawable(ContextCompat.getDrawable(getBaseContext(), R.drawable.rounded_arrow_shape_up_24));
                mHeaderPanel.setOnClickListener(view -> {
                    adapter.resetFilter();
                    adapter.search("");
                    doStartRefresh(SearchCriteria.TYPE.LIBRARY, Constants.TITLE_ALL_SONGS);
                });
            }
        }

        headerSearchView.setQueryHint("Search " + StringUtils.truncate(adapter.getHeaderTitle(), 25, StringUtils.TruncateType.SUFFIX));
        headerStatText.setText(statText);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mResideMenu.isOpened()) {
            mResideMenu.closeMenu();
        }
        if(headerSearchView != null) {
            headerSearchView.clearFocus();
        }

       // viewModel.getTagRepository().cleanInvalidTags();
        // load music items
       // viewModel.loadMusicItems(adapter.getCriteria());
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopDotAnimation();
        if(isPlaybackServiceBound) {
            unbindService(serviceConnection);
        }
        super.onDestroy();
    }

    public void scrollToSong(Track currentlyPlaying) {
        if (currentlyPlaying == null || (actionMode != null)) return;

        viewModel.loadUntilFound(currentlyPlaying, () -> {
            mRecyclerView.post(() -> {
                int positionToScroll = adapter.getMusicTagPosition(currentlyPlaying);
                if (positionToScroll != RecyclerView.NO_POSITION) {
                    scrollToPosition(positionToScroll);
                }
            });
        });
    }

    private void scrollToPosition(int position) {
        if (position != RecyclerView.NO_POSITION) {
            int positionWithOffset = position - RECYCLEVIEW_ITEM_SCROLLING_OFFSET;
            if (positionWithOffset < 0) {
                positionWithOffset = 0;
            }
            mRecyclerView.scrollToPosition(positionWithOffset);
            if (position - 1 > RecyclerView.NO_POSITION) {
                // show as 2nd item on screen
                position = position - 1;
            }
            LinearLayoutManager layoutManager = (LinearLayoutManager) mRecyclerView.getLayoutManager();
            Objects.requireNonNull(layoutManager).scrollToPositionWithOffset(position, RECYCLEVIEW_ITEM_OFFSET);
        }
    }

    private void doHideSearch() {
        adapter.search("");
        viewModel.loadMusicItems(adapter.getCriteria());
    }

    private void doStartRefresh(SearchCriteria.TYPE type, String keyword) {
        adapter.setType(type);
        adapter.setKeyword(keyword);
       // folderAdapter.refresh();
        viewModel.loadMusicItems(adapter.getCriteria());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
       /* } else if (item.getItemId() == R.id.menu_all_music) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.LIBRARY, null);
            return true; */
        } else if (item.getItemId() == R.id.menu_library_all_songs) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.LIBRARY, Constants.TITLE_ALL_SONGS);
            return true;
        } else if (item.getItemId() == R.id.menu_library_recently_added) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.LIBRARY, Constants.TITLE_INCOMING_SONGS);
            return true;
        } else if (item.getItemId() == R.id.menu_library_similar_songs) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.LIBRARY, Constants.TITLE_DUPLICATE);
            return true;
        } else if (item.getItemId() == R.id.menu_sound_grade) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.SOUND_GRADE, null);
            return true;
        } else if (item.getItemId() == R.id.menu_collection) {
            doHideSearch();
            PlaylistRepository.loadPlaylists(getApplicationContext());
            doStartRefresh(SearchCriteria.TYPE.PLAYLIST, null);
            return true;
        /*} else if (item.getItemId() == R.id.menu_groupings) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.GROUPING, null);
            return true; */
        } else if (item.getItemId() == R.id.menu_tag_genre) {
            doHideSearch();
            //doStartRefresh(SearchCriteria.TYPE.GENRE, viewModel.getTagRepository().getActualGenreList().get(0));
            doStartRefresh(SearchCriteria.TYPE.GENRE, null);
            return true;
        } else if (item.getItemId() == R.id.menu_tag_artist) {
            doHideSearch();
            //doStartRefresh(SearchCriteria.TYPE.ARTIST, TagRepository.getArtistList().get(0));
            doStartRefresh(SearchCriteria.TYPE.ARTIST, null);
            return true;
        } else if (item.getItemId() == R.id.menu_settings) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (item.getItemId() == R.id.menu_files_permission) {
            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivity(intent);
            return true;
        } else if (item.getItemId() == R.id.menu_notification_access) {
            Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
            return true;
        } else if (item.getItemId() == R.id.menu_about_music_mate) {
            doShowAboutApp();
            return true;
        } else if (item.getItemId() == R.id.navigation_settings) {
            doShowRightMenus();
            return true;
        } else if (item.getItemId() == R.id.menu_directories) {
            doScanDirectories();
            return true;
        } else if (item.getItemId() == R.id.menu_about_crash) {
            Intent intent = new Intent(MainActivity.this, CrashReporterActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void doManageMediaServer() {
        AudioHubBottomSheet sheet = AudioHubBottomSheet.newInstance(AudioHubBottomSheet.TAB_MEDIA_SERVER);
        sheet.show(getSupportFragmentManager(), AudioHubBottomSheet.TAG);
    }

    public void showPlayerPickerPopup(View anchorView) {
        if (!isPlaybackServiceBound || playbackService == null) return;

        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, anchorView, android.view.Gravity.END);
        List<apincer.music.core.playback.spi.PlaybackTarget> renderers = playbackService.getPlaybackTargets();
        apincer.music.core.playback.spi.PlaybackTarget current = playbackService.getPlayer();

        if (renderers != null && !renderers.isEmpty()) {
            for (int i = 0; i < renderers.size(); i++) {
                apincer.music.core.playback.spi.PlaybackTarget target = renderers.get(i);
                boolean isActive = current != null && current.getTargetId().equals(target.getTargetId());
                boolean isRemote = target.isStreaming();
                String baseLabel = apincer.music.core.utils.PlayerNameUtils.getDropdownPlayerLabel(target);
                String label = isActive ? baseLabel + "  ✓" : baseLabel;
                android.view.MenuItem item = popup.getMenu().add(0, i, i, label);

                if (target instanceof apincer.music.core.playback.ExternalAndroidPlayer) {
                    android.graphics.drawable.Drawable appIcon = apincer.music.core.playback.ExternalAndroidPlayer.Factory.getAppIcon(this, target.getTargetId());
                    if (appIcon != null) {
                        item.setIcon(apincer.android.mmate.utils.UIUtils.scaleDrawable(this, appIcon, 24));
                    } else {
                        item.setIcon(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_round_speaker_24));
                    }
                } else if (isRemote || target instanceof apincer.music.core.playback.DMRPlayer) {
                    item.setIcon(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_dlna));
                } else {
                    item.setIcon(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_round_speaker_24));
                }
            }
        } else {
            popup.getMenu().add(0, -1, 0, "No players discovered");
        }

        // Always show rescan option at the bottom
        final int RESCAN_ID = 9999;
        android.view.MenuItem rescanItem = popup.getMenu().add(1, RESCAN_ID, RESCAN_ID, "Rescan for players");
        rescanItem.setIcon(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_baseline_refresh_24));

        apincer.android.mmate.utils.UIUtils.makePopForceShowIcon(popup);

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == RESCAN_ID) {
                // Trigger UPnP M-SEARCH (MX=5)
                playbackService.refreshPlayerDiscovery();
                // Give immediate feedback
                android.widget.Toast.makeText(this, "🔄 Scanning for players…", android.widget.Toast.LENGTH_SHORT).show();
                // Poll every 500ms until we find players (or 6s elapses = 12 attempts)
                if (anchorView != null) {
                    final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                    final int[] attempts = {0};
                    final int MAX_ATTEMPTS = 12;
                    final Runnable[] poll = {null};
                    poll[0] = () -> {
                        attempts[0]++;
                        List<apincer.music.core.playback.spi.PlaybackTarget> found =
                                playbackService.getPlaybackTargets();
                        boolean hasRemote = found != null && found.stream()
                                .anyMatch(apincer.music.core.playback.spi.PlaybackTarget::isStreaming);
                        if (hasRemote || attempts[0] >= MAX_ATTEMPTS) {
                            showPlayerPickerPopup(anchorView);
                        } else {
                            handler.postDelayed(poll[0], 500);
                        }
                    };
                    handler.postDelayed(poll[0], 500);
                }
                return true;
            }
            if (renderers != null && item.getItemId() >= 0 && item.getItemId() < renderers.size()) {
                apincer.music.core.playback.spi.PlaybackTarget selectedPlayer = renderers.get(item.getItemId());
                playbackService.switchPlayer(selectedPlayer, true);
                androidx.fragment.app.Fragment sheet = getSupportFragmentManager().findFragmentByTag(AudioHubBottomSheet.TAG);
                if (sheet instanceof AudioHubBottomSheet audioHub) {
                    audioHub.refreshUI();
                }
            }
            return true;
        });

        popup.show();
    }

    private void doShowAboutApp() {
        AboutActivity.showAbout(this);
    }

    @SuppressLint("SetTextI18n")
    private void doScanDirectories() {
        if (!PermissionUtils.checkAccessPermissions(getApplicationContext())) {
            Intent intent = new Intent(MainActivity.this, PermissionActivity.class);
            startActivity(intent);
            return;
        }

        View cview = getLayoutInflater().inflate(R.layout.view_action_directories, null);

        ListView itemsView = cview.findViewById(R.id.itemListView);
        LinearLayout btnAddPanel = cview.findViewById(R.id.btn_add_panel);
        View btnOK = cview.findViewById(R.id.button_ok);
        CheckBox checkboxFullScan = cview.findViewById(R.id.checkbox_full_scan);
       // View btnOKFull = cview.findViewById(R.id.button_ok_full);
        View btnCancel = cview.findViewById(R.id.btn_close);

        List<String> defaultPaths = FileRepository.getDefaultMusicPaths(this);
        Set<String> defaultPathsSet = new HashSet<>(defaultPaths);
        List<String> dirs = TagRepository.getDirectories(this);

        itemsView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return dirs.size();
            }

            @Override
            public Object getItem(int i) {
                return null;
            }

            @Override
            public long getItemId(int i) {
                return 0;
            }

            @SuppressLint("InflateParams")
            @Override
            public View getView(int i, View view, ViewGroup viewGroup) {
                if (view == null) {
                    view = getLayoutInflater().inflate(R.layout.view_action_listview_item, null);
                }

                String dir = dirs.get(i);
                TextView seq = view.findViewById(R.id.seq);
                TextView name = view.findViewById(R.id.name);
                TextView status = view.findViewById(R.id.status);

                seq.setText(String.valueOf(i + 1));
                name.setText(dir);

                if (defaultPathsSet.contains(dir)) {
                    status.setText("");
                } else {
                    status.setText("X");
                    status.setOnClickListener(view1 -> {
                        dirs.remove(dir);
                        notifyDataSetChanged();
                    });
                }

                return view;
            }
        });

        AlertDialog alert = new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setTitle("")
                .setView(cview)
                .setCancelable(true)
                .create();

        alert.requestWindowFeature(Window.FEATURE_NO_TITLE);
        alert.setCanceledOnTouchOutside(false);

        // Make popup round corners
        if (alert.getWindow() != null) {
            alert.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        List<String> storageIds = DocumentFileCompat.getStorageIds(getApplicationContext());
        btnAddPanel.removeAllViews();

        for (String sid : storageIds) {
            Button btn = new Button(getApplicationContext());
            btn.setText("+"+ StringUtils.capitalize(sid));
            btn.setAllCaps(false);
            btnAddPanel.addView(btn);

            btn.setOnClickListener(view -> {
                DialogProperties properties = new DialogProperties();
                properties.selection_mode = DialogConfigs.SINGLE_MODE;
                properties.selection_type = DialogConfigs.DIR_SELECT;
                properties.root = new File(DocumentFileCompat.buildAbsolutePath(getApplicationContext(), sid, ""));
                properties.extensions = null;
                properties.show_hidden_files = false;

                FilePickerDialog dialog = new FilePickerDialog(MainActivity.this, properties);
                dialog.setTitle("Select music Directory");
                dialog.setPositiveBtnName("Add");
                dialog.setNegativeBtnName("Cancel");
                dialog.setDialogSelectionListener(files -> {
                    dirs.add(files[0]);
                    ((BaseAdapter) itemsView.getAdapter()).notifyDataSetChanged();
                });
                dialog.show();
            });
        }

        btnOK.setOnClickListener(v -> {
            Settings.setDirectories(getApplicationContext(), dirs);
            Log.i(TAG, "Starting scan music file.");
            boolean isFullScan = checkboxFullScan.isChecked();
            if (isFullScan) {
                new MaterialAlertDialogBuilder(MainActivity.this, R.style.AlertDialogTheme)
                        .setTitle("Full Rescan")
                        .setMessage(getString(R.string.directories_confirm_full_scan))
                        .setPositiveButton("Start", (dialog, which) -> {
                            ScanAudioFileWorker.startScan(getApplicationContext(), true);
                            alert.dismiss();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                ScanAudioFileWorker.startScan(getApplicationContext(), false);
                alert.dismiss();
            }
        });

        btnCancel.setOnClickListener(v -> alert.dismiss());
        alert.show();
    }

    private void doShowEditActivity(List<Track> selections) {
        ArrayList<Track> tagList = new ArrayList<>();

        for (Track tag : selections) {
            if (FileRepository.isMediaFileExist(tag)) {
                tagList.add(tag);
            } else {
                new MaterialAlertDialogBuilder(MainActivity.this, R.style.AlertDialogTheme)
                        .setTitle("Problem")
                        .setMessage(getString(R.string.alert_invalid_media_file, tag==null?" - ":tag.getPath()))
                        .setPositiveButton("GOT IT", (dialogInterface, i) -> {
                            if(isPlaybackServiceBound) {
                                playbackService.playSong(tag);
                            }
                            viewModel.deleteMediaTag(tag);
                           // repos.deleteMediaItem(tag);
                            viewModel.loadMusicItems(adapter.getCriteria());
                            dialogInterface.dismiss();
                        })
                        .show();
            }
        }

        if (!tagList.isEmpty()) {
            long[] tagIds = new long[tagList.size()];
            for (int i = 0; i < tagList.size(); i++) {
                tagIds[i] = tagList.get(i).getId();
            }

            Intent intent = new Intent(MainActivity.this, TagsActivity.class);
            intent.putExtra("MUSIC_TAG_IDS", tagIds);
            tagViewResultLauncher.launch(intent);
        }
    }

    private void openSearch() {

        // Show the SearchView
        headerSearchView.setVisibility(View.VISIBLE);

    }

    private void setupSearchView() {
        // This is the main listener for handling text changes and search submissions
        headerSearchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                // User pressed the search button on the keyboard
                viewModel.search(adapter, query);
                headerSearchView.clearFocus(); // Hide keyboard
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // User is typing
                viewModel.search(adapter, newText);
                return true;
            }
        });

        // This listener handles the "X" (close) button inside the SearchView
        headerSearchView.setOnCloseListener(() -> {
            // Note: This only fires if the search view is set to be iconified,
            // which ours is not. We'll manually handle the 'X' button.
            return false;
        });

        // Manually handle the "X" button click
        ImageView closeButton = headerSearchView.findViewById(R.id.search_close_btn);
        closeButton.setOnClickListener(v -> {
            headerSearchView.setQuery("", false); // Clear the text
            // You might also want to close the whole search bar here:
            // closeSearch();
        });
    }

    private void doDeleteMediaItems(List<Track> selections) {
        if (selections.isEmpty()) return;

        View cview = getLayoutInflater().inflate(R.layout.view_action_files, null);

        Map<Track, String> statusList = new HashMap<>();
        ListView itemsView = cview.findViewById(R.id.itemListView);
        ImageView titleIcon = cview.findViewById(R.id.title_icon);
        TextView titleText = cview.findViewById(R.id.title);
        TextView fileListTitleText = cview.findViewById(R.id.file_list_title);
        titleText.setText(R.string.title_removing_music_files);
        fileListTitleText.setText(R.string.files_to_delete);
        titleIcon.setImageDrawable(AppCompatResources.getDrawable(getApplicationContext(), R.drawable.rounded_delete_24));

        itemsView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return selections.size();
            }

            @Override
            public Object getItem(int i) {
                return null;
            }

            @Override
            public long getItemId(int i) {
                return 0;
            }

            @SuppressLint("InflateParams")
            @Override
            public View getView(int i, View view, ViewGroup viewGroup) {
                if (view == null) {
                    view = getLayoutInflater().inflate(R.layout.view_action_listview_item, null);
                }

                Track tag = selections.get(i);
                TextView seq = view.findViewById(R.id.seq);
                TextView name = view.findViewById(R.id.name);
                TextView status = view.findViewById(R.id.status);

                seq.setText(String.valueOf(i + 1));
                status.setText(statusList.getOrDefault(tag, "-"));
                name.setText(getTrackDisplayName(tag));

                return view;
            }
        });

        MaterialButton btnOK = cview.findViewById(R.id.button_ok);
        View btnCancel = cview.findViewById(R.id.btn_close);
        ProgressBar progressBar = cview.findViewById(R.id.progressBar);
        btnOK.setEnabled(true);
        btnOK.setText(R.string.move_to_trash);

        double block = Math.min(selections.size(), MAX_PROGRESS_BLOCK);
        double sizeInBlock = MAX_PROGRESS / block;
        List<Long> valueList = new ArrayList<>();

        for (int i = 0; i < block; i++) {
            valueList.add((long) sizeInBlock);
        }

      //  final double rate = 100.00 / selections.size();
        int barColor = getColor(R.color.material_color_green_400);
        // progressBar.setProgressDrawable(new RatioSegmentedProgressBarDrawable(barColor, Color.GRAY, valueList, 8f));
        progressBar.setMax((int) MAX_PROGRESS);

        AlertDialog alert = new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setTitle("")
                .setView(cview)
                .setCancelable(true)
                .create();

        alert.requestWindowFeature(Window.FEATURE_NO_TITLE);
        alert.setCanceledOnTouchOutside(false);

        if (alert.getWindow() != null) {
            alert.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnOK.setOnClickListener(v -> {
            busy = true;
            btnOK.setEnabled(false);
           // btnOK.setVisibility(GONE);

            progressBar.setProgress(FileOperationTask.getInitialProgress(selections.size()));

            operationTask.deleteFiles(getApplicationContext(), selections,
                    new FileOperationTask.ProgressCallback() {
                        @Override
                        public void onProgress(Track tag, int progress, String status) {
                            runOnUiThread(() -> {
                                statusList.put(tag, status);
                                itemsView.invalidateViews();
                                progressBar.setProgress(progress);
                                progressBar.invalidate();
                                if ("Deleted".equalsIgnoreCase(status) && isPlaybackServiceBound && playbackService != null) {
                                    playbackService.onTrackDeleted(tag);
                                }
                            });
                        }

                        @Override
                        public void onComplete() {
                            runOnUiThread(() -> {
                                viewModel.loadMusicItems();
                                busy = false;
                            });
                        }
                    });
        });

        btnCancel.setOnClickListener(v -> {
            alert.dismiss();
            busy = false;
        });

        alert.show();
    }

    private void doMoveMediaItems(List<Track> selections) {
        if (selections.isEmpty()) return;

        View cview = getLayoutInflater().inflate(R.layout.view_action_files, null);

        Map<Track, String> statusList = new HashMap<>();
        ListView itemsView = cview.findViewById(R.id.itemListView);
        TextView titleText = cview.findViewById(R.id.title);
        ImageView titleIcon = cview.findViewById(R.id.title_icon);
        TextView fileListTitleText = cview.findViewById(R.id.file_list_title);
        titleText.setText(R.string.title_import_to_music_directory);
        fileListTitleText.setText(R.string.files_to_move);
        titleIcon.setImageDrawable(AppCompatResources.getDrawable(getApplicationContext(), R.drawable.rounded_drive_file_move_24));

        itemsView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return selections.size();
            }

            @Override
            public Object getItem(int i) {
                return null;
            }

            @Override
            public long getItemId(int i) {
                return 0;
            }

            @SuppressLint("InflateParams")
            @Override
            public View getView(int i, View view, ViewGroup viewGroup) {
                if (view == null) {
                    view = getLayoutInflater().inflate(R.layout.view_action_listview_item, null);
                }

                Track tag = selections.get(i);
                TextView seq = view.findViewById(R.id.seq);
                TextView name = view.findViewById(R.id.name);
                TextView status = view.findViewById(R.id.status);

                seq.setText(String.valueOf(i + 1));
                status.setText(statusList.getOrDefault(tag, "-"));
                name.setText(getTrackDisplayName(tag));

                return view;
            }
        });

        TextView btnOK = cview.findViewById(R.id.button_ok);
        View btnCancel = cview.findViewById(R.id.btn_close);
        ProgressBar progressBar = cview.findViewById(R.id.progressBar);
        btnOK.setEnabled(true);
        btnOK.setText(R.string.move_to_music);

        double block = Math.min(selections.size(), MAX_PROGRESS_BLOCK);
        double sizeInBlock = MAX_PROGRESS / block;
        List<Long> valueList = new ArrayList<>();

        for (int i = 0; i < block; i++) {
            valueList.add((long) sizeInBlock);
        }

      //  final double rate = 100.00 / selections.size();
        int barColor = getColor(R.color.material_color_green_400);
        // progressBar.setProgressDrawable(new RatioSegmentedProgressBarDrawable(barColor, Color.GRAY, valueList, 8f));
        progressBar.setMax((int) MAX_PROGRESS);

        AlertDialog alert = new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setTitle("")
                .setView(cview)
                .setCancelable(true)
                .create();

        alert.requestWindowFeature(Window.FEATURE_NO_TITLE);
        alert.setCanceledOnTouchOutside(false);

        if (alert.getWindow() != null) {
            alert.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnOK.setOnClickListener(v -> {
            busy = true;
            btnOK.setEnabled(false);
           // btnOK.setVisibility(GONE);

            progressBar.setProgress(FileOperationTask.getInitialProgress(selections.size()));

            operationTask.moveFiles(getApplicationContext(), selections,
                    new FileOperationTask.ProgressCallback() {
                        @Override
                        public void onProgress(Track tag, int progress, String status) {
                            runOnUiThread(() -> {
                                statusList.put(tag, status);
                                itemsView.invalidateViews();
                                progressBar.setProgress(progress);
                                progressBar.invalidate();
                            });
                        }

                        @Override
                        public void onComplete() {
                            // Call ViewModel method after operation is completed
                            runOnUiThread(() -> {
                                viewModel.loadMusicItems();
                                busy = false;
                            });
                        }
                    });
        });

        btnCancel.setOnClickListener(v -> {
            alert.dismiss();
            busy = false;
        });

        alert.show();
    }

    private void doEncodeAudioFiles(List<Track> selections) {
        if (selections.isEmpty()) return;

        View cview = getLayoutInflater().inflate(R.layout.view_action_encoding_files, null);

        Map<Track, String> statusList = new HashMap<>();
        ListView itemsView = cview.findViewById(R.id.itemListView);
        AutoCompleteTextView outputFormat = cview.findViewById(R.id.output_format);
        AutoCompleteTextView sampleRateView = cview.findViewById(R.id.sample_rate);
        MaterialButton btnOK = cview.findViewById(R.id.button_encode_file);
        View btnCancel = cview.findViewById(R.id.btn_close);
        ImageView titleIcon = cview.findViewById(R.id.title_icon);
        if (titleIcon != null) {
            titleIcon.setImageDrawable(AppCompatResources.getDrawable(getApplicationContext(), R.drawable.rounded_swap_horiz_24));
        }
        ProgressBar progressBar = cview.findViewById(R.id.progressBar);

        btnOK.setText(R.string.convert);

        String[] outputFormatList = getResources().getStringArray(R.array.output_formats);
        setupListValuePopupFullList(outputFormat, Arrays.asList(outputFormatList));
        outputFormat.setText(outputFormatList[0]); // default to FLAC (Balanced)

        String[] sampleRateList = getResources().getStringArray(R.array.sample_rates);
        setupListValuePopupFullList(sampleRateView, Arrays.asList(sampleRateList));
        sampleRateView.setText(sampleRateList[0]); // default to Original (No Resampling)

        itemsView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return selections.size();
            }

            @Override
            public Object getItem(int i) {
                return null;
            }

            @Override
            public long getItemId(int i) {
                return 0;
            }

            @SuppressLint("InflateParams")
            @Override
            public View getView(int i, View view, ViewGroup viewGroup) {
                if (view == null) {
                    view = getLayoutInflater().inflate(R.layout.view_action_listview_item, null);
                }

                Track tag = selections.get(i);
                TextView seq = view.findViewById(R.id.seq);
                TextView name = view.findViewById(R.id.name);
                TextView status = view.findViewById(R.id.status);

                seq.setText(String.valueOf(i + 1));
                status.setText(statusList.getOrDefault(tag, "-"));
                name.setText(getTrackDisplayName(tag));

                return view;
            }
        });

        double block = Math.min(selections.size(), MAX_PROGRESS_BLOCK);
        double sizeInBlock = MAX_PROGRESS / block;
        List<Long> valueList = new ArrayList<>();

        for (int i = 0; i < block; i++) {
            valueList.add((long) sizeInBlock);
        }

       // final double rate = 100.00 / selections.size();
        int barColor = getColor(R.color.material_color_green_400);
        // progressBar.setProgressDrawable(new RatioSegmentedProgressBarDrawable(barColor, Color.GRAY, valueList, 8f));
        progressBar.setMax((int) MAX_PROGRESS);

        AlertDialog alert = new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setTitle("")
                .setView(cview)
                .setCancelable(true)
                .create();

        alert.requestWindowFeature(Window.FEATURE_NO_TITLE);
        alert.setCanceledOnTouchOutside(false);

        if (alert.getWindow() != null) {
            alert.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnOK.setOnClickListener(v -> {
            busy = true;
            btnOK.setEnabled(false);
           // btnOK.setVisibility(GONE);

            progressBar.setProgress(FileOperationTask.getInitialProgress(selections.size()));
            int compressionLevel = FLAC_BALANCE_COMPRESS_LEVEL;
            String targetExt;
            String selectedFormat = outputFormat.getText().toString();
            if(selectedFormat.contains(".aiff")) {
                targetExt = FILE_AIFF;
            }else if(selectedFormat.contains(".mp3")) {
                targetExt = FILE_MP3;
            }else if(selectedFormat.contains(".m4a")) {
                targetExt = FILE_ALAC;
            }else {
                if(selectedFormat.contains("fast")) compressionLevel = FLAC_FAST_COMPRESS_LEVEL;
                else if(selectedFormat.contains("maximum")) compressionLevel = FLAC_MAXIMUM_COMPRESS_LEVEL;
                targetExt = FILE_FLAC;
            }

            int targetSampleRate = 0;
            String selectedSampleRateStr = sampleRateView != null ? sampleRateView.getText().toString() : "";
            if (selectedSampleRateStr.contains("96")) {
                targetSampleRate = 96000;
            } else if (selectedSampleRateStr.contains("48")) {
                targetSampleRate = 48000;
            } else if (selectedSampleRateStr.contains("44.1")) {
                targetSampleRate = 44100;
            }

            operationTask.encodeFiles(getApplicationContext(), selections, targetExt, compressionLevel, targetSampleRate,
                    new FileOperationTask.ProgressCallback() {
                        @Override
                        public void onProgress(Track tag, int progress, String status) {
                            runOnUiThread(() -> {
                                statusList.put(tag, status);
                                itemsView.invalidateViews();
                                progressBar.setProgress(progress);
                                progressBar.invalidate();
                            });
                        }

                        @Override
                        public void onComplete() {
                            runOnUiThread(() -> {
                                viewModel.loadMusicItems();
                                busy = false;
                            });
                        }
                    });
        });

        btnCancel.setOnClickListener(v -> {
            alert.dismiss();
            busy = false;
        });

        alert.show();
    }

    private String getTrackDisplayName(Track tag) {
        if (tag == null) return "";
        if (!isEmpty(tag.getSimpleName())) {
            return tag.getSimpleName();
        }
        if (!isEmpty(tag.getTitle())) {
            return tag.getTitle();
        }
        if (!isEmpty(tag.getPath())) {
            return FileUtils.getFileName(tag.getPath());
        }
        return "";
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupListValuePopupFullList(AutoCompleteTextView input, List<String> dropdownList) {
        NoFilterArrayAdapter<String> adapter = new NoFilterArrayAdapter<>(
                this,
                R.layout.item_dropdown_dark,
                dropdownList
        );
        input.setAdapter(adapter);
        input.setThreshold(0);

        // Disable keyboard input — dropdown only
        input.setKeyListener(null);
        input.setFocusable(false);
        input.setClickable(true);

        // Always open dropdown when clicked
        input.setOnClickListener(v -> input.showDropDown());

        // Optional: dark popup background
        input.setDropDownBackgroundResource(R.color.black_transparent_64);
    }

    private boolean isSelectionBlocked() {
        return mRecyclerView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE 
                || (SystemClock.elapsedRealtime() - lastScrollEventTime < 500)
                || isScrollStoppingTouch;
    }

    // You can put this class inside your Activity/Fragment
    private class MusicTrackSelectionPredicate extends SelectionTracker.SelectionPredicate<Long> {

        private final MusicTagAdapter adapter;

        // Pass in your adapter so the predicate can look up items
        MusicTrackSelectionPredicate(@NonNull MusicTagAdapter adapter) {
            this.adapter = adapter;
        }

        /**
         * This is the main method that prevents selection.
         * It's called for both touch and programmatic selection.
         */
        @Override
        public boolean canSetStateForKey(@NonNull Long key, boolean nextState) {
            if (isSelectionBlocked()) {
                return false;
            }

            // We assume the 'key' is the position, based on your observer code.
            int position = key.intValue();

            if (position < 0 || position >= adapter.getItemCount()) {
                return false; // Safety check for invalid positions
            }

            // Get the item from the adapter.
            // NOTE: Make sure getMusicTag() returns the actual data object
            // (e.g., MusicFolder or MusicFile)
            Track item = adapter.getMusicTag(position);

            // If the item IS a MusicFolder, REJECT any state change.
            // This prevents it from being selected.
           // return !(item instanceof MusicFolder);
            return !item.isContainer();
        }

        /**
         * This method is called specifically for touch events.
         * We'll add the same logic here for safety.
         */
        @Override
        public boolean canSetStateAtPosition(int position, boolean nextState) {
            if (isSelectionBlocked()) {
                return false;
            }

            if (position < 0 || position >= adapter.getItemCount()) {
                return false;
            }

            Track item = adapter.getMusicTag(position);

            // REJECT state change for MusicFolder
            //return !(item instanceof MusicFolder);
            return !item.isContainer();
        }

        @Override
        public boolean canSelectMultiple() {
            // You still want to allow multi-select for the files
            return true;
        }
    }

    /**
     * Action Mode for handling contextual actions on selected items
     */
    private class ActionModeCallback implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.menu_main_actionmode, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            //mTitlePanel.setVisibility(GONE);
            mRecyclerView.setPadding(0, 0, 0, 0);
            mHeaderPanel.setVisibility(GONE);
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int id = item.getItemId();
           /* if (id == R.id.action_play_next) {
                List<Track> selectedSongs = getSelections();
                if (!selectedSongs.isEmpty() && isPlaybackServiceBound && playbackService != null) {
                    for (int i = selectedSongs.size() - 1; i >= 0; i--) {
                        playbackService.getQueueManager().addPlayNext(selectedSongs.get(i));
                    }
                    Toast.makeText(MainActivity.this, "Playing next: " + selectedSongs.size() + " track(s)", Toast.LENGTH_SHORT).show();
                }
                mode.finish();
                return true;
            } else*/
            if (id == R.id.action_add_queue) {
                List<Track> selectedSongs = getSelections();
                if (!selectedSongs.isEmpty() && isPlaybackServiceBound && playbackService != null) {
                    for (Track t : selectedSongs) {
                        playbackService.getQueueManager().addPlayingQueue(t.getId());
                    }
                }
                mode.finish();
                return true;
            } else if (id == R.id.action_delete) {
                doDeleteMediaItems(getSelections());
                mode.finish();
                return true;
            } else if (id == R.id.action_transfer_file) {
                doMoveMediaItems(getSelections());
                mode.finish();
                return true;
            } else if (id == R.id.action_edit_metadata) {
                doShowEditActivity(getSelections());
                mode.finish();
                return true;
            } else if (id == R.id.action_encoding_file) {
                doEncodeAudioFiles(getSelections());
                mode.finish();
                return true;
            /*} else if (id == R.id.action_measure_dr) {
                doMeasureDR(getSelections());
                mode.finish();
                return true; */
            } else if (id == R.id.action_select_all) {
                if (mTracker.getSelection().size() == adapter.getItemCount()) {
                    // Selected all, reset selection
                    mTracker.clearSelection();
                } else {
                    // Select all items
                    for (int i = 0; i < adapter.getItemCount(); i++) {
                        mTracker.select((long) i);
                    }
                }
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mTracker.clearSelection();
            actionMode = null;
            mHeaderPanel.setVisibility(VISIBLE);
            mRecyclerView.setPadding(0, (int) dpToPx(getApplicationContext(), 42), 0, 0);
            //mTitlePanel.setVisibility(VISIBLE);
        }

        private List<Track> getSelections() {
            return new ArrayList<>(selections);
        }
    }

    /**
     * Back pressed callback to handle navigation properly
     */
    private class BackPressedCallback extends OnBackPressedCallback {
        public BackPressedCallback(boolean enabled) {
            super(enabled);
        }

        @Override
        public void handleOnBackPressed() {
            if (mResideMenu.isOpened()) {
                mResideMenu.closeMenu();
                return;
            }

            if (actionMode != null) {
                actionMode.finish();
                return;
            }

            if(headerSearchView != null) {
                headerSearchView.clearFocus();
            }

            // if TYPE library or keyword is null, open leftMenu
            if(adapter != null) {
                if (adapter.hasFilter()) {
                    adapter.resetFilter();
                    swipeRefreshLayout.setRefreshing(true);
                    viewModel.loadMusicItems(adapter.getCriteria());

                    return;
                }

                if (adapter.isSearchMode()) {
                    doHideSearch();
                    swipeRefreshLayout.setRefreshing(true);
                    viewModel.loadMusicItems(adapter.getCriteria());

                    return;
                }

                if (isEmpty(adapter.getCriteria().getKeyword()) || SearchCriteria.TYPE.LIBRARY.equals(adapter.getCriteria().getType())) {
                    doShowLeftMenus();
                }else if ((!isEmpty(adapter.getCriteria().getKeyword())) && !SearchCriteria.TYPE.LIBRARY.equals(adapter.getCriteria().getType())) {
                    adapter.resetFilter();
                    adapter.getCriteria().setKeyword(null);
                    swipeRefreshLayout.setRefreshing(true);
                    viewModel.loadMusicItems(adapter.getCriteria());
                }
            }
        }
    }

    private void startDotAnimation() {
        if (dotAnimator != null) return;

        View dot1 = findViewById(R.id.scan_dot1);
        View dot2 = findViewById(R.id.scan_dot2);
        View dot3 = findViewById(R.id.scan_dot3);

        if (dot1 == null || dot2 == null || dot3 == null) return;

        // Bounce up (negative translationY) by 10 pixels
        android.animation.ObjectAnimator anim1 = android.animation.ObjectAnimator.ofFloat(dot1, "translationY", 0f, -10f, 0f);
        anim1.setDuration(1000);
        anim1.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        anim1.setRepeatMode(android.animation.ValueAnimator.RESTART);

        android.animation.ObjectAnimator anim2 = android.animation.ObjectAnimator.ofFloat(dot2, "translationY", 0f, -10f, 0f);
        anim2.setDuration(1000);
        anim2.setStartDelay(250);
        anim2.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        anim2.setRepeatMode(android.animation.ValueAnimator.RESTART);

        android.animation.ObjectAnimator anim3 = android.animation.ObjectAnimator.ofFloat(dot3, "translationY", 0f, -10f, 0f);
        anim3.setDuration(1000);
        anim3.setStartDelay(500);
        anim3.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        anim3.setRepeatMode(android.animation.ValueAnimator.RESTART);

        dotAnimator = new android.animation.AnimatorSet();
        dotAnimator.playTogether(anim1, anim2, anim3);
        dotAnimator.start();
    }

    private void stopDotAnimation() {
        if (dotAnimator != null) {
            dotAnimator.cancel();
            dotAnimator = null;
        }
    }

    public PlaybackService getPlaybackService() {
        return playbackService;
    }

    public boolean isPlaybackServiceBound() {
        return isPlaybackServiceBound && playbackService != null;
    }
}
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
import android.content.res.ColorStateList;
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
import apincer.android.mmate.utils.AudioOutputHelper;
import coil3.BitmapImage;
import coil3.Image;
import coil3.SingletonImageLoader;
import coil3.request.ImageRequest;
import coil3.target.Target;
import apincer.music.core.playback.PlaybackState;
import apincer.music.core.repository.QueueManager;
import apincer.music.core.utils.PlayerNameUtils;
import apincer.android.utils.FileUtils;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ContextMenu;
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
import apincer.android.mmate.ui.MySelectionTracker;
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
import apincer.android.mmate.utils.BitmapHelper;
import apincer.android.mmate.utils.PermissionUtils;
import apincer.music.core.Constants;
import apincer.music.core.Settings;
import apincer.music.core.model.Track;
import apincer.music.core.provider.MusicFileProvider;
import apincer.music.core.playback.spi.PlaybackService;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.PlaylistRepository;
import apincer.music.core.model.SearchCriteria;
import apincer.music.core.model.SearchResultStats;
import apincer.music.core.repository.TagRepository;
import apincer.android.mmate.ui.view.BottomOffsetDecoration;

import apincer.music.core.utils.ApplicationUtils;
import apincer.music.core.utils.StringUtils;
import apincer.android.mmate.utils.UIUtils;
import apincer.android.mmate.ui.viewmodel.MainViewModel;
import apincer.android.mmate.worker.FileOperationTask;
import apincer.android.mmate.worker.ScanAudioFileWorker;
import dagger.hilt.android.AndroidEntryPoint;
import me.stellarsand.android.fastscroll.FastScrollerBuilder;

/**
 * Main Activity for MusicMate application
 */
@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private androidx.compose.ui.platform.ComposeView composeListView;
    private View rootView;

    // Constants
    private static final int RECYCLEVIEW_ITEM_SCROLLING_OFFSET = 8; //16
    private static final int RECYCLEVIEW_ITEM_OFFSET = 8; //48; 1.5 item offset
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
    private apincer.music.core.model.SearchCriteria currentCriteria = new apincer.music.core.model.SearchCriteria(apincer.music.core.model.SearchCriteria.TYPE.LIBRARY);
    private MySelectionTracker mTracker;
    private final List<Track> selections = new ArrayList<>();
    private View mHeaderPanel;
    private ImageView mBackButton;
    private SearchView headerSearchView;
    private TextView headerStatText;

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
                    apincer.android.mmate.ui.compose.ListInterop.updateNowPlaying(playbackService.getNowPlayingSong(), false);

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

    private PlaybackState.State lastStateEnum = null;

    private void setNowPlaying(Track song, PlaybackState playbackState) {
        if (song != null) {
            composeListView.post(() -> {
                PlaybackState.State newStateEnum = playbackState != null ? playbackState.currentState : null;
                boolean songChanged = !song.equals(previouslyPlaying);
                boolean stateChanged = (lastStateEnum != newStateEnum);

                lastPlaybackState = playbackState;
                lastStateEnum = newStateEnum;

                if (true) {
                    apincer.android.mmate.ui.compose.ListInterop.updateNowPlaying(playbackService.getNowPlayingSong(), playbackState.currentState == apincer.music.core.playback.PlaybackState.State.PLAYING);

                }

                if (songChanged) {
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

                    // refresh previous playing music item
                    if (previouslyPlaying != null) {
                    }
                    updateGlassyPanelsColor(song);
                } else if (stateChanged) {
                    // refresh current playing music item only when play/pause state changes
                }

                if (nowPlayingLabel != null) {
                    nowPlayingLabel.setText(R.string.app_name);
                }
                previouslyPlaying = song;
                updateFloatingPlaybackBar(song, playbackState);
            });
        } else {
            composeListView.post(() -> {
                if (nowPlayingLabel != null) {
                    nowPlayingLabel.setText(R.string.app_name);
                }
                lastPlaybackState = playbackState;
                lastStateEnum = playbackState != null ? playbackState.currentState : null;
                previouslyPlaying = null;
                updateFloatingPlaybackBar(null, playbackState);
            });
        }
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
                String songTag = song.getPath();
                if (!songTag.equals(barAlbumArt.getTag())) {
                    barAlbumArt.setTag(songTag);
                    ImageRequest request = CoverartFetcher.builder(this, song)
                            .data(song)
                            .size(240, 240)
                            .target(new coil3.target.ImageViewTarget(barAlbumArt))
                            .build();
                    SingletonImageLoader.get(this).enqueue(request);
                }
            }
            if (barPlayPauseBtn != null && playbackState != null) {
                if (playbackState.currentState == PlaybackState.State.PLAYING) {
                    barPlayPauseBtn.setImageResource(R.drawable.ic_baseline_pause_24);
                    barPlayPauseBtn.setContentDescription(getString(R.string.pause));
                } else {
                    barPlayPauseBtn.setImageResource(R.drawable.ic_baseline_play_arrow_24);
                    barPlayPauseBtn.setContentDescription(getString(R.string.play));
                }
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
                barAlbumArt.setTag(null);
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

                            Bitmap paletteBitmap = BitmapHelper.ensureSoftwareBitmap(bitmap);
                            if (paletteBitmap != null && !paletteBitmap.isRecycled()) {
                                Palette.from(paletteBitmap).generate(palette -> {
                                    if (palette != null) {
                                        int color = palette.getVibrantColor(palette.getMutedColor(Color.DKGRAY));
                                        applyGlassyColor(color);
                                    }
                                });
                            }
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
        View bottomNav = rootView.findViewById(R.id.bottom_navigation_container);
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
                            if (currentCriteria != null) {
                                currentCriteria.setFilterType(filterType);
                                currentCriteria.setFilterText(filterText);
                            }
                            viewModel.loadMusicItems(currentCriteria);
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
        rootView = getLayoutInflater().inflate(R.layout.activity_main, null);
        setContentView(apincer.android.mmate.ui.compose.DrawerInterop.getComposeView(this, rootView));

        // Get the ViewModel. Hilt handles all the factory creation for you.
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        // Setup UI components
        setupHeaderPanel();
        setupBottomAppBar();
        setupRecycleView(searchCriteria);
        

        // Observe ViewModel LiveData
        setupObserveViewModel();

        // load music items
        viewModel.loadMusicItems(currentCriteria);

        // Bind to the MediaServerService as soon as this service is created
        Intent intent = new Intent(this, MusicMateServiceImpl.class);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    @SuppressLint("CheckResult")
    private void setupObserveViewModel() {
        viewModel.musicItems.observe(this, musicTags -> {
            composeListView.post(() -> {
                // If user is in selection mode, preserve active selection keys
                List<Long> selectedPositions = null;
                if (actionMode != null && mTracker != null && mTracker.hasSelection()) {
                    selectedPositions = new ArrayList<>();
                    mTracker.getSelection().forEach(selectedPositions::add);
                }

                // Save layout manager state to restore scroll position

                apincer.android.mmate.ui.compose.ListInterop.updateTracks(musicTags);
                swipeRefreshLayout.setRefreshing(false);
                // Update header after adapter is populated; stats observer will correct later
                updateHeaderPanel(viewModel.searchStats.getValue());


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

        viewModel.musicItemsLoading.observe(this, isLoading -> composeListView.post(() -> swipeRefreshLayout.setRefreshing(isLoading)));

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
                                viewModel.loadMusicItems(currentCriteria);
                            }
                        }
                        lastWorkState = currentState;
                    }
                });
    }

    private void setupHeaderPanel() {
        mBlurBackground = rootView.findViewById(R.id.main_background_blur);
        mHeaderPanel = rootView.findViewById(R.id.header_panel);
        mBackButton = rootView.findViewById(R.id.header_back_btn);
        headerSearchView = rootView.findViewById(R.id.search_view);
        headerStatText = rootView.findViewById(R.id.header_stats_text);

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
        View bottomNav = rootView.findViewById(R.id.bottom_navigation_container);
        // setSupportActionBar(bottomNav); // Removed as CardView is not a Toolbar

        // Handle Navigation Bar Insets for Bottom Capsule
        if (bottomNav != null) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomNav, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                if (v.getLayoutParams() instanceof MarginLayoutParams mlp) {
                    mlp.bottomMargin = systemBars.bottom + (int)dpToPx(this, 8);
                    v.setLayoutParams(mlp);
                }
                return insets;
            });
            bottomNav.setElevation(8f);
        }

        View leftMenu = rootView.findViewById(R.id.navigation_collections);
        ImageView rightMenu = rootView.findViewById(R.id.navigation_settings);

        // Setup menu click listeners
        leftMenu.setOnClickListener(v -> doShowLeftMenus());
        rightMenu.setOnClickListener(v -> doShowRightMenus());

        setupFloatingPlaybackBar();
    }

    private View floatingPlaybackBar;
    private ImageView barAlbumArt;
    private TextView barTrackTitle;
    private TextView barTargetSubtitle;
    private ImageView barPlayPauseBtn;
    private ImageView barNextBtn;

    private void setupFloatingPlaybackBar() {
        floatingPlaybackBar = rootView.findViewById(R.id.docked_playback_bar);
        if (floatingPlaybackBar == null) return;

        barAlbumArt = rootView.findViewById(R.id.bar_album_art);
        barTrackTitle = rootView.findViewById(R.id.bar_track_title);
        barTargetSubtitle = rootView.findViewById(R.id.bar_target_subtitle);
        barPlayPauseBtn = rootView.findViewById(R.id.btn_dock_play_pause);
        barNextBtn = rootView.findViewById(R.id.btn_dock_next);

        View titleContainer = rootView.findViewById(R.id.bar_title_container);
        View.OnClickListener openNowPlayingListener = v -> {
            // Sticky tab: reopen at the last tab the user viewed this session
            AudioHubBottomSheet sheet = AudioHubBottomSheet.newInstance();
            sheet.show(getSupportFragmentManager(), AudioHubBottomSheet.TAG);
        };

        if (titleContainer != null) {
            titleContainer.setOnClickListener(openNowPlayingListener);
        }

        if (barAlbumArt != null) {
            barAlbumArt.setOnClickListener(openNowPlayingListener);
        }
        
        if (barPlayPauseBtn != null) {
            barPlayPauseBtn.setOnClickListener(v -> {
                if (playbackService != null) {
                    if (lastPlaybackState != null && lastPlaybackState.currentState == PlaybackState.State.PLAYING) {
                        playbackService.pausePlayer();
                    } else {
                        Track nowPlaying = playbackService.getNowPlayingSong();
                        if (nowPlaying != null) {
                            playbackService.playSong(nowPlaying);
                        } else {
                            QueueManager qm = playbackService.getQueueManager();
                            if (qm != null) {
                                Track randomTrack = qm.getRandomTrack();
                                if (randomTrack != null) {
                                    playbackService.playSong(randomTrack);
                                }
                            }
                        }
                    }
                }
            });
        }
        
        if (barNextBtn != null) {
            barNextBtn.setOnClickListener(v -> {
                if (playbackService != null) {
                    playbackService.skipToNextInQueue();
                }
            });
        }
    }

    public void setFloatingDockVisible(boolean visible) {
        View bottomNav = rootView.findViewById(R.id.bottom_navigation_container);
        if (bottomNav != null) {
            if (visible) {
                bottomNav.setVisibility(View.VISIBLE);
                bottomNav.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(220)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
            } else {
                bottomNav.animate()
                        .alpha(0f)
                        .translationY(bottomNav.getHeight() > 0 ? bottomNav.getHeight() + dpToPx(this, 16) : dpToPx(this, 88))
                        .setDuration(180)
                        .setInterpolator(new android.view.animation.AccelerateInterpolator())
                        .withEndAction(() -> bottomNav.setVisibility(View.INVISIBLE))
                        .start();
            }
        }
    }

    public PlaybackState getLastPlaybackState() {
        return lastPlaybackState;
    }

    private void setupRecycleView(SearchCriteria searchCriteria) {
        if (searchCriteria == null) {
            searchCriteria = new SearchCriteria(SearchCriteria.TYPE.LIBRARY);
        }

        // Initialize adapter

        // Setup RecyclerView
        swipeRefreshLayout = rootView.findViewById(R.id.swipe_refresh_layout);
        emptyStateView = rootView.findViewById(R.id.empty_state_view);
        scanProgressDots = rootView.findViewById(R.id.scan_progress_dots);
        int spinnerOffset = getResources().getDimensionPixelSize(R.dimen.dimen_56_dp); // Example offset
        swipeRefreshLayout.setProgressViewOffset(false, 0, spinnerOffset);

        // --- Set the listener ---
        swipeRefreshLayout.setOnRefreshListener(() -> {
            // This method is called when the user swipes
            // 1. Load your new data here (e.g., make a network call)
            viewModel.loadMusicItems();
        });

        fabScrollToTop = rootView.findViewById(R.id.fab_scroll_to_top);
        fabScrollToTop.setOnClickListener(v -> {
            
            
            
        });

        ViewCompat.setOnApplyWindowInsetsListener(fabScrollToTop, (v, insets) -> {
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            int baseMargin = getResources().getDimensionPixelSize(R.dimen.dimen_64_dp);
            ViewGroup.LayoutParams params = v.getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams marginParams) {
                marginParams.bottomMargin = baseMargin + bottomInset;
                v.setLayoutParams(marginParams);
            }
            return insets;
        });

        composeListView = rootView.findViewById(R.id.compose_list_view);
        apincer.android.mmate.ui.compose.ListInterop.setMusicListContent(composeListView, this);
        
        ViewCompat.setOnApplyWindowInsetsListener(composeListView, (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        // add on item touch listener to detect and block touch selections that stop a fast move/scroll

        // add on scroll listener


            // Setup selection tracker
            mTracker = new MySelectionTracker();
            

            // Setup selection observer
            MySelectionTracker.SelectionObserver observer = new MySelectionTracker.SelectionObserver() {
                @Override
                public void onSelectionChanged() {
                    int count = mTracker.getSelection().size();
                    selections.clear();
                    if (count > 0) {
                        mTracker.getSelection().forEach(item -> {
                            Track tag = (item.intValue() >= 0 && item.intValue() < apincer.android.mmate.ui.compose.ListInterop.getTracks().size() ? apincer.android.mmate.ui.compose.ListInterop.getTracks().get(item.intValue()) : null);
                            if (tag != null) {
                                selections.add(tag);
                            }
                        });
                        if (actionMode == null) {
                            actionMode = startSupportActionMode(actionModeCallback);
                        }
                    } else if (actionMode != null) {
                        actionMode.finish();
                        actionMode = null;
                    }
                    if (actionMode != null) {
                        actionMode.setTitle(count + " Selected");
                        //actionMode.invalidate();
                    }
                }
            };
            mTracker.setObserver(observer);

        // Setup fast scroller

        // Initialize action mode callback
        actionModeCallback = new ActionModeCallback();
    }


    private void doShowLeftMenus() {
        apincer.android.mmate.ui.compose.DrawerInterop.openDrawer();
    }

    private void doShowRightMenus() {
        apincer.android.mmate.ui.compose.DrawerInterop.openDrawer();
    }

    private void updateHeaderPanel() {
        updateHeaderPanel(null);
    }

    private void updateHeaderPanel(SearchResultStats stats) {
        SearchCriteria.TYPE type = currentCriteria.getType();
        Drawable icon = ContextCompat.getDrawable(getBaseContext(), R.drawable.bg_transparent);

        // Prefer DB aggregate stats for accurate category-wide totals.
        // Fall back to adapter counts if stats not yet available (e.g. initial load).
        // For top-level category directories (where keyword is empty and type is not LIBRARY),
        // we display the category count itself, which is the total items in the adapter.
        boolean isTopLevelCategoryDir = isEmpty(currentCriteria.getKeyword())
                && !SearchCriteria.TYPE.LIBRARY.equals(type);
        boolean hasActiveFilter = (!(currentCriteria.getFilterType() == null || currentCriteria.getFilterType().isEmpty()));
        int count = (isTopLevelCategoryDir || hasActiveFilter) ? apincer.android.mmate.ui.compose.ListInterop.getTracks().size()
                : ((stats != null) ? stats.getTotalCount() : apincer.android.mmate.ui.compose.ListInterop.getTracks().size());
        long totalSize = hasActiveFilter ? apincer.android.mmate.ui.compose.ListInterop.getTracks().stream().mapToLong(Track::getFileSize).sum() : ((stats != null) ? stats.getTotalSize() : apincer.android.mmate.ui.compose.ListInterop.getTracks().stream().mapToLong(Track::getFileSize).sum());
        double totalDuration = hasActiveFilter ? apincer.android.mmate.ui.compose.ListInterop.getTracks().stream().mapToDouble(Track::getAudioDuration).sum() : ((stats != null) ? stats.getTotalDuration() : apincer.android.mmate.ui.compose.ListInterop.getTracks().stream().mapToDouble(Track::getAudioDuration).sum());

        String statText = "";
        if(!isEmpty(currentCriteria.getKeyword())) {
            // total songs
            if(count > 0) {
                statText = StringUtils.formatSongSize(count) + " Songs";
            }

            // filter details or duration
            if(isEmpty(currentCriteria.getFilterType()) && count > 0) {
                statText = statText + SYMBOL_ENC_SEP + StringUtils.formatStorageSize(totalSize) + SYMBOL_ENC_SEP + StringUtils.formatDuration(totalDuration, true);
            } else {
                String filterText = currentCriteria.getFilterText();
                if ("Folder".equals(currentCriteria.getFilterType())) {
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
                    currentCriteria.setFilterText(null); currentCriteria.setFilterType(null);
                    currentCriteria.setKeyword(null);
                    viewModel.loadMusicItems(currentCriteria);
                });
            }
        } else {
            // top-level category: show total count + category label
            if(count > 0) {
                statText = StringUtils.formatSongSize(count) + " " + StringUtils.formatTitle("Tracks");
                // Also show total storage + duration for the all-songs view (Library)
                if (stats != null && SearchCriteria.TYPE.LIBRARY.equals(type) && isEmpty(currentCriteria.getFilterType())) {
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
                    currentCriteria.setFilterText(null); currentCriteria.setFilterType(null);
                    currentCriteria.resetSearch();
                    doStartRefresh(SearchCriteria.TYPE.LIBRARY, Constants.TITLE_ALL_SONGS);
                });
            }
        }

        headerSearchView.setQueryHint("Search " + StringUtils.truncate(currentCriteria.getType().name(), 25, StringUtils.TruncateType.SUFFIX));
        headerStatText.setText(statText);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(headerSearchView != null) {
            headerSearchView.clearFocus();
        }

       // viewModel.getTagRepository().cleanInvalidTags();
        // load music items
       // viewModel.loadMusicItems(currentCriteria);
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
            composeListView.post(() -> {
                int positionToScroll = apincer.android.mmate.ui.compose.ListInterop.getTracks().indexOf(currentlyPlaying);
                if (positionToScroll != RecyclerView.NO_POSITION) {
                    scrollToPosition(positionToScroll);
                }
            });
        });
    }

    private void scrollToPosition(int position) {
        // TODO: Implement Compose LazyListState scrolling in ListInterop
    }

    private void doHideSearch() {
        currentCriteria.resetSearch();
        viewModel.loadMusicItems(currentCriteria);
    }

    private void doStartRefresh(SearchCriteria.TYPE type, String keyword) {
        currentCriteria.setType(type);
        currentCriteria.setKeyword(keyword);
       // folderAdapter.refresh();
        viewModel.loadMusicItems(currentCriteria);
    }

    public void handleNavigationItemClick(int itemId) {
        // Create a dummy MenuItem
        MenuItem item = new MenuItem() {
            @Override public int getItemId() { return itemId; }
            @Override public int getGroupId() { return 0; }
            @Override public int getOrder() { return 0; }
            @Override public MenuItem setTitle(CharSequence title) { return this; }
            @Override public MenuItem setTitle(int title) { return this; }
            @Override public CharSequence getTitle() { return null; }
            @Override public MenuItem setTitleCondensed(CharSequence title) { return this; }
            @Override public CharSequence getTitleCondensed() { return null; }
            @Override public MenuItem setIcon(Drawable icon) { return this; }
            @Override public MenuItem setIcon(int iconRes) { return this; }
            @Override public Drawable getIcon() { return null; }
            @Override public MenuItem setIntent(Intent intent) { return this; }
            @Override public Intent getIntent() { return null; }
            @Override public MenuItem setShortcut(char numericChar, char alphaChar) { return this; }
            @Override public MenuItem setShortcut(char numericChar, char alphaChar, int numericModifiers, int alphaModifiers) { return this; }
            @Override public MenuItem setNumericShortcut(char numericChar) { return this; }
            @Override public MenuItem setNumericShortcut(char numericChar, int numericModifiers) { return this; }
            @Override public char getNumericShortcut() { return 0; }
            @Override public int getNumericModifiers() { return 0; }
            @Override public MenuItem setAlphabeticShortcut(char alphaChar) { return this; }
            @Override public MenuItem setAlphabeticShortcut(char alphaChar, int alphaModifiers) { return this; }
            @Override public char getAlphabeticShortcut() { return 0; }
            @Override public int getAlphabeticModifiers() { return 0; }
            @Override public MenuItem setCheckable(boolean checkable) { return this; }
            @Override public boolean isCheckable() { return false; }
            @Override public MenuItem setChecked(boolean checked) { return this; }
            @Override public boolean isChecked() { return false; }
            @Override public MenuItem setVisible(boolean visible) { return this; }
            @Override public boolean isVisible() { return true; }
            @Override public MenuItem setEnabled(boolean enabled) { return this; }
            @Override public boolean isEnabled() { return true; }
            @Override public boolean hasSubMenu() { return false; }
            @Override public android.view.SubMenu getSubMenu() { return null; }
            @Override public MenuItem setOnMenuItemClickListener(OnMenuItemClickListener menuItemClickListener) { return this; }
            @Override public ContextMenu.ContextMenuInfo getMenuInfo() { return null; }
            @Override public void setShowAsAction(int actionEnum) {}
            @Override public MenuItem setShowAsActionFlags(int actionEnum) { return this; }
            @Override public MenuItem setActionView(View view) { return this; }
            @Override public MenuItem setActionView(int resId) { return this; }
            @Override public View getActionView() { return null; }
            @Override public MenuItem setActionProvider(android.view.ActionProvider actionProvider) { return this; }
            @Override public android.view.ActionProvider getActionProvider() { return null; }
            @Override public boolean expandActionView() { return false; }
            @Override public boolean collapseActionView() { return false; }
            @Override public boolean isActionViewExpanded() { return false; }
            @Override public MenuItem setOnActionExpandListener(OnActionExpandListener listener) { return this; }
        };
        onOptionsItemSelected(item);
    }

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

    public void showTrackPopupMenu(View anchorView, Track track) {
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, anchorView, android.view.Gravity.END);
        popup.getMenuInflater().inflate(R.menu.menu_track_popup, popup.getMenu());

        // Show/hide playback group based on whether a player device is active
        boolean playerActive = isPlaybackServiceBound && playbackService != null;
        popup.getMenu().setGroupVisible(R.id.group_playback, playerActive);

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            List<Track> singleTrackList = Collections.singletonList(track);
            if (id == R.id.action_play_now) {
                viewModel.playTrackList(apincer.android.mmate.ui.compose.ListInterop.getTracks(), track, playbackService);
                return true;
            } else if (id == R.id.action_play_next) {
                playbackService.getQueueManager().addPlayNext(track);
                android.widget.Toast.makeText(MainActivity.this, "Playing next", android.widget.Toast.LENGTH_SHORT).show();
                androidx.fragment.app.Fragment sheet = getSupportFragmentManager().findFragmentByTag(AudioHubBottomSheet.TAG);
                if (sheet instanceof AudioHubBottomSheet audioHub) {
                    audioHub.refreshUI();
                }
                return true;
            } else if (id == R.id.action_add_queue) {
                playbackService.getQueueManager().addPlayingQueue(track.getId());
                android.widget.Toast.makeText(MainActivity.this, "Added to queue", android.widget.Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.action_encoding_file) {
                doEncodeAudioFiles(singleTrackList);
                return true;
            } else if (id == R.id.action_open_with) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                android.net.Uri uri = MusicFileProvider.getUriForFile(track.getPath());
                intent.setDataAndType(uri, "audio/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(Intent.createChooser(intent, "Open with"));
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start external player activity", e);
                }
                return true;
            }
            return false;
        });
        popup.show();
    }

    public void showPlayerPickerPopup(View anchorView) {
        if (!isPlaybackServiceBound || playbackService == null) return;

        // Auto-trigger M-SEARCH the moment the popup opens — list will be fresh by the time
        // the user finishes reading it, without requiring a manual rescan tap.
        playbackService.refreshPlayerDiscovery();

        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, anchorView, android.view.Gravity.END);
        List<apincer.music.core.playback.spi.PlaybackTarget> renderers = playbackService.getPlaybackTargets();
        apincer.music.core.playback.spi.PlaybackTarget current = playbackService.getPlayer();

        apincer.android.mmate.utils.AudioOutputHelper.Device audioOutputDevice =
                apincer.android.mmate.utils.AudioOutputHelper.getOutputDevice(this, playbackService.getNowPlayingSong());

        // Group 0 = player targets (primary selection items)
        if (renderers != null && !renderers.isEmpty()) {
            for (int i = 0; i < renderers.size(); i++) {
                apincer.music.core.playback.spi.PlaybackTarget target = renderers.get(i);
                boolean isActive = current != null && current.getTargetId().equals(target.getTargetId());
                boolean isRemote = target.isStreaming();
                String baseLabel = apincer.music.core.utils.PlayerNameUtils.getDropdownPlayerLabel(target);

                if (target instanceof apincer.music.core.playback.ExternalAndroidPlayer extPlayer && "local".equalsIgnoreCase(extPlayer.getTargetId())) {
                    if (audioOutputDevice != null && audioOutputDevice.getName() != null && !audioOutputDevice.getName().isEmpty() && !"Phone Speaker".equalsIgnoreCase(audioOutputDevice.getName())) {
                        baseLabel = audioOutputDevice.getCompactLabel();
                    }
                }

                String label = isActive ? baseLabel + "  ✓" : baseLabel;
                android.view.MenuItem item = popup.getMenu().add(0, i, i, label);

                android.graphics.drawable.Drawable targetDrawable = AudioOutputHelper.getTargetDrawable(this, target, audioOutputDevice);
                if (targetDrawable != null) {
                    item.setIcon(targetDrawable);
                }
            }
        } else {
            android.view.MenuItem noPlayersItem = popup.getMenu().add(0, -1, 0, "Scanning for players…");
            noPlayersItem.setEnabled(false);
        }

        // Group 1 = utility/discovery actions — visually separated from player targets.
        // Order offset: Must use baseOrder > any Group 0 item index so Android MenuBuilder
        // places Group 1 items strictly AFTER all Group 0 target renderers.
        int baseOrder = (renderers != null ? renderers.size() : 0) + 10;
        final int RESCAN_ID = 9999;
        android.view.MenuItem rescanItem = popup.getMenu().add(1, RESCAN_ID, baseOrder, "Rescan for DLNA players");
        rescanItem.setIcon(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_baseline_refresh_24));

        final int BLUETOOTH_ID = 9998;
        android.view.MenuItem btItem = popup.getMenu().add(1, BLUETOOTH_ID, baseOrder + 1, "Bluetooth / System Output…");
        btItem.setIcon(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_round_bluetooth_audio_24));

        // Draw a visual divider between player targets and utility actions (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            popup.getMenu().setGroupDividerEnabled(true);
        }

        apincer.android.mmate.utils.UIUtils.makePopForceShowIcon(popup);

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == RESCAN_ID) {
                // Manual rescan — auto-rescan already fired on open, this is a "try again" fallback
                playbackService.refreshPlayerDiscovery();
                android.widget.Toast.makeText(this, "Scanning for players…", android.widget.Toast.LENGTH_SHORT).show();
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
            if (item.getItemId() == BLUETOOTH_ID) {
                openSystemAudioOutputPanel();
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


    public void openSystemAudioOutputPanel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent intent = new Intent("com.android.settings.panel.action.MEDIA_OUTPUT");
                intent.putExtra("com.android.settings.panel.extra.PACKAGE_NAME", getPackageName());
                startActivity(intent);
                return;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to launch MEDIA_OUTPUT panel, falling back to Bluetooth settings", e);
        }

        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
            startActivity(intent);
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Unable to open Bluetooth settings", android.widget.Toast.LENGTH_SHORT).show();
        }
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

        List<String> defaultPaths = FileRepository.getDefaultMusicPaths(this);
        Set<String> defaultPathsSet = new HashSet<>(defaultPaths);
        List<String> dirs = TagRepository.getDirectories(this);
        List<String> storageIds = DocumentFileCompat.getStorageIds(getApplicationContext());

        // We need a reference to the AlertDialog so we can dismiss it from inside the Compose callbacks
        final AlertDialog[] alertHolder = new AlertDialog[1];

        View cview = apincer.android.mmate.ui.compose.DialogInterop.createMusicFoldersDialogView(
            this,
            dirs,
            defaultPathsSet,
            storageIds,
            () -> { if(alertHolder[0] != null) alertHolder[0].dismiss(); }, // onClose
            () -> { if(alertHolder[0] != null) alertHolder[0].dismiss(); }, // onCancel
            (isDeep, updatedDirs) -> { // onScan
                apincer.music.core.Settings.setDirectories(getApplicationContext(), updatedDirs);
                if (isDeep) {
                    new MaterialAlertDialogBuilder(MainActivity.this, R.style.AlertDialogTheme)
                        .setTitle("Full Rescan")
                        .setMessage(getString(R.string.directories_confirm_full_scan))
                        .setPositiveButton("Start", (dialog, which) -> {
                            apincer.android.mmate.worker.ScanAudioFileWorker.startScan(getApplicationContext(), true);
                            if(alertHolder[0] != null) alertHolder[0].dismiss();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                } else {
                    apincer.android.mmate.worker.ScanAudioFileWorker.startScan(getApplicationContext(), false);
                    if(alertHolder[0] != null) alertHolder[0].dismiss();
                }
            },
            (sid) -> { // onAddStorage
                DialogProperties properties = new DialogProperties();
                properties.selection_mode = DialogConfigs.SINGLE_MODE;
                properties.selection_type = DialogConfigs.DIR_SELECT;
                FilePickerDialog dialog = new FilePickerDialog(MainActivity.this, properties);
                dialog.setDialogSelectionListener(files -> {
                    if (files != null && files.length > 0) {
                        String f = files[0];
                        // Re-trigger the dialog with new directory
                        dirs.add(f);
                        if(alertHolder[0] != null) alertHolder[0].dismiss();
                        apincer.music.core.Settings.setDirectories(getApplicationContext(), dirs); // Save immediately
                        doScanDirectories(); // Re-open
                    }
                });
                dialog.setTitle("Select a Directory");
                dialog.show();
            }
        );

        AlertDialog alert = new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setTitle("")
                .setView(cview)
                .setCancelable(true)
                .create();
        
        alertHolder[0] = alert;

        alert.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        alert.setCanceledOnTouchOutside(false);

        // Make popup round corners
        if (alert.getWindow() != null) {
            alert.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        alert.show();
    }

    private static void setListViewHeightBasedOnChildren(ListView listView) {
        android.widget.ListAdapter listAdapter = listView.getAdapter();
        if (listAdapter == null) return;

        int totalHeight = 0;
        int desiredWidth = View.MeasureSpec.makeMeasureSpec(listView.getWidth() > 0 ? listView.getWidth() : 800, View.MeasureSpec.AT_MOST);
        for (int i = 0; i < listAdapter.getCount(); i++) {
            View listItem = listAdapter.getView(i, null, listView);
            listItem.measure(desiredWidth, View.MeasureSpec.UNSPECIFIED);
            totalHeight += listItem.getMeasuredHeight();
        }

        ViewGroup.LayoutParams params = listView.getLayoutParams();
        params.height = totalHeight + (listView.getDividerHeight() * Math.max(0, listAdapter.getCount() - 1));
        listView.setLayoutParams(params);
        listView.requestLayout();
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
                            viewModel.loadMusicItems(currentCriteria);
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
                viewModel.search(currentCriteria, query);
                headerSearchView.clearFocus(); // Hide keyboard
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // User is typing
                viewModel.search(currentCriteria, newText);
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

        final AlertDialog[] alertHolder = new AlertDialog[1];
        apincer.android.mmate.ui.compose.ActionFilesState state = new apincer.android.mmate.ui.compose.ActionFilesState(selections);
        
        View cview = apincer.android.mmate.ui.compose.DialogInterop.createActionFilesDialogView(
            this,
            getString(R.string.title_removing_music_files),
            R.drawable.rounded_delete_24,
            state,
            getString(R.string.move_to_trash),
            () -> { if(alertHolder[0] != null) alertHolder[0].dismiss(); },
            () -> { if(alertHolder[0] != null) alertHolder[0].dismiss(); },
            () -> {
                state.setBusy(true);
                state.setProgress(FileOperationTask.getInitialProgress(selections.size()));
                operationTask.deleteFiles(getApplicationContext(), selections,
                        new FileOperationTask.ProgressCallback() {
                            @Override
                            public void onProgress(Track tag, int progress, String status) {
                                runOnUiThread(() -> {
                                    state.updateStatus(tag, status);
                                    state.setProgress(progress);
                                    if ("Deleted".equalsIgnoreCase(status) && isPlaybackServiceBound && playbackService != null) {
                                        playbackService.onTrackDeleted(tag);
                                    }
                                });
                            }

                            @Override
                            public void onComplete() {
                                runOnUiThread(() -> {
                                    viewModel.loadMusicItems();
                                    state.setBusy(false);
                                    if(alertHolder[0] != null) alertHolder[0].dismiss();
                                });
                            }
                        });
            }
        );

        AlertDialog alert = new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setTitle("")
                .setView(cview)
                .setCancelable(true)
                .create();
        
        alertHolder[0] = alert;
        alert.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        alert.setCanceledOnTouchOutside(false);
        if (alert.getWindow() != null) {
            alert.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        alert.show();
    }

    private void doMoveMediaItems(List<Track> selections) {
        if (selections.isEmpty()) return;

        final AlertDialog[] alertHolder = new AlertDialog[1];
        apincer.android.mmate.ui.compose.ActionFilesState state = new apincer.android.mmate.ui.compose.ActionFilesState(selections);
        
        View cview = apincer.android.mmate.ui.compose.DialogInterop.createActionFilesDialogView(
            this,
            getString(R.string.files_to_move),
            R.drawable.rounded_drive_file_move_24,
            state,
            getString(R.string.move_to_music),
            () -> { if(alertHolder[0] != null) alertHolder[0].dismiss(); },
            () -> { if(alertHolder[0] != null) alertHolder[0].dismiss(); },
            () -> {
                state.setBusy(true);
                state.setProgress(FileOperationTask.getInitialProgress(selections.size()));
                operationTask.moveFiles(getApplicationContext(), selections,
                        new FileOperationTask.ProgressCallback() {
                            @Override
                            public void onProgress(Track tag, int progress, String status) {
                                runOnUiThread(() -> {
                                    state.updateStatus(tag, status);
                                    state.setProgress(progress);
                                    if ("Deleted".equalsIgnoreCase(status) && isPlaybackServiceBound && playbackService != null) {
                                        playbackService.onTrackDeleted(tag);
                                    }
                                });
                            }

                            @Override
                            public void onComplete() {
                                runOnUiThread(() -> {
                                    viewModel.loadMusicItems();
                                    state.setBusy(false);
                                    if(alertHolder[0] != null) alertHolder[0].dismiss();
                                });
                            }
                        });
            }
        );

        AlertDialog alert = new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setTitle("")
                .setView(cview)
                .setCancelable(true)
                .create();
        
        alertHolder[0] = alert;
        alert.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        alert.setCanceledOnTouchOutside(false);
        if (alert.getWindow() != null) {
            alert.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        alert.show();
    }

    private void doEncodeAudioFiles(List<Track> selections) {
        if (selections.isEmpty()) return;

        apincer.android.mmate.ui.compose.FormatFilesState state = new apincer.android.mmate.ui.compose.FormatFilesState(selections);
        
        AlertDialog alert = new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setTitle("")
                .setCancelable(true)
                .create();

        View cview = apincer.android.mmate.ui.compose.DialogInterop.createFormatFilesDialogView(
            this,
            state,
            () -> alert.dismiss(),
            () -> alert.dismiss(),
            () -> {
                busy = true;
                state.isBusy().setValue(true);
                state.getProgress().setValue(FileOperationTask.getInitialProgress(selections.size()));

                int compressionLevel = FLAC_BALANCE_COMPRESS_LEVEL;
                String targetExt;
                String selectedFormat = state.getSelectedFormat().getValue();
                
                if (selectedFormat.contains(".aiff")) {
                    targetExt = FILE_AIFF;
                } else if (selectedFormat.contains(".mp3")) {
                    targetExt = FILE_MP3;
                } else if (selectedFormat.contains(".m4a")) {
                    targetExt = FILE_ALAC;
                } else {
                    if (selectedFormat.contains("fast")) compressionLevel = FLAC_FAST_COMPRESS_LEVEL;
                    else if (selectedFormat.contains("maximum")) compressionLevel = FLAC_MAXIMUM_COMPRESS_LEVEL;
                    targetExt = FILE_FLAC;
                }

                int targetSampleRate = 0;
                String selectedSampleRateStr = state.getSelectedSampleRate().getValue();
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
                                state.getStatusMap().put(tag, status);
                                state.getProgress().setValue(progress);
                            });
                        }

                        @Override
                        public void onComplete() {
                            runOnUiThread(() -> {
                                viewModel.loadMusicItems();
                                busy = false;
                                alert.dismiss();
                            });
                        }
                    });
            }
        );

        alert.setView(cview);
        alert.requestWindowFeature(Window.FEATURE_NO_TITLE);
        alert.setCanceledOnTouchOutside(false);

        if (alert.getWindow() != null) {
            alert.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

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

        // Allow dropdown popup to expand naturally to fit single-line text without wrapping
        input.setDropDownWidth(android.view.ViewGroup.LayoutParams.WRAP_CONTENT);

        // Optional: dark popup background
        input.setDropDownBackgroundResource(R.color.black_transparent_64);
    }

    private boolean isSelectionBlocked() {
        return false;
    }

    // You can put this class inside your Activity/Fragment

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
            mHeaderPanel.setVisibility(GONE);
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int id = item.getItemId();
            if (id == R.id.action_edit_metadata) {
                doShowEditActivity(getSelections());
                mode.finish();
                return true;
            } else if (id == R.id.action_transfer_file) {
                doMoveMediaItems(getSelections());
                mode.finish();
                return true;
            } else if (id == R.id.action_encoding_file) {
                doEncodeAudioFiles(getSelections());
                mode.finish();
                return true;
            } else if (id == R.id.action_delete) {
                doDeleteMediaItems(getSelections());
                mode.finish();
                return true;
            } else if (id == R.id.action_select_all) {
                if (mTracker.getSelection().size() == apincer.android.mmate.ui.compose.ListInterop.getTracks().size()) {
                    mTracker.clearSelection();
                } else {
                    for (int i = 0; i < apincer.android.mmate.ui.compose.ListInterop.getTracks().size(); i++) {
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

            if (actionMode != null) {
                actionMode.finish();
                return;
            }

            if(headerSearchView != null) {
                headerSearchView.clearFocus();
            }

            // if TYPE library or keyword is null, open leftMenu
            if(true) {
                if ((!(currentCriteria.getFilterType() == null || currentCriteria.getFilterType().isEmpty()))) {
                    currentCriteria.setFilterText(null); currentCriteria.setFilterType(null);
                    swipeRefreshLayout.setRefreshing(true);
                    viewModel.loadMusicItems(currentCriteria);

                    return;
                }

                if (currentCriteria.isSearchMode()) {
                    doHideSearch();
                    swipeRefreshLayout.setRefreshing(true);
                    viewModel.loadMusicItems(currentCriteria);

                    return;
                }

                if (isEmpty(currentCriteria.getKeyword()) || SearchCriteria.TYPE.LIBRARY.equals(currentCriteria.getType())) {
                    doShowLeftMenus();
                }else if ((!isEmpty(currentCriteria.getKeyword())) && !SearchCriteria.TYPE.LIBRARY.equals(currentCriteria.getType())) {
                    currentCriteria.setFilterText(null); currentCriteria.setFilterType(null);
                    currentCriteria.setKeyword(null);
                    swipeRefreshLayout.setRefreshing(true);
                    viewModel.loadMusicItems(currentCriteria);
                }
            }
        }
    }

    private void startDotAnimation() {
        if (dotAnimator != null) return;

        View dot1 = rootView.findViewById(R.id.scan_dot1);
        View dot2 = rootView.findViewById(R.id.scan_dot2);
        View dot3 = rootView.findViewById(R.id.scan_dot3);

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

    public void onTrackClicked(apincer.music.core.model.Track tag, int position) {
        if (isSelectionBlocked()) return;
        if (mTracker != null && mTracker.hasSelection()) {
            if (mTracker.isSelected((long) position)) {
                mTracker.deselect((long) position);
            } else {
                mTracker.select((long) position);
            }
            return;
        }

        if(tag != null && tag.isContainer()) {
            doStartRefresh(tag.getContainerType(), tag.getTitle());
        } else if (tag != null) {
            doShowEditActivity(java.util.Collections.singletonList(tag));
        }
    }

    public void onTrackLongClicked(apincer.music.core.model.Track tag, int position) {
        if (isSelectionBlocked()) return;
        if (mTracker != null) {
            mTracker.select((long) position);
        }
    }

    public void onTrackMenuClicked(apincer.music.core.model.Track tag, int position) {
        if (tag != null) {
            showTrackPopupMenu(rootView.findViewById(R.id.compose_list_view), tag);
        }
    }

}
package apincer.android.mmate.ui;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static apincer.android.mmate.utils.UIUtils.dpToPx;
import static apincer.music.core.Constants.FLAC_STANDARD_COMPRESS_LEVEL;
import static apincer.music.core.Constants.FLAC_UNCOMPRESS_LEVEL;
import static apincer.music.core.Constants.KEY_FILTER_KEYWORD;
import static apincer.music.core.Constants.KEY_FILTER_TYPE;
import static apincer.music.core.utils.StringUtils.isEmpty;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
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

import com.anggrayudi.storage.file.DocumentFileCompat;
import com.balsikandar.crashreporter.ui.CrashReporterActivity;
import com.developer.filepicker.model.DialogConfigs;
import com.developer.filepicker.model.DialogProperties;
import com.developer.filepicker.view.FilePickerDialog;
import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.apache.commons.text.WordUtils;

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
import java.util.Timer;
import java.util.TimerTask;

import javax.inject.Inject;

import apincer.android.mmate.R;
import apincer.android.mmate.Settings;
import apincer.android.mmate.service.MusicMateServiceImpl;
import apincer.android.mmate.utils.PermissionUtils;
import apincer.music.core.Constants;
import apincer.music.core.database.MusicTag;
import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.playback.spi.PlaybackService;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.model.MusicFolder;
import apincer.music.core.repository.PlaylistRepository;
import apincer.music.core.model.SearchCriteria;
import apincer.music.core.repository.TagRepository;
import apincer.android.mmate.ui.view.BottomOffsetDecoration;
import apincer.android.mmate.ui.view.MediaServerManagementSheet;
import apincer.android.mmate.ui.view.SignalPathBottomSheet;
import apincer.android.mmate.ui.widget.RatioSegmentedProgressBarDrawable;
import apincer.music.core.utils.ApplicationUtils;
import apincer.music.core.utils.StringUtils;
import apincer.android.mmate.utils.UIUtils;
import apincer.android.mmate.ui.viewmodel.MainViewModel;
import apincer.android.mmate.worker.FileOperationTask;
import apincer.android.mmate.worker.ScanAudioFileWorker;
import apincer.android.residemenu.ResideMenu;
import dagger.hilt.android.AndroidEntryPoint;
import me.stellarsand.android.fastscroll.FastScrollerBuilder;
import sakout.mehdi.StateViews.StateView;

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

    // Activity result launcher
    ActivityResultLauncher<Intent> tagViewResultLauncher;

    // ViewModel
    private MainViewModel viewModel;

    // UI components
    private ResideMenu mResideMenu;
    private MusicTagAdapter adapter;
    private SelectionTracker<Long> mTracker;
    private final List<MusicTag> selections = new ArrayList<>();
    //private Snackbar mExitSnackbar;
    private View mHeaderPanel;
    private ImageView mBackButton;
    private SearchView headerSearchView;
    private TextView headerStatText;

    private StateView mStateView;
    private RecyclerView mRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FloatingActionButton fabScrollToTop;

    private TextView nowPlayingLabel;

    // Action mode
    private ActionModeCallback actionModeCallback;
    private ActionMode actionMode;

    // State variables
    private long lastScrollEventTime = 0;
    private Timer timer;
    private volatile boolean busy;
    private MediaTrack previouslyPlaying;

    private PlaybackService playbackService;
    private boolean isPlaybackServiceBound = false;

    @Inject
    FileOperationTask operationTask;

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
                    playbackState -> setNowPlaying(playbackService.getNowPlayingSong()),
                    throwable -> Log.e("BaseServer", "Error in PlaybackState subscription", throwable));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isPlaybackServiceBound = false;
            playbackService = null;
          //  Log.w(TAG, "PlaybackService disconnected unexpectedly.");
        }
    };

    private void setNowPlaying(MediaTrack song) {
        if (song != null) {
            mRecyclerView.post(() -> {
                if(!song.equals(previouslyPlaying)) {
                    if (Settings.isListFollowNowPlaying(getBaseContext())) {
                        // only scrolled on first event for each song
                        if (timer != null) {
                            timer.cancel();
                        }
                        timer = new Timer();
                        timer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                if (!busy) {
                                    runOnUiThread(() -> scrollToSong(song));
                                }
                            }
                        }, 500); // 0.5 seconds
                    }

                    // refresh music list
                    adapter.notifyItemChanged(previouslyPlaying);
                    adapter.notifyItemChanged(song);
                }
                nowPlayingLabel.setText(StringUtils.truncate(song.getTitle(), 24, StringUtils.TruncateType.SUFFIX));
                previouslyPlaying = song;
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Setup night mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);

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
                        }
                    }
                    viewModel.loadMusicItems(adapter.getCriteria());
                });

        // Setup status bar
        Window window = getWindow();
        //window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
       // window.setStatusBarColor(ContextCompat.getColor(this, android.R.color.black));
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
        // If the background is dark, use light icons
        insetsController.setAppearanceLightStatusBars(false);

        // Get search criteria from intent
        SearchCriteria searchCriteria = ApplicationUtils.getSearchCriteria(getIntent());

        // Setup back press handler
        OnBackPressedCallback onBackPressedCallback = new BackPressedCallback(true);
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);

        // Initialize repository
        //repos = FileRepository.newInstance(getApplicationContext());

        // Set content view
        setContentView(R.layout.activity_main);

        // Initialize ViewModel
       // viewModel = new ViewModelProvider(this).get(MainViewModel.class);
       // MainViewModel.MusicViewModelFactory factory = new MainViewModel.MusicViewModelFactory(getApplication());
        //viewModel = new ViewModelProvider(this, factory).get(MainViewModel.class);
        // Get the ViewModel. Hilt handles all the factory creation for you.
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        //operationTask = new FileOperationTask(viewModel.getFileRepository(), viewModel.getTagRepository());
        // Setup UI components
        setupHeaderPanel();
        //setupNowPlayingView();
        setupBottomAppBar();
        setupRecycleView(searchCriteria);
        //setupSwipeToRefresh();
        setupResideMenus();

        // Observe ViewModel LiveData
        setupObserveViewModel();

        // load music items
        viewModel.loadMusicItems(adapter.getCriteria());

        // Setup exit snackbar
        //mExitSnackbar = Snackbar.make(mRecyclerView, R.string.alert_back_to_exit, Snackbar.LENGTH_LONG);
        //View snackBarView = mExitSnackbar.getView();
        //snackBarView.setBackgroundColor(getColor(R.color.warningColor));

        // Bind to the MediaServerService as soon as this service is created
        Intent intent = new Intent(this, MusicMateServiceImpl.class);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    @SuppressLint("CheckResult")
    private void setupObserveViewModel() {
        viewModel.musicItems.observe(this, musicTags -> {
            mRecyclerView.post(() -> {
                        adapter.setMusicTags(musicTags);
                        swipeRefreshLayout.setRefreshing(false);
                       // refreshLayout.finishRefresh();
                    });
            updateHeaderPanel();
            if (adapter.getItemCount() == 0) {
                mStateView.displayState("search");
            } else {
                mStateView.hideStates();
            }
        });

        viewModel.musicItemsLoading.observe(this, isLoading -> mRecyclerView.post(() -> swipeRefreshLayout.setRefreshing(isLoading)));
    }

    private void setupHeaderPanel() {
        mHeaderPanel = findViewById(R.id.header_panel);
        mBackButton = findViewById(R.id.header_back_btn);
        headerSearchView = findViewById(R.id.search_view);
        headerStatText = findViewById(R.id.header_stats_text);

      //  mHeaderPanel.setRenderEffect(
      //          RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)
      //  );
        mHeaderPanel.getBackground().setAlpha(90); // 0–255, lower = more transparent

        setupSearchView();
        openSearch();
    }

    private void setupBottomAppBar() {
        // Find components
        BottomAppBar bottomAppBar = findViewById(R.id.bottom_app_bar);
        setSupportActionBar(bottomAppBar);

        /*bottomAppBar.setRenderEffect(
                RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.CLAMP)
        ); */

        bottomAppBar.getBackground().setAlpha(200); // optional, 0–255
        bottomAppBar.setElevation(dpToPx(getApplicationContext(), 8));

        View leftMenu = bottomAppBar.findViewById(R.id.navigation_collections);
        nowPlayingLabel = bottomAppBar.findViewById(R.id.navigation_now_playing);

        ImageView rightMenu = bottomAppBar.findViewById(R.id.navigation_settings);
       // UIUtils.getTintedDrawable(rightMenu.getDrawable(), Color.WHITE);

      //  View signalPath = bottomAppBar.findViewById(R.id.navigation_signal_path);
        View mediaServer = bottomAppBar.findViewById(R.id.navigation_media_server);

        // Setup menu click listeners
        leftMenu.setOnClickListener(v -> doShowLeftMenus());
        rightMenu.setOnClickListener(v -> doShowRightMenus());
        nowPlayingLabel.setOnClickListener(v -> doShowSignalPath());
        mediaServer.setOnClickListener(v -> doManageMediaServer());
    }

    private void doShowSignalPath() {
        if(previouslyPlaying != null) {
            SignalPathBottomSheet bottomSheet = new SignalPathBottomSheet(v -> scrollToSong(previouslyPlaying));
            bottomSheet.show(getSupportFragmentManager(), "SignalPathBottomSheet");
        }
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
                if (adapter.getItemCount() == 0) {
                    mStateView.displayState("search");
                } else {
                    mStateView.hideStates();
                }
            }
        });

        // Setup RecyclerView
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
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

        // Add bottom padding
        RecyclerView.ItemDecoration itemDecoration = new BottomOffsetDecoration(64);
        mRecyclerView.addItemDecoration(itemDecoration);
        mRecyclerView.setPreserveFocusAfterLayout(true);

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
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                    // We are DRAGGING (state 1) or SETTLING (state 2).

                    // 1. Update the timestamp *every time* the state changes to non-idle.
                    lastScrollEventTime = SystemClock.elapsedRealtime();

                    // 2. Disable the view.
                    recyclerView.setEnabled(false);
                } else {
                    // We are IDLE (state 0).

                    // 1. Re-enable immediately.
                    recyclerView.setEnabled(true);

                    // 2. Update the timestamp ONE LAST TIME as we become idle.
                    //    This ensures the guard is active for the next 200ms.
                    lastScrollEventTime = SystemClock.elapsedRealtime();
                }
            }
        });

        // Setup item click listener
        MusicTagAdapter.OnListItemClick onListItemClick = (view, position) -> {

            MusicTag tag = adapter.getMusicTag(position);
            if(tag == null) return;

            if(tag instanceof MusicFolder folder) {
                doStartRefresh(folder.getType(), tag.getTitle());
            }else {
                doShowEditActivity(Collections.singletonList(tag));
            }
        };
        adapter.setClickListener(onListItemClick);

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
                            mTracker.getSelection().forEach(item ->
                                    selections.add(adapter.getMusicTag(item.intValue())));
                            if (actionMode == null) {
                                actionMode = startSupportActionMode(actionModeCallback);
                            }
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
                    MediaTrack track = adapter.getMusicTag(position);
                    if(track != null && !isEmpty(track.getTitle())) return track.getTitle().subSequence(0,1);
                    return "-";
                })
                .build();

        // Initialize action mode callback
        actionModeCallback = new ActionModeCallback();

        // Setup state view
        mStateView = findViewById(R.id.status_page);
        mStateView.hideStates();
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
            LinearLayout panel = storageView.findViewById(R.id.storage_bar);
            TextView totalSongText = storageView.findViewById(R.id.header_total_songs);
            TextView totalDurationText = storageView.findViewById(R.id.header_total_duration);

            List<MusicTag> list = viewModel.getTagRepository().getAllMusics();
            long songCount = list.size();
            long totalDuration = 0;
            for (MusicTag tag : list) {
                if (tag != null) { // Add null check for safety
                    totalDuration += (long) tag.getAudioDuration();
                }
            }

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
        SearchCriteria.TYPE type = adapter.getCriteria().getType();
        Drawable icon = ContextCompat.getDrawable(getBaseContext(), R.drawable.bg_transparent);

        int count = adapter.getTotalItems();
        String statText;
        if(!isEmpty(adapter.getCriteria().getKeyword())) {
            // total songs
            if(count > 0) {
                statText = StringUtils.formatSongSize(count) + " Songs";
            }else {
                statText = "No Results";
            }

            // filter details or duration
            if(isEmpty(adapter.getCriteria().getFilterType()) && count > 0) {
                statText = statText+ " · "+ StringUtils.formatDuration(adapter.getTotalDuration(), true);
            }else {
                String filterText = adapter.getCriteria().getFilterText();
                if ("Folder".equals(adapter.getCriteria().getFilterType())) {
                    filterText = StringUtils.truncate(DocumentFileCompat.getBasePath(getApplicationContext(), filterText), 38, StringUtils.TruncateType.PREFIX);
                }else {
                    filterText = StringUtils.truncate(filterText, 38, StringUtils.TruncateType.SUFFIX);
                }
                statText = statText+" · ["+filterText+"]";
            }

            // can back to higher category, except type library
            if(SearchCriteria.TYPE.LIBRARY.equals(type)){
                mBackButton.setImageDrawable(icon);
            }else {
                mBackButton.setImageDrawable(ContextCompat.getDrawable(getBaseContext(), R.drawable.rounded_arrow_shape_up_24));
                mHeaderPanel.setOnClickListener(view -> {
                    adapter.resetFilter();
                    adapter.getCriteria().setKeyword(null);
                    viewModel.loadMusicItems(adapter.getCriteria());
                });
            }
        }else {
            // total songs
            if(count > 0) {
                statText = StringUtils.formatSongSize(count) + " " + StringUtils.formatTitle(adapter.getHeaderLabel());
            }else {
                statText = "No Results";
            }
        }

        headerSearchView.setQueryHint("Search "+StringUtils.truncate(adapter.getHeaderTitle(), 25, StringUtils.TruncateType.SUFFIX));
        headerStatText.setText(statText);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mResideMenu.isOpened()) {
            mResideMenu.closeMenu();
        }

     //   closeSearch();
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
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if(isPlaybackServiceBound) {
            unbindService(serviceConnection);
        }
        super.onDestroy();
    }

    private boolean scrollToSong(MediaTrack currentlyPlaying) {
        if (currentlyPlaying == null) return false;

        int positionToScroll = adapter.getMusicTagPosition(currentlyPlaying);
        scrollToPosition(positionToScroll);
        return true;
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
        } else if (item.getItemId() == R.id.menu_codecs) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.CODEC, null);
            //doStartRefresh(SearchCriteria.TYPE.AUDIO_ENCODINGS, Constants.TITLE_HIGH_QUALITY);
            return true;
        } else if (item.getItemId() == R.id.menu_collection) {
            doHideSearch();
            PlaylistRepository.initPlaylist(getApplicationContext());
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
      //  } else if (item.getItemId() == R.id.menu_media_server) {
      //      doManageMediaServer();
      //      return true;
        } else if (item.getItemId() == R.id.menu_about_crash) {
            Intent intent = new Intent(MainActivity.this, CrashReporterActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void doManageMediaServer() {
        // bottom sheet to display dlna server status, and buttons to start/stop server
        MediaServerManagementSheet sheet = MediaServerManagementSheet.newInstance(playbackService);
        sheet.show(getSupportFragmentManager(), MediaServerManagementSheet.TAG);
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
        View btnCancel = cview.findViewById(R.id.button_cancel);

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
            btn.setText("+"+ WordUtils.capitalize(sid));
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
           // Settings.setLastScanTime(getApplicationContext(), 0);
            // start scan after setting dirs
            Log.i(TAG, "Starting scan music file for first time.");
            ScanAudioFileWorker.startScan(getApplicationContext());
            alert.dismiss();
        });

        btnCancel.setOnClickListener(v -> alert.dismiss());
        alert.show();
    }

    private void doShowEditActivity(List<MusicTag> selections) {
        ArrayList<MusicTag> tagList = new ArrayList<>();

        for (MusicTag tag : selections) {
            if (FileRepository.isMediaFileExist(tag)) {
                tagList.add(tag);
            } else {
                new MaterialAlertDialogBuilder(MainActivity.this, R.style.AlertDialogTheme)
                        .setTitle("Problem")
                        .setMessage(getString(R.string.alert_invalid_media_file, tag==null?" - ":tag.getPath()))
                        .setPositiveButton("GOT IT", (dialogInterface, i) -> {
                            if(isPlaybackServiceBound) {
                                playbackService.play(tag);
                            }
                            viewModel.getTagRepository().deleteMediaTag(tag);
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
           // sharedViewModel.addSharedItems(Constants.SHARED_TYPE.PREVIEW,tagList);

            Intent intent = new Intent(MainActivity.this, TagsActivity.class);
            intent.putExtra("MUSIC_TAG_IDS", tagIds);
            tagViewResultLauncher.launch(intent);
        }
    }

    private void openSearch() {

        // Show the SearchView
        headerSearchView.setVisibility(View.VISIBLE);
       // headerSearchView.requestFocus();

       // mBackButton.setImageDrawable(getDrawable(R.drawable.round_close_24));
       // mBackButton.setOnClickListener(view -> closeSearch());

        // Show keyboard
       // InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
       // imm.showSoftInput(headerSearchView.findViewById(androidx.appcompat.R.id.search_src_text), InputMethodManager.SHOW_IMPLICIT);
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
        ImageView closeButton = headerSearchView.findViewById(androidx.appcompat.R.id.search_close_btn);
        closeButton.setOnClickListener(v -> {
            headerSearchView.setQuery("", false); // Clear the text
            // You might also want to close the whole search bar here:
            // closeSearch();
        });
    }

    private void doDeleteMediaItems(List<MusicTag> selections) {
        if (selections.isEmpty()) return;

        View cview = getLayoutInflater().inflate(R.layout.view_action_files, null);

        Map<MusicTag, String> statusList = new HashMap<>();
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

                MusicTag tag = selections.get(i);
                TextView seq = view.findViewById(R.id.seq);
                TextView name = view.findViewById(R.id.name);
                TextView status = view.findViewById(R.id.status);

                seq.setText(String.valueOf(i + 1));
                status.setText(statusList.getOrDefault(tag, "-"));
                //name.setText(FileUtils.getFileName(tag.getPath()));
                name.setText(tag.getSimpleName());

                return view;
            }
        });

        MaterialButton btnOK = cview.findViewById(R.id.button_ok);
        View btnCancel = cview.findViewById(R.id.button_cancel);
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
        progressBar.setProgressDrawable(new RatioSegmentedProgressBarDrawable(barColor, Color.GRAY, valueList, 8f));
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
                        public void onProgress(MusicTag tag, int progress, String status) {
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

    private void doMoveMediaItems(List<MusicTag> selections) {
        if (selections.isEmpty()) return;

        View cview = getLayoutInflater().inflate(R.layout.view_action_files, null);

        Map<MusicTag, String> statusList = new HashMap<>();
        ListView itemsView = cview.findViewById(R.id.itemListView);
        TextView titleText = cview.findViewById(R.id.title);
        ImageView titleIcon = cview.findViewById(R.id.title_icon);
        TextView fileListTitleText = cview.findViewById(R.id.file_list_title);
        titleText.setText(R.string.title_import_to_music_directory);
        fileListTitleText.setText(R.string.files_to_move);
        titleIcon.setImageDrawable(AppCompatResources.getDrawable(getApplicationContext(), R.drawable.rounded_deployed_code_update_24));

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

                MusicTag tag = selections.get(i);
                TextView seq = view.findViewById(R.id.seq);
                TextView name = view.findViewById(R.id.name);
                TextView status = view.findViewById(R.id.status);

                seq.setText(String.valueOf(i + 1));
                status.setText(statusList.getOrDefault(tag, "-"));
                name.setText(tag.getSimpleName());

                return view;
            }
        });

        View btnOK = cview.findViewById(R.id.button_ok);
        View btnCancel = cview.findViewById(R.id.button_cancel);
        ProgressBar progressBar = cview.findViewById(R.id.progressBar);
        btnOK.setEnabled(true);

        double block = Math.min(selections.size(), MAX_PROGRESS_BLOCK);
        double sizeInBlock = MAX_PROGRESS / block;
        List<Long> valueList = new ArrayList<>();

        for (int i = 0; i < block; i++) {
            valueList.add((long) sizeInBlock);
        }

      //  final double rate = 100.00 / selections.size();
        int barColor = getColor(R.color.material_color_green_400);
        progressBar.setProgressDrawable(new RatioSegmentedProgressBarDrawable(barColor, Color.GRAY, valueList, 8f));
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
                        public void onProgress(MusicTag tag, int progress, String status) {
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

    private void doMeasureDR(List<MusicTag> selections) {
        if (selections.isEmpty()) return;

        View cview = getLayoutInflater().inflate(R.layout.view_action_files, null);

        Map<MusicTag, String> statusList = new HashMap<>();
        ListView itemsView = cview.findViewById(R.id.itemListView);
        TextView titleText = cview.findViewById(R.id.title);
        ImageView titleIcon = cview.findViewById(R.id.title_icon);
        TextView fileListTitleText = cview.findViewById(R.id.file_list_title);
        titleText.setText(R.string.title_dynamic_range_and_replay_gain);
        fileListTitleText.setText(R.string.files_to_analyze);
        titleIcon.setImageDrawable(AppCompatResources.getDrawable(getApplicationContext(), R.drawable.rounded_query_stats_24));

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

                MusicTag tag = selections.get(i);
                TextView seq = view.findViewById(R.id.seq);
                TextView name = view.findViewById(R.id.name);
                TextView status = view.findViewById(R.id.status);

                seq.setText(String.valueOf(i + 1));
                status.setText(statusList.getOrDefault(tag, "-"));
                name.setText(tag.getSimpleName());

                return view;
            }
        });

        MaterialButton btnOK = cview.findViewById(R.id.button_ok);
        View btnCancel = cview.findViewById(R.id.button_cancel);
        ProgressBar progressBar = cview.findViewById(R.id.progressBar);
        btnOK.setEnabled(true);
        btnOK.setText(R.string.analyst);

        double block = Math.min(selections.size(), MAX_PROGRESS_BLOCK);
        double sizeInBlock = MAX_PROGRESS / block;
        List<Long> valueList = new ArrayList<>();

        for (int i = 0; i < block; i++) {
            valueList.add((long) sizeInBlock);
        }

       // final double rate = 100.00 / selections.size();
        int barColor = getColor(R.color.material_color_green_400);
        progressBar.setProgressDrawable(new RatioSegmentedProgressBarDrawable(barColor, Color.GRAY, valueList, 8f));
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

            operationTask.measureDR(getApplicationContext(), selections,
                    new FileOperationTask.ProgressCallback() {
                        @Override
                        public void onProgress(MusicTag tag, int progress, String status) {
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

    private void doEncodeAudioFiles(List<MusicTag> selections) {
        if (selections.isEmpty()) return;

        View cview = getLayoutInflater().inflate(R.layout.view_action_encoding_files, null);

        Map<MusicTag, String> statusList = new HashMap<>();
        ListView itemsView = cview.findViewById(R.id.itemListView);
        AutoCompleteTextView outputFormat = cview.findViewById(R.id.output_format);
        MaterialButton btnOK = cview.findViewById(R.id.button_encode_file);
        View btnCancel = cview.findViewById(R.id.button_cancel);
        ProgressBar progressBar = cview.findViewById(R.id.progressBar);

        btnOK.setText(R.string.convert);

        String[] outputFormatList = getResources().getStringArray(R.array.output_formats);
        setupListValuePopupFullList(outputFormat, Arrays.asList(outputFormatList));
        outputFormat.setText(outputFormatList[1]); // set default, flac standard compress

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

                MusicTag tag = selections.get(i);
                TextView seq = view.findViewById(R.id.seq);
                TextView name = view.findViewById(R.id.name);
                TextView status = view.findViewById(R.id.status);

                seq.setText(String.valueOf(i + 1));
                status.setText(statusList.getOrDefault(tag, "-"));
                name.setText(tag.getSimpleName());

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
        progressBar.setProgressDrawable(new RatioSegmentedProgressBarDrawable(barColor, Color.GRAY, valueList, 8f));
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


        int compressionLevel = FLAC_STANDARD_COMPRESS_LEVEL;
        String targetExt;
        String selectedFormat = outputFormat.getText().toString();
        if(selectedFormat.contains(".aiff")) {
            targetExt = FILE_AIFF;
        }else if(selectedFormat.contains(".mp3")) {
            targetExt = FILE_MP3;
        }else {
            compressionLevel = selectedFormat.contains("Uncompressed")?FLAC_UNCOMPRESS_LEVEL:FLAC_STANDARD_COMPRESS_LEVEL;
            targetExt = FILE_FLAC;
        }

        final String finalTargetExt = targetExt.toLowerCase();
        final int finalCompressionLevel = compressionLevel;

        btnOK.setOnClickListener(v -> {
            busy = true;
            btnOK.setEnabled(false);
           // btnOK.setVisibility(GONE);

            progressBar.setProgress(FileOperationTask.getInitialProgress(selections.size()));

            operationTask.encodeFiles(getApplicationContext(), selections, finalTargetExt, finalCompressionLevel,
                    new FileOperationTask.ProgressCallback() {
                        @Override
                        public void onProgress(MusicTag tag, int progress, String status) {
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
            Object item = adapter.getMusicTag(position);

            // If the item IS a MusicFolder, REJECT any state change.
            // This prevents it from being selected.
            return !(item instanceof MusicFolder);
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

            Object item = adapter.getMusicTag(position);

            // REJECT state change for MusicFolder
            return !(item instanceof MusicFolder);
        }

        @Override
        public boolean canSelectMultiple() {
            // You still want to allow multi-select for the files
            return true;
        }

        private boolean isSelectionBlocked() {
            // This is the only guard we need.
            // We block if any scroll-related activity (drag, fling, or
            // stopping) has happened in the last 200 milliseconds.
            return (SystemClock.elapsedRealtime() - lastScrollEventTime < 200);
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
            if (id == R.id.action_delete) {
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
            } else if (id == R.id.action_measure_dr) {
                doMeasureDR(getSelections());
                mode.finish();
                return true;
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

        private List<MusicTag> getSelections() {
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
}
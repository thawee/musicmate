package apincer.android.mmate.ui;

import static apincer.android.mmate.Constants.FLAC_NO_COMPRESS_LEVEL;
import static apincer.android.mmate.Constants.FLAC_OPTIMAL_COMPRESS_LEVEL;
import static apincer.android.mmate.Constants.TITLE_DSD;
import static apincer.android.mmate.Constants.TITLE_GENRE;
import static apincer.android.mmate.Constants.TITLE_GROUPING;
import static apincer.android.mmate.Constants.TITLE_LIBRARY;
import static apincer.android.mmate.Constants.TITLE_PCM;
import static apincer.android.mmate.utils.StringUtils.isEmpty;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
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
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anggrayudi.storage.file.DocumentFileCompat;
import com.balsikandar.crashreporter.ui.CrashReporterActivity;
import com.developer.filepicker.model.DialogConfigs;
import com.developer.filepicker.model.DialogProperties;
import com.developer.filepicker.view.FilePickerDialog;
import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.skydoves.powermenu.MenuAnimation;
import com.skydoves.powermenu.PowerMenu;
import com.skydoves.powermenu.PowerMenuItem;
import com.skydoves.powerspinner.IconSpinnerAdapter;
import com.skydoves.powerspinner.IconSpinnerItem;
import com.skydoves.powerspinner.PowerSpinnerView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import apincer.android.mmate.Constants;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.Settings;
import apincer.android.mmate.notification.AudioTagEditEvent;
import apincer.android.mmate.notification.AudioTagEditResultEvent;
import apincer.android.mmate.notification.AudioTagPlayingEvent;
import apincer.android.mmate.player.PlayerInfo;
import apincer.android.mmate.provider.CoverartFetcher;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.SearchCriteria;
import apincer.android.mmate.repository.TagRepository;
import apincer.android.mmate.ui.view.BottomOffsetDecoration;
import apincer.android.mmate.ui.viewmodel.MainViewModel;
import apincer.android.mmate.ui.widget.RatioSegmentedProgressBarDrawable;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.AudioOutputHelper;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.PLSBuilder;
import apincer.android.mmate.utils.PermissionUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mmate.utils.UIUtils;
import apincer.android.mmate.worker.FileOperationTask;
import apincer.android.mmate.worker.MusicMateExecutors;
import apincer.android.mmate.worker.ScanAudioFileWorker;
import apincer.android.residemenu.ResideMenu;
import apincer.android.utils.FileUtils;
import cn.iwgang.simplifyspan.SimplifySpanBuild;
import cn.iwgang.simplifyspan.other.SpecialGravity;
import cn.iwgang.simplifyspan.unit.SpecialClickableUnit;
import cn.iwgang.simplifyspan.unit.SpecialLabelUnit;
import cn.iwgang.simplifyspan.unit.SpecialTextUnit;
import coil3.ImageLoader;
import coil3.SingletonImageLoader;
import coil3.request.ImageRequest;
import coil3.target.ImageViewTarget;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;
import sakout.mehdi.StateViews.StateView;

/**
 * Main Activity for MusicMate application
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // Constants
    private static final int RECYCLEVIEW_ITEM_SCROLLING_OFFSET = 16;
    private static final int RECYCLEVIEW_ITEM_OFFSET = 48;
    private static final double MAX_PROGRESS_BLOCK = 10.00;
    private static final double MAX_PROGRESS = 100.00;

    // File format constants
    public static final String FLAC_OPTIMAL = "FLAC (Optimal)";
    public static final String FLAC_LEVEL_0 = "FLAC (No Compression)";
    public static final String FILE_FLAC = "FLAC";
    public static final String FILE_AIFF = "AIFF";
    public static final String FILE_MP3 = "MP3";
    public static final String MP3_320_KHZ = "MPEG-3";
    public static final String AIFF = "AIFF";

    // Activity result launcher
    ActivityResultLauncher<Intent> permissionResultLauncher;

    // ViewModel and Repository
    private MainViewModel viewModel;
    private FileRepository repos;

    // UI components
    private ResideMenu mResideMenu;
    private MusicTagAdapter adapter;
    private SelectionTracker<Long> mTracker;
    private final List<MusicTag> selections = new ArrayList<>();
    private Snackbar mExitSnackbar;
    private View mHeaderPanel;
    private TextView titleLabel;
    private TextView titleText;
    private SearchView mSearchView;
    private RefreshLayout refreshLayout;
    private StateView mStateView;
    private RecyclerView mRecyclerView;
    private TextView headerSubtitle;

    // Now playing components
    private NowPlayingManager nowPlayingManager;

    // Action mode
    private ActionModeCallback actionModeCallback;
    private ActionMode actionMode;

    // State variables
    private Timer timer;
    private volatile boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Setup night mode
        if (Settings.isOnNightModeOnly(getApplicationContext())) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
        super.onCreate(savedInstanceState);

        // Start music scan
        MusixMateApp.getInstance().startMusicScan();

        // Setup status bar
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(this, android.R.color.black));

        // Get search criteria from intent
        SearchCriteria searchCriteria = ApplicationUtils.getSearchCriteria(getIntent());

        // Setup back press handler
        OnBackPressedCallback onBackPressedCallback = new BackPressedCallback(true);
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);

        // Initialize repository
        repos = FileRepository.newInstance(getApplicationContext());

        // Set content view
        setContentView(R.layout.activity_main);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        // Setup Activity Result Launchers
        setupActivityResultLaunchers();

        // Setup UI components
        setUpHeaderPanel();
        setUpNowPlayingView();
        setUpBottomAppBar();
        setUpRecycleView(searchCriteria);
        setUpSwipeToRefresh();
        setUpResideMenus();

        // Observe ViewModel LiveData
        observeViewModel();

        // Setup exit snackbar
        mExitSnackbar = Snackbar.make(mRecyclerView, R.string.alert_back_to_exit, Snackbar.LENGTH_LONG);
        View snackBarView = mExitSnackbar.getView();
        snackBarView.setBackgroundColor(getColor(R.color.warningColor));
    }

    private void setupActivityResultLaunchers() {
        permissionResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> ScanAudioFileWorker.startScan(getApplicationContext())
        );
    }

    private void observeViewModel() {
        viewModel.getMusicTags().observe(this, musicTags -> {
            adapter.setMusicTags(musicTags);
            refreshLayout.finishRefresh();
            updateHeaderPanel();
            if (adapter.getItemCount() == 0) {
                mStateView.displayState("search");
            } else {
                mStateView.hideStates();
            }
        });

        viewModel.getIsLoading().observe(this, isLoading -> {
            if (isLoading) {
                refreshLayout.autoRefresh();
            } else {
                refreshLayout.finishRefresh();
            }
        });
    }

    private void setUpHeaderPanel() {
        mHeaderPanel = findViewById(R.id.header_panel);
        titleLabel = findViewById(R.id.title_label);
        titleText = findViewById(R.id.title_text);
        titleText.setOnClickListener(v -> {
            adapter.resetFilter();
            adapter.setKeyword(titleText.getText().toString());
            refreshLayout.autoRefresh();
        });

        headerSubtitle = findViewById(R.id.header_subtitle);
    }

    private void setUpNowPlayingView() {
        // Initialize components
        View nowPlayingView = findViewById(R.id.now_playing_panel);
        ImageView nowPlayingCoverArt = findViewById(R.id.now_playing_coverart);
        ImageView nowPlayingPlayer = findViewById(R.id.now_playing_player);
        ImageView nowPlayingOutputDevice = findViewById(R.id.now_playing_device);

        // Create manager
        nowPlayingManager = new NowPlayingManager(
                getApplicationContext(),
                nowPlayingView,
                nowPlayingCoverArt,
                nowPlayingPlayer,
                nowPlayingOutputDevice
        );

        // Setup click listeners
        nowPlayingView.setOnClickListener(view1 -> scrollToListening());
        nowPlayingView.setOnLongClickListener(view1 -> {
            MusixMateApp.getPlayerControl().playNextSong(getApplicationContext());
            return true;
        });

        // Hide initially
        nowPlayingView.setVisibility(View.GONE);
    }

    private void setUpBottomAppBar() {
        // Find components
        BottomAppBar bottomAppBar = findViewById(R.id.bottom_app_bar);
        setSupportActionBar(bottomAppBar);

        ImageView leftMenu = bottomAppBar.findViewById(R.id.navigation_collections);
        UIUtils.getTintedDrawable(leftMenu.getDrawable(), Color.WHITE);

        ImageView rightMenu = bottomAppBar.findViewById(R.id.navigation_settings);
        UIUtils.getTintedDrawable(rightMenu.getDrawable(), Color.WHITE);

        mSearchView = bottomAppBar.findViewById(R.id.searchView);

        // Setup search listener
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.setSearchString(query);
                refreshLayout.autoRefresh();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (isEmpty(newText) || newText.length() >= 3) {
                    adapter.setSearchString(newText);
                    refreshLayout.autoRefresh();
                }
                return false;
            }
        });

        mSearchView.setOnSearchClickListener(v -> {
            adapter.setSearchString(String.valueOf(mSearchView.getQuery()));
            refreshLayout.autoRefresh();
        });

        mSearchView.setOnCloseListener(() -> {
            doHideSearch();
            refreshLayout.autoRefresh();
            return false;
        });

        // Setup menu click listeners
        leftMenu.setOnClickListener(v -> doShowLeftMenus());
        rightMenu.setOnClickListener(v -> doShowRightMenus());
    }

    private void setUpSwipeToRefresh() {
        refreshLayout = findViewById(R.id.refreshLayout);
        refreshLayout.setOnRefreshListener(refreshlayout -> viewModel.loadMusicTags(adapter.getCriteria()));
    }

    private void setUpRecycleView(SearchCriteria searchCriteria) {
        if (searchCriteria == null) {
            searchCriteria = new SearchCriteria(SearchCriteria.TYPE.MY_SONGS);
        }

        // Initialize adapter
        adapter = new MusicTagAdapter(searchCriteria);
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                refreshLayout.finishRefresh();
                updateHeaderPanel();
                if (adapter.getItemCount() == 0) {
                    mStateView.displayState("search");
                } else {
                    mStateView.hideStates();
                }
            }
        });

        // Setup RecyclerView
        mRecyclerView = findViewById(R.id.recycler_view);
        mRecyclerView.setItemViewCacheSize(0);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setAdapter(adapter);
        mRecyclerView.setHasFixedSize(true);

        // Add bottom padding
        RecyclerView.ItemDecoration itemDecoration = new BottomOffsetDecoration(64);
        mRecyclerView.addItemDecoration(itemDecoration);
        mRecyclerView.setPreserveFocusAfterLayout(true);

        // Setup item click listener
        MusicTagAdapter.OnListItemClick onListItemClick = (view, position) ->
                doShowEditActivity(Collections.singletonList(adapter.getMusicTag(position)));
        adapter.setClickListener(onListItemClick);

        // Setup selection tracker
        mTracker = new SelectionTracker.Builder<>(
                "selection-id",
                mRecyclerView,
                new MusicTagAdapter.KeyProvider(),
                new MusicTagAdapter.DetailsLookup(mRecyclerView),
                StorageStrategy.createLongStorage())
                .withSelectionPredicate(SelectionPredicates.createSelectAnything())
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
                .useMd2Style()
                .setPadding(0, 0, 8, 0)
                .setThumbDrawable(Objects.requireNonNull(
                        ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_fastscroll_thumb)))
                .build();

        // Setup scroll listener
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    MusicTag currentlyPlaying = MusixMateApp.getPlayerControl().getPlayingSong();
                    if (currentlyPlaying != null) {
                        nowPlayingManager.showNowPlaying(currentlyPlaying);
                    }
                } else {
                    nowPlayingManager.hideNowPlaying();
                }
            }
        });

        // Initialize action mode callback
        actionModeCallback = new ActionModeCallback();

        // Setup state view
        mStateView = findViewById(R.id.status_page);
        mStateView.hideStates();
    }

    private void setUpResideMenus() {
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
            UIUtils.buildStoragesStatus(getApplication(), panel);
            mResideMenu.setLeftHeader(storageView);
        }
        mResideMenu.openMenu(ResideMenu.DIRECTION_LEFT);
    }

    private void doShowRightMenus() {
        mResideMenu.openMenu(ResideMenu.DIRECTION_RIGHT);
    }

    private void updateHeaderPanel() {
        List<String> titles = adapter.getHeaderTitles(getApplicationContext());
        String headerTitle = adapter.getHeaderTitle();

        String label = adapter.getHeaderLabel();
        Drawable icon = null;

        // Set appropriate icon based on label
        if (TITLE_LIBRARY.equals(label)) {
            icon = ContextCompat.getDrawable(getBaseContext(), R.drawable.ic_round_library_music_24);
        } else if (TITLE_GENRE.equals(label)) {
            icon = ContextCompat.getDrawable(getBaseContext(), R.drawable.ic_round_style_24);
        } else if (TITLE_GROUPING.equals(label)) {
            icon = ContextCompat.getDrawable(getBaseContext(), R.drawable.ic_round_local_play_24);
        } else if (TITLE_PCM.equals(label)) {
            icon = UIUtils.getTintedDrawable(getBaseContext(), R.drawable.ic_sound_wave, Color.WHITE);
        } else if (TITLE_DSD.equals(label)) {
            icon = ContextCompat.getDrawable(getBaseContext(), R.drawable.ic_format_dsd_white);
        }

        titleLabel.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
        titleLabel.setText(label);
        titleText.setText(headerTitle);

        // Set click listener for title
        titleText.setOnClickListener(v -> {
            List<PowerMenuItem> items = new ArrayList<>();
            titles.forEach(s -> items.add(new PowerMenuItem(s)));
            int height = UIUtils.getScreenHeight(this) / 2;
            int width = UIUtils.getScreenWidth(this) / 2;

            PowerMenu powerMenu = new PowerMenu.Builder(this)
                    .setWidth(width)
                    .setHeight(height)
                    .setLifecycleOwner(MainActivity.this)
                    .addItemList(items)
                    .setAnimation(MenuAnimation.SHOWUP_TOP_LEFT)
                    .setMenuRadius(16f)
                    .setMenuShadow(8f)
                    .setTextColor(ContextCompat.getColor(getBaseContext(), R.color.grey200))
                    .setTextGravity(Gravity.START)
                    .setPadding(1)
                    .setMenuRadius(8)
                    .setShowBackground(true)
                    .setTextSize(16)
                    .setSelectedTextColor(Color.WHITE)
                    .setMenuColor(ContextCompat.getColor(getBaseContext(), R.color.colorPrimary_light))
                    .setSelectedMenuColor(ContextCompat.getColor(getBaseContext(), apincer.android.library.R.color.colorPrimary))
                    .setAutoDismiss(true)
                    .setDividerHeight(1)
                    .setSelectedEffect(false)
                    .setOnMenuItemClickListener((position, item) -> {
                        adapter.resetFilter();
                        adapter.setKeyword(String.valueOf(item.title));
                        refreshLayout.autoRefresh();
                    })
                    .build();

            powerMenu.showAsDropDown(titleText, -96, 0);
        });

        // Build statistics text
        int count = adapter.getTotalSongs();
        long totalSize = adapter.getTotalSize();
        String duration = StringUtils.formatDuration(adapter.getTotalDuration(), true);

        SimplifySpanBuild spannable = new SimplifySpanBuild("");
        if (count > 0) {
            SearchCriteria criteria = adapter.getCriteria();
            if (!isEmpty(criteria.getFilterType())) {
                String filterType = criteria.getFilterType();
                spannable.appendMultiClickable(
                        new SpecialClickableUnit(headerSubtitle, (tv, clickableSpan) -> adapter.resetFilter())
                                .setNormalTextColor(getColor(R.color.grey200)),
                        new SpecialTextUnit("[" + filterType + "]  ").setTextSize(10)
                );
            }

            spannable.append(new SpecialTextUnit(StringUtils.formatSongSize(count)).setTextSize(12).useTextBold())
                    .append(new SpecialLabelUnit(StringUtils.SYMBOL_HEADER_SEP,
                            ContextCompat.getColor(getApplicationContext(), R.color.grey100),
                            UIUtils.sp2px(getApplication(), 10),
                            Color.TRANSPARENT)
                            .setPadding(5).setPaddingLeft(10).setPaddingRight(10).setGravity(SpecialGravity.CENTER))
                    .append(new SpecialTextUnit(StringUtils.formatStorageSize(totalSize)).setTextSize(12).useTextBold())
                    .append(new SpecialLabelUnit(StringUtils.SYMBOL_HEADER_SEP,
                            ContextCompat.getColor(getApplicationContext(), R.color.grey100),
                            UIUtils.sp2px(getApplication(), 10),
                            Color.TRANSPARENT)
                            .setPadding(5).setPaddingLeft(10).setPaddingRight(10).setGravity(SpecialGravity.CENTER))
                    .append(new SpecialTextUnit(duration).setTextSize(12).useTextBold());
        } else {
            if (adapter.hasFilter()) {
                spannable.append(new SpecialTextUnit("No Results for filter: " +
                        StringUtils.trimToEmpty(adapter.getCriteria().getFilterText()))
                        .setTextSize(12).useTextBold());
            } else {
                spannable.append(new SpecialTextUnit("No Results").setTextSize(12).useTextBold());
            }
        }

        headerSubtitle.setText(spannable.build());
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mResideMenu.isOpened()) {
            mResideMenu.closeMenu();
        }

        // Load music items consistently through ViewModel
        if (MusixMateApp.getInstance().getCriteria() != null) {
            viewModel.loadMusicTags(MusixMateApp.getInstance().getCriteria());
        } else {
            viewModel.loadMusicTags(adapter.getCriteria());
        }

        // Show now playing panel
        MusicTag currentlyPlaying = MusixMateApp.getPlayerControl().getPlayingSong();
        if (currentlyPlaying != null) {
            nowPlayingManager.showNowPlaying(currentlyPlaying);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        super.onDestroy();
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onMessageEvent(AudioTagPlayingEvent event) {
        MusicMateExecutors.executeUI(() -> {
            MusicTag newPlayingSong = event.getPlayingSong();
            MusicTag previousPlayingSong = nowPlayingManager.getCurrentlyPlaying();

            // Update UI for previous playing song if it exists and is different
            if (previousPlayingSong != null && !previousPlayingSong.equals(newPlayingSong)) {
                int previousPosition = adapter.getMusicTagPosition(previousPlayingSong);
                if (previousPosition != RecyclerView.NO_POSITION) {
                    adapter.notifyItemChanged(previousPosition);
                }
            }

            // Update UI for new playing song
            if (newPlayingSong != null) {
                int newPosition = adapter.getMusicTagPosition(newPlayingSong);
                if (newPosition != RecyclerView.NO_POSITION) {
                    adapter.notifyItemChanged(newPosition);
                }
            }

            // Then proceed with the current implementation
            onPlaying(newPlayingSong);
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onMessageEvent(AudioTagEditResultEvent event) {
        try {
            MusicMateExecutors.executeUI(() -> {
                if (event.getItem() != null) {
                    int position = adapter.getMusicTagPosition(event.getItem());
                    if (AudioTagEditResultEvent.ACTION_DELETE.equals(event.getAction())) {
                        adapter.removeMusicTag(position, event.getItem());
                    } else if (adapter.isMatchFilter(event.getItem())) {
                        //adapter.loadDataSets();
                        viewModel.loadMusicTags(adapter.getCriteria());
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "onMessageEvent", e);
        }
    }

    public void onPlaying(MusicTag song) {
        if (song != null) {
            runOnUiThread(() -> nowPlayingManager.showNowPlaying(song));

            if (Settings.isListFollowNowPlaying(getBaseContext())) {
                if (timer != null) {
                    timer.cancel();
                }
                timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        if (!busy) {
                            runOnUiThread(() -> scrollToListening());
                        }
                    }
                }, 500); // 0.5 seconds
            }
        }
    }

    private void scrollToListening() {
        MusicTag currentlyPlaying = nowPlayingManager.getCurrentlyPlaying();
        if (currentlyPlaying == null) return;

        int positionToScroll = adapter.getMusicTagPosition(currentlyPlaying);
        scrollToPosition(positionToScroll, true);
    }

    private void scrollToPosition(int position, boolean offset) {
        if (position != RecyclerView.NO_POSITION) {
            if (offset) {
                int positionWithOffset = position - RECYCLEVIEW_ITEM_SCROLLING_OFFSET;
                if (positionWithOffset < 0) {
                    positionWithOffset = 0;
                }
                mRecyclerView.scrollToPosition(positionWithOffset);
            }
            if (position - 1 > RecyclerView.NO_POSITION) {
                // show as 2nd item on screen
                position = position - 1;
            }
            LinearLayoutManager layoutManager = (LinearLayoutManager) mRecyclerView.getLayoutManager();
            Objects.requireNonNull(layoutManager).scrollToPositionWithOffset(position, RECYCLEVIEW_ITEM_OFFSET);
        }
    }

    private void doHideSearch() {
        adapter.setSearchString("");
        refreshLayout.autoRefresh();
    }

    private void doStartRefresh(SearchCriteria.TYPE type, String keyword) {
        adapter.setType(type);
        adapter.setKeyword(keyword);
        refreshLayout.autoRefresh();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.menu_all_music) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.MY_SONGS, null);
            return true;
        } else if (item.getItemId() == R.id.menu_encodings) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.AUDIO_ENCODINGS, Constants.TITLE_HIGH_QUALITY);
            return true;
        } else if (item.getItemId() == R.id.menu_groupings) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.GROUPING, TagRepository.getActualGroupingList(getApplicationContext()).get(0));
            return true;
        } else if (item.getItemId() == R.id.menu_tag_genre) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.GENRE, TagRepository.getActualGenreList(getApplicationContext()).get(0));
            return true;
        } else if (item.getItemId() == R.id.menu_settings) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
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
            doSetDirectories();
            return true;
        } else if (item.getItemId() == R.id.menu_export_playlists) {
            doExportPlaylists();
            return true;
        } else if (item.getItemId() == R.id.menu_about_crash) {
            Intent intent = new Intent(MainActivity.this, CrashReporterActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void doShowAboutApp() {
        Intent intent = new Intent(MainActivity.this, AboutActivity.class);
        startActivity(intent);
    }

    private void doExportPlaylists() {
        MusicMateExecutors.execute(() -> {
            PLSBuilder.exportPlaylists(getApplicationContext());
            runOnUiThread(() -> {
                Snackbar snackbar = Snackbar.make(mSearchView, "Playlists exported successfully", Snackbar.LENGTH_SHORT);
                snackbar.setAction("Dismiss", view -> snackbar.dismiss());
                snackbar.setActionTextColor(Color.BLUE);
                snackbar.show();
            });
        });
    }

    private void doSetDirectories() {
        if (!PermissionUtils.checkAccessPermissions(getApplicationContext())) {
            Intent intent = new Intent(MainActivity.this, PermissionActivity.class);
            permissionResultLauncher.launch(intent);
            return;
        }

        View cview = getLayoutInflater().inflate(R.layout.view_action_directories, null);

        ListView itemsView = cview.findViewById(R.id.itemListView);
        LinearLayout btnAddPanel = cview.findViewById(R.id.btn_add_panel);
        View btnOK = cview.findViewById(R.id.btn_ok);
        View btnCancel = cview.findViewById(R.id.btn_cancel);

        List<String> defaultPaths = FileRepository.newInstance(getApplicationContext()).getDefaultMusicPaths();
        Set<String> defaultPathsSet = new HashSet<>(defaultPaths);
        List<String> dirs = Settings.getDirectories(getApplicationContext());

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
            btn.setText(sid);
            btnAddPanel.addView(btn);

            btn.setOnClickListener(view -> {
                DialogProperties properties = new DialogProperties();
                properties.selection_mode = DialogConfigs.SINGLE_MODE;
                properties.selection_type = DialogConfigs.DIR_SELECT;
                properties.root = new File(DocumentFileCompat.buildAbsolutePath(getApplicationContext(), sid, ""));
                properties.extensions = null;
                properties.show_hidden_files = false;

                FilePickerDialog dialog = new FilePickerDialog(MainActivity.this, properties);
                dialog.setTitle("Select Directory");
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
                        .setMessage(getString(R.string.alert_invalid_media_file, tag.getPath()))
                        .setPositiveButton("GOT IT", (dialogInterface, i) -> {
                            repos.deleteMediaItem(tag);
                           // adapter.loadDataSets();
                            viewModel.loadMusicTags(adapter.getCriteria());
                            dialogInterface.dismiss();
                        })
                        .show();
            }
        }

        if (!tagList.isEmpty()) {
            Intent intent = new Intent(MainActivity.this, TagsActivity.class);
            AudioTagEditEvent message = new AudioTagEditEvent("edit", adapter.getCriteria(), tagList);
            EventBus.getDefault().postSticky(message);
            startActivity(intent);
        }
    }

    private void doDeleteMediaItems(List<MusicTag> selections) {
        if (selections.isEmpty()) return;

        View cview = getLayoutInflater().inflate(R.layout.view_action_files, null);

        Map<MusicTag, String> statusList = new HashMap<>();
        ListView itemsView = cview.findViewById(R.id.itemListView);
        TextView titleText = cview.findViewById(R.id.title);
        titleText.setText(R.string.title_removing_music_files);

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
                name.setText(FileUtils.getFileName(tag.getPath()));

                return view;
            }
        });

        View btnOK = cview.findViewById(R.id.btn_ok);
        View btnCancel = cview.findViewById(R.id.btn_cancel);
        ProgressBar progressBar = cview.findViewById(R.id.progressBar);
        btnOK.setEnabled(true);

        double block = Math.min(selections.size(), MAX_PROGRESS_BLOCK);
        double sizeInBlock = MAX_PROGRESS / block;
        List<Long> valueList = new ArrayList<>();

        for (int i = 0; i < block; i++) {
            valueList.add((long) sizeInBlock);
        }

        final double rate = 100.00 / selections.size();
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
            btnOK.setVisibility(View.GONE);

            progressBar.setProgress(FileOperationTask.getInitialProgress(selections.size()));

            FileOperationTask.deleteFiles(getApplicationContext(), selections,
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
                            viewModel.deleteMediaItems(selections);
                            runOnUiThread(() -> busy = false);
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
        titleText.setText(R.string.title_import_to_music_directory);

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
                name.setText(FileUtils.getFileName(tag.getPath()));

                return view;
            }
        });

        View btnOK = cview.findViewById(R.id.btn_ok);
        View btnCancel = cview.findViewById(R.id.btn_cancel);
        ProgressBar progressBar = cview.findViewById(R.id.progressBar);
        btnOK.setEnabled(true);

        double block = Math.min(selections.size(), MAX_PROGRESS_BLOCK);
        double sizeInBlock = MAX_PROGRESS / block;
        List<Long> valueList = new ArrayList<>();

        for (int i = 0; i < block; i++) {
            valueList.add((long) sizeInBlock);
        }

        final double rate = 100.00 / selections.size();
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
            btnOK.setVisibility(View.GONE);

            progressBar.setProgress(FileOperationTask.getInitialProgress(selections.size()));

            FileOperationTask.moveFiles(getApplicationContext(), selections,
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
                            viewModel.moveMediaItems(selections);
                            runOnUiThread(() -> busy = false);
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
        titleText.setText(R.string.title_dynamic_range_and_replay_gain);

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
                name.setText(FileUtils.getFileName(tag.getPath()));

                return view;
            }
        });

        View btnOK = cview.findViewById(R.id.btn_ok);
        View btnCancel = cview.findViewById(R.id.btn_cancel);
        ProgressBar progressBar = cview.findViewById(R.id.progressBar);
        btnOK.setEnabled(true);

        double block = Math.min(selections.size(), MAX_PROGRESS_BLOCK);
        double sizeInBlock = MAX_PROGRESS / block;
        List<Long> valueList = new ArrayList<>();

        for (int i = 0; i < block; i++) {
            valueList.add((long) sizeInBlock);
        }

        final double rate = 100.00 / selections.size();
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
            btnOK.setVisibility(View.GONE);

            progressBar.setProgress(FileOperationTask.getInitialProgress(selections.size()));

            FileOperationTask.measureDR(getApplicationContext(), selections,
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
                            runOnUiThread(() -> busy = false);
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
        PowerSpinnerView encodingList = cview.findViewById(R.id.audioEncoding);
        View btnOK = cview.findViewById(R.id.btn_ok);
        View btnCancel = cview.findViewById(R.id.btn_cancel);
        ProgressBar progressBar = cview.findViewById(R.id.progressBar);

        List<IconSpinnerItem> iconSpinnerItems = new ArrayList<>();

        // Determine possible output formats based on input file type
        if (MusicTagUtils.isAIFFile(selections.get(0)) ||
                MusicTagUtils.isALACFile(selections.get(0)) ||
                MusicTagUtils.isDSD(selections.get(0))) {
            iconSpinnerItems.add(new IconSpinnerItem(FLAC_LEVEL_0, null));
            iconSpinnerItems.add(new IconSpinnerItem(FLAC_OPTIMAL, null));
        } else if (MusicTagUtils.isAACFile(selections.get(0))) {
            iconSpinnerItems.add(new IconSpinnerItem(MP3_320_KHZ, null));
        } else if (MusicTagUtils.isFLACFile(selections.get(0))) {
            iconSpinnerItems.add(new IconSpinnerItem(FLAC_LEVEL_0, null));
            iconSpinnerItems.add(new IconSpinnerItem(FLAC_OPTIMAL, null));
            iconSpinnerItems.add(new IconSpinnerItem(AIFF, null));
        } else if (MusicTagUtils.isWavFile(selections.get(0))) {
            iconSpinnerItems.add(new IconSpinnerItem(FLAC_LEVEL_0, null));
            iconSpinnerItems.add(new IconSpinnerItem(FLAC_OPTIMAL, null));
            iconSpinnerItems.add(new IconSpinnerItem(AIFF, null));
        }

        // Setup spinner
        IconSpinnerAdapter iconSpinnerAdapter = new IconSpinnerAdapter(encodingList);
        encodingList.setSpinnerAdapter(iconSpinnerAdapter);
        encodingList.setItems(iconSpinnerItems);

        if (!iconSpinnerItems.isEmpty()) {
            encodingList.selectItemByIndex(0);
            btnOK.setEnabled(true);
        }

        encodingList.setLifecycleOwner(this);

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
                name.setText(FileUtils.getFileName(tag.getPath()));

                return view;
            }
        });

        double block = Math.min(selections.size(), MAX_PROGRESS_BLOCK);
        double sizeInBlock = MAX_PROGRESS / block;
        List<Long> valueList = new ArrayList<>();

        for (int i = 0; i < block; i++) {
            valueList.add((long) sizeInBlock);
        }

        final double rate = 100.00 / selections.size();
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
            btnOK.setVisibility(View.GONE);

            int compressionLevel = FLAC_OPTIMAL_COMPRESS_LEVEL;
            String targetExt = FILE_FLAC;

            IconSpinnerItem item = iconSpinnerItems.get(encodingList.getSelectedIndex());
            if (FLAC_OPTIMAL.contentEquals(item.getText())) {
                targetExt = FILE_FLAC;
                compressionLevel = FLAC_OPTIMAL_COMPRESS_LEVEL;
            } else if (FLAC_LEVEL_0.contentEquals(item.getText())) {
                targetExt = FILE_FLAC;
                compressionLevel = FLAC_NO_COMPRESS_LEVEL;
            } else if (MP3_320_KHZ.contentEquals(item.getText())) {
                targetExt = FILE_MP3;
            } else if (AIFF.contentEquals(item.getText())) {
                targetExt = FILE_AIFF;
            }

            progressBar.setProgress(FileOperationTask.getInitialProgress(selections.size()));

            final String finalTargetExt = targetExt.toLowerCase();
            final int finalCompressionLevel = compressionLevel;

            FileOperationTask.encodeFiles(getApplicationContext(), selections, finalTargetExt, finalCompressionLevel,
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
                            runOnUiThread(() -> busy = false);
                        }
                    });
        });

        btnCancel.setOnClickListener(v -> {
            alert.dismiss();
            busy = false;
        });

        alert.show();
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
            mHeaderPanel.setVisibility(View.GONE);
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
            } else if (id == R.id.action_calculate_replay_gain) {
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
            mHeaderPanel.setVisibility(View.VISIBLE);
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

            if (adapter != null && adapter.isSearchMode()) {
                doHideSearch();
                refreshLayout.autoRefresh();
                return;
            }

            if (adapter != null && adapter.hasFilter()) {
                adapter.resetFilter();
                refreshLayout.autoRefresh();
                return;
            }

            // If not on first item in library
            if (adapter != null && !adapter.isFirstItem(getApplicationContext())) {
                adapter.resetSelectedItem();
                refreshLayout.autoRefresh();
                return;
            }

            if (!mExitSnackbar.isShown()) {
                mExitSnackbar.show();
            } else {
                mExitSnackbar.dismiss();
                finishAndRemoveTask();
            }
        }
    }

    /**
     * NowPlayingManager class abstraction implementation
     */
    public class NowPlayingManager {
        private final Context context;
        private final View nowPlayingView;
        private final ImageView coverArtImageView;
        private final ImageView playerImageView;
        private final ImageView outputDeviceImageView;
        private MusicTag currentlyPlaying;

        public NowPlayingManager(Context context, View nowPlayingView,
                                 ImageView coverArtImageView,
                                 ImageView playerImageView,
                                 ImageView outputDeviceImageView) {
            this.context = context;
            this.nowPlayingView = nowPlayingView;
            this.coverArtImageView = coverArtImageView;
            this.playerImageView = playerImageView;
            this.outputDeviceImageView = outputDeviceImageView;
        }

        public void showNowPlaying(MusicTag song) {
            if (song == null) {
                hideNowPlaying();
                return;
            }

            currentlyPlaying = song;

            // Load cover art
            ImageLoader imageLoader = SingletonImageLoader.get(context);
            ImageRequest request = CoverartFetcher.builder(context, song)
                    .data(song)
                    .target(new ImageViewTarget(coverArtImageView))
                    .error(imageRequest -> CoverartFetcher.getDefaultCover(context))
                    .build();
                  //  .let(request -> SingletonImageLoader.get(context).enqueue(request));
            imageLoader.enqueue(request);

            // Set player icon
            PlayerInfo player = MusixMateApp.getPlayerControl().getPlayerInfo();
            if (player != null) {
                playerImageView.setImageDrawable(player.getPlayerIconDrawable());
            }

            // Set output device icon
            MusicMateExecutors.executeUI(() -> {
                if (player != null) {
                    if (player.isStreamPlayer()) {
                        outputDeviceImageView.setVisibility(View.VISIBLE);
                        outputDeviceImageView.setImageBitmap(
                                AudioOutputHelper.getOutputDeviceIcon(
                                        context,
                                        AudioOutputHelper.getDLNADevice(song)
                                )
                        );
                    } else {
                        outputDeviceImageView.setVisibility(View.VISIBLE);
                        AudioOutputHelper.getOutputDevice(context, device ->
                                outputDeviceImageView.setImageBitmap(
                                        AudioOutputHelper.getOutputDeviceIcon(context, device)
                                )
                        );
                    }
                }

                // Animate view to show
                MainActivity.this.runOnUiThread(() ->
                        nowPlayingView.animate()
                                .scaleX(1f).scaleY(1f)
                                .alpha(1f).setDuration(250)
                                .setStartDelay(10L)
                                .setListener(new Animator.AnimatorListener() {
                                    @Override
                                    public void onAnimationStart(@NonNull Animator animator) {
                                        nowPlayingView.setVisibility(View.VISIBLE);
                                    }

                                    @Override
                                    public void onAnimationEnd(@NonNull Animator animator) {
                                        nowPlayingView.setVisibility(View.VISIBLE);
                                    }

                                    @Override
                                    public void onAnimationCancel(@NonNull Animator animator) {
                                    }

                                    @Override
                                    public void onAnimationRepeat(@NonNull Animator animator) {
                                    }
                                })
                                .start()
                );
            });
        }

        public void hideNowPlaying() {
            currentlyPlaying = null;
            MainActivity.this.runOnUiThread(() ->
                    nowPlayingView.animate()
                            .scaleX(0f).scaleY(0f)
                            .alpha(0f).setDuration(100)
                            .setStartDelay(10L)
                            .setListener(new Animator.AnimatorListener() {
                                @Override
                                public void onAnimationStart(@NonNull Animator animator) {
                                }

                                @Override
                                public void onAnimationEnd(@NonNull Animator animator) {
                                    nowPlayingView.setVisibility(View.GONE);
                                }

                                @Override
                                public void onAnimationCancel(@NonNull Animator animator) {
                                }

                                @Override
                                public void onAnimationRepeat(@NonNull Animator animator) {
                                }
                            })
                            .start()
            );
        }

        public MusicTag getCurrentlyPlaying() {
            return currentlyPlaying;
        }
    }
}

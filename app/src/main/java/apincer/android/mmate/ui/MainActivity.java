package apincer.android.mmate.ui;

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
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import apincer.android.mmate.utils.AudioOutputHelper;
import apincer.android.mmate.ui.compose.MainScaffoldState;
import apincer.music.core.playback.PlaybackState;
import apincer.music.core.repository.QueueManager;
import apincer.music.core.utils.PlayerNameUtils;
import apincer.android.utils.FileUtils;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ContextMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AutoCompleteTextView;
import android.widget.ListView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.view.ActionMode;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.anggrayudi.storage.file.DocumentFileCompat;
import com.balsikandar.crashreporter.ui.CrashReporterActivity;
import com.developer.filepicker.model.DialogConfigs;
import com.developer.filepicker.model.DialogProperties;
import com.developer.filepicker.view.FilePickerDialog;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

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

import apincer.music.core.server.spi.MediaServerHub;
import apincer.music.core.utils.ApplicationUtils;
import apincer.music.core.utils.NetworkUtils;
import apincer.music.core.utils.StringUtils;
import apincer.android.mmate.ui.viewmodel.MainViewModel;
import apincer.android.mmate.worker.FileOperationTask;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Main Activity for MusicMate application
 */
@AndroidEntryPoint
public class MainActivity extends AppCompatActivity implements apincer.android.mmate.ui.compose.MainScaffoldCallbacks {
    private static final String TAG = "MainActivity";

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

    private WorkInfo.State lastWorkState = null;

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
    private AutoCloseable playbackStateSubscription = null;
    private final androidx.lifecycle.Observer<apincer.music.core.server.spi.MediaServerHub.ServerStatus> serverStatusObserver = this::updateMediaServerState;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @SuppressLint("CheckResult")
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // Get the binder from the service and set the mediaServerService instance
            MusicMateServiceImpl.MusicMateServiceImplBinder binder = (MusicMateServiceImpl.MusicMateServiceImplBinder) service;
            playbackService = binder.getPlaybackService();
            isPlaybackServiceBound = true;
            apincer.android.mmate.ui.compose.ListInterop.updateNowPlaying(playbackService.getNowPlayingSong(), false);

            if (playbackService instanceof MusicMateServiceImpl msi) {
                runOnUiThread(() -> {
                    msi.getStatusLiveData().observe(MainActivity.this, serverStatusObserver);
                    updateMediaServerState(msi.getStatusLiveData().getValue());
                });
            }

            if (playbackStateSubscription != null) {
                try {
                    playbackStateSubscription.close();
                } catch (Exception ignored) {}
            }
            playbackStateSubscription = playbackService.subscribePlaybackState(
                    playbackState -> setNowPlaying(playbackService.getNowPlayingSong(), playbackState),
                    throwable -> Log.e(TAG, "Error in PlaybackState subscription", throwable));
            if (playbackService.getQueueManager() != null) {
                apincer.music.core.repository.QueueManager qm = playbackService.getQueueManager();
                apincer.android.mmate.ui.compose.NowPlayingState nps = apincer.android.mmate.ui.compose.MainScaffoldState.get().getNowPlayingState();
                nps.isShuffle().setValue(qm.isShuffle());
                int rMode = qm.getRepeatMode() == apincer.music.core.repository.QueueManager.RepeatMode.ALL ? 1
                        : qm.getRepeatMode() == apincer.music.core.repository.QueueManager.RepeatMode.ONE ? 2 : 0;
                nps.getRepeatMode().setValue(rMode);
            }
            updateVolumeState();
            syncQueueState();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (playbackStateSubscription != null) {
                try {
                    playbackStateSubscription.close();
                } catch (Exception ignored) {}
                playbackStateSubscription = null;
            }
            isPlaybackServiceBound = false;
            playbackService = null;
        }
    };

    private PlaybackState.State lastStateEnum = null;

    private void setNowPlaying(Track song, PlaybackState playbackState) {
        runOnUiThread(() -> {
            PlaybackState.State newStateEnum = playbackState != null ? playbackState.currentState : null;
            boolean songChanged = (song != null && !song.equals(previouslyPlaying));
            lastPlaybackState = playbackState;
            lastStateEnum = newStateEnum;

            boolean isPlaying = playbackState != null && playbackState.currentState == apincer.music.core.playback.PlaybackState.State.PLAYING;
            apincer.android.mmate.ui.compose.ListInterop.updateNowPlaying(song, isPlaying);

            String targetSubtitle = "";
            if (isPlaybackServiceBound && playbackService != null && playbackService.getPlayer() != null) {
                targetSubtitle = PlayerNameUtils.getDropdownPlayerLabel(playbackService.getPlayer());
            } else if (song != null && song.getArtist() != null) {
                targetSubtitle = song.getArtist();
            }

            float progress = 0f;
            if (song != null && song.getAudioDuration() > 0 && playbackState != null) {
                progress = (float) playbackState.currentPositionSecond / (float) song.getAudioDuration();
            }

            apincer.android.mmate.ui.compose.MainScaffoldState.updateNowPlaying(song, isPlaying, targetSubtitle, progress);

            // Update AudioHub sub-states
            apincer.android.mmate.ui.compose.NowPlayingState nps = apincer.android.mmate.ui.compose.MainScaffoldState.get().getNowPlayingState();
            nps.getPlaybackState().setValue(playbackState != null ? playbackState : new PlaybackState());
            nps.getTrack().setValue(song);
            if (song != null) {
                nps.getDurationMs().setValue((long) (song.getAudioDuration() * 1000.0));
                nps.getProgressMs().setValue(playbackState != null ? playbackState.currentPositionSecond * 1000L : 0L);

                // Quality verdict
                String verdict;
                if (apincer.music.core.utils.TagUtils.isLossy(song)) {
                    verdict = "STANDARD QUALITY";
                } else if (song.getAudioBitsDepth() >= 24 && song.getAudioSampleRate() > 48000) {
                    verdict = "HI-RES STUDIO MASTER";
                } else if (song.getAudioBitsDepth() >= 24) {
                    verdict = "24-BIT STUDIO QUALITY";
                } else {
                    verdict = "CD Quality";
                }
                nps.getSpecsVerdict().setValue(verdict);

                String fmtCodec = apincer.music.core.utils.TagUtils.formatCodec(song);
                String fmtRes = apincer.music.core.utils.TagUtils.formatResolution(song.getAudioBitsDepth(), song.getAudioSampleRate(), song.getMqaSampleRate());
                nps.getSpecsFormat().setValue(fmtCodec + (fmtRes.isEmpty() ? "" : " • " + fmtRes));

                long bitrate = song.getAudioBitRate();
                nps.getSpecsBitrate().setValue(bitrate > 0 ? (bitrate / 1000) + " kbps" : "Lossless Audio");

                double dr = song.getDrScore() > 0 ? song.getDrScore() : song.getDynamicRange();
                nps.getSpecsDr().setValue(dr > 0 ? "DR " + (int) dr : "");

                apincer.music.core.playback.ReplayGainManager.ReplayGainInfo rg = apincer.music.core.playback.ReplayGainManager.getInstance().getReplayGain(song.getPath());
                String rgMode = apincer.music.core.Settings.getReplayGainMode(this);
                nps.getSpecsReplayGain().setValue(rg != null ? rg.getDisplayString(rgMode) : "");

                nps.getSpecsFileSize().setValue(song.getFileSize() > 0 ? android.text.format.Formatter.formatFileSize(this, song.getFileSize()) : "");

                // Coil Image Loading into state.albumArt
                coil3.request.ImageRequest imgRequest = apincer.android.mmate.coil3.CoverartFetcher.builder(this, song)
                        .data(song)
                        .size(800, 800)
                        .target(new coil3.target.Target() {
                            @Override
                            public void onSuccess(coil3.Image result) {
                                if (result instanceof coil3.BitmapImage) {
                                    nps.getAlbumArt().setValue(((coil3.BitmapImage) result).getBitmap());
                                }
                            }
                            @Override
                            public void onError(coil3.Image error) {
                                nps.getAlbumArt().setValue(null);
                            }
                            @Override
                            public void onStart(coil3.Image placeholder) {
                            }
                        })
                        .build();
                coil3.SingletonImageLoader.get(this).enqueue(imgRequest);
            } else {
                nps.getAlbumArt().setValue(null);
                nps.getSpecsVerdict().setValue("");
                nps.getSpecsFormat().setValue("");
                nps.getSpecsBitrate().setValue("");
                nps.getSpecsDr().setValue("");
                nps.getSpecsFileSize().setValue("");
            }

            // Target Title & Badge & Details
            if (isPlaybackServiceBound && playbackService != null && playbackService.getPlayer() != null) {
                apincer.music.core.playback.spi.PlaybackTarget player = playbackService.getPlayer();
                if (player instanceof apincer.music.core.playback.ExternalAndroidPlayer extPlayer && "local".equalsIgnoreCase(extPlayer.getTargetId())) {
                    AudioOutputHelper.Device device = AudioOutputHelper.getOutputDevice(this, song);
                    boolean isBitPerfect = device.isBitPerfect();
                    boolean isBluetooth = device.isBluetooth();
                    String devName = (device.getName() != null && !device.getName().isEmpty()) ? device.getName() : "Phone Speaker";
                    nps.getTargetTitle().setValue(devName);
                    nps.getTargetBadge().setValue(isBitPerfect ? "BIT-PERFECT" : (isBluetooth ? "BLUETOOTH" : "DIRECT OUTPUT"));
                    StringBuilder devBuf = new StringBuilder();
                    devBuf.append(device.getDescription());
                    if (!apincer.music.core.utils.StringUtils.isEmpty(device.getCodec()) && !"PCM".equalsIgnoreCase(device.getCodec()) && !"-".equals(device.getCodec())) {
                        devBuf.append(" — ").append(device.getCodec());
                    } else if (!apincer.music.core.utils.StringUtils.isEmpty(device.getFriendyDescription())) {
                        devBuf.append(" — ").append(device.getFriendyDescription());
                    }
                    nps.getTargetDetails().setValue(devBuf.toString());
                } else {
                    nps.getTargetTitle().setValue(PlayerNameUtils.getDropdownPlayerLabel(player));
                    nps.getTargetBadge().setValue(player.isStreaming() ? "DLNA" : "EXTERNAL APP");
                    nps.getTargetDetails().setValue(player.getDescription() != null ? player.getDescription() : "");
                }
            } else {
                nps.getTargetTitle().setValue("Local Audio");
                nps.getTargetBadge().setValue("SYS OUT");
                nps.getTargetDetails().setValue("System Default Output");
            }

            if (songChanged && song != null) {
                if (Settings.isListFollowNowPlaying(getBaseContext()) && (actionMode == null)) {
                    if (scrollRunnable != null) {
                        scrollHandler.removeCallbacks(scrollRunnable);
                    }
                    scrollRunnable = () -> {
                        if (!busy) {
                            scrollToSong(song);
                        }
                    };
                    scrollHandler.postDelayed(scrollRunnable, 500);
                }
            }

            previouslyPlaying = song;
            syncQueueState();
        });
    }

    public void syncQueueState() {
        if (isPlaybackServiceBound && playbackService != null && playbackService.getQueueManager() != null) {
            apincer.music.core.repository.QueueManager qm = playbackService.getQueueManager();
            List<Track> songs = qm.getSongs();
            Track nowPlaying = playbackService.getNowPlayingSong();
            String playingKey = nowPlaying != null ? nowPlaying.getUniqueKey() : null;

            long totalSec = 0;
            if (songs != null) {
                for (Track t : songs) {
                    if (t != null && t.getAudioDuration() > 0) {
                        totalSec += (long) t.getAudioDuration();
                    }
                }
            }
            String totalDurationStr = totalSec > 0 ? apincer.music.core.utils.StringUtils.formatDuration(totalSec, true) : "";

            List<Track> queueCopy = songs != null ? new ArrayList<>(songs) : Collections.emptyList();
            runOnUiThread(() -> apincer.android.mmate.ui.compose.MainScaffoldState.updateQueue(queueCopy, playingKey, totalDurationStr));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Setup night mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);

        // Start the server here, where we are guaranteed to be in the foreground!
        mediaServerManager.startServer();
        mediaServerManager.getServerStatus().observe(this, this::updateMediaServerState);
        updateMediaServerState(mediaServerManager.getServerStatus().getValue());

        // Enable Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Enable Dynamic Colors
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
        insetsController.setAppearanceLightStatusBars(false);

        // Get search criteria from intent
        SearchCriteria searchCriteria = ApplicationUtils.getSearchCriteria(getIntent());

        // Setup back press handler
        OnBackPressedCallback onBackPressedCallback = new BackPressedCallback(true);
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);

        // Set pure Compose content view
        setContentView(apincer.android.mmate.ui.compose.DrawerInterop.getComposeView(this, this));

        // Get the ViewModel. Hilt handles all the factory creation for you.
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        // Setup Selection tracker & Action Mode
        setupSelectionTracker();

        if (searchCriteria != null) {
            currentCriteria = searchCriteria;
        }
        syncActiveDrawerItem();

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
            runOnUiThread(() -> {
                List<Long> selectedPositions = null;
                if (actionMode != null && mTracker != null && mTracker.hasSelection()) {
                    selectedPositions = new ArrayList<>();
                    mTracker.getSelection().forEach(selectedPositions::add);
                }

                apincer.android.mmate.ui.compose.ListInterop.updateTracks(musicTags);
                apincer.android.mmate.ui.compose.ListInterop.updateRefreshing(false);
                updateHeaderPanel(viewModel.searchStats.getValue());

                if (selectedPositions != null && !selectedPositions.isEmpty() && mTracker != null) {
                    for (Long pos : selectedPositions) {
                        mTracker.select(pos);
                    }
                }
            });
        });

        // When DB aggregate stats arrive, refresh the subtitle with accurate totals
        viewModel.searchStats.observe(this, this::updateHeaderPanel);

        viewModel.musicItemsLoading.observe(this, isLoading -> runOnUiThread(() -> apincer.android.mmate.ui.compose.ListInterop.updateRefreshing(isLoading)));

        WorkManager.getInstance(getApplicationContext())
                .getWorkInfosForUniqueWorkLiveData("MusicScanWork")
                .observe(this, workInfos -> {
                    if (workInfos != null && !workInfos.isEmpty()) {
                        WorkInfo workInfo = workInfos.get(0);
                        WorkInfo.State currentState = workInfo.getState();
                        boolean isRunning = currentState == WorkInfo.State.RUNNING;
                        if (isRunning) {
                            int progress = workInfo.getProgress().getInt("progress_value", 0);
                            int total = workInfo.getProgress().getInt("total_files", 0);
                            String scanMsg = total > 0 ? "Scanning: " + progress + "/" + total + " files" : "Scanning…";
                            apincer.android.mmate.ui.compose.MainScaffoldState.updateScanning(true, scanMsg);
                        } else if (currentState.isFinished()) {
                            apincer.android.mmate.ui.compose.MainScaffoldState.updateScanning(false, "");
                            if (lastWorkState == WorkInfo.State.RUNNING) {
                                viewModel.loadMusicItems(currentCriteria);
                            }
                        }
                        lastWorkState = currentState;
                    }
                });
    }

    public void setFloatingDockVisible(boolean visible) {
        apincer.android.mmate.ui.compose.MainScaffoldState.setFloatingDockVisible(visible);
    }

    public PlaybackState getLastPlaybackState() {
        return lastPlaybackState;
    }

    private void setupSelectionTracker() {
        mTracker = new MySelectionTracker();
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
                }
                apincer.android.mmate.ui.compose.ListInterop.updateSelectedTracks(new java.util.HashSet<>(selections));
            }
        };
        mTracker.setObserver(observer);
        actionModeCallback = new ActionModeCallback();
    }

    private void doShowLeftMenus() {
        apincer.android.mmate.ui.compose.DrawerInterop.openDrawer();
    }

    private void updateHeaderPanel() {
        updateHeaderPanel(null);
    }

    private void updateHeaderPanel(SearchResultStats stats) {
        SearchCriteria.TYPE type = currentCriteria.getType();

        boolean isTopLevelCategoryDir = isEmpty(currentCriteria.getKeyword())
                && !SearchCriteria.TYPE.LIBRARY.equals(type);
        boolean hasActiveFilter = (!(currentCriteria.getFilterType() == null || currentCriteria.getFilterType().isEmpty()));
        int count = (isTopLevelCategoryDir || hasActiveFilter) ? apincer.android.mmate.ui.compose.ListInterop.getTracks().size()
                : ((stats != null) ? stats.getTotalCount() : apincer.android.mmate.ui.compose.ListInterop.getTracks().size());
        long totalSize = hasActiveFilter ? apincer.android.mmate.ui.compose.ListInterop.getTracks().stream().mapToLong(Track::getFileSize).sum() : ((stats != null) ? stats.getTotalSize() : apincer.android.mmate.ui.compose.ListInterop.getTracks().stream().mapToLong(Track::getFileSize).sum());
        double totalDuration = hasActiveFilter ? apincer.android.mmate.ui.compose.ListInterop.getTracks().stream().mapToDouble(Track::getAudioDuration).sum() : ((stats != null) ? stats.getTotalDuration() : apincer.android.mmate.ui.compose.ListInterop.getTracks().stream().mapToDouble(Track::getAudioDuration).sum());

        String statText = "";
        if (!isEmpty(currentCriteria.getKeyword())) {
            if (count > 0) {
                statText = StringUtils.formatSongSize(count) + " Songs";
            }

            if (isEmpty(currentCriteria.getFilterType()) && count > 0) {
                statText = statText + SYMBOL_ENC_SEP + StringUtils.formatStorageSize(totalSize) + SYMBOL_ENC_SEP + StringUtils.formatDuration(totalDuration, true);
            } else {
                String filterText = currentCriteria.getFilterText();
                if ("Folder".equals(currentCriteria.getFilterType())) {
                    filterText = StringUtils.truncate(DocumentFileCompat.getBasePath(getApplicationContext(), filterText), 38, StringUtils.TruncateType.PREFIX);
                } else {
                    filterText = StringUtils.truncate(filterText, 38, StringUtils.TruncateType.SUFFIX);
                }
                if (!isEmpty(filterText)) {
                    statText = statText + " · [" + filterText + "]";
                }
            }
        } else {
            if (count > 0) {
                String unitTitle;
                if (SearchCriteria.TYPE.PLAYLIST.equals(type)) {
                    unitTitle = count == 1 ? "Playlist" : "Playlists";
                } else if (SearchCriteria.TYPE.ARTIST.equals(type)) {
                    unitTitle = count == 1 ? "Artist" : "Artists";
                } else if (SearchCriteria.TYPE.GENRE.equals(type)) {
                    unitTitle = count == 1 ? "Genre" : "Genres";
                } else {
                    unitTitle = "Tracks";
                }
                statText = StringUtils.formatSongSize(count) + " " + unitTitle;
                if (stats != null && SearchCriteria.TYPE.LIBRARY.equals(type) && isEmpty(currentCriteria.getFilterType())) {
                    statText = statText + SYMBOL_ENC_SEP + StringUtils.formatStorageSize(totalSize) + SYMBOL_ENC_SEP + StringUtils.formatDuration(totalDuration, true);
                }
            }
        }

        apincer.android.mmate.ui.compose.MainScaffoldState.updateHeaderStats(statText);
        apincer.android.mmate.ui.compose.MainScaffoldState.updateBackVisible(!SearchCriteria.TYPE.LIBRARY.equals(type) || !isEmpty(currentCriteria.getKeyword()));
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        scrollHandler.removeCallbacksAndMessages(null);
        if (playbackStateSubscription != null) {
            try {
                playbackStateSubscription.close();
            } catch (Exception ignored) {}
            playbackStateSubscription = null;
        }
        if (isPlaybackServiceBound) {
            unbindService(serviceConnection);
        }
        super.onDestroy();
    }

    public void scrollToSong(Track currentlyPlaying) {
        if (currentlyPlaying == null || (actionMode != null)) return;

        viewModel.loadUntilFound(currentlyPlaying, () -> {
            runOnUiThread(() -> {
                int positionToScroll = apincer.android.mmate.ui.compose.ListInterop.getTracks().indexOf(currentlyPlaying);
                if (positionToScroll != RecyclerView.NO_POSITION) {
                    scrollToPosition(positionToScroll);
                }
            });
        });
    }

    private void scrollToPosition(int position) {
        apincer.android.mmate.ui.compose.ListInterop.scrollToPosition(position);
    }

    private void doHideSearch() {
        currentCriteria.resetSearch();
        viewModel.loadMusicItems(currentCriteria);
    }

    private void doStartRefresh(SearchCriteria.TYPE type, String keyword) {
        currentCriteria.setType(type);
        currentCriteria.setKeyword(keyword);
        syncActiveDrawerItem();
        viewModel.loadMusicItems(currentCriteria);
    }

    public void onSearchQueryChanged(String query) {
        if (query == null || query.trim().isEmpty()) {
            if (currentCriteria != null && currentCriteria.isSearchMode()) {
                doHideSearch();
            }
        } else {
            viewModel.search(currentCriteria, query.trim());
        }
    }

    public void onSearchBackClicked() {
        if (actionMode != null) {
            actionMode.finish();
            return;
        }

        if (currentCriteria != null && !(currentCriteria.getFilterType() == null || currentCriteria.getFilterType().isEmpty())) {
            currentCriteria.setFilterText(null);
            currentCriteria.setFilterType(null);
            apincer.android.mmate.ui.compose.ListInterop.updateRefreshing(true);
            viewModel.loadMusicItems(currentCriteria);
            return;
        }

        if (currentCriteria != null && currentCriteria.isSearchMode()) {
            doHideSearch();
            apincer.android.mmate.ui.compose.ListInterop.updateRefreshing(true);
            viewModel.loadMusicItems(currentCriteria);
            return;
        }

        if (currentCriteria != null && (isEmpty(currentCriteria.getKeyword()) || SearchCriteria.TYPE.LIBRARY.equals(currentCriteria.getType()))) {
            doShowLeftMenus();
        } else if (currentCriteria != null && !isEmpty(currentCriteria.getKeyword()) && !SearchCriteria.TYPE.LIBRARY.equals(currentCriteria.getType())) {
            currentCriteria.setFilterText(null);
            currentCriteria.setFilterType(null);
            currentCriteria.setKeyword(null);
            apincer.android.mmate.ui.compose.ListInterop.updateRefreshing(true);
            viewModel.loadMusicItems(currentCriteria);
        }
    }

    public void onDockPlayPauseClicked() {
        if (playbackService != null) {
            if (lastPlaybackState != null && lastPlaybackState.currentState == PlaybackState.State.PLAYING) {
                playbackService.pausePlayer();
            } else if (lastPlaybackState != null && lastPlaybackState.currentState == PlaybackState.State.PAUSED) {
                playbackService.resumePlayer();
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
    }

    public void onDockNextClicked() {
        if (playbackService != null) {
            playbackService.skipToNextInQueue();
        }
    }

    public void onSelectPlaybackTargetClicked() {
        if (!isPlaybackServiceBound || playbackService == null) return;
        playbackService.refreshPlayerDiscovery();
        updatePlayerPickerState();
        MainScaffoldState.get().getShowPlayerPickerDialog().setValue(true);
    }

    public void onAudioHubPlayPause() {
        onDockPlayPauseClicked();
    }

    public void onAudioHubNext() {
        if (playbackService != null) playbackService.skipToNextInQueue();
    }

    public void onAudioHubPrevious() {
        if (playbackService != null) playbackService.skipToPrevious();
    }

    public void onAudioHubShuffleToggle() {
        if (playbackService != null) {
            boolean shuffle = !apincer.android.mmate.ui.compose.MainScaffoldState.get().getNowPlayingState().isShuffle().getValue();
            playbackService.setShuffleMode(shuffle);
            apincer.android.mmate.ui.compose.MainScaffoldState.get().getNowPlayingState().isShuffle().setValue(shuffle);
        }
    }

    public void onAudioHubRepeatToggle() {
        if (playbackService != null) {
            int mode = apincer.android.mmate.ui.compose.MainScaffoldState.get().getNowPlayingState().getRepeatMode().getValue();
            int nextMode = (mode == 0) ? 1 : (mode == 1) ? 2 : 0;
            String modeStr = (nextMode == 1) ? "ALL" : (nextMode == 2) ? "ONE" : "OFF";
            playbackService.setRepeatMode(modeStr);
            apincer.android.mmate.ui.compose.MainScaffoldState.get().getNowPlayingState().getRepeatMode().setValue(nextMode);
        }
    }

    public void onAudioHubSeek(float progress) {
        if (playbackService != null) {
            long duration = apincer.android.mmate.ui.compose.MainScaffoldState.get().getNowPlayingState().getDurationMs().getValue();
            if (duration > 0) playbackService.seekTo((long) (progress * duration));
        }
    }

    public void onAudioHubVolumeDown() {
        if (playbackService != null && playbackService.getPlayer() != null && playbackService.getPlayer().isStreaming()) {
            playbackService.adjustVolume(-1);
            return;
        }
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        if (am != null) {
            am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI);
            updateVolumeState();
        }
    }

    public void onAudioHubVolumeUp() {
        if (playbackService != null && playbackService.getPlayer() != null && playbackService.getPlayer().isStreaming()) {
            playbackService.adjustVolume(1);
            return;
        }
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        if (am != null) {
            am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI);
            updateVolumeState();
        }
    }

    public void onAudioHubVolumeChanged(float vol) {
        apincer.android.mmate.ui.compose.NowPlayingState nps = apincer.android.mmate.ui.compose.MainScaffoldState.get().getNowPlayingState();
        if (nps != null) nps.getVolume().setValue(vol);

        if (playbackService != null && playbackService.getPlayer() != null && playbackService.getPlayer().isStreaming()) {
            playbackService.setVolume(Math.round(vol * 100));
            return;
        }
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        if (am != null) {
            int max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
            int target = Math.round(vol * max);
            am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, android.media.AudioManager.FLAG_SHOW_UI);
        }
    }

    private void updateVolumeState() {
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        if (am != null) {
            int cur = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
            int max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
            if (max > 0) {
                float vol = (float) cur / max;
                apincer.android.mmate.ui.compose.NowPlayingState nps = apincer.android.mmate.ui.compose.MainScaffoldState.get().getNowPlayingState();
                if (nps != null) {
                    nps.getVolume().setValue(vol);
                }
            }
        }
    }

    public void onAudioHubTrackClicked() {
        if (playbackService != null && playbackService.getNowPlayingSong() != null) {
            apincer.android.mmate.ui.compose.MainScaffoldState.closeAudioHub();
            scrollToSong(playbackService.getNowPlayingSong());
        }
    }

    public void onAudioHubQueueTrackClicked(Track track) {
        if (playbackService != null && track != null) {
            playbackService.playSong(track);
        }
    }

    public void onAudioHubQueueTrackRemoved(Track track, int index) {
        if (playbackService != null && playbackService.getQueueManager() != null) {
            playbackService.getQueueManager().removeTrack(index);
            syncQueueState();
        }
    }

    @Override
    public void onAudioHubQueueTrackMoved(int fromIndex, int toIndex) {
        if (playbackService != null && playbackService.getQueueManager() != null) {
            playbackService.getQueueManager().moveTrack(fromIndex, toIndex);
            syncQueueState();
        }
    }

    @Override
    public void onAudioHubQueueClear() {
        if (playbackService != null && playbackService.getQueueManager() != null) {
            playbackService.getQueueManager().emptyPlayingQueue();
            syncQueueState();
            android.widget.Toast.makeText(this, "Queue cleared", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onAudioHubQueueJumpToPlaying() {
        if (playbackService != null && playbackService.getNowPlayingSong() != null) {
            apincer.android.mmate.ui.compose.MainScaffoldState.closeAudioHub();
            scrollToSong(playbackService.getNowPlayingSong());
        }
    }

    public void updateMediaServerState(MediaServerHub.ServerStatus status) {
        apincer.android.mmate.ui.compose.MediaServerState state =
                apincer.android.mmate.ui.compose.MainScaffoldState.get().getMediaServerState();
        if (state == null) return;

        boolean isRunning = (status == MediaServerHub.ServerStatus.RUNNING || status == MediaServerHub.ServerStatus.CAST);
        boolean networkAvailable = NetworkUtils.isServerNetworkAvailable(this);
        state.setNetworkAvailable(networkAvailable);
        state.setServerRunning(isRunning);

        String engine = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this).getString(Constants.PREF_SERVER_ENGINE, "httpcore");
        state.setCurrentEngine(engine);

        if (isRunning) {
            String url = Constants.getPresentationUrl();
            state.setServerUrl(url);
            String netDesc = NetworkUtils.isWifiConnected(this) ? "Wi-Fi" : NetworkUtils.isHotspotActive(this) ? "Hotspot" : "Local Network";
            state.setBroadcastInfo("DLNA 1.5 • " + netDesc + " • Port " + apincer.music.core.server.BaseServer.WEB_SERVER_PORT);
            state.setServerStatusText("DLNA Server Active");
            state.setQrCodeBitmap(BitmapHelper.generateQRCode(url, 400, 400));
        } else if (status == MediaServerHub.ServerStatus.STARTING) {
            state.setServerStatusText("Starting Server…");
            state.setServerUrl("");
            state.setBroadcastInfo("Initializing streaming services…");
            state.setQrCodeBitmap(null);
        } else if (status == MediaServerHub.ServerStatus.ERROR || !networkAvailable) {
            state.setServerStatusText("Network Disconnected");
            state.setServerUrl("");
            state.setBroadcastInfo("Wi-Fi / Hotspot required to stream");
            state.setQrCodeBitmap(null);
        } else {
            state.setServerStatusText("Server Stopped");
            state.setServerUrl("");
            state.setBroadcastInfo(networkAvailable ? "Ready to stream" : "Wi-Fi / Hotspot disconnected");
            state.setQrCodeBitmap(null);
        }
    }

    public void onAudioHubEngineChanged(String engine) {
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        String prevEngine = prefs.getString(Constants.PREF_SERVER_ENGINE, "httpcore");
        if (!engine.equals(prevEngine)) {
            prefs.edit().putString(Constants.PREF_SERVER_ENGINE, engine).apply();
            apincer.android.mmate.ui.compose.MainScaffoldState.get().getMediaServerState().setCurrentEngine(engine);
            if (playbackService instanceof MusicMateServiceImpl msi) {
                msi.stopServers();
                msi.startServers();
            }
            android.widget.Toast.makeText(this, "Switching engine — restarting server…", android.widget.Toast.LENGTH_SHORT).show();
            updateMediaServerState(MediaServerHub.ServerStatus.STARTING);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (playbackService instanceof MusicMateServiceImpl msi) {
                    updateMediaServerState(msi.getStatusLiveData().getValue());
                }
            }, 600);
        }
    }

    public void onAudioHubStartServer() {
        mediaServerManager.startServer();
        if (playbackService instanceof MusicMateServiceImpl msi) {
            msi.startServers();
        }
        updateMediaServerState(MediaServerHub.ServerStatus.STARTING);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (playbackService instanceof MusicMateServiceImpl msi) {
                updateMediaServerState(msi.getStatusLiveData().getValue());
            } else {
                updateMediaServerState(mediaServerManager.getServerStatus().getValue());
            }
        }, 600);
    }

    public void onAudioHubStopServer() {
        mediaServerManager.stopServer();
        if (playbackService instanceof MusicMateServiceImpl msi) {
            msi.stopServers();
        }
        updateMediaServerState(MediaServerHub.ServerStatus.STOPPED);
    }

    public void onAudioHubCopyUrl() {
        String url = apincer.android.mmate.ui.compose.MainScaffoldState.get().getMediaServerState().getServerUrl();
        if (url != null && !url.isEmpty()) {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Server URL", url);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                android.widget.Toast.makeText(this, "Server URL copied to clipboard", android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void onAudioHubOpenUrl() {
        String url = apincer.android.mmate.ui.compose.MainScaffoldState.get().getMediaServerState().getServerUrl();
        if (url != null && !url.isEmpty()) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
            startActivity(browserIntent);
        }
    }

    public void onAudioHubQrCode() {}

    // MainScaffoldCallbacks Interface Implementations
    @Override
    public void onNavigationItemClick(int itemId) {
        handleNavigationItemClick(itemId);
    }

    @Override
    public void onSearchQueryChange(String query) {
        onSearchQueryChanged(query);
    }

    @Override
    public void onSearchBackClick() {
        onSearchBackClicked();
    }

    @Override
    public void onTrackClick(Track track, int position) {
        onTrackClicked(track, position);
    }

    @Override
    public void onTrackLongClick(Track track, int position) {
        onTrackLongClicked(track, position);
    }

    @Override
    public void onTrackMenuClick(Track track, int position) {
        onTrackMenuClicked(track, position);
    }

    @Override
    public void onTrackQuickPlayClick(Track track) {
        onTrackQuickPlayClicked(track);
    }

    @Override
    public void onFolderPlayClick(Track track) {
        onFolderPlayClicked(track);
    }

    @Override
    public void onFolderEnqueueClick(Track track) {
        onFolderEnqueueClicked(track);
    }

    @Override
    public void onSmartPlaylistCreated(apincer.music.core.model.PlaylistEntry entry) {
        viewModel.loadMusicItems();
        android.widget.Toast.makeText(this, "Smart Playlist '" + entry.getName() + "' created", android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDockPlayPauseClick() {
        onDockPlayPauseClicked();
    }

    @Override
    public void onDockNextClick() {
        onDockNextClicked();
    }

    @Override
    public void onDockLongClick() {
        onAudioHubTrackClicked();
    }

    @Override
    public void onSelectPlaybackTargetClick() {
        onSelectPlaybackTargetClicked();
    }

    @Override
    public void onPlayerTargetSelected(apincer.music.core.playback.spi.PlaybackTarget target) {
        if (playbackService != null && target != null) {
            playbackService.switchPlayer(target, true);
            setNowPlaying(playbackService.getNowPlayingSong(), lastPlaybackState);
            updatePlayerPickerState();
        }
    }

    @Override
    public void onRescanTargets() {
        if (playbackService != null) {
            playbackService.refreshPlayerDiscovery();
            MainScaffoldState.setPlayerScanning(true);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                updatePlayerPickerState();
                MainScaffoldState.setPlayerScanning(false);
            }, 1200);
        }
    }

    @Override
    public void onOpenSystemAudioOutput() {
        openSystemAudioOutputPanel();
    }

    @Override
    public void onAudioHubSleepTimerSelected(long minutes, boolean endOfTrack) {
        if (playbackService != null) {
            playbackService.setSleepTimer(minutes, endOfTrack);
            apincer.android.mmate.ui.compose.NowPlayingState nps = apincer.android.mmate.ui.compose.MainScaffoldState.get().getNowPlayingState();
            if (nps != null) {
                nps.isSleepTimerActive().setValue(minutes > 0 || endOfTrack);
                if (endOfTrack) {
                    nps.getSleepTimerText().setValue("Track End");
                } else if (minutes > 0) {
                    nps.getSleepTimerText().setValue(minutes + "m");
                } else {
                    nps.getSleepTimerText().setValue("");
                }
            }
            if (minutes > 0) {
                android.widget.Toast.makeText(this, "Sleep timer set for " + minutes + " minutes", android.widget.Toast.LENGTH_SHORT).show();
            } else if (endOfTrack) {
                android.widget.Toast.makeText(this, "Sleep timer set to stop after this song", android.widget.Toast.LENGTH_SHORT).show();
            } else {
                android.widget.Toast.makeText(this, "Sleep timer turned off", android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onAudioHubTrackClick() {
        onAudioHubTrackClicked();
    }

    @Override
    public void onAudioHubQueueTrackClick(Track track) {
        onAudioHubQueueTrackClicked(track);
    }

    @Override
    public void onAudioHubQueueTrackRemove(Track track, int index) {
        onAudioHubQueueTrackRemoved(track, index);
    }

    @Override
    public void onEngineChanged(String engine) {
        onAudioHubEngineChanged(engine);
    }

    @Override
    public void onStartServerClicked() {
        onAudioHubStartServer();
    }

    @Override
    public void onStopServerClicked() {
        onAudioHubStopServer();
    }

    @Override
    public void onCopyUrlClicked() {
        onAudioHubCopyUrl();
    }

    @Override
    public void onOpenUrlClicked() {
        onAudioHubOpenUrl();
    }

    @Override
    public void onQrCodeClicked() {
        onAudioHubQrCode();
    }

    private void syncActiveDrawerItem() {
        if (currentCriteria == null) return;
        SearchCriteria.TYPE type = currentCriteria.getType();
        int activeId = R.id.menu_library_all_songs;
        if (SearchCriteria.TYPE.ARTIST.equals(type)) {
            activeId = R.id.menu_tag_artist;
        } else if (SearchCriteria.TYPE.GENRE.equals(type)) {
            activeId = R.id.menu_tag_genre;
        } else if (SearchCriteria.TYPE.PLAYLIST.equals(type)) {
            activeId = R.id.menu_collection;
        } else if (SearchCriteria.TYPE.SOUND_GRADE.equals(type)) {
            activeId = R.id.menu_sound_grade;
        } else if (SearchCriteria.TYPE.LIBRARY.equals(type)) {
            String kw = currentCriteria.getKeyword();
            if (Constants.TITLE_INCOMING_SONGS.equals(kw)) {
                activeId = R.id.menu_library_recently_added;
            } else if (Constants.TITLE_DUPLICATE.equals(kw)) {
                activeId = R.id.menu_library_similar_songs;
            } else {
                activeId = R.id.menu_library_all_songs;
            }
        }
        apincer.android.mmate.ui.compose.DrawerInterop.updateActiveItem(activeId);
    }

    public void handleNavigationItemClick(int itemId) {
        // Create a dummy MenuItem
        MenuItem item = new MenuItem() {
            @Override public int getItemId() { return itemId; }
            @Override public int getGroupId() { return 0; }
            @Override public int getOrder() { return 0; }
            @NonNull
            @Override public MenuItem setTitle(CharSequence title) { return this; }
            @NonNull
            @Override public MenuItem setTitle(int title) { return this; }
            @Override public CharSequence getTitle() { return null; }
            @NonNull
            @Override public MenuItem setTitleCondensed(CharSequence title) { return this; }
            @Override public CharSequence getTitleCondensed() { return null; }
            @NonNull
            @Override public MenuItem setIcon(Drawable icon) { return this; }
            @NonNull
            @Override public MenuItem setIcon(int iconRes) { return this; }
            @Override public Drawable getIcon() { return null; }
            @NonNull
            @Override public MenuItem setIntent(Intent intent) { return this; }
            @Override public Intent getIntent() { return null; }
            @NonNull
            @Override public MenuItem setShortcut(char numericChar, char alphaChar) { return this; }
            @NonNull
            @Override public MenuItem setShortcut(char numericChar, char alphaChar, int numericModifiers, int alphaModifiers) { return this; }
            @NonNull
            @Override public MenuItem setNumericShortcut(char numericChar) { return this; }
            @NonNull
            @Override public MenuItem setNumericShortcut(char numericChar, int numericModifiers) { return this; }
            @Override public char getNumericShortcut() { return 0; }
            @Override public int getNumericModifiers() { return 0; }
            @NonNull
            @Override public MenuItem setAlphabeticShortcut(char alphaChar) { return this; }
            @NonNull
            @Override public MenuItem setAlphabeticShortcut(char alphaChar, int alphaModifiers) { return this; }
            @Override public char getAlphabeticShortcut() { return 0; }
            @Override public int getAlphabeticModifiers() { return 0; }
            @NonNull
            @Override public MenuItem setCheckable(boolean checkable) { return this; }
            @Override public boolean isCheckable() { return false; }
            @NonNull
            @Override public MenuItem setChecked(boolean checked) { return this; }
            @Override public boolean isChecked() { return false; }
            @NonNull
            @Override public MenuItem setVisible(boolean visible) { return this; }
            @Override public boolean isVisible() { return true; }
            @NonNull
            @Override public MenuItem setEnabled(boolean enabled) { return this; }
            @Override public boolean isEnabled() { return true; }
            @Override public boolean hasSubMenu() { return false; }
            @Override public android.view.SubMenu getSubMenu() { return null; }
            @NonNull
            @Override public MenuItem setOnMenuItemClickListener(OnMenuItemClickListener menuItemClickListener) { return this; }
            @Override public ContextMenu.ContextMenuInfo getMenuInfo() { return null; }
            @Override public void setShowAsAction(int actionEnum) {}
            @NonNull
            @Override public MenuItem setShowAsActionFlags(int actionEnum) { return this; }
            @NonNull
            @Override public MenuItem setActionView(View view) { return this; }
            @NonNull
            @Override public MenuItem setActionView(int resId) { return this; }
            @Override public View getActionView() { return null; }
            @NonNull
            @Override public MenuItem setActionProvider(android.view.ActionProvider actionProvider) { return this; }
            @Override public android.view.ActionProvider getActionProvider() { return null; }
            @Override public boolean expandActionView() { return false; }
            @Override public boolean collapseActionView() { return false; }
            @Override public boolean isActionViewExpanded() { return false; }
            @NonNull
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
                syncQueueState();
                android.widget.Toast.makeText(MainActivity.this, "Playing next", android.widget.Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.action_add_queue) {
                playbackService.getQueueManager().addPlayingQueue(track);
                syncQueueState();
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
        onSelectPlaybackTargetClicked();
    }

    public void updatePlayerPickerState() {
        if (playbackService == null) return;
        List<apincer.music.core.playback.spi.PlaybackTarget> renderers = playbackService.getPlaybackTargets();
        apincer.music.core.playback.spi.PlaybackTarget current = playbackService.getPlayer();

        apincer.android.mmate.utils.AudioOutputHelper.Device audioOutputDevice =
                apincer.android.mmate.utils.AudioOutputHelper.getOutputDevice(this, playbackService.getNowPlayingSong());

        List<apincer.android.mmate.ui.compose.PlayerTargetItem> items = new java.util.ArrayList<>();
        if (renderers != null && !renderers.isEmpty()) {
            for (apincer.music.core.playback.spi.PlaybackTarget target : renderers) {
                boolean isSelected = current != null && current.getTargetId().equals(target.getTargetId());
                String baseLabel = apincer.music.core.utils.PlayerNameUtils.getDropdownPlayerLabel(target);

                if (target instanceof apincer.music.core.playback.ExternalAndroidPlayer extPlayer && "local".equalsIgnoreCase(extPlayer.getTargetId())) {
                    if (audioOutputDevice != null && audioOutputDevice.getName() != null && !audioOutputDevice.getName().isEmpty() && !"Phone Speaker".equalsIgnoreCase(audioOutputDevice.getName())) {
                        baseLabel = audioOutputDevice.getCompactLabel();
                    }
                }

                int iconRes = R.drawable.rounded_music_cast_24;
                if (target.isStreaming()) {
                    iconRes = R.drawable.rounded_music_cast_24;
                } else if (target.getTargetId() != null && (target.getTargetId().toLowerCase().contains("bt") || target.getTargetId().toLowerCase().contains("bluetooth"))) {
                    iconRes = R.drawable.ic_round_bluetooth_audio_24;
                } else if ("local".equalsIgnoreCase(target.getTargetId())) {
                    iconRes = R.drawable.round_sd_storage_24;
                }

                String subtitle = "";
                if (target.getDescription() != null) {
                    subtitle = target.getDescription();
                } else if ("local".equalsIgnoreCase(target.getTargetId()) && audioOutputDevice != null) {
                    subtitle = audioOutputDevice.getName();
                }

                items.add(new apincer.android.mmate.ui.compose.PlayerTargetItem(
                        target,
                        baseLabel,
                        subtitle,
                        iconRes,
                        isSelected,
                        target.isStreaming()
                ));
            }
        }
        MainScaffoldState.setPlayerTargets(items);
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
            onSearchBackClicked();
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
            String mode = Settings.getTapActionMode(this);
            if (Constants.TAP_MODE_LISTEN.equalsIgnoreCase(mode)) {
                onTrackQuickPlayClicked(tag);
            } else {
                doShowEditActivity(java.util.Collections.singletonList(tag));
            }
        }
    }

    public void onTrackQuickPlayClicked(apincer.music.core.model.Track tag) {
        if (tag == null) return;
        if (isPlaybackServiceBound && playbackService != null) {
            viewModel.playTrackList(apincer.android.mmate.ui.compose.ListInterop.getTracks(), tag, playbackService);
        } else {
            android.widget.Toast.makeText(this, "No active player — connect a device first", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    public void onTrackLongClicked(apincer.music.core.model.Track tag, int position) {
        if (isSelectionBlocked()) return;
        if (mTracker != null && mTracker.hasSelection()) {
            mTracker.select((long) position);
            return;
        }
        String mode = Settings.getTapActionMode(this);
        if (Constants.TAP_MODE_LISTEN.equalsIgnoreCase(mode) && tag != null && !tag.isContainer()) {
            doShowEditActivity(java.util.Collections.singletonList(tag));
            return;
        }
        if (mTracker != null) {
            mTracker.select((long) position);
        }
    }

    public void onFolderPlayClicked(apincer.music.core.model.Track tag) {
        if (isPlaybackServiceBound && playbackService != null) {
            viewModel.playCollection(tag, playbackService, false);
            android.widget.Toast.makeText(this, "Playing collection", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    public void onFolderEnqueueClicked(apincer.music.core.model.Track tag) {
        if (isPlaybackServiceBound && playbackService != null) {
            viewModel.playCollection(tag, playbackService, true);
            android.widget.Toast.makeText(this, "Collection added to queue", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    public void onListRefresh() {
        apincer.android.mmate.ui.compose.ListInterop.updateRefreshing(true);
        viewModel.loadMusicItems();
        apincer.android.mmate.ui.compose.ListInterop.updateRefreshing(false);
    }

    public void onTrackMenuClicked(apincer.music.core.model.Track tag, int position) {
        if (tag != null) {
            showTrackPopupMenu(getWindow().getDecorView(), tag);
        }
    }
}
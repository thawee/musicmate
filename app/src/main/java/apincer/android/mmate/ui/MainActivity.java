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
    private LibrarySelectionModel selectionModel;

    private WorkInfo.State lastWorkState = null;
    private WorkInfo scanWork;
    private WorkInfo analyzeWork;

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
    /** The folder picker sent the user to grant storage access; reopen it once access is granted. */
    private boolean reopenFoldersAfterStorageGrant = false;
    private static final String STATE_REOPEN_FOLDERS = "reopen_folders_after_storage_grant";

    @Inject
    FileOperationTask operationTask;

    @Inject
    MediaServerManager mediaServerManager;

    @Inject
    apincer.music.core.server.spi.WebServer webServer;

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
            MainScaffoldState.get().isPlaybackAvailable().setValue(true);
            refreshSleepTimerChip();
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
                // Shuffle/repeat and the queue are restored from the database in the background
                qm.whenLoaded(() -> runOnUiThread(() -> {
                    apincer.android.mmate.ui.compose.NowPlayingState nps = apincer.android.mmate.ui.compose.MainScaffoldState.get().getNowPlayingState();
                    nps.isShuffle().setValue(qm.isShuffle());
                    int rMode = qm.getRepeatMode() == apincer.music.core.repository.QueueManager.RepeatMode.ALL ? 1
                            : qm.getRepeatMode() == apincer.music.core.repository.QueueManager.RepeatMode.ONE ? 2 : 0;
                    nps.getRepeatMode().setValue(rMode);
                    syncQueueState();
                }));
            }
            updateVolumeState();
            syncQueueState();
            refreshSystemAccessState(true);
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
            MainScaffoldState.get().isPlaybackAvailable().setValue(false);
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

            // Update AudioHub sub-states
            apincer.android.mmate.ui.compose.NowPlayingState nps = apincer.android.mmate.ui.compose.MainScaffoldState.get().getNowPlayingState();
            nps.getPlaybackState().setValue(playbackState != null ? playbackState : new PlaybackState());
            nps.getTrack().setValue(song);
            if (song != null) {
                nps.getDurationMs().setValue((long) (song.getAudioDuration() * 1000.0));
                nps.getProgressMs().setValue(playbackState != null ? playbackState.currentPositionSecond * 1000L : 0L);

                nps.getSpecsVerdict().setValue(
                        apincer.android.mmate.ui.compose.AudioPresentation.qualityLabel(song, true));

                String fmtCodec = apincer.music.core.utils.TagUtils.formatCodec(song);
                String fmtRes = apincer.music.core.utils.TagUtils.formatResolution(song.getAudioBitsDepth(), song.getAudioSampleRate(), song.getMqaSampleRate());
                nps.getSpecsFormat().setValue(fmtCodec + (fmtRes.isEmpty() ? "" : " • " + fmtRes));

                long bitrate = song.getAudioBitRate();
                nps.getSpecsBitrate().setValue(bitrate > 0 ? (bitrate / 1000) + " kbps" : "Lossless Audio");

                double dr = apincer.music.core.utils.TagUtils.effectiveDr(song);
                // Rounded like the library badge, so both read the same DR
                nps.getSpecsDr().setValue(dr > 0 ? "DR " + Math.round(dr) : "");

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
                    apincer.android.mmate.audio.UsbBitPerfectSession.Status usbStatus =
                            (playbackService instanceof apincer.android.mmate.service.MusicMateServiceImpl msi)
                                    ? msi.getUsbBitPerfectStatus()
                                    : apincer.android.mmate.audio.UsbBitPerfectSession.Status.disabled();
                    boolean isBitPerfectRequested = usbStatus.isRequested() && usbStatus.getDeviceId() == device.getId();
                    boolean isBluetooth = device.isBluetooth();
                    String devName = (device.getName() != null && !device.getName().isEmpty()) ? device.getName() : "Phone Speaker";
                    targetSubtitle = devName;
                    nps.getTargetTitle().setValue(devName);
                    nps.getTargetBadge().setValue(isBitPerfectRequested ? "BIT-PERFECT REQUESTED"
                            : (isBluetooth ? "BLUETOOTH" : (device.isUsb() ? "USB AUDIO" : "DIRECT OUTPUT")));
                    StringBuilder devBuf = new StringBuilder();
                    devBuf.append(device.getDescription());
                    if (!apincer.music.core.utils.StringUtils.isEmpty(device.getCodec()) && !"PCM".equalsIgnoreCase(device.getCodec()) && !"-".equals(device.getCodec())) {
                        devBuf.append(" — ").append(device.getCodec());
                    } else if (!apincer.music.core.utils.StringUtils.isEmpty(device.getFriendyDescription())) {
                        devBuf.append(" — ").append(device.getFriendyDescription());
                    }
                    if (device.isUsb() && usbStatus.getState() != apincer.android.mmate.audio.UsbBitPerfectSession.State.DISABLED) {
                        devBuf.append("\n").append(usbStatus.getReason());
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

            apincer.android.mmate.ui.compose.MainScaffoldState.updateNowPlaying(song, isPlaying, targetSubtitle, progress);

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
            runOnUiThread(() -> {
                apincer.android.mmate.ui.compose.MainScaffoldState.get().getQueueState().setManager(qm);
                apincer.android.mmate.ui.compose.MainScaffoldState.updateQueue(queueCopy, playingKey, totalDurationStr);
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Setup night mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            reopenFoldersAfterStorageGrant = savedInstanceState.getBoolean(STATE_REOPEN_FOLDERS);
            restoreCriteria(savedInstanceState);
        }

        // Start the server here, where we are guaranteed to be in the foreground!
        // Opt-in: the server starts automatically only after the user has started it themselves
        if (getPreferences(MODE_PRIVATE).getBoolean("media_server_auto_start", false)) {
            mediaServerManager.startServer();
        }
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
        if (searchCriteria != null) {
            currentCriteria = searchCriteria;
        }

        // Initialize Java-owned data callbacks before Compose can dispatch a restored route.
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        setupSelectionTracker();
        refreshSystemAccessState(false);

        // Setup back press handler
        OnBackPressedCallback onBackPressedCallback = new BackPressedCallback(true);
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);

        // Set pure Compose content view
        setContentView(apincer.android.mmate.ui.compose.DrawerInterop.getComposeView(
                this,
                this,
                getInitialLibraryDestination()));

        // Observe ViewModel LiveData
        apincer.android.mmate.ui.compose.MainScaffoldState.updateSearchQuery(
                currentCriteria.isSearchMode() ? StringUtils.trimToEmpty(currentCriteria.getSearchText()) : "");
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
                MainScaffoldState.get().getMusicListKey().setValue(viewModel.getMusicListKey());
                apincer.android.mmate.ui.compose.ListInterop.updateTracks(musicTags);
                selectionModel.setTracks(musicTags);
                apincer.android.mmate.ui.compose.ListInterop.updateRefreshing(false);
                updateHeaderPanel(viewModel.searchStats.getValue());
            });
        });

        // When DB aggregate stats arrive, refresh the subtitle with accurate totals
        viewModel.searchStats.observe(this, this::updateHeaderPanel);

        viewModel.hasMoreItems.observe(this, more -> apincer.android.mmate.ui.compose.MainScaffoldState.get().getHasMoreMusic().setValue(Boolean.TRUE.equals(more)));
        viewModel.loadError.observe(this, error -> apincer.android.mmate.ui.compose.MainScaffoldState.get().getMusicLoadError().setValue(error));
        viewModel.libraryEmpty.observe(this, empty -> apincer.android.mmate.ui.compose.MainScaffoldState.get().getLibraryEmpty().setValue(Boolean.TRUE.equals(empty)));
        viewModel.playbackError.observe(this, error -> {
            if (error != null) {
                android.widget.Toast.makeText(this, error, android.widget.Toast.LENGTH_LONG).show();
                viewModel.playbackError.setValue(null);
            }
        });
        viewModel.playbackNotice.observe(this, notice -> {
            if (notice != null) {
                android.widget.Toast.makeText(this, notice, android.widget.Toast.LENGTH_SHORT).show();
                viewModel.playbackNotice.setValue(null);
            }
        });
        viewModel.musicItemsLoading.observe(this, isLoading -> runOnUiThread(() -> apincer.android.mmate.ui.compose.ListInterop.updateRefreshing(isLoading)));

        WorkManager.getInstance(getApplicationContext())
                .getWorkInfosForUniqueWorkLiveData("MusicScanWork")
                .observe(this, workInfos -> {
                    if (workInfos != null && !workInfos.isEmpty()) {
                        WorkInfo workInfo = activeScanWork(workInfos);
                        WorkInfo.State currentState = workInfo.getState();
                        scanWork = workInfo;
                        if (currentState.isFinished()) {
                            if (lastWorkState == WorkInfo.State.RUNNING) {
                                viewModel.loadMusicItems(currentCriteria);
                            }
                            if (currentState == WorkInfo.State.FAILED && lastWorkState != null && !lastWorkState.isFinished()) {
                                android.widget.Toast.makeText(this, "Library scan failed. Run it again from Folders.", android.widget.Toast.LENGTH_LONG).show();
                            }
                        }
                        lastWorkState = currentState;
                        renderScanStatus();
                    }
                });
        WorkManager.getInstance(getApplicationContext())
                .getWorkInfosForUniqueWorkLiveData(apincer.android.mmate.worker.AnalyzeTracksWorker.WORK_NAME)
                .observe(this, workInfos -> {
                    if (workInfos == null || workInfos.isEmpty()) return;
                    WorkInfo workInfo = activeScanWork(workInfos);
                    // Show the measured DR values once analysis completes
                    if (workInfo.getState() == WorkInfo.State.SUCCEEDED
                            && analyzeWork != null && analyzeWork.getState() == WorkInfo.State.RUNNING) {
                        viewModel.loadMusicItems(currentCriteria);
                    }
                    analyzeWork = workInfo;
                    renderScanStatus();
                });
    }

    /** Header status: the scan while it is queued or running, then track analysis. */
    private void renderScanStatus() {
        WorkInfo.State scanState = scanWork != null ? scanWork.getState() : null;
        if (scanState == WorkInfo.State.RUNNING) {
            int progress = scanWork.getProgress().getInt("progress_value", 0);
            int total = scanWork.getProgress().getInt("total_files", 0);
            String scanMsg = total > 0 ? "Scanning: " + progress + "/" + total + " files" : "Scanning…";
            apincer.android.mmate.ui.compose.MainScaffoldState.updateScanning(true, scanMsg);
        } else if (scanState == WorkInfo.State.ENQUEUED || scanState == WorkInfo.State.BLOCKED) {
            // Waiting on its constraint (storage not low) or on a scan ahead of it
            apincer.android.mmate.ui.compose.MainScaffoldState.updateScanning(true, "Scan waiting to start…");
        } else if (analyzeWork != null && analyzeWork.getState() == WorkInfo.State.RUNNING) {
            int progress = analyzeWork.getProgress().getInt("progress_value", 0);
            int total = analyzeWork.getProgress().getInt("total_files", 0);
            String msg = total > 0 ? "Analyzing: " + progress + "/" + total + " tracks" : "Analyzing tracks…";
            apincer.android.mmate.ui.compose.MainScaffoldState.updateScanning(true, msg);
        } else {
            apincer.android.mmate.ui.compose.MainScaffoldState.updateScanning(false, "");
        }
    }

    /** A running scan first, then one still waiting, else the last reported (chained scans share one name). */
    private static WorkInfo activeScanWork(java.util.List<WorkInfo> workInfos) {
        WorkInfo waiting = null;
        for (WorkInfo info : workInfos) {
            if (info.getState() == WorkInfo.State.RUNNING) return info;
            if (waiting == null && !info.getState().isFinished()) waiting = info;
        }
        return waiting != null ? waiting : workInfos.get(workInfos.size() - 1);
    }

    public void setFloatingDockVisible(boolean visible) {
        apincer.android.mmate.ui.compose.MainScaffoldState.setFloatingDockVisible(visible);
    }

    public PlaybackState getLastPlaybackState() {
        return lastPlaybackState;
    }

    private void setupSelectionTracker() {
        actionModeCallback = new ActionModeCallback();
        selectionModel = new LibrarySelectionModel((count, selectedTracks) -> {
            if (count > 0) {
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
            apincer.android.mmate.ui.compose.ListInterop.updateSelectedTracks(new java.util.HashSet<>(selectedTracks));
        });
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
        apincer.android.mmate.ui.compose.MainScaffoldState.get().getHasActiveMusicFilters().setValue(
                hasActiveFilter || currentCriteria.isSearchMode());
        int count = (isTopLevelCategoryDir || hasActiveFilter) ? apincer.android.mmate.ui.compose.ListInterop.getTracks().size()
                : ((stats != null) ? stats.getTotalCount() : apincer.android.mmate.ui.compose.ListInterop.getTracks().size());
        long totalSize = hasActiveFilter ? apincer.android.mmate.ui.compose.ListInterop.getTracks().stream().mapToLong(Track::getFileSize).sum() : ((stats != null) ? stats.getTotalSize() : apincer.android.mmate.ui.compose.ListInterop.getTracks().stream().mapToLong(Track::getFileSize).sum());
        double totalDuration = hasActiveFilter ? apincer.android.mmate.ui.compose.ListInterop.getTracks().stream().mapToDouble(Track::getAudioDuration).sum() : ((stats != null) ? stats.getTotalDuration() : apincer.android.mmate.ui.compose.ListInterop.getTracks().stream().mapToDouble(Track::getAudioDuration).sum());

        String statText = "";
        if (!isEmpty(currentCriteria.getKeyword())) {
            if (count > 0) {
                statText = StringUtils.formatSongSize(count) + (count == 1 ? " Song" : " Songs");
            }

            if (isEmpty(currentCriteria.getFilterType()) && count > 0) {
                statText = statText + SYMBOL_ENC_SEP + playtimeLabel(totalDuration) + SYMBOL_ENC_SEP + StringUtils.formatStorageSize(totalSize);
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
                } else if (isTopLevelCategoryDir) {
                    // e.g. Audio Quality lists categories (Hi-Res, CD, Compressed), not tracks
                    unitTitle = count == 1 ? "Category" : "Categories";
                } else {
                    unitTitle = count == 1 ? "Track" : "Tracks";
                }
                statText = StringUtils.formatSongSize(count) + " " + unitTitle;
                if (stats != null && SearchCriteria.TYPE.LIBRARY.equals(type) && isEmpty(currentCriteria.getFilterType())) {
                    statText = statText + SYMBOL_ENC_SEP + playtimeLabel(totalDuration);
                    // Whole-library size was nearly always cut off beside Play, so About shows it;
                    // search results are short enough to keep theirs
                    if (currentCriteria.isSearchMode()) {
                        statText = statText + SYMBOL_ENC_SEP + StringUtils.formatStorageSize(totalSize);
                    }
                }
            }
        }

        // Name the active filter first ("Artist: Queen • 21 Tracks") so it survives truncation.
        String filterLabel = hasActiveFilter ? getFilterLabel() : "";
        if (!isEmpty(filterLabel)) {
            statText = isEmpty(statText) ? filterLabel : filterLabel + SYMBOL_ENC_SEP + statText;
        }

        apincer.android.mmate.ui.compose.MainScaffoldState.updateHeaderStats(statText);
        apincer.music.core.model.PlaylistEntry openPlaylist = SearchCriteria.TYPE.PLAYLIST.equals(type) && !isEmpty(currentCriteria.getKeyword())
                ? PlaylistRepository.findUserSongPlaylistByName(currentCriteria.getKeyword()) : null;
        apincer.android.mmate.ui.compose.MainScaffoldState.get().getOpenUserPlaylistUuid().setValue(
                openPlaylist != null ? openPlaylist.getUuid() : null);
        apincer.android.mmate.ui.compose.MainScaffoldState.updatePlaylistOverview(
                SearchCriteria.TYPE.PLAYLIST.equals(type) && isEmpty(currentCriteria.getKeyword()));
        apincer.android.mmate.ui.compose.MainScaffoldState.updateBackVisible(hasActiveFilter || currentCriteria.isSearchMode()
                || !SearchCriteria.TYPE.LIBRARY.equals(type) || !isEmpty(currentCriteria.getKeyword()));
    }

    /**
     * "22.9 Days of Music": a bare "22.9 Days" did not read as total playing time.
     * In search results it comes before the storage size, so truncation drops the size first.
     */
    private static String playtimeLabel(double totalSeconds) {
        return StringUtils.formatDuration(totalSeconds, true) + " of Music";
    }

    private String getFilterLabel() {
        String filterText = currentCriteria.getFilterText();
        if (isEmpty(filterText)) return "";
        if (Constants.FILTER_TYPE_PATH.equals(currentCriteria.getFilterType())) {
            filterText = StringUtils.truncate(DocumentFileCompat.getBasePath(getApplicationContext(), filterText), 38, StringUtils.TruncateType.PREFIX);
        } else {
            filterText = StringUtils.truncate(filterText, 38, StringUtils.TruncateType.SUFFIX);
        }
        return currentCriteria.getFilterType() + ": " + filterText;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSystemAccessState(false);
        refreshSleepTimerChip();
        // Settings may have changed the tap mode; row accessibility labels follow it
        MainScaffoldState.get().getListenerTapMode().setValue(
                Constants.TAP_MODE_LISTEN.equalsIgnoreCase(Settings.getTapActionMode(this)));
        if (reopenFoldersAfterStorageGrant && PermissionUtils.checkAccessPermissions(getApplicationContext())) {
            reopenFoldersAfterStorageGrant = false;
            doScanDirectories();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_REOPEN_FOLDERS, reopenFoldersAfterStorageGrant);
        // The open collection, filter and search survive configuration changes and process death
        if (currentCriteria != null) {
            outState.putString("criteria_type", currentCriteria.getType().name());
            outState.putString("criteria_keyword", currentCriteria.getKeyword());
            outState.putString("criteria_filter_type", currentCriteria.getFilterType());
            outState.putString("criteria_filter_text", currentCriteria.getFilterText());
            outState.putBoolean("criteria_search_mode", currentCriteria.isSearchMode());
            outState.putString("criteria_search_text", currentCriteria.getSearchText());
        }
    }

    private void restoreCriteria(@NonNull Bundle state) {
        String type = state.getString("criteria_type");
        if (type == null) return;
        try {
            SearchCriteria restored = new SearchCriteria(SearchCriteria.TYPE.valueOf(type), state.getString("criteria_keyword"));
            restored.setFilterType(state.getString("criteria_filter_type"));
            restored.setFilterText(state.getString("criteria_filter_text"));
            if (state.getBoolean("criteria_search_mode")) {
                restored.searchFor(state.getString("criteria_search_text"));
            }
            currentCriteria = restored;
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Ignoring unknown saved library type " + type);
        }
    }

    @Override
    protected void onPause() {
        sleepTimerHandler.removeCallbacks(sleepTimerTick);
        super.onPause();
    }

    private void refreshSystemAccessState(boolean forcePlayerRefresh) {
        boolean previouslyHadExternalPlayerAccess = MainScaffoldState.get()
                .getSystemAccess().getValue().getHasExternalPlayerAccess();
        boolean hasStorageAccess = PermissionUtils.checkFullStorageAccessPermissions(this);
        boolean hasExternalPlayerAccess = PermissionUtils.isNotificationListenerEnabled(this);
        MainScaffoldState.updateSystemAccess(hasStorageAccess, hasExternalPlayerAccess);
        boolean externalPlayerAccessChanged =
                previouslyHadExternalPlayerAccess != hasExternalPlayerAccess;
        if (isPlaybackServiceBound && playbackService != null
                && (forcePlayerRefresh || externalPlayerAccessChanged)) {
            playbackService.refreshPlayerDiscovery();
            updatePlayerPickerState();
        }
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
        apincer.android.mmate.ui.compose.MainScaffoldState.updateSearchQuery("");
        viewModel.loadMusicItems(currentCriteria);
    }

    private void doStartRefresh(SearchCriteria.TYPE type, String keyword) {
        currentCriteria.setType(type);
        currentCriteria.setKeyword(keyword);
        currentCriteria.setFilterType(null);
        currentCriteria.setFilterText(null);
        endSelectionForNavigation();
        viewModel.loadMusicItems(currentCriteria);
    }

    /**
     * Contextual selection refers to rows of the current list, so it must not survive a
     * library-destination change. Selection is additionally keyed by track identity, so a
     * batch action can never fall through to a track the user did not pick.
     */
    private void endSelectionForNavigation() {
        // Clear the field first: finish() synchronously invokes onDestroyActionMode(), which
        // would otherwise re-enter and finish a second time.
        ActionMode mode = actionMode;
        actionMode = null;
        if (mode != null) {
            mode.finish();
        }
        if (selectionModel != null) {
            selectionModel.clear();
        }
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
            return;
        }

        if (currentCriteria != null && !isEmpty(currentCriteria.getKeyword())
                && !SearchCriteria.TYPE.LIBRARY.equals(currentCriteria.getType())) {
            currentCriteria.setFilterText(null);
            currentCriteria.setFilterType(null);
            currentCriteria.setKeyword(null);
            apincer.android.mmate.ui.compose.ListInterop.updateRefreshing(true);
            viewModel.loadMusicItems(currentCriteria);
            return;
        }

        if (!apincer.android.mmate.ui.navigation.MainNavigationInterop.isAtLibraryRoot()) {
            apincer.android.mmate.ui.navigation.MainNavigationInterop.selectLibrary(
                    apincer.android.mmate.ui.navigation.LibraryDestination.ALL_SONGS);
            return;
        }

        doShowLeftMenus();
    }

    private boolean hasTransientLibraryState() {
        if (currentCriteria == null) return false;
        boolean hasFilter = !isEmpty(currentCriteria.getFilterType());
        boolean hasNestedCategory = !SearchCriteria.TYPE.LIBRARY.equals(currentCriteria.getType())
                && !isEmpty(currentCriteria.getKeyword());
        return hasFilter || currentCriteria.isSearchMode() || hasNestedCategory;
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
                        Track startTrack = null;
                        if (!qm.isShuffle()) {
                            startTrack = qm.getCurrentTrack();
                            if (startTrack == null) {
                                startTrack = qm.getNextTrack();
                            }
                        }
                        if (startTrack == null) {
                            startTrack = qm.getRandomTrack();
                        }
                        if (startTrack != null) {
                            playbackService.playSong(startTrack);
                        } else {
                            viewModel.playCurrentResults(null, playbackService);
                        }
                    }
                }
            }
        }
    }

    public void onDockNextClicked() {
        if (playbackService != null) {
            if (playbackService.getNowPlayingSong() == null) {
                onDockPlayPauseClicked();
            } else {
                playbackService.skipToNextInQueue();
            }
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
        if (playbackService != null) {
            if (playbackService.getNowPlayingSong() == null) {
                onDockPlayPauseClicked();
            } else {
                playbackService.skipToNextInQueue();
            }
        }
    }

    public void onAudioHubPrevious() {
        if (playbackService != null) playbackService.skipToPrevious();
    }

    public void onAudioHubShuffleToggle() {
        if (playbackService != null) {
            if (explainSmartQueueOrder()) return;
            boolean shuffle = !apincer.android.mmate.ui.compose.MainScaffoldState.get().getNowPlayingState().isShuffle().getValue();
            playbackService.setShuffleMode(shuffle);
            apincer.android.mmate.ui.compose.MainScaffoldState.get().getNowPlayingState().isShuffle().setValue(shuffle);
        }
    }

    public void onAudioHubRepeatToggle() {
        if (playbackService != null) {
            if (explainSmartQueueOrder()) return;
            int mode = apincer.android.mmate.ui.compose.MainScaffoldState.get().getNowPlayingState().getRepeatMode().getValue();
            int nextMode = (mode == 0) ? 1 : (mode == 1) ? 2 : 0;
            String modeStr = (nextMode == 1) ? "ALL" : (nextMode == 2) ? "ONE" : "OFF";
            playbackService.setRepeatMode(modeStr);
            apincer.android.mmate.ui.compose.MainScaffoldState.get().getNowPlayingState().getRepeatMode().setValue(nextMode);
        }
    }

    private boolean explainSmartQueueOrder() {
        if (playbackService.getQueueManager().getSource() == apincer.music.core.repository.QueueManager.Source.MANUAL) return false;
        android.widget.Toast.makeText(this, "Select Manual queue to enable shuffle or repeat", android.widget.Toast.LENGTH_SHORT).show();
        return true;
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

        if (isRunning) {
            String url = Constants.getPresentationUrl();
            state.setServerUrl(url);
            String netDesc = NetworkUtils.isWifiConnected(this) ? "Wi-Fi" : NetworkUtils.isHotspotActive(this) ? "Hotspot" : "Local Network";
            state.setBroadcastInfo("DLNA 1.5 • " + netDesc + " • Port " + apincer.music.core.server.BaseServer.WEB_SERVER_PORT);
            state.setServerStatusText("DLNA Server Active");
            state.setDiagnosticsSource(() -> webServer.getDiagnostics());
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
            state.setServerStatusText("Server Off");
            state.setServerUrl("");
            state.setBroadcastInfo(networkAvailable ? "Ready to stream" : "Wi-Fi / Hotspot disconnected");
            state.setQrCodeBitmap(null);
        }
    }

    public void onAudioHubStartServer() {
        getPreferences(MODE_PRIVATE).edit().putBoolean("media_server_auto_start", true).apply();
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
        getPreferences(MODE_PRIVATE).edit().putBoolean("media_server_auto_start", false).apply();
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
    public void onLibraryDestinationChanged(
            apincer.android.mmate.ui.navigation.LibraryDestination destination) {
        currentCriteria.resetSearch();
        apincer.android.mmate.ui.compose.MainScaffoldState.updateSearchQuery("");
        switch (destination) {
            case ALL_SONGS -> doStartRefresh(
                    SearchCriteria.TYPE.LIBRARY, Constants.TITLE_ALL_SONGS);
            case RECENTLY_ADDED -> doStartRefresh(
                    SearchCriteria.TYPE.LIBRARY, Constants.TITLE_INCOMING_SONGS);
            case SIMILAR_TRACKS -> doStartRefresh(
                    SearchCriteria.TYPE.LIBRARY, Constants.TITLE_DUPLICATE);
            case AUDIO_QUALITY -> doStartRefresh(SearchCriteria.TYPE.SOUND_GRADE, null);
            case PLAYLISTS -> {
                PlaylistRepository.loadPlaylists(getApplicationContext());
                doStartRefresh(SearchCriteria.TYPE.PLAYLIST, null);
            }
            case GENRES -> doStartRefresh(SearchCriteria.TYPE.GENRE, null);
            case ARTISTS -> doStartRefresh(SearchCriteria.TYPE.ARTIST, null);
        }
    }

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
    public void onTrackMenuAction(apincer.music.core.model.Track tag, int position, int actionId) {
        if (tag != null) {
            handleTrackMenuAction(tag, actionId);
        }
    }

    @Override
    public void onTrackQuickPlayClick(Track track) {
        if (isSelectionBlocked()) return;
        if (selectionModel != null && selectionModel.hasSelection()) {
            // During selection the artwork toggles the row like a row tap, never replaces the queue
            int position = viewModel.getMusicItemsFlow().getValue().indexOf(track);
            if (position >= 0) selectionModel.toggle(position);
            return;
        }
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
    public void onPlayResults() {
        if (isPlaybackServiceBound && playbackService != null) {
            viewModel.playCurrentResults(null, playbackService);
        }
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
    public void onRefreshPlayerTargets() {
        updatePlayerPickerState();
    }

    @Override
    public void onOpenSystemAudioOutput() {
        openSystemAudioOutputPanel();
    }

    @Override
    public void onEnableExternalPlayerAccess() {
        startActivity(PermissionActivity.createIntent(
                this,
                apincer.android.mmate.ui.compose.SystemAccessCapability.EXTERNAL_PLAYERS));
    }

    @Override
    public void onAudioHubSleepTimerSelected(long minutes, boolean endOfTrack) {
        if (playbackService != null) {
            playbackService.setSleepTimer(minutes, endOfTrack);
            refreshSleepTimerChip();
            if (minutes > 0) {
                android.widget.Toast.makeText(this, "Sleep timer set for " + minutes + " minutes", android.widget.Toast.LENGTH_SHORT).show();
            } else if (endOfTrack) {
                android.widget.Toast.makeText(this, "Sleep timer set to stop after this song", android.widget.Toast.LENGTH_SHORT).show();
            } else {
                android.widget.Toast.makeText(this, "Sleep timer turned off", android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final android.os.Handler sleepTimerHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable sleepTimerTick = this::refreshSleepTimerChip;

    /**
     * Shows the service's remaining sleep time and keeps it counting down while the screen is
     * visible. The chip clears itself once the timer fires (the service reports 0).
     */
    private void refreshSleepTimerChip() {
        sleepTimerHandler.removeCallbacks(sleepTimerTick);
        apincer.android.mmate.ui.compose.NowPlayingState nps = MainScaffoldState.get().getNowPlayingState();
        if (nps == null || playbackService == null) return;
        long remainingMs = playbackService.getSleepTimerRemainingMs();
        nps.isSleepTimerActive().setValue(remainingMs != 0);
        nps.getSleepTimerText().setValue(formatSleepRemaining(remainingMs));
        if (remainingMs != 0 && !isFinishing()) {
            sleepTimerHandler.postDelayed(sleepTimerTick, 1000);
        }
    }

    /** "Track End", "12m" (rounded up), "45s" in the last minute, or "" when off. */
    static String formatSleepRemaining(long remainingMs) {
        if (remainingMs < 0) return "Track End";
        if (remainingMs == 0) return "";
        long seconds = (remainingMs + 999) / 1000;
        return seconds < 60 ? seconds + "s" : ((seconds + 59) / 60) + "m";
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

    private apincer.android.mmate.ui.navigation.LibraryDestination getInitialLibraryDestination() {
        if (currentCriteria == null) {
            return apincer.android.mmate.ui.navigation.LibraryDestination.ALL_SONGS;
        }
        SearchCriteria.TYPE type = currentCriteria.getType();
        if (SearchCriteria.TYPE.ARTIST.equals(type)) {
            return apincer.android.mmate.ui.navigation.LibraryDestination.ARTISTS;
        } else if (SearchCriteria.TYPE.GENRE.equals(type)) {
            return apincer.android.mmate.ui.navigation.LibraryDestination.GENRES;
        } else if (SearchCriteria.TYPE.PLAYLIST.equals(type)) {
            return apincer.android.mmate.ui.navigation.LibraryDestination.PLAYLISTS;
        } else if (SearchCriteria.TYPE.SOUND_GRADE.equals(type)) {
            return apincer.android.mmate.ui.navigation.LibraryDestination.AUDIO_QUALITY;
        } else if (SearchCriteria.TYPE.LIBRARY.equals(type)) {
            String kw = currentCriteria.getKeyword();
            if (Constants.TITLE_INCOMING_SONGS.equals(kw)) {
                return apincer.android.mmate.ui.navigation.LibraryDestination.RECENTLY_ADDED;
            } else if (Constants.TITLE_DUPLICATE.equals(kw)) {
                return apincer.android.mmate.ui.navigation.LibraryDestination.SIMILAR_TRACKS;
            }
        }
        return apincer.android.mmate.ui.navigation.LibraryDestination.ALL_SONGS;
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
        }
        apincer.android.mmate.ui.navigation.LibraryDestination libraryDestination =
                apincer.android.mmate.ui.navigation.LibraryDestinationMenuMapping
                        .fromMenuItemId(item.getItemId());
        if (libraryDestination != null) {
            apincer.android.mmate.ui.navigation.MainNavigationInterop
                    .selectLibrary(libraryDestination);
            return true;
        }
        if (item.getItemId() == R.id.menu_settings) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (item.getItemId() == R.id.menu_system_access) {
            startActivity(PermissionActivity.createIntent(
                    this,
                    apincer.android.mmate.ui.compose.SystemAccessCapability.NONE));
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

    public void handleTrackMenuAction(Track track, int actionId) {
        List<Track> singleTrackList = Collections.singletonList(track);
        if (actionId == R.id.action_play_now) {
            viewModel.playCurrentResults(track, playbackService);
        } else if (actionId == R.id.action_play_next) {
            if (isPlaybackServiceBound && playbackService != null) {
                playbackService.getQueueManager().addPlayNext(track);
                syncQueueState();
                android.widget.Toast.makeText(MainActivity.this, "Playing next", android.widget.Toast.LENGTH_SHORT).show();
            }
        } else if (actionId == R.id.action_add_queue) {
            if (isPlaybackServiceBound && playbackService != null) {
                playbackService.getQueueManager().addPlayingQueue(track);
                syncQueueState();
                android.widget.Toast.makeText(MainActivity.this, "Added to queue", android.widget.Toast.LENGTH_SHORT).show();
            }
        } else if (actionId == R.id.action_add_to_playlist) {
            AddToPlaylistDialog.show(this, track, () -> {
                // Showing a playlist's songs: the new song belongs on screen
                if (SearchCriteria.TYPE.PLAYLIST.equals(currentCriteria.getType())) {
                    viewModel.loadMusicItems(currentCriteria);
                }
            });
        } else if (actionId == R.id.action_remove_from_playlist) {
            doConfirmRemoveFromPlaylist(track);
        } else if (actionId == R.id.action_go_to_artist) {
            doShowFilteredLibrary(Constants.FILTER_TYPE_ARTIST, track.getArtist());
        } else if (actionId == R.id.action_go_to_album) {
            doShowFilteredLibrary(Constants.FILTER_TYPE_ALBUM, track.getAlbum());
        } else if (actionId == R.id.action_encoding_file) {
            doEncodeAudioFiles(singleTrackList);
        } else if (actionId == R.id.action_open_with) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            android.net.Uri uri = MusicFileProvider.getUriForFile(track.getPath());
            intent.setDataAndType(uri, "audio/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(Intent.createChooser(intent, "Open with"));
            } catch (Exception e) {
                Log.e(TAG, "Failed to start external player activity", e);
            }
        }
    }

    private void doConfirmRemoveFromPlaylist(Track track) {
        String uuid = apincer.android.mmate.ui.compose.MainScaffoldState.get().getOpenUserPlaylistUuid().getValue();
        String playlistName = currentCriteria != null ? currentCriteria.getKeyword() : null;
        if (uuid == null || playlistName == null) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setTitle(R.string.playlist_remove_menu)
                .setMessage(getString(R.string.playlist_remove_confirm, track.getTitle(), playlistName))
                .setPositiveButton(R.string.playlist_remove, (d, w) -> {
                    if (PlaylistRepository.removeTrackFromPlaylist(getApplicationContext(), uuid, track)) {
                        android.widget.Toast.makeText(this, getString(R.string.playlist_removed, playlistName), android.widget.Toast.LENGTH_SHORT).show();
                        viewModel.loadMusicItems(currentCriteria);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Long-press on one of the user's playlist cards: delete it (bundled playlists are not deletable). */
    private boolean doConfirmDeletePlaylist(Track playlistCard) {
        String uuid = playlistCard.getUniqueKey();
        if (!PlaylistRepository.isUserPlaylist(uuid)) return false;
        String name = playlistCard.getTitle();
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setTitle(R.string.playlist_delete_title)
                .setMessage(getString(R.string.playlist_delete_confirm, name))
                .setPositiveButton(R.string.playlist_delete, (d, w) -> {
                    PlaylistRepository.deleteCustomPlaylist(getApplicationContext(), uuid);
                    android.widget.Toast.makeText(this, getString(R.string.playlist_deleted, name), android.widget.Toast.LENGTH_SHORT).show();
                    viewModel.loadMusicItems(currentCriteria);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        return true;
    }

    /** Narrows the current library view to one artist or album, as the song page's links do. */
    private void doShowFilteredLibrary(String filterType, String filterText) {
        if (isEmpty(filterText) || currentCriteria == null) return;
        if (currentCriteria.isSearchMode()) {
            // Search results ignore filters (TagRepository), so leave search first.
            currentCriteria.resetSearch();
            apincer.android.mmate.ui.compose.MainScaffoldState.updateSearchQuery("");
        }
        currentCriteria.setFilterType(filterType);
        currentCriteria.setFilterText(filterText);
        viewModel.loadMusicItems(currentCriteria);
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
        apincer.android.mmate.audio.UsbBitPerfectSession.Status usbStatus =
                (playbackService instanceof apincer.android.mmate.service.MusicMateServiceImpl msi)
                        ? msi.getUsbBitPerfectStatus()
                        : apincer.android.mmate.audio.UsbBitPerfectSession.Status.disabled();

        List<apincer.android.mmate.ui.compose.PlayerTargetItem> streamerItems = new java.util.ArrayList<>();
        List<apincer.android.mmate.ui.compose.PlayerTargetItem> localItems = new java.util.ArrayList<>();
        List<apincer.android.mmate.ui.compose.PlayerTargetItem> appItems = new java.util.ArrayList<>();

        if (renderers != null && !renderers.isEmpty()) {
            for (apincer.music.core.playback.spi.PlaybackTarget target : renderers) {
                boolean isSelected = current != null && current.getTargetId().equals(target.getTargetId());

                if (target instanceof apincer.music.core.playback.ExternalAndroidPlayer extPlayer && "local".equalsIgnoreCase(extPlayer.getTargetId())) {
                    // 1. Local Device Target (This Device)
                    String title = "Phone Speaker";
                    int iconRes = R.drawable.ic_round_speaker_24;
                    String subtitle = "Plays on this phone";

                    if (audioOutputDevice != null) {
                        if (audioOutputDevice.getName() != null && !audioOutputDevice.getName().isEmpty()) {
                            title = audioOutputDevice.getName();
                        }
                        if (audioOutputDevice.isUsb()) {
                            subtitle = isSelected && usbStatus.isRequested()
                                    && usbStatus.getDeviceId() == audioOutputDevice.getId()
                                    ? "USB bit-perfect requested" : "USB audio output";
                            iconRes = R.drawable.ic_baseline_usb_24;
                        } else if (audioOutputDevice.isBluetooth()) {
                            String codec = audioOutputDevice.getCodec();
                            subtitle = (codec != null && !codec.isEmpty()) ? "Bluetooth • " + codec : "Bluetooth Audio";
                            iconRes = R.drawable.ic_round_bluetooth_audio_24;
                        } else if (audioOutputDevice.getResId() != 0) {
                            iconRes = audioOutputDevice.getResId();
                            subtitle = "Plays on this phone";
                        }
                    }

                    localItems.add(new apincer.android.mmate.ui.compose.PlayerTargetItem(
                            target,
                            title,
                            subtitle,
                            iconRes,
                            isSelected,
                            false,
                            null,
                            apincer.android.mmate.ui.compose.PlayerCategory.THIS_DEVICE
                    ));
                } else if (AudioOutputHelper.isExternalAppTarget(target)) {
                    // 2. External Music App Target
                    String title = target.getDisplayName();
                    String subtitle = "External Music App";
                    int iconRes = R.drawable.rounded_music_note_24;

                    appItems.add(new apincer.android.mmate.ui.compose.PlayerTargetItem(
                            target,
                            title,
                            subtitle,
                            iconRes,
                            isSelected,
                            false,
                            target.getTargetId(), // Package name for real app icon
                            apincer.android.mmate.ui.compose.PlayerCategory.MUSIC_APP
                    ));
                } else {
                    // 3. Network Streamer (DLNA / UPnP)
                    String title = target.getDisplayName();
                    String ip = apincer.music.core.utils.NetworkUtils.extractIpAddress(target.getDescription());
                    String subtitle;
                    if (!ip.isEmpty()) {
                        subtitle = ip + " • DLNA Renderer";
                    } else if (target.getDescription() != null && !target.getDescription().isEmpty()) {
                        subtitle = target.getDescription() + " • DLNA Renderer";
                    } else {
                        subtitle = "DLNA Network Renderer";
                    }
                    int iconRes = R.drawable.rounded_music_cast_24;

                    streamerItems.add(new apincer.android.mmate.ui.compose.PlayerTargetItem(
                            target,
                            title,
                            subtitle,
                            iconRes,
                            isSelected,
                            target.isStreaming(),
                            null,
                            apincer.android.mmate.ui.compose.PlayerCategory.NETWORK_STREAMER
                    ));
                }
            }
        }

        // Sort items: Streamers first, then Local Device, then Music Apps
        streamerItems.sort((a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()));
        appItems.sort((a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()));

        List<apincer.android.mmate.ui.compose.PlayerTargetItem> allItems = new java.util.ArrayList<>();
        allItems.addAll(streamerItems);
        allItems.addAll(localItems);
        allItems.addAll(appItems);

        MainScaffoldState.setPlayerTargets(allItems);
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
            reopenFoldersAfterStorageGrant = true;
            startActivity(PermissionActivity.createIntent(
                    this,
                    apincer.android.mmate.ui.compose.SystemAccessCapability.STORAGE));
            return;
        }

        List<String> defaultPaths = FileRepository.getDefaultMusicPaths(this);
        Set<String> defaultPathsSet = new HashSet<>(defaultPaths);
        List<String> dirs = TagRepository.getDirectories(this);
        List<String> storageIds = DocumentFileCompat.getStorageIds(getApplicationContext());

        androidx.activity.ComponentDialog foldersDialog = new androidx.activity.ComponentDialog(this, R.style.AlertDialogTheme);
        foldersDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        View cview = apincer.android.mmate.ui.compose.DialogInterop.createMusicFoldersDialogView(
            this,
            foldersDialog,
            dirs,
            defaultPathsSet,
            storageIds,
            foldersDialog::dismiss, // onClose
            foldersDialog::dismiss, // onCancel
            (isDeep, updatedDirs) -> { // onScan
                if (isDeep) {
                    new MaterialAlertDialogBuilder(MainActivity.this, R.style.AlertDialogTheme)
                        .setTitle("Full Rescan")
                        .setMessage(getString(R.string.directories_confirm_full_scan))
                        .setPositiveButton("Start", (dialog, which) -> {
                            apincer.music.core.Settings.setDirectories(getApplicationContext(), updatedDirs);
                            apincer.android.mmate.worker.ScanAudioFileWorker.startScan(getApplicationContext(), true);
                            foldersDialog.dismiss();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                } else {
                    apincer.music.core.Settings.setDirectories(getApplicationContext(), updatedDirs);
                    apincer.android.mmate.worker.ScanAudioFileWorker.startScan(getApplicationContext(), false);
                    foldersDialog.dismiss();
                }
            },
            (sid, onSelected) -> { // onAddStorage: return the selection to the dialog's draft
                DialogProperties properties = new DialogProperties();
                properties.selection_mode = DialogConfigs.SINGLE_MODE;
                properties.selection_type = DialogConfigs.DIR_SELECT;
                FilePickerDialog dialog = new FilePickerDialog(MainActivity.this, properties);
                dialog.setDialogSelectionListener(files -> {
                    if (files != null && files.length > 0) {
                        onSelected.accept(files[0]);
                    }
                });
                dialog.setTitle("Select a Directory");
                dialog.show();
            }
        );

        foldersDialog.setContentView(cview);
        foldersDialog.setCanceledOnTouchOutside(false);

        // Make popup round corners
        if (foldersDialog.getWindow() != null) {
            foldersDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        foldersDialog.show();
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

    /** Batch dialogs close on completion, so per-file failures are summarized afterwards. */
    private void showFileOperationFailures(String title, List<Track> failed, int total) {
        if (failed.isEmpty() || isFinishing() || isDestroyed()) return;
        StringBuilder names = new StringBuilder();
        for (Track tag : failed) {
            names.append("\n").append(new java.io.File(tag.getPath()).getName());
        }
        new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setTitle(title)
                .setMessage(failed.size() + " of " + total + " files failed:" + names)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void doDeleteMediaItems(List<Track> selections) {
        if (selections.isEmpty()) return;

        final AlertDialog[] alertHolder = new AlertDialog[1];
        final List<Track> failed = new ArrayList<>();
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
                                    if (FileOperationTask.isFailureStatus(status)) failed.add(tag);
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
                                    showFileOperationFailures("Some tracks weren’t removed", failed, selections.size());
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
        final List<Track> failed = new ArrayList<>();
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
                                    if (FileOperationTask.isFailureStatus(status)) failed.add(tag);
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
                                    showFileOperationFailures("Some files weren’t moved", failed, selections.size());
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
        final List<Track> failed = new ArrayList<>();

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
                                if (FileOperationTask.isFailureStatus(status)) failed.add(tag);
                            });
                        }

                        @Override
                        public void onComplete() {
                            runOnUiThread(() -> {
                                viewModel.loadMusicItems();
                                busy = false;
                                alert.dismiss();
                                showFileOperationFailures("Some files weren’t converted", failed, selections.size());
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
            } else if (id == R.id.action_add_to_playlist) {
                AddToPlaylistDialog.show(MainActivity.this, new java.util.ArrayList<>(getSelections()), null);
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
                selectionModel.selectAll();
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            actionMode = null;
            selectionModel.clear();
        }

        private List<Track> getSelections() {
            return selectionModel.getSelectedTracks();
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
            MainBackPolicy.Action action = MainBackPolicy.resolve(
                    apincer.android.mmate.ui.compose.DrawerInterop.isDrawerOpen(),
                    apincer.android.mmate.ui.navigation.MainNavigationInterop.isOverlayOpen(),
                    actionMode != null,
                    hasTransientLibraryState(),
                    apincer.android.mmate.ui.navigation.MainNavigationInterop.isAtLibraryRoot());
            if (action == MainBackPolicy.Action.CLOSE_DRAWER) {
                apincer.android.mmate.ui.compose.DrawerInterop.closeDrawer();
                return;
            }
            if (action == MainBackPolicy.Action.DISMISS_OVERLAY) {
                apincer.android.mmate.ui.navigation.MainNavigationInterop.popOverlay();
                return;
            }
            if (action == MainBackPolicy.Action.FINISH_SELECTION && actionMode != null) {
                actionMode.finish();
                return;
            }
            if (action == MainBackPolicy.Action.CLEAR_SEARCH) {
                onSearchBackClicked();
                return;
            }
            if (action == MainBackPolicy.Action.NAVIGATE_LIBRARY) {
                apincer.android.mmate.ui.navigation.MainNavigationInterop.selectLibrary(
                        apincer.android.mmate.ui.navigation.LibraryDestination.ALL_SONGS);
                return;
            }
            setEnabled(false);
            getOnBackPressedDispatcher().onBackPressed();
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
        if (selectionModel != null && selectionModel.hasSelection()) {
            selectionModel.toggle(position);
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
            viewModel.playCurrentResults(tag, playbackService);
        } else {
            android.widget.Toast.makeText(this, "No active player — connect a device first", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    public void onTrackLongClicked(apincer.music.core.model.Track tag, int position) {
        if (isSelectionBlocked()) return;
        if (tag != null && tag.isContainer()) {
            // Cards are not selectable; a long-press on one of the user's playlists offers Delete
            if (SearchCriteria.TYPE.PLAYLIST.equals(tag.getContainerType())) doConfirmDeletePlaylist(tag);
            return;
        }
        if (selectionModel != null && selectionModel.hasSelection()) {
            selectionModel.select(position);
            return;
        }
        String mode = Settings.getTapActionMode(this);
        if (Constants.TAP_MODE_LISTEN.equalsIgnoreCase(mode) && tag != null && !tag.isContainer()) {
            doShowEditActivity(java.util.Collections.singletonList(tag));
            return;
        }
        if (selectionModel != null) {
            selectionModel.select(position);
        }
    }

    public void onFolderPlayClicked(apincer.music.core.model.Track tag) {
        if (isPlaybackServiceBound && playbackService != null) {
            viewModel.playCollection(tag, playbackService, false); // reports its outcome via playbackNotice/Error
        } else {
            android.widget.Toast.makeText(this, "No active player — connect a device first", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    public void onFolderEnqueueClicked(apincer.music.core.model.Track tag) {
        if (isPlaybackServiceBound && playbackService != null) {
            viewModel.playCollection(tag, playbackService, true); // reports its outcome via playbackNotice/Error
        } else {
            android.widget.Toast.makeText(this, "No active player — connect a device first", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    public void onLoadMoreMusic() {
        viewModel.loadMoreMusicItems();
    }

    public void onListRefresh() {
        viewModel.loadMusicItems();
    }

    @Override
    public void onDiscoverMusicFolders() {
        doScanDirectories();
    }

    @Override
    public void onClearMusicFilters() {
        currentCriteria.resetSearch();
        currentCriteria.setFilterType(null);
        currentCriteria.setFilterText(null);
        apincer.android.mmate.ui.compose.MainScaffoldState.updateSearchQuery("");
        viewModel.loadMusicItems(currentCriteria);
    }

    @Override
    public void onBrowseAllMusic() {
        if (apincer.android.mmate.ui.navigation.MainNavigationInterop.isAtLibraryRoot()) {
            onLibraryDestinationChanged(
                    apincer.android.mmate.ui.navigation.LibraryDestination.ALL_SONGS);
        } else {
            apincer.android.mmate.ui.navigation.MainNavigationInterop.selectLibrary(
                    apincer.android.mmate.ui.navigation.LibraryDestination.ALL_SONGS);
        }
    }
}

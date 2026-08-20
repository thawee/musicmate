package apincer.android.mmate.ui.view;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static apincer.android.mmate.service.MusicMateServiceImpl.SERVER_STATUS_NO_WIFI;
import static apincer.android.mmate.service.MusicMateServiceImpl.SERVER_STATUS_OFFLINE;
import static apincer.android.mmate.service.MusicMateServiceImpl.SERVER_STATUS_ONLINE_PREFIX;
import static apincer.music.core.utils.StringUtils.SYMBOL_ENC_SEP;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textview.MaterialTextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import apincer.android.mmate.R;
import apincer.android.mmate.coil3.CoverartFetcher;
import apincer.android.mmate.service.MusicMateServiceImpl;
import apincer.android.mmate.ui.viewmodel.MediaServerViewModel;
import apincer.android.mmate.ui.compose.DialogInterop;
import apincer.android.mmate.ui.compose.MediaServerState;
import apincer.android.mmate.utils.AudioOutputHelper;
import apincer.android.mmate.utils.BitmapHelper;
import apincer.android.mmate.utils.TagUIUtils;
import apincer.music.core.Constants;
import apincer.music.core.model.Track;
import apincer.music.core.playback.DMRPlayer;
import apincer.music.core.playback.ExternalAndroidPlayer;
import apincer.music.core.playback.PlaybackState;
import apincer.music.core.playback.spi.PlaybackService;
import apincer.music.core.playback.spi.PlaybackTarget;
import apincer.music.core.repository.QueueManager;
import apincer.music.core.server.spi.MediaServerHub;
import apincer.music.core.utils.ApplicationUtils;
import apincer.music.core.utils.NetworkUtils;
import apincer.music.core.utils.PlayerNameUtils;
import apincer.music.core.utils.StringUtils;
import apincer.music.core.utils.TagUtils;
import coil3.SingletonImageLoader;
import coil3.request.ImageRequest;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Master Unified Audio Hub Bottom Sheet ("Music Center"):
 * a full-height 3-tab viewport (Playback | Queue | Server) driven by a
 * segmented tab switcher backed by a ViewPager2. The selected tab is sticky
 * for the current app session — reopening the sheet restores the last tab.
 */
@AndroidEntryPoint
public class AudioHubBottomSheet extends BottomSheetDialogFragment {
    public static final String TAG = "AudioHubBottomSheet";
    private static final String ARG_INITIAL_TAB = "ARG_INITIAL_TAB";

    public static final int TAB_NOW_PLAYING = 0;
    public static final int TAB_QUEUE = 1;
    public static final int TAB_MEDIA_SERVER = 2;

    // Sticky session state: last tab viewed, restored on next open (per DESIGN.md §8C)
    private static int sLastSelectedTab = TAB_NOW_PLAYING;

    private int initialTab = TAB_NOW_PLAYING;

    private PlaybackService playbackService;
    private boolean isPlaybackServiceBound = false;
    private MediaServerViewModel mediaServerViewModel;
    private MediaServerState mediaServerState = new MediaServerState();
    private apincer.android.mmate.ui.compose.NowPlayingState nowPlayingState = new apincer.android.mmate.ui.compose.NowPlayingState();
    private apincer.android.mmate.ui.compose.QueueState queueState = new apincer.android.mmate.ui.compose.QueueState(new ArrayList<>(), null);

    // ViewPager & Segmented Tab UI
    private ViewPager2 viewPager;
    private MaterialButtonToggleGroup tabToggleGroup;

    // Master Header Actions
    private ImageView btnCastHeader;

    // Pre-inflated Pages
    private View viewNowPlayingPage;
    private View viewQueuePage;
    private View viewMediaServerPage;

    // Media Server UI
    private TextView tvServerName;
    private TextView tvServerStatus;
    private View tvServerStatusIcon;
    private TextView tvServerAddress;
    private TextView tvServerBroadcastInfo;
    private TextView tvEngineDescription;
   // private TextView tvServerPowerBy;
    private ImageView qrCodeImage;
    private Button btnStartServer;
    private Button btnStopServer;

    public AudioHubBottomSheet() {
        // Default constructor
    }

    public static AudioHubBottomSheet newInstance() {
        // Sticky tab: reopen at the last tab viewed this session
        return newInstance(sLastSelectedTab);
    }

    public static AudioHubBottomSheet newInstance(int initialTab) {
        AudioHubBottomSheet sheet = new AudioHubBottomSheet();
        Bundle args = new Bundle();
        args.putInt(ARG_INITIAL_TAB, initialTab);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            initialTab = getArguments().getInt(ARG_INITIAL_TAB, TAB_NOW_PLAYING);
        }
        mediaServerViewModel = new ViewModelProvider(this).get(MediaServerViewModel.class);
    }

    private AutoCloseable songSubscription;
    private AutoCloseable playerSubscription;
    private AutoCloseable stateSubscription;

    private final android.media.AudioDeviceCallback audioDeviceCallback = new android.media.AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(android.media.AudioDeviceInfo[] addedDevices) {
            super.onAudioDevicesAdded(addedDevices);
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(this::refreshUIFromDeviceChange);
            }
        }

        @Override
        public void onAudioDevicesRemoved(android.media.AudioDeviceInfo[] removedDevices) {
            super.onAudioDevicesRemoved(removedDevices);
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(this::refreshUIFromDeviceChange);
            }
        }
        
        private void refreshUIFromDeviceChange() {
            if (viewNowPlayingPage != null) populateNowPlayingSheet(viewNowPlayingPage);
            if (viewQueuePage != null) populateQueueSection(viewQueuePage);
        }
    };

    private void subscribeToServiceUpdates() {
        unsubscribeFromServiceUpdates();
        if (playbackService == null) return;

        songSubscription = playbackService.subscribeNowPlayingSong(
                optTrack -> {
                    if (isAdded() && getActivity() != null) {
                        getActivity().runOnUiThread(this::refreshUI);
                    }
                },
                err -> Log.e(TAG, "Error observing song change", err)
        );
        
        playerSubscription = playbackService.subscribePlaybackTarget(
                optPlayer -> {
                    if (isAdded() && getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (viewNowPlayingPage != null) populateSignalPathWidget(viewNowPlayingPage, playbackService.getNowPlayingSong());
                            if (viewQueuePage != null) populateSignalPathWidget(viewQueuePage, playbackService.getNowPlayingSong());
                        });
                    }
                },
                err -> Log.e(TAG, "Error observing player change", err)
        );

        stateSubscription = playbackService.subscribePlaybackState(
                state -> {
                    if (isAdded() && getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (viewNowPlayingPage != null) {
                                updatePlaybackProgress(viewNowPlayingPage, state);
                            }
                        });
                    }
                },
                err -> Log.e(TAG, "Error observing playback state", err)
        );
    }

    private void unsubscribeFromServiceUpdates() {
        if (songSubscription != null) {
            try { songSubscription.close(); } catch (Exception ignored) {}
            songSubscription = null;
        }
        if (playerSubscription != null) {
            try { playerSubscription.close(); } catch (Exception ignored) {}
            playerSubscription = null;
        }
        if (stateSubscription != null) {
            try { stateSubscription.close(); } catch (Exception ignored) {}
            stateSubscription = null;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getContext() != null) {
            Intent intent = new Intent(getContext(), MusicMateServiceImpl.class);
            getContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        }

        if (getActivity() instanceof apincer.android.mmate.ui.MainActivity ma && ma.isPlaybackServiceBound()) {
            playbackService = ma.getPlaybackService();
            isPlaybackServiceBound = true;
            subscribeToServiceUpdates();
            refreshUI();
        }

        if (getActivity() instanceof apincer.android.mmate.ui.MainActivity ma) {
            ma.setFloatingDockVisible(false);
        }

        if (getDialog() != null) {
            View container = getDialog().findViewById(com.google.android.material.R.id.container);
            View coordinator = getDialog().findViewById(com.google.android.material.R.id.coordinator);
            View touchOutside = getDialog().findViewById(com.google.android.material.R.id.touch_outside);
            View bottomSheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);

            if (bottomSheet != null) {
                bottomSheet.setBackground(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);

                float density = getResources().getDisplayMetrics().density;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                int bottomNavMargin = (int) (12 * density);

                if (coordinator != null) {
                    coordinator.setPadding(0, 0, 0, bottomNavMargin);
                }

                int targetHeight = (int) (screenHeight * 0.65);
                int maxHeight = screenHeight - bottomNavMargin - (int) (48 * density);
                if (targetHeight > maxHeight) {
                    targetHeight = maxHeight;
                }
                int minHeight = (int) (420 * density);
                if (targetHeight < minHeight) {
                    targetHeight = minHeight;
                }

                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) bottomSheet.getLayoutParams();
                if (lp != null) {
                    int sideMargin = (int) (12 * density);
                    lp.leftMargin = sideMargin;
                    lp.rightMargin = sideMargin;
                    lp.height = targetHeight;
                    bottomSheet.setLayoutParams(lp);
                }

                behavior.setPeekHeight(targetHeight);
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            }
        }

        if (getContext() != null) {
            android.media.AudioManager audioManager = (android.media.AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.registerAudioDeviceCallback(audioDeviceCallback, null);
            }
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (getActivity() instanceof apincer.android.mmate.ui.MainActivity ma) {
            ma.setFloatingDockVisible(true);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        unsubscribeFromServiceUpdates();
        if (isPlaybackServiceBound && getContext() != null) {
            getContext().unbindService(serviceConnection);
            isPlaybackServiceBound = false;
        }

        if (getContext() != null) {
            android.media.AudioManager audioManager = (android.media.AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.unregisterAudioDeviceCallback(audioDeviceCallback);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_audio_hub, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tabToggleGroup = view.findViewById(R.id.audio_hub_tab_group);
        viewPager = view.findViewById(R.id.audio_hub_view_pager);

        btnCastHeader = view.findViewById(R.id.btn_select_target_player);

        View closeBtn = view.findViewById(R.id.btn_close_audio_hub);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> dismiss());
        }

        if (btnCastHeader != null) {
            btnCastHeader.setOnClickListener(v -> showPlayerPicker(btnCastHeader));
        }

        if (tabToggleGroup != null) {
            tabToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (isChecked) {
                    int selectedTab = TAB_NOW_PLAYING;
                    if (checkedId == R.id.tab_now_playing) {
                        selectedTab = TAB_NOW_PLAYING;
                    } else if (checkedId == R.id.tab_queue) {
                        selectedTab = TAB_QUEUE;
                    } else if (checkedId == R.id.tab_media_server) {
                        selectedTab = TAB_MEDIA_SERVER;
                    }
                    viewPager.setCurrentItem(selectedTab, true);
                    updateTabColors(selectedTab);
                }
            });
        }

        // Disable nested scrolling on ViewPager2 so BottomSheetBehavior ignores it
        // and correctly targets the Queue RecyclerView for vertical nested scrolling.
        if (viewPager != null && viewPager.getChildCount() > 0) {
            View child = viewPager.getChildAt(0);
            if (child instanceof RecyclerView) {
                child.setNestedScrollingEnabled(false);
            }
        }

        // Pre-inflate page views for ViewPager2
        
        viewNowPlayingPage = DialogInterop.createNowPlayingPageView(
            requireContext(),
            nowPlayingState,
            () -> {
                if (playbackService != null) {
                    if (nowPlayingState.getPlaybackState().getValue() != null && nowPlayingState.getPlaybackState().getValue().currentState == apincer.music.core.playback.PlaybackState.State.PLAYING) playbackService.pausePlayer();
                    else if (playbackService.getNowPlayingSong() != null) playbackService.playSong(playbackService.getNowPlayingSong());
                }
            },
            () -> { if (playbackService != null) playbackService.skipToNextInQueue(); },
            () -> { if (playbackService != null) playbackService.skipToPrevious(); },
            () -> {
                if (playbackService != null) {
                    boolean shuffle = !nowPlayingState.isShuffle().getValue();
                    playbackService.setShuffleMode(shuffle);
                    nowPlayingState.isShuffle().setValue(shuffle);
                }
            },
            () -> {
                if (playbackService != null) {
                    int mode = nowPlayingState.getRepeatMode().getValue();
                    int nextMode = (mode == 0) ? 1 : (mode == 1) ? 2 : 0;
                    playbackService.setRepeatMode(String.valueOf(nextMode));
                    nowPlayingState.getRepeatMode().setValue(nextMode);
                }
            },
            (progress) -> {
                if (playbackService != null) {
                    long duration = nowPlayingState.getDurationMs().getValue();
                    if (duration > 0) playbackService.seekTo((long) (progress * duration));
                }
            },
            () -> {}, // Vol Down
            () -> {}, // Vol Up
            (vol) -> {}, // Vol Change
            () -> {
                if (playbackService != null && playbackService.getNowPlayingSong() != null) {
                    dismiss();
                    if (getActivity() instanceof apincer.android.mmate.ui.MainActivity) {
                        ((apincer.android.mmate.ui.MainActivity) getActivity()).scrollToSong(playbackService.getNowPlayingSong());
                    }
                }
            }
        );

        
        viewQueuePage = DialogInterop.createQueuePageView(
            requireContext(),
            queueState,
            track -> {
                if (isPlaybackServiceBound && playbackService != null) {
                    playbackService.playSong(track);
                    if (viewNowPlayingPage != null) populateNowPlayingSheet(viewNowPlayingPage);
                    populateQueueSection(viewQueuePage);
                }
            },
            (track, index) -> {
                QueueManager qm = playbackService != null ? playbackService.getQueueManager() : null;
                if (qm != null) {
                    qm.removeTrack(index);
                }
            },
            () -> {
                QueueManager qm = playbackService != null ? playbackService.getQueueManager() : null;
                if (qm != null) {
                    qm.emptyPlayingQueue();
                    Toast.makeText(getContext(), "Queue cleared", Toast.LENGTH_SHORT).show();
                    populateQueueSection(viewQueuePage);
                }
            },
            () -> {
                Toast.makeText(getContext(), "Jumped to playing track", Toast.LENGTH_SHORT).show();
            }
        );

        
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
        String initialEngine = prefs.getString(Constants.PREF_SERVER_ENGINE, "httpcore");
        mediaServerState.setCurrentEngine(initialEngine);
        mediaServerState.setEngineDescription(getEngineDescription(initialEngine));

        viewMediaServerPage = DialogInterop.createMediaServerPageView(
            requireContext(),
            mediaServerState,
            engine -> {
                String prevEngine = prefs.getString(Constants.PREF_SERVER_ENGINE, "httpcore");
                if (!engine.equals(prevEngine)) {
                    prefs.edit().putString(Constants.PREF_SERVER_ENGINE, engine).apply();
                    mediaServerState.setCurrentEngine(engine);
                    mediaServerState.setEngineDescription(getEngineDescription(engine));
                    mediaServerViewModel.restartServer();
                    Toast.makeText(getContext(), "Switching engine — restarting server…", Toast.LENGTH_SHORT).show();
                }
            },
            () -> mediaServerViewModel.startServer(),
            () -> mediaServerViewModel.stopServer(),
            () -> {
                String url = mediaServerState.getServerUrl();
                if (url != null && !url.isEmpty()) {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("Server URL", url);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(getContext(), "Server URL copied to clipboard", Toast.LENGTH_SHORT).show();
                    }
                }
            },
            () -> {
                String url = mediaServerState.getServerUrl();
                if (url != null && url.startsWith("http")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "Could not open browser: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            },
            () -> Toast.makeText(getContext(), "Scan with phone or tablet to open WebUI", Toast.LENGTH_SHORT).show()
        );


        flattenPage(viewNowPlayingPage, true);
        flattenPage(viewQueuePage, false);
        flattenPage(viewMediaServerPage, false);

        setupNowPlayingTab();
        setupMediaServerTab(viewMediaServerPage);

        // ViewPager2 Adapter
        viewPager.setAdapter(new RecyclerView.Adapter<>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                FrameLayout container = new FrameLayout(parent.getContext());
                container.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                return new RecyclerView.ViewHolder(container) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                FrameLayout container = (FrameLayout) holder.itemView;
                container.removeAllViews();
                View pageView;
                if (position == TAB_MEDIA_SERVER) {
                    pageView = viewMediaServerPage;
                } else if (position == TAB_QUEUE) {
                    pageView = viewQueuePage;
                } else {
                    pageView = viewNowPlayingPage;
                }
                if (pageView.getParent() != null) {
                    ((ViewGroup) pageView.getParent()).removeView(pageView);
                }
                pageView.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                container.addView(pageView);
            }

            @Override
            public int getItemCount() {
                return 3;
            }
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                sLastSelectedTab = position;
                updatePageIndicator(position);
            }
        });

        viewPager.setCurrentItem(initialTab, false);
        updatePageIndicator(initialTab);
    }

    /**
     * Strips redundant outer backgrounds, elevations, margins, duplicate drag handles,
     * duplicate inner headers, and duplicate inner queue sections so pages blend seamlessly.
     */
    private void flattenPage(@Nullable View pageView, boolean isNowPlaying) {
        if (pageView == null) return;
        pageView.setBackground(null);
        pageView.setElevation(0f);
        pageView.setPadding(0, 0, 0, 0);

        if (pageView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams params) {
            params.setMargins(0, 0, 0, 0);
        }

        if (pageView instanceof ViewGroup vg) {
            if (pageView == viewMediaServerPage) {
                // For Server page: hide duplicate inner header row
                if (vg.getChildCount() > 0) {
                    View firstChild = vg.getChildAt(0);
                    if (firstChild != null) firstChild.setVisibility(GONE);
                }
            }
        }
    }

    private void updatePageIndicator(int position) {
        if (getContext() == null) return;

        if (tabToggleGroup != null) {
            int targetId;
            if (position == TAB_MEDIA_SERVER) {
                targetId = R.id.tab_media_server;
            } else if (position == TAB_QUEUE) {
                targetId = R.id.tab_queue;
            } else {
                targetId = R.id.tab_now_playing;
            }
            if (tabToggleGroup.getCheckedButtonId() != targetId) {
                tabToggleGroup.check(targetId);
            }
        }

        updateTabColors(position);

        if (position == TAB_NOW_PLAYING && isPlaybackServiceBound && viewNowPlayingPage != null) {
            populateNowPlayingSheet(viewNowPlayingPage);
        } else if (position == TAB_QUEUE && isPlaybackServiceBound && viewQueuePage != null) {
            populateQueueSection(viewQueuePage);
        } else if (position == TAB_MEDIA_SERVER && viewMediaServerPage != null) {
            if (mediaServerViewModel != null && mediaServerViewModel.getServerStatus() != null) {
                updateServerUI(mediaServerViewModel.getServerStatus().getValue());
            }
        }
    }

    private void updateTabColors(int selectedIndex) {
        if (tabToggleGroup == null || getContext() == null) return;
        int gold = ContextCompat.getColor(requireContext(), R.color.colorGold);
        int muted = ContextCompat.getColor(requireContext(), R.color.colorMuted);

        com.google.android.material.button.MaterialButton tabPlayback = tabToggleGroup.findViewById(R.id.tab_now_playing);
        com.google.android.material.button.MaterialButton tabQueue = tabToggleGroup.findViewById(R.id.tab_queue);
        com.google.android.material.button.MaterialButton tabServer = tabToggleGroup.findViewById(R.id.tab_media_server);

        if (tabPlayback != null) tabPlayback.setTextColor(selectedIndex == TAB_NOW_PLAYING ? gold : muted);
        if (tabQueue != null) tabQueue.setTextColor(selectedIndex == TAB_QUEUE ? gold : muted);
        if (tabServer != null) tabServer.setTextColor(selectedIndex == TAB_MEDIA_SERVER ? gold : muted);
    }

    /** Live queue count on the Queue tab label, e.g. "Queue (12)" (per DESIGN.md §8C). */
    private void updateQueueTabLabel(int queueSize) {
        if (tabToggleGroup == null) return;
        com.google.android.material.button.MaterialButton tabQueue = tabToggleGroup.findViewById(R.id.tab_queue);
        if (tabQueue != null) {
            tabQueue.setText(queueSize > 0
                    ? String.format(Locale.US, "Queue (%d)", queueSize)
                    : "Queue");
        }
    }

    // ── Service Connection ───────────────────────────────────────────────────

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicMateServiceImpl.MusicMateServiceImplBinder binder =
                    (MusicMateServiceImpl.MusicMateServiceImplBinder) service;
            playbackService = binder.getService();
            isPlaybackServiceBound = true;

            subscribeToServiceUpdates();
            refreshUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            unsubscribeFromServiceUpdates();
            isPlaybackServiceBound = false;
            playbackService = null;
        }
    };

    private void setupNowPlayingTab() { /* Migrated to Compose */ }

    private void toggleTechSpecsFlip() { /* Migrated to Compose */ }

    private void showGestureOverlayIcon() { /* Migrated to Compose */ }

    private void animateGestureFeedback() { /* Migrated to Compose */ }

    private void setupArtworkGestures() { /* Migrated to Compose */ }

    private void applyAmbientGlow(@Nullable View cardView, @Nullable Drawable drawable) {
        if (cardView == null || !(drawable instanceof BitmapDrawable) || !isAdded()) return;
        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
        Bitmap paletteBitmap = BitmapHelper.ensureSoftwareBitmap(bitmap);
        if (paletteBitmap == null || paletteBitmap.isRecycled()) return;

        Palette.from(paletteBitmap).generate(palette -> {
            if (palette == null || !isAdded() || getContext() == null) return;
            int defaultColor = 0xFF1E1E2C;
            int dominantColor = palette.getVibrantColor(palette.getDominantColor(defaultColor));

            float[] hsv = new float[3];
            Color.colorToHSV(dominantColor, hsv);
            hsv[1] = Math.min(hsv[1], 0.55f);
            hsv[2] = Math.max(0.18f, Math.min(hsv[2], 0.32f));
            int glowColor = Color.HSVToColor(hsv);

            float radius = 16 * getResources().getDisplayMetrics().density;
            GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{ glowColor, 0xFF12121A }
            );
            bg.setCornerRadius(radius);
            cardView.setBackground(bg);
        });
    }

    private void populateNowPlayingSheet(@Nullable View view) {
        if (view == null || !isAdded()) return;

        Track track = playbackService != null ? playbackService.getNowPlayingSong() : null;
        nowPlayingState.getTrack().setValue(track);

        if (track != null) {
            nowPlayingState.getSpecsFormat().setValue(TagUtils.formatCodec(track) + " • " + formatShortResolution(track));
            long bitrate = track.getAudioBitRate();
            nowPlayingState.getSpecsBitrate().setValue(bitrate > 0 ? bitrate + " kbps" : "Lossless Audio");
            double dr = track.getDrScore() > 0 ? track.getDrScore() : track.getDynamicRange();
            nowPlayingState.getSpecsDr().setValue(dr > 0 ? "Dynamic Range: DR " + (int)dr : "Studio Master Dynamic");
            nowPlayingState.getSpecsFileSize().setValue(track.getFileSize() > 0 && getContext() != null ? android.text.format.Formatter.formatFileSize(getContext(), track.getFileSize()) : "Hi-Res Audio");
            
            long durationMs = (long) (track.getAudioDuration() * 1000);
            nowPlayingState.getDurationMs().setValue(durationMs);
            
            if (getContext() != null) {
                coil3.request.ImageRequest request = CoverartFetcher.builder(requireContext(), track)
                        .data(track)
                        .size(240, 240)
                        .target(new coil3.target.Target() {
                            @Override
                            public void onSuccess(coil3.Image result) {
                                if (result instanceof coil3.BitmapImage) {
                                    nowPlayingState.getAlbumArt().setValue(((coil3.BitmapImage) result).getBitmap());
                                }
                            }
                            @Override
                            public void onError(coil3.Image error) {
                                nowPlayingState.getAlbumArt().setValue(null);
                            }
                            @Override
                            public void onStart(coil3.Image placeholder) {
                                nowPlayingState.getAlbumArt().setValue(null);
                            }
                        })
                        .build();
                coil3.SingletonImageLoader.get(requireContext()).enqueue(request);
            }
        } else {
            nowPlayingState.getAlbumArt().setValue(null);
        }

        if (playbackService != null) {
            // nowPlayingState.isShuffle().setValue(playbackService.isShuffleModeEnabled()); // Not directly available
            // nowPlayingState.getRepeatMode().setValue(playbackService.getRepeatMode()); // Not directly available
        }

        if (getActivity() instanceof apincer.android.mmate.ui.MainActivity) {
            PlaybackState state = ((apincer.android.mmate.ui.MainActivity) getActivity()).getLastPlaybackState();
            if (state != null) {
                nowPlayingState.getPlaybackState().setValue(state);
                nowPlayingState.getProgressMs().setValue(state.currentPositionSecond * 1000L);
            }
        }
    }

    public void refreshUI() {
        if (!isAdded()) return;
        if (viewNowPlayingPage != null) {
            populateNowPlayingSheet(viewNowPlayingPage);
        }
        if (viewQueuePage != null) {
            populateQueueSection(viewQueuePage);
        }
        if (viewMediaServerPage != null && mediaServerViewModel != null && mediaServerViewModel.getServerStatus() != null) {
            updateServerUI(mediaServerViewModel.getServerStatus().getValue());
        }
    }

    // ── Permanent Bottom Queue Section ────────────────────────────────────────

    private void populateQueueSection(@NonNull View root) {
        QueueManager qm = playbackService != null ? playbackService.getQueueManager() : null;
        if (qm != null) {
            qm.loadPlayingQueue();
        }
        List<Track> queue = (qm != null) ? new ArrayList<>(qm.getSongs()) : new ArrayList<>();
        Track track = playbackService != null ? playbackService.getNowPlayingSong() : null;
        String currentKey = (track != null) ? track.getUniqueKey() : null;
        
        queueState.updateTracks(queue);
        queueState.setCurrentPlayingKey(currentKey);
        updateQueueTabLabel(queue.size());
        
        // Duration calculation
        double totalDur = 0;
        for (Track t : queue) {
            totalDur += t.getAudioDuration();
        }
        if (totalDur > 0) {
            int mins = (int) totalDur / 60;
            int secs = (int) totalDur % 60;
            queueState.setTotalDurationText(String.format(java.util.Locale.US, "%d:%02d", mins, secs));
        } else {
            queueState.setTotalDurationText("");
        }
    }

    

    private void updatePlaybackProgress(@Nullable View view, @Nullable PlaybackState state) {
        if (!isAdded() || state == null) return;
        nowPlayingState.getPlaybackState().setValue(state);
        nowPlayingState.getProgressMs().setValue(state.currentPositionSecond * 1000L);
    }

    private void showPlayerPicker(View anchorView) {
        if (getActivity() instanceof apincer.android.mmate.ui.MainActivity) {
            ((apincer.android.mmate.ui.MainActivity) getActivity()).showPlayerPickerPopup(anchorView);
        }
    }

    // ── Page 1: Audio Signal Path ────────────────────────────────────────────

    private String formatShortResolution(Track track) {
        if (track == null) return "";
        int bitDepth = track.getAudioBitsDepth();
        long sampleRate = track.getAudioSampleRate();
        long bitRate = track.getAudioBitRate();

        if (sampleRate > 0) {
            String rateStr = (sampleRate % 1000 == 0)
                    ? (sampleRate / 1000) + "k"
                    : String.format(Locale.US, "%.1fk", sampleRate / 1000.0);
            if (bitDepth > 0) {
                return bitDepth + "/" + rateStr;
            }
            return rateStr;
        } else if (bitRate > 0) {
            return (bitRate / 1000) + "k";
        }
        return "";
    }

    private void populateSignalPathWidget(@NonNull View view, @Nullable Track track) {
        // Migrated to compose, this could be handled by state in the future.
    }

    private void addSignalPathSteps(LinearLayout signalPathContainer) {
        if (signalPathContainer == null || playbackService == null || getContext() == null) return;
        signalPathContainer.removeAllViews();

        Track song = playbackService.getNowPlayingSong();
        if (song == null) {
            TextView emptyText = new TextView(getContext());
            emptyText.setText("No active audio route telemetry available.");
            emptyText.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorMuted));
            emptyText.setTextSize(12f);
            signalPathContainer.addView(emptyText);
            return;
        }

        boolean isLossyFile = TagUtils.isLossy(song);
        boolean isHiRes = TagUtils.isHiRes(song);

        // --- Step 1: Source ---
        int sourceShape = R.drawable.shape_node_source;
        int sourceIcon = R.drawable.ic_baseline_audio_file_24;
        String sourceTitle = isLossyFile ? "Source: Lossy Compressed Audio" : (isHiRes ? "Source: Hi-Res Lossless Audio" : "Source: Lossless CD Quality");
        String songTitleText = song.getTitle();
        if (!StringUtils.isEmpty(song.getArtist())) {
            songTitleText = songTitleText + " — " + song.getArtist();
        }

        String codec = TagUtils.formatCodec(song);
        if (codec.isEmpty()) {
            codec = song.getAudioEncoding().toUpperCase();
        }

        String sourceBadge = isHiRes ? "HI-RES" : (isLossyFile ? "LOSSY" : "LOSSLESS");
        String sourceText = songTitleText + "\n" +
                "Format: " + codec + SYMBOL_ENC_SEP + TagUtils.formatResolution(song.getAudioBitsDepth(), song.getAudioSampleRate(), song.getMqaSampleRate()) +
                " (" + (song.getAudioBitRate() > 0 ? (song.getAudioBitRate() / 1000) + " kbps" : "VBR") + ")\n" +
                "Container: " + (song.getFileType() != null ? song.getFileType().toUpperCase() : "AUDIO");

        addSignalPathStep(signalPathContainer, sourceShape, sourceIcon, sourceTitle, sourceBadge, sourceText, true);

        // --- Step 2: Transport Engine ---
        PlaybackTarget playbackTarget = playbackService.getPlayer();
        if (playbackTarget != null) {
            if (playbackTarget.isStreaming() && !(playbackTarget instanceof DMRPlayer) && playbackTarget.getDescription() != null) {
                String incomingIp = NetworkUtils.extractIpAddress(playbackTarget.getDescription());
                List<PlaybackTarget> targets = playbackService.getPlaybackTargets();
                if (targets != null && !incomingIp.isEmpty()) {
                    for (PlaybackTarget target : targets) {
                        if (target instanceof DMRPlayer dmr && dmr.getDescription() != null) {
                            String devIp = NetworkUtils.extractIpAddress(dmr.getDescription());
                            if (incomingIp.equals(devIp)) {
                                playbackTarget = dmr;
                                break;
                            }
                        }
                    }
                }
            }

            boolean isStreaming = playbackTarget.isStreaming();
            int transportShape = R.drawable.shape_node_transport;
            int transportIcon = R.drawable.ic_baseline_audio_path_24;
            String transportTitle = "MusicMate Transport Engine";
            String transportBadge = isStreaming ? "NETWORK STREAMER" : "LOCAL TRANSPORT";
            String serverDetails = ApplicationUtils.getFriendlyDeviceName() + " • v" + ApplicationUtils.getVersionNumber(getContext()) + "\n" +
                    (isStreaming ? "Streaming via HTTP Media Server Pipeline" : "Direct Local Storage IO Read");

            addSignalPathStep(signalPathContainer, transportShape, transportIcon, transportTitle, transportBadge, serverDetails, true);

            // --- Step 3: Target Audio Output ---
            int targetShape;
            String targetTitle;
            String targetBadge;
            String targetDetails;

            if (playbackTarget instanceof ExternalAndroidPlayer player) {
                if ("local".equalsIgnoreCase(player.getTargetId())) {
                    AudioOutputHelper.Device device = AudioOutputHelper.getOutputDevice(getContext(), song);
                    boolean isBitPerfect = device.isBitPerfect();
                    boolean isBluetooth = device.isBluetooth();
                    targetShape = isBitPerfect ? R.drawable.shape_node_target_bitperfect : R.drawable.shape_node_target;
                    int targetIcon = (device.getResId() != 0) ? device.getResId() : (isBitPerfect ? R.drawable.ic_baseline_usb_24 : (isBluetooth ? R.drawable.ic_round_bluetooth_audio_24 : R.drawable.ic_baseline_volume_up_24));
                    targetTitle = "Output Device: " + device.getName();
                    targetBadge = isBitPerfect ? "BIT-PERFECT" : (isBluetooth ? "BLUETOOTH A2DP" : "SYSTEM OUTPUT");

                    StringBuilder devBuf = new StringBuilder();
                    devBuf.append("Type: ").append(device.getDescription());
                    if (!StringUtils.isEmpty(device.getCodec()) && !"PCM".equalsIgnoreCase(device.getCodec()) && !"-".equals(device.getCodec())) {
                        devBuf.append(" • ").append(device.getCodec());
                    }
                    devBuf.append("\nSpecs: ").append(TagUtils.formatResolution(device.getBitPerSampling(), device.getSamplingRate(), -1));
                    if (isBitPerfect) {
                        devBuf.append("\nStatus: Bit-Perfect Direct USB Hardware Passthrough (1:1)");
                    } else if (isBluetooth) {
                        devBuf.append("\nStatus: Active Bluetooth A2DP Wireless Stream");
                    } else {
                        devBuf.append("\nStatus: ").append(device.getFriendyDescription());
                    }
                    targetDetails = devBuf.toString();

                    View stepView = addSignalPathStep(signalPathContainer, targetShape, targetIcon, targetTitle, targetBadge, targetDetails, false);
                    if (isBluetooth && stepView != null) {
                        View card = stepView.findViewById(R.id.step_card_container);
                        if (card != null) {
                            card.setOnClickListener(v -> AudioOutputHelper.openSystemAudioOrBluetooth(getContext()));
                        }
                    }
                } else {
                    targetShape = R.drawable.shape_node_target;
                    targetTitle = "External Player: " + player.getDisplayName();
                    targetBadge = "EXTERNAL APP";
                    Drawable targetDrawable = ExternalAndroidPlayer.Factory.getAppIcon(getContext(), player.getTargetId());
                    String pkgDesc = ExternalAndroidPlayer.Factory.getAppDescription(getContext(), player.getTargetId());
                    targetDetails = "Package: " + player.getTargetId() + (pkgDesc != null ? "\n" + pkgDesc : "");
                    if (targetDrawable != null) {
                        addSignalPathStep(signalPathContainer, targetShape, targetDrawable, targetTitle, targetBadge, targetDetails, false, true);
                    } else {
                        addSignalPathStep(signalPathContainer, targetShape, R.drawable.ic_round_speaker_24, targetTitle, targetBadge, targetDetails, false);
                    }
                }
            } else {
                targetShape = R.drawable.shape_node_target;
                int targetIcon = R.drawable.ic_dlna;
                targetTitle = "Network Target: " + playbackTarget.getDisplayName();
                targetBadge = "DLNA RENDERER";
                String playerLabel = PlayerNameUtils.getTwoLinePlayerLabel(playbackTarget);
                targetDetails = playerLabel + "\nProtocol: UPnP AVTransport / DLNA Render";
                addSignalPathStep(signalPathContainer, targetShape, targetIcon, targetTitle, targetBadge, targetDetails, false);
            }
        }
    }

    private View addSignalPathStep(LinearLayout container, int shapeResId, int iconResId, String title, String badgeText, String description, boolean hasNext) {
        return addSignalPathStep(container, shapeResId, iconResId, null, title, badgeText, description, hasNext, false);
    }

    private View addSignalPathStep(LinearLayout container, int shapeResId, Drawable iconDrawable, String title, String badgeText, String description, boolean hasNext, boolean isExternalApp) {
        return addSignalPathStep(container, shapeResId, 0, iconDrawable, title, badgeText, description, hasNext, isExternalApp);
    }

    private View addSignalPathStep(LinearLayout container, int shapeResId, int iconResId, Drawable iconDrawable, String title, String badgeText, String description, boolean hasNext, boolean isExternalApp) {
        if (getContext() == null) return null;
        View stepView = LayoutInflater.from(getContext()).inflate(R.layout.signal_path_step, container, false);
        View cardContainer = stepView.findViewById(R.id.step_card_container);
        ImageView iconView = stepView.findViewById(R.id.step_icon);
        MaterialTextView titleTextView = stepView.findViewById(R.id.step_title);
        TextView badgeTextView = stepView.findViewById(R.id.step_badge);
        MaterialTextView descriptionTextView = stepView.findViewById(R.id.step_description);
        View lineView = stepView.findViewById(R.id.vertical_line);

        if (cardContainer != null) {
            cardContainer.setBackgroundResource(shapeResId);
        }
        if (iconView != null) {
            if (iconDrawable != null) {
                iconView.setImageDrawable(iconDrawable);
            } else if (iconResId != 0) {
                iconView.setImageResource(iconResId);
            }
            if (isExternalApp) {
                iconView.setImageTintList(null);
            }
        }
        if (titleTextView != null) titleTextView.setText(title);

        if (badgeTextView != null) {
            if (StringUtils.isEmpty(badgeText)) {
                badgeTextView.setVisibility(GONE);
            } else {
                badgeTextView.setVisibility(VISIBLE);
                badgeTextView.setText(badgeText);
            }
        }

        if (descriptionTextView != null) descriptionTextView.setText(description);

        if (lineView != null) {
            lineView.setVisibility(hasNext ? VISIBLE : View.INVISIBLE);
        }

        container.addView(stepView);
        return stepView;
    }

    // ── Page 2: Media Server Management ──────────────────────────────────────

    private void setupMediaServerTab(View view) { /* Handled by Compose */ }

    /** Runtime web engine switcher (SonicNIO / CoreHTTP / Netty) — persists the
     *  preference and restarts the media server when a new engine is selected. */
    private void setupEngineSwitcher(View view) { /* Handled by Compose */ }

    private String getEngineDescription(String engine) {
        switch (engine) {
            case "nio":
                return "⚡ Ultra-low latency • Minimal battery & RAM footprint";
            case "netty":
                return "🚀 High concurrency • Zero-copy file streaming";
            default:
                return "🛡️ Apache Async Reactor • Maximum network resilience";
        }
    }

    private void updateEngineDescription(String engine) {
        if (tvEngineDescription == null) return;
        switch (engine) {
            case "nio":
                tvEngineDescription.setText("⚡ Ultra-low latency • Minimal battery & RAM footprint");
                break;
            case "netty":
                tvEngineDescription.setText("🚀 High-concurrency streaming • Zero-copy DMA throughput");
                break;
            default:
                tvEngineDescription.setText("🛡️ Apache Async Reactor • Maximum network resilience");
                break;
        }
    }

    private void observeServerStatus() {
        mediaServerViewModel.getServerStatus().observe(getViewLifecycleOwner(), this::updateServerUI);
    }

    private void updateServerUI(MediaServerHub.ServerStatus status) {
        if (getContext() == null) return;
        boolean isNetworkAvailable = NetworkUtils.isWifiConnected(requireContext()) || NetworkUtils.isHotspotActive(requireContext());
        mediaServerState.setNetworkAvailable(isNetworkAvailable);
        
        switch (status) {
            case RUNNING:
                mediaServerState.setServerRunning(true);
                mediaServerState.setBroadcastInfo("DLNA 1.5 / UPnP AV • Active on Port 9000");

                String ssid = ApplicationUtils.getWifiSSID(getContext());
                if (!StringUtils.isEmpty(ssid)) {
                    mediaServerState.setServerStatusText(SERVER_STATUS_ONLINE_PREFIX + " (" + ssid + ")");
                } else if (NetworkUtils.isHotspotActive(requireContext())) {
                    mediaServerState.setServerStatusText(SERVER_STATUS_ONLINE_PREFIX + " (Hotspot)");
                } else {
                    mediaServerState.setServerStatusText(SERVER_STATUS_ONLINE_PREFIX);
                }

                String serverLocation = mediaServerViewModel.getServerLocationUrl();
                mediaServerState.setServerUrl(serverLocation);
                mediaServerState.setQrCodeBitmap(generateQRCode(serverLocation));
                break;

            case STOPPED:
            case ERROR:
                mediaServerState.setServerRunning(false);
                mediaServerState.setBroadcastInfo("");
                mediaServerState.setQrCodeBitmap(null);
                mediaServerState.setServerStatusText(SERVER_STATUS_OFFLINE);
                mediaServerState.setServerUrl(isNetworkAvailable ? getString(R.string.server_url_not_available) : getString(R.string.notification_server_not_running));
                break;

            case STARTING:
                mediaServerState.setServerRunning(false);
                mediaServerState.setBroadcastInfo("");
                mediaServerState.setQrCodeBitmap(null);
                mediaServerState.setServerUrl("");
                mediaServerState.setServerStatusText(SERVER_STATUS_OFFLINE);
                break;
        }
    }

    private Bitmap generateQRCode(String text) {
        if (text == null || text.isEmpty()) return null;
        QRCodeWriter writer = new QRCodeWriter();
        try {
            BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bmp;
        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private String formatTime(long ms) {
        long sec = ms / 1000;
        long min = sec / 60;
        sec = sec % 60;
        return String.format(Locale.US, "%02d:%02d", min, sec);
    }

    // ── Queue Adapter ─────────────────────────────────────────────────────────

    interface OnTrackClickListener {
        void onTrackClick(Track track);
    }

    
}

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
        viewNowPlayingPage = LayoutInflater.from(getContext()).inflate(R.layout.sheet_now_playing_queue, null);
        viewQueuePage = LayoutInflater.from(getContext()).inflate(R.layout.view_audio_hub_queue_page, null);
        viewMediaServerPage = LayoutInflater.from(getContext()).inflate(R.layout.view_action_server_management_bottom_sheet, null);

        flattenPage(viewNowPlayingPage, true);
        flattenPage(viewQueuePage, false);
        flattenPage(viewMediaServerPage, false);

        setupNowPlayingTab(viewNowPlayingPage);
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
            if (isNowPlaying) {
                // For Now Playing page: hide drag handle, inner header, and inner queue section
                for (int i = 0; i < vg.getChildCount(); i++) {
                    View child = vg.getChildAt(i);
                    if (child.getId() != R.id.sheet_now_playing_card) {
                        child.setVisibility(GONE);
                    }
                }
            } else if (pageView == viewMediaServerPage) {
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

    private void setupNowPlayingTab(View view) {
        View cardView = view.findViewById(R.id.sheet_now_playing_card);
        if (cardView != null) {
            cardView.setOnClickListener(v -> {
                if (playbackService != null && playbackService.getNowPlayingSong() != null) {
                    dismiss();
                    if (getActivity() instanceof apincer.android.mmate.ui.MainActivity) {
                        Track currentTrack = playbackService.getNowPlayingSong();
                        if (currentTrack != null) {
                            ((apincer.android.mmate.ui.MainActivity) getActivity()).scrollToSong(currentTrack);
                        }
                    }
                }
            });
        }

        View btnFlipSpecs = view.findViewById(R.id.sheet_btn_flip_specs);
        if (btnFlipSpecs != null) {
            btnFlipSpecs.setOnClickListener(v -> toggleTechSpecsFlip(view));
        }

        View techSpecsOverlay = view.findViewById(R.id.sheet_tech_specs_overlay);
        if (techSpecsOverlay != null) {
            techSpecsOverlay.setOnClickListener(v -> toggleTechSpecsFlip(view));
        }

        setupArtworkGestures(view);
    }

    private void toggleTechSpecsFlip(@Nullable View view) {
        if (view == null) return;
        View albumArt = view.findViewById(R.id.sheet_album_art);
        View techSpecsOverlay = view.findViewById(R.id.sheet_tech_specs_overlay);
        if (albumArt == null || techSpecsOverlay == null) return;

        boolean showSpecs = techSpecsOverlay.getVisibility() != VISIBLE;
        View outgoing = showSpecs ? albumArt : techSpecsOverlay;
        View incoming = showSpecs ? techSpecsOverlay : albumArt;

        outgoing.animate()
                .rotationY(90f)
                .setDuration(140)
                .withEndAction(() -> {
                    outgoing.setVisibility(GONE);
                    outgoing.setRotationY(0f);
                    incoming.setVisibility(VISIBLE);
                    incoming.setRotationY(-90f);
                    incoming.animate()
                            .rotationY(0f)
                            .setDuration(140)
                            .start();
                })
                .start();
    }

    private void showGestureOverlayIcon(View parentView, int iconResId) {
        if (parentView == null) return;
        ImageView feedbackIcon = parentView.findViewById(R.id.sheet_gesture_feedback_icon);
        if (feedbackIcon == null) return;

        feedbackIcon.animate().cancel();
        feedbackIcon.setImageResource(iconResId);
        feedbackIcon.setVisibility(VISIBLE);
        feedbackIcon.setAlpha(0.0f);
        feedbackIcon.setScaleX(0.7f);
        feedbackIcon.setScaleY(0.7f);

        feedbackIcon.animate()
                .alpha(1.0f)
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(160)
                .withEndAction(() -> feedbackIcon.animate()
                        .alpha(0.0f)
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(200)
                        .withEndAction(() -> feedbackIcon.setVisibility(GONE))
                        .start())
                .start();
    }

    private void animateGestureFeedback(View albumArt, float translationX) {
        if (albumArt == null) return;
        albumArt.animate().cancel();
        albumArt.setTranslationX(translationX);
        albumArt.setAlpha(0.6f);
        albumArt.animate()
                .translationX(0f)
                .alpha(1.0f)
                .setDuration(220)
                .start();
    }

    private void setupArtworkGestures(View view) {
        if (view == null || getContext() == null) return;
        View albumArt = view.findViewById(R.id.sheet_album_art);
        if (albumArt == null) return;

        GestureDetector gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                if (e1 != null && e2 != null) {
                    float diffX = e2.getX() - e1.getX();
                    float diffY = e2.getY() - e1.getY();
                    if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 80 && Math.abs(velocityX) > 150) {
                        if (diffX < 0) {
                            if (isPlaybackServiceBound && playbackService != null) {
                                animateGestureFeedback(albumArt, -35f);
                                showGestureOverlayIcon(viewNowPlayingPage, R.drawable.ic_baseline_skip_next_48);
                                playbackService.skipToNextInQueue();
                                populateNowPlayingSheet(viewNowPlayingPage);
                            }
                        } else {
                            if (isPlaybackServiceBound && playbackService != null) {
                                animateGestureFeedback(albumArt, 35f);
                                showGestureOverlayIcon(viewNowPlayingPage, R.drawable.ic_baseline_skip_previous_48);
                                playbackService.skipToPrevious();
                                populateNowPlayingSheet(viewNowPlayingPage);
                            }
                        }
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                if (isPlaybackServiceBound && playbackService != null) {
                    albumArt.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100)
                            .withEndAction(() -> albumArt.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start())
                            .start();
                    boolean isPlaying = false;
                    if (getActivity() instanceof apincer.android.mmate.ui.MainActivity) {
                        PlaybackState state =
                                ((apincer.android.mmate.ui.MainActivity) getActivity()).getLastPlaybackState();
                        isPlaying = state != null && state.currentState == PlaybackState.State.PLAYING;
                    }
                    if (isPlaying) {
                        showGestureOverlayIcon(viewNowPlayingPage, R.drawable.ic_baseline_pause_48);
                        playbackService.pausePlayer();
                    } else {
                        showGestureOverlayIcon(viewNowPlayingPage, R.drawable.ic_baseline_play_arrow_48);
                        Track current = playbackService.getNowPlayingSong();
                        if (current != null) {
                            playbackService.playSong(current);
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
                    populateNowPlayingSheet(viewNowPlayingPage);
                    return true;
                }
                return false;
            }
        });

        albumArt.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            v.performClick();
            return true;
        });
    }

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

        ShapeableImageView albumArt = view.findViewById(R.id.sheet_album_art);
        TextView titleView = view.findViewById(R.id.sheet_track_title);
        TextView artistView = view.findViewById(R.id.sheet_artist);

        if (track != null) {
            titleView.setText(track.getTitle());
            artistView.setText(track.getArtist());

            if (albumArt != null && getContext() != null) {
                ImageRequest request = CoverartFetcher.builder(requireContext(), track)
                        .data(track)
                        .size(240, 240)
                        .target(new coil3.target.ImageViewTarget(albumArt))
                        .build();
                SingletonImageLoader.get(requireContext()).enqueue(request);

                albumArt.postDelayed(() -> {
                    View card = view.findViewById(R.id.sheet_now_playing_card);
                    if (card != null && albumArt.getDrawable() != null) {
                        applyAmbientGlow(card, albumArt.getDrawable());
                    }
                }, 150);
            }

            populateSignalPathWidget(view, track);

            View techSpecsOverlay = view.findViewById(R.id.sheet_tech_specs_overlay);
            if (techSpecsOverlay != null) {
                TextView specsFormat = techSpecsOverlay.findViewById(R.id.sheet_specs_format);
                TextView specsBitrate = techSpecsOverlay.findViewById(R.id.sheet_specs_bitrate);
                TextView specsDr = techSpecsOverlay.findViewById(R.id.sheet_specs_dr);
                TextView specsFileSize = techSpecsOverlay.findViewById(R.id.sheet_specs_file_size);

                String codec = TagUtils.formatCodec(track);
                String res = formatShortResolution(track);
                if (specsFormat != null) specsFormat.setText(!res.isEmpty() ? codec + " • " + res : codec);

                if (specsBitrate != null) {
                    long bitrate = track.getAudioBitRate();
                    if (bitrate > 0) {
                        specsBitrate.setText(String.format(Locale.US, "%d kbps", bitrate));
                    } else {
                        specsBitrate.setText(track.getAudioChannels() != null ? track.getAudioChannels() + " Ch Stereo" : "Lossless Audio");
                    }
                }

                if (specsDr != null) {
                    double dr = track.getDrScore() > 0 ? track.getDrScore() : track.getDynamicRange();
                    if (dr > 0) {
                        specsDr.setText(String.format(Locale.US, "Dynamic Range: DR %.0f", dr));
                    } else {
                        specsDr.setText("Studio Master Dynamic");
                    }
                }

                if (specsFileSize != null) {
                    long size = track.getFileSize();
                    if (size > 0 && getContext() != null) {
                        specsFileSize.setText(android.text.format.Formatter.formatFileSize(getContext(), size));
                    } else {
                        specsFileSize.setText("Hi-Res Audio");
                    }
                }
            }
        } else {
            titleView.setText("Music Mate Ready");
            artistView.setText("Select a song or player target");
            populateSignalPathWidget(view, null);
            if (albumArt != null) albumArt.setImageResource(R.drawable.ic_now_playing_idle);
        }

        SeekBar seekBar = view.findViewById(R.id.sheet_seekbar);
        TextView currentTimeView = view.findViewById(R.id.sheet_current_time);
        TextView totalTimeView = view.findViewById(R.id.sheet_total_time);

        if (track != null && track.getAudioDuration() > 0) {
            long durationMs = (long) (track.getAudioDuration() * 1000);
            if (totalTimeView != null) {
                totalTimeView.setText(formatTime(durationMs));
            }

            if (getActivity() instanceof apincer.android.mmate.ui.MainActivity) {
                PlaybackState state = ((apincer.android.mmate.ui.MainActivity) getActivity()).getLastPlaybackState();
                updatePlaybackProgress(view, state);
            }

            if (seekBar != null) {
                seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                        if (fromUser && durationMs > 0) {
                            long seekMs = (progress * durationMs) / 1000;
                            if (currentTimeView != null) {
                                currentTimeView.setText(formatTime(seekMs));
                            }
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar sb) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar sb) {
                        if (isPlaybackServiceBound && playbackService != null && durationMs > 0) {
                            long seekMs = (sb.getProgress() * durationMs) / 1000;
                            playbackService.seekTo(seekMs);
                        }
                    }
                });
            }
        } else {
            if (currentTimeView != null) currentTimeView.setText("00:00");
            if (totalTimeView != null) totalTimeView.setText("00:00");
            if (seekBar != null) seekBar.setProgress(0);
        }

        PlaybackTarget target = playbackService != null ? playbackService.getPlayer() : null;
        if (btnCastHeader != null && getContext() != null) {
            boolean isRemote = target != null && target.isStreaming();
            int tintColor = isRemote
                    ? ContextCompat.getColor(requireContext(), R.color.colorGold)
                    : ContextCompat.getColor(requireContext(), R.color.colorOnSurface);
            btnCastHeader.setImageTintList(android.content.res.ColorStateList.valueOf(tintColor));
        }

        ImageView sheetBtnPrevious = view.findViewById(R.id.sheet_btn_previous);
        ImageView sheetBtnPlayPause = view.findViewById(R.id.sheet_btn_play_pause);
        ImageView sheetBtnNext = view.findViewById(R.id.sheet_btn_next);

        if (sheetBtnPrevious != null) {
            sheetBtnPrevious.setOnClickListener(v -> {
                if (isPlaybackServiceBound && playbackService != null) {
                    playbackService.skipToPrevious();
                    view.postDelayed(() -> populateNowPlayingSheet(view), 200);
                }
            });
        }

        if (sheetBtnPlayPause != null) {
            boolean isPlaying = false;
            if (getActivity() instanceof apincer.android.mmate.ui.MainActivity) {
                PlaybackState state =
                        ((apincer.android.mmate.ui.MainActivity) getActivity()).getLastPlaybackState();
                isPlaying = state != null && state.currentState == PlaybackState.State.PLAYING;
            }
            final boolean currentlyPlaying = isPlaying;
            sheetBtnPlayPause.setImageResource(currentlyPlaying ? R.drawable.ic_pause_rounded : R.drawable.ic_play_rounded);
            sheetBtnPlayPause.setOnClickListener(v -> {
                if (isPlaybackServiceBound && playbackService != null) {
                    if (currentlyPlaying) {
                        playbackService.pausePlayer();
                    } else {
                        Track current = playbackService.getNowPlayingSong();
                        if (current != null) {
                            playbackService.playSong(current);
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
                    view.postDelayed(() -> populateNowPlayingSheet(view), 200);
                }
            });
        }

        if (sheetBtnNext != null) {
            sheetBtnNext.setOnClickListener(v -> {
                if (isPlaybackServiceBound && playbackService != null) {
                    playbackService.skipToNextInQueue();
                    view.postDelayed(() -> populateNowPlayingSheet(view), 200);
                }
            });
        }

        // Shuffle toggle inside Now Playing transport row
        ImageView btnShuffle = view.findViewById(R.id.btn_toggle_shuffle);
        if (btnShuffle != null && getContext() != null) {
            QueueManager qm = playbackService != null ? playbackService.getQueueManager() : null;
            boolean isShuffle = qm != null && qm.isShuffle();
            androidx.core.widget.ImageViewCompat.setImageTintList(btnShuffle,
                    ContextCompat.getColorStateList(requireContext(), isShuffle ? R.color.colorGold : R.color.colorMuted));
            btnShuffle.setOnClickListener(v -> {
                if (qm != null && playbackService != null) {
                    playbackService.setShuffleMode(!isShuffle);
                    Toast.makeText(getContext(), !isShuffle ? "Shuffle ON" : "Shuffle OFF", Toast.LENGTH_SHORT).show();
                    populateNowPlayingSheet(view);
                }
            });
        }

        // Repeat toggle inside Now Playing transport row
        ImageView btnRepeat = view.findViewById(R.id.btn_toggle_repeat);
        if (btnRepeat != null && getContext() != null) {
            QueueManager qm = playbackService != null ? playbackService.getQueueManager() : null;
            QueueManager.RepeatMode mode = qm != null ? qm.getRepeatMode() : QueueManager.RepeatMode.OFF;
            if (mode == QueueManager.RepeatMode.ONE) {
                btnRepeat.setImageResource(R.drawable.ic_baseline_repeat_one_24);
                androidx.core.widget.ImageViewCompat.setImageTintList(btnRepeat,
                        ContextCompat.getColorStateList(requireContext(), R.color.colorGold));
            } else if (mode == QueueManager.RepeatMode.ALL) {
                btnRepeat.setImageResource(R.drawable.ic_baseline_repeat_24);
                androidx.core.widget.ImageViewCompat.setImageTintList(btnRepeat,
                        ContextCompat.getColorStateList(requireContext(), R.color.colorGold));
            } else {
                btnRepeat.setImageResource(R.drawable.ic_baseline_repeat_24);
                androidx.core.widget.ImageViewCompat.setImageTintList(btnRepeat,
                        ContextCompat.getColorStateList(requireContext(), R.color.colorMuted));
            }

            btnRepeat.setOnClickListener(v -> {
                if (qm != null && playbackService != null) {
                    QueueManager.RepeatMode nextMode;
                    if (mode == QueueManager.RepeatMode.OFF) nextMode = QueueManager.RepeatMode.ALL;
                    else if (mode == QueueManager.RepeatMode.ALL) nextMode = QueueManager.RepeatMode.ONE;
                    else nextMode = QueueManager.RepeatMode.OFF;

                    playbackService.setRepeatMode(nextMode.name());
                    Toast.makeText(getContext(), "Repeat: " + nextMode.name(), Toast.LENGTH_SHORT).show();
                    populateNowPlayingSheet(view);
                }
            });
        }

        // Also update queue list when now playing updates
        if (viewQueuePage != null) {
            populateQueueSection(viewQueuePage);
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
        updateQueueTabLabel(queue.size());
        Track track = playbackService != null ? playbackService.getNowPlayingSong() : null;
        String currentKey = (track != null) ? track.getUniqueKey() : null;
        int playingPosition = -1;
        if (currentKey != null) {
            for (int i = 0; i < queue.size(); i++) {
                if (currentKey.equals(queue.get(i).getUniqueKey())) {
                    playingPosition = i;
                    break;
                }
            }
        }

        View btnJumpToNowPlaying = root.findViewById(R.id.btn_jump_to_now_playing);
        RecyclerView recycler = root.findViewById(R.id.sheet_queue_list);
        if (btnJumpToNowPlaying != null) {
            btnJumpToNowPlaying.setOnClickListener(v -> {
                // Re-compute playing position at click time to avoid stale captures
                QueueManager liveQm = playbackService != null ? playbackService.getQueueManager() : null;
                Track liveTrack = playbackService != null ? playbackService.getNowPlayingSong() : null;
                String liveKey = liveTrack != null ? liveTrack.getUniqueKey() : null;
                List<Track> liveQueue = liveQm != null ? liveQm.getSongs() : null;
                int livePos = -1;
                if (liveKey != null && liveQueue != null) {
                    for (int i = 0; i < liveQueue.size(); i++) {
                        if (liveKey.equals(liveQueue.get(i).getUniqueKey())) {
                            livePos = i;
                            break;
                        }
                    }
                }
                if (livePos >= 0 && recycler != null) {
                    if (recycler.getLayoutManager() instanceof LinearLayoutManager lm) {
                        lm.scrollToPositionWithOffset(livePos, 0);
                    } else {
                        recycler.smoothScrollToPosition(livePos);
                    }
                    Toast.makeText(getContext(), "Jumped to playing track", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "No active playing track", Toast.LENGTH_SHORT).show();
                }
            });
        }

        View btnClearQueue = root.findViewById(R.id.btn_clear_queue);
        if (btnClearQueue != null) {
            btnClearQueue.setOnClickListener(v -> {
                if (qm != null) {
                    qm.emptyPlayingQueue();
                    Toast.makeText(getContext(), "Queue cleared", Toast.LENGTH_SHORT).show();
                    populateQueueSection(root);
                }
            });
        }

        TextView emptyMsg = root.findViewById(R.id.sheet_empty_queue_msg);
        //TextView queueLabel = root.findViewById(R.id.sheet_queue_label);
        if (recycler != null) {
            updateQueueHeader(root, queue);
            if (queue.isEmpty()) {
                if (emptyMsg != null) emptyMsg.setVisibility(VISIBLE);
                recycler.setVisibility(GONE);
            } else {
                if (emptyMsg != null) emptyMsg.setVisibility(GONE);
                recycler.setVisibility(VISIBLE);

                QueueAdapter existingAdapter = null;
                if (recycler.getAdapter() instanceof QueueAdapter) {
                    existingAdapter = (QueueAdapter) recycler.getAdapter();
                }

                if (existingAdapter != null) {
                    existingAdapter.updateData(queue, currentKey);
                } else {
                    QueueAdapter adapter = new QueueAdapter(queue, currentKey, selectedTrack -> {
                        if (isPlaybackServiceBound && playbackService != null) {
                            playbackService.playSong(selectedTrack);
                            if (viewNowPlayingPage != null) populateNowPlayingSheet(viewNowPlayingPage);
                            populateQueueSection(root);
                        }
                    });
                    LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
                    recycler.setLayoutManager(layoutManager);
                    recycler.setNestedScrollingEnabled(true);
                    recycler.setAdapter(adapter);

                    ItemTouchHelper touchHelper = new ItemTouchHelper(
                            new ItemTouchHelper.SimpleCallback(
                                    ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                                    ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT
                            ) {
                                @Override
                                public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                                    int fromPos = viewHolder.getBindingAdapterPosition();
                                    int toPos = target.getBindingAdapterPosition();
                                    if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION && fromPos != toPos) {
                                        if (qm != null) {
                                            qm.moveTrack(fromPos, toPos);
                                        }
                                        QueueAdapter currentAdapter = (QueueAdapter) recyclerView.getAdapter();
                                        if (currentAdapter != null) {
                                            Track movedItem = currentAdapter.queue.remove(fromPos);
                                            currentAdapter.queue.add(toPos, movedItem);
                                            currentAdapter.notifyItemMoved(fromPos, toPos);
                                            int start = Math.min(fromPos, toPos);
                                            int count = Math.abs(fromPos - toPos) + 1;
                                            currentAdapter.notifyItemRangeChanged(start, count);
                                        }
                                        return true;
                                    }
                                    return false;
                                }

                                @Override
                                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                                    int pos = viewHolder.getBindingAdapterPosition();
                                    QueueAdapter currentAdapter = (QueueAdapter) recycler.getAdapter();
                                    if (currentAdapter != null && pos != RecyclerView.NO_POSITION && pos < currentAdapter.queue.size()) {
                                        Track removedTrack = currentAdapter.queue.get(pos);
                                        if (qm != null) {
                                            qm.removeTrack(pos);
                                        }
                                        currentAdapter.queue.remove(pos);
                                        currentAdapter.notifyItemRemoved(pos);
                                        
                                        // Update headers only
                                        if (qm != null) {
                                            updateQueueHeader(root, qm.getSongs());
                                        }

                                        if (getView() != null) {
                                            Snackbar.make(getView(), "Removed " + removedTrack.getTitle(), Snackbar.LENGTH_SHORT)
                                                    .setAction("UNDO", v -> {
                                                        if (qm != null) {
                                                            qm.addPlayingQueue(removedTrack.getId());
                                                        }
                                                        populateQueueSection(root);
                                                    }).show();
                                        }
                                    }
                                }
                            }
                    );
                    touchHelper.attachToRecyclerView(recycler);
                }

                if (playingPosition >= 0) {
                    final int scrollPos = playingPosition;
                    recycler.post(() -> {
                        if (recycler.getLayoutManager() instanceof LinearLayoutManager lm) {
                            lm.scrollToPositionWithOffset(scrollPos, 0);
                        }
                    });
                }
            }
        }
    }

    private void updateQueueHeader(@NonNull View root, List<Track> queue) {
        TextView queueLabel = root.findViewById(R.id.sheet_queue_label);
        TextView queueSubtitle = root.findViewById(R.id.sheet_queue_subtitle);
        RecyclerView recycler = root.findViewById(R.id.sheet_queue_list);
        TextView emptyMsg = root.findViewById(R.id.sheet_empty_queue_msg);

        if (queue == null || queue.isEmpty()) {
            if (queueLabel != null) queueLabel.setText("Upcoming Queue");
            if (queueSubtitle != null) queueSubtitle.setText("0 tracks");
            if (recycler != null) recycler.setVisibility(View.GONE);
            if (emptyMsg != null) emptyMsg.setVisibility(View.VISIBLE);
            return;
        } else {
            if (recycler != null) recycler.setVisibility(View.VISIBLE);
            if (emptyMsg != null) emptyMsg.setVisibility(View.GONE);
        }

        double totalDurSec = 0;
        for (Track t : queue) {
            if (t != null) totalDurSec += t.getAudioDuration();
        }
        String durStr = "";
        if (totalDurSec > 0) {
            int totalMins = (int) (totalDurSec / 60);
            if (totalMins >= 60) {
                int hrs = totalMins / 60;
                int mins = totalMins % 60;
                durStr = String.format(Locale.US, "%dh %dmin total", hrs, mins);
            } else {
                durStr = String.format(Locale.US, "%d min total", totalMins);
            }
        }

        String countStr = queue.size() + " track" + (queue.size() != 1 ? "s" : "");
        if (queueLabel != null) queueLabel.setText("Upcoming Queue");
        if (queueSubtitle != null) {
            queueSubtitle.setText(!durStr.isEmpty() ? countStr + " • " + durStr : countStr);
        } else if (queueLabel != null) {
            queueLabel.setText("Queue  •  " + countStr + (!durStr.isEmpty() ? " (" + durStr + ")" : ""));
        }
    }

    private void updatePlaybackProgress(@Nullable View view, @Nullable PlaybackState state) {
        if (view == null || !isAdded() || state == null) return;

        Track track = playbackService != null ? playbackService.getNowPlayingSong() : null;

        ImageView sheetBtnPlayPause = view.findViewById(R.id.sheet_btn_play_pause);
        if (sheetBtnPlayPause != null) {
            boolean isPlaying = state.currentState == PlaybackState.State.PLAYING;
            sheetBtnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause_rounded : R.drawable.ic_play_rounded);
        }

        if (track != null && track.getAudioDuration() > 0) {
            long durationMs = (long) (track.getAudioDuration() * 1000);
            long currentMs = state.currentPositionSecond * 1000L;

            TextView currentTimeView = view.findViewById(R.id.sheet_current_time);
            if (currentTimeView != null) {
                currentTimeView.setText(formatTime(currentMs));
            }

            SeekBar seekBar = view.findViewById(R.id.sheet_seekbar);
            if (seekBar != null && durationMs > 0) {
                seekBar.setProgress((int) ((currentMs * 1000) / durationMs));
            }
        }
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
        View widgetView = view.findViewById(R.id.sheet_signal_path_widget);
        TextView verdictView = view.findViewById(R.id.sheet_signal_verdict);

        TextView sourceTitle = view.findViewById(R.id.sheet_node_source_title);
        TextView engineSubtitle = view.findViewById(R.id.sheet_node_engine_subtitle);

       // View targetBox = view.findViewById(R.id.sheet_node_target_box);
        TextView targetTitle = view.findViewById(R.id.sheet_node_target_title);

        View expandableContainer = view.findViewById(R.id.sheet_signal_path_expandable);
        LinearLayout stepsContainer = view.findViewById(R.id.sheet_signal_path_steps_container);

        if (track != null && getContext() != null) {
            String quality = TagUIUtils.getQualityIndFullString(track);
            if (verdictView != null) {
                verdictView.setText(VerdictFormatter.format(getContext(), quality));
            }

            if (sourceTitle != null) {
                String codec = TagUtils.formatCodec(track);
                if (codec.isEmpty()) codec = track.getAudioEncoding().toUpperCase();
                String res = formatShortResolution(track);
                if (!res.isEmpty()) {
                    sourceTitle.setText(codec + " " + res);
                } else {
                    sourceTitle.setText(codec);
                }
            }
        } else {
            if (verdictView != null) verdictView.setText("IDLE");
            if (sourceTitle != null) sourceTitle.setText("No Source");
        }

        if (getContext() != null) {
            PlaybackTarget target = playbackService != null ? playbackService.getPlayer() : null;
            boolean isStreaming = target != null && target.isStreaming();

            if (engineSubtitle != null) {
                engineSubtitle.setText(isStreaming ? "MusicMate Server" : "Local");
            }

            if (targetTitle != null) {
                String playerLabel = target != null ? apincer.music.core.utils.PlayerNameUtils.getDropdownPlayerLabel(target) : "Local Device";

                if (target == null || target instanceof ExternalAndroidPlayer || (target != null && !target.isStreaming())) {
                    AudioOutputHelper.Device device = AudioOutputHelper.getOutputDevice(getContext(), track);
                    if (device != null && device.getName() != null && !device.getName().isEmpty() && !"Phone Speaker".equalsIgnoreCase(device.getName())) {
                        playerLabel = device.getCompactLabel();
                    } else if (target != null) {
                        playerLabel = apincer.music.core.utils.PlayerNameUtils.getDropdownPlayerLabel(target);
                    } else if (device != null && device.getName() != null && !device.getName().isEmpty()) {
                        playerLabel = device.getName();
                    } else {
                        playerLabel = "Speaker";
                    }
                }

                targetTitle.setText(playerLabel);
            }

            ImageView targetIconView = view.findViewById(R.id.sheet_node_target_icon);
            if (targetIconView != null) {
                Drawable targetDrawable = AudioOutputHelper.getTargetDrawable(getContext(), target, track);
                if (targetDrawable != null) {
                    targetIconView.setImageDrawable(targetDrawable);
                    if (AudioOutputHelper.isExternalAppTarget(target)) {
                        targetIconView.setImageTintList(null);
                    } else {
                        targetIconView.setImageTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00E5FF")));
                    }
                }
            }

            // Dynamic Range (DR) Badge
            /*View drBadge = view.findViewById(R.id.sheet_node_dr_badge);
            TextView drTitle = view.findViewById(R.id.sheet_node_dr_title);
            if (drBadge != null && drTitle != null) {
                double dr = (track != null) ? (track.getDrScore() > 0 ? track.getDrScore() : track.getDynamicRange()) : 0;
                if (dr > 0) {
                    drTitle.setText(String.format(Locale.US, "DR %.0f", dr));
                    drBadge.setVisibility(VISIBLE);
                } else {
                    drBadge.setVisibility(GONE);
                }
            } */

            // Bit-Perfect Direct Badge
            View bitperfectBadge = view.findViewById(R.id.sheet_node_bitperfect_badge);
            if (bitperfectBadge != null) {
                boolean isBitPerfect = false;
                if (track != null) {
                    String enc = track.getAudioEncoding();
                    boolean isLossless = "FLAC".equalsIgnoreCase(enc) || "ALAC".equalsIgnoreCase(enc) || "DSD".equalsIgnoreCase(enc) || "WAV".equalsIgnoreCase(enc) || "AIFF".equalsIgnoreCase(enc);
                    if (isLossless) {
                        if (isStreaming) {
                            isBitPerfect = true;
                        } else if (target != null && !AudioOutputHelper.isExternalAppTarget(target)) {
                            AudioOutputHelper.Device device = AudioOutputHelper.getOutputDevice(getContext(), track);
                            isBitPerfect = device != null && device.isBitPerfect();
                        }
                    }
                }
                bitperfectBadge.setVisibility(isBitPerfect ? VISIBLE : GONE);
            }

            if (widgetView != null) {
                widgetView.setOnClickListener(v -> {
                    if (expandableContainer != null) {
                        boolean isCurrentlyVisible = expandableContainer.getVisibility() == VISIBLE;
                        ViewGroup sceneRoot = (view.getParent() instanceof ViewGroup) ? (ViewGroup) view.getParent() : null;
                        if (sceneRoot != null) {
                            android.transition.TransitionManager.beginDelayedTransition(sceneRoot, new android.transition.AutoTransition().setDuration(200));
                        }
                        if (!isCurrentlyVisible) {
                            expandableContainer.setVisibility(VISIBLE);
                            if (stepsContainer != null) {
                                stepsContainer.removeAllViews();
                                addSignalPathSteps(stepsContainer);
                            }
                        } else {
                            expandableContainer.setVisibility(GONE);
                        }
                    }
                });
            }
        }
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

    private void setupMediaServerTab(View view) {
        View serverHeader = view.findViewById(R.id.server_name);
        if (serverHeader != null && serverHeader.getParent() instanceof View parentHeader) {
            parentHeader.setVisibility(GONE);
        }

        tvServerName = view.findViewById(R.id.server_name);
        tvServerStatus = view.findViewById(R.id.server_status);
        tvServerStatusIcon = view.findViewById(R.id.status_indicator);

        tvServerAddress = view.findViewById(R.id.server_address);
        tvServerBroadcastInfo = view.findViewById(R.id.server_broadcast_info);
        tvEngineDescription = view.findViewById(R.id.tv_engine_description);
       // tvServerPowerBy = view.findViewById(R.id.server_power_by);
        qrCodeImage = view.findViewById(R.id.qr_code_image);

        btnStartServer = view.findViewById(R.id.btn_start_server);
        btnStopServer = view.findViewById(R.id.btn_stop_server);

        View btnCopyUrl = view.findViewById(R.id.btn_copy_server_url);
        if (btnCopyUrl != null) {
            btnCopyUrl.setOnClickListener(v -> {
                if (tvServerAddress != null && getContext() != null) {
                    CharSequence url = tvServerAddress.getText();
                    if (url != null && !url.toString().isEmpty()) {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("Server URL", url.toString());
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(getContext(), "Server URL copied to clipboard", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });
        }

        View btnOpenUrl = view.findViewById(R.id.btn_open_server_url);
        if (btnOpenUrl != null) {
            btnOpenUrl.setOnClickListener(v -> {
                if (tvServerAddress != null && getContext() != null) {
                    CharSequence url = tvServerAddress.getText();
                    if (url != null && !url.toString().isEmpty() && url.toString().startsWith("http")) {
                        try {
                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url.toString()));
                            startActivity(browserIntent);
                        } catch (Exception e) {
                            Toast.makeText(getContext(), "Could not open browser: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });
        }

        if (qrCodeImage != null) {
            qrCodeImage.setOnClickListener(v -> {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Scan with phone or tablet to open WebUI", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnStartServer != null) {
            btnStartServer.setOnClickListener(v -> mediaServerViewModel.startServer());
        }
        if (btnStopServer != null) {
            btnStopServer.setOnClickListener(v -> mediaServerViewModel.stopServer());
        }

        if (tvServerName != null) {
            tvServerName.setText(Constants.getPresentationName());
        }

        setupEngineSwitcher(view);

        observeServerStatus();
       // detectWebEngine();
    }

    /** Runtime web engine switcher (SonicNIO / CoreHTTP / Netty) — persists the
     *  preference and restarts the media server when a new engine is selected. */
    private void setupEngineSwitcher(View view) {
        MaterialButtonToggleGroup engineGroup = view.findViewById(R.id.server_engine_group);
        if (engineGroup == null || getContext() == null) return;

        android.content.SharedPreferences prefs =
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
        String currentEngine = prefs.getString(Constants.PREF_SERVER_ENGINE, "httpcore");

        int checkedId = switch (currentEngine) {
            case "nio" -> R.id.engine_nio;
            case "netty" -> R.id.engine_netty;
            default -> R.id.engine_httpcore;
        };
        engineGroup.check(checkedId);
        updateEngineDescription(currentEngine);

        engineGroup.addOnButtonCheckedListener((group, buttonId, isChecked) -> {
            if (!isChecked) return;
            String newEngine;
            if (buttonId == R.id.engine_nio) {
                newEngine = "nio";
            } else if (buttonId == R.id.engine_netty) {
                newEngine = "netty";
            } else {
                newEngine = "httpcore";
            }
            updateEngineDescription(newEngine);

            String prevEngine = prefs.getString(Constants.PREF_SERVER_ENGINE, "httpcore");
            if (!newEngine.equals(prevEngine)) {
                prefs.edit().putString(Constants.PREF_SERVER_ENGINE, newEngine).apply();
                mediaServerViewModel.restartServer();
                Toast.makeText(getContext(), "Switching engine — restarting server…", Toast.LENGTH_SHORT).show();
            }
        });
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
        if (viewMediaServerPage == null || getContext() == null) return;
        boolean isNetworkAvailable = NetworkUtils.isWifiConnected(requireContext()) || NetworkUtils.isHotspotActive(requireContext());

        switch (status) {
            case RUNNING:
                if (btnStartServer != null) btnStartServer.setVisibility(GONE);
                if (btnStopServer != null) {
                    btnStopServer.setVisibility(VISIBLE);
                    btnStopServer.setEnabled(true);
                }
                if (tvServerAddress != null) tvServerAddress.setVisibility(VISIBLE);
                if (qrCodeImage != null) qrCodeImage.setVisibility(VISIBLE);
                if (tvServerBroadcastInfo != null) {
                    tvServerBroadcastInfo.setVisibility(VISIBLE);
                    tvServerBroadcastInfo.setText("DLNA 1.5 / UPnP AV • Active on Port 9000");
                }

                String ssid = ApplicationUtils.getWifiSSID(getContext());
                if (!StringUtils.isEmpty(ssid)) {
                    if (tvServerStatus != null) tvServerStatus.setText(SERVER_STATUS_ONLINE_PREFIX + " (" + ssid + ")");
                } else if (NetworkUtils.isHotspotActive(requireContext())) {
                    if (tvServerStatus != null) tvServerStatus.setText(SERVER_STATUS_ONLINE_PREFIX + " (Hotspot)");
                } else if (NetworkUtils.isWifiConnected(requireContext())) {
                    if (tvServerStatus != null) tvServerStatus.setText(SERVER_STATUS_ONLINE_PREFIX);
                } else {
                    if (tvServerStatus != null) tvServerStatus.setText(SERVER_STATUS_NO_WIFI);
                }
                if (tvServerStatusIcon != null) tvServerStatusIcon.setBackgroundResource(R.drawable.shape_circle_green);

                String serverLocation = mediaServerViewModel.getServerLocationUrl();
                if (tvServerAddress != null) tvServerAddress.setText(serverLocation);
                //detectWebEngine();
                generateAndSetQRCode(serverLocation);
                break;

            case STOPPED:
            case ERROR:
                if (btnStartServer != null) {
                    btnStartServer.setVisibility(VISIBLE);
                    btnStartServer.setEnabled(isNetworkAvailable);
                }
                if (btnStopServer != null) btnStopServer.setVisibility(GONE);
                if (qrCodeImage != null) qrCodeImage.setVisibility(GONE);
                if (tvServerBroadcastInfo != null) tvServerBroadcastInfo.setVisibility(GONE);

                if (tvServerStatus != null) tvServerStatus.setText(SERVER_STATUS_OFFLINE);
                if (tvServerStatusIcon != null) tvServerStatusIcon.setBackgroundResource(R.drawable.shape_circle_red);

                if (tvServerAddress != null) {
                    tvServerAddress.setVisibility(VISIBLE);
                    tvServerAddress.setText(isNetworkAvailable ? R.string.server_url_not_available : R.string.notification_server_not_running);
                }
                break;

            case STARTING:
                if (qrCodeImage != null) qrCodeImage.setVisibility(GONE);
                if (tvServerAddress != null) tvServerAddress.setVisibility(GONE);
                if (tvServerBroadcastInfo != null) tvServerBroadcastInfo.setVisibility(GONE);
                if (btnStartServer != null) btnStartServer.setEnabled(false);
                if (btnStopServer != null) btnStopServer.setEnabled(false);
                if (tvServerStatus != null) tvServerStatus.setText(SERVER_STATUS_OFFLINE);
                break;
        }
    }

    private void generateAndSetQRCode(String text) {
        if (text == null || text.isEmpty()) {
            if (qrCodeImage != null) qrCodeImage.setVisibility(GONE);
            return;
        }

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
            if (qrCodeImage != null) {
                qrCodeImage.setImageBitmap(bmp);
            }
        } catch (WriterException e) {
            e.printStackTrace();
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

    private static class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.VH> {

        private final List<Track> queue;
        private String currentKey;
        private final OnTrackClickListener listener;

        QueueAdapter(List<Track> queue, @Nullable String currentKey, OnTrackClickListener listener) {
            this.queue = new ArrayList<>(queue);
            this.currentKey = currentKey;
            this.listener = listener;
        }

        public void updateData(List<Track> newQueue, @Nullable String newKey) {
            this.queue.clear();
            this.queue.addAll(newQueue);
            this.currentKey = newKey;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_queue_track, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Track track = queue.get(position);
            boolean isCurrent = currentKey != null
                    && currentKey.equals(track.getUniqueKey());

            holder.position.setText(isCurrent ? "▶" : String.valueOf(position + 1));
            holder.position.setTextColor(isCurrent
                    ? 0xFFFFD700
                    : holder.position.getResources().getColor(
                            android.R.color.darker_gray, null));

            holder.title.setText(track.getTitle());
            holder.title.setTextColor(isCurrent ? 0xFFFFD700 : 0xFFFFFFFF);

            String artist = track.getArtist();
            holder.artist.setText((artist != null && !artist.isEmpty()) ? artist : track.getAlbum());

            double durSec = track.getAudioDuration();
            if (durSec > 0) {
                int mins = (int) durSec / 60;
                int secs = (int) durSec % 60;
                holder.duration.setText(String.format(Locale.US, "%d:%02d", mins, secs));
            } else {
                holder.duration.setText("");
            }

            holder.itemView.setBackgroundColor(
                    isCurrent ? 0x22FFD700 : Color.TRANSPARENT);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTrackClick(track);
                }
            });
        }

        @Override
        public int getItemCount() { return queue.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView position, title, artist, duration;
            final ImageView dragHandle;
            VH(@NonNull View itemView) {
                super(itemView);
                position = itemView.findViewById(R.id.queue_item_position);
                title = itemView.findViewById(R.id.queue_item_title);
                artist = itemView.findViewById(R.id.queue_item_artist);
                duration = itemView.findViewById(R.id.queue_item_duration);
                dragHandle = itemView.findViewById(R.id.queue_item_drag_handle);
            }
        }
    }
}
